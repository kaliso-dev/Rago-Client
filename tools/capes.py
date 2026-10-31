"""What Rago's capes are drawn with (tools/hd_capes.py draws them): the cape layout, gradients and the
R logo (textures/logo.png, a white mask). The original Rago cape (textures/cape.png) is kept as it is.
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'rago')
OUT = os.path.join(RES, 'textures', 'capes')
S = 16
W, H = 10 * S, 16 * S  # the outer face of the cape


def vertical(top, bottom, w=W, h=H):
    img = Image.new('RGBA', (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)) + (255,))
    return img


def logo(size, colour, glow=None):
    """The R logo tinted, optionally with a soft glow behind it, on a transparent square."""
    mask = Image.open(os.path.join(RES, 'textures', 'logo.png')).convert('RGBA').resize((size, size), Image.LANCZOS)
    alpha = mask.split()[3]
    pad = size // 3
    out = Image.new('RGBA', (size + 2 * pad, size + 2 * pad))
    if glow:
        g = Image.new('RGBA', out.size, glow + (0,))
        g.putalpha(Image.new('L', out.size))
        halo = Image.new('L', out.size)
        halo.paste(alpha, (pad, pad))
        halo = halo.filter(ImageFilter.GaussianBlur(size / 6))
        g.putalpha(halo.point(lambda v: min(255, v * 2)))
        out = Image.alpha_composite(out, g)
    tinted = Image.new('RGBA', (size, size), colour + (255,))
    tinted.putalpha(alpha)
    out.alpha_composite(tinted, (pad, pad))
    return out


def centred(face, img, cy):
    face.alpha_composite(img, ((W - img.width) // 2, cy - img.height // 2))


def text(face, s, cy, size, colour, weight='600'):
    font = ImageFont.truetype(os.path.join(RES, 'fonts', f'poppins-{weight}.ttf'), size)
    d = ImageDraw.Draw(face)
    box = d.textbbox((0, 0), s, font=font)
    d.text(((W - (box[2] - box[0])) // 2 - box[0], cy - (box[3] - box[1]) // 2 - box[1]), s, font=font, fill=colour)


def overlay(face, draw):
    """Draws see-through shapes on a layer of their own, blended over the face."""
    layer = Image.new('RGBA', face.size)
    draw(ImageDraw.Draw(layer))
    return Image.alpha_composite(face, layer)


def border(face, colour, width):
    d = ImageDraw.Draw(face)
    for i in range(width):
        d.rectangle([i, i, W - 1 - i, H - 1 - i], outline=colour)


def cape(face, edge, s=S, inside=None):
    """Places the outer face in the 64 x 32 cape layout (s texture pixels to a cape pixel), with plain
    edges, top and inside (the side against the back, dark unless given)."""
    img = Image.new('RGBA', (64 * s, 32 * s))
    d = ImageDraw.Draw(img)
    e = edge + (255,)
    dark = (inside or tuple(round(c * 0.35) for c in edge)) + (255,)
    d.rectangle([1 * s, 0, 11 * s - 1, 1 * s - 1], fill=e)  # top
    d.rectangle([11 * s, 0, 21 * s - 1, 1 * s - 1], fill=e)  # bottom
    d.rectangle([0, 1 * s, 1 * s - 1, 17 * s - 1], fill=e)  # side
    d.rectangle([11 * s, 1 * s, 12 * s - 1, 17 * s - 1], fill=e)  # side
    d.rectangle([12 * s, 1 * s, 22 * s - 1, 17 * s - 1], fill=dark)  # inside, against the back
    img.paste(face, (1 * s, 1 * s))
    return img
