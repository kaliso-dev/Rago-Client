//! Minecraft 1.8.9 as Mojang publishes it: the version file, its libraries and natives, the
//! sounds and textures (assets) and the logging configuration.

use std::collections::HashMap;
use std::path::{Path, PathBuf};

use serde::Deserialize;

use crate::http::{Download, Hash};
use crate::os::{arch_bits, Os};

/// Mojang's file for 1.8.9, as listed in https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
pub const VERSION_ID: &str = "1.8.9";
pub const VERSION_URL: &str = "https://piston-meta.mojang.com/v1/packages/d546f1707a3f2b7d034eece5ea2e311eda875787/1.8.9.json";
pub const VERSION_SHA1: &str = "d546f1707a3f2b7d034eece5ea2e311eda875787";
pub const RESOURCES_URL: &str = "https://resources.download.minecraft.net";

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Version {
    pub id: String,
    pub asset_index: AssetIndexRef,
    pub downloads: VersionDownloads,
    pub libraries: Vec<Library>,
    pub main_class: String,
    pub minecraft_arguments: String,
    pub logging: Option<Logging>,
}

#[derive(Debug, Deserialize)]
pub struct AssetIndexRef {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Deserialize)]
pub struct VersionDownloads {
    pub client: Artifact,
}

#[derive(Clone, Debug, Deserialize)]
pub struct Artifact {
    pub path: Option<String>,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Deserialize)]
pub struct Library {
    pub name: String,
    pub downloads: Option<LibraryDownloads>,
    pub natives: Option<HashMap<String, String>>,
    pub rules: Option<Vec<Rule>>,
    pub extract: Option<Extract>,
}

#[derive(Debug, Deserialize)]
pub struct LibraryDownloads {
    pub artifact: Option<Artifact>,
    pub classifiers: Option<HashMap<String, Artifact>>,
}

#[derive(Debug, Deserialize)]
pub struct Rule {
    pub action: String,
    pub os: Option<OsRule>,
}

#[derive(Debug, Deserialize)]
pub struct OsRule {
    pub name: Option<String>,
}

#[derive(Clone, Debug, Default, Deserialize)]
pub struct Extract {
    #[serde(default)]
    pub exclude: Vec<String>,
}

#[derive(Debug, Deserialize)]
pub struct Logging {
    pub client: LoggingClient,
}

#[derive(Debug, Deserialize)]
pub struct LoggingClient {
    pub argument: String,
    pub file: LogFile,
}

#[derive(Debug, Deserialize)]
pub struct LogFile {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Deserialize)]
pub struct AssetIndex {
    pub objects: HashMap<String, AssetObject>,
}

#[derive(Debug, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}

/// Whether a library's rules let it in on this system (no rules: always).
pub fn allowed(rules: Option<&[Rule]>, os: Os) -> bool {
    let Some(rules) = rules else {
        return true;
    };
    let mut allow = false;
    for rule in rules {
        let applies = match &rule.os {
            None => true,
            Some(r) => r.name.as_deref().map_or(true, |n| n == os.mojang()),
        };
        if applies {
            allow = rule.action == "allow";
        }
    }
    allow
}

/// A jar with native code to unpack before launching.
#[derive(Clone, Debug)]
pub struct Native {
    pub jar: PathBuf,
    pub exclude: Vec<String>,
}

/// What 1.8.9 needs on disk: the downloads, then the class path and natives they make.
#[derive(Debug, Default)]
pub struct Resolved {
    pub downloads: Vec<Download>,
    /// (group:artifact, jar) in Mojang's order
    pub classpath: Vec<(String, PathBuf)>,
    pub natives: Vec<Native>,
}

fn artifact_download(a: &Artifact, path: PathBuf) -> Download {
    Download { url: a.url.clone(), path, hash: Hash::Sha1(a.sha1.clone()), size: Some(a.size) }
}

/// "group:artifact" of a "group:artifact:version" name, to tell the same library apart from its version.
pub fn library_key(name: &str) -> String {
    let mut parts = name.split(':');
    format!("{}:{}", parts.next().unwrap_or(""), parts.next().unwrap_or(""))
}

