#!/usr/bin/env python3
import struct
import zlib
import os
import sys

def make_png(w, h, r, g, b):
    def chunk(t, d):
        x = struct.pack('>I', len(d)) + t + d
        return x + struct.pack('>I', zlib.crc32(x[4:]) & 0xffffffff)
    raw = b''.join(b'\x00' + bytes([r, g, b, 255] * w) for _ in range(h))
    return (
        b'\x89PNG\r\n\x1a\n'
        + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
        + chunk(b'IDAT', zlib.compress(raw))
        + chunk(b'IEND', b'')
    )

project = sys.argv[1]
r, g, b = int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])

sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192,
}

for density, px in sizes.items():
    d = f'{project}/app/src/main/res/mipmap-{density}'
    os.makedirs(d, exist_ok=True)
    data = make_png(px, px, r, g, b)
    with open(f'{d}/ic_launcher.png', 'wb') as f:
        f.write(data)
    with open(f'{d}/ic_launcher_round.png', 'wb') as f:
        f.write(data)
    print(f'Created {d}/ic_launcher.png ({px}x{px})')

print('Icons done!')
