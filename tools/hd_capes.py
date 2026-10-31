"""Draws Rago's HD capes into assets/rago/textures/capes: 32 times the 64 x 32 layout (2048 x 1024),
an outer face of 320 x 512, or 64 times (4096 x 2048, a face of 640 x 1024) for black_cat, primal and
mfga. The game shows them smooth (filtered, with mipmaps: gg.rago.render.HdTexture).

  black_cat  the Discord picture of Rago's creator, a black cat, on an all black cape under a small logo
  pixel_cat  a plain black cat's face in Minecraft's pixel style, at a cape's own 10 x 16 (Rago's own)
  night_cat  a black cat on the roofs under the moon (a picture from Rago's creator)
  qrago      a photo of Rago's creator
  primal     a photo of a meal by Rago's creator (beef tartare)
  mfga       blue, white and red with golden fleurs-de-lis (Rago's own drawing)

The ones made from pictures need them (they are not kept in the repository):
  python3 tools/hd_capes.py --discord cat.png --night night.png --photo me.jpg --meal meal.jpg
Any left out keeps the cape already drawn; pixel_cat and mfga are always drawn.
"""
import argparse
import os

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps

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


# ---- primal: a photo of a meal -----------------------------------------------------------------

def primal(src):
    """The photo cut to the cape around the plate (the jar above it, the fork beside it), its colours a
    little livelier."""
    img = ImageOps.exif_transpose(src).convert('RGB')
    w, h = img.size
    # in fractions of the photo: from just above the jar to below the board, the tartare in the middle
    ch = round(h * 0.9)
    cw = round(ch * BW / BH)
    cx = round(w * 0.383)
    left = max(0, min(w - cw, cx - cw // 2))
    top = max(0, min(h - ch, round(h * 0.017)))
    face = img.crop((left, top, left + cw, top + ch)).resize((BW, BH), Image.LANCZOS, reducing_gap=3.0)
    face = ImageEnhance.Color(sharpen(face, 45)).enhance(1.12)
    return face.convert('RGBA'), (40, 30, 22), BIG


# ---- mfga: blue, white and red with fleurs-de-lis ----------------------------------------------

FLAG = ((0, 45, 170), (250, 250, 248), (226, 18, 40))
GOLD_LIGHT = (255, 224, 120)
GOLD_DEEP = (206, 140, 30)
GOLD_EDGE = (120, 76, 10)


def bezier(p0, p1, p2, p3, n=48):
    return [((1 - t) ** 3 * p0[0] + 3 * (1 - t) ** 2 * t * p1[0] + 3 * (1 - t) * t * t * p2[0] + t ** 3 * p3[0],
             (1 - t) ** 3 * p0[1] + 3 * (1 - t) ** 2 * t * p1[1] + 3 * (1 - t) * t * t * p2[1] + t ** 3 * p3[1])
            for t in (i / n for i in range(n + 1))]


def outline(start, segments):
    """Points along cubic curves (three points each) or straight lines (one point) from {@code start}."""
    pts = [start]
    for seg in segments:
        pts += [seg[0]] if len(seg) == 1 else bezier(pts[-1], *seg)[1:]
    return pts


def mirrored(pts):
    return [(100 - x, y) for x, y in pts]


# A fleur-de-lis on a 100 x 116 grid, its middle at x 50: the tall middle petal, the two side petals
# curling down, the band across them and the three small ends below it.
FLEUR_MIDDLE = outline((43, 78), [((33, 60), (35, 26), (50, 2)), ((65, 26), (67, 60), (57, 78))])
FLEUR_SIDE = outline((42, 68), [((38, 46), (30, 28), (17, 28)), ((7, 28), (1, 38), (2, 47)), ((3, 56), (9, 62), (13, 64)),
                                ((10, 57), (12, 47), (19, 46)), ((27, 45), (32, 60), (33, 80)), [(42, 80)]])
FLEUR_FOOT = outline((44, 89), [((45, 96), (47, 102), (50, 108)), ((53, 102), (55, 96), (56, 89))])
FLEUR_FOOT_SIDE = outline((44, 89), [((36, 89), (25, 90), (20, 97)), ((16, 103), (19, 110), (26, 109)),
                                     ((22, 105), (25, 100), (31, 99)), ((36, 98), (41, 95), (45, 93))])
# the corner between the middle petal and each side petal, above the band
FLEUR_CORNER = [(37, 72), (43, 64), (45, 80), (36, 80)]


def fleur_mask(size):
    """The fleur-de-lis, {@code size} pixels wide, as a mask (drawn bigger, then made smaller: smooth)."""
    k = size * 4 / 100
    mask = Image.new('L', (round(100 * k), round(118 * k)))
    d = ImageDraw.Draw(mask)
    for shape in (FLEUR_MIDDLE, FLEUR_SIDE, mirrored(FLEUR_SIDE), FLEUR_CORNER, mirrored(FLEUR_CORNER), FLEUR_FOOT,
                  FLEUR_FOOT_SIDE, mirrored(FLEUR_FOOT_SIDE)):
        d.polygon([(x * k, y * k) for x, y in shape], fill=255)
    d.rounded_rectangle([28 * k, 78 * k, 72 * k, 89 * k], radius=3 * k, fill=255)
    return mask.resize((size, round(size * 1.18)), Image.LANCZOS)


def golden_fleur(size):
    """A golden fleur-de-lis: light at the top, deeper below, a dark gold edge and a soft shadow."""
    mask = fleur_mask(size)
    w, h = mask.size
    pad = max(4, size // 12)
    out = Image.new('RGBA', (w + 2 * pad, h + 2 * pad))
    shadow = Image.new('L', out.size)
    shadow.paste(mask, (pad + size // 40, pad + size // 25))
    shadow = shadow.filter(ImageFilter.GaussianBlur(size / 40)).point(lambda v: v * 70 // 255)
    out.paste(Image.new('RGBA', out.size, (0, 0, 0, 255)), (0, 0), shadow)
    edge = Image.new('L', out.size)
    edge.paste(mask, (pad, pad))
    edge = edge.filter(ImageFilter.MaxFilter(3 if size < 160 else 5))
    out.alpha_composite(Image.composite(Image.new('RGBA', out.size, GOLD_EDGE + (255,)), Image.new('RGBA', out.size), edge))
    gold = capes.vertical(GOLD_LIGHT, GOLD_DEEP, w, h).convert('RGBA')
    gold.putalpha(mask)
    out.alpha_composite(gold, (pad, pad))
    # the band's own edge, over the petals it binds (drawn bigger, then made smaller: smooth)
    k = size / 100 * 4
    band = Image.new('RGBA', (out.width * 4, out.height * 4))
    ImageDraw.Draw(band).rounded_rectangle([pad * 4 + 28 * k, pad * 4 + 78 * k, pad * 4 + 72 * k, pad * 4 + 89 * k],
                                           radius=3 * k, outline=GOLD_EDGE + (255,), width=max(4, round(k * 0.9)))
    out.alpha_composite(band.resize(out.size, Image.LANCZOS))
    return out


def mfga():
    """The three bands, blue, white and red, sown with golden fleurs-de-lis: three across, five down."""
    face = Image.new('RGBA', (BW, BH))
    d = ImageDraw.Draw(face)
    for i, colour in enumerate(FLAG):
        d.rectangle([round(i * BW / 3), 0, round((i + 1) * BW / 3) - 1, BH - 1], fill=colour + (255,))
    fleur = golden_fleur(round(BW / 3 * 0.7))
    rows = 5
    for row in range(rows):
        for col in range(3):
            cx = (col + 0.5) * BW / 3
            cy = (row + 0.5) * BH / rows
            face.alpha_composite(fleur, (round(cx - fleur.width / 2), round(cy - fleur.height / 2)))
    return face, (14, 26, 96), BIG


def save(name, face, edge, s):
    os.makedirs(OUT, exist_ok=True)
    capes.cape(face.convert('RGBA'), edge, s=s).save(os.path.join(OUT, name + '.png'), optimize=True)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--discord', help='the Discord picture (black_cat)')
    p.add_argument('--night', help='the cat in the night (night_cat)')
    p.add_argument('--photo', help='the photo (qrago)')
    p.add_argument('--meal', help='the photo of the meal (primal)')
    p.add_argument('--preview', help='also save the faces drawn side by side to this file')
    a = p.parse_args()
    faces = []
    for name, fn in (('pixel_cat', pixel_cat), ('mfga', mfga)):
        face, edge, s = fn()
        save(name, face, edge, s)
        faces.append(face.resize((W, H), Image.LANCZOS))
    for name, path, fn in (('black_cat', a.discord, black_cat), ('night_cat', a.night, night_cat), ('qrago', a.photo, qrago),
                           ('primal', a.meal, primal)):
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
