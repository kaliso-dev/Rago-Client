//! Rago's own loader (loader/ in this repository): Minecraft 1.8.9 and Rago on Java 21, without
//! Forge. The launcher carries the loader's jar; before the first start the loader renames the
//! game once with Forge's names (`remap`), into `versions/1.8.9/rago/`.

use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

use anyhow::{bail, Context, Result};
use reqwest::blocking::Client;
use sha1::{Digest, Sha1};

use crate::install::{Installed, Layout};
use crate::java;
use crate::os::Os;

/// The Java the loader runs the game on.
pub const JAVA: u8 = 21;
pub const MAIN_CLASS: &str = "gg.rago.loader.Main";

static JAR: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/rago-loader.jar"));

/// False in a launcher built without the loader.
pub fn available() -> bool {
    !JAR.is_empty()
}

/// Windows only for now. On Linux, LWJGL 2's native library asks Java for a symbol version
/// (`SUNWprivate_1.1`) that Temurin's Java 21 no longer has; on macOS, LWJGL 2 cannot open its
/// window on a Java newer than 8.
pub fn supported(os: Os) -> bool {
    os == Os::Windows
}

fn id() -> String {
    hex::encode(&Sha1::digest(JAR)[..6])
}

/// Java 21, the loader jar and the renamed game, then what to start instead of Forge.
pub fn prepare(client: &Client, layout: &Layout, inst: &Installed) -> Result<Installed> {
    if !available() {
        bail!("Cette version du launcher n'a pas le chargeur Rago.");
    }
    if !supported(Os::current()) {
        bail!("Le chargeur Rago ne marche que sur Windows pour l'instant.");
    }
    let java = java::ensure(client, &layout.runtime().join(format!("java-{JAVA}")), Os::current(), JAVA)?;
    prepare_with(layout, inst, java)
}

/// [`prepare`] with a Java 21 already there.
pub fn prepare_with(layout: &Layout, inst: &Installed, java: PathBuf) -> Result<Installed> {
    let rago = inst.rago.clone().context("Rago n'est pas installé")?;
    let dir = layout.version_dir().join("rago");
    fs::create_dir_all(&dir)?;
    let id = id();
    let jar = dir.join(format!("rago-loader-{id}.jar"));
    if fs::read(&jar).map(|d| d != JAR).unwrap_or(true) {
        let tmp = dir.join("rago-loader.tmp");
        fs::write(&tmp, JAR)?;
        fs::rename(&tmp, &jar)?;
    }
    // each loader renames the game its own way: a new loader renames it again
    let renamed = dir.join(format!("{}-{id}.jar", crate::mojang::VERSION_ID));
    if !renamed.is_file() {
        rename_game(&java, &jar, inst, &renamed)?;
    }
    remove_others(&dir, &[&jar, &renamed]);
    Ok(arguments(inst, java, jar, renamed, rago))
}

fn rename_game(java: &Path, jar: &Path, inst: &Installed, out: &Path) -> Result<()> {
    let tmp = out.with_extension("tmp");
    let mut cmd = Command::new(java);
    cmd.arg("-cp").arg(jar).arg(MAIN_CLASS).arg("remap").arg(&inst.vanilla.forge_jar).arg(&inst.vanilla.client_jar).arg(&tmp);
    crate::system::hide_window(&mut cmd);
    let result = cmd.output().with_context(|| format!("{} ne démarre pas", java.display()))?;
    if !result.status.success() || !tmp.is_file() {
        let _ = fs::remove_file(&tmp);
        bail!("le chargeur Rago n'a pas pu préparer le jeu : {}", String::from_utf8_lossy(&result.stderr).trim());
    }
    fs::rename(&tmp, out)?;
    Ok(())
}

/// Older loaders and the games they renamed.
fn remove_others(dir: &Path, keep: &[&Path]) {
    let Ok(entries) = fs::read_dir(dir) else { return };
    for e in entries.flatten() {
        let p = e.path();
        if p.extension().is_some_and(|x| x == "jar") && !keep.contains(&p.as_path()) {
            let _ = fs::remove_file(&p);
        }
    }
}

/// The launch of `inst` moved to the loader: Java 21, the loader and Minecraft's own libraries on
/// the class path, and the loader's arguments before Minecraft's.
pub fn arguments(inst: &Installed, java: PathBuf, jar: PathBuf, renamed: PathBuf, rago: PathBuf) -> Installed {
    let mut out = inst.clone();
    out.java = java;
    out.classpath = std::iter::once(jar).chain(inst.vanilla.classpath.iter().cloned()).collect();
    out.main_class = MAIN_CLASS.into();
    // paths go through ${...} so a space in them never splits an argument
    out.minecraft_arguments = format!("launch --game ${{rago_game}} --mod ${{rago_mod}} -- {}", inst.vanilla.arguments);
    out.vars = vec![
        ("rago_game".into(), renamed.to_string_lossy().into_owned()),
        ("rago_mod".into(), rago.to_string_lossy().into_owned()),
    ];
    out.version_name = format!("{}-rago", crate::mojang::VERSION_ID);
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::install::Vanilla;
    use crate::launch::{game_arguments, Account, Options};

    #[test]
    fn starts_the_loader_instead_of_forge() {
        let inst = Installed {
            java: PathBuf::from("java8"),
            classpath: vec![PathBuf::from("forge.jar"), PathBuf::from("lwjgl.jar"), PathBuf::from("1.8.9.jar")],
            natives: PathBuf::from("natives"),
            assets: PathBuf::from("assets"),
            asset_index: "1.8".into(),
            game: PathBuf::from("game"),
            main_class: "net.minecraft.launchwrapper.Launch".into(),
            minecraft_arguments: "--username ${auth_player_name} --tweakClass x".into(),
            version_name: "1.8.9-forge".into(),
            log_argument: None,
            rago: None,
            vars: Vec::new(),
            vanilla: Vanilla {
                classpath: vec![PathBuf::from("lwjgl.jar")],
                client_jar: PathBuf::from("1.8.9.jar"),
                arguments: "--username ${auth_player_name} --version ${version_name}".into(),
                forge_jar: PathBuf::from("forge.jar"),
            },
        };
        let out = arguments(&inst, "java21".into(), "loader.jar".into(), "C:\\My Games\\1.8.9-x.jar".into(), "mods\\rago 1.jar".into());
        assert_eq!(out.java, PathBuf::from("java21"));
        assert_eq!(out.classpath, [PathBuf::from("loader.jar"), PathBuf::from("lwjgl.jar")]);
        assert_eq!(out.main_class, MAIN_CLASS);
        let account = Account { name: "Steve".into(), uuid: "0".repeat(32), access_token: "t".into() };
        let args = game_arguments(&out, &account, &Options::default());
        assert_eq!(
            args,
            ["launch", "--game", "C:\\My Games\\1.8.9-x.jar", "--mod", "mods\\rago 1.jar", "--", "--username", "Steve", "--version", "1.8.9-rago"]
        );
    }
}
