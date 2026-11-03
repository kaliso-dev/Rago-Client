// No console window next to the launcher on Windows
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::collections::HashMap;
use std::fs::{self, File};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, SystemTime};

use base64::engine::general_purpose::STANDARD;
use base64::Engine;
use rago_launcher_core::auth::{self, McSession};
use rago_launcher_core::install::{install, repair as repair_install, Layout, RagoSource, Step};
use rago_launcher_core::{command, http, import, rago, skin, updates, Options};
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager, State};
use tauri_plugin_dialog::DialogExt;
use tauri_plugin_opener::OpenerExt;

const KEYRING_SERVICE: &str = "gg.rago.launcher";
const OPTIFINE_PAGE: &str = "https://optifine.net/downloads";
const VERSION: &str = env!("CARGO_PKG_VERSION");

/// The launcher's folder: %APPDATA%\.rago on Windows, ~/.rago elsewhere.
fn root() -> PathBuf {
    if let Some(appdata) = std::env::var_os("APPDATA") {
        return PathBuf::from(appdata).join(".rago");
    }
    let home = std::env::var_os("HOME").map(PathBuf::from).unwrap_or_else(|| PathBuf::from("."));
    home.join(".rago")
}

fn layout() -> Layout {
    Layout::new(root())
}

fn err(e: impl std::fmt::Display) -> String {
    e.to_string()
}

fn err_chain(e: anyhow::Error) -> String {
    format!("{e:#}")
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SavedAccount {
    name: String,
    uuid: String,
}

/// What the launcher remembers between runs. No token in here: each account's refresh token is
/// in the system's credential store.
#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Settings {
    memory_mb: Option<u32>,
    #[serde(default)]
    accounts: Vec<SavedAccount>,
    selected: Option<String>,
    /// "minimize", "close" or "keep": what the launcher does once the game has started
    on_launch: Option<String>,
}

fn settings_path() -> PathBuf {
    root().join("launcher.json")
}

fn load_settings() -> Settings {
    fs::read(settings_path()).ok().and_then(|d| serde_json::from_slice(&d).ok()).unwrap_or_default()
}

fn save_settings(s: &Settings) -> Result<(), String> {
    fs::create_dir_all(root()).map_err(err)?;
    let tmp = settings_path().with_extension("json.tmp");
    fs::write(&tmp, serde_json::to_vec_pretty(s).map_err(err)?).map_err(err)?;
    fs::rename(tmp, settings_path()).map_err(err)
}

fn keyring_entry(uuid: &str) -> Result<keyring::Entry, String> {
    keyring::Entry::new(KEYRING_SERVICE, &format!("microsoft:{uuid}")).map_err(err)
}

