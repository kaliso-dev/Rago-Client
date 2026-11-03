//! Signing in with the player's Microsoft account, then Xbox Live, then Minecraft services.
//!
//! The browser opens Microsoft's own sign-in page (authorization code with PKCE); the launcher
//! never sees the password. Microsoft sends the code back to a one-shot server on localhost.
//! Chain: https://minecraft.wiki/w/Microsoft_authentication

use std::io::{BufRead, BufReader, Write};
use std::net::{TcpListener, TcpStream};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use anyhow::{anyhow, bail, Context, Result};
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use reqwest::blocking::Client;
use serde::Deserialize;
use serde_json::json;
use sha2::Digest;

use crate::launch::Account;

/// Rago's own Azure application (public client, personal Microsoft accounts), set at build time
/// with RAGO_MS_CLIENT_ID until Mojang has approved it.
pub fn client_id() -> Option<&'static str> {
    option_env!("RAGO_MS_CLIENT_ID").filter(|s| !s.is_empty())
}

const AUTHORIZE: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const TOKEN: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const SCOPE: &str = "XboxLive.signin offline_access";

fn now() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

fn random_url_safe(bytes: usize) -> Result<String> {
    let mut buf = vec![0u8; bytes];
    getrandom::getrandom(&mut buf).map_err(|e| anyhow!("aléatoire indisponible : {e}"))?;
    Ok(URL_SAFE_NO_PAD.encode(buf))
}

/// A PKCE pair: the secret kept here and its SHA-256 sent to Microsoft.
pub struct Pkce {
    pub verifier: String,
    pub challenge: String,
}

pub fn pkce() -> Result<Pkce> {
    let verifier = random_url_safe(48)?;
    let challenge = URL_SAFE_NO_PAD.encode(sha2::Sha256::digest(verifier.as_bytes()));
    Ok(Pkce { verifier, challenge })
}

/// A sign-in under way: open `url` in the browser, then call `finish`.
pub struct Pending {
    pub url: String,
    listeners: Vec<TcpListener>,
    redirect: String,
    state: String,
    pkce: Pkce,
    client_id: String,
}

/// Starts a sign-in: listens on a free localhost port and builds Microsoft's sign-in address.
pub fn begin(client_id: &str) -> Result<Pending> {
    let v4 = TcpListener::bind("127.0.0.1:0")?;
    let port = v4.local_addr()?.port();
    let mut listeners = vec![v4];
    // browsers may resolve "localhost" to ::1
    if let Ok(v6) = TcpListener::bind(("::1", port)) {
        listeners.push(v6);
    }
    for l in &listeners {
        l.set_nonblocking(true)?;
    }
    let redirect = format!("http://localhost:{port}");
    let state = random_url_safe(16)?;
    let pkce = pkce()?;
    let url = url::Url::parse_with_params(
        AUTHORIZE,
        &[
            ("client_id", client_id),
            ("response_type", "code"),
            ("redirect_uri", redirect.as_str()),
            ("scope", SCOPE),
            ("state", state.as_str()),
            ("code_challenge", pkce.challenge.as_str()),
            ("code_challenge_method", "S256"),
            ("prompt", "select_account"),
        ],
    )?
    .to_string();
    Ok(Pending { url, listeners, redirect, state, pkce, client_id: client_id.to_string() })
}

const PAGE_OK: &str = "<!doctype html><meta charset=utf-8><title>Rago</title><body style=\"background:#0B0A0D;color:#F4F4F5;font-family:sans-serif;display:grid;place-items:center;height:90vh\"><div><h1>Connecté à Rago</h1><p>Tu peux fermer cette page et revenir au launcher.</p></div>";
const PAGE_ERR: &str = "<!doctype html><meta charset=utf-8><title>Rago</title><body style=\"background:#0B0A0D;color:#F4F4F5;font-family:sans-serif;display:grid;place-items:center;height:90vh\"><div><h1>Connexion annulée</h1><p>Reviens au launcher pour réessayer.</p></div>";

fn respond(stream: &mut TcpStream, status: &str, body: &str) {
    let _ = write!(stream, "HTTP/1.1 {status}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}", body.len());
}

