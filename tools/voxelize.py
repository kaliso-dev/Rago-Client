"""Turns a 3D model made with an AI tool (Meshy, Tripo) or exported from Blender or Blockbench into a
Rago hat: cubes of half a skin pixel, like Rago's own voxel hats (tools/reward_hats.py), coloured from
the model's texture.

  python3 tools/voxelize.py MODEL.glb --name NAME [--width 10] [--sink 0] [--turn 0] [--colors 32]

writes assets/rago/models/cosmetics/hat_NAME.rmdl and assets/rago/textures/cosmetics/hat_NAME.png, then
pictures of the hat on a player (tools/preview.py) in --preview (by default next to MODEL).

How:
  1. the model's triangles, from its glTF scene (y up, its front toward +z), turned into Minecraft's head
     space: pixels of the skin, y down, the face toward -z, the top of the head at y = -8;
  2. scaled to --width (or --height, --depth) pixels and set on the head: centred, its bottom just over
     the skin's hat layer, or --sink pixels lower for a hat that goes down around the head (then the
     cubes where the head is are taken out, so the head shows under it);
  3. points all over its surface, each with the colour of the texture there; every cube that holds a
     point is filled, then the inside too, so only the outside has faces;
  4. the colours reduced to --colors (a palette texture, a 4 x 4 square a colour), each face of a cube
     taking the colour most of the points facing its way have;
  5. faces between two cubes dropped; with --merge, the others joined into rectangles of one colour.

The model is read from a .glb (or a .gltf with its files): its meshes, their base colour texture or
colour, and their vertex colours. Draco compressed meshes need `pip install DracoPy`.
"""
import argparse
import base64
import io
import json
import math
import os
import struct
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import models  # noqa: E402  (walk, write_model, MODELS, TEXTURES)

# head space, in pixels of the skin (as tools/reward_hats.py)
HEAD_TOP = -8.0
# the skin's hat layer reaches half a pixel further than the head
LAYER = 0.5
# how far outside that layer a hat stays (Cosmetics.LAYER_CLEARANCE)
CLEARANCE = 0.4
# where a hat's bottom rests: an eighth of a pixel over the hat layer (exact in binary, no z-fighting)
REST = HEAD_TOP - LAYER - 0.125
# triangles a hat may have before --merge is used by itself (the biggest voxel hat has 2,040)
HAT_BUDGET = 4000

# the six directions a face looks, in head space: (axis, sign)
DIRECTIONS = [(0, 1), (0, -1), (1, 1), (1, -1), (2, 1), (2, -1)]


# ---- reading glTF -------------------------------------------------------------------------------------

COMPONENTS = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
WIDTH = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def load(path):
    """The glTF json and its buffers, from a .glb or a .gltf."""
    raw = open(path, 'rb').read()
    base = os.path.dirname(os.path.abspath(path))
    if raw[:4] == b'glTF':
        length, = struct.unpack('<I', raw[12:16])
        j = json.loads(raw[20:20 + length])
        off = 20 + length
        chunks = []
        while off + 8 <= len(raw):
            size, kind = struct.unpack('<II', raw[off:off + 8])
            if kind == 0x004E4942:  # BIN
                chunks.append(raw[off + 8:off + 8 + size])
            off += 8 + size
        binary = chunks[0] if chunks else b''
    else:
        j = json.loads(raw)
        binary = b''
    buffers = []
    for b in j.get('buffers', []):
        uri = b.get('uri')
        if uri is None:
            buffers.append(binary)
        elif uri.startswith('data:'):
            buffers.append(base64.b64decode(uri.split(',', 1)[1]))
        else:
            buffers.append(open(os.path.join(base, uri), 'rb').read())
    j['_base'] = base
    return j, buffers


def view_bytes(j, buffers, index):
    view = j['bufferViews'][index]
    start = view.get('byteOffset', 0)
    return buffers[view['buffer']][start:start + view['byteLength']], view.get('byteStride')


