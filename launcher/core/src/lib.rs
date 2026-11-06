//! The Rago launcher's engine: no window, no async runtime, just the steps.
//!
//! Minecraft 1.8.9, its libraries, sounds and textures always come from Mojang's servers, fetched
//! for the player and checked against Mojang's own SHA-1s; nothing of the game is in this crate.
//! Java 8 and 21 come from Eclipse Adoptium (checked by SHA-256), Forge from its maven (pinned SHA-1s
//! in `data/forge-1.8.9.json`), Rago from this repository's releases. Rago's own loader, when the
//! launcher was built with it, starts the game on Java 21 without Forge.

pub mod auth;
pub mod forge;
pub mod http;
pub mod import;
pub mod install;
pub mod java;
pub mod launch;
pub mod loader;
pub mod mojang;
pub mod net;
pub mod network;
pub mod news;
pub mod options;
pub mod os;
pub mod rago;
pub mod servers;
pub mod skin;
pub mod system;
pub mod updates;

pub use install::{install, Installed, Layout, Step};
pub use launch::{command, Account, Options};
