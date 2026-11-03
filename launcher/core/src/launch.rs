//! The command line that starts Minecraft with Forge and Rago.

use std::collections::HashMap;
use std::process::Command;

use crate::install::Installed;
use crate::os::Os;

/// The player, as Minecraft services gave them after signing in.
#[derive(Clone)]
pub struct Account {
    pub name: String,
    /// the profile id, without dashes
    pub uuid: String,
    pub access_token: String,
}

impl std::fmt::Debug for Account {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        // the token never goes to logs
        f.debug_struct("Account").field("name", &self.name).field("uuid", &self.uuid).finish_non_exhaustive()
    }
}

#[derive(Clone, Debug)]
pub struct Options {
    pub memory_mb: u32,
    /// the tuned garbage collector and startup flags below; off leaves Java's defaults
    pub tuned_jvm: bool,
    /// the player's own JVM arguments, after ours (so theirs win)
    pub extra_jvm: Vec<String>,
    /// the game window's size at startup; None keeps the game's default (854x480)
    pub size: Option<(u32, u32)>,
    pub fullscreen: bool,
    /// Windows' "above normal" priority for the game
    pub high_priority: bool,
    /// a server to join as soon as the game is up
    pub server: Option<(String, u16)>,
}

impl Default for Options {
    fn default() -> Options {
        Options { memory_mb: 2048, tuned_jvm: true, extra_jvm: Vec::new(), size: None, fullscreen: false, high_priority: false, server: None }
    }
}

/// Splits the player's JVM arguments, refusing the ones that would break the launch.
pub fn parse_jvm_arguments(text: &str) -> Result<Vec<String>, String> {
    let args: Vec<String> = text.split_whitespace().map(str::to_string).collect();
    for a in &args {
        if ["-cp", "-classpath", "--class-path", "-jar"].contains(&a.as_str()) || a.starts_with("-Djava.library.path=") {
            return Err(format!("{a} est géré par le launcher."));
        }
        if !a.starts_with('-') {
            return Err(format!("« {a} » n'est pas un argument de Java (il doit commencer par -)."));
        }
    }
    Ok(args)
}

/// G1 settings the official launcher uses for Java 8 (short pauses, fewer stutters), plus:
/// references processed in parallel (shorter pauses), no full collection when a mod calls
/// System.gc(), and Forge's start without checking the vanilla jar's signature again.
const TUNED: [&str; 10] = [
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+UseG1GC",
    "-XX:G1NewSizePercent=20",
    "-XX:G1ReservePercent=20",
    "-XX:MaxGCPauseMillis=50",
    "-XX:G1HeapRegionSize=32M",
    "-XX:+ParallelRefProcEnabled",
    "-XX:+DisableExplicitGC",
    "-Dfml.ignoreInvalidMinecraftCertificates=true",
    "-Dfml.ignorePatchDiscrepancies=true",
];

/// Replaces each `${name}` token of the version's argument line.
pub fn substitute(template: &str, vars: &HashMap<&str, String>) -> Vec<String> {
    template
        .split_whitespace()
        .map(|arg| {
            let mut out = arg.to_string();
            for (k, v) in vars {
                out = out.replace(&format!("${{{k}}}"), v);
            }
            out
        })
        .collect()
}

pub fn jvm_arguments(inst: &Installed, opts: &Options, os: Os) -> Vec<String> {
    let memory = opts.memory_mb.max(512);
    let mut args = vec![format!("-Xms{}M", memory.min(1024)), format!("-Xmx{memory}M")];
    if opts.tuned_jvm {
        args.extend(TUNED.iter().map(|s| s.to_string()));
    }
    args.push(format!("-Djava.library.path={}", inst.natives.display()));
    args.push("-Dminecraft.launcher.brand=rago-launcher".into());
    args.push(format!("-Dminecraft.launcher.version={}", env!("CARGO_PKG_VERSION")));
    if let Some(log) = &inst.log_argument {
        args.push(log.clone());
    }
    args.extend(opts.extra_jvm.iter().cloned());
    let classpath: Vec<String> = inst.classpath.iter().map(|p| p.to_string_lossy().into_owned()).collect();
    args.push("-cp".into());
    args.push(classpath.join(os.classpath_separator()));
    args
}

