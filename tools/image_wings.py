"""Rago wings from a picture of a pair of wings (made with ChatGPT, or drawn), in tools/wings, in high
definition: the right wing of the picture, with its half of the piece that joins the two in the middle,
becomes a texture of TEXTURE x TEXTURE pixels in its own colours (the picture's full detail) and a model
cut along the wing's outline, smooth to a fraction of a pixel, not in steps. Each kind of colour is as
thick as the picture's spec says, its outline smoothed too: the darker bones stand out of the membrane.
In game the other wing is the same model mirrored, the two halves of the middle piece meeting on the
spine (gg.rago.modules.Cosmetics, WING_FITS), and the texture is drawn filtered with mipmaps
(gg.rago.render.HdTexture), so it stays smooth near and far.

The picture: the pair seen from the front or the back, centred, on a plain white background.

  python3 tools/image_wings.py [NAME ...]      (all of WINGS by default; needs numpy, Pillow and
                                              mapbox_earcut: pip install mapbox_earcut)

writes assets/rago/textures/cosmetics/wings_NAME.png and assets/rago/models/cosmetics/wings_NAME.rmdl,
and pictures of the wings on a player (tools/preview.py) in build/wing-previews.
"""
import os
import sys

import mapbox_earcut
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import cosmetics  # noqa: E402
import models  # noqa: E402

SRC = os.path.join(HERE, 'wings')
# the wing's square: 64 cells as the older wings (one block at a span of 1), the root on the spine
N = cosmetics.N
# texture pixels across the square: a little more than the picture has (a cell is 11 to 13 of its
# pixels, 16 of the texture's), so none of its detail is lost
TEXTURE = 1024
# a piece or a hole smaller than this (texture pixels) is left out: specks of the background
MIN_AREA = 60
# how far the outline may stray from the traced one, in texture pixels, once simplified
TOLERANCE = 1.0

# For each picture: how many cells (1/64 block) thick each kind of colour is (the first that matches
# wins, the others are `base`), and how many cells tall the pair is in the square (`height`), or how
# many wide one wing is (`width`, for a low wide pair). The kinds: dark (black, charcoal), grey
# (white to grey, little colour), bright (strong light colours: claws, glowing parts, crystals).
# Optional: `erase`, boxes of the picture (x0, y0, x1, y1) whose glowing red marks are painted over
# with the colours around them; `variants`, to make the wing in each of NeonColors' colours, one
# texture each (wings_NAME_COLOUR.png) on the one model (wings_NAME.rmdl).
WINGS = {
    'dragon_red_black': dict(thick=[('dark', 3)], base=1.5, height=62),
    'rago_frost': dict(thick=[('grey', 3)], base=2, height=62),
    'rago_inferno': dict(thick=[('dark', 3)], base=2, height=62),
    # small and wide: one wing 62 cells wide; the mark on the middle piece painted over
    'little_wings': dict(thick=[('dark', 3)], base=1.5, width=62, erase=[(580, 585, 650, 650)], variants=True),
}


def flood(seed, allowed):
    """The part of `allowed` reached from `seed` through neighbours (up, down, left, right)."""
    out = seed & allowed
    while True:
        grown = out.copy()
        grown[1:] |= out[:-1]
        grown[:-1] |= out[1:]
        grown[:, 1:] |= out[:, :-1]
        grown[:, :-1] |= out[:, 1:]
        grown &= allowed
        if (grown == out).all():
            return out
        out = grown


def background(rgb):
    """The white around the wings: near-white pixels reached from the edge of the picture (white
    highlights inside the wings stay)."""
    white = (255 - rgb.min(2)) < 14
    edge = np.zeros_like(white)
    edge[0, :] = edge[-1, :] = edge[:, 0] = edge[:, -1] = True
    return flood(edge, white)


def kinds(rgb):
    """Each pixel's kind of colour: dark, grey, bright or none."""
    lo, hi = rgb.min(2), rgb.max(2)
    out = np.full(rgb.shape[:2], '', object)
    out[(hi > 200) & (hi - lo > 120)] = 'bright'
    out[hi - lo < 40] = 'grey'
    out[(hi < 70) & (hi - lo < 35)] = 'dark'
    return out


def box(a, r):
    """The mean of `a` over the square of side 2r+1 around each pixel (the edges padded with 0)."""
    p = np.pad(a.astype(float), r + 1)
    c = p.cumsum(0).cumsum(1)
    k = 2 * r + 1
    s = c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]
    return s[:a.shape[0], :a.shape[1]] / (k * k)


def erode(mask, r):
    return box(mask, r) > 1 - 1e-9 if r > 0 else mask


