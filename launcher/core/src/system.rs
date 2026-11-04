//! What the player's computer has, and the few Windows settings that help a game run.

use std::path::Path;
use std::process::Command;

use anyhow::{bail, Context, Result};

/// The computer's memory, in megabytes.
pub fn total_memory_mb() -> Option<u64> {
    imp::total_memory_bytes().map(|b| b / (1024 * 1024))
}

/// The share of the computer's memory in use, from 0 to 100.
pub fn memory_load() -> Option<u32> {
    imp::memory_load()
}

/// The share of the processor in use since the last call (the first call measures 250 ms), 0 to 100.
pub fn cpu_load() -> Option<u32> {
    static LAST: std::sync::Mutex<Option<(u64, u64)>> = std::sync::Mutex::new(None);
    let mut last = LAST.lock().unwrap();
    let (idle0, total0) = match *last {
        Some(t) => t,
        None => {
            let t = imp::cpu_times()?;
            std::thread::sleep(std::time::Duration::from_millis(250));
            t
        }
    };
    let (idle1, total1) = imp::cpu_times()?;
    *last = Some((idle1, total1));
    let total = total1.saturating_sub(total0);
    if total == 0 {
        return Some(0);
    }
    let busy = total.saturating_sub(idle1.saturating_sub(idle0));
    Some(((busy * 100 + total / 2) / total).min(100) as u32)
}

/// The graphics cards' names ("NVIDIA GeForce RTX 3060"), the dedicated one first when there are two.
pub fn gpus() -> Vec<String> {
    let mut names = imp::gpus();
    names.dedup();
    // an integrated Intel chip or Microsoft's basic driver after a real card
    names.sort_by_key(|n| {
        let n = n.to_ascii_lowercase();
        (n.contains("microsoft") || n.contains("basic")) as u8 * 2 + (n.contains("intel") && !n.contains("arc")) as u8
    });
    names
}

/// "NVIDIA GeForce RTX 3060" as "RTX 3060", short enough for a tile.
pub fn short_gpu_name(name: &str) -> String {
    let mut n = name.to_string();
    for noise in ["NVIDIA ", "GeForce ", "AMD ", "Radeon(TM) ", "(R)", "(TM)", " Graphics", " Series", " Laptop GPU"] {
        n = n.replace(noise, " ");
    }
    let n = n.split_whitespace().collect::<Vec<_>>().join(" ");
    if n.is_empty() {
        name.to_string()
    } else {
        n
    }
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
        bail!("invalid Java path");
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
        bail!("Windows refused the graphics card setting");
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

    #[repr(C)]
    #[derive(Default, Clone, Copy)]
    struct FileTime {
        low: u32,
        high: u32,
    }

    #[link(name = "kernel32")]
    extern "system" {
        fn GetSystemTimes(idle: *mut FileTime, kernel: *mut FileTime, user: *mut FileTime) -> i32;
    }

    fn status() -> Option<MemoryStatusEx> {
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
        (ok != 0).then_some(s)
    }

    pub fn total_memory_bytes() -> Option<u64> {
        status().map(|s| s.total_phys)
    }

    pub fn memory_load() -> Option<u32> {
        status().map(|s| s.memory_load)
    }

    /// (idle, total) in 100 ns units; Windows counts idle time inside kernel time
    pub fn cpu_times() -> Option<(u64, u64)> {
        let (mut idle, mut kernel, mut user) = (FileTime::default(), FileTime::default(), FileTime::default());
        // SAFETY: three valid FILETIME out-pointers
        let ok = unsafe { GetSystemTimes(&mut idle, &mut kernel, &mut user) };
        let v = |t: FileTime| (t.high as u64) << 32 | t.low as u64;
        (ok != 0).then(|| (v(idle), v(kernel) + v(user)))
    }

    /// The display adapters' names, from the registry (the same list as Device Manager).
    pub fn gpus() -> Vec<String> {
        let mut cmd = std::process::Command::new("reg");
        cmd.args(["query", r"HKLM\SYSTEM\CurrentControlSet\Control\Class\{4d36e968-e325-11ce-bfc1-08002be10318}", "/s", "/v", "DriverDesc"]);
        super::hide_window(&mut cmd);
        let Ok(out) = cmd.output() else { return Vec::new() };
        super::reg_values(&String::from_utf8_lossy(&out.stdout), "DriverDesc")
    }
}

#[cfg(target_os = "macos")]
mod imp {
    pub fn total_memory_bytes() -> Option<u64> {
        let out = std::process::Command::new("sysctl").args(["-n", "hw.memsize"]).output().ok()?;
        String::from_utf8_lossy(&out.stdout).trim().parse().ok()
    }

