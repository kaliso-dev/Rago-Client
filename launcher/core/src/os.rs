//! The machine the launcher runs on, in the names Mojang and Adoptium use.

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Os {
    Windows,
    Linux,
    Osx,
}

impl Os {
    pub fn current() -> Os {
        if cfg!(windows) {
            Os::Windows
        } else if cfg!(target_os = "macos") {
            Os::Osx
        } else {
            Os::Linux
        }
    }

    /// The name in Mojang's library rules and natives.
    pub fn mojang(self) -> &'static str {
        match self {
            Os::Windows => "windows",
            Os::Linux => "linux",
            Os::Osx => "osx",
        }
    }

    /// The name in the Adoptium API.
    pub fn adoptium(self) -> &'static str {
        match self {
            Os::Windows => "windows",
            Os::Linux => "linux",
            Os::Osx => "mac",
        }
    }

    pub fn classpath_separator(self) -> &'static str {
        if self == Os::Windows {
            ";"
        } else {
            ":"
        }
    }
}

/// "64" or "32", for `${arch}` in natives classifiers.
pub fn arch_bits() -> &'static str {
    if cfg!(target_pointer_width = "64") {
        "64"
    } else {
        "32"
    }
}

/// The architecture in the Adoptium API.
pub fn adoptium_arch() -> &'static str {
    if cfg!(target_arch = "aarch64") {
        "aarch64"
    } else if cfg!(target_pointer_width = "64") {
        "x64"
    } else {
        "x32"
    }
}
