"""Draws Rago's HD capes into assets/rago/textures/capes: 32 times the 64 x 32 layout (2048 x 1024),
an outer face of 320 x 512. The game shows them smooth (filtered, with mipmaps: gg.rago.render.HdTexture).

  black_cat  the Discord picture of Rago's creator, a black cat, in a fine silver frame
  pixel_cat  a black cat drawn in Minecraft's pixel style (Rago's own drawing)
  night_cat  a black cat on the roofs under the moon (a picture from Rago's creator)
  qrago      a photo of Rago's creator

The three made from pictures need them (they are not kept in the repository):
  python3 tools/hd_capes.py --discord cat.png --night night.png --photo me.jpg
Any left out keeps the cape already drawn; the pixel cat is always drawn.
"""
import argparse
import os
import random

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

import capes

S = 32
W, H = 10 * S, 16 * S  # the outer face of the cape, 320 x 512
OUT = capes.OUT
FONTS = os.path.join(capes.RES, 'fonts')


def fit(img, box):
    """{@code img} cropped to {@code box} (left, top, right, bottom, in its pixels) and resized to the face."""
    return img.crop(box).resize((W, H), Image.LANCZOS, reducing_gap=3.0)


def sharpen(img, amount=70):
    return img.filter(ImageFilter.UnsharpMask(radius=1.2, percent=amount, threshold=2))


# ---- black_cat: the Discord picture ------------------------------------------------------------

