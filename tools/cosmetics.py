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



def butterfly():
    """Fore and hind wing as one layer, veins and border dark so the tint colours the rest."""
    img = Image.new('RGBA', (N, N))
    d = ImageDraw.Draw(img)
    fore = [(2, 26), (12, 12), (28, 4), (46, 1), (60, 3), (62, 10), (56, 20), (44, 30), (26, 34), (8, 32)]
    hind = [(4, 32), (22, 34), (38, 38), (46, 48), (42, 58), (30, 62), (16, 58), (6, 46)]
    for shape in (hind, fore):
        d.polygon(shape, fill=grey(34))
    # inner area: inset copies, light towards the edge
    def inset(shape, k, cx, cy):
        return [(cx + (x - cx) * k, cy + (y - cy) * k) for x, y in shape]
    for shape, (cx, cy) in ((fore, (8, 26)), (hind, (8, 36))):
        d.polygon(inset(shape, 0.86, cx, cy), fill=grey(225))
        d.polygon(inset(shape, 0.55, cx, cy), fill=grey(200))
        d.polygon(inset(shape, 0.3, cx, cy), fill=grey(170))
    # veins from the root
    for tx, ty in ((58, 6), (50, 16), (40, 26), (26, 32), (36, 42), (34, 56), (20, 58)):
        d.line([(5, 30), (tx, ty)], fill=grey(40), width=1)
    # white dots in the dark border
    rnd = random.Random(7)
    for shape in (fore, hind):
        for i in range(len(shape)):
            x0, y0 = shape[i]
            x1, y1 = shape[(i + 1) % len(shape)]
            if rnd.random() < 0.75 and (x0, y0) not in ((2, 26), (4, 32)):
                t = 0.5
                cx, cy = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
                # pull the dot just inside the edge
                cx += (12 - cx) * 0.06
                cy += (30 - cy) * 0.06
                d.point([(round(cx), round(cy))], fill=grey(250))
    # the body side: a dark sliver at the root
    d.line([(1, 24), (2, 40)], fill=grey(20), width=2)
    return img


def dragon():
    """Bat-like membrane between long finger bones, scalloped trailing edge. Root at (3, 12)."""
    img = Image.new('RGBA', (N, N))
    d = ImageDraw.Draw(img)
    joint = (40, 5)
    tips = [(63, 14), (60, 40), (46, 54), (28, 58), (10, 48)]
    outline = [(2, 10), (20, 5), joint, tips[0]]
    for i in range(1, len(tips)):
        a, b = tips[i - 1], tips[i]
        # the membrane sags between two bone tips
        mid = ((a[0] + b[0]) / 2 - 7, (a[1] + b[1]) / 2 - 7)
        outline += [mid, b]
    outline += [(3, 30)]
    d.polygon(outline, fill=grey(150))
    # lighter membrane near the bones, darker in between
    for t in tips:
        d.line([joint, t], fill=grey(170), width=4)
    for t in tips:
        d.line([joint, t], fill=grey(72), width=2)
    d.line([(2, 10), (20, 5), joint], fill=grey(64), width=3)
    # claw at the joint
    d.polygon([(joint[0] - 1, joint[1]), (joint[0] + 2, joint[1] - 5), (joint[0] + 3, joint[1] + 1)], fill=grey(240))
    return img


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


def main():
    os.makedirs(OUT, exist_ok=True)
    butterfly().save(os.path.join(OUT, 'butterfly.png'))
    dragon().save(os.path.join(OUT, 'dragon.png'))
    cap().save(os.path.join(OUT, 'cap.png'))
    beanie().save(os.path.join(OUT, 'beanie.png'))


if __name__ == '__main__':
    main()
