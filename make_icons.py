#!/usr/bin/env python3
"""
VerySchool icon generator.
Usage:
  python3 make_icons.py client 108 92 231   # purple #6C5CE7
  python3 make_icons.py server 0 184 148    # green #00B894
"""
import sys, os, struct, zlib, math

def write_png(path, width, height, pixels):
    """pixels: list of (r,g,b,a) tuples, row-major"""
    def chunk(name, data):
        c = name + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    raw = b''
    for y in range(height):
        raw += b'\x00'
        for x in range(width):
            r,g,b,a = pixels[y*width+x]
            raw += bytes([r,g,b,a])
    compressed = zlib.compress(raw, 9)
    sig = b'\x89PNG\r\n\x1a\n'
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)
    ihdr_data = struct.pack('>II', width, height) + bytes([8,6,0,0,0])
    png = sig + chunk(b'IHDR', ihdr_data) + chunk(b'IDAT', compressed) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)

def circle_px(cx, cy, r, x, y):
    return (x-cx)**2 + (y-cy)**2 <= r**2

def make_icon(proj_dir, r, g, b, size=108, fg_size=92):
    """Generate launcher icon PNGs for all densities."""
    densities = {
        'mipmap-mdpi':    48,
        'mipmap-hdpi':    72,
        'mipmap-xhdpi':   96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi':192,
    }
    for density, px in densities.items():
        res_dir = os.path.join(proj_dir, 'app', 'src', 'main', 'res', density)
        os.makedirs(res_dir, exist_ok=True)
        pixels = []
        half = px / 2
        for y in range(px):
            for x in range(px):
                # Background circle
                if circle_px(half, half, half-1, x+0.5, y+0.5):
                    # "VS" text area - simple gradient
                    dist = math.sqrt((x-half)**2 + (y-half)**2) / half
                    brightness = max(0.0, 1.0 - dist * 0.3)
                    pr = int(r * brightness)
                    pg = int(g * brightness)
                    pb = int(b * brightness)
                    pixels.append((pr, pg, pb, 255))
                else:
                    pixels.append((0,0,0,0))
        write_png(os.path.join(res_dir, 'ic_launcher.png'), px, px, pixels)
        write_png(os.path.join(res_dir, 'ic_launcher_round.png'), px, px, pixels)
    print(f"Icons generated for {proj_dir}")

def make_frozen_overlay(output_path, size=108):
    """Generate snowflake overlay for frozen accounts."""
    px = size
    pixels = []
    half = px / 2
    for y in range(px):
        for x in range(px):
            # Semi-transparent cyan overlay with snowflake pattern
            nx = (x - half) / half
            ny = (y - half) / half
            # Snowflake: 6 arms
            angle = math.atan2(ny, nx)
            dist = math.sqrt(nx**2 + ny**2)
            arm = abs(math.sin(angle * 3))
            if dist < 0.9 and (arm > 0.85 or dist < 0.15):
                alpha = int(200 * (1 - dist))
                pixels.append((103, 232, 249, alpha))
            else:
                pixels.append((0, 0, 0, 0))
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    write_png(output_path, px, px, pixels)
    print(f"Frozen overlay: {output_path}")

def make_deleted_avatar(output_path, size=108):
    """Generate 'DELETED ACCOUNT' placeholder avatar."""
    px = size
    pixels = []
    half = px / 2
    for y in range(px):
        for x in range(px):
            if circle_px(half, half, half-1, x+0.5, y+0.5):
                # Dark red background
                ry = (y - px*0.3) / (px * 0.2)
                if 0 <= ry <= 1 and abs(x - half) < px * 0.4:
                    pixels.append((239, 68, 68, 255))  # Red
                else:
                    pixels.append((45, 27, 27, 255))
            else:
                pixels.append((0, 0, 0, 0))
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    write_png(output_path, px, px, pixels)
    print(f"Deleted avatar: {output_path}")

if __name__ == '__main__':
    if len(sys.argv) < 5:
        print("Usage: python3 make_icons.py <proj_dir> <R> <G> <B>")
        sys.exit(1)
    proj = sys.argv[1]
    rv, gv, bv = int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
    make_icon(proj, rv, gv, bv)
    # Also generate special avatars for client
    if 'client' in proj:
        drawable = os.path.join(proj, 'app', 'src', 'main', 'res', 'drawable')
        os.makedirs(drawable, exist_ok=True)
        make_frozen_overlay(os.path.join(drawable, 'frozen_overlay.png'))
        make_deleted_avatar(os.path.join(drawable, 'deleted_avatar.png'))
