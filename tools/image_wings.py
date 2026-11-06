"""Rago wings from a picture of a pair of wings (made with ChatGPT, or drawn), in tools/wings: the right
wing of the picture, with its half of the piece that joins the two in the middle, traced onto the
64 x 64 grid of the wing textures (tools/cosmetics.py) with its own colours; tools/models.py's
extrude then makes it a voxel model, each part as many cells thick as its colour says. In game the
other wing is the same model mirrored, the two halves of the middle piece meeting on the spine
(gg.rago.modules.Cosmetics, WING_FITS).

The picture: the pair seen from the front or the back, centred, on a plain white background.

  python3 tools/image_wings.py [NAME ...]      (all of WINGS by default)

writes assets/rago/textures/cosmetics/wings_NAME.png and assets/rago/models/cosmetics/wings_NAME.rmdl,
and pictures of the wings on a player (tools/preview.py) in build/wing-previews.
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import cosmetics  # noqa: E402
import models  # noqa: E402

SRC = os.path.join(HERE, 'wings')
N = cosmetics.N
K = cosmetics.WING_SCALE
# a cell of the grid holds a part of the wing when this much of it is wing in the picture
COVER = 0.5

# For each picture: how many cells thick each kind of colour is (the first that matches wins, the
# others are `base`), how many colours the texture keeps, and how many cells tall the pair is on the
# grid. The kinds: dark (black, charcoal), grey (white to grey, little colour), bright (strong
# light colours: claws, glowing parts, crystals).
WINGS = {
    'dragon_red_black': dict(thick=[('dark', 3), ('bright', 2)], base=1, colours=24, height=62),
    'rago_frost': dict(thick=[('grey', 3)], base=2, colours=28, height=62),
    'rago_inferno': dict(thick=[('dark', 3), ('bright', 2)], base=2, colours=24, height=62),
}


def background(rgb):
    """The white around the wings: near-white pixels reached from the edge of the picture (white
    highlights inside the wings stay)."""
    white = (255 - rgb.min(2)) < 14
    out = np.zeros_like(white)
    out[0, :] = out[-1, :] = out[:, 0] = out[:, -1] = True
    out &= white
    while True:
        grown = out.copy()
        grown[1:] |= out[:-1]
        grown[:-1] |= out[1:]
        grown[:, 1:] |= out[:, :-1]
        grown[:, :-1] |= out[:, 1:]
        grown &= white
        if (grown == out).all():
            return out
        out = grown


def kind(c):
    lo, hi = int(min(c)), int(max(c))
    if hi < 70 and hi - lo < 35:
        return 'dark'
    if hi - lo < 40:
        return 'grey'
    if hi > 200 and hi - lo > 120:
        return 'bright'
    return None


def trace(name, spec):
    """The wing's texture (N K x N K) and the thickness of each of its pixels, and its root."""
    rgb = np.asarray(Image.open(os.path.join(SRC, name + '.png')).convert('RGB')).astype(np.int64)
    wing = ~background(rgb)
    ys, xs = np.nonzero(wing)
    top, bottom = ys.min(), ys.max() + 1
    centre = (xs.min() + xs.max() + 1) / 2
    cell = (bottom - top) / spec['height']
    # the root: where the middle piece crosses the centre of the picture, half way down it
    mid = wing[:, int(centre) - 3:int(centre) + 3].any(1)
    rows = np.nonzero(mid)[0]
    root_y = (rows.min() + rows.max() + 1) / 2 if len(rows) else (top + bottom) / 2
    # the grid: column 0 starts at the centre line, the root row lands on a whole cell
    first_row = root_y - round((root_y - top) / cell + 1) * cell
    # the colours of the whole right wing, reduced
    right = wing.copy()
    right[:, :int(centre)] = False
    pixels = rgb[right].astype(np.uint8)
    q = Image.fromarray(pixels.reshape(1, -1, 3), 'RGB').quantize(
        colors=spec['colours'], method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    palette = np.array(q.getpalette()[:3 * spec['colours']], np.uint8).reshape(-1, 3)
    index = np.full(wing.shape, -1)
    index[right] = np.asarray(q).reshape(-1)
    cells = np.zeros((N, N, 4), np.uint8)
    depth = np.zeros((N, N))
    for gy in range(N):
        y0, y1 = int(round(first_row + gy * cell)), int(round(first_row + (gy + 1) * cell))
        if y1 <= 0 or y0 >= rgb.shape[0]:
            continue
        for gx in range(N):
            x0, x1 = int(round(centre + gx * cell)), int(round(centre + (gx + 1) * cell))
            if x0 >= rgb.shape[1]:
                continue
            block = index[max(y0, 0):y1, x0:x1]
            if block.size == 0 or (block >= 0).mean() < COVER:
                continue
            c = palette[np.bincount(block[block >= 0]).argmax()]
            cells[gy, gx] = tuple(c) + (255,)
            d = spec['base']
            for k, cells_thick in spec['thick']:
                if kind(c) == k:
                    d = cells_thick
                    break
            depth[gy, gx] = d
    root = (0, int(round((root_y - first_row) / cell)))
    img = Image.fromarray(cells, 'RGBA').resize((N * K, N * K), Image.NEAREST)
    used = (depth > 0).sum()
    print(f'{name}: {used} cells, root at cell {root}, {int(cell)} picture pixels a cell')
    return img, depth, root


def build(name):
    spec = WINGS[name]
    img, depth, root = trace(name, spec)
    tex = 'wings_' + name
    img.save(os.path.join(cosmetics.OUT, tex + '.png'), optimize=True)

    def depth_at(x, y):
        return depth[y // K, x // K] * K
    tris = models.extrude(tex, (root[0] * K, root[1] * K), None, 0.25, depth_at)
    models.write_model(tex, [('wing', (0, 0, 0), tris)])
    return tex


def main():
    import preview
    names = sys.argv[1:] or list(WINGS)
    out = os.path.join(HERE, '..', 'build', 'wing-previews')
    os.makedirs(out, exist_ok=True)
    for name in names:
        tex = build(name)
        path = os.path.join(out, tex + '.png')
        preview.sheet(os.path.join(models.MODELS, tex + '.rmdl'), os.path.join(models.TEXTURES, tex + '.png'),
                      os.path.join(models.RES, 'skins', 'qrago.png'), path, title=name, place=preview.WING_FITS[name])
        print(f'preview: {path}')


if __name__ == '__main__':
    main()
