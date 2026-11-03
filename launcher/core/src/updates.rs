//! A newer launcher, from this repository's `launcher-v*` releases.

use anyhow::Result;
use reqwest::blocking::Client;
use serde::Deserialize;

pub const RELEASES: &str = "https://api.github.com/repos/kaliso-dev/Rago-Client/releases?per_page=30";

#[derive(Debug, Deserialize)]
struct Release {
    tag_name: String,
    draft: bool,
    prerelease: bool,
    html_url: String,
    assets: Vec<Asset>,
}

#[derive(Debug, Deserialize)]
struct Asset {
    name: String,
    size: u64,
    browser_download_url: String,
    digest: Option<String>,
}

/// A launcher installer newer than this one.
#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Update {
    pub version: String,
    pub page: String,
    pub file: String,
    pub url: String,
    pub sha256: String,
    pub size: u64,
}

/// "1.2.3" as numbers, to compare versions.
pub fn parse(version: &str) -> Option<Vec<u64>> {
    version.split('.').map(|p| p.parse().ok()).collect()
}

pub fn newer(candidate: &str, current: &str) -> bool {
    match (parse(candidate), parse(current)) {
        (Some(a), Some(b)) => a > b,
        _ => false,
    }
}

/// The newest launcher release, if it is newer than `current` and has a checked installer.
pub fn check(client: &Client, current: &str) -> Result<Option<Update>> {
    let releases: Vec<Release> = client.get(RELEASES).header("Accept", "application/vnd.github+json").send()?.error_for_status()?.json()?;
    let mut best: Option<Update> = None;
    for r in releases {
        let Some(version) = r.tag_name.strip_prefix("launcher-v") else {
            continue;
        };
        if r.draft || r.prerelease || !newer(version, current) || best.as_ref().is_some_and(|b| !newer(version, &b.version)) {
            continue;
        }
        let installer = r.assets.into_iter().find(|a| {
            a.name.ends_with("-setup.exe") && !a.name.contains(['/', '\\']) && a.digest.as_deref().is_some_and(|d| d.starts_with("sha256:"))
        });
        if let Some(a) = installer {
            best = Some(Update {
                version: version.to_string(),
                page: r.html_url,
                sha256: a.digest.unwrap()["sha256:".len()..].to_string(),
                file: a.name,
                url: a.browser_download_url,
                size: a.size,
            });
        }
    }
    Ok(best)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn compares_versions() {
        assert!(newer("0.2.0", "0.1.9"));
        assert!(newer("0.10.0", "0.9.0"));
        assert!(!newer("0.1.0", "0.1.0"));
        assert!(!newer("abc", "0.1.0"));
    }
}
