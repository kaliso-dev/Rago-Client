//! Installs everything for real and starts the game until Forge has loaded Rago.
//! Slow and online, so ignored by default:
//! RAGO_E2E_DIR=/tmp/rago RAGO_E2E_JAR=../build/libs/rago-x+1.8.9.jar DISPLAY=:99 cargo test -- --ignored

use std::io::{BufRead, BufReader};
use std::path::PathBuf;
use std::process::Stdio;
use std::time::{Duration, Instant};

use rago_launcher_core::install::{install, Layout, RagoSource, Step};
use rago_launcher_core::{command, http, Account, Options};

#[test]
#[ignore]
fn installs_and_starts_the_game() {
    let dir = PathBuf::from(std::env::var("RAGO_E2E_DIR").expect("RAGO_E2E_DIR"));
    let source = match std::env::var("RAGO_E2E_JAR") {
        Ok(jar) => RagoSource::Local(jar.into()),
        Err(_) => RagoSource::Keep,
    };
    let client = http::client().unwrap();
    let started = Instant::now();
    let inst = install(&client, &Layout::new(&dir), &source, &|s| {
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

    // a test profile with no token: the game opens, nothing online works
    let account = Account { name: "RagoTest".into(), uuid: "0".repeat(32), access_token: "0".into() };
    let mut child = command(&inst, &account, &Options::default()).stdout(Stdio::piped()).stderr(Stdio::null()).spawn().unwrap();
    let out = BufReader::new(child.stdout.take().unwrap());
    let deadline = Instant::now() + Duration::from_secs(240);
    let (mut fml, mut rago, mut loaded) = (false, false, false);
    for line in out.lines() {
        let Ok(line) = line else { break };
        eprintln!("{line}");
        fml |= line.contains("Forge Mod Loader version");
        // Rago's coremod logs each hook it puts into Minecraft's classes
        rago |= line.contains("logger=\"Rago\"") || line.contains("Hook installed");
        loaded |= line.contains("Forge Mod Loader has successfully loaded");
        if loaded || Instant::now() > deadline {
            break;
        }
    }
    let _ = child.kill();
    let _ = child.wait();
    // a machine without a graphics card (CI on Windows) stops at the window; Forge and Rago
    // have run by then
    assert!(fml, "Forge never started");
    assert!(rago || matches!(source, RagoSource::Keep), "Rago was not loaded");
    eprintln!("mods loaded: {loaded}");
}
