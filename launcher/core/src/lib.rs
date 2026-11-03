//! The Rago launcher's engine: no window, no async runtime, just the steps.
//!
//! Minecraft 1.8.9, its libraries, sounds and textures always come from Mojang's servers, fetched
//! for the player and checked against Mojang's own SHA-1s; nothing of the game is in this crate.
//! Java 8 comes from Eclipse Adoptium (checked by SHA-256), Forge from its maven (pinned SHA-1s in
//! `data/forge-1.8.9.json`), Rago from this repository's releases.

pub mod auth;
pub mod forge;
pub mod http;
pub mod import;
pub mod install;
pub mod java;
pub mod launch;
pub mod mojang;
pub mod os;
pub mod rago;
pub mod skin;
pub mod updates;

pub use install::{install, Installed, Layout, Step};
pub use launch::{command, Account, Options};
