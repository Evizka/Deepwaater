"""Generates the 16x16 block textures of the Deepwaater mod.

Pure Python (no PIL needed). Run from the repository root:
    python3 tools/generate_textures.py
Output goes to src/main/resources/assets/deepwaater/textures/block/.
"""
import os
import random
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "deepwaater", "textures", "block")
SIZE = 16


def write_png(path, pixels):
    """pixels: list of 16 rows, each a list of 16 (r, g, b) tuples."""
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in pixels)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0)  # 8-bit RGB
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def clamp(v):
    return max(0, min(255, int(v)))


def noisy(rng, base, amount):
    return tuple(clamp(c + rng.uniform(-amount, amount)) for c in base)


def make(name, fn, seed):
    rng = random.Random(seed)
    pixels = [[fn(rng, x, y) for x in range(SIZE)] for y in range(SIZE)]
    write_png(os.path.join(OUT, name + ".png"), pixels)
    print("wrote", name)


BARK = (92, 66, 48)
BARK_DARK = (58, 40, 30)
LEAF = (38, 74, 38)
ROOT = (84, 60, 40)
MUD = (62, 52, 44)
MOSS = (74, 112, 52)


def log_side(rng, x, y):
    # vertical grain: each column gets its own shade, with some dark knots
    column_shade = rng.uniform(-14, 14)
    base = noisy(rng, BARK, 6)
    shade = column_shade + (-30 if (y % 5 == 0 and x % 7 in (2, 3)) else 0)
    return tuple(clamp(c + shade) for c in base)


def log_top(rng, x, y):
    # growth rings around the centre
    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
    ring = int(d) % 2
    base = BARK_DARK if ring else BARK
    return noisy(rng, base, 5)


def leaves(rng, x, y):
    spot = rng.random()
    shade = 22 if spot < 0.18 else (-14 if spot > 0.82 else 0)
    return tuple(clamp(c + shade) for c in noisy(rng, LEAF, 6))


def roots(rng, x, y):
    # darker knotted fibres running diagonally
    fibre = ((x + y) % 5 == 0) or ((x - y) % 7 == 0)
    base = tuple(c - 22 for c in ROOT) if fibre else ROOT
    return noisy(rng, base, 6)


def mud(rng, x, y):
    return noisy(rng, MUD, 9)


def moss(rng, x, y):
    spot = rng.random()
    shade = 26 if spot < 0.22 else (-18 if spot > 0.85 else 0)
    return tuple(clamp(c + shade) for c in noisy(rng, MOSS, 6))


if __name__ == "__main__":
    make("deep_bog_log", log_side, 1)
    make("deep_bog_log_top", log_top, 2)
    make("deep_bog_leaves", leaves, 3)
    make("deep_bog_roots", roots, 4)
    make("bog_mud", mud, 5)
    make("bog_moss", moss, 6)
