//! Java 8 for Forge 1.8.9: Eclipse Temurin's JRE from the Adoptium API, checked by SHA-256 and
//! unpacked under `runtime/<release>`. Without the network, the last one unpacked is used.

use std::fs::{self, File};
use std::io;
use std::path::{Component, Path, PathBuf};

use anyhow::{bail, Context, Result};
use reqwest::blocking::Client;
use serde::Deserialize;

use crate::http::{Download, Hash};
use crate::os::{adoptium_arch, Os};

const DONE: &str = ".rago-ok";

#[derive(Debug, Deserialize)]
struct Asset {
    release_name: String,
    binary: Binary,
}

#[derive(Debug, Deserialize)]
struct Binary {
    package: Package,
}

#[derive(Debug, Deserialize)]
struct Package {
    link: String,
    checksum: String,
    size: u64,
    name: String,
}

/// The newest Temurin 8 JRE for this machine.
#[derive(Clone, Debug)]
pub struct Jre {
    pub release: String,
    pub url: String,
    pub sha256: String,
    pub size: u64,
    pub file: String,
}

pub fn latest(client: &Client, os: Os) -> Result<Jre> {
    let url = format!(
        "https://api.adoptium.net/v3/assets/latest/8/hotspot?architecture={}&image_type=jre&os={}&vendor=eclipse",
        adoptium_arch(),
        os.adoptium()
    );
    let assets: Vec<Asset> = client.get(url).send()?.error_for_status()?.json()?;
    let Some(a) = assets.into_iter().next() else {
        bail!("Adoptium n'a pas de Java 8 pour ce système");
    };
    if a.release_name.contains(['/', '\\']) || a.release_name.contains("..") {
        bail!("nom de version Java inattendu : {}", a.release_name);
    }
    Ok(Jre {
        release: a.release_name,
        url: a.binary.package.link,
        sha256: a.binary.package.checksum,
        size: a.binary.package.size,
        file: a.binary.package.name,
    })
}

/// The `java` (javaw on Windows) inside an unpacked runtime.
pub fn executable(home: &Path, os: Os) -> PathBuf {
    match os {
        Os::Windows => home.join("bin").join("javaw.exe"),
        Os::Osx => home.join("Contents").join("Home").join("bin").join("java"),
        Os::Linux => home.join("bin").join("java"),
    }
}

/// The version a Java prints with `-version` ("1.8.0_402"), for a Java the player picked.
pub fn version(java: &Path) -> Result<String> {
    // java.exe and javaw.exe answer the same; javaw opens no console window
    let mut cmd = std::process::Command::new(java);
    cmd.arg("-version");
    crate::system::hide_window(&mut cmd);
    let out = cmd.output().with_context(|| format!("{} ne démarre pas", java.display()))?;
    let text = String::from_utf8_lossy(&out.stderr);
    parse_version(&text).ok_or_else(|| anyhow::anyhow!("{} n'est pas Java", java.display()))
}

fn parse_version(text: &str) -> Option<String> {
    let line = text.lines().find(|l| l.contains(" version \""))?;
    let start = line.find('"')? + 1;
    let end = start + line[start..].find('"')?;
    Some(line[start..end].to_string())
}

/// Forge 1.8.9 runs on Java 8 only.
pub fn is_java_8(version: &str) -> bool {
    version.starts_with("1.8.")
}

/// Java 8 ready to run, from `runtime_dir`: the newest release, or the last one unpacked when
/// Adoptium cannot be reached.
pub fn ensure(client: &Client, runtime_dir: &Path, os: Os) -> Result<PathBuf> {
    match latest(client, os) {
        Ok(jre) => install(client, &jre, runtime_dir, os),
        Err(e) => installed(runtime_dir, os).ok_or(e).context("Java 8 introuvable et Adoptium injoignable"),
    }
}

/// The newest runtime already unpacked, if any.
pub fn installed(runtime_dir: &Path, os: Os) -> Option<PathBuf> {
    let mut found: Vec<PathBuf> = fs::read_dir(runtime_dir)
        .ok()?
        .filter_map(|e| e.ok().map(|e| e.path()))
        .filter(|p| p.join(DONE).is_file() && executable(p, os).is_file())
        .collect();
    found.sort();
    found.pop().map(|p| executable(&p, os))
}

