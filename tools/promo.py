"""Draws the Rago Optimizer card's picture for the home screen (textures/promo/optimizer.png) from
the creator's Rago Optimizer banner (1920 x 1080): the logo, the name and the tagline, cut to the
card's shape (250 x 113 at 2 pixels a unit) with its top corners rounded.

  python3 tools/promo.py path/to/rago-banniere-invitation.png
"""
import os
import sys

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'rago',
                   'textures', 'promo', 'optimizer.png')
UNIT = 2                       # pixels a design unit
WIDTH, HEIGHT = 250, 113       # the picture's size on the card, in design units
RADIUS = 12                    # the card's corner radius
SUPER = 8                      # the corner mask is drawn this many times bigger: smooth edges


def main(src):
    banner = Image.open(src).convert('RGB')
    bw, bh = banner.size
    # the middle of the banner, around the logo and the tagline, in the card's proportions
    cut_w = round(bw * 0.64)
    cut_h = round(cut_w * HEIGHT / WIDTH)
    cx, cy = bw / 2, bh * 0.47
    box = (round(cx - cut_w / 2), round(cy - cut_h / 2), round(cx + cut_w / 2), round(cy + cut_h / 2))
    w, h = WIDTH * UNIT, HEIGHT * UNIT
    img = banner.crop(box).resize((w, h), Image.LANCZOS).convert('RGBA')
    # round the top corners
    r = RADIUS * UNIT * SUPER
    mask = Image.new('L', (w * SUPER, h * SUPER), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, w * SUPER - 1, h * SUPER + r], radius=r, fill=255)
    img.putalpha(mask.resize((w, h), Image.LANCZOS))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT, optimize=True)
    with open(OUT + '.mcmeta', 'w') as f:
        f.write('{\n  "texture": {\n    "blur": true\n  }\n}\n')


if __name__ == '__main__':
    main(sys.argv[1])
