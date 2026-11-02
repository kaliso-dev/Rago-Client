"""The hats won with Rago Points, as voxels: Rago's own models, made here from shapes. tools/models.py
turns each into a model (only the faces that can be seen) and a small palette texture.

Head space, in pixels of the skin (16 to a block): x across, y down (the top of the head at -8),
z from the face (-4) to the back of the head (4). The skin's hat layer reaches half a pixel further.

  cat_ears     a black headband with two cat ears, pink inside
  devil_horns  two glossy red horns curving out and up
  headphones   black headphones over the head, Rago's red on the band and the ear cups
  kitsune      a white fox mask with red markings, worn on the side of the head

  python3 tools/models.py voxel_hats
"""
import math

import numpy as np

BLACK = (30, 30, 36)
PINK = (255, 150, 190)
RAGO_RED = (242, 57, 44)


class Voxels:
    """Cubes of `size` pixels on a grid, by their corner (in units of `size`), with a colour each."""

    def __init__(self, size):
        self.size = size
        self.cells = {}
        # applied to every corner once the faces are made (a rotation and a move), in pixels
        self.matrix = np.eye(3)
        self.offset = np.zeros(3)

    def put(self, x, y, z, colour):
        """The cube whose centre is nearest (x, y, z), in pixels."""
        s = self.size
        self.cells[(math.floor(x / s), math.floor(y / s), math.floor(z / s))] = colour

    def fill(self, test, colour_of, box):
        """Every cube of the box (x0, y0, z0, x1, y1, z1, pixels) whose centre passes test(x, y, z)."""
        s = self.size
        x0, y0, z0, x1, y1, z1 = box
        for i in range(math.floor(x0 / s), math.ceil(x1 / s)):
            for j in range(math.floor(y0 / s), math.ceil(y1 / s)):
                for k in range(math.floor(z0 / s), math.ceil(z1 / s)):
                    c = ((i + 0.5) * s, (j + 0.5) * s, (k + 0.5) * s)
                    if test(*c):
                        colour = colour_of(*c)
                        if colour is not None:
                            self.cells[(i, j, k)] = colour


def cat_ears():
    v = Voxels(0.5)
    # the band: over the top of the head and down the sides, a little in front of the middle
    v.fill(lambda x, y, z: (-4.5 <= x <= 4.5 and -9 <= y <= -8) or (4.5 <= abs(x) <= 5 and -9 <= y <= -5),
           lambda x, y, z: BLACK, (-5, -9, -1.5, 5, -5, -0.5))
    for side in (-1, 1):
        cx = side * 2.6

        def ear(x, y, z, cx=cx):
            # a triangle standing on the band, a little rounded at the tip
            h = -9 - y
            return 0 <= h <= 4.2 and abs(x - cx) <= 2.0 * (1 - h / 4.4)

        def colour(x, y, z, cx=cx):
            h = -9 - y
            inner = z < -1.0 and 0.4 <= h <= 3.0 and abs(x - cx) <= 1.2 * (1 - h / 3.4)
            return PINK if inner else BLACK
        v.fill(ear, colour, (cx - 2.2, -13.5, -1.5, cx + 2.2, -9, -0.5))
    return v


