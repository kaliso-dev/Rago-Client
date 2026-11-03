//! Puts Rago's loader jar into the launcher when `RAGO_LOADER_JAR` names it (CI builds it from
//! loader/ first). Without it the launcher still builds, with the loader off.

use std::path::PathBuf;
use std::{env, fs};

fn main() {
    println!("cargo:rerun-if-env-changed=RAGO_LOADER_JAR");
    let out = PathBuf::from(env::var("OUT_DIR").expect("OUT_DIR")).join("rago-loader.jar");
    match env::var("RAGO_LOADER_JAR") {
        Ok(jar) if !jar.is_empty() => {
            println!("cargo:rerun-if-changed={jar}");
            fs::copy(&jar, &out).unwrap_or_else(|e| panic!("RAGO_LOADER_JAR={jar}: {e}"));
        }
        _ => fs::write(&out, b"").expect("OUT_DIR"),
    }
}
