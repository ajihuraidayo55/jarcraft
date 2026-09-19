#!/usr/bin/env python3
"""Generate a Minecraft-style grass block icon (multi-size ICO) with PIL."""
from PIL import Image, ImageDraw
import random

SIZES = [16, 32, 48, 64, 128, 256]

def make(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rng = random.Random(1337)
    s = size
    top_h = int(s * 0.34)

    # grass top
    for y in range(top_h):
        for x in range(s):
            n = rng.randint(-14, 14)
            d.point((x, y), fill=(106 + n, 170 + n, 64 + n, 255))
    # dirt body
    for y in range(top_h, s):
        for x in range(s):
            n = rng.randint(-12, 12)
            d.point((x, y), fill=(134 + n, 96 + n, 67 + n, 255))
    # grass fringe dripping into the dirt
    for x in range(s):
        drop = rng.randint(0, max(1, s // 16)) + (1 if s >= 32 else 0)
        for y in range(top_h, min(s, top_h + drop)):
            n = rng.randint(-14, 14)
            d.point((x, y), fill=(96 + n, 156 + n, 58 + n, 255))
    # subtle border
    if s >= 32:
        d.rectangle([0, 0, s - 1, s - 1], outline=(60, 44, 30, 255))
    return img

imgs = [(sz, make(sz)) for sz in SIZES]
imgs[-1][1].save("/home/z/my-project/launcher/jarcraft.ico",
                 format="ICO", sizes=[(sz, sz) for sz, _ in imgs])
print("icon written")
