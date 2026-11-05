import os
import numpy as np
from PIL import Image, ImageDraw

SS = 4  # supersample factor

STOPS = [(0.0, (0x42,0x85,0xF4)), (0.55, (0x7C,0x6C,0xF0)), (1.0, (0xE8,0x5D,0x9E))]

def lerp_stops(t):
    for i in range(len(STOPS)-1):
        t0, c0 = STOPS[i]; t1, c1 = STOPS[i+1]
        if t <= t1 or i == len(STOPS)-2:
            k = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            k = min(max(k, 0.0), 1.0)
            return tuple(int(round(c0[j] + (c1[j]-c0[j])*k)) for j in range(3))
    return STOPS[-1][1]

def gradient(size):
    w = h = size
    xs = np.linspace(0, 1, w)
    ys = np.linspace(0, 1, h)
    t = (xs[None, :] + ys[:, None]) / 2.0
    out = np.zeros((h, w, 3), dtype=np.uint8)
    # Piecewise interpolation across the three stops.
    for i in range(len(STOPS)-1):
        t0, c0 = STOPS[i]; t1, c1 = STOPS[i+1]
        mask = (t >= t0) & (t <= t1) if i < len(STOPS)-2 else (t >= t0)
        k = np.clip((t - t0) / (t1 - t0), 0, 1)
        for j in range(3):
            seg = c0[j] + (c1[j] - c0[j]) * k
            out[..., j] = np.where(mask, seg, out[..., j])
    return Image.fromarray(out, "RGB")

def bez(p0, c1, c2, p3, n=80):
    pts = []
    for i in range(n):
        t = i / n
        mt = 1 - t
        x = mt**3*p0[0] + 3*mt*mt*t*c1[0] + 3*mt*t*t*c2[0] + t**3*p3[0]
        y = mt**3*p0[1] + 3*mt*mt*t*c1[1] + 3*mt*t*t*c2[1] + t**3*p3[1]
        pts.append((x, y))
    return pts

def star_points(tx, ty, s, scale):
    """The master star path, transformed by (translate, scale) then canvas scale."""
    segs = [
        ((50,5),(59.5,35.5),(64.5,40.5),(95,50)),
        ((95,50),(64.5,59.5),(59.5,64.5),(50,95)),
        ((50,95),(40.5,64.5),(35.5,59.5),(5,50)),
        ((5,50),(35.5,40.5),(40.5,35.5),(50,5)),
    ]
    pts = []
    for p0,c1,c2,p3 in segs:
        pts += bez(p0,c1,c2,p3)
    return [((tx + s*x) * scale, (ty + s*y) * scale) for x, y in pts]

def mask_from(outer, inner, size):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.polygon(outer, fill=255)
    if inner:
        d.polygon(inner, fill=0)
    return m

def render_composite(size, tx, ty, s, itx, ity, is_, unit, circle=False):
    big = size * SS
    scale = big / unit
    bg = gradient(big)
    m = mask_from(star_points(tx, ty, s, scale), star_points(itx, ity, is_, scale), big)
    white = Image.new("RGB", (big, big), (255,255,255))
    img = Image.composite(white, bg, m)
    if circle:
        cm = Image.new("L", (big, big), 0)
        ImageDraw.Draw(cm).ellipse((0,0,big-1,big-1), fill=255)
        img = Image.composite(img, Image.new("RGB", (big,big), (255,255,255)), cm)
    return img.resize((size, size), Image.LANCZOS)

def render_layer(size, tx, ty, s, itx, ity, is_, unit):
    big = size * SS
    scale = big / unit
    m = mask_from(star_points(tx, ty, s, scale), star_points(itx, ity, is_, scale), big)
    img = Image.new("RGBA", (big, big), (255,255,255,0))
    img.putalpha(m)
    white = Image.new("RGB", (big, big), (255,255,255))
    img.paste(white, (0,0), m)
    return img.resize((size, size), Image.LANCZOS)

def render_background(size, unit):
    big = size * SS
    return gradient(big).resize((size, size), Image.LANCZOS)

root = "/root/Starveil"
icons = os.path.join(root, "icon-final")

# --- icon-final raster set (composite: 100-unit canvas, star 70% at 15, cutout 30% at 35)
for n in (512, 192, 144, 96, 72, 48):
    render_composite(n, 15, 15, 0.70, 35, 35, 0.30, 100).save(f"{icons}/icon-composite-{n}.png")

# --- adaptive layers (108-unit canvas)
# Star 0.60 at 24 (54% of the canvas), cutout 0.2728 at 40.36 — the master
# ratio (0.4545) is preserved, the whole mark just sits smaller inside the
# adaptive canvas so a launcher that enlarges it does not clip the corners.
render_background(432, 108).save(f"{icons}/ic_launcher_background-432.png")
render_layer(432, 24, 24, 0.60, 40.36, 40.36, 0.2728, 108).save(f"{icons}/starveil-icon-foreground-432.png")
render_layer(432, 24, 24, 0.60, 40.36, 40.36, 0.2728, 108).save(f"{icons}/starveil-icon-monochrome-432.png")

# --- Android resources
res = os.path.join(root, "app/src/main/res")
buckets = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
for name, f in buckets.items():
    d = os.path.join(res, f"mipmap-{name}")
    os.makedirs(d, exist_ok=True)
    legacy = int(48 * f)
    render_composite(legacy, 15, 15, 0.70, 35, 35, 0.30, 100).save(f"{d}/ic_launcher.png")
    render_composite(legacy, 15, 15, 0.70, 35, 35, 0.30, 100, circle=True).save(f"{d}/ic_launcher_round.png")

print("rendered")
