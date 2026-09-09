# Genera el icono de Cretaceous Park: cabeza del Dilophosaurus (modelo voxel del juego, misma proyeccion
# isometrica 2:1 y mismo sombreado top/izq/der que IsoRenderer.drawBox) sobre fondo azul cielo.
from PIL import Image, ImageDraw
import os, sys

RES = sys.argv[1]      # app/src/main/res
STORE = sys.argv[2]    # carpeta store (icono 512)

def shade(c, f):
    r, g, b = c
    return (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)))

def hexc(h): return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

body = hexc("5C4F47"); red = hexc("C43A2E"); orange = hexc("E39A3C"); face = hexc("EADDC6")
cream = hexc("F4F1EA"); eye = hexc("1B1B1B"); dark = shade(body, 0.72)

parts = []  # (fc, lc, z, lenF, lenL, h, color)
def part(fc, lc, z, lF, lL, h, col): parts.append((fc, lc, z, lF, lL, h, col))

# cuello en S, garganta naranja y crin oscura (receta DinoModels.dilophosaurus)
part(0.65, 0, 0.85, 0.26, 0.26, 0.4, body)
part(0.9, 0, 1.1, 0.22, 0.22, 0.4, body)
part(0.79, 0, 0.9, 0.02, 0.2, 0.3, orange)
part(1.02, 0, 1.13, 0.02, 0.16, 0.26, orange)
part(0.6, 0, 1.25, 0.14, 0.16, 0.14, dark)
part(0.88, 0, 1.5, 0.1, 0.14, 0.12, dark)
# cabeza
part(1.175, 0, 1.49, 0.25, 0.26, 0.2, body)
part(1.45, 0, 1.49, 0.3, 0.24, 0.17, face)
part(1.3, 0, 1.4, 0.5, 0.22, 0.09, shade(face, 0.9))
part(1.57, 0, 1.38, 0.14, 0.23, 0.02, cream)
for sg in (-1, 1):
    part(1.2, sg * (0.13 + 0.015), 1.53, 0.12, 0.03, 0.12, red)
    part(1.2, sg * (0.16 + 0.01), 1.56, 0.06, 0.02, 0.06, eye)
    part(0.98, sg * 0.07, 1.69, 0.14, 0.05, 0.3, red)
    part(0.98, sg * 0.07, 1.99, 0.14, 0.05, 0.06, dark)
    part(1.2, sg * 0.07, 1.69, 0.3, 0.05, 0.18, red)

# marco local -> mundo (facing=1: adelante = +x)
boxes = []
for fc, lc, z, lF, lL, h, col in parts:
    boxes.append(dict(x0=fc - lF / 2, x1=fc + lF / 2, y0=lc - lL / 2, y1=lc + lL / 2, z0=z, z1=z + h, c=col))

# orden de pintado: A antes que B si A queda detras en algun eje
EPS = 1e-4
n = len(boxes)
after = [set() for _ in range(n)]
for i in range(n):
    for j in range(n):
        if i == j: continue
        a, b = boxes[i], boxes[j]
        if a['x1'] <= b['x0'] + EPS or a['y1'] <= b['y0'] + EPS or a['z1'] <= b['z0'] + EPS:
            after[i].add(j)   # i antes que j
order = []; state = [0] * n
def visit(i):
    if state[i]: return
    state[i] = 1
    for j in range(n):
        if i in after[j]: visit(j)   # j debe ir antes que i
    state[i] = 2; order.append(i)
for i in range(n): visit(i)
# visit anade i tras sus predecesores -> order ya es "de atras hacia delante"