def accessor(j, buffers, index):
    """An accessor's values as floats (normalised integers turned into 0 to 1), one row a value."""
    a = j['accessors'][index]
    n = WIDTH[a['type']]
    dt = np.dtype(COMPONENTS[a['componentType']]).newbyteorder('<')
    count = a['count']
    if 'bufferView' in a:
        data, stride = view_bytes(j, buffers, a['bufferView'])
        start = a.get('byteOffset', 0)
        stride = stride or n * dt.itemsize
        rows = np.lib.stride_tricks.as_strided(
            np.frombuffer(data, np.uint8, stride * (count - 1) + n * dt.itemsize, start),
            (count, n * dt.itemsize), (stride, 1))
        values = rows.copy().view(dt).reshape(count, n).astype(np.float64)
    else:
        values = np.zeros((count, n))
    if 'sparse' in a:
        s = a['sparse']
        idt = np.dtype(COMPONENTS[s['indices']['componentType']]).newbyteorder('<')
        idata, _ = view_bytes(j, buffers, s['indices']['bufferView'])
        idx = np.frombuffer(idata, idt, s['count'], s['indices'].get('byteOffset', 0))
        vdata, _ = view_bytes(j, buffers, s['values']['bufferView'])
        vals = np.frombuffer(vdata, dt, s['count'] * n, s['values'].get('byteOffset', 0)).reshape(-1, n)
        values[idx] = vals
    if a.get('normalized'):
        if dt == np.uint8:
            values /= 255
        elif dt == np.uint16:
            values /= 65535
        elif dt == np.int8:
            values = np.maximum(values / 127, -1)
        elif dt == np.int16:
            values = np.maximum(values / 32767, -1)
    return values


def image(j, buffers, index):
    """An image of the model as an RGBA array."""
    img = j['images'][index]
    if 'bufferView' in img:
        raw, _ = view_bytes(j, buffers, img['bufferView'])
    elif img.get('uri', '').startswith('data:'):
        raw = base64.b64decode(img['uri'].split(',', 1)[1])
    else:
        raw = open(os.path.join(j['_base'], img['uri']), 'rb').read()
    return np.asarray(Image.open(io.BytesIO(bytes(raw))).convert('RGBA'))


class Material:
    """What colours a primitive: a base colour, times its texture (if any), times its vertex colours."""

    def __init__(self, j, buffers, index):
        self.factor = np.ones(4)
        self.texture = None
        self.texcoord = 0
        if index is None:
            return
        m = j['materials'][index]
        pbr = m.get('pbrMetallicRoughness', {})
        self.factor = np.array(pbr.get('baseColorFactor', [1, 1, 1, 1]), float)
        tex = pbr.get('baseColorTexture')
        if tex is None:
            # an old exporter's specular-glossiness material
            sg = m.get('extensions', {}).get('KHR_materials_pbrSpecularGlossiness', {})
            if 'diffuseFactor' in sg:
                self.factor = np.array(sg['diffuseFactor'], float)
            tex = sg.get('diffuseTexture')
        if tex is not None:
            t = j['textures'][tex['index']]
            source = t.get('source')
            if source is None:
                exts = t.get('extensions', {})
                for e in ('EXT_texture_webp', 'KHR_texture_basisu', 'MSFT_texture_dds'):
                    if e in exts:
                        source = exts[e].get('source')
                        break
            if source is not None:
                self.texture = image(j, buffers, source)
            self.texcoord = tex.get('texCoord', 0)

    def colours(self, uv, vertex_colours):
        """The colour (0 to 1, RGBA) at each texture coordinate."""
        c = np.tile(self.factor, (len(uv), 1))
        if self.texture is not None:
            h, w = self.texture.shape[:2]
            x = np.floor(np.mod(uv[:, 0], 1) * w).astype(int).clip(0, w - 1)
            y = np.floor(np.mod(uv[:, 1], 1) * h).astype(int).clip(0, h - 1)
            c = c * self.texture[y, x] / 255
        if vertex_colours is not None:
            c = c * vertex_colours
        return c


