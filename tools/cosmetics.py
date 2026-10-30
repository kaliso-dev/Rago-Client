"""Draws the pixel-art textures of Rago's cosmetics into assets/rago/textures/cosmetics.

Every texture is Rago's own, drawn here from shapes. Wings are grey so the colour chosen in game
tints them, and tools/models.py extrudes them into voxel models; run this first, then that, after
changing a shape.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'rago', 'textures', 'cosmetics')
N = 64


def grey(v, a=255):
    return (v, v, v, a)


WING_SCALE = 4
"""The wings are drawn on a 64 x 64 grid, each cell WING_SCALE pixels, so their curves are smooth in game."""


def quad_curve(p0, p1, p2, steps=24):
    """Points of the quadratic curve from p0 to p2 pulled toward p1."""
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in (i / steps for i in range(steps + 1))]


def tapered(d, a, b, wa, wb, fill):
    """A bone from a to b, wa pixels wide at a and wb at b, with round ends."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    length = math.hypot(dx, dy) or 1
    nx, ny = -dy / length, dx / length
    d.polygon([(a[0] + nx * wa / 2, a[1] + ny * wa / 2), (b[0] + nx * wb / 2, b[1] + ny * wb / 2),
               (b[0] - nx * wb / 2, b[1] - ny * wb / 2), (a[0] - nx * wa / 2, a[1] - ny * wa / 2)], fill=fill)
    for c, w in ((a, wa), (b, wb)):
        d.ellipse([c[0] - w / 2, c[1] - w / 2, c[0] + w / 2, c[1] + w / 2], fill=fill)


