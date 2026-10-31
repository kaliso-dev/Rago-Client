"""Draws the creator's badge textures into assets/rago/textures: Rago's R logo in a pink gradient
(badge_creator.png, the logo before the creator's name) and the crown and sparkle shown after it
(badge_icons.png). Rago's own drawings, from textures/logo.png and plain shapes.

  python3 tools/badges.py
"""
import os

from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'rago')
TEXTURES = os.path.join(RES, 'textures')

# the pink gradient, from light to deep (the name's own gradient is in RagoBadge.CREATOR_LIGHT/DEEP)
LIGHT = (255, 200, 228)
DEEP = (255, 62, 158)

# drawn this many times bigger, then made smaller: smooth edges
SUPER = 8


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def gradient(w, h, light, deep, diagonal):
    """light at the top left, deep at the bottom right (diagonal) or the bottom."""
    img = Image.new('RGBA', (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / (w - 1) * 0.6 + y / (h - 1) * 0.4) if diagonal else y / (h - 1)
            px[x, y] = mix(light, deep, t) + (255,)
    return img


def creator_logo(size=32):
    """The R logo, its shape from logo.png, filled with the pink gradient."""
    mask = Image.open(os.path.join(TEXTURES, 'logo.png')).convert('RGBA').split()[3]
    mask = mask.resize((size, size), Image.LANCZOS)
    img = gradient(size, size, LIGHT, DEEP, True)
    img.putalpha(mask)
    return img


def icons():
    """A crown and a four-pointed sparkle, 16 x 8 units at 4 pixels a unit (64 x 32)."""
    u = 4 * SUPER  # pixels per unit while drawing
    w, h = 16 * u, 8 * u
    shape = Image.new('L', (w, h))
    d = ImageDraw.Draw(shape)

    def pts(points):
        return [(x * u, y * u) for x, y in points]

    # the crown: three points, a ball on each, over a band
    d.polygon(pts([(0.6, 2.6), (2.8, 4.6), (4.5, 1.3), (6.2, 4.6), (8.4, 2.6), (7.8, 6.0), (1.2, 6.0)]), fill=255)
    d.rectangle([1.2 * u, 6.4 * u, 7.8 * u, 7.4 * u], fill=255)
    for cx, cy in ((0.6, 2.3), (4.5, 1.0), (8.4, 2.3)):
        r = 0.65
        d.ellipse([(cx - r) * u, (cy - r) * u, (cx + r) * u, (cy + r) * u], fill=255)
    # the sparkle: four points with hollow sides
    cx, cy, big, small = 12.7, 4.0, 3.1, 0.7
    d.polygon(pts([(cx, cy - big), (cx + small, cy - small), (cx + big, cy), (cx + small, cy + small),
                   (cx, cy + big), (cx - small, cy + small), (cx - big, cy), (cx - small, cy - small)]), fill=255)
    out_w, out_h = w // SUPER, h // SUPER
    img = gradient(out_w, out_h, LIGHT, DEEP, False)
    img.putalpha(shape.resize((out_w, out_h), Image.LANCZOS))
    return img


def save(img, name):
    path = os.path.join(TEXTURES, name)
    img.save(path, optimize=True)
    with open(path + '.mcmeta', 'w') as f:
        f.write('{\n  "texture": {\n    "blur": true\n  }\n}\n')


def main():
    save(creator_logo(), 'badge_creator.png')
    save(icons(), 'badge_icons.png')


if __name__ == '__main__':
    main()
