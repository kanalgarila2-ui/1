#!/usr/bin/env python3
import sys, os, struct, zlib, math

def write_png(path, w, h, pixels):
    def chunk(name, data):
        c = name + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    raw = b''
    for y in range(h):
        raw += b'\x00'
        for x in range(w):
            r,g,b,a = pixels[y*w+x]; raw += bytes([r,g,b,a])
    sig = b'\x89PNG\r\n\x1a\n'
    ihdr = struct.pack('>II', w, h) + bytes([8,6,0,0,0])
    png = sig + chunk(b'IHDR', ihdr) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(png)

def circle_px(cx, cy, r, x, y): return (x-cx)**2 + (y-cy)**2 <= r**2

def make_icon(proj_dir, r, g, b):
    densities = {'mipmap-mdpi':48,'mipmap-hdpi':72,'mipmap-xhdpi':96,'mipmap-xxhdpi':144,'mipmap-xxxhdpi':192}
    for density, px in densities.items():
        res_dir = os.path.join(proj_dir, 'app', 'src', 'main', 'res', density)
        os.makedirs(res_dir, exist_ok=True)
        pixels, half = [], px / 2
        for y in range(px):
            for x in range(px):
                if circle_px(half, half, half-1, x+0.5, y+0.5):
                    dist = math.sqrt((x-half)**2+(y-half)**2)/half
                    br = max(0.0, 1.0-dist*0.3)
                    pixels.append((int(r*br), int(g*br), int(b*br), 255))
                else: pixels.append((0,0,0,0))
        write_png(os.path.join(res_dir,'ic_launcher.png'), px, px, pixels)
        write_png(os.path.join(res_dir,'ic_launcher_round.png'), px, px, pixels)
    print(f"Icons: {proj_dir}")

def make_frozen_overlay(path, size=108):
    half, pixels = size/2, []
    for y in range(size):
        for x in range(size):
            nx,ny = (x-half)/half,(y-half)/half
            dist = math.sqrt(nx**2+ny**2)
            arm = abs(math.sin(math.atan2(ny,nx)*3))
            if dist < 0.9 and (arm > 0.85 or dist < 0.15):
                pixels.append((103,232,249,int(200*(1-dist))))
            else: pixels.append((0,0,0,0))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    write_png(path, size, size, pixels)

def make_deleted_avatar(path, size=108):
    half, pixels = size/2, []
    for y in range(size):
        for x in range(size):
            if circle_px(half, half, half-1, x+0.5, y+0.5):
                ry = (y-size*0.3)/(size*0.2)
                if 0<=ry<=1 and abs(x-half)<size*0.4: pixels.append((239,68,68,255))
                else: pixels.append((45,27,27,255))
            else: pixels.append((0,0,0,0))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    write_png(path, size, size, pixels)

if __name__ == '__main__':
    if len(sys.argv) < 5: print("Usage: make_icons.py <dir> <R> <G> <B>"); sys.exit(1)
    proj, rv, gv, bv = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
    make_icon(proj, rv, gv, bv)
    if 'client' in proj:
        drawable = os.path.join(proj, 'app', 'src', 'main', 'res', 'drawable')
        os.makedirs(drawable, exist_ok=True)
        make_frozen_overlay(os.path.join(drawable, 'frozen_overlay.png'))
        make_deleted_avatar(os.path.join(drawable, 'deleted_avatar.png'))