def primitive_arrays(j, buffers, p):
    """A primitive's positions, triangle indices, texture coordinates (by set) and vertex colours."""
    draco = p.get('extensions', {}).get('KHR_draco_mesh_compression')
    if draco is not None:
        try:
            import DracoPy
        except ImportError:
            sys.exit('This model is compressed with Draco: run `pip install DracoPy`, or export it again '
                     'without compression.')
        data, _ = view_bytes(j, buffers, draco['bufferView'])
        mesh = DracoPy.decode(bytes(data))
        pos = np.asarray(mesh.points, float).reshape(-1, 3)
        idx = np.asarray(mesh.faces, np.int64).reshape(-1)
        uvs = {}
        if getattr(mesh, 'tex_coord', None) is not None and len(mesh.tex_coord):
            uvs[0] = np.asarray(mesh.tex_coord, float).reshape(-1, 2)
        colours = None
        if getattr(mesh, 'colors', None) is not None and len(mesh.colors):
            colours = np.asarray(mesh.colors, float).reshape(len(pos), -1)
            colours = colours / (255 if colours.max() > 1 else 1)
        return pos, idx, uvs, colours
    attrs = p['attributes']
    pos = accessor(j, buffers, attrs['POSITION'])
    if 'indices' in p:
        idx = accessor(j, buffers, p['indices']).reshape(-1).astype(np.int64)
    else:
        idx = np.arange(len(pos))
    mode = p.get('mode', 4)
    if mode == 5:  # strip
        t = [(idx[i], idx[i + 1], idx[i + 2]) if i % 2 == 0 else (idx[i + 1], idx[i], idx[i + 2])
             for i in range(len(idx) - 2)]
        idx = np.array(t, np.int64).reshape(-1)
    elif mode == 6:  # fan
        idx = np.array([(idx[0], idx[i], idx[i + 1]) for i in range(1, len(idx) - 1)], np.int64).reshape(-1)
    elif mode != 4:
        return pos, np.zeros(0, np.int64), {}, None
    uvs = {}
    for k in range(4):
        if f'TEXCOORD_{k}' in attrs:
            uvs[k] = accessor(j, buffers, attrs[f'TEXCOORD_{k}'])
    colours = None
    if 'COLOR_0' in attrs:
        colours = accessor(j, buffers, attrs['COLOR_0'])
        if colours.shape[1] == 3:
            colours = np.c_[colours, np.ones(len(colours))]
    return pos, idx, uvs, colours