#[derive(Default)]
struct AppState {
    sessions: Mutex<HashMap<String, McSession>>,
    busy: Mutex<bool>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct View {
    accounts: Vec<SavedAccount>,
    selected: Option<String>,
    sign_in_ready: bool,
    memory_mb: u32,
    on_launch: String,
    optifine: Option<String>,
    rago: Option<String>,
    official_found: bool,
    version: String,
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
    let l = layout();
    let selected = s.selected.clone().filter(|u| s.accounts.iter().any(|a| &a.uuid == u)).or_else(|| s.accounts.first().map(|a| a.uuid.clone()));
    View {
        selected,
        accounts: s.accounts,
        sign_in_ready: auth::client_id().is_some(),
        memory_mb: s.memory_mb.unwrap_or(2048),
        on_launch: s.on_launch.unwrap_or_else(|| "minimize".into()),
        optifine: optifine_in(&l.mods()),
        rago: rago::installed(&l.mods()).and_then(|p| p.file_name().map(|n| n.to_string_lossy().into_owned())),
        official_found: import::official_dir().is_some_and(|d| d.is_dir()),
        version: VERSION.into(),
    }
}

/// Opens Microsoft's sign-in page in the browser, waits for the player, and adds the account.
#[tauri::command]
async fn sign_in(app: AppHandle, state: State<'_, AppState>) -> Result<SavedAccount, String> {
    let Some(client_id) = auth::client_id() else {
        return Err("La connexion Microsoft arrive bientôt : Mojang doit encore autoriser l'application Rago.".into());
    };
    let pending = auth::begin(client_id).map_err(err)?;
    app.opener().open_url(&pending.url, None::<&str>).map_err(err)?;
    let session = tauri::async_runtime::spawn_blocking(move || -> anyhow::Result<McSession> {
        let client = http::client()?;
        let tokens = pending.finish(&client, Duration::from_secs(300))?;
        let session = auth::minecraft_login(&client, &tokens.access_token)?;
        if let Some(refresh) = &tokens.refresh_token {
            keyring::Entry::new(KEYRING_SERVICE, &format!("microsoft:{}", session.account.uuid))?.set_password(refresh)?;
        }
        Ok(session)
    })
    .await
    .map_err(err)?
    .map_err(err_chain)?;
    let account = SavedAccount { name: session.account.name.clone(), uuid: session.account.uuid.clone() };
    let mut s = load_settings();
    s.accounts.retain(|a| a.uuid != account.uuid);
    s.accounts.push(account.clone());
    s.selected = Some(account.uuid.clone());
    save_settings(&s)?;
    state.sessions.lock().unwrap().insert(account.uuid.clone(), session);
    Ok(account)
}

#[tauri::command]
fn select_account(uuid: String) -> Result<(), String> {
    let mut s = load_settings();
    if !s.accounts.iter().any(|a| a.uuid == uuid) {
        return Err("Compte inconnu.".into());
    }
    s.selected = Some(uuid);
    save_settings(&s)
}

#[tauri::command]
fn remove_account(uuid: String, state: State<'_, AppState>) -> Result<(), String> {
    state.sessions.lock().unwrap().remove(&uuid);
    if let Ok(entry) = keyring_entry(&uuid) {
        let _ = entry.delete_credential();
    }
    let mut s = load_settings();
    s.accounts.retain(|a| a.uuid != uuid);
    if s.selected.as_deref() == Some(uuid.as_str()) {
        s.selected = s.accounts.first().map(|a| a.uuid.clone());
    }
    save_settings(&s)
}

/// The account's face as a data: URL, cached for a day.
#[tauri::command]
async fn skin_face(uuid: String) -> Result<Option<String>, String> {
    if uuid.len() != 32 || !uuid.chars().all(|c| c.is_ascii_hexdigit()) {
        return Ok(None);
    }
    tauri::async_runtime::spawn_blocking(move || {
        let cache = root().join("cache").join("faces").join(format!("{uuid}.png"));
        let fresh = fs::metadata(&cache).and_then(|m| m.modified()).ok().and_then(|t| SystemTime::now().duration_since(t).ok()).is_some_and(|age| age < Duration::from_secs(86400));
        let png = if fresh {
            fs::read(&cache).ok()
        } else {
            let got = http::client().ok().and_then(|c| skin::face_png(&c, &uuid, 64).ok());
            if let Some(p) = &got {
                let _ = fs::create_dir_all(cache.parent().unwrap()).and_then(|_| fs::write(&cache, p));
            }
            got.or_else(|| fs::read(&cache).ok())
        };
        Ok(png.map(|p| format!("data:image/png;base64,{}", STANDARD.encode(p))))
    })
    .await
    .map_err(err)?
}

#[tauri::command]
fn set_memory(mb: u32) -> Result<(), String> {
    let mut s = load_settings();
    s.memory_mb = Some(mb.clamp(1024, 8192));
    save_settings(&s)
}

#[tauri::command]
fn set_on_launch(value: String) -> Result<(), String> {
    if !["minimize", "close", "keep"].contains(&value.as_str()) {
        return Err("Choix inconnu.".into());
    }
    let mut s = load_settings();
    s.on_launch = Some(value);
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

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ClosedEvent {
    code: Option<i32>,
    crash_report: Option<String>,
}

/// A Minecraft session for `uuid` still valid for an hour, from memory or from its refresh token.
fn fresh_session(state: &AppState, uuid: &str) -> Result<McSession, String> {
    let now = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    if let Some(s) = state.sessions.lock().unwrap().get(uuid) {
        if s.expires_at > now + 3600 {
            return Ok(s.clone());
        }
    }
    let again = || "Reconnecte ce compte avec Microsoft.".to_string();
    let client_id = auth::client_id().ok_or_else(again)?;
    let refresh = keyring_entry(uuid)?.get_password().map_err(|_| again())?;
    let client = http::client().map_err(err)?;
    let tokens = auth::refresh(&client, client_id, &refresh).map_err(|e| format!("{}\n{}", again(), err_chain(e)))?;
    if let Some(r) = &tokens.refresh_token {
        let _ = keyring_entry(uuid)?.set_password(r);
    }
    let session = auth::minecraft_login(&client, &tokens.access_token).map_err(err_chain)?;
    if session.account.uuid != uuid {
        return Err(again());
    }
    state.sessions.lock().unwrap().insert(uuid.to_string(), session.clone());
    Ok(session)
}

/// The newest crash report written since `since`.
fn crash_report_since(game: &Path, since: SystemTime) -> Option<PathBuf> {
    fs::read_dir(game.join("crash-reports"))
        .ok()?
        .filter_map(|e| e.ok())
        .filter_map(|e| Some((e.metadata().ok()?.modified().ok()?, e.path())))
        .filter(|(t, _)| *t >= since)
        .max_by_key(|(t, _)| *t)
        .map(|(_, p)| p)
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
        let settings = load_settings();
        let uuid = settings.selected.clone().or_else(|| settings.accounts.first().map(|a| a.uuid.clone())).ok_or("Connecte-toi d'abord avec ton compte Microsoft.")?;
        let session = fresh_session(&state, &uuid)?;
        let client = http::client().map_err(err)?;
        let layout = layout();
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
        .map_err(err_chain)?;
        let opts = Options { memory_mb: settings.memory_mb.unwrap_or(2048), extra_jvm: Vec::new() };
        // the game's own output, for when something goes wrong (never holds the token: the game does not print it)
        let logs = installed.game.join("logs");
        fs::create_dir_all(&logs).map_err(err)?;
        let log = File::create(logs.join("rago-launcher.log")).map_err(err)?;
        let started = SystemTime::now();
        let mut child = command(&installed, &session.account, &opts)
            .stdout(log.try_clone().map_err(err)?)
            .stderr(log)
            .spawn()
            .map_err(|e| format!("Java n'a pas pu démarrer : {e}"))?;
        let _ = handle.emit("playing", ());
        match settings.on_launch.as_deref().unwrap_or("minimize") {
            "close" => {
                // the game keeps running on its own
                handle.exit(0);
                return Ok(());
            }
            "keep" => {}
            _ => {
                if let Some(w) = handle.get_webview_window("main") {
                    let _ = w.minimize();
                }
            }
        }
        let status = child.wait().map_err(err)?;
        let crash = if status.success() { None } else { crash_report_since(&installed.game, started) };
        let _ = handle.emit("closed", ClosedEvent { code: status.code(), crash_report: crash.map(|p| p.to_string_lossy().into_owned()) });
        if let Some(w) = handle.get_webview_window("main") {
            let _ = w.unminimize();
            let _ = w.set_focus();
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
    let mods = layout().mods();
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
fn remove_optifine() -> Result<(), String> {
    let mods = layout().mods();
    for e in fs::read_dir(&mods).map_err(err)?.filter_map(|e| e.ok()) {
        if is_optifine(&e.file_name().to_string_lossy()) {
            fs::remove_file(e.path()).map_err(err)?;
        }
    }
    Ok(())
}

/// Opens a folder of the game: "game", "logs", "mods", "resourcepacks" or "screenshots".
#[tauri::command]
fn open_folder(app: AppHandle, which: String) -> Result<(), String> {
    let game = layout().game();
    let dir = match which.as_str() {
        "game" => game,
        "logs" | "mods" | "resourcepacks" | "screenshots" => game.join(&which),
        _ => return Err("Dossier inconnu.".into()),
    };
    fs::create_dir_all(&dir).map_err(err)?;
    app.opener().open_path(dir.to_string_lossy(), None::<&str>).map_err(err)
}

/// Opens a crash report the launcher pointed to (only inside the game's crash-reports folder).
#[tauri::command]
fn open_crash_report(app: AppHandle, path: String) -> Result<(), String> {
    let folder = layout().game().join("crash-reports").canonicalize().map_err(err)?;
    let file = PathBuf::from(path).canonicalize().map_err(err)?;
    if !file.starts_with(&folder) {
        return Err("Rapport introuvable.".into());
    }
    app.opener().open_path(file.to_string_lossy(), None::<&str>).map_err(err)
}

/// Copies options, servers and resource packs from the official launcher's .minecraft.
#[tauri::command]
async fn import_official() -> Result<Vec<String>, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let from = import::official_dir().filter(|d| d.is_dir()).ok_or("Aucun dossier .minecraft trouvé.")?;
        import::import(&from, &layout().game()).map_err(err_chain)
    })
    .await
    .map_err(err)?
}

/// Makes the next Play download the game and Java again; worlds, options and mods stay.
#[tauri::command]
fn repair() -> Result<(), String> {
    repair_install(&layout()).map_err(err_chain)
}

#[tauri::command]
async fn check_update() -> Result<Option<updates::Update>, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let client = http::client().map_err(err)?;
        updates::check(&client, VERSION).map_err(err_chain)
    })
    .await
    .map_err(err)?
}

