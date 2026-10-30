"""Draws the pixel-art textures of Rago's cosmetics into assets/rago/textures/cosmetics.

Every texture is Rago's own, drawn here from shapes. Wings are grey so the colour chosen in game
tints them, and tools/models.py extrudes them into voxel models; run this first, then that, after
changing a shape.
"""
import math
import os
import random

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'rago', 'textures', 'cosmetics')
N = 64


def grey(v, a=255):
    return (v, v, v, a)


def scales(img, shape_mask, seed, light=196, dark=124):
    """Overlapping scales on the membrane: little rounded cells, each darker along its lower rim."""
    rnd = random.Random(seed)
    for y in range(N):
        for x in range(N):
            if not shape_mask(x, y):
                continue
            row = y // 4
            sx = (x + (2 if row % 2 else 0)) % 4
            sy = y % 4
            # an arc: the rim runs down the sides and under the middle of each scale
            rim = (sy == 3 and sx in (1, 2)) or (sy == 2 and sx in (0, 3))
            v = dark if rim else light - sy * 12 + rnd.randint(-5, 5)
            img.putpixel((x, y), grey(v))


def bat_wing(joint, tips, top, root_low, bone=46, membrane_seed=3):
    """
    A dragon wing: an arm bone from the root to the joint, finger bones to each tip, a membrane
    with scales sagging between the fingers, and claws on the joint and the tips.
    """
    img = Image.new('RGBA', (N, N))
    d = ImageDraw.Draw(img)
    outline = top + [joint, tips[0]]
    for i in range(1, len(tips)):
        a, b = tips[i - 1], tips[i]
        # the membrane sags between two finger tips
        mid = ((a[0] + b[0]) / 2 - 6, (a[1] + b[1]) / 2 - 6)
        outline += [mid, b]
    outline += [root_low]
    mask_img = Image.new('L', (N, N))
    ImageDraw.Draw(mask_img).polygon(outline, fill=255)
    scales(img, lambda x, y: mask_img.getpixel((x, y)) > 127, membrane_seed)
    # the membrane darkens towards its trailing edge
    edge = Image.new('L', (N, N))
    ImageDraw.Draw(edge).line(outline[len(top) + 1:], fill=255, width=2)
    for y in range(N):
        for x in range(N):
            if edge.getpixel((x, y)) and mask_img.getpixel((x, y)) > 127:
                img.putpixel((x, y), grey(112))
    # finger bones with a lighter ridge, the arm bone thicker
    for t in tips:
        d.line([joint, t], fill=grey(bone), width=2)
    d.line(top + [joint], fill=grey(bone - 8), width=3)
    for t in tips:
        d.line([joint, t], fill=grey(bone + 30), width=1)
    # knuckles along the arm, claws on the joint and the finger tips
    for k in range(1, len(top)):
        d.point([top[k]], fill=grey(bone + 40))
    d.polygon([(joint[0] - 1, joint[1]), (joint[0] + 2, joint[1] - 5), (joint[0] + 3, joint[1] + 1)], fill=grey(240))
    for t in tips[:-1]:
        d.point([(min(N - 1, t[0] + 1), t[1])], fill=grey(235))
    return img


def dragon():
    """Ender-dragon-like wing: long bones, scaled membrane, scalloped trailing edge. Root at (3, 12)."""
    return bat_wing((40, 5), [(63, 14), (60, 40), (46, 54), (28, 58), (10, 48)], [(2, 10), (20, 5)], (3, 30))


def dragon_small():
    """
    A short dragon wing, as compact as Lunar Client's: the arm bone rising from the root, three
    fingers, the membrane scaled. Root at (3, 22); the model is drawn smaller in game. Coloured in
    Rago's neon look: a black membrane with glowing red bones and edge (drawn untinted).
    """
    wing = bat_wing((34, 8), [(60, 16), (52, 34), (34, 42), (16, 38)], [(2, 20), (16, 12)], (3, 32), membrane_seed=5)
    return neon(wing)


def neon(img):
    """
    A grey wing in red and black: bones (dark greys) glowing red, the trailing edge red, claws
    light, the scaled membrane almost black. Bones keep a blue of 10 so the model makes them thick.
    """
    out = img.copy()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            v = r
            if v >= 230:
                c = (255, 96, 84)
            elif v == 112:
                c = (214, 22, 30)
            elif v < 110:
                k = max(0.0, min(1.0, (v - 38) / 48))
                c = (int(150 + 105 * k), int(12 + 48 * k), 10)
            else:
                c = (int(v * 0.17), int(v * 0.10), int(v * 0.11))
            out.putpixel((x, y), c + (a,))
    return out


def box_faces(u, v, w, h, d):
    """Where each face of a w x h x d box sits in the texture, as Rago's model code reads it."""
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'west': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'east': (u + d + w, v + d, d, h),
        'back': (u + 2 * d + w, v + d, w, h),
    }


def fill(d, rect, colour):
    x, y, w, h = rect
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=colour)


