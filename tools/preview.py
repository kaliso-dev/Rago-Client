"""Pictures of a cosmetic on a player, made without the game, to look at a model before trying it in
Rago: the .rmdl is read back as gg.rago.render.CosmeticModel reads it (its triangles, texture
coordinates and baked shade), and drawn with the player's skin under the same flat light.

  python3 tools/preview.py MODEL.rmdl TEXTURE.png OUT.png [--skin SKIN.png] [--title TEXT]

The pictures: from the front, three-quarters, the side, the back, above, and the whole player.
For now the model is drawn in head space (a hat); wings and shields will need their own places.
"""
import argparse
import gzip
import math
import os
import struct

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'rago')
FONT = os.path.join(RES, 'fonts', 'inter-600.ttf')

# the player, in pixels of the skin: x toward the left hand, y down from the neck, z from the face
# to the back. (box corner, size, skin texture corner, outer layer texture corner, how much bigger
# the outer layer is)
PARTS = [
    ((-4, -8, -4), (8, 8, 8), (0, 0), (32, 0), 0.5),       # head and its hat layer
    ((-4, 0, -2), (8, 12, 4), (16, 16), (16, 32), 0.25),    # body and jacket
    ((-8, 0, -2), (4, 12, 4), (40, 16), (40, 32), 0.25),    # right arm and sleeve
    ((4, 0, -2), (4, 12, 4), (32, 48), (48, 48), 0.25),     # left arm and sleeve
    ((-3.9, 12, -2), (4, 12, 4), (0, 16), (0, 32), 0.25),   # right leg
    ((-0.1, 12, -2), (4, 12, 4), (16, 48), (0, 48), 0.25),  # left leg
]

VIEWS = [
    ('Face', 0, 8, 'head'),
    ('Trois-quarts', 35, 18, 'head'),
    ('Profil', 90, 6, 'head'),
    ('Dos', 180, 10, 'head'),
    ('Dessus', 30, 58, 'head'),
    ('En entier', 28, 12, 'all'),
]
WING_VIEWS = [
    ('Dos', 180, 8, 'all'),
    ('Trois-quarts dos', 145, 16, 'all'),
    ('Profil', 90, 6, 'all'),
    ('Face', 0, 6, 'all'),
    ('Dessus', 160, 55, 'all'),
    ('Trois-quarts face', 35, 14, 'all'),
]

# Where wings go on the back, as gg.rago.modules.Cosmetics.WING_FITS says (keep the two the same):
# the root of the model on the body (x from the spine, y down from the neck, z back from the middle,
# in blocks), its turn back around the spine and up around the root (degrees, for the left wing;
# the right one mirrored), and how wide the texture is in blocks.
DEFAULT_WINGS = (0.05, 0.04, 0.16, -30, -22, 0.7)
WING_FITS = {
    'dragon_red_black': (0, 0.16, 0.16, -18, -10, 1.0),
    'rago_frost': (0, 0.16, 0.16, -18, -10, 1.0),
    'rago_inferno': (0, 0.16, 0.16, -18, -10, 1.0),
}


def read_rmdl(path):
    """The groups of a Rago model: (name, pivot, positions (n, 3, 3) in blocks, uvs (n, 3, 2), shades (n,))."""
    with gzip.open(path, 'rb') as f:
        b = f.read()
    magic, version, count = struct.unpack('>iii', b[:12])
    assert magic == 0x524D444C and version == 1, 'not a Rago model'
    off = 12
    groups = []
    vertex = struct.Struct('>fffffB')
    for _ in range(count):
        n, = struct.unpack('>H', b[off:off + 2])
        name = b[off + 2:off + 2 + n].decode('utf-8')
        off += 2 + n
        px, py, pz, tris = struct.unpack('>fffi', b[off:off + 16])
        off += 16
        raw = np.frombuffer(b, dtype=np.dtype([('p', '>f4', 3), ('uv', '>f4', 2), ('s', 'u1')]),
                            count=tris * 3, offset=off)
        off += tris * 3 * vertex.size
        groups.append((name, (px, py, pz), raw['p'].astype(float).reshape(-1, 3, 3),
                       raw['uv'].astype(float).reshape(-1, 3, 2), raw['s'].reshape(-1, 3)[:, 0] / 255))
    return groups