def fill(rgb, known):
    """The colours of the `known` pixels carried over the others (each takes the colours around it),
    so the edges of the wing are not mixed with the white, near or far (mipmaps)."""
    if known.all() or not known.any():
        return rgb
    w = known.astype(float)
    small = fill(*halve(rgb * w[..., None], w))
    up = np.repeat(np.repeat(small, 2, 0), 2, 1)[:rgb.shape[0], :rgb.shape[1]]
    out = rgb.copy()
    out[~known] = up[~known]
    return out


def halve(weighted, w):
    h, wd = w.shape
    h2, w2 = (h + 1) // 2, (wd + 1) // 2
    pw = np.zeros((h2 * 2, w2 * 2))
    pw[:h, :wd] = w
    pc = np.zeros((h2 * 2, w2 * 2, 3))
    pc[:h, :wd] = weighted
    sw = pw.reshape(h2, 2, w2, 2).sum((1, 3))
    sc = pc.reshape(h2, 2, w2, 2, 3).sum((1, 3))
    rgb = sc / np.maximum(sw, 1e-9)[..., None]
    return rgb, sw > 0


def loops(mask):
    """The outlines of `mask` along the sides of its pixels: closed loops of corners (x, y), the
    mask on their right (outlines clockwise, holes anticlockwise, on screen). Two pixels touching
    by a corner only are apart."""
    m = np.pad(mask, 1)
    ys, xs = np.nonzero(m[1:-1, 1:-1])
    outs = {}

    def add(sel, ax, ay, bx, by):
        for e in zip(ax[sel].tolist(), ay[sel].tolist(), bx[sel].tolist(), by[sel].tolist()):
            outs.setdefault(e[:2], []).append(e[2:])
    add(~m[ys, xs + 1], xs, ys, xs + 1, ys)
    add(~m[ys + 1, xs + 2], xs + 1, ys, xs + 1, ys + 1)
    add(~m[ys + 2, xs + 1], xs + 1, ys + 1, xs, ys + 1)
    add(~m[ys + 1, xs], xs, ys + 1, xs, ys)
    used = set()
    out = []
    for a0, ends in outs.items():
        for b0 in ends:
            if (a0, b0) in used:
                continue
            loop = []
            a, b = a0, b0
            while True:
                used.add((a, b))
                loop.append(a)
                options = outs[b]
                c = options[0]
                if len(options) > 1:
                    # where two ways go on, turn right: round the pixel the loop goes along
                    c = (b[0] - (b[1] - a[1]), b[1] + (b[0] - a[0]))
                if (b, c) == (a0, b0):
                    break
                a, b = b, c
            out.append(np.array(loop, float))
    return out


def area(p):
    x, y = p[:, 0], p[:, 1]
    return 0.5 * float(np.dot(x, np.roll(y, -1)) - np.dot(np.roll(x, -1), y))


def smooth(p, passes=4):
    """The steps of the pixels rounded off: each corner moved toward its neighbours, a few times."""
    for _ in range(passes):
        p = (np.roll(p, 1, 0) + 2 * p + np.roll(p, -1, 0)) / 4
    return p


def simplify(p, tolerance):
    """The loop with as few corners as keep it within `tolerance` of itself (Douglas-Peucker)."""
    n = len(p)
    if n < 8:
        return p
    far = int(np.argmax(((p - p[0]) ** 2).sum(1)))
    keep = np.zeros(n, bool)
    keep[0] = keep[far] = True
    stack = [(0, far), (far, n)]
    while stack:
        i, j = stack.pop()
        if j - i < 2:
            continue
        a, b = p[i], p[j % n]
        seg = p[i + 1:j]
        d = b - a
        length = np.hypot(*d)
        if length < 1e-9:
            dist = np.hypot(*(seg - a).T)
        else:
            dist = np.abs(d[0] * (seg[:, 1] - a[1]) - d[1] * (seg[:, 0] - a[0])) / length
        k = int(np.argmax(dist))
        if dist[k] > tolerance:
            m = i + 1 + k
            keep[m] = True
            stack += [(i, m), (m, j)]
    return p[keep]


def inside(point, poly):
    x, y = point
    xs, ys = poly[:, 0], poly[:, 1]
    xn, yn = np.roll(xs, -1), np.roll(ys, -1)
    cross = (ys > y) != (yn > y)
    with np.errstate(divide='ignore', invalid='ignore'):
        at = xs + (y - ys) * (xn - xs) / (yn - ys)
    return bool(np.count_nonzero(cross & (x < at)) % 2)