def bat_wing(joint, tips, top, root_low, bone=46, membrane_seed=3):
    """
    A dragon wing on the 64 x 64 grid: an arm bone from the root to the joint, tapered finger bones
    to each tip, a membrane of fine scales sagging in curves between the fingers, and claws on the
    joint and the tips. Grey, so the colour chosen in game tints it. Returns the image and the set of
    bone pixels, which the model makes thicker than the membrane.
    """
    k = WING_SCALE
    n = N * k

    def at(p):
        return (p[0] * k + k / 2, p[1] * k + k / 2)
    J, T, TOP = at(joint), [at(t) for t in tips], [at(p) for p in top]
    outline = TOP + [J, T[0]]
    sags = []
    for i in range(1, len(T)):
        a, b = T[i - 1], T[i]
        # the membrane sags between two finger tips, through the old notch point, in a smooth curve
        mid = ((a[0] + b[0]) / 2 - 6 * k, (a[1] + b[1]) / 2 - 6 * k)
        pull = (2 * mid[0] - (a[0] + b[0]) / 2, 2 * mid[1] - (a[1] + b[1]) / 2)
        curve = quad_curve(a, pull, b)
        sags.append(curve)
        outline += curve[1:]
    outline += [at(root_low)]
    shape = Image.new('L', (n, n))
    ImageDraw.Draw(shape).polygon(outline, fill=255)
    img = Image.new('RGBA', (n, n))
    rnd = random.Random(membrane_seed)
    cell = 3 * k
    for y in range(n):
        for x in range(n):
            if shape.getpixel((x, y)) <= 127:
                continue
            # rows of rounded scales, each darker along its lower rim
            row = y // cell
            sx = ((x + (cell // 2 if row % 2 else 0)) % cell + 0.5) / cell
            sy = (y % cell + 0.5) / cell
            r = math.hypot(sx - 0.5, sy - 0.1)
            v = 190 - sy * 34 + rnd.randint(-2, 2)
            if 0.5 < r < 0.62 and sy > 0.3:
                v = 132
            img.putpixel((x, y), grey(int(v)))
    d = ImageDraw.Draw(img)
    # the trailing edge, a little darker
    edge = Image.new('L', (n, n))
    for curve in sags + [[T[-1], at(root_low)]]:
        ImageDraw.Draw(edge).line(curve, fill=255, width=round(1.5 * k), joint='curve')
    for y in range(n):
        for x in range(n):
            if edge.getpixel((x, y)) and shape.getpixel((x, y)) > 127:
                img.putpixel((x, y), grey(112))
    # the bones: the arm thick from the root to the joint, the fingers thinning toward their tips
    bones = Image.new('L', (n, n))
    db = ImageDraw.Draw(bones)
    arm = TOP + [J]
    for i in range(1, len(arm)):
        for target, fill in ((d, grey(bone - 8)), (db, 255)):
            tapered(target, arm[i - 1], arm[i], 2.8 * k, 2.6 * k, fill)
    for t in T:
        for target, fill in ((d, grey(bone)), (db, 255)):
            tapered(target, J, t, 2.2 * k, 1.1 * k, fill)
    # a lighter ridge along each finger, knuckles along the arm
    for t in T:
        tapered(d, J, t, 0.7 * k, 0.3 * k, grey(bone + 30))
    for p in TOP[1:]:
        d.ellipse([p[0] - 0.9 * k, p[1] - 0.9 * k, p[0] + 0.9 * k, p[1] + 0.9 * k], fill=grey(bone + 40))
    # claws: a hook on the joint, a point past each finger tip
    d.polygon([(J[0] - 1 * k, J[1]), (J[0] + 2 * k, J[1] - 5 * k), (J[0] + 3 * k, J[1] + 1 * k)], fill=grey(240))
    for t in T[:-1]:
        dx, dy = t[0] - J[0], t[1] - J[1]
        length = math.hypot(dx, dy)
        ux, uy = dx / length, dy / length
        tip = (t[0] + ux * 1.6 * k, t[1] + uy * 1.6 * k)
        d.polygon([(t[0] - uy * 0.6 * k, t[1] + ux * 0.6 * k), tip, (t[0] + uy * 0.6 * k, t[1] - ux * 0.6 * k)], fill=grey(235))
    bone_set = {(x, y) for y in range(n) for x in range(n) if bones.getpixel((x, y)) > 127 and img.getpixel((x, y))[3]}
    return img, bone_set


def dragon_parts():
    """Ender-dragon-like wing: long bones, scaled membrane, scalloped trailing edge. Root at (3, 12)."""
    return bat_wing((40, 5), [(63, 14), (60, 40), (46, 54), (28, 58), (10, 48)], [(2, 10), (20, 5)], (3, 30))


def dragon_small_parts():
    """
    A short dragon wing, as compact as Lunar Client's: the arm bone rising from the root, three
    fingers, the membrane scaled. Root at (3, 22); the model is drawn smaller in game. Coloured in
    Rago's neon look, the Rago cape's black and red: a black membrane with red bones and edge (drawn
    untinted).
    """
    wing, bones = bat_wing((34, 8), [(60, 16), (52, 34), (34, 42), (16, 38)], [(2, 20), (16, 12)], (3, 32), membrane_seed=5)
    return neon(wing, bones), bones


def neon(img, bones):
    """
    A grey wing in the Rago cape's red and black: the bones and the trailing edge red, the claws
    light, the membrane black with its scales just visible.
    """
    out = img.copy()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            v = r
            if v >= 230:
                c = (255, 96, 84)
            elif (x, y) in bones or v == 112:
                c = (242, 57, 44)
            else:
                # the membrane: 132 to 190 grey becomes the cape's black, 10 to 22
                k = max(0.0, min(1.0, (v - 132) / 58))
                c = (round(10 + 12 * k), round(10 + 12 * k), round(12 + 14 * k))
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

# ---- shields: a heater shield with a rim, the R logo in the middle ---------------------------------------
# Drawn on a 40 x 48 grid, each cell SHIELD_SCALE pixels, so the edges and the logo are fine in game.

SHIELD_SCALE = 4
SHIELD_W, SHIELD_H = 40 * SHIELD_SCALE, 48 * SHIELD_SCALE
SHIELD_STYLES = {
    # name: (face top, face bottom, rim, logo)
    # the Rago shield in the Rago cape's black and red, plain like the cape
    'rago': ((22, 22, 26), (10, 10, 12), (242, 57, 44), (242, 57, 44)),
    'crimson': ((210, 48, 40), (130, 22, 20), (36, 36, 42), (255, 255, 255)),
    'gold': ((250, 246, 236), (214, 206, 190), (232, 182, 64), (242, 57, 44)),
    'frost': ((200, 232, 250), (120, 176, 220), (250, 252, 255), (32, 80, 150)),
    'void': ((22, 14, 34), (8, 4, 14), (150, 80, 230), (200, 150, 255)),
}


def shield_masks():
    """The shield's silhouette, its rim (two cells deep) and the logo, as sets of (x, y)."""
    w, h, k = SHIELD_W, SHIELD_H, SHIELD_SCALE
    outline = Image.new('L', (w, h))
    d = ImageDraw.Draw(outline)
    # straight top and sides, then two curves meeting at a point at the bottom
    def bezier(p0, p1, p2, steps=60):
        return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
                 (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in (i / steps for i in range(steps + 1))]
    side = h * 0.42
    pts = [(0.5, 0.5), (w - 0.5, 0.5), (w - 0.5, side)]
    pts += bezier((w - 0.5, side), (w - 0.5, h * 0.8), (w / 2, h - 0.5))
    pts += bezier((w / 2, h - 0.5), (0.5, h * 0.8), (0.5, side))
    d.polygon(pts, fill=255)
    shape = {(x, y) for y in range(h) for x in range(w) if outline.getpixel((x, y)) > 127}
    # the rim: within two cells of the outside, found by eroding the silhouette (padded, so the
    # image's own edge counts as outside)
    r = 2 * k
    padded = Image.new('L', (w + 2 * r, h + 2 * r))
    padded.paste(outline, (r, r))
    inner = padded.filter(ImageFilter.MinFilter(2 * r + 1))
    rim = {(x, y) for (x, y) in shape if inner.getpixel((x + r, y + r)) <= 127}
    size = 24 * k
    logo_img = Image.open(os.path.join(OUT, '..', 'logo.png')).convert('RGBA').resize((size, size), Image.LANCZOS)
    logo = {(x + 8 * k, y + 10 * k) for y in range(size) for x in range(size) if logo_img.getpixel((x, y))[3] > 127}
    return shape, rim, logo & shape - rim


def shield(style):
    top, bottom, rim_c, logo_c = SHIELD_STYLES[style]
    shape, rim, logo = shield_masks()
    k = SHIELD_SCALE
    plain = style == 'rago'
    img = Image.new('RGBA', (SHIELD_W, SHIELD_H))
    rnd = random.Random(7)
    for (x, y) in shape:
        t = y / (SHIELD_H - 1)
        c = [round(a + (b - a) * t) for a, b in zip(top, bottom)]
        if (x, y) in rim:
            c = list(rim_c)
            # a lighter inner edge and rivets on the rim
            inside = all((x + dx, y + dy) in shape for dx in (-3 * k, 3 * k) for dy in (-3 * k, 3 * k))
            if not plain and (inside or (x // k + y // k) % 9 == 0 and y < 6 * k):
                c = [min(255, v + 30) for v in c]
        elif (x, y) in logo:
            c = list(logo_c)
        elif not plain:
            n = rnd.randint(-6, 6)
            c = [max(0, min(255, v + n)) for v in c]
        img.putpixel((x, y), tuple(c) + (255,))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    dragon_parts()[0].save(os.path.join(OUT, 'dragon.png'))
    dragon_small_parts()[0].save(os.path.join(OUT, 'dragon_small.png'))
    cap().save(os.path.join(OUT, 'cap.png'))
    beanie().save(os.path.join(OUT, 'beanie.png'))
    for style in SHIELD_STYLES:
        shield(style).save(os.path.join(OUT, 'shield_' + style + '.png'))


if __name__ == '__main__':
    main()