/// Downloads the newer installer (checked by SHA-256), starts it and closes the launcher.
#[tauri::command]
async fn install_update(app: AppHandle, update: updates::Update) -> Result<(), String> {
    let path = tauri::async_runtime::spawn_blocking(move || -> Result<PathBuf, String> {
        // the update must come from this repository's releases
        if !update.url.starts_with("https://github.com/kaliso-dev/Rago-Client/releases/download/") || update.file.contains(['/', '\\']) {
            return Err("Mise à jour refusée : elle ne vient pas des versions de Rago.".into());
        }
        let client = http::client().map_err(err)?;
        let path = root().join("updates").join(&update.file);
        let d = http::Download { url: update.url, path: path.clone(), hash: http::Hash::Sha256(update.sha256), size: Some(update.size) };
        if !d.is_present() {
            d.fetch(&client).map_err(err_chain)?;
        }
        Ok(path)
    })
    .await
    .map_err(err)??;
    std::process::Command::new(&path).spawn().map_err(|e| format!("L'installeur n'a pas pu démarrer : {e}"))?;
    app.exit(0);
    Ok(())
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .manage(AppState::default())
        .invoke_handler(tauri::generate_handler![
            state,
            sign_in,
            select_account,
            remove_account,
            skin_face,
            set_memory,
            set_on_launch,
            play,
            open_optifine_page,
            add_optifine,
            remove_optifine,
            open_folder,
            open_crash_report,
            import_official,
            repair,
            check_update,
            install_update
        ])
        .run(tauri::generate_context!())
        .expect("Rago launcher");
}
