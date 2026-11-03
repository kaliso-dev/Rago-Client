// No console window next to the launcher on Windows
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::Duration;

use rago_launcher_core::auth::{self, McSession};
use rago_launcher_core::install::{install, Layout, RagoSource, Step};
use rago_launcher_core::{command, http, Options};
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager, State};
use tauri_plugin_dialog::DialogExt;
use tauri_plugin_opener::OpenerExt;

const KEYRING_SERVICE: &str = "gg.rago.launcher";
const OPTIFINE_PAGE: &str = "https://optifine.net/downloads";

/// The launcher's folder: %APPDATA%\.rago on Windows, ~/.rago elsewhere.
fn root() -> PathBuf {
    if let Some(appdata) = std::env::var_os("APPDATA") {
        return PathBuf::from(appdata).join(".rago");
    }
    let home = std::env::var_os("HOME").map(PathBuf::from).unwrap_or_else(|| PathBuf::from("."));
    home.join(".rago")
}

/// What the launcher remembers between runs. No token in here: the refresh token is in the
/// system's credential store.
#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Settings {
    memory_mb: Option<u32>,
    account_name: Option<String>,
    account_uuid: Option<String>,
}

fn settings_path() -> PathBuf {
    root().join("launcher.json")
}

fn load_settings() -> Settings {
    fs::read(settings_path()).ok().and_then(|d| serde_json::from_slice(&d).ok()).unwrap_or_default()
}

fn save_settings(s: &Settings) -> Result<(), String> {
    fs::create_dir_all(root()).map_err(err)?;
    fs::write(settings_path(), serde_json::to_vec_pretty(s).map_err(err)?).map_err(err)
}

fn err(e: impl std::fmt::Display) -> String {
    e.to_string()
}

fn keyring_entry() -> Result<keyring::Entry, String> {
    keyring::Entry::new(KEYRING_SERVICE, "microsoft").map_err(err)
}