def triangles(path, node_filter=None):
    """Every triangle of the model's scene, in its world space: positions (n, 3, 3), texture
    coordinates (n, 3, 2), and the colours of a point given by its triangle and barycentric weights."""
    j, buffers = load(path)
    materials = {}
    parts = []

    def visit(node, world, names):
        if 'mesh' not in node:
            return
        if node_filter and node_filter not in names + [node.get('name', '')]:
            return
        flip = np.linalg.det(world[:3, :3]) < 0
        for p in j['meshes'][node['mesh']]['primitives']:
            pos, idx, uvs, colours = primitive_arrays(j, buffers, p)
            if len(idx) < 3:
                continue
            m = p.get('material')
            if m not in materials:
                materials[m] = Material(j, buffers, m)
            mat = materials[m]
            tri = idx[:len(idx) // 3 * 3].reshape(-1, 3)
            if flip:
                tri = tri[:, ::-1]
            world_pos = (world @ np.c_[pos, np.ones(len(pos))].T).T[:, :3]
            uv = uvs.get(mat.texcoord, np.zeros((len(pos), 2)))
            parts.append((world_pos[tri], uv[tri], None if colours is None else colours[tri], mat))

    models.walk(j, visit)
    if not parts:
        sys.exit('No triangles found' + (f' under a node named {node_filter!r}' if node_filter else '') + '.')
    return parts


# ---- turning it into cubes ----------------------------------------------------------------------------

def to_head_space(parts, turn):
    """glTF (y up, front +z) to head space (y down, front -z): half a turn around x, then `turn`
    degrees around the vertical (positive: what faced the model's left faces forward)."""
    a = math.radians(turn)
    turn_m = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    m = turn_m @ np.diag([1, -1, -1])
    return [(pos @ m.T, uv, col, mat) for pos, uv, col, mat in parts]


def place(parts, args):
    """Scales and moves the model onto the head, as the arguments say."""
    allp = np.concatenate([p[0].reshape(-1, 3) for p in parts])
    lo, hi = allp.min(0), allp.max(0)
    size = hi - lo
    if args.height:
        scale = args.height / size[1]
    elif args.depth:
        scale = args.depth / size[2]
    else:
        scale = args.width / size[0]
    centre = (lo + hi) / 2
    bottom = REST + args.sink
    shift = np.array(args.shift, float)

    def move(pos):
        q = (pos - centre) * scale
        q[..., 1] += bottom - (hi[1] - centre[1]) * scale
        return q + shift

    placed = [(move(pos), uv, col, mat) for pos, uv, col, mat in parts]
    print(f'model: {size[0]:.3g} x {size[1]:.3g} x {size[2]:.3g} in its file, '
          f'{size[0] * scale:.1f} x {size[1] * scale:.1f} x {size[2] * scale:.1f} pixels on the head')
    return placed, bottom + shift[1]


def sample(parts, step):
    """Points all over the surface, at most `step` pixels apart: positions, colours (RGBA 0 to 1) and
    the normal of the triangle each is on."""
    out_p, out_c, out_n = [], [], []
    for pos, uv, col, mat in parts:
        e = np.stack([pos[:, 1] - pos[:, 0], pos[:, 2] - pos[:, 1], pos[:, 0] - pos[:, 2]], 1)
        normal = np.cross(pos[:, 1] - pos[:, 0], pos[:, 2] - pos[:, 0])
        length = np.linalg.norm(normal, axis=1)
        keep = length > 1e-12
        normal[keep] /= length[keep, None]
        n = np.maximum(1, np.ceil(np.linalg.norm(e, axis=2).max(1) / step)).astype(int)
        for k in np.unique(n[keep]):
            ids = np.nonzero((n == k) & keep)[0]
            a, b = np.meshgrid(np.arange(k + 1), np.arange(k + 1), indexing='ij')
            on = a + b <= k
            w = np.stack([a[on], b[on]], 1) / k
            w = np.c_[1 - w.sum(1), w]
            w = np.r_[w, [[1 / 3, 1 / 3, 1 / 3]]]
            p = np.einsum('sk,tkc->tsc', w, pos[ids]).reshape(-1, 3)
            t = np.einsum('sk,tkc->tsc', w, uv[ids]).reshape(-1, 2)
            vc = None if col is None else np.einsum('sk,tkc->tsc', w, col[ids]).reshape(-1, 4)
            out_p.append(p)
            out_c.append(mat.colours(t, vc))
            out_n.append(np.repeat(normal[ids], len(w), axis=0))
    return np.concatenate(out_p), np.concatenate(out_c), np.concatenate(out_n)


def median_cut(rgb, count):
    """rgb (n, 3, uint8) reduced to at most `count` colours: the palette (k, 3) and each one's index."""
    img = Image.fromarray(np.ascontiguousarray(rgb).reshape(1, -1, 3), 'RGB')
    q = img.quantize(colors=count, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    index = np.asarray(q).reshape(-1).astype(np.int64)
    used = np.unique(index)
    palette = np.array(q.getpalette()[:3 * (int(used.max()) + 1)], np.uint8).reshape(-1, 3)
    # only the colours something has, renumbered
    remap = np.full(len(palette), -1)
    remap[used] = np.arange(len(used))
    return palette[used], remap[index]


def nearest(rgb, palette):
    d = ((rgb[:, None, :].astype(float) - palette[None, :, :].astype(float)) ** 2).sum(2)
    return d.argmin(1), np.sqrt(d.min(1))


def reduce_colours(rgb, count, accent=48, extra=16):
    """The colours of the faces (n, 3) reduced to `count`, then up to `extra` more for the small spots
    of their own colour (an eye, a gem) that the reduction would lose: any colour still more than
    `accent` away from the palette gets in. The palette and each face's index in it."""
    palette, _ = median_cut(rgb, count)
    index, error = nearest(rgb, palette)
    while error.max() > accent and len(palette) < min(256, count + extra):
        palette = np.r_[palette, rgb[error.argmax()][None]]
        index, error = nearest(rgb, palette)
    used = np.unique(index)
    remap = np.full(len(palette), -1)
    remap[used] = np.arange(len(used))
    return palette[used], remap[index]


def modes(keys, index, count):
    """For each key, the index it has most often (a dict)."""
    combo = keys.astype(np.int64) * count + index
    u, c = np.unique(combo, return_counts=True)
    k, i = u // count, u % count
    order = np.lexsort((-c, k))
    k, i = k[order], i[order]
    first = np.r_[True, k[1:] != k[:-1]]
    return dict(zip(k[first].tolist(), i[first].tolist()))


def shifted(a, axis, sign):
    """a moved one cell along axis toward sign (what lies next to each cell that way), False at the edge."""
    out = np.zeros_like(a)
    src = [slice(None)] * 3
    dst = [slice(None)] * 3
    if sign > 0:
        src[axis], dst[axis] = slice(1, None), slice(None, -1)
    else:
        src[axis], dst[axis] = slice(None, -1), slice(1, None)
    out[tuple(dst)] = a[tuple(src)]
    return out


def outside(filled):
    """The empty cells reached from the edge of the grid without crossing a filled one."""
    out = np.zeros_like(filled)
    out[0, :, :] = out[-1, :, :] = out[:, 0, :] = out[:, -1, :] = out[:, :, 0] = out[:, :, -1] = True
    out &= ~filled
    while True:
        grown = out.copy()
        for axis, sign in DIRECTIONS:
            grown |= shifted(out, axis, sign)
        grown &= ~filled
        if (grown == out).all():
            return out
        out = grown


def voxelize(args):
    parts = to_head_space(triangles(args.model, args.node), args.turn)
    parts, bottom = place(parts, args)
    v = args.voxel
    points, colours, normals = sample(parts, v * 0.3)
    opaque = colours[:, 3] >= 0.5
    points, colours, normals = points[opaque], colours[opaque], normals[opaque]
    # a point exactly on the side of a cube belongs to the cube behind it, inside the model
    points = points - normals * (v * 0.01)
    # cells: x in [i v, (i + 1) v), z the same, y in (bottom - (j + 1) v, bottom - j v]
    cell = np.c_[np.floor(points[:, 0] / v), np.floor((bottom - points[:, 1]) / v), np.floor(points[:, 2] / v)]
    cell = cell.astype(np.int64)
    lo = cell.min(0) - 1
    cell -= lo
    shape = tuple(cell.max(0) + 2)
    filled = np.zeros(shape, bool)
    filled[cell[:, 0], cell[:, 1], cell[:, 2]] = True
    shell = filled.sum()
    air = outside(filled)
    solid = ~air
    closed = solid.sum() > shell
    # a hat that goes down around the head: the cubes where the head and its hat layer are come out
    ii, jj, kk = np.indices(shape)
    cx = (ii + lo[0] + 0.5) * v
    cy = bottom - (jj + lo[1] + 0.5) * v
    cz = (kk + lo[2] + 0.5) * v
    edge = 4 + LAYER + CLEARANCE
    head = (np.abs(cx) < edge) & (np.abs(cz) < edge) & (cy > HEAD_TOP - LAYER)
    carved = (solid & head).sum()
    solid &= ~head
    print(f'cubes: {solid.sum()} of {v} pixel ({shape[0] - 2} x {shape[1] - 2} x {shape[2] - 2}), '
          f'{"filled inside" if closed else "hollow: the surface is not closed"}'
          + (f', {carved} taken out for the head' if carved else ''))

    # first many colours (the texture's own, its noise and shading smoothed a little): the faces take
    # the one most of their points have; main() then reduces those to --colors
    rgb = (colours[:, :3].clip(0, 1) * 255 + 0.5).astype(np.uint8)
    palette, index = median_cut(rgb, 256)
    flat = np.ravel_multi_index(cell.T, shape)
    by_cell = modes(flat, index, len(palette))
    axis = np.abs(normals).argmax(1)
    sign = np.take_along_axis(normals, axis[:, None], 1)[:, 0] > 0
    # the index in DIRECTIONS, in cell space, where y goes up (head space y goes down)
    toward_plus = np.where(axis == 1, ~sign, sign)
    direction = axis * 2 + (~toward_plus).astype(int)
    by_face = modes(flat * 6 + direction, index, len(palette))
    # cubes no point fell in (inside, shown where the head was taken out): the colour of the nearest one
    coloured = np.array(sorted(by_cell))
    cc = np.c_[np.unravel_index(coloured, shape)]

    def colour_of(c, d):
        f = c * 6 + d
        if f in by_face:
            return by_face[f]
        if c in by_cell:
            return by_cell[c]
        p = np.array(np.unravel_index(c, shape))
        near = coloured[np.argmin(((cc - p) ** 2).sum(1))]
        by_cell[c] = by_cell[near]
        return by_cell[c]

    return solid, lo, shape, bottom, palette, colour_of


# ---- faces ---------------------------------------------------------------------------------------------

def faces(solid, colour_of, merge):
    """The outside faces: (axis, sign, layer, (u0, v0, u1, v1) in cells, colour), merged into rectangles
    of one colour if asked."""
    shape = solid.shape
    out = []
    for d, (axis, sign) in enumerate(DIRECTIONS):
        visible = solid & ~shifted(solid, axis, sign)
        others = [a for a in range(3) if a != axis]
        for layer in range(shape[axis]):
            sl = [slice(None)] * 3
            sl[axis] = layer
            mask = visible[tuple(sl)]
            if not mask.any():
                continue
            colours = np.zeros(mask.shape, np.int64)
            for a, b in zip(*np.nonzero(mask)):
                c = [0, 0, 0]
                c[axis], c[others[0]], c[others[1]] = layer, a, b
                colours[a, b] = colour_of(int(np.ravel_multi_index(c, shape)), d) + 1
            if not merge:
                for a, b in zip(*np.nonzero(colours)):
                    out.append((axis, sign, layer, (a, b, a + 1, b + 1), colours[a, b] - 1))
                continue
            w, h = colours.shape
            for a in range(w):
                b = 0
                while b < h:
                    c = colours[a, b]
                    if c == 0:
                        b += 1
                        continue
                    e = b + 1
                    while e < h and colours[a, e] == c:
                        e += 1
                    f = a + 1
                    while f < w and (colours[f, b:e] == c).all():
                        f += 1
                    colours[a:f, b:e] = 0
                    out.append((axis, sign, layer, (a, b, f, e), c - 1))
                    b = e
    return out


def triangles_of(rects, lo, v, bottom, uv_of):
    """Two triangles a rectangle, in head space blocks (the model's units), turned to face outward."""
    tris = []
    for axis, sign, layer, (a0, b0, a1, b1), colour in rects:
        others = [a for a in range(3) if a != axis]
        plane = layer + (1 if sign > 0 else 0)

        def corner(a, b):
            c = [0, 0, 0]
            c[axis], c[others[0]], c[others[1]] = plane, a, b
            i, j, k = (np.array(c) + lo)
            return np.array([i * v, bottom - j * v, k * v]) / 16

        p = [corner(a0, b0), corner(a1, b0), corner(a1, b1), corner(a0, b1)]
        outward = np.zeros(3)
        outward[axis] = sign * (-1 if axis == 1 else 1)
        uv = uv_of[colour]
        for t in ((p[0], p[1], p[2]), (p[0], p[2], p[3])):
            tri = np.array(t)
            if np.dot(np.cross(tri[1] - tri[0], tri[2] - tri[0]), outward) < 0:
                tri = tri[[0, 2, 1]]
            tris.append((tri, np.array([uv, uv, uv])))
    return tris


def palette_texture(palette, name):
    """A palette texture as tools/models.voxel_model makes: each colour one 4 x 4 square of a 64 x 64."""
    cell = 4
    side = 16 * cell
    assert len(palette) <= 256
    tex = Image.new('RGBA', (side, side))
    uv_of = []
    for i, c in enumerate(palette):
        x, y = i % 16 * cell, i // 16 * cell
        tex.paste(tuple(int(v) for v in c) + (255,), (x, y, x + cell, y + cell))
        uv_of.append(((x + cell / 2) / side, (y + cell / 2) / side))
    path = os.path.join(models.TEXTURES, name + '.png')
    tex.save(path)
    return uv_of, path


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('model', help='the .glb (or .gltf) file')
    ap.add_argument('--name', required=True, help='the hat\'s id: hat_NAME.rmdl, "NAME" in the catalogue')
    ap.add_argument('--width', type=float, default=10, help='width across the face, in skin pixels (the head is 8)')
    ap.add_argument('--height', type=float, help='or the height, in skin pixels')
    ap.add_argument('--depth', type=float, help='or the depth, front to back, in skin pixels')
    ap.add_argument('--sink', type=float, default=0, help='pixels the hat goes down around the head')
    ap.add_argument('--shift', type=float, nargs=3, default=(0, 0, 0), metavar=('X', 'Y', 'Z'),
                    help='a last move, in pixels (x toward the left ear, y down, z toward the back)')
    ap.add_argument('--turn', type=float, default=0, help='degrees around the vertical, if the front is not toward +z')
    ap.add_argument('--voxel', type=float, default=0.5, help='side of a cube, in skin pixels')
    ap.add_argument('--colors', type=int, default=32, help='at most this many colours (up to 256)')
    ap.add_argument('--merge', choices=('auto', 'yes', 'no'), default='auto',
                    help='join faces of one colour into rectangles (auto: when over the budget)')
    ap.add_argument('--node', help='only the meshes under the node of this name')
    ap.add_argument('--preview', help='folder for the pictures on a player (default: next to the model)')
    ap.add_argument('--skin', default=os.path.join(models.RES, 'skins', 'qrago.png'), help='skin for the pictures')
    args = ap.parse_args()
    args.colors = max(2, min(256, args.colors))

    solid, lo, shape, bottom, fine, fine_of = voxelize(args)
    # the faces' colours reduced to --colors (and the few small spots of their own colour)
    seen = [c for *_, c in faces(solid, fine_of, merge=False)]
    palette, final = reduce_colours(fine[seen], args.colors)
    to_final = dict(zip(seen, final.tolist()))

    def colour_of(c, d):
        return to_final[fine_of(c, d)]

    rects = faces(solid, colour_of, merge=args.merge == 'yes')
    if args.merge == 'auto' and 2 * len(rects) > HAT_BUDGET:
        merged = faces(solid, colour_of, merge=True)
        print(f'{2 * len(rects)} triangles is over {HAT_BUDGET}: faces joined into {2 * len(merged)}')
        rects = merged
    name = 'hat_' + args.name
    uv_of, tex_path = palette_texture(palette, name)
    tris = triangles_of(rects, lo, args.voxel, bottom, uv_of)
    models.write_model(name, [('hat', (0, 0, 0), tris)])
    print(f'{len(palette)} colours: {tex_path}')
    if 2 * len(rects) > HAT_BUDGET:
        print(f'warning: more than {HAT_BUDGET} triangles; try --voxel 1 or fewer --colors')

    import preview
    folder = args.preview or os.path.dirname(os.path.abspath(args.model))
    os.makedirs(folder, exist_ok=True)
    out = os.path.join(folder, name + '-apercu.png')
    preview.sheet(os.path.join(models.MODELS, name + '.rmdl'), tex_path, args.skin, out, title=args.name)
    print(f'preview: {out}')


if __name__ == '__main__':
    main()
