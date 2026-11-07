"""Draws the icons a Rago player can show after their name (catalogue "icon:..."), won with Rago Points,
into assets/rago/textures/name_icons: Rago's own drawings from plain shapes, 32 x 32, drawn 8 times
bigger and made smaller for smooth edges (shown smooth: each has a .mcmeta with blur).

  python3 tools/name_icons.py [--preview sheet.png]
"""
import argparse
import math
import os

from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'rago')
OUT = os.path.join(RES, 'textures', 'name_icons')
SIZE = 32
SUPER = 8
U = SIZE * SUPER / 16  # drawing units: the icon is 16 x 16 of them


def canvas():
    img = Image.new('RGBA', (SIZE * SUPER, SIZE * SUPER))
    return img, ImageDraw.Draw(img)


def pts(points):
    return [(x * U, y * U) for x, y in points]


def circle(d, cx, cy, r, fill):
    d.ellipse([(cx - r) * U, (cy - r) * U, (cx + r) * U, (cy + r) * U], fill=fill)


def heart():
    img, d = canvas()
    circle(d, 5.2, 6.0, 3.6, (232, 48, 72, 255))
    circle(d, 10.8, 6.0, 3.6, (232, 48, 72, 255))
    d.polygon(pts([(1.8, 7.2), (14.2, 7.2), (8, 14.6)]), fill=(232, 48, 72, 255))
    circle(d, 4.6, 5.0, 1.2, (255, 160, 172, 255))
    return img


def star_points(cx, cy, outer, inner, n=5, turn=-math.pi / 2):
    out = []
    for i in range(n * 2):
        r = outer if i % 2 == 0 else inner
        a = turn + i * math.pi / n
        out.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return out


def star():
    img, d = canvas()
    d.polygon(pts(star_points(8, 8.6, 7.6, 3.2)), fill=(214, 150, 20, 255))
    d.polygon(pts(star_points(8, 8.6, 6.0, 2.5)), fill=(255, 210, 64, 255))
    return img


def note():
    img, d = canvas()
    c = (122, 200, 255, 255)
    circle(d, 4.4, 12.4, 2.4, c)
    circle(d, 11.6, 10.8, 2.4, c)
    d.rectangle([5.6 * U, 3.0 * U, 6.8 * U, 12.4 * U], fill=c)
    d.rectangle([12.8 * U, 1.6 * U, 14.0 * U, 10.8 * U], fill=c)
    d.polygon(pts([(5.6, 3.0), (14.0, 1.4), (14.0, 4.0), (5.6, 5.6)]), fill=c)
    return img


def flame():
    img, d = canvas()

    def tongue(cx, cy, r, tip, fill):
        """A round bottom of radius r at (cx, cy) rising to a point at `tip`."""
        arc = [(cx + math.cos(math.radians(t)) * r, cy + math.sin(math.radians(t)) * r) for t in range(-20, 201, 10)]
        d.polygon(pts([tip] + arc), fill=fill)
    tongue(8, 10.2, 5.0, (9.2, 0.6), (240, 92, 24, 255))
    tongue(8.2, 11.6, 3.0, (8.8, 5.4), (255, 196, 64, 255))
    return img


def bolt():
    img, d = canvas()
    d.polygon(pts([(9.6, 0.6), (3.0, 9.0), (7.6, 9.0), (5.8, 15.4), (13.0, 6.4), (8.4, 6.4), (10.6, 0.6)]),
              fill=(255, 214, 40, 255))
    d.polygon(pts([(9.6, 0.6), (3.0, 9.0), (5.0, 9.0), (9.8, 2.2)]), fill=(255, 240, 150, 255))
    return img


def paw():
    img, d = canvas()
    c = (255, 136, 178, 255)
    d.ellipse([3.6 * U, 7.6 * U, 12.4 * U, 14.8 * U], fill=c)
    for x, y, r in ((3.2, 5.6, 1.9), (6.4, 3.2, 2.0), (9.6, 3.2, 2.0), (12.8, 5.6, 1.9)):
        circle(d, x, y, r, c)
    return img


def sword():
    img, d = canvas()
    # along the diagonal, the point at the top right
    d.polygon(pts([(14.6, 1.4), (13.8, 4.2), (6.2, 11.8), (4.2, 9.8), (11.8, 2.2)]), fill=(214, 222, 236, 255))
    d.polygon(pts([(14.6, 1.4), (12.8, 3.2), (5.2, 10.8), (4.2, 9.8), (11.8, 2.2)]), fill=(246, 248, 255, 255))
    d.polygon(pts([(2.6, 9.2), (6.8, 13.4), (5.8, 14.4), (1.6, 10.2)]), fill=(232, 182, 64, 255))
    d.polygon(pts([(4.2, 11.6), (5.4, 12.8), (2.6, 15.6), (1.4, 14.4)]), fill=(120, 72, 40, 255))
    return img


