//! Ready-made graphics settings, written into the game's own options.txt.

use std::fs;
use std::path::Path;

use anyhow::{bail, Context, Result};

/// A set of Minecraft 1.8.9 video settings. The player can change each one in game afterwards.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Preset {
    /// the most frames: short view, no fancy effects
    Performance,
    /// a middle ground
    Balanced,
    /// everything on, the view distance a bit longer
    Quality,
}

impl Preset {
    pub fn parse(name: &str) -> Result<Preset> {
        Ok(match name {
            "performance" => Preset::Performance,
            "balanced" => Preset::Balanced,
            "quality" => Preset::Quality,
            _ => bail!("préréglage inconnu"),
        })
    }

    /// The options.txt keys this preset sets. VBOs and the framebuffer stay on and VSync off in
    /// every preset: the frame cap is the game's own setting (260 means no cap).
    pub fn values(self) -> &'static [(&'static str, &'static str)] {
        match self {
            Preset::Performance => &[
                ("renderDistance", "8"),
                ("fancyGraphics", "false"),
                ("ao", "0"),
                ("renderClouds", "false"),
                ("particles", "1"),
                ("mipmapLevels", "0"),
                ("entityShadows", "false"),
                ("useVbo", "true"),
                ("fboEnable", "true"),
                ("enableVsync", "false"),
                ("maxFps", "260"),
            ],
            Preset::Balanced => &[
                ("renderDistance", "10"),
                ("fancyGraphics", "false"),
                ("ao", "1"),
                ("renderClouds", "fast"),
                ("particles", "1"),
                ("mipmapLevels", "2"),
                ("entityShadows", "true"),
                ("useVbo", "true"),
                ("fboEnable", "true"),
                ("enableVsync", "false"),
                ("maxFps", "260"),
            ],
            Preset::Quality => &[
                ("renderDistance", "12"),
                ("fancyGraphics", "true"),
                ("ao", "2"),
                ("renderClouds", "true"),
                ("particles", "0"),
                ("mipmapLevels", "4"),
                ("entityShadows", "true"),
                ("useVbo", "true"),
                ("fboEnable", "true"),
                ("enableVsync", "false"),
                ("maxFps", "260"),
            ],
        }
    }
}

/// `options.txt` with the preset's keys replaced (added when missing); every other line is kept.
pub fn with_preset(options: &str, preset: Preset) -> String {
    let values = preset.values();
    let mut seen = vec![false; values.len()];
    let mut out = String::with_capacity(options.len() + 256);
    for line in options.lines() {
        let key = line.split_once(':').map(|(k, _)| k);
        match key.and_then(|k| values.iter().position(|(v, _)| *v == k)) {
            Some(i) => {
                seen[i] = true;
                out.push_str(values[i].0);
                out.push(':');
                out.push_str(values[i].1);
            }
            None => out.push_str(line),
        }
        out.push('\n');
    }
    for (i, (k, v)) in values.iter().enumerate() {
        if !seen[i] {
            out.push_str(k);
            out.push(':');
            out.push_str(v);
            out.push('\n');
        }
    }
    out
}

/// Writes the preset into `<game>/options.txt` (through a temporary file). The game must be closed:
/// it writes the file again when it quits.
pub fn apply(game: &Path, preset: Preset) -> Result<()> {
    let path = game.join("options.txt");
    let current = match fs::read_to_string(&path) {
        Ok(s) => s,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => String::new(),
        Err(e) => return Err(e).context("options.txt"),
    };
    fs::create_dir_all(game)?;
    let tmp = path.with_extension("txt.tmp");
    fs::write(&tmp, with_preset(&current, preset))?;
    fs::rename(&tmp, &path).context("options.txt")?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn replaces_and_adds_keys_and_keeps_the_rest() {
        let before = "invertYMouse:false\nrenderDistance:16\nfov:0.25\nkey_key.attack:-100\n";
        let after = with_preset(before, Preset::Performance);
        assert!(after.starts_with("invertYMouse:false\nrenderDistance:8\nfov:0.25\nkey_key.attack:-100\n"));
        assert!(after.contains("\nmaxFps:260\n"));
        assert_eq!(after.matches("renderDistance").count(), 1);
    }

    #[test]
    fn writes_a_new_file() {
        let dir = tempfile::tempdir().unwrap();
        apply(dir.path(), Preset::Quality).unwrap();
        let text = fs::read_to_string(dir.path().join("options.txt")).unwrap();
        assert!(text.contains("fancyGraphics:true\n"));
        apply(dir.path(), Preset::Balanced).unwrap();
        let text = fs::read_to_string(dir.path().join("options.txt")).unwrap();
        assert!(text.contains("fancyGraphics:false\n") && !text.contains("fancyGraphics:true"));
        assert!(Preset::parse("nope").is_err());
    }
}
