"""Builds the 3D cosmetic models in assets/rago/models/cosmetics.

Two kinds of source:
  * glTF binaries (.glb) in tools/models, made by other people under CC BY 4.0. They are credited in
    tools/models/CREDITS.md and in the README. Each is converted to Minecraft's model space.
  * Rago's own pixel-art wing textures (drawn by tools/cosmetics.py), extruded into voxels: the
    membrane is one pixel thick and the bones stand out, like the voxel models above.

The output is a small gzipped file per model: groups of triangles with their texture coordinates and
a baked shade, read by gg.rago.render.CosmeticModel. Run with `python3 tools/models.py`.
"""
import gzip
import json
import os
import struct

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'rago')
MODELS = os.path.join(RES, 'models', 'cosmetics')
TEXTURES = os.path.join(RES, 'textures', 'cosmetics')
SRC = os.path.join(HERE, 'models')


# ---- glTF ------------------------------------------------------------------------------------------

def load_glb(path):
    b = open(path, 'rb').read()
    length, = struct.unpack('<I', b[12:16])
    j = json.loads(b[20:20 + length])
    off = 20 + length
    blen, = struct.unpack('<I', b[off:off + 4])
    return j, b[off + 8:off + 8 + blen]


def accessor(j, data, i):
    a = j['accessors'][i]
    view = j['bufferViews'][a['bufferView']]
    n = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}[a['type']]
    dt = np.dtype({5126: np.float32, 5123: np.uint16, 5125: np.uint32, 5121: np.uint8}[a['componentType']])
    start = view.get('byteOffset', 0) + a.get('byteOffset', 0)
    stride = view.get('byteStride') or n * dt.itemsize
    raw = np.frombuffer(data, np.uint8, stride * (a['count'] - 1) + n * dt.itemsize, start)
    rows = np.lib.stride_tricks.as_strided(raw, (a['count'], n * dt.itemsize), (stride, 1))
    return rows.copy().view(dt).reshape(a['count'], n)


def local_matrix(node):
    if 'matrix' in node:
        return np.array(node['matrix']).reshape(4, 4).T
    x, y, z, w = node.get('rotation', [0, 0, 0, 1])
    r = np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
    m = np.eye(4)
    m[:3, :3] = r * np.array(node.get('scale', [1, 1, 1]))
    m[:3, 3] = node.get('translation', [0, 0, 0])
    return m


def walk(j, fn):
    """Calls fn(node, world matrix, names of the ancestors) for every node of the scene."""
    def rec(i, parent, path):
        node = j['nodes'][i]
        world = parent @ local_matrix(node)
        fn(node, world, path)
        for c in node.get('children', []):
            rec(c, world, path + [node.get('name', '')])
    for root in j['scenes'][j.get('scene', 0)]['nodes']:
        rec(root, np.eye(4), [])


def triangles(j, data, node, world):
    """The node's triangles in world space: a list of (3x3 positions, 3x2 uvs, material)."""
    out = []
    for p in j['meshes'][node['mesh']]['primitives']:
        pos = accessor(j, data, p['attributes']['POSITION']).astype(float)
        uv = accessor(j, data, p['attributes']['TEXCOORD_0']).astype(float)
        idx = accessor(j, data, p['indices']).reshape(-1) if 'indices' in p else np.arange(len(pos))
        world_pos = (world @ np.c_[pos, np.ones(len(pos))].T).T[:, :3]
        for t in idx.reshape(-1, 3):
            out.append((world_pos[t], uv[t], p.get('material', 0)))
    return out


def texture(j, data, material):
    image = j['textures'][j['materials'][material]['pbrMetallicRoughness']['baseColorTexture']['index']]['source']
    view = j['bufferViews'][j['images'][image]['bufferView']]
    start = view.get('byteOffset', 0)
    return data[start:start + view['byteLength']]


# ---- output ----------------------------------------------------------------------------------------

