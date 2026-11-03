"""Draws the flag capes into assets/rago/textures/capes (flag_<code>.png), from the official flags in
tools/flags (public domain SVGs from Wikimedia Commons, listed in tools/flags/README.md).

A cape's face is tall (10 x 16) and a flag is wide, so each flag is fitted the way that keeps it
exact and recognisable:

  extend   the flag at its own proportions across the whole width, its field continued above and
           below (flags whose top and bottom edges are plain stripes or a plain field: France,
           Canada, Turkey...). Nothing is stretched.
  stretch  the stripes made taller, the emblem (if any) kept at its own proportions (Germany, Spain).
  usa      the 13 stripes and the canton made taller, the 50 stars redrawn round, at their places.
  uk       the Union Jack drawn again at the face's proportions from its construction sheet.

4096 x 2048 (64 times the 64 x 32 layout, a face of 640 x 1024), as the other 4K capes. Flags are
flat colours, so the files stay small.

  python3 tools/flag_capes.py [--preview sheet.png]
"""
import argparse
import io
import math
import os

import cairosvg
import numpy as np
from PIL import Image, ImageDraw

import capes

S = 64
W, H = 10 * S, 16 * S
HERE = os.path.dirname(os.path.abspath(__file__))
FLAGS = os.path.join(HERE, 'flags')
SUPER = 2


def render(code, width, height=None):
    """The flag `code` drawn at `width` (and `height`: stretched when given) pixels, RGBA."""
    with open(os.path.join(FLAGS, code + '.svg'), 'rb') as f:
        svg = f.read()
    # cairosvg keeps the flag's proportions: a stretch is made on the picture
    img = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg, output_width=width * SUPER))).convert('RGBA')
    return img.resize((width, round(img.height / SUPER) if height is None else height), Image.LANCZOS)


def extend(code):
    """The flag across the width, centred, its top and bottom rows continued to the face's edges."""
    flag = render(code, W)
    face = Image.new('RGBA', (W, H))
    y = (H - flag.height) // 2
    top = flag.crop((0, 2, W, 3)).resize((W, y + 2))
    bottom = flag.crop((0, flag.height - 3, W, flag.height - 2)).resize((W, H - y - flag.height + 2))
    face.paste(top, (0, 0))
    face.paste(bottom, (0, y + flag.height - 2))
    face.paste(flag.crop((0, 2, W, flag.height - 2)), (0, y + 2))
    return face


def stretch(code, emblem=None):
    """The flag stretched to the face; `emblem` (x0, y0, x1, y1 in parts of the flag) is drawn again at
    its own proportions, as large as the stretch allows within the width."""
    face = render(code, W, H)
    if emblem is None:
        return face
    flag = render(code, W * 2)  # the emblem from a sharp copy at its own proportions
    x0, y0, x1, y1 = emblem
    box = flag.crop((round(x0 * flag.width), round(y0 * flag.height), round(x1 * flag.width), round(y1 * flag.height)))
    cx, cy = (x0 + x1) / 2 * W, (y0 + y1) / 2 * H
    # the emblem keeps the height it has in its stripe, unless it would then leave the face (a margin
    # of 1/20 of the width on the nearer side)
    k = min(H * (y1 - y0) / box.height, 2 * (min(cx, W - cx) - W / 20) / box.width)
    box = box.resize((round(box.width * k), round(box.height * k)), Image.LANCZOS)
    face.alpha_composite(box, (round(cx - box.width / 2), round(cy - box.height / 2)))
    return face


def star(d, cx, cy, r, fill):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        rr = r if i % 2 == 0 else r * 0.381966  # a regular five-pointed star
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=fill)


def usa():
    """Flag of the United States (Executive Order 10834): 13 stripes, the canton over the top 7 and
    0.76 of the hoist wide, 50 stars in 9 rows of 6 and 5. Stretched to the face, stars kept round."""
    red, white, blue = (0xB3, 0x19, 0x42), (255, 255, 255), (0x0A, 0x31, 0x61)
    w, h = W * SUPER, H * SUPER
    img = Image.new('RGBA', (w, h), red + (255,))
    d = ImageDraw.Draw(img)
    stripe = h / 13
    for i in range(1, 13, 2):
        d.rectangle([0, round(i * stripe), w, round((i + 1) * stripe) - 1], fill=white)
    # the canton keeps its share of the width (0.76 hoist on a 1.9 fly = 40 %)
    cw, ch = w * 0.76 / 1.9, stripe * 7
    d.rectangle([0, 0, round(cw) - 1, round(ch) - 1], fill=blue)
    # stars: E = F = 0.054 and G = H = 0.063 of the hoist, diameter K = 0.0616, here in parts of the canton
    r = min(cw / 12 * 0.0616 / 0.063, ch / 10 * 0.0616 / 0.054) / 2 * 1.0
    for row in range(9):
        for col in range(11):
            if (row + col) % 2:
                continue
            star(d, cw * (col + 1) / 12, ch * (row + 1) / 10, r, white)
    return img.resize((W, H), Image.LANCZOS)