def gem():
    img, d = canvas()
    d.polygon(pts([(4.0, 2.4), (12.0, 2.4), (15.2, 6.0), (8.0, 15.2), (0.8, 6.0)]), fill=(64, 200, 240, 255))
    d.polygon(pts([(4.0, 2.4), (12.0, 2.4), (10.4, 6.0), (5.6, 6.0)]), fill=(170, 240, 255, 255))
    d.polygon(pts([(0.8, 6.0), (5.6, 6.0), (8.0, 15.2)]), fill=(40, 150, 210, 255))
    d.polygon(pts([(10.4, 6.0), (15.2, 6.0), (8.0, 15.2)]), fill=(110, 220, 250, 255))
    return img


def sakura():
    img, d = canvas()
    for k in range(5):
        a = -math.pi / 2 + k * math.tau / 5
        cx, cy = 8 + math.cos(a) * 3.8, 8 + math.sin(a) * 3.8
        circle(d, cx, cy, 3.4, (255, 176, 210, 255))
        # the notch at each petal's tip
        nx, ny = 8 + math.cos(a) * 7.0, 8 + math.sin(a) * 7.0
        circle(d, nx, ny, 0.9, (0, 0, 0, 0))
    circle(d, 8, 8, 2.0, (232, 80, 140, 255))
    circle(d, 8, 8, 1.0, (255, 226, 120, 255))
    return img


def skull():
    img, d = canvas()
    white, dark = (240, 238, 232, 255), (30, 28, 34, 255)
    circle(d, 8, 7.0, 6.2, white)
    d.rounded_rectangle([4.6 * U, 10.0 * U, 11.4 * U, 15.0 * U], radius=1.2 * U, fill=white)
    circle(d, 5.5, 7.6, 1.8, dark)
    circle(d, 10.5, 7.6, 1.8, dark)
    d.polygon(pts([(8, 9.6), (7.0, 11.4), (9.0, 11.4)]), fill=dark)
    for x in (6.4, 8.0, 9.6):
        d.rectangle([(x - 0.3) * U, 12.8 * U, (x + 0.3) * U, 15.0 * U], fill=dark)
    return img


def hourglass():
    img, d = canvas()
    gold, sand, glass = (220, 160, 52, 255), (255, 212, 110, 255), (200, 236, 255, 110)
    d.polygon(pts([(3.4, 2.2), (12.6, 2.2), (8.6, 8.0), (12.6, 13.8), (3.4, 13.8), (7.4, 8.0)]), fill=glass)
    d.polygon(pts([(5.2, 4.2), (10.8, 4.2), (8, 7.6)]), fill=sand)
    d.polygon(pts([(8, 10.4), (11.4, 13.4), (4.6, 13.4)]), fill=sand)
    d.rectangle([7.7 * U, 7.6 * U, 8.3 * U, 10.6 * U], fill=sand)
    d.rounded_rectangle([2.0 * U, 0.6 * U, 14.0 * U, 2.4 * U], radius=0.6 * U, fill=gold)
    d.rounded_rectangle([2.0 * U, 13.6 * U, 14.0 * U, 15.4 * U], radius=0.6 * U, fill=gold)
    return img


ICONS = (('heart', heart), ('star', star), ('note', note), ('flame', flame), ('bolt', bolt), ('paw', paw),
         ('sword', sword), ('gem', gem), ('sakura', sakura), ('skull', skull), ('hourglass', hourglass))


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--preview')
    a = p.parse_args()
    os.makedirs(OUT, exist_ok=True)
    made = []
    for name, draw in ICONS:
        img = draw().resize((SIZE, SIZE), Image.LANCZOS)
        path = os.path.join(OUT, name + '.png')
        img.save(path, optimize=True)
        with open(path + '.mcmeta', 'w') as f:
            f.write('{\n  "texture": {\n    "blur": true\n  }\n}\n')
        made.append(img)
    if a.preview:
        sheet = Image.new('RGBA', (len(made) * 72 + 8, 80), (40, 40, 46, 255))
        for i, img in enumerate(made):
            sheet.alpha_composite(img.resize((64, 64), Image.LANCZOS), (8 + i * 72, 8))
        sheet.save(a.preview)


if __name__ == '__main__':
    main()
