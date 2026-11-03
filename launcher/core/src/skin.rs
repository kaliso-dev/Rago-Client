//! The player's face from their Minecraft skin, for the account button.

use anyhow::{anyhow, Result};
use base64::engine::general_purpose::STANDARD;
use base64::Engine;
use image::{imageops, Rgba, RgbaImage};
use reqwest::blocking::Client;

/// The skin's address from Mojang's public profile (no token needed).
pub fn skin_url(client: &Client, uuid: &str) -> Result<String> {
    if uuid.len() != 32 || !uuid.chars().all(|c| c.is_ascii_hexdigit()) {
        return Err(anyhow!("identifiant de joueur invalide"));
    }
    let profile: serde_json::Value = client
        .get(format!("https://sessionserver.mojang.com/session/minecraft/profile/{uuid}"))
        .send()?
        .error_for_status()?
        .json()?;
    let value = profile["properties"]
        .as_array()
        .and_then(|p| p.iter().find(|x| x["name"] == "textures"))
        .and_then(|x| x["value"].as_str())
        .ok_or_else(|| anyhow!("profil sans skin"))?;
    let textures: serde_json::Value = serde_json::from_slice(&STANDARD.decode(value)?)?;
    let url = textures["textures"]["SKIN"]["url"].as_str().ok_or_else(|| anyhow!("profil sans skin"))?;
    let url = url.replacen("http://", "https://", 1);
    if !url.starts_with("https://textures.minecraft.net/") {
        return Err(anyhow!("adresse de skin inattendue"));
    }
    Ok(url)
}

/// The face (with the hat layer over it) of a 64×64 or 64×32 skin, scaled up to `size` pixels.
pub fn face(skin_png: &[u8], size: u32) -> Result<RgbaImage> {
    let skin = image::load_from_memory_with_format(skin_png, image::ImageFormat::Png)?.to_rgba8();
    if skin.width() < 64 || skin.height() < 16 {
        return Err(anyhow!("skin trop petit"));
    }
    let mut face = imageops::crop_imm(&skin, 8, 8, 8, 8).to_image();
    let hat = imageops::crop_imm(&skin, 40, 8, 8, 8).to_image();
    for (x, y, p) in hat.enumerate_pixels() {
        if p[3] > 0 {
            let under = *face.get_pixel(x, y);
            let a = p[3] as u32;
            let mix = |i: usize| ((p[i] as u32 * a + under[i] as u32 * (255 - a)) / 255) as u8;
            face.put_pixel(x, y, Rgba([mix(0), mix(1), mix(2), 255]));
        }
    }
    Ok(imageops::resize(&face, size, size, imageops::FilterType::Nearest))
}

/// The face as a PNG, from the player's uuid.
pub fn face_png(client: &Client, uuid: &str, size: u32) -> Result<Vec<u8>> {
    let url = skin_url(client, uuid)?;
    let skin = client.get(url).send()?.error_for_status()?.bytes()?;
    let face = face(&skin, size)?;
    let mut out = std::io::Cursor::new(Vec::new());
    face.write_to(&mut out, image::ImageFormat::Png)?;
    Ok(out.into_inner())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hat_layer_covers_the_face() {
        let mut skin = RgbaImage::from_pixel(64, 64, Rgba([0, 0, 0, 0]));
        for y in 8..16 {
            for x in 8..16 {
                skin.put_pixel(x, y, Rgba([10, 20, 30, 255]));
            }
        }
        skin.put_pixel(40, 8, Rgba([200, 0, 0, 255]));
        let mut png = std::io::Cursor::new(Vec::new());
        skin.write_to(&mut png, image::ImageFormat::Png).unwrap();
        let face = face(&png.into_inner(), 16).unwrap();
        assert_eq!(face.get_pixel(0, 0), &Rgba([200, 0, 0, 255]));
        assert_eq!(face.get_pixel(15, 15), &Rgba([10, 20, 30, 255]));
    }
}
