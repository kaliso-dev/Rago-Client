//! What the player's computer has, and the few Windows settings that help a game run.

use std::path::Path;
use std::process::Command;

use anyhow::{bail, Context, Result};

/// The computer's memory, in megabytes.
pub fn total_memory_mb() -> Option<u64> {
    imp::total_memory_bytes().map(|b| b / (1024 * 1024))
}

/// The number of threads the processor runs at once.
pub fn cpu_threads() -> usize {
    std::thread::available_parallelism().map(|n| n.get()).unwrap_or(1)
}

/// The memory to give Minecraft 1.8.9 by default: 2 GB, a bit more on a computer with plenty, never
/// more than half of what the computer has. More memory than the game uses only makes each garbage
/// collection longer.
pub fn recommended_memory_mb(total_mb: Option<u64>) -> u32 {
    match total_mb {
        Some(t) if t >= 16 * 1024 => 3072,
        Some(t) if t >= 6 * 1024 => 2048,
        Some(t) => ((t / 2) as u32).clamp(1024, 2048) / 512 * 512,
        None => 2048,
    }
}

/// The most the memory slider offers: what the computer has minus 2 GB for the system, between 2
/// and 12 GB.
pub fn max_memory_mb(total_mb: Option<u64>) -> u32 {
    let t = total_mb.unwrap_or(8192).saturating_sub(2048);
    (t as u32).clamp(2048, 12 * 1024) / 512 * 512
}

/// Asks Windows to run `java` on the dedicated graphics card (Settings > Display > Graphics), for
/// laptops with two. Removes the choice again when `on` is false. Nothing to do elsewhere.
pub fn set_dedicated_gpu(java: &Path, on: bool) -> Result<()> {
    if !cfg!(windows) {
        return Ok(());
    }
    let key = r"HKCU\Software\Microsoft\DirectX\UserGpuPreferences";
    let path = java.to_string_lossy().into_owned();
    if path.contains('"') {
        bail!("chemin de Java invalide");
    }
    let mut cmd = Command::new("reg");
    if on {
        cmd.args(["add", key, "/v", &path, "/t", "REG_SZ", "/d", "GpuPreference=2;", "/f"]);
    } else {
        cmd.args(["delete", key, "/v", &path, "/f"]);
    }
    hide_window(&mut cmd);
    let out = cmd.output().context("reg")?;
    // deleting a value that is not there is fine
    if on && !out.status.success() {
        bail!("Windows a refusé le réglage de la carte graphique");
    }
    Ok(())
}

/// Starts the process without a console window (Windows).
pub fn hide_window(cmd: &mut Command) {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    #[cfg(not(windows))]
    let _ = cmd;
}

/// Starts the game above other programs for the processor (Windows' "above normal" priority).
/// Other systems need more rights than a player has to raise a priority, so nothing changes there.
pub fn above_normal_priority(cmd: &mut Command) {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const ABOVE_NORMAL_PRIORITY_CLASS: u32 = 0x0000_8000;
        cmd.creation_flags(ABOVE_NORMAL_PRIORITY_CLASS);
    }
    #[cfg(not(windows))]
    let _ = cmd;
}

#[cfg(windows)]
mod imp {
    #[repr(C)]
    struct MemoryStatusEx {
        length: u32,
        memory_load: u32,
        total_phys: u64,
        avail_phys: u64,
        total_page_file: u64,
        avail_page_file: u64,
        total_virtual: u64,
        avail_virtual: u64,
        avail_extended_virtual: u64,
    }

    #[link(name = "kernel32")]
    extern "system" {
        fn GlobalMemoryStatusEx(buffer: *mut MemoryStatusEx) -> i32;
    }

    pub fn total_memory_bytes() -> Option<u64> {
        let mut s = MemoryStatusEx {
            length: std::mem::size_of::<MemoryStatusEx>() as u32,
            memory_load: 0,
            total_phys: 0,
            avail_phys: 0,
            total_page_file: 0,
            avail_page_file: 0,
            total_virtual: 0,
            avail_virtual: 0,
            avail_extended_virtual: 0,
        };
        // SAFETY: the buffer is a MEMORYSTATUSEX with its length set, as the function requires
        let ok = unsafe { GlobalMemoryStatusEx(&mut s) };
        (ok != 0).then_some(s.total_phys)
    }
}

#[cfg(target_os = "macos")]
mod imp {
    pub fn total_memory_bytes() -> Option<u64> {
        let out = std::process::Command::new("sysctl").args(["-n", "hw.memsize"]).output().ok()?;
        String::from_utf8_lossy(&out.stdout).trim().parse().ok()
    }
}

#[cfg(not(any(windows, target_os = "macos")))]
mod imp {
    pub fn total_memory_bytes() -> Option<u64> {
        let info = std::fs::read_to_string("/proc/meminfo").ok()?;
        super::mem_total(&info)
    }
}

#[cfg_attr(any(windows, target_os = "macos"), allow(dead_code))]
fn mem_total(meminfo: &str) -> Option<u64> {
    let line = meminfo.lines().find(|l| l.starts_with("MemTotal:"))?;
    let kb: u64 = line.split_whitespace().nth(1)?.parse().ok()?;
    Some(kb * 1024)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_linux_memory() {
        assert_eq!(mem_total("MemTotal:       16314580 kB\nMemFree: 1 kB\n"), Some(16314580 * 1024));
        assert_eq!(mem_total("nothing"), None);
    }

    #[test]
    fn recommends_sensible_memory() {
        assert_eq!(recommended_memory_mb(Some(4096)), 2048);
        assert_eq!(recommended_memory_mb(Some(3000)), 1024);
        assert_eq!(recommended_memory_mb(Some(8192)), 2048);
        assert_eq!(recommended_memory_mb(Some(32768)), 3072);
        assert_eq!(recommended_memory_mb(None), 2048);
        assert_eq!(max_memory_mb(Some(4096)), 2048);
        assert_eq!(max_memory_mb(Some(16384)), 12 * 1024);
        assert_eq!(max_memory_mb(Some(8192)), 6144);
    }

    #[test]
    fn this_computer_has_memory() {
        assert!(total_memory_mb().unwrap_or(1) > 0);
        assert!(cpu_threads() >= 1);
    }
}
