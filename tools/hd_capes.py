"""Draws Rago's HD capes into assets/rago/textures/capes: 32 times the 64 x 32 layout (2048 x 1024),
an outer face of 320 x 512, or 64 times for black_cat. The game shows them smooth (filtered, with
mipmaps: gg.rago.render.HdTexture).

  black_cat  the Discord picture of Rago's creator, a black cat, on an all black cape under a small logo
  pixel_cat  a plain black cat's face in Minecraft's pixel style, at a cape's own 10 x 16 (Rago's own)
  night_cat  a black cat on the roofs under the moon (a picture from Rago's creator)
  qrago      a photo of Rago's creator

The three made from pictures need them (they are not kept in the repository):
  python3 tools/hd_capes.py --discord cat.png --night night.png --photo me.jpg
Any left out keeps the cape already drawn; the pixel cat is always drawn.
"""
import argparse
import os

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

import capes

S = 32
W, H = 10 * S, 16 * S  # the outer face of the cape, 320 x 512
OUT = capes.OUT


def fit(img, box):
    """{@code img} cropped to {@code box} (left, top, right, bottom, in its pixels) and resized to the face."""
    return img.crop(box).resize((W, H), Image.LANCZOS, reducing_gap=3.0)


def sharpen(img, amount=70):
    return img.filter(ImageFilter.UnsharpMask(radius=1.2, percent=amount, threshold=2))


# ---- black_cat: the Discord picture ------------------------------------------------------------

# The Discord picture is 1024 px wide: this cape is drawn at 64 times the layout (4096 x 2048, an outer
# face of 640 x 1024) so the picture keeps most of its pixels.
BIG = 64
BW, BH = 10 * BIG, 16 * BIG


def black_cat(src):
    """The cat's face over the width of an all black cape, its round edge melted into the black, and the
    R logo, small and white, above it."""
    face = Image.new('RGBA', (BW, BH), (0, 0, 0, 255))
    pic = src.convert('RGBA')
    size = round(BW * 1.16)  # a little wider than the cape: the round edge falls off its sides
    pic = pic.resize((size, size), Image.LANCZOS, reducing_gap=3.0)
    pic = sharpen(pic, 35)
    # the picture is round and black around the cat: fade its edge into the cape's black
    mask = Image.new('L', (size, size))
    ImageDraw.Draw(mask).ellipse([size * 0.05, size * 0.05, size * 0.95, size * 0.95], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(size * 0.03))
    alpha = pic.split()[3].point(lambda v: v)
    pic.putalpha(Image.composite(alpha, Image.new('L', (size, size)), mask))
    face.alpha_composite(pic, ((BW - size) // 2, round(BH * 0.1)))
    logo = capes.logo(round(BW * 0.09), (255, 255, 255))
    face.alpha_composite(logo, ((BW - logo.width) // 2, round(BH * 0.066) - logo.height // 2))
    return face, (6, 6, 7), BIG


# ---- pixel_cat: Rago's own pixel drawing -------------------------------------------------------

# 10 x 16 squares, a Minecraft cape's own size (32 texture pixels each): a black cat's face, kept
# plain. K fur  P inside of the ears and nose  Y eye  B pupil
PIXEL_CAT = [
    'KKKKKKKKKK',
    'KPKKKKKKPK',
    'KPPKKKKPPK',
    'KKKKKKKKKK',
    'KYYYKKYYYK',
    'KYBYKKYBYK',
    'KYYYKKYYYK',
    'KKKKKKKKKK',
    'KKKKPPKKKK',
    'KKKKKKKKKK',
    'KKKKKKKKKK',
    'KKKKKKKKKK',
    'KKKKKKKKKK',
    'KKKKKKKKKK',
    'KKKKKKKKKK',
    'KKKKKKKKKK',
]

PIXEL_COLOURS = {'K': (14, 14, 17), 'P': (184, 100, 126), 'Y': (240, 194, 58), 'B': (5, 5, 7)}


def pixel_cat():
    rows = PIXEL_CAT
    assert len(rows) == 16 and all(len(r) == 10 for r in rows), 'the drawing is 10 x 16 squares'
    small = Image.new('RGBA', (10, 16))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            small.putpixel((x, y), PIXEL_COLOURS[c] + (255,))
    return small.resize((W, H), Image.NEAREST), PIXEL_COLOURS['K'], S


# ---- night_cat and qrago: pictures -------------------------------------------------------------

def night_cat(src):
    """The whole picture, cut to the cape: the moon, the cat and the roofs."""
    img = src.convert('RGB')
    w, h = img.size
    ch = round(w * H / W)
    top = max(0, min(h - ch, round(h * 0.03)))
    face = ImageEnhance.Brightness(sharpen(fit(img, (0, top, w, top + ch)), 45)).enhance(1.18).convert('RGBA')
    return face, (16, 18, 28), S


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
    return face, (34, 37, 42), S


def save(name, face, edge, s):
    os.makedirs(OUT, exist_ok=True)
    capes.cape(face.convert('RGBA'), edge, s=s).save(os.path.join(OUT, name + '.png'), optimize=True)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--discord', help='the Discord picture (black_cat)')
    p.add_argument('--night', help='the cat in the night (night_cat)')
    p.add_argument('--photo', help='the photo (qrago)')
    p.add_argument('--preview', help='also save the four faces side by side to this file')
    a = p.parse_args()
    faces = []
    face, edge, s = pixel_cat()
    save('pixel_cat', face, edge, s)
    faces.append(face)
    for name, path, fn in (('black_cat', a.discord, black_cat), ('night_cat', a.night, night_cat), ('qrago', a.photo, qrago)):
        if path:
            face, edge, s = fn(Image.open(path))
            save(name, face, edge, s)
            faces.append(face.resize((W, H), Image.LANCZOS))
    if a.preview:
        sheet = Image.new('RGBA', (len(faces) * (W + 20) + 20, H + 40), (40, 40, 46, 255))
        for i, f in enumerate(faces):
            sheet.alpha_composite(f, (20 + i * (W + 20), 20))
        sheet.save(a.preview)


if __name__ == '__main__':
    main()