/// The libraries and natives of `version` for `os`, under `libraries`.
pub fn resolve_libraries(version: &Version, os: Os, libraries: &Path) -> Resolved {
    let mut out = Resolved::default();
    for lib in &version.libraries {
        if !allowed(lib.rules.as_deref(), os) {
            continue;
        }
        let Some(downloads) = &lib.downloads else {
            continue;
        };
        match &lib.natives {
            None => {
                if let Some(a) = &downloads.artifact {
                    if let Some(p) = &a.path {
                        let path = libraries.join(p);
                        out.downloads.push(artifact_download(a, path.clone()));
                        out.classpath.push((library_key(&lib.name), path));
                    }
                }
            }
            Some(natives) => {
                let Some(classifier) = natives.get(os.mojang()) else {
                    continue;
                };
                let classifier = classifier.replace("${arch}", arch_bits());
                let Some(a) = downloads.classifiers.as_ref().and_then(|c| c.get(&classifier)) else {
                    continue;
                };
                let Some(p) = &a.path else {
                    continue;
                };
                let path = libraries.join(p);
                out.downloads.push(artifact_download(a, path.clone()));
                out.natives.push(Native { jar: path, exclude: lib.extract.clone().unwrap_or_default().exclude });
            }
        }
    }
    out
}

/// Every sound and texture of the index, under `assets/objects`.
pub fn asset_downloads(index: &AssetIndex, assets: &Path) -> Vec<Download> {
    let mut seen = std::collections::HashSet::new();
    let mut out = Vec::new();
    for object in index.objects.values() {
        if object.hash.len() < 2 || !seen.insert(object.hash.clone()) {
            continue;
        }
        let prefix = &object.hash[..2];
        out.push(Download {
            url: format!("{RESOURCES_URL}/{prefix}/{}", object.hash),
            path: assets.join("objects").join(prefix).join(&object.hash),
            hash: Hash::Sha1(object.hash.clone()),
            size: Some(object.size),
        });
    }
    out.sort_by(|a, b| b.size.cmp(&a.size));
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rules(json: &str) -> Vec<Rule> {
        serde_json::from_str(json).unwrap()
    }

    #[test]
    fn library_rules() {
        let not_mac = rules(r#"[{"action":"allow"},{"action":"disallow","os":{"name":"osx"}}]"#);
        assert!(allowed(Some(&not_mac), Os::Windows));
        assert!(!allowed(Some(&not_mac), Os::Osx));
        let only_mac = rules(r#"[{"action":"allow","os":{"name":"osx"}}]"#);
        assert!(!allowed(Some(&only_mac), Os::Linux));
        assert!(allowed(Some(&only_mac), Os::Osx));
        assert!(allowed(None, Os::Linux));
    }

    #[test]
    fn resolves_natives_and_class_path() {
        let version: Version = serde_json::from_str(
            r#"{"id":"1.8.9","assetIndex":{"id":"1.8","sha1":"a","size":1,"url":"u"},
            "downloads":{"client":{"sha1":"b","size":2,"url":"c"}},"mainClass":"m","minecraftArguments":"",
            "libraries":[
              {"name":"a:b:1","downloads":{"artifact":{"path":"a/b/1/b-1.jar","sha1":"s","size":3,"url":"u1"}}},
              {"name":"t:p:6","downloads":{"classifiers":{"natives-windows-64":{"path":"t/p-w64.jar","sha1":"s","size":4,"url":"u2"}}},
               "natives":{"windows":"natives-windows-${arch}"},"extract":{"exclude":["META-INF/"]}},
              {"name":"m:only:1","rules":[{"action":"allow","os":{"name":"osx"}}],
               "downloads":{"artifact":{"path":"m/only.jar","sha1":"s","size":5,"url":"u3"}}}
            ]}"#,
        )
        .unwrap();
        let r = resolve_libraries(&version, Os::Windows, Path::new("libs"));
        assert_eq!(r.classpath, vec![("a:b".to_string(), Path::new("libs").join("a/b/1/b-1.jar"))]);
        if arch_bits() == "64" {
            assert_eq!(r.natives.len(), 1);
            assert_eq!(r.natives[0].exclude, vec!["META-INF/"]);
        }
        assert_eq!(r.downloads.len(), 1 + r.natives.len());
    }
}