def box_faces(corner, size, uv, inflate, tex_size):
    """A box's six faces as Minecraft maps a skin onto them: (four corners as seen from outside, top
    left first; their texture coordinates; the face's shade)."""
    x0, y0, z0 = (np.array(corner, float) - inflate)
    w, h, d = size
    x1, y1, z1 = x0 + w + 2 * inflate, y0 + h + 2 * inflate, z0 + d + 2 * inflate
    u, v = uv
    tw, th = tex_size

    def rect(a, b, c, e):
        return [(a / tw, b / th), (c / tw, b / th), (c / tw, e / th), (a / tw, e / th)]

    return [
        # front (toward -z), right side (-x), left side (+x), back (+z), top, bottom
        ([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], rect(u + d, v + d, u + d + w, v + d + h), 0.8),
        ([(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)], rect(u, v + d, u + d, v + d + h), 0.6),
        ([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], rect(u + d + w, v + d, u + 2 * d + w, v + d + h), 0.6),
        ([(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)], rect(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), 0.8),
        ([(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], rect(u + d, v, u + d + w, v + d), 1.0),
        ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], rect(u + d + w, v, u + d + 2 * w, v + d), 0.5),
    ]


def player(skin):
    """The player's triangles: positions (pixels), uvs, shades."""
    pos, uvs, shades = [], [], []
    size = skin.shape[1], skin.shape[0]
    for corner, dims, uv, layer_uv, inflate in PARTS:
        for base, grow in ((uv, 0), (layer_uv, inflate)):
            for corners, tex, shade in box_faces(corner, dims, base, grow, size):
                for t in ((0, 1, 2), (0, 2, 3)):
                    pos.append([corners[i] for i in t])
                    uvs.append([tex[i] for i in t])
                    shades.append(shade)
    return np.array(pos, float), np.array(uvs, float), np.array(shades, float)


def rotation(yaw, pitch):
    """Turns the scene so the camera, looking toward +z with -y up, sees it from yaw and pitch degrees."""
    a, b = math.radians(yaw), math.radians(pitch)
    ry = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    rx = np.array([[1, 0, 0], [0, math.cos(b), -math.sin(b)], [0, math.sin(b), math.cos(b)]])
    return rx @ ry


