"""Draws the wings won with Rago Points into assets/rago/textures/cosmetics, Rago's own drawings from
shapes, on the 64 x 64 grid of the small dragon wing (tools/cosmetics.py) with the root at the same
cell, so the game places them the same way. tools/models.py extrudes them into voxels; each drawing
says how thick each part is (in cells).

  angel  white feathers in three rows (coverts, secondaries, primaries) under a gold edged arm
  frost  ice shards from the root, deep blue to white, with small branches like a snowflake's
  mecha  gunmetal blades on a jointed arm, a line of cyan light along each (it glows in game)

  python3 tools/reward_wings.py   (then python3 tools/models.py)
"""
import math
import os
import random

from PIL import Image, ImageDraw

import cosmetics

K = cosmetics.WING_SCALE
N = cosmetics.N * K
OUT = cosmetics.OUT
ROOT = (3, 22)


def at(p):
    return (p[0] * K + K / 2, p[1] * K + K / 2)


def feather(d, base, tip, width, fill):
    """A feather from `base` to `tip` (cells), `width` cells wide, quickly full width, round at the tip."""
    b, t = at(base), at(tip)
    dx, dy = t[0] - b[0], t[1] - b[1]
    length = math.hypot(dx, dy) or 1
    ux, uy = dx / length, dy / length
    nx, ny = -uy, ux
    w = width * K / 2

    def half(s):
        if s < 0.12:
            return w * (s / 0.12) ** 0.5
        if s < 0.82:
            return w
        return w * math.sqrt(max(0.0, 1 - ((s - 0.82) / 0.18) ** 2))
    side = [(b[0] + ux * length * s + nx * half(s), b[1] + uy * length * s + ny * half(s)) for s in (i / 16 for i in range(17))]
    other = [(b[0] + ux * length * s - nx * half(s), b[1] + uy * length * s - ny * half(s)) for s in (i / 16 for i in range(16, -1, -1))]
    d.polygon(side + other, fill=fill)


def outlined_feather(d, base, tip, width, fill, edge, mask=None):
    """A feather with a darker rim, so overlapping feathers stay apart, and its shaft."""
    feather(d, base, tip, width + 0.6, edge)
    feather(d, base, tip, width, fill)
    if mask is not None:
        feather(mask, base, tip, width + 0.6, 255)
    feather(d, base, tip, 0.5, edge)


def thick(img, layers):
    """{(x, y): cells thick} from (mask, cells) layers, the later ones winning."""
    depth = {}
    for mask, cells in layers:
        for y in range(N):
            for x in range(N):
                if mask.getpixel((x, y)) > 127 and img.getpixel((x, y))[3] > 25:
                    depth[(x, y)] = cells
    return depth


def angel():
    img = Image.new('RGBA', (N, N))
    d = ImageDraw.Draw(img)
    covert_mask = Image.new('L', (N, N))
    arm_mask = Image.new('L', (N, N))
    arm = [ROOT, (12, 14), (22, 8), (34, 4), (42, 5)]
    rnd = random.Random(4)

    def point(t):
        """A point along the arm, t from 0 (root) to 1 (wrist)."""
        f = t * (len(arm) - 1)
        i = min(len(arm) - 2, int(f))
        u = f - i
        return (arm[i][0] + (arm[i + 1][0] - arm[i][0]) * u, arm[i][1] + (arm[i + 1][1] - arm[i][1]) * u)

    edge = (196, 202, 220, 255)
    # primaries: long, fanned from the wrist, outwards then down
    for i in range(9):
        base = point(0.7 + i * 0.035)
        a = math.radians(-22 + i * 11.5)
        length = 27 - i * 0.9
        tip = (base[0] + math.cos(a) * length, base[1] + math.sin(a) * length + 3)
        outlined_feather(d, base, tip, 6.6, (246, 247, 252, 255), edge)
    # secondaries: along the arm, down
    for i in range(10):
        base = point(0.06 + i * 0.07)
        a = math.radians(100 - i * 4.5)
        length = 19 + i * 0.9
        tip = (base[0] + math.cos(a) * length, base[1] + math.sin(a) * length)
        outlined_feather(d, base, tip, 7.4, (250, 250, 254, 255), edge)
    # coverts: short and round, two rows along the arm, a little warmer
    cd = ImageDraw.Draw(covert_mask)
    for row, (start, step, length, width) in enumerate(((0.04, 0.07, 10, 6.0), (0.06, 0.08, 6.5, 5.2))):
        for i in range(14 - row * 3):
            base = point(min(1.0, start + i * step))
            a = math.radians(78 - i * 4 - row * 8)
            tip = (base[0] + math.cos(a) * length, base[1] + math.sin(a) * length)
            colour = (255, 251, 242, 255) if row else (252, 248, 238, 255)
            outlined_feather(d, base, tip, width, colour, (214, 206, 190, 255), cd)
    # the arm, its leading edge gold
    ad = ImageDraw.Draw(arm_mask)
    for a, b in zip(arm, arm[1:]):
        cosmetics.tapered(d, at(a), at(b), 3.2 * K, 2.8 * K, (250, 246, 236, 255))
        cosmetics.tapered(ad, at(a), at(b), 3.2 * K, 2.8 * K, 255)
        cosmetics.tapered(d, (at(a)[0], at(a)[1] - 1.1 * K), (at(b)[0], at(b)[1] - 1.1 * K), 1.0 * K, 0.9 * K, (236, 192, 88, 255))
    return img, thick(img, [(Image.new('L', (N, N), 255), 1), (covert_mask, 2), (arm_mask, 3)]), None