def cap():
    """Baseball cap: crown 9x4x9 at (0, 0), brim 9x1x5 at (0, 14), button 1x1x1 at (40, 0)."""
    img = Image.new('RGBA', (64, 32))
    d = ImageDraw.Draw(img)
    crown = box_faces(0, 0, 9, 4, 9)
    for name, rect in crown.items():
        fill(d, rect, grey(205 if name != 'bottom' else 120))
    x, y, w, h = crown['top']
    # six panels meeting at the button: seams from the centre to the corners and edge middles
    cx, cy = x + 4, y + 4
    for ex, ey in ((x, y), (x + 8, y), (x, y + 8), (x + 8, y + 8), (x + 4, y), (x + 4, y + 8)):
        d.line([(cx, cy), (ex, ey)], fill=grey(170))
    for side in ('west', 'east', 'back', 'front'):
        x, y, w, h = crown[side]
        d.line([(x, y + h - 1), (x + w - 1, y + h - 1)], fill=grey(178))  # band at the bottom
        if side != 'front':
            d.line([(x + w // 2, y), (x + w // 2, y + h - 2)], fill=grey(180))  # seam
    x, y, w, h = crown['back']
    fill(d, (x + 3, y + 2, 3, 2), grey(90))  # the strap opening
    brim = box_faces(0, 14, 9, 1, 5)
    for name, rect in brim.items():
        fill(d, rect, grey(185 if name == 'top' else 95 if name == 'bottom' else 160))
    x, y, w, h = brim['top']
    for i in range(1, 4):
        d.line([(x, y + h - i), (x + w - 1, y + h - i)], fill=grey(175 if i % 2 else 192))
    for name, rect in box_faces(40, 0, 1, 1, 1).items():
        fill(d, rect, grey(170))
    return img


def beanie():
    """Knitted beanie: body 9x4x9 at (0, 0), folded cuff 9x2x9 at (0, 14), pompom 3x3x3 at (40, 0)."""
    img = Image.new('RGBA', (64, 32))
    d = ImageDraw.Draw(img)
    body = box_faces(0, 0, 9, 4, 9)
    for name, (x, y, w, h) in body.items():
        for i in range(w):
            for j in range(h):
                rib = 214 if (i % 2 == 0) else 190
                if name == 'top':
                    rib = 214 if ((i + j) % 3) else 192
                d.point([(x + i, y + j)], fill=grey(rib - (6 if j % 2 else 0) if name != 'top' else rib))
    cuff = box_faces(0, 14, 9, 2, 9)
    for name, (x, y, w, h) in cuff.items():
        for i in range(w):
            for j in range(h):
                d.point([(x + i, y + j)], fill=grey(236 if (i % 2) else 214))
    rnd = random.Random(3)
    for name, (x, y, w, h) in box_faces(40, 0, 3, 3, 3).items():
        for i in range(w):
            for j in range(h):
                d.point([(x + i, y + j)], fill=grey(rnd.choice((255, 240, 228))))
    return img

# ---- shields: 40 x 48, a heater shield with a rim, the R logo in the middle ---------------------------

SHIELD_W, SHIELD_H = 40, 48
SHIELD_STYLES = {
    # name: (face top, face bottom, rim, logo)
    'rago': ((44, 44, 52), (24, 24, 30), (242, 57, 44), (242, 57, 44)),
    'crimson': ((210, 48, 40), (130, 22, 20), (36, 36, 42), (255, 255, 255)),
    'gold': ((250, 246, 236), (214, 206, 190), (232, 182, 64), (242, 57, 44)),
    'frost': ((200, 232, 250), (120, 176, 220), (250, 252, 255), (32, 80, 150)),
    'void': ((22, 14, 34), (8, 4, 14), (150, 80, 230), (200, 150, 255)),
}


def shield_masks():
    """The shield's silhouette, its rim (two pixels deep) and the logo, as sets of (x, y)."""
    w, h = SHIELD_W, SHIELD_H
    outline = Image.new('L', (w, h))
    d = ImageDraw.Draw(outline)
    # straight top and sides, then two curves meeting at a point at the bottom
    def bezier(p0, p1, p2, steps=20):
        return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
                 (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in (i / steps for i in range(steps + 1))]
    side = h * 0.42
    pts = [(0.5, 0.5), (w - 0.5, 0.5), (w - 0.5, side)]
    pts += bezier((w - 0.5, side), (w - 0.5, h * 0.8), (w / 2, h - 0.5))
    pts += bezier((w / 2, h - 0.5), (0.5, h * 0.8), (0.5, side))
    d.polygon(pts, fill=255)
    shape = {(x, y) for y in range(h) for x in range(w) if outline.getpixel((x, y)) > 127}
    rim = {(x, y) for (x, y) in shape
           if any((x + dx, y + dy) not in shape for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2))}
    logo_img = Image.open(os.path.join(OUT, '..', 'logo.png')).convert('RGBA').resize((24, 24), Image.LANCZOS)
    logo = {(x + 8, y + 10) for y in range(24) for x in range(24) if logo_img.getpixel((x, y))[3] > 70}
    return shape, rim, logo & shape - rim


def shield(style):
    top, bottom, rim_c, logo_c = SHIELD_STYLES[style]
    shape, rim, logo = shield_masks()
    img = Image.new('RGBA', (SHIELD_W, SHIELD_H))
    rnd = random.Random(7)
    for (x, y) in shape:
        t = y / (SHIELD_H - 1)
        c = [round(a + (b - a) * t) for a, b in zip(top, bottom)]
        if (x, y) in rim:
            c = list(rim_c)
            # a lighter inner edge and rivets on the rim
            if all((x + dx, y + dy) in shape for dx in (-3, 3) for dy in (-3, 3)) or (x + y) % 9 == 0 and y < 6:
                c = [min(255, v + 30) for v in c]
        elif (x, y) in logo:
            c = list(logo_c)
        else:
            n = rnd.randint(-6, 6)
            c = [max(0, min(255, v + n)) for v in c]
        img.putpixel((x, y), tuple(c) + (255,))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    dragon().save(os.path.join(OUT, 'dragon.png'))
    dragon_small().save(os.path.join(OUT, 'dragon_small.png'))
    cap().save(os.path.join(OUT, 'cap.png'))
    beanie().save(os.path.join(OUT, 'beanie.png'))
    for style in SHIELD_STYLES:
        shield(style).save(os.path.join(OUT, 'shield_' + style + '.png'))


if __name__ == '__main__':
    main()