def shapes(mask):
    """The mask as smooth polygons: [(outline, [holes])], in texture pixels."""
    outer, holes = [], []
    for raw in loops(mask):
        a = area(raw)
        if abs(a) < MIN_AREA:
            continue
        p = simplify(smooth(raw), TOLERANCE)
        if len(p) >= 3:
            (outer if a > 0 else holes).append((p, raw, abs(a)))
    out = [(p, [], raw, size) for p, raw, size in outer]
    for hole, raw, _ in holes:
        # the smallest outline around the hole is the one it is cut from
        probe = raw[0] + 0.25
        around = [s for s in out if inside(probe, s[2])]
        if around:
            min(around, key=lambda s: s[3])[1].append(hole)
    return [(p, hs) for p, hs, _, _ in out]


def caps(shape):
    """Triangles (as indices into the corners of outline + holes) covering a polygon with holes."""
    outline, holes = shape
    rings = [outline] + holes
    verts = np.concatenate(rings)
    ends = np.cumsum([len(r) for r in rings]).astype(np.uint32)
    return verts, np.asarray(mapbox_earcut.triangulate_float64(verts, ends)).reshape(-1, 3)


# gg.rago.render.NeonColors' colours and their hues (degrees; white has none)
NEON = [('red', 0), ('orange', 28), ('yellow', 54), ('green', 125), ('cyan', 186), ('blue', 218),
        ('purple', 272), ('pink', 325), ('white', None)]


def to_hsv(rgb):
    f = rgb / 255.0
    v = f.max(2)
    d = v - f.min(2)
    s = np.where(v > 0, d / np.maximum(v, 1e-9), 0)
    r, g, b = f[..., 0], f[..., 1], f[..., 2]
    dd = np.maximum(d, 1e-9)
    h = np.where(v == r, (g - b) / dd % 6, np.where(v == g, (b - r) / dd + 2, (r - g) / dd + 4)) * 60
    return np.where(d > 0, h, 0), s, v


def to_rgb(h, s, v):
    k = lambda n: (n + h / 60) % 6  # noqa: E731
    f = lambda n: v - v * s * np.clip(np.minimum(k(n), 4 - k(n)), 0, 1)  # noqa: E731
    return np.stack([f(5), f(3), f(1)], -1) * 255


def recolour(rgb, hue):
    """The picture's reds in another colour as NeonColors makes them (the hue turned, as far from
    the new one as it was from red; or white), its shading kept and the other colours left; the
    change fades out between 25 and 40 degrees from red, so no edge shows between what changes and
    what does not."""
    h, s, v = to_hsv(rgb.astype(float))
    offset = (h + 180) % 360 - 180
    weight = np.clip((40 - np.abs(offset)) / 15, 0, 1)[..., None]
    if hue is None:
        new = to_rgb(h, s * 0.05, np.minimum(1, v * 1.05))
    else:
        new = to_rgb((hue + offset) % 360, s, v)
    return (rgb * (1 - weight) + new * weight).round().clip(0, 255).astype(np.uint8)


def erase(rgb, boxes):
    """The glowing red marks inside each box painted over with the colours around them."""
    known = np.ones(rgb.shape[:2], bool)
    for x0, y0, x1, y1 in boxes:
        # the mark and its glow: clearly redder than the dark material around it
        part = rgb[y0:y1, x0:x1]
        lit = part[..., 0] - (part[..., 1] + part[..., 2]) / 2 > 28
        known[y0:y1, x0:x1] &= ~(box(lit, 2) > 0)
    out = fill(rgb, known)
    # then smoothed, the painted part only, into the shading around it
    for x0, y0, x1, y1 in boxes:
        part, hole = out[y0 - 3:y1 + 3, x0 - 3:x1 + 3], ~known[y0 - 3:y1 + 3, x0 - 3:x1 + 3]
        for _ in range(60):
            blurred = np.stack([box(part[..., c], 1) for c in range(3)], -1)
            part[hole] = blurred[hole]
    return out