pub fn game_arguments(inst: &Installed, account: &Account, opts: &Options) -> Vec<String> {
    let vars: HashMap<&str, String> = [
        ("auth_player_name", account.name.clone()),
        ("version_name", inst.version_name.clone()),
        ("game_directory", inst.game.to_string_lossy().into_owned()),
        ("assets_root", inst.assets.to_string_lossy().into_owned()),
        ("assets_index_name", inst.asset_index.clone()),
        ("auth_uuid", account.uuid.clone()),
        ("auth_access_token", account.access_token.clone()),
        ("user_properties", "{}".into()),
        ("user_type", "msa".into()),
    ]
    .into_iter()
    .collect();
    let mut args = substitute(&inst.minecraft_arguments, &vars);
    if let Some((w, h)) = opts.size {
        args.extend(["--width".into(), w.to_string(), "--height".into(), h.to_string()]);
    }
    if opts.fullscreen {
        args.push("--fullscreen".into());
    }
    if let Some((host, port)) = &opts.server {
        args.extend(["--server".into(), host.clone(), "--port".into(), port.to_string()]);
    }
    args
}

/// The game's process, ready to spawn, in the game folder.
pub fn command(inst: &Installed, account: &Account, opts: &Options) -> Command {
    let mut cmd = Command::new(&inst.java);
    cmd.args(jvm_arguments(inst, opts, Os::current()));
    cmd.arg(&inst.main_class);
    cmd.args(game_arguments(inst, account, opts));
    cmd.current_dir(&inst.game);
    if opts.high_priority {
        crate::system::above_normal_priority(&mut cmd);
    }
    cmd
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    fn installed() -> Installed {
        Installed {
            java: PathBuf::from("java"),
            classpath: vec![PathBuf::from("a.jar"), PathBuf::from("b.jar")],
            natives: PathBuf::from("natives"),
            assets: PathBuf::from("assets"),
            asset_index: "1.8".into(),
            game: PathBuf::from("game"),
            main_class: "net.minecraft.launchwrapper.Launch".into(),
            minecraft_arguments: "--username ${auth_player_name} --assetIndex ${assets_index_name} --accessToken ${auth_access_token} --userType ${user_type} --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker".into(),
            version_name: "1.8.9-forge".into(),
            log_argument: Some("-Dlog4j.configurationFile=client-1.7.xml".into()),
            rago: None,
        }
    }

    #[test]
    fn fills_the_game_arguments() {
        let account = Account { name: "Steve".into(), uuid: "0".repeat(32), access_token: "tok".into() };
        let args = game_arguments(&installed(), &account, &Options::default());
        assert_eq!(
            args,
            ["--username", "Steve", "--assetIndex", "1.8", "--accessToken", "tok", "--userType", "msa", "--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker"]
        );
        assert!(!format!("{account:?}").contains("tok"));
        let opts = Options { size: Some((1280, 720)), fullscreen: true, ..Options::default() };
        let args = game_arguments(&installed(), &account, &opts);
        assert_eq!(&args[args.len() - 5..], ["--width", "1280", "--height", "720", "--fullscreen"]);
    }

    #[test]
    fn checks_the_players_jvm_arguments() {
        assert_eq!(parse_jvm_arguments(" -XX:+UseStringDeduplication  -Dfoo=1 ").unwrap(), ["-XX:+UseStringDeduplication", "-Dfoo=1"]);
        assert!(parse_jvm_arguments("-cp x.jar").is_err());
        assert!(parse_jvm_arguments("Xmx4G").is_err());
        assert!(parse_jvm_arguments("").unwrap().is_empty());
    }

    #[test]
    fn jvm_arguments_end_with_the_class_path() {
        let args = jvm_arguments(&installed(), &Options { memory_mb: 3072, ..Options::default() }, Os::Linux);
        assert!(args.contains(&"-Xmx3072M".to_string()));
        assert!(args.contains(&"-XX:+UseG1GC".to_string()));
        let plain = jvm_arguments(&installed(), &Options { tuned_jvm: false, extra_jvm: vec!["-Dx=1".into()], ..Options::default() }, Os::Linux);
        assert!(!plain.contains(&"-XX:+UseG1GC".to_string()));
        assert_eq!(plain[plain.len() - 3], "-Dx=1");
        assert!(args.contains(&"-Djava.library.path=natives".to_string()));
        assert!(args.contains(&"-Dlog4j.configurationFile=client-1.7.xml".to_string()));
        assert_eq!(&args[args.len() - 2..], ["-cp", "a.jar:b.jar"]);
    }
}
