"""Synthesizes Rago's action sounds (hit, hurt and block-break sounds to choose from in game) into
assets/rago/sounds/action, and writes assets/rago/sounds.json.

Every sound is made here from sine waves and filtered noise; nothing is sampled. Needs numpy and
ffmpeg (with libvorbis). Run with `python3 tools/sounds.py`.
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'rago')
OUT = os.path.join(RES, 'sounds', 'action')
RATE = 44100
rng = np.random.default_rng(7)


def t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def sweep(f0, f1, seconds, curve=3.0):
    """A sine gliding from f0 to f1 (exponentially), as an array."""
    x = t(seconds)
    k = (x / seconds) ** (1 / curve) if f1 < f0 else x / seconds
    freq = f0 * (f1 / f0) ** k
    return np.sin(2 * np.pi * np.cumsum(freq) / RATE)


def decay(n, tau):
    return np.exp(-np.arange(n) / RATE / tau)


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / RATE)
    y = np.zeros_like(x)
    prev = 0.0
    for i, v in enumerate(x):
        prev = (1 - a) * v + a * prev
        y[i] = prev
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def noise(seconds):
    return rng.uniform(-1, 1, int(RATE * seconds))


def fade_in(x, ms=1.5):
    n = int(RATE * ms / 1000)
    x[:n] *= np.linspace(0, 1, n)
    return x


def mix(*parts):
    n = max(int(RATE * at) + len(p) for p, at in parts)
    out = np.zeros(n)
    for p, at in parts:
        s = int(RATE * at)
        out[s:s + len(p)] += p[:n - s]
    return out


def pop():
    """A bubble pop: a quick downward glide with a tiny click on top."""
    body = sweep(1400, 320, 0.07) * decay(int(RATE * 0.07), 0.018)
    click = highpass(noise(0.004), 2500) * decay(int(RATE * 0.004), 0.001) * 0.5
    return fade_in(mix((body, 0), (click, 0)))


def click():
    """A crisp mechanical click: a bright transient and a short ring."""
    snap = highpass(noise(0.006), 3000) * decay(int(RATE * 0.006), 0.0012)
    ring = np.sin(2 * np.pi * 2300 * t(0.03)) * decay(int(RATE * 0.03), 0.006) * 0.6
    return fade_in(mix((snap, 0), (ring, 0.0005)), 0.3)


def tick():
    """A very short, high tick, as a rhythm game's hit sound."""
    body = np.sin(2 * np.pi * 5200 * t(0.02)) * decay(int(RATE * 0.02), 0.0035)
    body += np.sin(2 * np.pi * 7800 * t(0.02)) * decay(int(RATE * 0.02), 0.002) * 0.4
    return fade_in(body, 0.3)


def bubble():
    """Two pops one after the other, the second higher."""
    a = sweep(700, 380, 0.05) * decay(int(RATE * 0.05), 0.014)
    b = sweep(1300, 650, 0.06) * decay(int(RATE * 0.06), 0.016)
    return fade_in(mix((a, 0), (b * 0.9, 0.045)))


def ding():
    """A small bell: a few partials ringing out."""
    x = t(0.5)
    out = np.zeros_like(x)
    for mult, amp, tau in ((1, 1.0, 0.16), (2.01, 0.45, 0.09), (3.02, 0.25, 0.05), (4.2, 0.12, 0.03)):
        out += amp * np.sin(2 * np.pi * 1320 * mult * x) * np.exp(-x / tau)
    return fade_in(out, 0.8)


def thud():
    """A punch: a low knock with a soft body."""
    body = sweep(170, 55, 0.14) * decay(int(RATE * 0.14), 0.04)
    knock = lowpass(noise(0.03), 900) * decay(int(RATE * 0.03), 0.008) * 2.5
    return fade_in(mix((body, 0), (knock, 0)), 0.8)


def crunch():
    """A block giving way: grainy filtered noise that falls off."""
    n = noise(0.12)
    grains = (rng.uniform(0, 1, len(n)) > 0.985).astype(float)
    grains = np.convolve(grains, np.ones(60) / 6, mode='same')
    body = lowpass(n * (0.35 + grains), 2800) * decay(len(n), 0.035) * 2.2
    low = sweep(220, 90, 0.08) * decay(int(RATE * 0.08), 0.02) * 0.5
    return fade_in(mix((body, 0), (low, 0)))


SOUNDS = {
    'pop': pop, 'click': click, 'tick': tick, 'bubble': bubble,
    'ding': ding, 'thud': thud, 'crunch': crunch,
}


def save(name, samples):
    samples = samples / max(1e-9, np.max(np.abs(samples))) * 0.9
    pcm = (samples * 32767).astype('<i2')
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        path = tmp.name
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', path, '-c:a', 'libvorbis', '-q:a', '5',
                    os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(path)


def main():
    os.makedirs(OUT, exist_ok=True)
    events = {}
    for name, fn in SOUNDS.items():
        save(name, fn())
        events['action.' + name] = {'category': 'player', 'sounds': [{'name': 'action/' + name, 'stream': False}]}
    with open(os.path.join(RES, 'sounds.json'), 'w') as f:
        json.dump(events, f, indent=2)
        f.write('\n')


if __name__ == '__main__':
    main()