def devil_horns():
    v = Voxels(0.5)
    for side in (-1, 1):
        # a cone along a curve: from the top of the forehead out and up, its tip turning back in
        p0, p1, p2 = np.array([2.1, -8.0, -2.0]), np.array([4.4, -10.4, -1.8]), np.array([3.4, -13.8, -0.6])
        steps = 60
        for i in range(steps + 1):
            t = i / steps
            c = (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t * t * p2
            x, y, z = side * c[0], c[1], c[2]
            r = 1.15 * (1 - t) ** 0.8 + 0.18

            def inside(px, py, pz, x=x, y=y, z=z, r=r):
                return (px - x) ** 2 + (py - y) ** 2 + (pz - z) ** 2 <= r * r

            def colour(px, py, pz, t=t, x=x, y=y):
                # deep red at the base to almost black at the tip, lighter on the upper outer side
                shine = 1.25 if py < y - 0.15 and (px - x) * side > 0 else 1.0
                k = 1 - 0.75 * t
                return (min(255, round(196 * k * shine)), min(255, round(28 * k * shine)), min(255, round(32 * k * shine)))
            v.fill(inside, colour, (x - r - 0.5, y - r - 0.5, z - r - 0.5, x + r + 0.5, y + r + 0.5, z + r + 0.5))
        # a ring where the horn meets the head
    return v


def headphones():
    v = Voxels(0.5)
    cy = -3.6
    radius = 5.7

    def band(x, y, z):
        d = math.hypot(x, y - cy)
        return -1 <= z <= 1 and radius - 0.5 <= d <= radius + 0.5 and y <= cy - 1.5

    def band_colour(x, y, z):
        d = math.hypot(x, y - cy)
        return RAGO_RED if d > radius and abs(z) < 0.5 else BLACK
    v.fill(band, band_colour, (-7, -10, -1, 7, cy, 1))
    for side in (-1, 1):
        def cup(x, y, z, side=side):
            return 4.6 <= x * side <= 6.6 and (y - cy) ** 2 + z ** 2 <= 2.6 ** 2

        def cup_colour(x, y, z, side=side):
            r = math.hypot(y - cy, z)
            outer = x * side >= 6.1
            if outer and 1.4 <= r <= 2.0:
                return RAGO_RED
            if x * side < 5.1:
                return (60, 60, 68)  # the soft cushion against the head
            return BLACK
        v.fill(cup, cup_colour, (side * 4.6 if side > 0 else -6.6, cy - 2.6, -2.6, 6.6 if side > 0 else -4.6, cy + 2.6, 2.6))
    return v


def kitsune():
    """Made facing out of the left side of the head, then tilted back a little."""
    v = Voxels(0.5)
    white, red, black, shade = (246, 244, 240), (214, 36, 48), (24, 22, 26), (228, 224, 220)

    def half_width(b):
        """Half the face's width at height b (down from the brow), the chin narrowing."""
        return 3.3 if b < 0.6 else 3.3 - (b - 0.6) * 0.95

    def inside(a, b, w):
        # the face, a little domed; the snout standing out low in the middle; the ears on top
        if -2.6 <= b <= 3.0 and abs(a) <= half_width(b):
            dome = 1.0 + 0.5 * max(0.0, 1 - abs(a) / 3.3)
            snout = 1.6 * max(0.0, 1 - abs(a) / 1.1) if 0.4 <= b <= 3.0 else 0
            return 0 <= w <= dome + snout
        for ear in (-2.1, 2.1):
            h = -2.6 - b
            if 0 <= h <= 3.4 and abs(a - ear) <= 1.4 * (1 - h / 3.6) and 0 <= w <= 1.0:
                return True
        return False

    def colour(a, b, w):
        top = not inside(a, b, w + 0.5)
        for ear in (-2.1, 2.1):
            h = -2.6 - b
            if 0.3 <= h <= 2.4 and abs(a - ear) <= 0.7 * (1 - h / 2.6) and top:
                return red
        if not top:
            return shade
        if 2.2 <= b <= 3.0 and abs(a) <= 0.6:
            return black  # the nose
        for eye in (-1.6, 1.6):
            # slanted eye slits
            if abs((b + 0.3) - (a - eye) * 0.45 * (1 if eye > 0 else -1)) <= 0.3 and abs(a - eye) <= 0.9:
                return red
        if abs(a) <= 0.35 and -2.4 <= b <= -1.0:
            return red  # the mark on the forehead
        for cheek in (-2.5, 2.5):
            if abs(a - cheek) <= 0.6 and 1.1 <= b <= 1.4:
                return red
        return white

    # local (a across, b down, w out of the mask) -> head (x out of the side, y, z)
    for i in range(int(-3.5 / 0.5), int(3.5 / 0.5)):
        for j in range(int(-6.2 / 0.5), int(3.0 / 0.5)):
            for k in range(0, int(3.0 / 0.5)):
                a, b, w = (i + 0.5) * 0.5, (j + 0.5) * 0.5, (k + 0.5) * 0.5
                if inside(a, b, w):
                    v.cells[(k, j, i)] = colour(a, b, w)
    tilt = math.radians(-22)
    turn = math.radians(12)
    rx = np.array([[1, 0, 0], [0, math.cos(tilt), -math.sin(tilt)], [0, math.sin(tilt), math.cos(tilt)]])
    ry = np.array([[math.cos(turn), 0, math.sin(turn)], [0, 1, 0], [-math.sin(turn), 0, math.cos(turn)]])
    v.matrix = ry @ rx
    # against the left side of the head (x = 4.5 is the skin layer), above the ear, toward the back
    v.offset = np.array([4.7, -5.2, 0.8])
    return v


HATS = (('cat_ears', cat_ears), ('devil_horns', devil_horns), ('headphones', headphones), ('kitsune', kitsune))