def frost():
    img = Image.new('RGBA', (N, N))
    d = ImageDraw.Draw(img)
    main = Image.new('L', (N, N))
    md = ImageDraw.Draw(main)
    rnd = random.Random(12)
    tips = [(44, 2), (58, 10), (62, 22), (56, 34), (44, 42), (28, 44), (14, 38)]

    def shard(dr, a, b, width, colours, mask=None):
        """A crystal from a to b: a long hexagon, deep at the root, pale at the tip, a light edge."""
        pa, pb = at(a), at(b)
        dx, dy = pb[0] - pa[0], pb[1] - pa[1]
        length = math.hypot(dx, dy)
        ux, uy = dx / length, dy / length
        nx, ny = -uy, ux
        w = width * K / 2
        steps = 10
        for s in range(steps):
            t0, t1 = s / steps, (s + 1) / steps

            def half(t):
                return w * (t / 0.25 if t < 0.25 else 1 if t < 0.7 else (1 - t) / 0.3)
            quad = [(pa[0] + dx * t0 + nx * half(t0), pa[1] + dy * t0 + ny * half(t0)),
                    (pa[0] + dx * t1 + nx * half(t1), pa[1] + dy * t1 + ny * half(t1)),
                    (pa[0] + dx * t1 - nx * half(t1), pa[1] + dy * t1 - ny * half(t1)),
                    (pa[0] + dx * t0 - nx * half(t0), pa[1] + dy * t0 - ny * half(t0))]
            c = tuple(round(colours[0][i] + (colours[1][i] - colours[0][i]) * (t0 + t1) / 2) for i in range(3)) + (255,)
            dr.polygon(quad, fill=c)
            if mask is not None:
                mask.polygon(quad, fill=255)
        # the light edge, along the upper side
        dr.line([(pa[0] + nx * w * 0.3, pa[1] + ny * w * 0.3), (pb[0], pb[1])], fill=(236, 250, 255, 255), width=max(1, K // 2))

    deep, pale = (40, 96, 190), (200, 240, 255)
    for tip in tips:
        # small branches first, under the main shard
        a, b = at(ROOT), at(tip)
        for j in range(3):
            t = 0.35 + j * 0.18
            px = ROOT[0] + (tip[0] - ROOT[0]) * t
            py = ROOT[1] + (tip[1] - ROOT[1]) * t
            ang = math.atan2(tip[1] - ROOT[1], tip[0] - ROOT[0])
            for side in (-1, 1):
                if rnd.random() < 0.75:
                    length = 6 - j * 1.2
                    end = (px + math.cos(ang + side * 0.8) * length, py + math.sin(ang + side * 0.8) * length)
                    shard(d, (px, py), end, 2.2, ((90, 160, 230), pale))
    for tip in tips:
        shard(d, ROOT, tip, 5.0, (deep, pale), md)
    # the core at the root, a cluster of small crystals
    for _ in range(5):
        ang = rnd.uniform(-1.6, 1.2)
        end = (ROOT[0] + math.cos(ang) * 6, ROOT[1] + math.sin(ang) * 6)
        shard(d, ROOT, end, 4.0, ((30, 70, 160), (120, 190, 240)), md)
    return img, thick(img, [(Image.new('L', (N, N), 255), 1), (main, 2)]), None


def mecha():
    img = Image.new('RGBA', (N, N))
    glow = Image.new('RGBA', (N, N))
    d = ImageDraw.Draw(img)
    gd = ImageDraw.Draw(glow)
    arm_mask = Image.new('L', (N, N))
    ad = ImageDraw.Draw(arm_mask)
    arm = [ROOT, (20, 10), (38, 4)]
    cyan = (64, 224, 255, 255)
    # blades hanging from the arm, fanned like feathers, the longest at the wrist
    blades = [((10, 17), (8, 40)), ((16, 13), (18, 44)), ((22, 10), (28, 46)), ((28, 8), (40, 44)), ((33, 6), (52, 38)),
              ((37, 5), (61, 26)), ((40, 4), (62, 12))]
    for i, (a, b) in enumerate(blades):
        pa, pb = at(a), at(b)
        dx, dy = pb[0] - pa[0], pb[1] - pa[1]
        length = math.hypot(dx, dy)
        ux, uy = dx / length, dy / length
        nx, ny = -uy, ux
        w = 2.8 * K
        poly = [(pa[0] + nx * w, pa[1] + ny * w), (pb[0] + nx * w * 0.6 - ux * K, pb[1] + ny * w * 0.6 - uy * K),
                (pb[0] + ux * K * 1.5, pb[1] + uy * K * 1.5), (pb[0] - nx * w * 0.9, pb[1] - ny * w * 0.9),
                (pa[0] - nx * w, pa[1] - ny * w)]
        d.polygon(poly, fill=(52, 56, 68, 255))
        # a lighter bevel on one side and a dark seam on the other
        d.line([poly[0], poly[1], poly[2]], fill=(104, 110, 126, 255), width=K)
        d.line([poly[2], poly[3], poly[4]], fill=(28, 30, 38, 255), width=K)
        # the light line along the blade, also drawn alone in the glow texture
        line = [(pa[0] + ux * 3 * K, pa[1] + uy * 3 * K), (pb[0] - ux * 2 * K, pb[1] - uy * 2 * K)]
        d.line(line, fill=cyan, width=K)
        gd.line(line, fill=cyan, width=K)
    # the arm: thick gunmetal with round joints and bolts
    for a, b in zip(arm, arm[1:]):
        cosmetics.tapered(d, at(a), at(b), 4.4 * K, 3.6 * K, (78, 84, 98, 255))
        cosmetics.tapered(ad, at(a), at(b), 4.4 * K, 3.6 * K, 255)
        cosmetics.tapered(d, (at(a)[0], at(a)[1] - 1.2 * K), (at(b)[0], at(b)[1] - 1.2 * K), 1.0 * K, 0.8 * K, (140, 148, 164, 255))
    for j in arm:
        c = at(j)
        r = 2.6 * K
        d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], fill=(96, 102, 118, 255))
        ad.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], fill=255)
        r2 = 1.1 * K
        d.ellipse([c[0] - r2, c[1] - r2, c[0] + r2, c[1] + r2], fill=cyan)
        gd.ellipse([c[0] - r2, c[1] - r2, c[0] + r2, c[1] + r2], fill=cyan)
    return img, thick(img, [(Image.new('L', (N, N), 255), 2), (arm_mask, 3)]), glow


WINGS = (('angel', angel), ('frost', frost), ('mecha', mecha))


def main():
    for name, fn in WINGS:
        img, _, glow = fn()
        img.save(os.path.join(OUT, 'wings_' + name + '.png'), optimize=True)
        if glow is not None:
            glow.save(os.path.join(OUT, 'wings_' + name + '_glow.png'), optimize=True)
        print(name)


if __name__ == '__main__':
    main()