impl Pending {
    /// Waits (at most `timeout`) for Microsoft to send the browser back, then gets the tokens.
    pub fn finish(self, client: &Client, timeout: Duration) -> Result<MsTokens> {
        let code = self.wait_for_code(timeout)?;
        let form = [
            ("client_id", self.client_id.as_str()),
            ("grant_type", "authorization_code"),
            ("code", code.as_str()),
            ("redirect_uri", self.redirect.as_str()),
            ("code_verifier", self.pkce.verifier.as_str()),
            ("scope", SCOPE),
        ];
        token_request(client, &form)
    }

    fn wait_for_code(&self, timeout: Duration) -> Result<String> {
        let end = Instant::now() + timeout;
        while Instant::now() < end {
            for l in &self.listeners {
                let Ok((mut stream, _)) = l.accept() else {
                    continue;
                };
                stream.set_nonblocking(false)?;
                stream.set_read_timeout(Some(Duration::from_secs(5)))?;
                let mut line = String::new();
                BufReader::new(&stream).read_line(&mut line)?;
                let target = line.split_whitespace().nth(1).unwrap_or("/");
                let url = url::Url::parse(&format!("http://localhost{target}"))?;
                let get = |k: &str| url.query_pairs().find(|(n, _)| n == k).map(|(_, v)| v.into_owned());
                if url.path() != "/" || (get("code").is_none() && get("error").is_none()) {
                    respond(&mut stream, "404 Not Found", "");
                    continue;
                }
                if get("state").as_deref() != Some(self.state.as_str()) {
                    respond(&mut stream, "400 Bad Request", PAGE_ERR);
                    bail!("réponse de connexion inattendue");
                }
                if let Some(err) = get("error") {
                    respond(&mut stream, "200 OK", PAGE_ERR);
                    bail!("connexion Microsoft refusée : {}", get("error_description").unwrap_or(err));
                }
                respond(&mut stream, "200 OK", PAGE_OK);
                return Ok(get("code").unwrap());
            }
            std::thread::sleep(Duration::from_millis(50));
        }
        bail!("connexion Microsoft trop longue, réessaie")
    }
}

/// Microsoft's tokens. The refresh token signs the player in again without the browser; keep it
/// in the system's credential store, never in a plain file and never sent to Rago's servers.
#[derive(Clone)]
pub struct MsTokens {
    pub access_token: String,
    pub refresh_token: Option<String>,
    pub expires_at: u64,
}

#[derive(Deserialize)]
struct TokenResponse {
    access_token: String,
    refresh_token: Option<String>,
    expires_in: u64,
}

fn token_request(client: &Client, form: &[(&str, &str)]) -> Result<MsTokens> {
    let response = client.post(TOKEN).form(form).send()?;
    if !response.status().is_success() {
        let status = response.status();
        let body: serde_json::Value = response.json().unwrap_or_default();
        bail!("Microsoft a refusé la connexion ({status}) : {}", body["error_description"].as_str().unwrap_or("?"));
    }
    let t: TokenResponse = response.json()?;
    Ok(MsTokens { access_token: t.access_token, refresh_token: t.refresh_token, expires_at: now() + t.expires_in })
}

/// New tokens from a refresh token, without the browser.
pub fn refresh(client: &Client, client_id: &str, refresh_token: &str) -> Result<MsTokens> {
    token_request(client, &[("client_id", client_id), ("grant_type", "refresh_token"), ("refresh_token", refresh_token), ("scope", SCOPE)])
}

#[derive(Deserialize)]
#[serde(rename_all = "PascalCase")]
struct XboxResponse {
    token: String,
    display_claims: DisplayClaims,
}

#[derive(Deserialize)]
struct DisplayClaims {
    xui: Vec<Xui>,
}

#[derive(Deserialize)]
struct Xui {
    uhs: String,
}

fn xbox_error(code: u64) -> &'static str {
    match code {
        2148916233 => "ce compte Microsoft n'a pas de profil Xbox : crée-le sur xbox.com puis réessaie",
        2148916235 => "Xbox Live n'est pas disponible dans ton pays",
        2148916236 | 2148916237 => "ce compte doit être vérifié comme adulte sur xbox.com",
        2148916238 => "compte enfant : un adulte doit l'ajouter à une famille Microsoft",
        _ => "Xbox Live a refusé la connexion",
    }
}

