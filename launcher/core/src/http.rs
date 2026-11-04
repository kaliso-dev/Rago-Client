//! Downloads that are only kept once their size and hash match.

use std::fs::{self, File};
use std::io::{self, Read, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering};
use std::sync::Mutex;
use std::time::Duration;

use anyhow::{bail, Context, Result};
use reqwest::blocking::Client;
use sha1::Digest;

pub const USER_AGENT: &str = concat!("RagoLauncher/", env!("CARGO_PKG_VERSION"));

pub fn client() -> Result<Client> {
    Ok(Client::builder()
        .user_agent(USER_AGENT)
        .connect_timeout(Duration::from_secs(20))
        .timeout(Duration::from_secs(300))
        .build()?)
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Hash {
    Sha1(String),
    Sha256(String),
}

enum Hasher {
    Sha1(sha1::Sha1),
    Sha256(sha2::Sha256),
}

impl Hash {
    fn hasher(&self) -> Hasher {
        match self {
            Hash::Sha1(_) => Hasher::Sha1(sha1::Sha1::new()),
            Hash::Sha256(_) => Hasher::Sha256(sha2::Sha256::new()),
        }
    }

    fn expected(&self) -> &str {
        match self {
            Hash::Sha1(h) | Hash::Sha256(h) => h,
        }
    }

    /// Whether the file at `path` has this hash.
    pub fn check_file(&self, path: &Path) -> io::Result<bool> {
        let mut hasher = self.hasher();
        let mut file = File::open(path)?;
        let mut buf = vec![0u8; 64 * 1024];
        loop {
            let n = file.read(&mut buf)?;
            if n == 0 {
                break;
            }
            hasher.update(&buf[..n]);
        }
        Ok(hasher.finish().eq_ignore_ascii_case(self.expected()))
    }
}

impl Hasher {
    fn update(&mut self, data: &[u8]) {
        match self {
            Hasher::Sha1(h) => h.update(data),
            Hasher::Sha256(h) => h.update(data),
        }
    }

    fn finish(self) -> String {
        match self {
            Hasher::Sha1(h) => hex::encode(h.finalize()),
            Hasher::Sha256(h) => hex::encode(h.finalize()),
        }
    }
}

/// One file to have on disk.
#[derive(Clone, Debug)]
pub struct Download {
    pub url: String,
    pub path: PathBuf,
    pub hash: Hash,
    pub size: Option<u64>,
}

impl Download {
    /// Whether the file is already there, whole and unchanged.
    pub fn is_present(&self) -> bool {
        let Ok(meta) = fs::metadata(&self.path) else {
            return false;
        };
        if self.size.is_some_and(|s| s != meta.len()) {
            return false;
        }
        self.hash.check_file(&self.path).unwrap_or(false)
    }

    /// Fetches the file (three tries), keeping it only if it matches.
    pub fn fetch(&self, client: &Client) -> Result<u64> {
        let mut last = None;
        for attempt in 0..3u32 {
            if attempt > 0 {
                std::thread::sleep(Duration::from_millis(500 << attempt));
            }
            match self.try_fetch(client) {
                Ok(n) => return Ok(n),
                Err(e) => last = Some(e),
            }
        }
        Err(last.unwrap()).with_context(|| format!("downloading {}", self.url))
    }

    fn try_fetch(&self, client: &Client) -> Result<u64> {
        let mut response = client.get(&self.url).send()?.error_for_status()?;
        if let Some(parent) = self.path.parent() {
            fs::create_dir_all(parent)?;
        }
        let mut part = self.path.clone().into_os_string();
        part.push(".part");
        let part = PathBuf::from(part);
        let mut file = File::create(&part)?;
        let mut hasher = self.hash.hasher();
        let mut buf = vec![0u8; 64 * 1024];
        let mut total = 0u64;
        loop {
            let n = response.read(&mut buf)?;
            if n == 0 {
                break;
            }
            hasher.update(&buf[..n]);
            file.write_all(&buf[..n])?;
            total += n as u64;
            if self.size.is_some_and(|s| total > s) {
                break;
            }
        }
        file.flush()?;
        drop(file);
        let got = hasher.finish();
        if self.size.is_some_and(|s| s != total) || !got.eq_ignore_ascii_case(self.hash.expected()) {
            let _ = fs::remove_file(&part);
            bail!("damaged file ({total} bytes, hash {got})");
        }
        fs::rename(&part, &self.path)?;
        Ok(total)
    }
}

/// Where a batch of downloads is.
#[derive(Clone, Copy, Debug)]
pub struct Progress {
    pub done: usize,
    pub total: usize,
    pub bytes: u64,
}

/// Makes sure every file is present, fetching the missing ones on `workers` threads.
pub fn fetch_all(client: &Client, items: &[Download], workers: usize, progress: &(dyn Fn(Progress) + Sync)) -> Result<()> {
    let next = AtomicUsize::new(0);
    let done = AtomicUsize::new(0);
    let bytes = AtomicU64::new(0);
    let failed = AtomicBool::new(false);
    let error = Mutex::new(None);
    let total = items.len();
    progress(Progress { done: 0, total, bytes: 0 });
    std::thread::scope(|scope| {
        for _ in 0..workers.max(1) {
            scope.spawn(|| loop {
                if failed.load(Ordering::Relaxed) {
                    return;
                }
                let i = next.fetch_add(1, Ordering::Relaxed);
                let Some(item) = items.get(i) else {
                    return;
                };
                if !item.is_present() {
                    match item.fetch(client) {
                        Ok(n) => {
                            bytes.fetch_add(n, Ordering::Relaxed);
                        }
                        Err(e) => {
                            failed.store(true, Ordering::Relaxed);
                            error.lock().unwrap().get_or_insert(e);
                            return;
                        }
                    }
                }
                let d = done.fetch_add(1, Ordering::Relaxed) + 1;
                progress(Progress { done: d, total, bytes: bytes.load(Ordering::Relaxed) });
            });
        }
    });
    match error.into_inner().unwrap() {
        Some(e) => Err(e),
        None => Ok(()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn checks_files_by_hash() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("a.txt");
        fs::write(&path, b"rago").unwrap();
        let real = Hash::Sha1(hex::encode(sha1::Sha1::digest(b"rago")));
        assert!(real.check_file(&path).unwrap());
        assert!(!Hash::Sha1("0".repeat(40)).check_file(&path).unwrap());
        let d = Download { url: String::new(), path: path.clone(), hash: real, size: Some(4) };
        assert!(d.is_present());
        let wrong_size = Download { size: Some(5), ..d };
        assert!(!wrong_size.is_present());
    }
}
