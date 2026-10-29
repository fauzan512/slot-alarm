#!/usr/bin/env python3
"""Generate icon PNG Slot Alarm — lonceng merah di atas background gelap.
Dipakai buat semua density mipmap + round variant."""
from PIL import Image, ImageDraw

BG = (26, 26, 26, 255)          # #1A1A1A
BG_TOP = (36, 36, 36, 255)      # gradasi simulasi
RED = (255, 82, 82, 255)        # #FF5252
RED_HI = (255, 138, 128, 255)   # #FF8A80

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

def draw_bell(d, cx, cy, s):
    """Lonceng: badan, lip, pemukul, pegangan. s = skala (0..1 dari canvas)."""
    # pegangan atas
    r = 0.055 * s
    d.ellipse([cx - r, cy - 0.40 * s, cx + r, cy - 0.40 * s + 2 * r], fill=RED)

    # badan lonceng (trapesium membulat) — pakai polygon + ellipse
    top_w = 0.16 * s
    bot_w = 0.30 * s
    top_y = cy - 0.34 * s
    bot_y = cy + 0.20 * s
    d.polygon([
        (cx - top_w, top_y), (cx + top_w, top_y),
        (cx + bot_w, bot_y), (cx - bot_w, bot_y),
    ], fill=RED)
    # lengkung bawah
    d.ellipse([cx - bot_w, bot_y - 0.06 * s, cx + bot_w, bot_y + 0.10 * s], fill=RED)

    # sorot dalam (bagian kiri lebih terang)
    d.polygon([
        (cx - top_w * 0.75, top_y + 0.02 * s),
        (cx - 0.02 * s, top_y + 0.02 * s),
        (cx - bot_w * 0.70, bot_y - 0.02 * s),
        (cx - bot_w * 0.70, bot_y + 0.04 * s),
    ], fill=RED_HI)

    # lip bawah
    lip_h = 0.055 * s
    d.rounded_rectangle(
        [cx - bot_w - 0.02 * s, bot_y + 0.08 * s,
         cx + bot_w + 0.02 * s, bot_y + 0.08 * s + lip_h],
        radius=lip_h / 2, fill=RED,
    )

    # pemukul
    pr = 0.10 * s
    py = bot_y + 0.30 * s
    d.ellipse([cx - pr, py - pr, cx + pr, py + pr], fill=RED)


def make(path, size, round_icon=False):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # background: gradasi vertikal sederhana (per-baris)
    for y in range(size):
        t = y / max(size - 1, 1)
        d.line([(0, y), (size, y)], fill=lerp(BG_TOP, BG, t))

    # cincin aksen
    pad = size * 0.06
    d.ellipse([pad, pad, size - pad, size - pad],
              outline=(58, 31, 31, 255), width=max(1, size // 48))

    if round_icon:
        # potong jadi lingkaran
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size, size], fill=255)
        img.putalpha(mask)

    draw_bell(d, size / 2, size / 2, size)
    img.save(path, "PNG", optimize=True)
    print(f"{path} ({size}px)")


if __name__ == "__main__":
    import os
    base = "/home/ubuntu/slot-alarm-apk/app/src/main/res"
    for dname, px in DENSITIES.items():
        os.makedirs(f"{base}/{dname}", exist_ok=True)
        make(f"{base}/{dname}/ic_launcher.png", px)
        make(f"{base}/{dname}/ic_launcher_round.png", px, round_icon=True)
