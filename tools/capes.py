"""Draws Rago's extra capes into assets/rago/textures/capes, in HD (16 times the 64 x 32 layout).

The original Rago cape (textures/cape.png) is kept as it is; these are other designs to choose from
in game. Each uses the R logo (textures/logo.png, a white mask) and the bundled Poppins font.
Run with `python3 tools/capes.py`.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'rago')
OUT = os.path.join(RES, 'textures', 'capes')
S = 16
W, H = 10 * S, 16 * S  # the outer face of the cape


def vertical(top, bottom, w=W, h=H):
    img = Image.new('RGBA', (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)) + (255,))
    return img


def logo(size, colour, glow=None):
    """The R logo tinted, optionally with a soft glow behind it, on a transparent square."""
    mask = Image.open(os.path.join(RES, 'textures', 'logo.png')).convert('RGBA').resize((size, size), Image.LANCZOS)
    alpha = mask.split()[3]
    pad = size // 3
    out = Image.new('RGBA', (size + 2 * pad, size + 2 * pad))
    if glow:
        g = Image.new('RGBA', out.size, glow + (0,))
        g.putalpha(Image.new('L', out.size))
        halo = Image.new('L', out.size)
        halo.paste(alpha, (pad, pad))
        halo = halo.filter(ImageFilter.GaussianBlur(size / 6))
        g.putalpha(halo.point(lambda v: min(255, v * 2)))
        out = Image.alpha_composite(out, g)
    tinted = Image.new('RGBA', (size, size), colour + (255,))
    tinted.putalpha(alpha)
    out.alpha_composite(tinted, (pad, pad))
    return out


def centred(face, img, cy):
    face.alpha_composite(img, ((W - img.width) // 2, cy - img.height // 2))


def text(face, s, cy, size, colour, weight='600'):
    font = ImageFont.truetype(os.path.join(RES, 'fonts', f'poppins-{weight}.ttf'), size)
    d = ImageDraw.Draw(face)
    box = d.textbbox((0, 0), s, font=font)
    d.text(((W - (box[2] - box[0])) // 2 - box[0], cy - (box[3] - box[1]) // 2 - box[1]), s, font=font, fill=colour)


def overlay(face, draw):
    """Draws see-through shapes on a layer of their own, blended over the face."""
    layer = Image.new('RGBA', face.size)
    draw(ImageDraw.Draw(layer))
    return Image.alpha_composite(face, layer)


def border(face, colour, width):
    d = ImageDraw.Draw(face)
    for i in range(width):
        d.rectangle([i, i, W - 1 - i, H - 1 - i], outline=colour)


def midnight():
    face = vertical((14, 18, 44), (4, 5, 14))
    rnd = random.Random(3)
    d = ImageDraw.Draw(face)
    for _ in range(70):
        x, y = rnd.randrange(W), rnd.randrange(H)
        r = rnd.choice((1, 1, 1, 2))
        v = rnd.randrange(150, 256)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(v, v, 255, 255))
    # a crescent moon low on the cape
    d.ellipse([W * 0.55, H * 0.68, W * 0.85, H * 0.68 + W * 0.3], fill=(236, 236, 255, 255))
    d.ellipse([W * 0.61, H * 0.66, W * 0.91, H * 0.66 + W * 0.3], fill=(9, 11, 28, 255))
    centred(face, logo(72, (176, 196, 255), (80, 110, 255)), 76)
    text(face, 'RAGO', 150, 26, (224, 230, 255, 255))
    return face, (60, 80, 170)


def flame():
    face = vertical((22, 12, 10), (8, 4, 4))
    rnd = random.Random(5)
    fire = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(fire)
    # flame tongues rising from the hem, hotter at the bottom
    for i in range(26):
        x = rnd.uniform(-10, W + 10)
        top = rnd.uniform(H * 0.45, H * 0.8)
        w = rnd.uniform(14, 30)
        col = rnd.choice(((255, 90, 30), (255, 140, 40), (240, 50, 30)))
        d.polygon([(x - w, H), (x, top), (x + w, H)], fill=col + (200,))
    for i in range(14):
        x = rnd.uniform(0, W)
        top = rnd.uniform(H * 0.72, H * 0.9)
        d.polygon([(x - 10, H), (x, top), (x + 10, H)], fill=(255, 210, 90, 220))
    fire = fire.filter(ImageFilter.GaussianBlur(2))
    face = Image.alpha_composite(face, fire)
    centred(face, logo(76, (255, 120, 50), (255, 70, 20)), 74)
    return face, (200, 60, 30)


def ocean():
    face = vertical((20, 120, 150), (6, 30, 60))
    def waves(d):
        for k in range(7):
            y0 = H * 0.55 + k * 16
            pts = [(x, y0 + math.sin(x / 14 + k) * 5) for x in range(0, W + 1, 4)]
            d.line(pts, fill=(170, 235, 245, 70 + k * 15), width=3)
    face = overlay(face, waves)
    centred(face, logo(76, (230, 252, 255), (120, 230, 255)), 74)
    return face, (20, 90, 120)


def royal():
    face = vertical((58, 22, 92), (26, 8, 44))
    def pattern(d):
        # a faint diamond pattern under everything
        for y in range(-16, H + 16, 32):
            for x in range(-16, W + 16, 32):
                d.polygon([(x, y - 10), (x + 10, y), (x, y + 10), (x - 10, y)], fill=(255, 255, 255, 12))
        d.rectangle([10, 10, W - 11, H - 11], outline=(232, 190, 80, 140), width=2)
    face = overlay(face, pattern)
    border(face, (232, 190, 80, 255), 6)
    centred(face, logo(70, (245, 205, 95), (150, 90, 20)), 80)
    text(face, 'RAGO', 158, 24, (245, 214, 130, 255))
    return face, (232, 190, 80)


def frost():
    face = vertical((236, 242, 250), (190, 205, 225))
    d = ImageDraw.Draw(face)
    rnd = random.Random(9)
    for _ in range(18):
        x, y, r = rnd.uniform(0, W), rnd.uniform(0, H), rnd.uniform(4, 9)
        for a in range(6):
            t = a * math.pi / 3
            d.line([(x, y), (x + math.cos(t) * r, y + math.sin(t) * r)], fill=(150, 175, 210, 255), width=2)
    centred(face, logo(76, (40, 60, 100), (120, 160, 230)), 74)
    text(face, 'RAGO', 154, 26, (40, 60, 100, 255))
    return face, (150, 175, 210)


def cape(face, edge, s=S, inside=None):
    """Places the outer face in the 64 x 32 cape layout (s texture pixels to a cape pixel), with plain
    edges, top and inside (the side against the back, dark unless given)."""
    img = Image.new('RGBA', (64 * s, 32 * s))
    d = ImageDraw.Draw(img)
    e = edge + (255,)
    dark = (inside or tuple(round(c * 0.35) for c in edge)) + (255,)
    d.rectangle([1 * s, 0, 11 * s - 1, 1 * s - 1], fill=e)  # top
    d.rectangle([11 * s, 0, 21 * s - 1, 1 * s - 1], fill=e)  # bottom
    d.rectangle([0, 1 * s, 1 * s - 1, 17 * s - 1], fill=e)  # side
    d.rectangle([11 * s, 1 * s, 12 * s - 1, 17 * s - 1], fill=e)  # side
    d.rectangle([12 * s, 1 * s, 22 * s - 1, 17 * s - 1], fill=dark)  # inside, against the back
    img.paste(face, (1 * s, 1 * s))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in (('midnight', midnight), ('flame', flame), ('ocean', ocean), ('royal', royal), ('frost', frost)):
        face, edge = fn()
        cape(face.convert('RGBA'), edge).save(os.path.join(OUT, name + '.png'), optimize=True)


if __name__ == '__main__':
    main()
