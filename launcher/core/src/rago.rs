//! The Rago mod: the newest jar of this repository's GitHub releases, checked by the SHA-256
//! GitHub publishes for it, put alone in the game's mods folder.

use std::fs;
use std::path::{Path, PathBuf};

use anyhow::{bail, Result};
use reqwest::blocking::Client;
use serde::Deserialize;

use crate::http::{Download, Hash};

pub const LATEST_RELEASE: &str = "https://api.github.com/repos/kaliso-dev/Rago-Client/releases/latest";

#[derive(Debug, Deserialize)]
struct Release {
    tag_name: String,
    assets: Vec<Asset>,
}

#[derive(Debug, Deserialize)]
struct Asset {
    name: String,
    size: u64,
    browser_download_url: String,
    digest: Option<String>,
}

#[derive(Clone, Debug)]
pub struct RagoMod {
    pub version: String,
    pub file: String,
    pub url: String,
    pub sha256: String,
    pub size: u64,
}

/// Whether `name` is a Rago mod jar for 1.8.9 (not its sources).
pub fn is_rago_jar(name: &str) -> bool {
    name.starts_with("rago-")
        && name.ends_with("+1.8.9.jar")
        && !name.contains(['/', '\\'])
        && !name.contains("..")
        && !name.contains("-sources")
}

pub fn latest(client: &Client) -> Result<RagoMod> {
    let release: Release = client
        .get(LATEST_RELEASE)
        .header("Accept", "application/vnd.github+json")
        .send()?
        .error_for_status()?
        .json()?;
    let Some(asset) = release.assets.into_iter().find(|a| is_rago_jar(&a.name)) else {
        bail!("the latest Rago release ({}) has no jar", release.tag_name);
    };
    let Some(sha256) = asset.digest.as_deref().and_then(|d| d.strip_prefix("sha256:")) else {
        bail!("GitHub gives no hash for {}", asset.name);
    };
    Ok(RagoMod { version: release.tag_name, sha256: sha256.to_string(), file: asset.name, url: asset.browser_download_url, size: asset.size })
}

/// The Rago jar already in `mods`, if any.
pub fn installed(mods: &Path) -> Option<PathBuf> {
    fs::read_dir(mods)
        .ok()?
        .filter_map(|e| e.ok())
        .find(|e| is_rago_jar(&e.file_name().to_string_lossy()))
        .map(|e| e.path())
}

/// Puts `m` in `mods` and takes out every other Rago jar.
pub fn install(client: &Client, m: &RagoMod, mods: &Path) -> Result<PathBuf> {
    if !is_rago_jar(&m.file) {
        bail!("unexpected Rago file name: {}", m.file);
    }
    let path = mods.join(&m.file);
    let download = Download { url: m.url.clone(), path: path.clone(), hash: Hash::Sha256(m.sha256.clone()), size: Some(m.size) };
    if !download.is_present() {
        download.fetch(client)?;
    }
    remove_others(mods, &m.file)?;
    Ok(path)
}

/// Copies a jar built locally into `mods` (for testing a build before it is released).
pub fn install_local(jar: &Path, mods: &Path) -> Result<PathBuf> {
    let Some(name) = jar.file_name().map(|n| n.to_string_lossy().into_owned()) else {
        bail!("not a file: {}", jar.display());
    };
    if !is_rago_jar(&name) {
        bail!("not a Rago jar: {name}");
    }
    fs::create_dir_all(mods)?;
    let path = mods.join(&name);
    fs::copy(jar, &path)?;
    remove_others(mods, &name)?;
    Ok(path)
}

fn remove_others(mods: &Path, keep: &str) -> Result<()> {
    for e in fs::read_dir(mods)? {
        let e = e?;
        let name = e.file_name().to_string_lossy().into_owned();
        if name != keep && is_rago_jar(&name) {
            fs::remove_file(e.path())?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recognises_rago_jars() {
        assert!(is_rago_jar("rago-1.11.0+1.8.9.jar"));
        assert!(!is_rago_jar("rago-1.11.0+1.8.9-sources.jar"));
        assert!(!is_rago_jar("OptiFine_1.8.9_HD_U_M5.jar"));
        assert!(!is_rago_jar("rago-../x+1.8.9.jar"));
    }
}