/// The signed-in player, kept in memory for the run.
#[derive(Default)]
struct AppState {
    session: Mutex<Option<McSession>>,
    busy: Mutex<bool>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct View {
    account: Option<AccountView>,
    sign_in_ready: bool,
    memory_mb: u32,
    optifine: Option<String>,
    folder: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct AccountView {
    name: String,
    uuid: String,
}

fn optifine_in(mods: &Path) -> Option<String> {
    fs::read_dir(mods).ok()?.filter_map(|e| e.ok()).map(|e| e.file_name().to_string_lossy().into_owned()).find(|n| is_optifine(n))
}

fn is_optifine(name: &str) -> bool {
    name.starts_with("OptiFine_1.8.9_") && name.ends_with(".jar") && !name.contains(['/', '\\']) && !name.contains("..")
}

#[tauri::command]
fn state() -> View {
    let s = load_settings();
    let layout = Layout::new(root());
    View {
        account: s.account_name.zip(s.account_uuid).map(|(name, uuid)| AccountView { name, uuid }),
        sign_in_ready: auth::client_id().is_some(),
        memory_mb: s.memory_mb.unwrap_or(2048),
        optifine: optifine_in(&layout.mods()),
        folder: root().to_string_lossy().into_owned(),
    }
}

/// Opens Microsoft's sign-in page in the browser and waits for the player.
#[tauri::command]
async fn sign_in(app: AppHandle, state: State<'_, AppState>) -> Result<AccountView, String> {
    let Some(client_id) = auth::client_id() else {
        return Err("La connexion Microsoft arrive bientôt : Mojang doit encore autoriser l'application Rago.".into());
    };
    let pending = auth::begin(client_id).map_err(err)?;
    app.opener().open_url(&pending.url, None::<&str>).map_err(err)?;
    let session = tauri::async_runtime::spawn_blocking(move || -> anyhow::Result<McSession> {
        let client = http::client()?;
        let tokens = pending.finish(&client, Duration::from_secs(300))?;
        if let Some(refresh) = &tokens.refresh_token {
            keyring::Entry::new(KEYRING_SERVICE, "microsoft")?.set_password(refresh)?;
        }
        auth::minecraft_login(&client, &tokens.access_token)
    })
    .await
    .map_err(err)?
    .map_err(|e| format!("{e:#}"))?;
    let mut s = load_settings();
    s.account_name = Some(session.account.name.clone());
    s.account_uuid = Some(session.account.uuid.clone());
    save_settings(&s)?;
    let view = AccountView { name: session.account.name.clone(), uuid: session.account.uuid.clone() };
    *state.session.lock().unwrap() = Some(session);
    Ok(view)
}

#[tauri::command]
fn sign_out(state: State<'_, AppState>) -> Result<(), String> {
    *state.session.lock().unwrap() = None;
    if let Ok(entry) = keyring_entry() {
        let _ = entry.delete_credential();
    }
    let mut s = load_settings();
    s.account_name = None;
    s.account_uuid = None;
    save_settings(&s)
}

#[tauri::command]
fn set_memory(mb: u32) -> Result<(), String> {
    let mut s = load_settings();
    s.memory_mb = Some(mb.clamp(1024, 8192));
    save_settings(&s)
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ProgressEvent {
    stage: &'static str,
    done: usize,
    total: usize,
    megabytes: u64,
}

/// A Minecraft session still valid for an hour, from memory or from the stored refresh token.
fn fresh_session(state: &AppState) -> Result<McSession, String> {
    let now = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    if let Some(s) = state.session.lock().unwrap().as_ref() {
        if s.expires_at > now + 3600 {
            return Ok(s.clone());
        }
    }
    let client_id = auth::client_id().ok_or("Connecte-toi d'abord avec ton compte Microsoft.")?;
    let refresh = keyring_entry()?.get_password().map_err(|_| "Connecte-toi d'abord avec ton compte Microsoft.".to_string())?;
    let client = http::client().map_err(err)?;
    let tokens = auth::refresh(&client, client_id, &refresh).map_err(|e| format!("{e:#}"))?;
    if let Some(r) = &tokens.refresh_token {
        let _ = keyring_entry()?.set_password(r);
    }
    let session = auth::minecraft_login(&client, &tokens.access_token).map_err(|e| format!("{e:#}"))?;
    *state.session.lock().unwrap() = Some(session.clone());
    Ok(session)
}

/// Installs what is missing, then starts the game. Events: "progress", "playing", "closed".
#[tauri::command]
async fn play(app: AppHandle) -> Result<(), String> {
    {
        let state = app.state::<AppState>();
        let mut busy = state.busy.lock().unwrap();
        if *busy {
            return Err("Le jeu est déjà en route.".into());
        }
        *busy = true;
    }
    let handle = app.clone();
    let result = tauri::async_runtime::spawn_blocking(move || -> Result<(), String> {
        let state = handle.state::<AppState>();
        let session = fresh_session(&state)?;
        let client = http::client().map_err(err)?;
        let layout = Layout::new(root());
        let emit = |stage: &'static str, done: usize, total: usize, megabytes: u64| {
            let _ = handle.emit("progress", ProgressEvent { stage, done, total, megabytes });
        };
        let installed = install(&client, &layout, &RagoSource::Latest, &|step| match step {
            Step::Version => emit("version", 0, 0, 0),
            Step::Files(p) => emit("files", p.done, p.total, p.bytes / 1_000_000),
            Step::Natives => emit("natives", 0, 0, 0),
            Step::Java => emit("java", 0, 0, 0),
            Step::Rago => emit("rago", 0, 0, 0),
        })
        .map_err(|e| format!("{e:#}"))?;
        let opts = Options { memory_mb: load_settings().memory_mb.unwrap_or(2048), extra_jvm: Vec::new() };
        let mut child = command(&installed, &session.account, &opts)
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .spawn()
            .map_err(|e| format!("Java n'a pas pu démarrer : {e}"))?;
        let _ = handle.emit("playing", ());
        if let Some(w) = handle.get_webview_window("main") {
            let _ = w.minimize();
        }
        let status = child.wait().map_err(err)?;
        let _ = handle.emit("closed", status.code());
        if let Some(w) = handle.get_webview_window("main") {
            let _ = w.unminimize();
        }
        Ok(())
    })
    .await
    .map_err(err)?;
    *app.state::<AppState>().busy.lock().unwrap() = false;
    result
}

#[tauri::command]
fn open_optifine_page(app: AppHandle) -> Result<(), String> {
    app.opener().open_url(OPTIFINE_PAGE, None::<&str>).map_err(err)
}

/// Lets the player pick the OptiFine jar they downloaded themselves and puts it in the mods folder.
#[tauri::command]
async fn add_optifine(app: AppHandle) -> Result<Option<String>, String> {
    let Some(picked) = app.dialog().file().add_filter("OptiFine", &["jar"]).blocking_pick_file() else {
        return Ok(None);
    };
    let path = picked.into_path().map_err(err)?;
    let name = path.file_name().map(|n| n.to_string_lossy().into_owned()).unwrap_or_default();
    if !is_optifine(&name) {
        return Err("Choisis le fichier OptiFine pour Minecraft 1.8.9 (OptiFine_1.8.9_….jar).".into());
    }
    let mods = Layout::new(root()).mods();
    fs::create_dir_all(&mods).map_err(err)?;
    for old in fs::read_dir(&mods).map_err(err)?.filter_map(|e| e.ok()) {
        if is_optifine(&old.file_name().to_string_lossy()) {
            fs::remove_file(old.path()).map_err(err)?;
        }
    }
    fs::copy(&path, mods.join(&name)).map_err(err)?;
    Ok(Some(name))
}

#[tauri::command]
fn open_folder(app: AppHandle) -> Result<(), String> {
    let game = Layout::new(root()).game();
    fs::create_dir_all(&game).map_err(err)?;
    app.opener().open_path(game.to_string_lossy(), None::<&str>).map_err(err)
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .manage(AppState::default())
        .invoke_handler(tauri::generate_handler![state, sign_in, sign_out, set_memory, play, open_optifine_page, add_optifine, open_folder])
        .run(tauri::generate_context!())
        .expect("Rago launcher");
}
