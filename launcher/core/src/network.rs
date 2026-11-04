//! Rago Network, the server behind Rago's online features (the same one the client talks to in
//! game): signing in with the Minecraft account, friends and Rago Points.
//!
//! Signing in is the check every Minecraft server makes: the launcher tells Mojang it joins a
//! server id made from Rago Network's challenge, and Rago Network asks Mojang whether it did. The
//! Minecraft token only ever goes to Mojang.

use std::time::Duration;

use anyhow::{anyhow, bail, Context, Result};
pub use reqwest::blocking::Client;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha1::{Digest, Sha1};

use crate::launch::Account;

pub const SERVER: &str = "https://rago-network.onrender.com";
const MOJANG_JOIN: &str = "https://sessionserver.mojang.com/session/minecraft/join";
/// the free host sleeps when idle and takes a while to wake up
const TIMEOUT: Duration = Duration::from_secs(60);

/// A Rago Network session: the bearer token and when it stops working (seconds since 1970).
#[derive(Clone)]
pub struct Session {
    pub token: String,
    pub expires_at: u64,
}

impl std::fmt::Debug for Session {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Session").field("expires_at", &self.expires_at).finish_non_exhaustive()
    }
}

/// The server id given to Mojang for a challenge: the SHA-1 of "rago-network:" and the challenge,
/// written as Minecraft writes server ids (a signed number in hex).
pub fn join_id(challenge: &str) -> String {
    let mut digest: [u8; 20] = Sha1::digest(format!("rago-network:{challenge}").as_bytes()).into();
    let negative = digest[0] & 0x80 != 0;
    if negative {
        // two's complement: invert and add one
        let mut carry = true;
        for b in digest.iter_mut().rev() {
            *b = !*b;
            if carry {
                let (v, c) = b.overflowing_add(1);
                *b = v;
                carry = c;
            }
        }
    }
    let hex: String = digest.iter().map(|b| format!("{b:02x}")).collect();
    let hex = hex.trim_start_matches('0');
    let hex = if hex.is_empty() { "0" } else { hex };
    if negative { format!("-{hex}") } else { hex.to_string() }
}

fn dashed(uuid: &str) -> String {
    let u = uuid.replace('-', "").to_ascii_lowercase();
    if u.len() != 32 {
        return u;
    }
    format!("{}-{}-{}-{}-{}", &u[..8], &u[8..12], &u[12..16], &u[16..20], &u[20..])
}

fn post(client: &Client, base: &str, path: &str, body: &Value, token: Option<&str>) -> Result<Value> {
    let mut req = client.post(format!("{base}{path}")).timeout(TIMEOUT).json(body);
    if let Some(t) = token {
        req = req.bearer_auth(t);
    }
    let res = req.send().context("Rago Network unreachable")?;
    let status = res.status();
    let value: Value = res.json().unwrap_or(Value::Null);
    if !status.is_success() {
        let code = value["error"].as_str().unwrap_or("error").to_string();
        return Err(anyhow!(NetworkError { status: status.as_u16(), code }));
    }
    Ok(value)
}

/// Rago Network said no: its HTTP status and error code ("sign_in", "not_on_rago", ...).
#[derive(Debug)]
pub struct NetworkError {
    pub status: u16,
    pub code: String,
}

impl std::fmt::Display for NetworkError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let text = match self.code.as_str() {
            "not_on_rago" => "this player has never played with Rago",
            "yourself" => "that is you",
            "bad_name" => "not a Minecraft name",
            "too_many_friends" => "too many friends (200 at most)",
            "too_many_requests" => "too many requests waiting (50 at most)",
            "slow_down" => "too many tries, wait a minute",
            "no_request" => "that request is gone",
            "not_verified" | "unknown_server_id" => "Mojang did not confirm the account",
            _ => return write!(f, "Rago Network refused ({} {})", self.status, self.code),
        };
        f.write_str(text)
    }
}

impl std::error::Error for NetworkError {}

/// Whether an error is Rago Network asking to sign in again.
pub fn needs_sign_in(e: &anyhow::Error) -> bool {
    e.downcast_ref::<NetworkError>().is_some_and(|n| n.status == 401)
}

