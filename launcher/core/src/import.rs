//! Bringing the player's settings over from the official launcher's .minecraft folder.

use std::fs;
use std::path::{Path, PathBuf};

use anyhow::Result;

/// The official launcher's game folder.
pub fn official_dir() -> Option<PathBuf> {
    if let Some(appdata) = std::env::var_os("APPDATA") {
        return Some(PathBuf::from(appdata).join(".minecraft"));
    }
    let home = PathBuf::from(std::env::var_os("HOME")?);
    if cfg!(target_os = "macos") {
        Some(home.join("Library/Application Support/minecraft"))
    } else {
        Some(home.join(".minecraft"))
    }
}

/// What is copied: the options (vanilla and OptiFine), the server list, the resource packs.
const FILES: [&str; 3] = ["options.txt", "optionsof.txt", "servers.dat"];
const FOLDERS: [&str; 1] = ["resourcepacks"];

/// Copies the player's options, servers and resource packs from `from` into `game`, keeping
/// anything `game` already has. Returns what was copied.
pub fn import(from: &Path, game: &Path) -> Result<Vec<String>> {
    let mut copied = Vec::new();
    fs::create_dir_all(game)?;
    for name in FILES {
        let (src, dst) = (from.join(name), game.join(name));
        if src.is_file() && !dst.exists() {
            fs::copy(&src, &dst)?;
            copied.push(name.to_string());
        }
    }
    for folder in FOLDERS {
        let src = from.join(folder);
        let Ok(entries) = fs::read_dir(&src) else {
            continue;
        };
        let dst = game.join(folder);
        fs::create_dir_all(&dst)?;
        for e in entries.filter_map(|e| e.ok()) {
            let target = dst.join(e.file_name());
            // packs are zips or folders; links are skipped
            let kind = e.file_type()?;
            if target.exists() || kind.is_symlink() {
                continue;
            }
            if kind.is_file() {
                fs::copy(e.path(), &target)?;
            } else if kind.is_dir() {
                copy_dir(&e.path(), &target)?;
            } else {
                continue;
            }
            copied.push(format!("{folder}/{}", e.file_name().to_string_lossy()));
        }
    }
    Ok(copied)
}

fn copy_dir(src: &Path, dst: &Path) -> Result<()> {
    fs::create_dir_all(dst)?;
    for e in fs::read_dir(src)?.filter_map(|e| e.ok()) {
        let kind = e.file_type()?;
        let target = dst.join(e.file_name());
        if kind.is_dir() {
            copy_dir(&e.path(), &target)?;
        } else if kind.is_file() {
            fs::copy(e.path(), &target)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn copies_without_overwriting() {
        let from = tempfile::tempdir().unwrap();
        let game = tempfile::tempdir().unwrap();
        fs::write(from.path().join("options.txt"), "fov:1").unwrap();
        fs::write(from.path().join("servers.dat"), "x").unwrap();
        fs::create_dir_all(from.path().join("resourcepacks/Pack/assets")).unwrap();
        fs::write(from.path().join("resourcepacks/Pack/pack.mcmeta"), "{}").unwrap();
        fs::write(game.path().join("servers.dat"), "mine").unwrap();
        let copied = import(from.path(), game.path()).unwrap();
        assert_eq!(copied, vec!["options.txt", "resourcepacks/Pack"]);
        assert_eq!(fs::read_to_string(game.path().join("servers.dat")).unwrap(), "mine");
        assert!(game.path().join("resourcepacks/Pack/pack.mcmeta").is_file());
    }
}
