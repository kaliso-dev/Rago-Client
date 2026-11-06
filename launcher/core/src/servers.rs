//! The servers saved in the game's multiplayer list (`servers.dat`, an uncompressed NBT file).

use std::path::Path;

use anyhow::{bail, Result};
use serde::Serialize;

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Server {
    pub name: String,
    pub address: String,
    /// the icon the game saved for it, as a data: URL
    pub icon: Option<String>,
}

/// The servers in `<game>/servers.dat`, in the player's order; none when the file is missing.
pub fn saved(game: &Path) -> Result<Vec<Server>> {
    match std::fs::read(game.join("servers.dat")) {
        Ok(data) => parse(&data),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(Vec::new()),
        Err(e) => Err(e.into()),
    }
}

struct Reader<'a> {
    data: &'a [u8],
    depth: u32,
}

#[derive(Debug)]
enum Tag {
    Str(String),
    List(Vec<Tag>),
    Compound(Vec<(String, Tag)>),
    Other,
}

impl Reader<'_> {
    fn take(&mut self, n: usize) -> Result<&[u8]> {
        if n > self.data.len() {
            bail!("servers.dat is truncated");
        }
        let (a, b) = self.data.split_at(n);
        self.data = b;
        Ok(a)
    }
    fn u8(&mut self) -> Result<u8> {
        Ok(self.take(1)?[0])
    }
    fn u16(&mut self) -> Result<u16> {
        Ok(u16::from_be_bytes(self.take(2)?.try_into()?))
    }
    fn i32(&mut self) -> Result<i32> {
        Ok(i32::from_be_bytes(self.take(4)?.try_into()?))
    }
    fn len(&mut self) -> Result<usize> {
        let n = self.i32()?;
        if n < 0 {
            bail!("invalid servers.dat");
        }
        Ok(n as usize)
    }
    fn string(&mut self) -> Result<String> {
        let n = self.u16()? as usize;
        // Java's modified UTF-8 is plain UTF-8 for everything a server name holds
        Ok(String::from_utf8_lossy(self.take(n)?).into_owned())
    }
    fn payload(&mut self, kind: u8) -> Result<Tag> {
        self.depth += 1;
        if self.depth > 64 {
            bail!("invalid servers.dat");
        }
        let tag = match kind {
            1 => self.take(1).map(|_| Tag::Other)?,
            2 => self.take(2).map(|_| Tag::Other)?,
            3 | 5 => self.take(4).map(|_| Tag::Other)?,
            4 | 6 => self.take(8).map(|_| Tag::Other)?,
            7 => {
                let n = self.len()?;
                self.take(n).map(|_| Tag::Other)?
            }
            8 => Tag::Str(self.string()?),
            9 => {
                let item = self.u8()?;
                let n = self.len()?;
                let mut items = Vec::new();
                for _ in 0..n {
                    items.push(self.payload(item)?);
                }
                Tag::List(items)
            }
            10 => {
                let mut fields = Vec::new();
                loop {
                    let k = self.u8()?;
                    if k == 0 {
                        break;
                    }
                    let name = self.string()?;
                    fields.push((name, self.payload(k)?));
                }
                Tag::Compound(fields)
            }
            11 => {
                let n = self.len()?;
                self.take(n.checked_mul(4).ok_or_else(|| anyhow::anyhow!("invalid servers.dat"))?).map(|_| Tag::Other)?
            }
            12 => {
                let n = self.len()?;
                self.take(n.checked_mul(8).ok_or_else(|| anyhow::anyhow!("invalid servers.dat"))?).map(|_| Tag::Other)?
            }
            _ => bail!("invalid servers.dat"),
        };
        self.depth -= 1;
        Ok(tag)
    }
}

fn field<'a>(fields: &'a [(String, Tag)], name: &str) -> Option<&'a Tag> {
    fields.iter().find(|(n, _)| n == name).map(|(_, t)| t)
}

pub fn parse(data: &[u8]) -> Result<Vec<Server>> {
    let mut r = Reader { data, depth: 0 };
    if r.u8()? != 10 {
        bail!("invalid servers.dat");
    }
    r.string()?;
    let Tag::Compound(root) = r.payload(10)? else { bail!("invalid servers.dat") };
    let Some(Tag::List(list)) = field(&root, "servers") else {
        return Ok(Vec::new());
    };
    let mut out = Vec::new();
    for item in list {
        let Tag::Compound(f) = item else { continue };
        let (Some(Tag::Str(name)), Some(Tag::Str(ip))) = (field(f, "name"), field(f, "ip")) else { continue };
        let icon = match field(f, "icon") {
            Some(Tag::Str(b64)) if !b64.is_empty() && b64.len() < 100_000 && b64.bytes().all(|c| c.is_ascii_alphanumeric() || b"+/=\r\n".contains(&c)) => {
                Some(format!("data:image/png;base64,{}", b64.replace(['\r', '\n'], "")))
            }
            _ => None,
        };
        out.push(Server { name: name.clone(), address: ip.clone(), icon });
    }
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn s(out: &mut Vec<u8>, text: &str) {
        out.extend((text.len() as u16).to_be_bytes());
        out.extend(text.as_bytes());
    }

    fn server(out: &mut Vec<u8>, name: &str, ip: &str, icon: Option<&str>) {
        out.push(8);
        s(out, "name");
        s(out, name);
        out.push(8);
        s(out, "ip");
        s(out, ip);
        if let Some(i) = icon {
            out.push(8);
            s(out, "icon");
            s(out, i);
        }
        out.push(1);
        s(out, "acceptTextures");
        out.push(1);
        out.push(0);
    }

    #[test]
    fn reads_the_servers_list() {
        let mut d = vec![10];
        s(&mut d, "");
        d.push(9);
        s(&mut d, "servers");
        d.push(10);
        d.extend(2i32.to_be_bytes());
        server(&mut d, "Hypixel", "mc.hypixel.net", Some("iVBORw0K"));
        server(&mut d, "Ami", "play.ami.fr:25570", None);
        d.push(0);
        let list = parse(&d).unwrap();
        assert_eq!(list.len(), 2);
        assert_eq!(list[0].name, "Hypixel");
        assert_eq!(list[0].icon.as_deref(), Some("data:image/png;base64,iVBORw0K"));
        assert_eq!(list[1], Server { name: "Ami".into(), address: "play.ami.fr:25570".into(), icon: None });
    }

    #[test]
    fn refuses_broken_files() {
        assert!(parse(&[10, 0, 0, 9]).is_err());
        assert!(parse(&[]).is_err());
        let dir = tempfile::tempdir().unwrap();
        assert!(saved(dir.path()).unwrap().is_empty());
    }
}
