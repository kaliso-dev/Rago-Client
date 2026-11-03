//! A server's connection quality and status, measured the way the game's server list does.

use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream, ToSocketAddrs};
use std::time::{Duration, Instant};

use anyhow::{anyhow, bail, Context, Result};
use serde::Serialize;

const TIMEOUT: Duration = Duration::from_secs(3);
/// Minecraft 1.8's protocol number, sent in the handshake.
const PROTOCOL_1_8: i32 = 47;

/// "mc.hypixel.net" or "mc.hypixel.net:25565" as a host and a port, refusing anything else.
pub fn parse_address(address: &str) -> Result<(String, u16)> {
    let address = address.trim();
    let (host, port) = match address.rsplit_once(':') {
        Some((h, p)) => (h, p.parse::<u16>().map_err(|_| anyhow!("port invalide"))?),
        None => (address, 25565),
    };
    let ok = !host.is_empty()
        && host.len() <= 253
        && host.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '-')
        && !host.starts_with(['-', '.'])
        && port != 0;
    if !ok {
        bail!("adresse de serveur invalide : {address}");
    }
    Ok((host.to_ascii_lowercase(), port))
}

fn resolve(host: &str, port: u16) -> Result<SocketAddr> {
    (host, port).to_socket_addrs().with_context(|| format!("{host} introuvable"))?.next().ok_or_else(|| anyhow!("{host} introuvable"))
}

/// The connection to a server, from a few TCP connections.
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Latency {
    /// the median time to connect, in milliseconds
    pub ping_ms: u32,
    /// how much that time moves from one try to the next (mean difference), in milliseconds
    pub jitter_ms: u32,
    /// tries that did not connect
    pub lost: u32,
    pub tries: u32,
}

/// Connects `tries` times to `host:port` and measures how long each connection takes.
pub fn latency(host: &str, port: u16, tries: u32) -> Result<Latency> {
    let addr = resolve(host, port)?;
    let mut times = Vec::new();
    let mut lost = 0;
    for i in 0..tries {
        if i > 0 {
            std::thread::sleep(Duration::from_millis(120));
        }
        let start = Instant::now();
        match TcpStream::connect_timeout(&addr, TIMEOUT) {
            Ok(s) => {
                times.push(start.elapsed().as_secs_f64() * 1000.0);
                drop(s);
            }
            Err(_) => lost += 1,
        }
    }
    if times.is_empty() {
        bail!("{host} ne répond pas");
    }
    Ok(summarize(&times, lost, tries))
}

fn summarize(times: &[f64], lost: u32, tries: u32) -> Latency {
    let mut sorted = times.to_vec();
    sorted.sort_by(|a, b| a.total_cmp(b));
    let median = sorted[sorted.len() / 2];
    let jitter = if times.len() > 1 { times.windows(2).map(|w| (w[1] - w[0]).abs()).sum::<f64>() / (times.len() - 1) as f64 } else { 0.0 };
    Latency { ping_ms: median.round() as u32, jitter_ms: jitter.round() as u32, lost, tries }
}

/// What a server says about itself in the server list.
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    pub online: u32,
    pub max: u32,
    /// the server's own icon, as a data: URL
    pub favicon: Option<String>,
    pub ping_ms: u32,
}

fn write_varint(out: &mut Vec<u8>, mut v: i32) {
    loop {
        let mut b = (v & 0x7f) as u8;
        v = ((v as u32) >> 7) as i32;
        if v != 0 {
            b |= 0x80;
        }
        out.push(b);
        if v == 0 {
            break;
        }
    }
}

fn read_varint(r: &mut impl Read) -> Result<i32> {
    let mut v: i32 = 0;
    for i in 0..5 {
        let mut b = [0u8];
        r.read_exact(&mut b)?;
        v |= ((b[0] & 0x7f) as i32) << (7 * i);
        if b[0] & 0x80 == 0 {
            return Ok(v);
        }
    }
    bail!("réponse invalide")
}