def shade(a, b, c):
    """Minecraft-like flat shading from the face normal, in model space where y points down."""
    n = np.cross(b - a, c - a)
    length = np.linalg.norm(n)
    if length == 0:
        return 1.0
    n = n / length
    # y is down, so a normal with negative y faces up
    vertical = 1.0 if n[1] < 0 else 0.5
    return float(n[0] ** 2 * 0.6 + n[1] ** 2 * vertical + n[2] ** 2 * 0.8)


def write_model(name, groups):
    """groups: list of (name, pivot xyz, triangles as (positions, uvs))."""
    os.makedirs(MODELS, exist_ok=True)
    out = bytearray(b'RMDL')
    out += struct.pack('>ii', 1, len(groups))
    count = 0
    for gname, pivot, tris in groups:
        encoded = gname.encode('utf-8')
        out += struct.pack('>H', len(encoded)) + encoded
        out += struct.pack('>fffi', *pivot, len(tris))
        for pos, uv in tris:
            s = round(shade(*pos) * 255)
            for k in range(3):
                out += struct.pack('>fffffB', *pos[k], *uv[k], s)
        count += len(tris)
    with gzip.open(os.path.join(MODELS, name + '.rmdl'), 'wb', 9) as f:
        f.write(bytes(out))
    print(f'{name}: {count} triangles')


def drop_hidden(tris):
    """Removes the faces two cubes share exactly, which nobody can see."""
    keys = {}
    for i, (pos, _) in enumerate(tris):
        keys.setdefault(tuple(sorted(tuple(np.round(p, 4)) for p in pos)), []).append(i)
    hidden = {i for group in keys.values() if len(group) > 1 for i in group}
    return [t for i, t in enumerate(tris) if i not in hidden]


# ---- the voxel hats --------------------------------------------------------------------------------

HATS = {
    # glb file: {name of the hat's node: (model name, texture name)}
    'hats_mini_pack_1.glb': {'cylinder_18': ('top_hat', 'hats_atlas')},
    'hats_mini_pack_3.glb': {'magehat_20': ('wizard_hat', 'wizard_hat')},
}


def hats():
    for file, wanted in HATS.items():
        j, data = load_glb(os.path.join(SRC, file))
        found = {}

        def visit(node, world, path):
            if 'mesh' not in node:
                return
            for hat in wanted:
                if hat in path:
                    found.setdefault(hat, []).extend(triangles(j, data, node, world))
            if path and path[-1].startswith('Head'):
                # the dummy head of the hat sitting next to it: its centre is the hat's centre
                found.setdefault('head:' + path[-2], []).extend(triangles(j, data, node, world))

        walk(j, visit)
        heads = {k[5:]: v for k, v in found.items() if k.startswith('head:')}
        for hat, (model, tex) in wanted.items():
            tris = found[hat]
            centre = None
            for dummy, head in heads.items():
                p = np.concatenate([t[0] for t in head])
                mid = (p.min(0) + p.max(0)) / 2
                q = np.concatenate([t[0] for t in tris])
                if abs((q.min(0)[0] + q.max(0)[0]) / 2 - mid[0]) < 0.3:
                    centre = mid
            # glTF: y up, the face looking to +z, the head from y 0.25 to 0.75. Minecraft's head space:
            # y down from the neck, the face looking to -z. Turning half a circle around x does both.
            converted = []
            for pos, uv, _ in tris:
                p = pos - np.array([centre[0], 0.25, centre[2]])
                converted.append((p * np.array([1, -1, -1]), uv))
            write_model(model, [('hat', (0, 0, 0), drop_hidden(converted))])
            open(os.path.join(TEXTURES, tex + '.png'), 'wb').write(texture(j, data, tris[0][2]))


# ---- extruded pixel-art wings ---------------------------------------------------------------------

def quad(a, b, c, d, uv):
    """Two triangles for the corners a b c d, with uv the matching four texture coordinates."""
    return [(np.array([a, b, c]), np.array([uv[0], uv[1], uv[2]])),
            (np.array([a, c, d]), np.array([uv[0], uv[2], uv[3]]))]