/// A signed-in Minecraft player, valid until `expires_at`.
#[derive(Clone)]
pub struct McSession {
    pub account: Account,
    pub expires_at: u64,
}

/// Microsoft token → Xbox Live → XSTS → Minecraft services → the player's profile.
pub fn minecraft_login(client: &Client, ms_access_token: &str) -> Result<McSession> {
    let xbl: XboxResponse = client
        .post("https://user.auth.xboxlive.com/user/authenticate")
        .json(&json!({
            "Properties": {"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": format!("d={ms_access_token}")},
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        }))
        .send()?
        .error_for_status()
        .context("Xbox Live a refusé la connexion")?
        .json()?;
    let uhs = xbl.display_claims.xui.first().map(|x| x.uhs.clone()).ok_or_else(|| anyhow!("réponse Xbox Live incomplète"))?;

    let xsts = client
        .post("https://xsts.auth.xboxlive.com/xsts/authorize")
        .json(&json!({
            "Properties": {"SandboxId": "RETAIL", "UserTokens": [xbl.token]},
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        }))
        .send()?;
    if xsts.status().as_u16() == 401 {
        let body: serde_json::Value = xsts.json().unwrap_or_default();
        bail!("{}", xbox_error(body["XErr"].as_u64().unwrap_or(0)));
    }
    let xsts: XboxResponse = xsts.error_for_status()?.json()?;

    let login = client
        .post("https://api.minecraftservices.com/authentication/login_with_xbox")
        .json(&json!({"identityToken": format!("XBL3.0 x={uhs};{}", xsts.token)}))
        .send()?;
    if login.status().as_u16() == 403 {
        bail!("Mojang n'a pas encore autorisé l'application Rago à se connecter à Minecraft");
    }
    let login: serde_json::Value = login.error_for_status()?.json()?;
    let access = login["access_token"].as_str().ok_or_else(|| anyhow!("réponse Minecraft incomplète"))?.to_string();
    let expires_at = now() + login["expires_in"].as_u64().unwrap_or(86400);

    let profile = client.get("https://api.minecraftservices.com/minecraft/profile").bearer_auth(&access).send()?;
    if profile.status().as_u16() == 404 {
        bail!("ce compte ne possède pas Minecraft Java Edition");
    }
    let profile: serde_json::Value = profile.error_for_status()?.json()?;
    let name = profile["name"].as_str().ok_or_else(|| anyhow!("profil Minecraft incomplet"))?.to_string();
    let uuid = profile["id"].as_str().ok_or_else(|| anyhow!("profil Minecraft incomplet"))?.replace('-', "");
    Ok(McSession { account: Account { name, uuid, access_token: access }, expires_at })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pkce_challenge_is_the_verifiers_sha256() {
        let p = pkce().unwrap();
        assert!(p.verifier.len() >= 43 && p.verifier.len() <= 128);
        assert_eq!(p.challenge, URL_SAFE_NO_PAD.encode(sha2::Sha256::digest(p.verifier.as_bytes())));
    }

    #[test]
    fn sign_in_address_and_redirect() {
        let pending = begin("00000000-0000-0000-0000-000000000000").unwrap();
        let url = url::Url::parse(&pending.url).unwrap();
        let get = |k: &str| url.query_pairs().find(|(n, _)| n == k).map(|(_, v)| v.into_owned()).unwrap();
        assert_eq!(url.host_str(), Some("login.microsoftonline.com"));
        assert_eq!(get("code_challenge_method"), "S256");
        assert!(get("redirect_uri").starts_with("http://localhost:"));
        assert!(get("scope").contains("XboxLive.signin"));

        // the browser comes back with the code
        let port = get("redirect_uri").rsplit(':').next().unwrap().to_string();
        let state = get("state");
        let browser = std::thread::spawn(move || {
            let mut s = TcpStream::connect(format!("127.0.0.1:{port}")).unwrap();
            write!(s, "GET /?code=abc&state={state} HTTP/1.1\r\nHost: localhost\r\n\r\n").unwrap();
            let mut out = String::new();
            std::io::Read::read_to_string(&mut s, &mut out).unwrap();
            out
        });
        assert_eq!(pending.wait_for_code(Duration::from_secs(5)).unwrap(), "abc");
        assert!(browser.join().unwrap().contains("Connecté à Rago"));
    }
}