def picture(name, spec):
    """The right wing of the picture in the wing's square: its colours (TEXTURE x TEXTURE), how much
    of each pixel is wing, how many cells thick each pixel is, and the root (texture pixels)."""
    rgb = np.asarray(Image.open(os.path.join(SRC, name + '.png')).convert('RGB')).astype(float)
    if spec.get('erase'):
        rgb = erase(rgb, spec['erase'])
    wing = ~background(rgb.astype(int))
    ys, xs = np.nonzero(wing)
    top, bottom = ys.min(), ys.max() + 1
    centre = (xs.min() + xs.max() + 1) / 2
    cell = (xs.max() + 1 - centre) / spec['width'] if 'width' in spec else (bottom - top) / spec['height']
    # the root: where the middle piece crosses the centre of the picture, half way down it
    mid = wing[:, int(centre) - 3:int(centre) + 3].any(1)
    rows = np.nonzero(mid)[0]
    root_y = (rows.min() + rows.max() + 1) / 2 if len(rows) else (top + bottom) / 2
    # the square: its left side on the centre line, the root on a whole cell
    first_row = root_y - round((root_y - top) / cell + 1) * cell
    root_row = int(round((root_y - first_row) / cell))
    # thickness of each pixel of the picture, in cells
    depth = np.full(wing.shape, float(spec['base']))
    k = kinds(rgb.astype(int))
    for kind_name, cells in reversed(spec['thick']):
        depth[k == kind_name] = cells
    # the colours of the wing, carried out past its edges (the picture's edge pixels are part white)
    core = erode(wing, 1)
    rgb = fill(rgb, core)
    # the square, cut from the picture (carried on past its sides) and resized to the texture
    size = N * cell
    pad = int(size) + 4
    square = (centre + pad, first_row + pad, centre + pad + size, first_row + pad + size)

    def cut(a, mode, mode_name):
        big = np.pad(a, [(pad, pad), (pad, pad)] + [(0, 0)] * (a.ndim - 2), mode=mode)
        return Image.fromarray(big, mode_name).resize((TEXTURE, TEXTURE), Image.LANCZOS if mode_name == 'RGB' else Image.BOX,
                                                   box=square)
    colours = cut(rgb.clip(0, 255).astype(np.uint8), 'edge', 'RGB')
    cover = cut(wing.astype(np.float32), 'constant', 'F')
    thick = cut(depth.astype(np.float32), 'constant', 'F')
    root = (0, root_row * TEXTURE / N)
    return np.asarray(colours), np.asarray(cover), np.asarray(thick), root, cell


def build(name):
    spec = WINGS[name]
    colours, cover, thick, root, cell = picture(name, spec)
    tex = 'wings_' + name
    # opaque: the model's outline makes the wing's shape, the colours carried past it fill the mipmaps
    for suffix, hue in NEON if spec.get('variants') else [('', 0)]:
        img = colours if hue == 0 else recolour(colours, hue)
        Image.fromarray(img, 'RGB').save(os.path.join(cosmetics.OUT, tex + (suffix and '_' + suffix) + '.png'),
                                         optimize=True)

    t = TEXTURE
    per_cell = t / N
    rx, ry = root
    sweep = 0.25

    def at(x, y, z):
        xx = (x - rx) / t
        return (xx, (y - ry) / t, z / t + xx * sweep)

    # the layers: the whole wing, then each thicker kind, inside the one below it
    wing = cover > 0.5
    levels = sorted({spec['base']} | {c for _, c in spec['thick'] if c > spec['base']})
    masks = [wing]
    for cells in levels[1:]:
        # a part thicker than the one below where most of the pixels around are (no specks)
        m = box(thick >= cells - 1e-6, int(per_cell / 3)) > 0.5
        masks.append(m & erode(masks[-1], 2))
    tris = []
    below = None
    for cells, mask in zip(levels, masks):
        h = cells * per_cell / 2
        for shape in shapes(mask):
            verts, faces = caps(shape)
            for a, b, c in faces:
                p = [verts[a], verts[b], verts[c]]
                uv = [(q[0] / t, q[1] / t) for q in p]
                tris.append((np.array([at(*q, -h) for q in p]), np.array(uv)))
                tris.append((np.array([at(*q, h) for q in p[::-1]]), np.array(uv[::-1])))
            # the sides, facing out: through the wing, or on a thicker part only where it stands out
            sides = [(-h, h)] if below is None else [(below, h), (-h, -below)]
            for ring in [shape[0]] + shape[1]:
                for a, b in zip(ring, np.roll(ring, -1, 0)):
                    uv = [(a[0] / t, a[1] / t), (b[0] / t, b[1] / t), (b[0] / t, b[1] / t), (a[0] / t, a[1] / t)]
                    for z0, z1 in sides:
                        tris += models.quad(at(*a, z0), at(*b, z0), at(*b, z1), at(*a, z1), uv)
        below = h
    models.write_model(tex, [('wing', (0, 0, 0), tris)])
    print(f'{name}: {int(cell)} picture pixels a cell, root at {root}')
    return tex


def main():
    import preview
    names = sys.argv[1:] or list(WINGS)
    out = os.path.join(HERE, '..', 'build', 'wing-previews')
    os.makedirs(out, exist_ok=True)
    for name in names:
        tex = build(name)
        texture = tex + ('_red' if WINGS[name].get('variants') else '')
        path = os.path.join(out, tex + '.png')
        preview.sheet(os.path.join(models.MODELS, tex + '.rmdl'), os.path.join(models.TEXTURES, texture + '.png'),
                      os.path.join(models.RES, 'skins', 'qrago.png'), path, title=name, place=preview.WING_FITS[name])
        print(f'preview: {path}')


if __name__ == '__main__':
    main()