def extrude(tex_name, root, depth_of, sweep=0.25, depth_at=None):
    """
    A wing from a 64 x 64 texture, one voxel per opaque pixel, `depth_of(pixel)` pixels thick. The root
    pixel lands on (0, 0, 0); one texture is one unit wide, and the wing sweeps back toward its tip.
    """
    img = Image.open(os.path.join(TEXTURES, tex_name + '.png')).convert('RGBA')
    n = max(img.size)
    px = np.zeros((n, n, 4), np.uint8)
    px[:img.size[1], :img.size[0]] = np.array(img)
    depth = np.zeros((n, n))
    for y in range(n):
        for x in range(n):
            if px[y, x, 3] > 25:
                depth[y, x] = depth_at(x, y) if depth_at else depth_of(px[y, x])
    s = 1 / n
    tw, th = img.size
    rx, ry = root

    def at(x, y, z):
        xx = (x - rx) * s
        return (xx, (y - ry) * s, z * s + xx * sweep)

    tris = []
    for y in range(n):
        x = 0
        while x < n:
            d = depth[y, x]
            if d == 0:
                x += 1
                continue
            # a run of pixels as thick as this one shares its front and back faces
            end = x
            while end + 1 < n and depth[y, end + 1] == d:
                end += 1
            u0, u1, v0, v1 = x / tw, (end + 1) / tw, y / th, (y + 1) / th
            h = d / 2
            tris += quad(at(x, y, -h), at(end + 1, y, -h), at(end + 1, y + 1, -h), at(x, y + 1, -h),
                         [(u0, v0), (u1, v0), (u1, v1), (u0, v1)])
            tris += quad(at(end + 1, y, h), at(x, y, h), at(x, y + 1, h), at(end + 1, y + 1, h),
                         [(u1, v0), (u0, v0), (u0, v1), (u1, v1)])
            x = end + 1
    for y in range(n):
        for x in range(n):
            d = depth[y, x]
            if d == 0:
                continue
            h = d / 2
            # the side faces only where the neighbour is thinner, coloured with this pixel
            c = ((x + 0.5) / tw, (y + 0.5) / th)
            uv = [c, c, c, c]
            if x == 0 or depth[y, x - 1] < d:
                tris += quad(at(x, y, -h), at(x, y + 1, -h), at(x, y + 1, h), at(x, y, h), uv)
            if x == n - 1 or depth[y, x + 1] < d:
                tris += quad(at(x + 1, y, h), at(x + 1, y + 1, h), at(x + 1, y + 1, -h), at(x + 1, y, -h), uv)
            if y == 0 or depth[y - 1, x] < d:
                tris += quad(at(x, y, h), at(x + 1, y, h), at(x + 1, y, -h), at(x, y, -h), uv)
            if y == n - 1 or depth[y + 1, x] < d:
                tris += quad(at(x, y + 1, -h), at(x + 1, y + 1, -h), at(x + 1, y + 1, h), at(x, y + 1, h), uv)
    return tris


def bones_thicker(p):
    """Dark pixels are the bones and veins: three pixels thick, the membrane one."""
    return 3 if p[0] < 110 else 1


def neon_bones(p):
    """The red bones of a neon wing (blue kept at 10): three pixels thick, the rest one."""
    return 3 if p[0] > 120 and p[2] < 20 else 1


def wings():
    write_model('dragon', [('wing', (0, 0, 0), extrude('dragon', (3, 12), bones_thicker))])
    write_model('dragon_small', [('wing', (0, 0, 0), extrude('dragon_small', (3, 22), neon_bones))])


def shields():
    """Rago's shields: the rim three cells thick, the logo two, the face one (a cell is SHIELD_SCALE pixels)."""
    import cosmetics
    _, rim, logo = cosmetics.shield_masks()
    k = cosmetics.SHIELD_SCALE

    def depth(x, y):
        return k * (3 if (x, y) in rim else 2 if (x, y) in logo else 1)
    for style in cosmetics.SHIELD_STYLES:
        name = 'shield_' + style
        # centred on the shield: one unit is the shield's height
        write_model(name, [('shield', (0, 0, 0), extrude(name, (20 * k, 24 * k), None, 0, depth))])


if __name__ == '__main__':
    shields()
    hats()
    wings()