fn packet(id: i32, body: &[u8]) -> Vec<u8> {
    let mut inner = Vec::new();
    write_varint(&mut inner, id);
    inner.extend_from_slice(body);
    let mut out = Vec::new();
    write_varint(&mut out, inner.len() as i32);
    out.extend(inner);
    out
}

/// Asks the server for its status, like the multiplayer screen does.
pub fn status(host: &str, port: u16) -> Result<Status> {
    let addr = resolve(host, port)?;
    let start = Instant::now();
    let mut s = TcpStream::connect_timeout(&addr, TIMEOUT)?;
    s.set_read_timeout(Some(TIMEOUT))?;
    s.set_write_timeout(Some(TIMEOUT))?;
    s.set_nodelay(true)?;
    let mut hello = Vec::new();
    write_varint(&mut hello, PROTOCOL_1_8);
    write_varint(&mut hello, host.len() as i32);
    hello.extend_from_slice(host.as_bytes());
    hello.extend_from_slice(&port.to_be_bytes());
    write_varint(&mut hello, 1);
    let mut request = packet(0, &hello);
    request.extend(packet(0, &[]));
    s.write_all(&request)?;
    let len = read_varint(&mut s)?;
    if !(1..=1 << 21).contains(&len) {
        bail!("réponse invalide");
    }
    let mut body = vec![0u8; len as usize];
    s.read_exact(&mut body)?;
    let ping_ms = start.elapsed().as_millis() as u32;
    let mut cursor = &body[..];
    if read_varint(&mut cursor)? != 0 {
        bail!("réponse invalide");
    }
    let text_len = read_varint(&mut cursor)?;
    if text_len < 0 || text_len as usize > cursor.len() {
        bail!("réponse invalide");
    }
    let json: serde_json::Value = serde_json::from_slice(&cursor[..text_len as usize])?;
    Ok(parse_status(&json, ping_ms))
}

fn parse_status(json: &serde_json::Value, ping_ms: u32) -> Status {
    let favicon = json["favicon"]
        .as_str()
        .filter(|f| f.starts_with("data:image/png;base64,") && f.len() < 100_000)
        .map(|f| f.replace(['\n', '\r'], ""));
    Status {
        online: json["players"]["online"].as_u64().unwrap_or(0) as u32,
        max: json["players"]["max"].as_u64().unwrap_or(0) as u32,
        favicon,
        ping_ms,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_server_addresses() {
        assert_eq!(parse_address("mc.hypixel.net").unwrap(), ("mc.hypixel.net".into(), 25565));
        assert_eq!(parse_address(" Play.Example.org:25570 ").unwrap(), ("play.example.org".into(), 25570));
        assert!(parse_address("evil host").is_err());
        assert!(parse_address("a:0").is_err());
        assert!(parse_address("-x.org").is_err());
        assert!(parse_address("").is_err());
    }

    #[test]
    fn varints_round_trip() {
        for v in [0, 1, 127, 128, 300, 47, 2_097_151, -1] {
            let mut b = Vec::new();
            write_varint(&mut b, v);
            assert_eq!(read_varint(&mut &b[..]).unwrap(), v);
        }
    }

    #[test]
    fn summarizes_latency() {
        let l = summarize(&[30.0, 34.0, 31.0, 33.0], 0, 4);
        assert_eq!(l.ping_ms, 33);
        assert_eq!(l.jitter_ms, 3);
    }

    #[test]
    fn reads_a_status() {
        let json = serde_json::json!({"players": {"online": 41234, "max": 200000}, "favicon": "data:image/png;base64,AAA\n"});
        let s = parse_status(&json, 40);
        assert_eq!((s.online, s.max, s.favicon.as_deref()), (41234, 200000, Some("data:image/png;base64,AAA")));
        assert!(parse_status(&serde_json::json!({"favicon": "javascript:x"}), 1).favicon.is_none());
    }
}