def black_cat(src):
    """The cat's face over the width of the cape, its round edge melted into the black, with a fine
    silver frame, the R logo above and the creator's name below."""
    face = capes.vertical((13, 13, 16), (5, 5, 6), W, H)
    pic = src.convert('RGBA')
    size = 372  # a little wider than the cape: the round edge falls off its sides
    pic = pic.resize((size, size), Image.LANCZOS, reducing_gap=3.0)
    # the picture is round: fade its edge into the black around it
    mask = Image.new('L', (size, size))
    ImageDraw.Draw(mask).ellipse([size * 0.07, size * 0.07, size * 0.93, size * 0.93], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(size * 0.045))
    pic.putalpha(mask)
    top = 50
    face.alpha_composite(pic, ((W - size) // 2, top))

    silver = (206, 207, 216)
    logo = capes.logo(30, silver, (120, 120, 140))
    face.alpha_composite(logo, ((W - logo.width) // 2, 34 - logo.height // 2))
    name = 'qRago'
    font = ImageFont.truetype(os.path.join(FONTS, 'poppins-500.ttf'), 30)
    spaced(face, name, 446, font, silver + (255,), 3)
    d = ImageDraw.Draw(face)
    # two hairlines around the name, and a small diamond
    for x0, x1 in ((38, 104), (W - 104, W - 38)):
        d.line([(x0, 447), (x1, 447)], fill=silver + (150,), width=1)
    cx, cy = W // 2, 486
    d.polygon([(cx, cy - 5), (cx + 5, cy), (cx, cy + 5), (cx - 5, cy)], fill=silver + (200,))
    # the frame: a line near the edge and a finer one inside it
    frame(face, 9, silver + (170,), 2)
    frame(face, 15, silver + (70,), 1)
    return face, (24, 24, 28)


def spaced(face, s, cy, font, colour, gap):
    """Text with letters a little apart, centred on the face."""
    d = ImageDraw.Draw(face)
    widths = [d.textlength(c, font=font) for c in s]
    total = sum(widths) + gap * (len(s) - 1)
    box = d.textbbox((0, 0), s, font=font)
    x = (W - total) / 2
    y = cy - (box[3] - box[1]) / 2 - box[1]
    for c, w in zip(s, widths):
        d.text((x, y), c, font=font, fill=colour)
        x += w + gap


def frame(face, inset, colour, width):
    layer = Image.new('RGBA', face.size)
    ImageDraw.Draw(layer).rounded_rectangle([inset, inset, W - 1 - inset, H - 1 - inset], radius=10, outline=colour,
                                            width=width)
    face.alpha_composite(layer)


# ---- pixel_cat: Rago's own pixel drawing -------------------------------------------------------

# 20 x 32 squares (16 texture pixels each): a black cat sitting, seen from the front, filling the cape.
# . night sky  * star  K fur  P inner ear  E eye  e dark of the eye  B pupil  W eye light
# m muzzle  N nose  n its shadow  M mouth  w whisker  R collar  r collar shadow  O bell  o bell shadow  L bell light
# h paw edge  j paw
PIXEL_CAT = [
    '....................',
    '.KK...........*..KK.',
    '.KKK............KKK.',
    '.KPKK..........KKPK.',
    '.KPPKK..*.....KKPPK.',
    'KKPPPKK......KKPPPKK',
    'KKPPPKKKKKKKKKKPPPKK',
    'KKKPKKKKKKKKKKKKPKKK',
    'KKKKKKKKKKKKKKKKKKKK',
    'KKKKKKKKKKKKKKKKKKKK',
    'KKKEEEKKKKKKKKEEEKKK',
    'KKEWEEEKKKKKKEWEEEKK',
    'KKEEBEEKKKKKKEEBEEKK',
    'KKEEBEEKKKKKKEEBEEKK',
    'KKeEBEeKKKKKKeEBEeKK',
    'KKKeeeKKKKKKKKeeeKKK',
    'KKKKKKKmmNNmmKKKKKKK',
    'KwwwwKmmmnnmmmKwwwwK',
    'KKKKKKmmmMMmmmKKKKKK',
    'KwwwwKmmMmmMmmKwwwwK',
    'KKKKKKKmmmmmmKKKKKKK',
    'RRRRRRRRRRRRRRRRRRRR',
    'rrrrrrrrrOOrrrrrrrrr',
    'KKKKKKKKOLOoKKKKKKKK',
    'KKKKKKKKOMMoKKKKKKKK',
    'KKKKKKKKKooKKKKKKKKK',
    'KKKKKKKKKKKKKKKKKKKK',
    'KKKKKKKKKKKKKKKKKKKK',
    'KKKhhhhhKKKKhhhhhKKK',
    'KKhjjjjjhKKhjjjjjhKK',
    'KKhjjjjjhKKhjjjjjhKK',
    'KKhjhjhjhKKhjhjhjhKK',
]

PIXEL_COLOURS = {
    '.': (36, 30, 58), '*': (246, 232, 170),
    'P': (232, 143, 166), 'E': (128, 214, 92), 'e': (78, 160, 60), 'B': (8, 8, 10), 'W': (255, 255, 255),
    'N': (242, 155, 177), 'n': (196, 108, 134), 'M': (10, 10, 13), 'w': (196, 196, 206),
    'R': (210, 58, 58), 'r': (150, 36, 38), 'O': (244, 197, 66), 'o': (184, 137, 42), 'L': (255, 241, 168),
    'h': (62, 61, 73), 'j': (44, 43, 52), 'm': (50, 48, 60),
}
FUR = ((23, 22, 27), (31, 30, 37), (40, 39, 48))


def pixel_cat():
    rows = PIXEL_CAT
    assert len(rows) == 32 and all(len(r) == 20 for r in rows), 'the drawing is 20 x 32 squares'
    rnd = random.Random(11)
    small = Image.new('RGBA', (20, 32))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == 'K':
                # Minecraft-style fur: a few shades at random, a little lighter on the top of the head
                k = rnd.random()
                shade = 2 if k < 0.07 + (0.08 if 6 <= y <= 9 else 0) else 1 if k < 0.35 else 0
                colour = FUR[shade]
            else:
                colour = PIXEL_COLOURS[c]
            small.putpixel((x, y), colour + (255,))
    # the sky behind the ears a little lighter at the top
    for y in range(7):
        for x in range(20):
            if rows[y][x] == '.':
                t = y / 6
                small.putpixel((x, y), tuple(round(a + (b - a) * t) for a, b in zip((52, 42, 84), (30, 25, 48))) + (255,))
    return small.resize((W, H), Image.NEAREST), FUR[0]


# ---- night_cat and qrago: pictures -------------------------------------------------------------

def night_cat(src):
    """The whole picture, cut to the cape: the moon, the cat and the roofs."""
    img = src.convert('RGB')
    w, h = img.size
    ch = round(w * H / W)
    top = max(0, min(h - ch, round(h * 0.03)))
    face = ImageEnhance.Brightness(sharpen(fit(img, (0, top, w, top + ch)), 45)).enhance(1.18).convert('RGBA')
    return face, (16, 18, 28)


def qrago(src):
    """The photo cut around the face, hair and hoodie, as sharp as it can be."""
    img = src.convert('RGB')
    w, h = img.size
    cw = round(w * 0.845)
    ch = round(cw * H / W)
    left = round(w * 0.115)
    top = round(h * 0.13)
    left = max(0, min(w - cw, left))
    top = max(0, min(h - ch, top))
    face = sharpen(fit(img, (left, top, left + cw, top + ch)), 80).convert('RGBA')
    return face, (34, 37, 42)


def save(name, face, edge):
    os.makedirs(OUT, exist_ok=True)
    capes.cape(face.convert('RGBA'), edge, s=S).save(os.path.join(OUT, name + '.png'), optimize=True)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--discord', help='the Discord picture (black_cat)')
    p.add_argument('--night', help='the cat in the night (night_cat)')
    p.add_argument('--photo', help='the photo (qrago)')
    p.add_argument('--preview', help='also save the four faces side by side to this file')
    a = p.parse_args()
    faces = []
    face, edge = pixel_cat()
    save('pixel_cat', face, edge)
    faces.append(face)
    for name, path, fn in (('black_cat', a.discord, black_cat), ('night_cat', a.night, night_cat), ('qrago', a.photo, qrago)):
        if path:
            face, edge = fn(Image.open(path))
            save(name, face, edge)
            faces.append(face)
    if a.preview:
        sheet = Image.new('RGBA', (len(faces) * (W + 20) + 20, H + 40), (40, 40, 46, 255))
        for i, f in enumerate(faces):
            sheet.alpha_composite(f, (20 + i * (W + 20), 20))
        sheet.save(a.preview)


if __name__ == '__main__':
    main()
