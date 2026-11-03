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
    pub extra_jvm: Vec<String>,
}

impl Default for Options {
    fn default() -> Options {
        Options { memory_mb: 2048, extra_jvm: Vec::new() }
    }
}

/// G1 settings the official launcher uses for Java 8: short pauses, fewer stutters.
const G1: [&str; 6] = [
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+UseG1GC",
    "-XX:G1NewSizePercent=20",
    "-XX:G1ReservePercent=20",
    "-XX:MaxGCPauseMillis=50",
    "-XX:G1HeapRegionSize=32M",
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
    args.extend(G1.iter().map(|s| s.to_string()));
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

pub fn game_arguments(inst: &Installed, account: &Account) -> Vec<String> {
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
    substitute(&inst.minecraft_arguments, &vars)
}

/// The game's process, ready to spawn, in the game folder.
pub fn command(inst: &Installed, account: &Account, opts: &Options) -> Command {
    let mut cmd = Command::new(&inst.java);
    cmd.args(jvm_arguments(inst, opts, Os::current()));
    cmd.arg(&inst.main_class);
    cmd.args(game_arguments(inst, account));
    cmd.current_dir(&inst.game);
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
        let args = game_arguments(&installed(), &account);
        assert_eq!(
            args,
            ["--username", "Steve", "--assetIndex", "1.8", "--accessToken", "tok", "--userType", "msa", "--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker"]
        );
        assert!(!format!("{account:?}").contains("tok"));
    }

    #[test]
    fn jvm_arguments_end_with_the_class_path() {
        let args = jvm_arguments(&installed(), &Options { memory_mb: 3072, extra_jvm: vec![] }, Os::Linux);
        assert!(args.contains(&"-Xmx3072M".to_string()));
        assert!(args.contains(&"-Djava.library.path=natives".to_string()));
        assert!(args.contains(&"-Dlog4j.configurationFile=client-1.7.xml".to_string()));
        assert_eq!(&args[args.len() - 2..], ["-cp", "a.jar:b.jar"]);
    }
}
