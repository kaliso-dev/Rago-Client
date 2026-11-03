//! Forge 1.8.9, pinned: the libraries its installer lists for the client, each with the SHA-1 and
//! size it had when `data/forge-1.8.9.json` was made, so a changed file on a mirror is refused.

use std::path::Path;

use serde::Deserialize;

use crate::http::{Download, Hash};
use crate::mojang::library_key;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Forge {
    pub id: String,
    pub inherits_from: String,
    pub main_class: String,
    pub minecraft_arguments: String,
    pub libraries: Vec<LockedLibrary>,
}

#[derive(Debug, Deserialize)]
pub struct LockedLibrary {
    pub name: String,
    pub path: String,
    pub url: String,
    pub sha1: String,
    pub size: u64,
}

pub fn forge() -> Forge {
    serde_json::from_str(include_str!("../data/forge-1.8.9.json")).expect("data/forge-1.8.9.json")
}

impl Forge {
    pub fn downloads(&self, libraries: &Path) -> Vec<Download> {
        self.libraries
            .iter()
            .map(|l| Download {
                url: l.url.clone(),
                path: libraries.join(&l.path),
                hash: Hash::Sha1(l.sha1.clone()),
                size: Some(l.size),
            })
            .collect()
    }

    /// (group:artifact, jar) in Forge's order: launchwrapper and Forge before Minecraft's own.
    pub fn classpath(&self, libraries: &Path) -> Vec<(String, std::path::PathBuf)> {
        self.libraries.iter().map(|l| (library_key(&l.name), libraries.join(&l.path))).collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lock_is_complete() {
        let f = forge();
        assert_eq!(f.inherits_from, "1.8.9");
        assert_eq!(f.main_class, "net.minecraft.launchwrapper.Launch");
        assert!(f.minecraft_arguments.contains("FMLTweaker"));
        assert!(f.libraries.iter().any(|l| l.name.starts_with("net.minecraftforge:forge:")));
        assert!(f.libraries.iter().any(|l| l.name.starts_with("net.minecraft:launchwrapper:")));
        for l in &f.libraries {
            assert_eq!(l.sha1.len(), 40, "{}", l.name);
            assert!(l.url.starts_with("https://"), "{}", l.name);
            assert!(!l.path.contains(".."), "{}", l.name);
        }
    }
}