pub fn install(client: &Client, jre: &Jre, runtime_dir: &Path, os: Os) -> Result<PathBuf> {
    let home = runtime_dir.join(&jre.release);
    if home.join(DONE).is_file() && executable(&home, os).is_file() {
        return Ok(executable(&home, os));
    }
    let archive = runtime_dir.join(&jre.file);
    let download = Download { url: jre.url.clone(), path: archive.clone(), hash: Hash::Sha256(jre.sha256.clone()), size: Some(jre.size) };
    if !download.is_present() {
        download.fetch(client)?;
    }
    if home.exists() {
        fs::remove_dir_all(&home)?;
    }
    fs::create_dir_all(&home)?;
    if jre.file.ends_with(".zip") {
        unzip_stripped(&archive, &home)?;
    } else if jre.file.ends_with(".tar.gz") {
        untar_stripped(&archive, &home)?;
    } else {
        bail!("archive Java inconnue : {}", jre.file);
    }
    let java = executable(&home, os);
    if !java.is_file() {
        bail!("Java absent de l'archive {}", jre.file);
    }
    File::create(home.join(DONE))?;
    let _ = fs::remove_file(&archive);
    Ok(java)
}

/// The path without its first folder, refused if it could leave the destination.
fn strip_first(path: &Path) -> Option<PathBuf> {
    let mut out = PathBuf::new();
    for (i, c) in path.components().enumerate() {
        match c {
            Component::Normal(part) if i > 0 => out.push(part),
            Component::Normal(_) => {}
            _ => return None,
        }
    }
    (!out.as_os_str().is_empty()).then_some(out)
}

fn unzip_stripped(archive: &Path, dest: &Path) -> Result<()> {
    let mut zip = zip::ZipArchive::new(File::open(archive)?)?;
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i)?;
        let Some(name) = entry.enclosed_name() else {
            continue;
        };
        let Some(rel) = strip_first(&name) else {
            continue;
        };
        let out = dest.join(rel);
        if entry.is_dir() {
            fs::create_dir_all(&out)?;
        } else {
            if let Some(p) = out.parent() {
                fs::create_dir_all(p)?;
            }
            io::copy(&mut entry, &mut File::create(&out)?)?;
            #[cfg(unix)]
            if let Some(mode) = entry.unix_mode() {
                use std::os::unix::fs::PermissionsExt;
                fs::set_permissions(&out, fs::Permissions::from_mode(mode))?;
            }
        }
    }
    Ok(())
}

fn untar_stripped(archive: &Path, dest: &Path) -> Result<()> {
    let mut tar = tar::Archive::new(flate2::read::GzDecoder::new(File::open(archive)?));
    for entry in tar.entries()? {
        let mut entry = entry?;
        let path = entry.path()?.into_owned();
        let Some(rel) = strip_first(&path) else {
            continue;
        };
        let kind = entry.header().entry_type();
        if kind.is_symlink() || kind.is_hard_link() {
            // the JRE needs no links outside its own tree; refuse any that would escape
            let target = entry.link_name()?.map(|t| t.into_owned());
            if target.as_ref().is_none_or(|t| t.is_absolute() || t.components().any(|c| c == Component::ParentDir)) {
                continue;
            }
        }
        let out = dest.join(rel);
        if let Some(p) = out.parent() {
            fs::create_dir_all(p)?;
        }
        entry.unpack(&out)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_java_versions() {
        let v = parse_version("openjdk version \"1.8.0_402\"\nOpenJDK Runtime Environment (Temurin)(build 1.8.0_402-b06)\n").unwrap();
        assert_eq!(v, "1.8.0_402");
        assert!(is_java_8(&v));
        assert!(!is_java_8(&parse_version("openjdk version \"21.0.5\" 2024-10-15\n").unwrap()));
        assert_eq!(parse_version("bash: java: not found"), None);
    }

    #[test]
    fn strips_the_top_folder_safely() {
        assert_eq!(strip_first(Path::new("jdk8u504-b01-jre/bin/java")), Some(PathBuf::from("bin/java")));
        assert_eq!(strip_first(Path::new("jdk/../../evil")), None);
        assert_eq!(strip_first(Path::new("/abs/bin")), None);
        assert_eq!(strip_first(Path::new("top")), None);
    }
}