def render(size, frac, cx_frac=0.5, cy_frac=0.5, ss=4):
    """Dibuja la cabeza con z-buffer por pixel (sin depender del orden de pintado) en un lienzo transparente."""
    import numpy as np
    W = size * ss
    def sx(x, y, tH): return (x - y) * tH
    def sy(x, y, z, tH): return (x + y) * tH / 2 - z * tH * 1.15
    head = [b for b in boxes if b['x0'] >= 0.9]
    pts = []
    for b in head:
        for x in (b['x0'], b['x1']):
            for y in (b['y0'], b['y1']):
                for z in (b['z0'], b['z1']):
                    pts.append((sx(x, y, 1), sy(x, y, z, 1)))
    minx = min(p[0] for p in pts); maxx = max(p[0] for p in pts)
    miny = min(p[1] for p in pts); maxy = max(p[1] for p in pts)
    tH = W * frac / max(maxy - miny, (maxx - minx) * 0.9)
    ox = W * cx_frac - (minx + maxx) / 2 * tH
    oy = W * cy_frac - (miny + maxy) / 2 * tH
    def P(x, y, z): return (ox + sx(x, y, tH), oy + sy(x, y, z, tH))
    color = np.zeros((W, W, 4), dtype=np.uint8)
    depth = np.full((W, W), -1e9, dtype=np.float64)
    ys, xs = np.mgrid[0:W, 0:W]
    SX = (xs + 0.5 - ox) / tH            # x - y
    SY = (ys + 0.5 - oy) / tH            # (x + y)/2 - 1.15 z
    def paint(poly, col, kind, b):
        m = Image.new("L", (W, W), 0); ImageDraw.Draw(m).polygon(poly, fill=255)
        mask = np.array(m) > 0
        if not mask.any(): return
        if kind == 'top':
            z = np.full((W, W), b['z1']); s = 2 * (SY + 1.15 * z); x = (s + SX) / 2; y = (s - SX) / 2
        elif kind == 'y':
            y = np.full((W, W), b['y1']); x = SX + y; z = ((x + y) / 2 - SY) / 1.15
        else:
            x = np.full((W, W), b['x1']); y = x - SX; z = ((x + y) / 2 - SY) / 1.15
        d = x + y + z
        upd = mask & (d > depth)
        depth[upd] = d[upd]
        color[upd] = col + (255,)
    for b in boxes:
        x0, x1, y0, y1, z0, z1, c = b['x0'], b['x1'], b['y0'], b['y1'], b['z0'], b['z1'], b['c']
        paint([P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)], c, 'top', b)
        paint([P(x0, y1, z1), P(x1, y1, z1), P(x1, y1, z0), P(x0, y1, z0)], shade(c, 0.86), 'y', b)
        paint([P(x1, y0, z1), P(x1, y1, z1), P(x1, y1, z0), P(x1, y0, z0)], shade(c, 0.7), 'x', b)
    img = Image.fromarray(color, "RGBA")
    return img.resize((size, size), Image.LANCZOS)

def sky(size):
    top, bot = hexc("55B4EC"), hexc("A9DCF6")
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        t = y / max(1, size - 1)
        col = tuple(int(top[k] + (bot[k] - top[k]) * t) for k in range(3)) + (255,)
        for x in range(size): px[x, y] = col
    return img

def mask(size, round_):
    ss = 4; W = size * ss
    m = Image.new("L", (W, W), 0); d = ImageDraw.Draw(m)
    if round_: d.ellipse([0, 0, W - 1, W - 1], fill=255)
    else: d.rounded_rectangle([0, 0, W - 1, W - 1], radius=int(W * 0.18), fill=255)
    return m.resize((size, size), Image.LANCZOS)

dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
# capa adaptativa (108dp): cabeza ~ zona segura de 66dp, ligeramente por encima del centro para que el cuello quede abajo
for name, k in dens.items():
    size = int(108 * k)
    fg = render(size, 0.62, 0.54, 0.46)
    os.makedirs(f"{RES}/drawable-{name}", exist_ok=True)
    fg.save(f"{RES}/drawable-{name}/ic_launcher_fg.png")
    sky(size).save(f"{RES}/drawable-{name}/ic_launcher_bg.png")
# iconos heredados (48dp) compuestos con mascara
for name, k in dens.items():
    size = int(48 * k)
    full = sky(size); full.alpha_composite(render(size, 0.70, 0.54, 0.46))
    for round_, fn in ((False, "ic_launcher.png"), (True, "ic_launcher_round.png")):
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(full, (0, 0), mask(size, round_))
        os.makedirs(f"{RES}/mipmap-{name}", exist_ok=True)
        out.save(f"{RES}/mipmap-{name}/{fn}")
# icono de tienda 512 (cuadrado, sin transparencia)
os.makedirs(STORE, exist_ok=True)
st = sky(512); st.alpha_composite(render(512, 0.66, 0.54, 0.46)); st.convert("RGB").save(f"{STORE}/icono_512.png")
print("ok", len(order), "cajas")