pub fn sign_in(client: &Client, base: &str, account: &Account, now: u64) -> Result<Session> {
    let start = post(client, base, "/v1/auth/start", &json!({ "name": account.name }), None)?;
    let challenge = start["serverId"].as_str().unwrap_or_default();
    if challenge.len() != 40 || !challenge.bytes().all(|b| b.is_ascii_hexdigit()) {
        bail!("Rago Network sent no sign-in challenge");
    }
    let joined = client
        .post(MOJANG_JOIN)
        .timeout(Duration::from_secs(20))
        .json(&json!({ "accessToken": account.access_token, "selectedProfile": account.uuid, "serverId": join_id(challenge) }))
        .send()
        .context("Mojang unreachable")?;
    if !joined.status().is_success() {
        bail!("Mojang refused the sign-in to Rago Network ({})", joined.status().as_u16());
    }
    let done = post(client, base, "/v1/auth/finish", &json!({ "name": account.name, "serverId": challenge }), None)?;
    let token = done["token"].as_str().filter(|t| t.len() == 64).ok_or_else(|| anyhow!("Rago Network sent no session"))?;
    if done["uuid"].as_str().map(dashed) != Some(dashed(&account.uuid)) {
        bail!("Rago Network signed in another account");
    }
    let ttl = done["expiresIn"].as_u64().unwrap_or(3600);
    Ok(Session { token: token.to_string(), expires_at: now + ttl })
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Friend {
    pub uuid: String,
    pub name: String,
    #[serde(default)]
    pub role: String,
    #[serde(default)]
    pub online: bool,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Person {
    pub uuid: String,
    pub name: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Friends {
    pub friends: Vec<Friend>,
    pub received: Vec<Person>,
    pub sent: Vec<Person>,
}

pub fn friends(client: &Client, base: &str, s: &Session) -> Result<Friends> {
    Ok(serde_json::from_value(post(client, base, "/v1/friends", &json!({}), Some(&s.token))?)?)
}

/// Asks a player to be friends; true when they already asked, so you are friends now.
pub fn add_friend(client: &Client, base: &str, s: &Session, name: &str) -> Result<bool> {
    let v = post(client, base, "/v1/friends/add", &json!({ "name": name }), Some(&s.token))?;
    Ok(v["state"] == "friends")
}

pub fn answer(client: &Client, base: &str, s: &Session, uuid: &str, accept: bool) -> Result<()> {
    post(client, base, "/v1/friends/answer", &json!({ "uuid": uuid, "accept": accept }), Some(&s.token)).map(|_| ())
}

pub fn remove_friend(client: &Client, base: &str, s: &Session, uuid: &str) -> Result<()> {
    post(client, base, "/v1/friends/remove", &json!({ "uuid": uuid }), Some(&s.token)).map(|_| ())
}

/// The player's Rago Points.
pub fn points(client: &Client, base: &str, s: &Session) -> Result<u64> {
    let v = post(client, base, "/v1/rewards", &json!({}), Some(&s.token))?;
    v["points"].as_u64().ok_or_else(|| anyhow!("Rago Network sent no points"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn join_ids_match_the_server_and_the_client() {
        // the same values as the server's test and the client's NetworkClientTest
        assert_eq!(join_id("abc"), "3eb134e9faffe05e70d4f1174927717bece96767");
        assert_eq!(join_id("3f1c9a0e5b7d2c4a6e8f0b1d3c5a7e9f2b4d6a8c"), "-1ed171fca8556eb96ab06194d04b3938f1367609");
    }

    #[test]
    fn uuids_are_compared_with_dashes() {
        assert_eq!(dashed("0123456789ABCDEF0123456789abcdef"), "01234567-89ab-cdef-0123-456789abcdef");
        assert_eq!(dashed("01234567-89ab-cdef-0123-456789abcdef"), "01234567-89ab-cdef-0123-456789abcdef");
    }

    #[test]
    fn refusals_read_well() {
        let e = anyhow!(NetworkError { status: 404, code: "not_on_rago".into() });
        assert_eq!(e.to_string(), "this player has never played with Rago");
        assert!(!needs_sign_in(&e));
        assert!(needs_sign_in(&anyhow!(NetworkError { status: 401, code: "sign_in".into() })));
    }
}
