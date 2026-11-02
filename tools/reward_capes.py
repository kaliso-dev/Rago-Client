"""Draws the capes won with Rago Points into assets/rago/textures/capes, all Rago's own drawings from
shapes and noise (no pictures):

  sakura      a branch of cherry blossoms against a red sun, petals falling, from night to dawn
  circuit     neon circuit tracks in Rago's red and cyan around the R logo, on black
  inferno     flames rising from the bottom, embers in the dark above
  galaxy      a nebula of violet, blue and pink, stars and a ringed planet (mythic, chest only)
  streak_100  gold, a flame and 100: the cape of a hundred day streak

16 times the 64 x 32 layout (1024 x 512, a face of 160 x 256): shapes stay smooth in game (the HD
textures are filtered, with mipmaps) for a quarter of the memory of the 2048 px capes. Each face is
drawn 4 times bigger and made smaller, for clean edges.

  python3 tools/reward_capes.py [--preview sheet.png]
"""
import argparse
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import capes

S = 16
W, H = 10 * S, 16 * S
SUPER = 4
BW, BH = W * SUPER, H * SUPER


def noise(w, h, scale, octaves, seed):
    """Smooth value noise in [0, 1], `octaves` layers each twice finer and half as strong."""
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w))
    total = 0
    amp = 1.0
    for o in range(octaves):
        cells = max(2, int(scale * 2 ** o))
        grid = rng.random((cells + 2, cells + 2))
        img = Image.fromarray((grid * 255).astype(np.uint8)).resize((w + w // cells * 2, h + h // cells * 2), Image.BICUBIC)
        arr = np.asarray(img, dtype=float)[h // cells // 2:h // cells // 2 + h, w // cells // 2:w // cells // 2 + w] / 255
        out += arr * amp
        total += amp
        amp *= 0.5
    out /= total
    return (out - out.min()) / (out.max() - out.min() + 1e-9)


def gradient(stops, w=BW, h=BH):
    """A vertical gradient through (position 0..1, rgb) stops."""
    ys = np.linspace(0, 1, h)
    cols = np.zeros((h, 3))
    for c in range(3):
        cols[:, c] = np.interp(ys, [p for p, _ in stops], [rgb[c] for _, rgb in stops])
    arr = np.repeat(cols[:, None, :], w, axis=1)
    return arr


def to_image(arr):
    a = np.clip(arr, 0, 255).astype(np.uint8)
    return Image.fromarray(a, 'RGB').convert('RGBA')


def glow_layer(shape, colour, radius, strength=1.0):
    """A soft light of `colour` around the white parts of the L image `shape`."""
    blur = shape.filter(ImageFilter.GaussianBlur(radius))
    layer = Image.new('RGBA', shape.size, colour + (0,))
    layer.putalpha(blur.point(lambda v: min(255, round(v * strength))))
    return layer


def logo_mask(size):
    mask = Image.open(os.path.join(capes.RES, 'textures', 'logo.png')).convert('RGBA').split()[3]
    return mask.resize((size, size), Image.LANCZOS)


def finish(face):
    return face.resize((W, H), Image.LANCZOS, reducing_gap=3.0)


# ---- sakura -----------------------------------------------------------------------------------

def sakura():
    sky = gradient([(0, (26, 18, 52)), (0.45, (92, 42, 92)), (0.8, (232, 128, 140)), (1, (255, 190, 170))])
    face = to_image(sky)
    d = ImageDraw.Draw(face)
    # the sun, low, a deep red disc with a soft halo
    cx, cy, r = BW * 0.6, BH * 0.52, BW * 0.3
    halo = Image.new('L', face.size)
    ImageDraw.Draw(halo).ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    face.alpha_composite(glow_layer(halo, (255, 120, 110), BW * 0.08, 0.7))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(214, 40, 56, 255))
    # far hills, two layers of soft blue-violet
    for base, amp, colour, seed in ((0.78, 0.05, (70, 40, 92), 1), (0.86, 0.04, (40, 24, 62), 2)):
        rnd = random.Random(seed)
        phase = [rnd.random() * 6 for _ in range(3)]
        pts = [(0, BH)]
        for x in range(0, BW + 8, 8):
            t = x / BW
            y = base + amp * (math.sin(t * 5 + phase[0]) * 0.6 + math.sin(t * 11 + phase[1]) * 0.3 + math.sin(t * 23 + phase[2]) * 0.1)
            pts.append((x, y * BH))
        pts.append((BW, BH))
        d.polygon(pts, fill=colour + (255,))
    # the branch from the top left, dark, forking twice
    branch = Image.new('RGBA', face.size)
    bd = ImageDraw.Draw(branch)
    bark = (34, 18, 26, 255)
    blossoms = []

    def limb(x0, y0, angle, length, width, depth, rnd):
        steps = 10
        x, y = x0, y0
        for i in range(steps):
            a = angle + math.sin(i * 0.9 + depth) * 0.18
            nx, ny = x + math.cos(a) * length / steps, y + math.sin(a) * length / steps
            w = width * (1 - i / steps * 0.55)
            bd.line([(x, y), (nx, ny)], fill=bark, width=max(2, round(w)))
            bd.ellipse([nx - w / 2, ny - w / 2, nx + w / 2, ny + w / 2], fill=bark)
            if i > 2 and rnd.random() < 0.7:
                blossoms.append((nx + rnd.uniform(-BW * 0.04, BW * 0.04), ny + rnd.uniform(-BW * 0.04, BW * 0.04)))
            x, y = nx, ny
        if depth > 0:
            limb(x, y, angle - 0.55, length * 0.6, width * 0.55, depth - 1, rnd)
            limb(x, y, angle + 0.35, length * 0.5, width * 0.5, depth - 1, rnd)
        else:
            blossoms.extend([(x + rnd.uniform(-BW * 0.05, BW * 0.05), y + rnd.uniform(-BW * 0.05, BW * 0.05)) for _ in range(5)])

    rnd = random.Random(7)
    limb(-BW * 0.05, BH * 0.08, 0.32, BW * 0.75, BW * 0.07, 2, rnd)
    limb(-BW * 0.05, BH * 0.3, -0.05, BW * 0.5, BW * 0.045, 1, rnd)
    face.alpha_composite(branch)

    def flower(dr, x, y, size, rot, colour, centre):
        for k in range(5):
            a = rot + k * math.tau / 5
            px, py = x + math.cos(a) * size * 0.55, y + math.sin(a) * size * 0.55
            dr.ellipse([px - size * 0.45, py - size * 0.45, px + size * 0.45, py + size * 0.45], fill=colour)
        dr.ellipse([x - size * 0.18, y - size * 0.18, x + size * 0.18, y + size * 0.18], fill=centre)

    petals = Image.new('RGBA', face.size)
    pd = ImageDraw.Draw(petals)
    for x, y in blossoms:
        size = BW * rnd.uniform(0.045, 0.07)
        shade = rnd.choice([(255, 196, 220, 255), (255, 176, 206, 255), (250, 214, 230, 255)])
        flower(pd, x, y, size, rnd.random() * math.tau, shade, (226, 70, 120, 255))
    # falling petals over the lower half
    for _ in range(46):
        x, y = rnd.uniform(0, BW), rnd.uniform(BH * 0.25, BH)
        s = BW * rnd.uniform(0.012, 0.024)
        a = rnd.random() * math.pi
        dx, dy = math.cos(a) * s, math.sin(a) * s
        pd.polygon([(x - dx, y - dy), (x - dy * 0.5, y + dx * 0.5), (x + dx, y + dy), (x + dy * 0.5, y - dx * 0.5)],
                   fill=(255, 200, 222, 220))
    face.alpha_composite(petals)
    return finish(face), (40, 22, 48)


# ---- circuit ----------------------------------------------------------------------------------

def circuit():
    face = to_image(gradient([(0, (8, 8, 12)), (1, (14, 12, 20))]))
    rnd = random.Random(11)
    grid = 20  # cells across the face
    cell = BW / grid
    rows = int(BH / cell)
    lines = {(255, 59, 59): Image.new('L', face.size), (64, 224, 255): Image.new('L', face.size)}
    taken = set()
    # keep the logo's place free
    lx0, ly0, lsize = BW * 0.22, BH * 0.3, BW * 0.56
    for gx in range(grid + 1):
        for gy in range(rows + 1):
            x, y = gx * cell, gy * cell
            if lx0 - cell < x < lx0 + lsize + cell and ly0 - cell < y < ly0 + lsize + cell:
                taken.add((gx, gy))
    for t in range(44):
        colour = (255, 59, 59) if t % 3 else (64, 224, 255)
        d = ImageDraw.Draw(lines[colour])
        gx, gy = rnd.randrange(grid + 1), rnd.randrange(rows + 1)
        if (gx, gy) in taken:
            continue
        direction = rnd.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
        path = [(gx, gy)]
        for _ in range(rnd.randrange(4, 14)):
            if rnd.random() < 0.3:
                # tracks turn by 45 degrees, as on a circuit board
                dx, dy = direction
                direction = rnd.choice([(dx + dy, dy - dx), (dx - dy, dy + dx)]) if dx == 0 or dy == 0 else rnd.choice([(dx, 0), (0, dy)])
                direction = (max(-1, min(1, direction[0])), max(-1, min(1, direction[1])))
                if direction == (0, 0):
                    direction = (1, 0)
            nx, ny = path[-1][0] + direction[0], path[-1][1] + direction[1]
            if not (0 <= nx <= grid and 0 <= ny <= rows) or (nx, ny) in taken:
                break
            path.append((nx, ny))
        if len(path) < 3:
            continue
        taken.update(path)
        pts = [(x * cell, y * cell) for x, y in path]
        d.line(pts, fill=255, width=round(cell * 0.16), joint='curve')
        for x, y in (pts[0], pts[-1]):
            r = cell * 0.28
            d.ellipse([x - r, y - r, x + r, y + r], outline=255, width=round(cell * 0.12))
    for colour, shape in lines.items():
        face.alpha_composite(glow_layer(shape, colour, cell * 0.5, 1.4))
        solid = Image.new('RGBA', face.size, colour + (0,))
        solid.putalpha(shape)
        face.alpha_composite(solid)
    # the R logo, glowing red, with a thin frame like a chip
    d = ImageDraw.Draw(face)
    d.rounded_rectangle([lx0, ly0, lx0 + lsize, ly0 + lsize], radius=cell * 0.6, fill=(16, 14, 22, 255),
                        outline=(255, 59, 59, 255), width=round(cell * 0.14))
    for i in range(5):
        for side in (0, 1):
            x = lx0 + lsize * (0.18 + i * 0.16)
            y = ly0 - cell * 0.6 if side == 0 else ly0 + lsize
            d.rectangle([x - cell * 0.12, y, x + cell * 0.12, y + cell * 0.6], fill=(150, 150, 160, 255))
    mask = logo_mask(round(lsize * 0.7))
    shape = Image.new('L', face.size)
    shape.paste(mask, (round(lx0 + lsize * 0.15), round(ly0 + lsize * 0.15)))
    face.alpha_composite(glow_layer(shape, (255, 59, 59), cell * 0.8, 1.2))
    solid = Image.new('RGBA', face.size, (255, 92, 84, 0))
    solid.putalpha(shape)
    face.alpha_composite(solid)
    return finish(face), (10, 10, 14)


# ---- inferno ----------------------------------------------------------------------------------

def inferno():
    h, w = BH, BW
    n = noise(w, h, 3, 5, 21)
    ys = np.linspace(0, 1, h)[:, None]
    xs = np.linspace(0, 1, w)[None, :]
    # the flames' height wavers along the width; noise tears their tops into tongues
    top = 0.38 + 0.08 * np.sin(xs * 9.0) + 0.05 * np.sin(xs * 23.0 + 1.3)
    # tongues: the noise stretched upwards, so flames are tall and thin
    tall = np.asarray(Image.fromarray((noise(w, h // 4, 6, 4, 22) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC),
                      dtype=float) / 255
    heat = np.clip((ys - top) / (1 - top), 0, 1) * 1.05 + (n - 0.5) * 0.5 + (tall - 0.5) * 0.7
    heat = np.clip(heat, 0, 0.93)
    stops = [(0.0, (14, 6, 8)), (0.25, (70, 8, 10)), (0.45, (178, 26, 14)), (0.65, (240, 92, 20)), (0.85, (255, 186, 52)),
             (1.0, (255, 244, 190))]
    flat = heat.ravel()
    rgb = np.zeros((flat.size, 3))
    for c in range(3):
        rgb[:, c] = np.interp(flat, [p for p, _ in stops], [col[c] for _, col in stops])
    face = to_image(rgb.reshape(h, w, 3))
    # embers rising in the dark
    rnd = random.Random(5)
    sparks = Image.new('L', face.size)
    d = ImageDraw.Draw(sparks)
    for _ in range(46):
        x, y = rnd.uniform(0, w), rnd.uniform(0, h * 0.62)
        r = rnd.uniform(0.6, 1.8) * SUPER
        d.ellipse([x - r, y - r, x + r, y + r], fill=rnd.randrange(150, 255))
    face.alpha_composite(glow_layer(sparks, (255, 120, 30), 3 * SUPER, 2.0))
    lit = Image.new('RGBA', face.size, (255, 210, 120, 0))
    lit.putalpha(sparks)
    face.alpha_composite(lit)
    return finish(face), (40, 8, 8)


# ---- galaxy -----------------------------------------------------------------------------------

def galaxy():
    h, w = BH, BW
    base = gradient([(0, (6, 4, 18)), (1, (10, 6, 28))])
    clouds = [(noise(w, h, 2.5, 6, 31), (120, 50, 200), 1.6), (noise(w, h, 3, 6, 32), (40, 90, 220), 1.4),
              (noise(w, h, 4, 5, 33), (240, 80, 160), 1.3)]
    # the nebula follows a band across the cape
    ys = np.linspace(0, 1, h)[:, None]
    xs = np.linspace(0, 1, w)[None, :]
    band = np.exp(-((ys - 0.45 - (xs - 0.5) * 0.5) ** 2) / 0.05)
    for n, colour, power in clouds:
        k = np.clip((n - 0.45) * 2.2, 0, 1) ** power * band
        base = base + k[:, :, None] * np.array(colour)[None, None, :] * 0.85
    face = to_image(base)
    rnd = random.Random(9)
    stars = Image.new('L', face.size)
    d = ImageDraw.Draw(stars)
    for _ in range(420):
        x, y = rnd.uniform(0, w), rnd.uniform(0, h)
        r = rnd.choice([0.6, 0.8, 1.0, 1.2, 1.8]) * SUPER
        d.ellipse([x - r, y - r, x + r, y + r], fill=rnd.randrange(120, 255))
    face.alpha_composite(glow_layer(stars, (180, 200, 255), 2 * SUPER, 1.2))
    white = Image.new('RGBA', face.size, (255, 255, 255, 0))
    white.putalpha(stars)
    face.alpha_composite(white)
    # a few bright stars with four rays
    d = ImageDraw.Draw(face)
    for _ in range(6):
        x, y = rnd.uniform(w * 0.1, w * 0.9), rnd.uniform(h * 0.05, h * 0.95)
        s = rnd.uniform(8, 16) * SUPER
        for dx, dy in ((s, 0), (0, s)):
            d.polygon([(x - dx, y - dy), (x + dy * 0.08, y - dx * 0.08), (x + dx, y + dy), (x - dy * 0.08, y + dx * 0.08)],
                      fill=(230, 236, 255, 255))
    # the planet and its ring, low on the cape
    cx, cy, r = w * 0.55, h * 0.74, w * 0.2
    ring = Image.new('RGBA', face.size)
    rd = ImageDraw.Draw(ring)
    rd.ellipse([cx - r * 2.1, cy - r * 0.5, cx + r * 2.1, cy + r * 0.5], outline=(240, 200, 140, 255), width=round(r * 0.14))
    rd.ellipse([cx - r * 1.8, cy - r * 0.38, cx + r * 1.8, cy + r * 0.38], outline=(200, 150, 110, 200), width=round(r * 0.06))
    back = ring.crop((0, 0, w, round(cy)))
    face.alpha_composite(back, (0, 0))
    planet = Image.new('RGBA', face.size)
    pd = ImageDraw.Draw(planet)
    pd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(230, 120, 90, 255))
    # bands and a shadow on the planet
    pn = noise(w, h, 2, 3, 40)
    arr = np.asarray(planet, dtype=float)
    yy = (np.arange(h)[:, None] - cy) / r
    xx = (np.arange(w)[None, :] - cx) / r
    stripes = 0.85 + 0.15 * np.sin(yy * 9 + pn * 3)
    light = np.clip(1.1 - (xx + yy + 0.6) * 0.45, 0.25, 1.1)
    arr[:, :, :3] *= (stripes * light)[:, :, None]
    face.alpha_composite(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA'))
    front = ring.crop((0, round(cy), w, h))
    face.alpha_composite(front, (0, round(cy)))
    return finish(face), (16, 10, 36)


# ---- streak_100 -------------------------------------------------------------------------------

def streak_100():
    face = to_image(gradient([(0, (60, 30, 6)), (0.5, (150, 84, 14)), (1, (70, 34, 6))]))
    # rays from the centre
    rays = Image.new('L', face.size)
    d = ImageDraw.Draw(rays)
    cx, cy = BW / 2, BH * 0.42
    for i in range(24):
        a0 = i * math.tau / 24
        a1 = a0 + math.tau / 48
        d.polygon([(cx, cy), (cx + math.cos(a0) * BH, cy + math.sin(a0) * BH), (cx + math.cos(a1) * BH, cy + math.sin(a1) * BH)],
                  fill=60)
    gold = Image.new('RGBA', face.size, (255, 214, 110, 0))
    gold.putalpha(rays)
    face.alpha_composite(gold)
    # the flame
    flame = Image.new('L', face.size)
    fd = ImageDraw.Draw(flame)
    s = BW * 0.28

    def drop(dr, x, y, size, fill):
        pts = []
        for i in range(60):
            t = i / 59 * math.tau
            r = size * (1 - 0.55 * (1 - math.cos(t)) / 2) if True else size
            px = x + math.sin(t) * r * 0.75
            py = y - size * 0.6 * math.cos(t) - (size * 0.9 if math.cos(t) > 0.98 else 0) * 0
            pts.append((px, py))
        tip = (x + size * 0.1, y - size * 1.7)
        dr.polygon([tip] + [p for p in pts if p[1] > y - size * 0.4] + [tip], fill=fill)
        dr.ellipse([x - size * 0.75, y - size * 0.75, x + size * 0.75, y + size * 0.6], fill=fill)

    drop(fd, cx, cy - s * 0.2, s, 255)
    face.alpha_composite(glow_layer(flame, (255, 140, 30), BW * 0.06, 1.3))
    outer = Image.new('RGBA', face.size, (255, 120, 24, 0))
    outer.putalpha(flame)
    face.alpha_composite(outer)
    inner = Image.new('L', face.size)
    drop(ImageDraw.Draw(inner), cx, cy, s * 0.55, 255)
    core = Image.new('RGBA', face.size, (255, 236, 150, 0))
    core.putalpha(inner)
    face.alpha_composite(core)
    # 100, in Rago's bold font, gold with a dark edge
    font = ImageFont.truetype(os.path.join(capes.RES, 'fonts', 'inter-700.ttf'), round(BW * 0.36))
    d = ImageDraw.Draw(face)
    text = '100'
    box = d.textbbox((0, 0), text, font=font)
    tx = (BW - (box[2] - box[0])) / 2 - box[0]
    ty = BH * 0.7 - (box[3] - box[1]) / 2 - box[1]
    d.text((tx, ty), text, font=font, fill=(255, 226, 122, 255), stroke_width=round(BW * 0.012), stroke_fill=(70, 30, 4, 255))
    return finish(face), (90, 50, 10)


CAPES = (('sakura', sakura), ('circuit', circuit), ('inferno', inferno), ('galaxy', galaxy), ('streak_100', streak_100))


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--preview', help='also save the faces side by side to this file')
    a = p.parse_args()
    faces = []
    for name, fn in CAPES:
        face, edge = fn()
        capes.cape(face, edge, s=S).save(os.path.join(capes.OUT, name + '.png'), optimize=True)
        faces.append(face)
        print(name)
    if a.preview:
        sheet = Image.new('RGBA', (len(faces) * (W + 20) + 20, H + 40), (40, 40, 46, 255))
        for i, f in enumerate(faces):
            sheet.alpha_composite(f, (20 + i * (W + 20), 20))
        sheet.save(a.preview)


if __name__ == '__main__':
    main()
