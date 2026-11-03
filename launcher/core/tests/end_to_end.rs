//! Installs everything for real and starts the game until Forge has loaded Rago, then the same
//! with Rago's own loader on Java 21 when the launcher was built with it (RAGO_LOADER_JAR).
//! Slow and online, so ignored by default:
//! RAGO_E2E_DIR=/tmp/rago RAGO_E2E_JAR=../build/libs/rago-x+1.8.9.jar DISPLAY=:99 cargo test -- --ignored

use std::io::{BufRead, BufReader};
use std::path::PathBuf;
use std::process::Stdio;
use std::time::{Duration, Instant};

use rago_launcher_core::install::{install, Installed, Layout, RagoSource, Step};
use rago_launcher_core::{command, http, loader, Account, Options};

fn source() -> RagoSource {
    match std::env::var("RAGO_E2E_JAR") {
        Ok(jar) => RagoSource::Local(jar.into()),
        Err(_) => RagoSource::Keep,
    }
}

fn layout() -> Layout {
    Layout::new(PathBuf::from(std::env::var("RAGO_E2E_DIR").expect("RAGO_E2E_DIR")))
}

fn install_all(client: &reqwest::blocking::Client, source: &RagoSource) -> Installed {
    let started = Instant::now();
    let inst = install(client, &layout(), source, &|s| {
        if let Step::Files(p) = s {
            if p.done % 200 == 0 || p.done == p.total {
                eprintln!("{}/{} files, {} MB", p.done, p.total, p.bytes / 1_000_000);
            }
        } else {
            eprintln!("{s:?}");
        }
    })
    .unwrap();
    eprintln!("installed in {:?}", started.elapsed());
    inst
}

/// Starts the game with a test profile (no token: the game opens, nothing online works) and calls
/// `seen` with each line it prints until `seen` says to stop or four minutes have passed.
fn run(inst: &Installed, mut seen: impl FnMut(&str) -> bool) {
    let account = Account { name: "RagoTest".into(), uuid: "0".repeat(32), access_token: "0".into() };
    let mut child = command(inst, &account, &Options::default()).stdout(Stdio::piped()).stderr(Stdio::inherit()).spawn().unwrap();
    let out = BufReader::new(child.stdout.take().unwrap());
    let deadline = Instant::now() + Duration::from_secs(240);
    for line in out.lines() {
        let Ok(line) = line else { break };
        eprintln!("{line}");
        if seen(&line) || Instant::now() > deadline {
            break;
        }
    }
    let _ = child.kill();
    let _ = child.wait();
}

#[test]
#[ignore]
fn installs_and_starts_the_game() {
    let source = source();
    let inst = install_all(&http::client().unwrap(), &source);
    let (mut fml, mut rago, mut loaded) = (false, false, false);
    run(&inst, |line| {
        fml |= line.contains("Forge Mod Loader version");
        // Rago's coremod logs each hook it puts into Minecraft's classes
        rago |= line.contains("logger=\"Rago\"") || line.contains("Hook installed");
        loaded |= line.contains("Forge Mod Loader has successfully loaded");
        loaded
    });
    // a machine without a graphics card (CI on Windows) stops at the window; Forge and Rago
    // have run by then
    assert!(fml, "Forge never started");
    assert!(rago || matches!(source, RagoSource::Keep), "Rago was not loaded");
    eprintln!("mods loaded: {loaded}");
}

#[test]
#[ignore]
fn starts_rago_with_its_own_loader() {
    if !loader::available() {
        eprintln!("launcher built without RAGO_LOADER_JAR: nothing to test");
        return;
    }
    // RAGO_E2E_JAVA: a Java 21 to use instead of Temurin's (Linux, where Temurin's cannot load LWJGL 2)
    let java = std::env::var("RAGO_E2E_JAVA").ok().map(PathBuf::from);
    if java.is_none() && !loader::supported(rago_launcher_core::os::Os::current()) {
        eprintln!("the loader does not run here without RAGO_E2E_JAVA");
        return;
    }
    let client = http::client().unwrap();
    let inst = install_all(&client, &source());
    let started = Instant::now();
    let inst = match java {
        Some(java) => loader::prepare_with(&layout(), &inst, java),
        None => loader::prepare(&client, &layout(), &inst),
    }
    .unwrap();
    eprintln!("Java {} and the renamed game ready in {:?}", loader::JAVA, started.elapsed());
    let (mut found, mut hooked, mut rago, mut running) = (false, false, false, false);
    run(&inst, |line| {
        found |= line.contains("[RagoLoader] 1 mod(s) found");
        hooked |= line.contains("[RagoLoader] hooked net.minecraft.client.Minecraft");
        rago |= line.contains("Hook installed");
        running |= line.contains("mod(s) running");
        running
    });
    // without a graphics card the game stops at the window, after the loader has found Rago,
    // changed the game's classes and run Rago's own transformer
    assert!(found, "the loader did not find Rago");
    assert!(hooked, "the loader did not change Minecraft's classes");
    assert!(rago, "Rago's transformer did not run");
    eprintln!("mods running: {running}");
}