def uk():
    """The Union Jack from its construction sheet, at the face's proportions: St George's cross 1/5 of
    the shorter side with 1/15 of white on each side, the saltires 1/5 wide (St Andrew's white,
    St Patrick's red 1/15, set off clockwise)."""
    blue, red, white = (0x01, 0x21, 0x69), (0xC8, 0x10, 0x2E), (255, 255, 255)
    w, h = W * SUPER, H * SUPER
    u = min(w, h) / 30  # the sheet's unit: the flag is 30 units on its shorter side
    img = Image.new('RGBA', (w, h), blue + (255,))
    d = ImageDraw.Draw(img)
    cx, cy = w / 2, h / 2

    def beyond(p, q, by):
        """p moved `by` pixels away from q, along their line (past a corner, so no notch shows)."""
        dx, dy = p[0] - q[0], p[1] - q[1]
        n = math.hypot(dx, dy)
        return p[0] + dx / n * by, p[1] + dy / n * by

    corners = ((0, 0), (w, 0), (w, h), (0, h))
    # St Andrew's saltire, white, 6 units wide
    for a in corners:
        d.line([beyond(a, (cx, cy), 6 * u), (cx, cy)], fill=white, width=round(6 * u))
    # St Patrick's saltire, red, 2 units beside the centre line of St Andrew's, turned clockwise: in the
    # top left quarter below the line, in the top right above it, and so on around
    for a in corners:
        far = beyond(a, (cx, cy), 6 * u)
        dx, dy = cx - a[0], cy - a[1]
        n = math.hypot(dx, dy)
        # on the side the flag turns towards going clockwise: from the top left corner, below the line
        ox, oy = -dy / n * 2 * u, dx / n * 2 * u
        d.polygon([far, (cx, cy), (cx + ox, cy + oy), (far[0] + ox, far[1] + oy)], fill=red)
    # St George's cross with its white border: 10 units white, 6 red
    d.rectangle([cx - 5 * u, 0, cx + 5 * u, h], fill=white)
    d.rectangle([0, cy - 5 * u, w, cy + 5 * u], fill=white)
    d.rectangle([cx - 3 * u, 0, cx + 3 * u, h], fill=red)
    d.rectangle([0, cy - 3 * u, w, cy + 3 * u], fill=red)
    return img.resize((W, H), Image.LANCZOS)


# code, edge colour (top, sides, inside), how it is fitted
FLAG_CAPES = (
    ('fr', (0x00, 0x23, 0x95), lambda: extend('fr')),
    ('be', (0x00, 0x00, 0x00), lambda: extend('be')),
    ('ch', (0xDA, 0x29, 0x1C), lambda: extend('ch')),
    ('ca', (0xD8, 0x06, 0x21), lambda: extend('ca')),
    ('ma', (0xC1, 0x27, 0x2D), lambda: extend('ma')),
    ('dz', (0x00, 0x62, 0x33), lambda: extend('dz')),
    ('tn', (0xE7, 0x00, 0x13), lambda: extend('tn')),
    ('us', (0x0A, 0x31, 0x61), usa),
    ('gb', (0x01, 0x21, 0x69), uk),
    ('de', (0x00, 0x00, 0x00), lambda: stretch('de')),
    ('es', (0xAD, 0x15, 0x19), None),  # spain(), below
    ('it', (0x00, 0x92, 0x46), lambda: extend('it')),
    ('pt', (0x04, 0x6A, 0x38), lambda: extend('pt')),
    ('br', (0x00, 0x9B, 0x3A), lambda: extend('br')),
    ('tr', (0xE3, 0x0A, 0x17), lambda: extend('tr')),
)


def spain():
    """Spain: the stripes (1:2:1) made taller, the coat of arms found on the flag and kept at its own
    proportions, at its place (its centre half the hoist from the hoist)."""
    flag = np.asarray(render('es', 1500))
    yellow = np.array([0xFA, 0xBD, 0x00])
    band = flag[flag.shape[0] // 4 + 4:flag.shape[0] * 3 // 4 - 4, :, :3].astype(int)
    off = np.abs(band - yellow).sum(axis=2) > 40
    ys, xs = np.nonzero(off)
    top = flag.shape[0] // 4 + 4
    x0, x1 = xs.min() - 4, xs.max() + 5
    y0, y1 = ys.min() + top - 4, ys.max() + top + 5
    return stretch('es', emblem=(x0 / flag.shape[1], y0 / flag.shape[0], x1 / flag.shape[1], y1 / flag.shape[0]))


FLAG_CAPES = tuple((c, e, spain) if c == 'es' else (c, e, f) for c, e, f in FLAG_CAPES)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--preview', help='also save the faces side by side to this file')
    p.add_argument('--only', help='comma-separated codes to draw')
    a = p.parse_args()
    only = set(a.only.split(',')) if a.only else None
    faces = []
    for code, edge, draw in FLAG_CAPES:
        if only and code not in only:
            continue
        face = draw()
        capes.cape(face, edge, s=S).save(os.path.join(capes.OUT, 'flag_' + code + '.png'), optimize=True)
        faces.append(face)
        print('flag_' + code)
    if a.preview:
        small = [f.resize((W // 4, H // 4), Image.LANCZOS) for f in faces]
        sheet = Image.new('RGBA', (len(small) * (W // 4 + 12) + 12, H // 4 + 24), (40, 40, 46, 255))
        for i, f in enumerate(small):
            sheet.alpha_composite(f, (12 + i * (W // 4 + 12), 12))
        sheet.save(a.preview)


if __name__ == '__main__':
    main()
