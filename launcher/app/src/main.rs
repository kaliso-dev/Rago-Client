// No console window next to the launcher on Windows
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::collections::{HashMap, VecDeque};
use std::fs::{self, File};
use std::io::{BufRead, BufReader, Read, Write};
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::{mpsc, Mutex};
use std::time::{Duration, Instant, SystemTime};

use base64::engine::general_purpose::STANDARD;
use base64::Engine;
use rago_launcher_core::auth::{self, McSession};
use rago_launcher_core::install::{install, repair as repair_install, Layout, RagoSource, Step};
use rago_launcher_core::launch::parse_jvm_arguments;
use rago_launcher_core::os::Os;
use rago_launcher_core::options::{self as presets, Preset};
use rago_launcher_core::{command, http, import, java, rago, skin, system, updates, Options};
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
    /// Java's tuned flags (on unless turned off)
    tuned_jvm: Option<bool>,
    /// the player's own JVM arguments
    jvm_args: Option<String>,
    /// a Java 8 the player picked instead of the launcher's
    java_path: Option<String>,
    /// the game window at startup
    width: Option<u32>,
    height: Option<u32>,
    #[serde(default)]
    fullscreen: bool,
    #[serde(default)]
    high_priority: bool,
    #[serde(default)]
    dedicated_gpu: bool,
    /// Rago updated on each Play (on unless turned off)
    auto_update: Option<bool>,
    /// the console page opens when the game starts
    #[serde(default)]
    show_console: bool,
}