def render(meshes, yaw, pitch, focus, size, background):
    """Draws meshes [(positions, uvs, shades, texture)] seen from yaw and pitch, the points `focus`
    filling the picture; a z-buffer, nearest texels, the alpha test of the game."""
    r = rotation(yaw, pitch)
    f = focus @ r.T
    lo, hi = f[:, :2].min(0), f[:, :2].max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1]) * 1.18
    scale = size / span
    mid = (lo + hi) / 2
    color = background.copy()
    depth = np.full((size, size), np.inf)
    for pos, uv, shade, tex in meshes:
        th, tw = tex.shape[:2]
        p = pos @ r.T
        sx = (p[..., 0] - mid[0]) * scale + size / 2
        sy = -(p[..., 1] - mid[1]) * scale + size / 2
        # the camera looks toward +z with -y up: screen up is -y
        sy = size - sy
        for t in range(len(p)):
            x, y, z = sx[t], sy[t], p[t, :, 2]
            x0, x1 = max(int(math.floor(x.min())), 0), min(int(math.ceil(x.max())), size - 1)
            y0, y1 = max(int(math.floor(y.min())), 0), min(int(math.ceil(y.max())), size - 1)
            if x0 > x1 or y0 > y1:
                continue
            area = (x[1] - x[0]) * (y[2] - y[0]) - (x[2] - x[0]) * (y[1] - y[0])
            if abs(area) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            w0 = ((x[1] - gx) * (y[2] - gy) - (x[2] - gx) * (y[1] - gy)) / area
            w1 = ((x[2] - gx) * (y[0] - gy) - (x[0] - gx) * (y[2] - gy)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            zz = w0 * z[0] + w1 * z[1] + w2 * z[2]
            region = depth[y0:y1 + 1, x0:x1 + 1]
            closer = inside & (zz < region - 1e-6)
            if not closer.any():
                continue
            u = w0 * uv[t, 0, 0] + w1 * uv[t, 1, 0] + w2 * uv[t, 2, 0]
            v = w0 * uv[t, 0, 1] + w1 * uv[t, 1, 1] + w2 * uv[t, 2, 1]
            tx = np.clip((u * tw).astype(int), 0, tw - 1)
            ty = np.clip((v * th).astype(int), 0, th - 1)
            texel = tex[ty, tx]
            keep = closer & (texel[..., 3] > 25)
            if not keep.any():
                continue
            region[keep] = zz[keep]
            out = color[y0:y1 + 1, x0:x1 + 1]
            out[keep] = texel[keep][:, :3] * shade[t]
    return color


def on_back(pos, fit):
    """A wing model's triangles (blocks) placed on the body as Cosmetics.extrudedWing does, both
    sides: translate, turn back around y, up around z, scale (mirrored on the right). In pixels."""
    x, y, z, sweep, lift, span = fit
    out = []
    for side in (-1, 1):
        a, b = np.radians(side * sweep), np.radians(side * lift)
        ry = np.array([[np.cos(a), 0, np.sin(a)], [0, 1, 0], [-np.sin(a), 0, np.cos(a)]])
        rz = np.array([[np.cos(b), -np.sin(b), 0], [np.sin(b), np.cos(b), 0], [0, 0, 1]])
        m = ry @ rz @ np.diag([side * span, span, span])
        out.append((pos @ m.T + np.array([side * x, y, z])) * 16)
    return out


def gradient(size):
    top, bottom = np.array([92, 98, 112], float), np.array([58, 62, 72], float)
    t = np.linspace(0, 1, size)[:, None, None]
    return np.broadcast_to(top * (1 - t) + bottom * t, (size, size, 3)).copy()


def sheet(model, texture, skin, out, title=None, panel=360, supersample=2, place='head'):
    """A picture of six views of the model on the player: a hat (place 'head'), or wings (place: the
    fit of the wings, as WING_FITS)."""
    skin_tex = np.asarray(Image.open(skin).convert('RGBA')).astype(float)
    cos_img = Image.open(texture).convert('RGBA')
    if cos_img.width > 512:
        # a big texture is drawn from its mipmaps in game (HdTexture): one halved enough for these views
        cos_img = cos_img.resize((512, 512 * cos_img.height // cos_img.width), Image.BOX)
    cos_tex = np.asarray(cos_img).astype(float)
    ppos, puv, pshade = player(skin_tex)
    meshes = [(ppos, puv, pshade, skin_tex)]
    cosmetic = []
    for _, _, pos, uv, shade in read_rmdl(model):
        if place == 'head':
            # the model is in blocks, in head space: the same as the player's pixels / 16
            placed = [pos * 16]
        else:
            placed = on_back(pos, place)
        for p in placed:
            meshes.append((p, uv, shade, cos_tex))
            cosmetic.append(p.reshape(-1, 3))
    cosmetic = np.concatenate(cosmetic)
    head = np.array([[x, y, z] for x in (-4.5, 4.5) for y in (-8.5, 3) for z in (-4.5, 4.5)])
    everything = np.r_[cosmetic, ppos.reshape(-1, 3)]
    big = panel * supersample
    pictures = []
    for label, yaw, pitch, focus in (VIEWS if place == 'head' else WING_VIEWS):
        pts = np.r_[cosmetic, head] if focus == 'head' else everything
        img = render(meshes, yaw, pitch, pts, big, gradient(big))
        pictures.append((label, Image.fromarray(img.clip(0, 255).astype(np.uint8)).resize((panel, panel), Image.BOX)))
    cols, gap, header = 3, 8, 56 if title else 0
    rows = math.ceil(len(pictures) / cols)
    w = cols * panel + (cols + 1) * gap
    h = header + rows * panel + (rows + 1) * gap
    page = Image.new('RGB', (w, h), (30, 32, 38))
    d = ImageDraw.Draw(page)
    try:
        font = ImageFont.truetype(FONT, 18)
        big_font = ImageFont.truetype(FONT, 24)
    except OSError:
        font = big_font = ImageFont.load_default()
    if title:
        d.text((gap + 6, 16), title, fill=(240, 240, 245), font=big_font)
    for i, (label, img) in enumerate(pictures):
        x = gap + (i % cols) * (panel + gap)
        y = header + gap + (i // cols) * (panel + gap)
        page.paste(img, (x, y))
        d.text((x + 10, y + 8), label, fill=(245, 245, 250), font=font)
    page.save(out)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('model')
    ap.add_argument('texture')
    ap.add_argument('out')
    ap.add_argument('--skin', default=os.path.join(RES, 'skins', 'qrago.png'))
    ap.add_argument('--title')
    args = ap.parse_args()
    print(sheet(args.model, args.texture, args.skin, args.out, args.title))


if __name__ == '__main__':
    main()
