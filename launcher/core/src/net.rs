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
        Some((h, p)) => (h, p.parse::<u16>().map_err(|_| anyhow!("invalid port"))?),
        None => (address, 25565),
    };
    let ok = !host.is_empty()
        && host.len() <= 253
        && host.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '-')
        && !host.starts_with(['-', '.'])
        && port != 0;
    if !ok {
        bail!("invalid server address: {address}");
    }
    Ok((host.to_ascii_lowercase(), port))
}

fn resolve(host: &str, port: u16) -> Result<SocketAddr> {
    (host, port).to_socket_addrs().with_context(|| format!("{host} not found"))?.next().ok_or_else(|| anyhow!("{host} not found"))
}

/// The connection to a server, from a few pings.
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Latency {
    /// the median ping, in milliseconds
    pub ping_ms: u32,
    /// how much that time moves from one try to the next (mean difference), in milliseconds
    pub jitter_ms: u32,
    /// tries that did not connect
    pub lost: u32,
    pub tries: u32,
}

/// Pings `host:port` `tries` times the way the game's server list does and measures each answer.
pub fn latency(host: &str, port: u16, tries: u32) -> Result<Latency> {
    let addr = resolve(host, port)?;
    let mut times = Vec::new();
    let mut lost = 0;
    for i in 0..tries {
        if i > 0 {
            std::thread::sleep(Duration::from_millis(120));
        }
        match ask(&addr, host, port) {
            Ok((_, ms)) => times.push(ms),
            Err(_) => lost += 1,
        }
    }
    if times.is_empty() {
        bail!("{host} does not answer");
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
    bail!("invalid response")
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
    let (json, ms) = ask(&addr, host, port)?;
    Ok(parse_status(&json, ms.round() as u32))
}

/// One exchange with the server, as the game's server list does it: the status, then a ping packet.
/// The time is the ping packet's round trip only, so neither the TCP connection, nor the server
/// building its status, nor the icon's download count in it, just like the ping the game shows.
fn ask(addr: &SocketAddr, host: &str, port: u16) -> Result<(serde_json::Value, f64)> {
    let mut s = TcpStream::connect_timeout(addr, TIMEOUT)?;
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
    let body = read_packet(&mut s)?;
    let mut cursor = &body[..];
    if read_varint(&mut cursor)? != 0 {
        bail!("invalid response");
    }
    let text_len = read_varint(&mut cursor)?;
    if text_len < 0 || text_len as usize > cursor.len() {
        bail!("invalid response");
    }
    let json: serde_json::Value = serde_json::from_slice(&cursor[..text_len as usize])?;

    let token: i64 = 0x5241_474f; // any number: the server sends it back
    let start = Instant::now();
    s.write_all(&packet(1, &token.to_be_bytes()))?;
    let pong = read_packet(&mut s)?;
    let ms = start.elapsed().as_secs_f64() * 1000.0;
    let mut cursor = &pong[..];
    if read_varint(&mut cursor)? != 1 || cursor != token.to_be_bytes() {
        bail!("invalid response");
    }
    Ok((json, ms))
}

fn read_packet(s: &mut impl Read) -> Result<Vec<u8>> {
    let len = read_varint(s)?;
    if !(1..=1 << 21).contains(&len) {
        bail!("invalid response");
    }
    let mut body = vec![0u8; len as usize];
    s.read_exact(&mut body)?;
    Ok(body)
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

    /// A server that answers one status and one ping, after `delay`, like a 1.8 server would.
    fn fake_server(delay: Duration) -> u16 {
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port();
        std::thread::spawn(move || {
            let (mut s, _) = listener.accept().unwrap();
            read_packet(&mut s).unwrap(); // handshake
            read_packet(&mut s).unwrap(); // status request
            let text = br#"{"players":{"online":3,"max":20}}"#;
            let mut body = Vec::new();
            write_varint(&mut body, text.len() as i32);
            body.extend_from_slice(text);
            s.write_all(&packet(0, &body)).unwrap();
            let ping = read_packet(&mut s).unwrap();
            std::thread::sleep(delay);
            s.write_all(&packet(1, &ping[1..])).unwrap();
        });
        port
    }

    #[test]
    fn times_the_ping_packet() {
        let port = fake_server(Duration::from_millis(60));
        let s = status("127.0.0.1", port).unwrap();
        assert_eq!((s.online, s.max), (3, 20));
        assert!((55..200).contains(&s.ping_ms), "{}", s.ping_ms);
    }
}
