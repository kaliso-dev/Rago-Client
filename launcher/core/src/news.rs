//! Rago's news: the notes of its newest releases on GitHub.

use anyhow::Result;
use reqwest::blocking::Client;
use serde::{Deserialize, Serialize};

use crate::updates::RELEASES;

#[derive(Debug, Deserialize)]
struct Release {
    tag_name: String,
    name: Option<String>,
    draft: bool,
    prerelease: bool,
    html_url: String,
    published_at: Option<String>,
    body: Option<String>,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct News {
    pub title: String,
    /// RFC 3339
    pub date: String,
    /// the first lines of the notes, without Markdown
    pub summary: String,
    pub url: String,
}

/// The newest Rago releases (not the launcher's), newest first.
pub fn latest(client: &Client, count: usize) -> Result<Vec<News>> {
    let releases: Vec<Release> = client.get(RELEASES).header("Accept", "application/vnd.github+json").send()?.error_for_status()?.json()?;
    Ok(releases
        .into_iter()
        .filter(|r| !r.draft && !r.prerelease && !r.tag_name.starts_with("launcher-"))
        .filter(|r| r.html_url.starts_with("https://github.com/kaliso-dev/Rago-Client/releases/"))
        .take(count)
        .map(|r| News {
            title: r.name.filter(|n| !n.trim().is_empty()).unwrap_or_else(|| r.tag_name.clone()),
            date: r.published_at.unwrap_or_default(),
            summary: summary(r.body.as_deref().unwrap_or(""), 180),
            url: r.html_url,
        })
        .collect())
}

/// The first plain sentences of release notes: no headings, bullets, links or emphasis.
pub fn summary(markdown: &str, max: usize) -> String {
    let mut out = String::new();
    for line in markdown.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') || line.starts_with("```") || line.starts_with('|') || line.starts_with("<!--") {
            continue;
        }
        let line = line.trim_start_matches(['-', '*', '+', '>', ' ']);
        let mut clean = String::new();
        let mut chars = line.chars().peekable();
        while let Some(c) = chars.next() {
            match c {
                '*' | '_' | '`' => {}
                '[' => {
                    // [text](url) keeps its text
                    let text: String = chars.by_ref().take_while(|&c| c != ']').collect();
                    clean.push_str(&text);
                    if chars.peek() == Some(&'(') {
                        for c in chars.by_ref() {
                            if c == ')' {
                                break;
                            }
                        }
                    }
                }
                c => clean.push(c),
            }
        }
        if !out.is_empty() {
            out.push(' ');
        }
        out.push_str(clean.trim());
        if out.chars().count() >= max {
            break;
        }
    }
    if out.chars().count() > max {
        let cut: String = out.chars().take(max).collect();
        let cut = cut.rsplit_once(' ').map(|(a, _)| a).unwrap_or(&cut);
        return format!("{}…", cut.trim_end_matches([',', '.', ';', ':']));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn summarizes_release_notes() {
        let md = "## Nouveau\n\n- **Hitboxes** plus fines et [Zoom](https://x.y) réglable.\n- `FPS` en hausse.\n";
        assert_eq!(summary(md, 200), "Hitboxes plus fines et Zoom réglable. FPS en hausse.");
        let long = summary("un deux trois quatre cinq six", 12);
        assert_eq!(long, "un deux…");
    }
}