impl Settings {
    fn options(&self) -> Result<Options, String> {
        Ok(Options {
            memory_mb: self.memory_mb.unwrap_or_else(|| system::recommended_memory_mb(system::total_memory_mb())),
            tuned_jvm: self.tuned_jvm.unwrap_or(true),
            extra_jvm: parse_jvm_arguments(self.jvm_args.as_deref().unwrap_or(""))?,
            size: self.width.zip(self.height),
            fullscreen: self.fullscreen,
            high_priority: self.high_priority,
        })
    }
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

/// Lines of the game's output the console page shows when it opens.
const CONSOLE_LINES: usize = 4000;

#[derive(Default)]
struct AppState {
    sessions: Mutex<HashMap<String, McSession>>,
    busy: Mutex<bool>,
    console: Mutex<VecDeque<String>>,
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
    system_memory_mb: Option<u64>,
    recommended_memory_mb: u32,
    max_memory_mb: u32,
    cpu_threads: usize,
    tuned_jvm: bool,
    jvm_args: String,
    java_path: Option<String>,
    width: Option<u32>,
    height: Option<u32>,
    fullscreen: bool,
    high_priority: bool,
    dedicated_gpu: bool,
    auto_update: bool,
    show_console: bool,
    game_dir: String,
    windows: bool,
    playing: bool,
}

fn optifine_in(mods: &Path) -> Option<String> {
    fs::read_dir(mods).ok()?.filter_map(|e| e.ok()).map(|e| e.file_name().to_string_lossy().into_owned()).find(|n| is_optifine(n))
}

fn is_optifine(name: &str) -> bool {
    name.starts_with("OptiFine_1.8.9_") && name.ends_with(".jar") && !name.contains(['/', '\\']) && !name.contains("..")
}

#[tauri::command]
fn state(state: State<'_, AppState>) -> View {
    let s = load_settings();
    let l = layout();
    let total = system::total_memory_mb();
    let selected = s.selected.clone().filter(|u| s.accounts.iter().any(|a| &a.uuid == u)).or_else(|| s.accounts.first().map(|a| a.uuid.clone()));
    View {
        selected,
        accounts: s.accounts,
        sign_in_ready: auth::client_id().is_some(),
        memory_mb: s.memory_mb.unwrap_or_else(|| system::recommended_memory_mb(total)),
        on_launch: s.on_launch.clone().unwrap_or_else(|| "minimize".into()),
        optifine: optifine_in(&l.mods()),
        rago: rago::installed(&l.mods()).and_then(|p| p.file_name().map(|n| n.to_string_lossy().into_owned())),
        official_found: import::official_dir().is_some_and(|d| d.is_dir()),
        version: VERSION.into(),
        system_memory_mb: total,
        recommended_memory_mb: system::recommended_memory_mb(total),
        max_memory_mb: system::max_memory_mb(total),
        cpu_threads: system::cpu_threads(),
        tuned_jvm: s.tuned_jvm.unwrap_or(true),
        jvm_args: s.jvm_args.clone().unwrap_or_default(),
        java_path: s.java_path.clone(),
        width: s.width,
        height: s.height,
        fullscreen: s.fullscreen,
        high_priority: s.high_priority,
        dedicated_gpu: s.dedicated_gpu,
        auto_update: s.auto_update.unwrap_or(true),
        show_console: s.show_console,
        game_dir: l.game().to_string_lossy().into_owned(),
        windows: cfg!(windows),
        playing: *state.busy.lock().unwrap(),
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

/// Changes one setting from the settings page, checked here.
#[tauri::command]
fn set_setting(key: String, value: serde_json::Value) -> Result<(), String> {
    use serde_json::Value;
    let mut s = load_settings();
    let flag = || value.as_bool().ok_or("Valeur invalide.");
    let size = |v: &Value| -> Result<Option<u32>, String> {
        match v {
            Value::Null => Ok(None),
            v => v.as_u64().filter(|n| (320..=7680).contains(n)).map(|n| Some(n as u32)).ok_or_else(|| "Taille de fenêtre invalide.".to_string()),
        }
    };
    match key.as_str() {
        "memoryMb" => {
            let mb = value.as_u64().ok_or("Valeur invalide.")? as u32;
            s.memory_mb = Some(mb.clamp(1024, system::max_memory_mb(system::total_memory_mb())));
        }
        "onLaunch" => {
            let v = value.as_str().filter(|v| ["minimize", "close", "keep"].contains(v)).ok_or("Choix inconnu.")?;
            s.on_launch = Some(v.into());
        }
        "tunedJvm" => s.tuned_jvm = Some(flag()?),
        "jvmArgs" => {
            let text = value.as_str().ok_or("Valeur invalide.")?.trim().to_string();
            parse_jvm_arguments(&text)?;
            s.jvm_args = (!text.is_empty()).then_some(text);
        }
        "size" => {
            let w = size(&value["width"])?;
            let h = size(&value["height"])?;
            (s.width, s.height) = if w.is_some() && h.is_some() { (w, h) } else { (None, None) };
        }
        "fullscreen" => s.fullscreen = flag()?,
        "highPriority" => s.high_priority = flag()?,
        "dedicatedGpu" => {
            s.dedicated_gpu = flag()?;
            if !s.dedicated_gpu {
                // forget the choice for every Java the game may have run on
                let javas = java::installed(&layout().runtime(), Os::current()).into_iter().chain(s.java_path.as_deref().map(PathBuf::from));
                for j in javas {
                    let _ = system::set_dedicated_gpu(&j, false);
                }
            }
        }
        "autoUpdate" => s.auto_update = Some(flag()?),
        "showConsole" => s.show_console = flag()?,
        _ => return Err("Réglage inconnu.".into()),
    }
    save_settings(&s)
}

/// Lets the player pick their own Java 8 (java or javaw), checked by running it.
#[tauri::command]
async fn pick_java(app: AppHandle) -> Result<Option<String>, String> {
    let Some(picked) = app.dialog().file().set_title("Choisis l'exécutable de Java 8 (bin/javaw.exe)").blocking_pick_file() else {
        return Ok(None);
    };
    let path = picked.into_path().map_err(err)?;
    let name = path.file_name().map(|n| n.to_string_lossy().to_lowercase()).unwrap_or_default();
    if !["java", "javaw", "java.exe", "javaw.exe"].contains(&name.as_str()) {
        return Err("Choisis le fichier java (javaw.exe sur Windows), dans le dossier bin de Java.".into());
    }
    let probe = path.clone();
    let version = tauri::async_runtime::spawn_blocking(move || java::version(&probe)).await.map_err(err)?.map_err(err_chain)?;
    if !java::is_java_8(&version) {
        return Err(format!("Ce Java est en version {version}. Forge 1.8.9 a besoin de Java 8 (1.8)."));
    }
    let mut s = load_settings();
    s.java_path = Some(path.to_string_lossy().into_owned());
    save_settings(&s)?;
    Ok(Some(version))
}

/// Back to the Java the launcher installs.
#[tauri::command]
fn reset_java() -> Result<(), String> {
    let mut s = load_settings();
    s.java_path = None;
    save_settings(&s)
}

/// Writes a graphics preset into the game's options.txt.
#[tauri::command]
fn apply_preset(name: String, state: State<'_, AppState>) -> Result<(), String> {
    if *state.busy.lock().unwrap() {
        return Err("Ferme le jeu d'abord : il réécrit ses options en quittant.".into());
    }
    presets::apply(&layout().game(), Preset::parse(&name).map_err(err)?).map_err(err_chain)
}

/// The game's output so far, for the console page.
#[tauri::command]
fn console(state: State<'_, AppState>) -> Vec<String> {
    state.console.lock().unwrap().iter().cloned().collect()
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ProgressEvent {
    stage: &'static str,
    done: usize,
    total: usize,
    megabytes: u64,
}

/// Copies the game's output to its log file, to the console page (in batches), and keeps the last
/// lines for when the page opens. The access token is masked, should anything ever print it.
fn pipe_output(handle: &AppHandle, out: impl Read + Send + 'static, err: impl Read + Send + 'static, mut log: File, token: String) -> std::thread::JoinHandle<()> {
    let (tx, rx) = mpsc::channel::<String>();
    for stream in [Box::new(out) as Box<dyn Read + Send>, Box::new(err)] {
        let tx = tx.clone();
        std::thread::spawn(move || {
            let mut reader = BufReader::new(stream);
            let mut buf = Vec::new();
            while reader.read_until(b'\n', &mut buf).map(|n| n > 0).unwrap_or(false) {
                let line = String::from_utf8_lossy(&buf).trim_end_matches(['\r', '\n']).to_string();
                buf.clear();
                if tx.send(line).is_err() {
                    break;
                }
            }
        });
    }
    drop(tx);
    let handle = handle.clone();
    std::thread::spawn(move || {
        let mut batch: Vec<String> = Vec::new();
        let mut last = Instant::now();
        let flush = |batch: &mut Vec<String>| {
            if batch.is_empty() {
                return;
            }
            {
                let state = handle.state::<AppState>();
                let mut console = state.console.lock().unwrap();
                for l in batch.iter() {
                    if console.len() == CONSOLE_LINES {
                        console.pop_front();
                    }
                    console.push_back(l.clone());
                }
            }
            let _ = handle.emit("console", std::mem::take(batch));
        };
        loop {
            match rx.recv_timeout(Duration::from_millis(150)) {
                Ok(mut line) => {
                    if !token.is_empty() && line.contains(&token) {
                        line = line.replace(&token, "<jeton masqué>");
                    }
                    let _ = writeln!(log, "{line}");
                    batch.push(line);
                    if batch.len() >= 200 || last.elapsed() >= Duration::from_millis(150) {
                        flush(&mut batch);
                        last = Instant::now();
                    }
                }
                Err(mpsc::RecvTimeoutError::Timeout) => {
                    flush(&mut batch);
                    last = Instant::now();
                }
                Err(mpsc::RecvTimeoutError::Disconnected) => break,
            }
        }
        flush(&mut batch);
        let _ = log.flush();
    })
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
        let opts = settings.options()?;
        let source = if settings.auto_update.unwrap_or(true) || rago::installed(&layout.mods()).is_none() { RagoSource::Latest } else { RagoSource::Keep };
        let mut installed = install(&client, &layout, &source, &|step| match step {
            Step::Version => emit("version", 0, 0, 0),
            Step::Files(p) => emit("files", p.done, p.total, p.bytes / 1_000_000),
            Step::Natives => emit("natives", 0, 0, 0),
            Step::Java => emit("java", 0, 0, 0),
            Step::Rago => emit("rago", 0, 0, 0),
        })
        .map_err(err_chain)?;
        if let Some(own) = settings.java_path.as_deref().map(PathBuf::from) {
            if !own.is_file() {
                return Err(format!("Le Java choisi n'existe plus : {}. Remets celui du launcher dans les paramètres.", own.display()));
            }
            installed.java = own;
        }
        if settings.dedicated_gpu {
            // a refusal only costs the setting, not the game
            let _ = system::set_dedicated_gpu(&installed.java, true);
        }
        // the game's own output, for when something goes wrong
        let logs = installed.game.join("logs");
        fs::create_dir_all(&logs).map_err(err)?;
        let log = File::create(logs.join("rago-launcher.log")).map_err(err)?;
        handle.state::<AppState>().console.lock().unwrap().clear();
        let started = SystemTime::now();
        let on_launch = settings.on_launch.as_deref().unwrap_or("minimize");
        let mut cmd = command(&installed, &session.account, &opts);
        if on_launch == "close" {
            // the game outlives the launcher: its output goes straight to the file
            cmd.stdout(log.try_clone().map_err(err)?).stderr(log);
            cmd.spawn().map_err(|e| format!("Java n'a pas pu démarrer : {e}"))?;
            let _ = handle.emit("playing", ());
            handle.exit(0);
            return Ok(());
        }
        let mut child = cmd.stdout(Stdio::piped()).stderr(Stdio::piped()).spawn().map_err(|e| format!("Java n'a pas pu démarrer : {e}"))?;
        let output = pipe_output(&handle, child.stdout.take().unwrap(), child.stderr.take().unwrap(), log, session.account.access_token.clone());
        let _ = handle.emit("playing", ());
        match on_launch {
            "keep" => {}
            _ => {
                if let Some(w) = handle.get_webview_window("main") {
                    let _ = w.minimize();
                }
            }
        }
        let status = child.wait().map_err(err)?;
        let _ = output.join();
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
            set_setting,
            pick_java,
            reset_java,
            apply_preset,
            console,
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