    pub fn memory_load() -> Option<u32> {
        None
    }

    pub fn cpu_times() -> Option<(u64, u64)> {
        None
    }

    pub fn gpus() -> Vec<String> {
        Vec::new()
    }
}

#[cfg(not(any(windows, target_os = "macos")))]
mod imp {
    pub fn total_memory_bytes() -> Option<u64> {
        let info = std::fs::read_to_string("/proc/meminfo").ok()?;
        super::mem_total(&info)
    }

    pub fn memory_load() -> Option<u32> {
        let info = std::fs::read_to_string("/proc/meminfo").ok()?;
        let total = super::mem_total(&info)?;
        let available = super::meminfo_kb(&info, "MemAvailable:")? * 1024;
        Some((100 - available * 100 / total.max(1)) as u32)
    }

    pub fn cpu_times() -> Option<(u64, u64)> {
        super::proc_stat(&std::fs::read_to_string("/proc/stat").ok()?)
    }

    pub fn gpus() -> Vec<String> {
        let Ok(out) = std::process::Command::new("lspci").output() else { return Vec::new() };
        String::from_utf8_lossy(&out.stdout)
            .lines()
            .filter(|l| l.contains("VGA compatible controller") || l.contains("3D controller"))
            .filter_map(|l| l.split_once(": ").map(|(_, s)| s.trim().to_string()))
            .collect()
    }
}

#[cfg_attr(any(windows, target_os = "macos"), allow(dead_code))]
fn meminfo_kb(meminfo: &str, key: &str) -> Option<u64> {
    meminfo.lines().find(|l| l.starts_with(key))?.split_whitespace().nth(1)?.parse().ok()
}

#[cfg_attr(any(windows, target_os = "macos"), allow(dead_code))]
fn mem_total(meminfo: &str) -> Option<u64> {
    meminfo_kb(meminfo, "MemTotal:").map(|kb| kb * 1024)
}

/// (idle, total) jiffies from /proc/stat's first line; iowait counts as idle.
#[cfg_attr(any(windows, target_os = "macos"), allow(dead_code))]
fn proc_stat(stat: &str) -> Option<(u64, u64)> {
    let line = stat.lines().find(|l| l.starts_with("cpu "))?;
    let v: Vec<u64> = line.split_whitespace().skip(1).filter_map(|x| x.parse().ok()).collect();
    if v.len() < 5 {
        return None;
    }
    // user nice system idle iowait irq softirq steal (guests are already in user)
    let total = v.iter().take(8).sum();
    Some((v[3] + v[4], total))
}

/// The values named `name` in `reg query` output.
#[cfg_attr(not(windows), allow(dead_code))]
fn reg_values(output: &str, name: &str) -> Vec<String> {
    output
        .lines()
        .filter_map(|l| {
            let l = l.trim();
            let rest = l.strip_prefix(name)?.trim_start();
            let value = rest.strip_prefix("REG_SZ")?.trim();
            (!value.is_empty()).then(|| value.to_string())
        })
        .collect()
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
    fn reads_load_and_gpus() {
        assert_eq!(proc_stat("cpu  100 0 50 800 50 0 0 0 0 0\ncpu0 1 2 3 4 5\n"), Some((850, 1000)));
        let reg = "\r\nHKEY_LOCAL_MACHINE\\...\\0000\r\n    DriverDesc    REG_SZ    Intel(R) UHD Graphics 630\r\n\r\nHKEY_LOCAL_MACHINE\\...\\0001\r\n    DriverDesc    REG_SZ    NVIDIA GeForce RTX 3060 Laptop GPU\r\n";
        assert_eq!(reg_values(reg, "DriverDesc"), ["Intel(R) UHD Graphics 630", "NVIDIA GeForce RTX 3060 Laptop GPU"]);
        assert_eq!(short_gpu_name("NVIDIA GeForce RTX 3060 Laptop GPU"), "RTX 3060");
        assert_eq!(short_gpu_name("AMD Radeon(TM) RX 6700 XT"), "RX 6700 XT");
        assert_eq!(short_gpu_name("Intel(R) UHD Graphics 630"), "Intel UHD 630");
    }

    #[test]
    fn this_computer_has_memory() {
        assert!(cpu_load().is_some_and(|l| l <= 100) || cfg!(target_os = "macos"));
        assert!(memory_load().is_some_and(|l| l <= 100) || cfg!(target_os = "macos"));
        assert!(total_memory_mb().unwrap_or(1) > 0);
        assert!(cpu_threads() >= 1);
    }
}
