# -*- coding: utf-8 -*-
"""
Genera i fotogrammi vettoriali delle icone meteo animate.

Ogni condizione ha 6 fotogrammi che il ViewFlipper del widget scorre in ciclo.
La geometria e' calcolata qui: nessun disegno copiato, nessun valore a caso.
Tavola 48x48.
"""
import math, os

DEST = '/home/user/GuidoCostalonga.github.io/meteo-android/app/src/main/res/drawable'
os.makedirs(DEST, exist_ok=True)

# colori del sistema visivo di costalonga.org
GIALLO   = '#FFE6C168'
GIALLO_S = '#FFC39F47'
NUVOLA   = '#FF93A8BA'
NUVOLA_S = '#FF6F869A'
PIOGGIA  = '#FF1A6098'
NEVE     = '#FF4C8FBF'
NEBBIA   = '#FF9FB3C4'
FULMINE  = '#FFD79A12'

def n(v):
    """Numero compatto: tre decimali, senza zeri inutili."""
    s = f'{v:.3f}'.rstrip('0').rstrip('.')
    return '0' if s in ('', '-0') else s

def cerchio(cx, cy, r):
    return (f'M{n(cx-r)},{n(cy)}'
            f'A{n(r)},{n(r)} 0 0 1 {n(cx+r)},{n(cy)}'
            f'A{n(r)},{n(r)} 0 0 1 {n(cx-r)},{n(cy)}Z')

def rett(x0, y0, x1, y1):
    return f'M{n(x0)},{n(y0)}L{n(x1)},{n(y0)}L{n(x1)},{n(y1)}L{n(x0)},{n(y1)}Z'

def nuvola(cx, cy, s):
    """Nuvola come unione di tre sbuffi, base e due estremi arrotondati."""
    p = []
    p.append(cerchio(cx + 0.0*s, cy - 2.0*s, 7.0*s))
    p.append(cerchio(cx - 8.0*s, cy + 1.0*s, 5.5*s))
    p.append(cerchio(cx + 8.0*s, cy + 0.5*s, 6.0*s))
    p.append(rett(cx - 13.0*s, cy + 0.0*s, cx + 14.0*s, cy + 6.0*s))
    p.append(cerchio(cx - 13.0*s, cy + 3.0*s, 3.0*s))
    p.append(cerchio(cx + 14.0*s, cy + 3.0*s, 3.0*s))
    return ''.join(p)

def goccia(x, y, s=1.0):
    """Goccia: punta in alto, pancia tonda in basso."""
    return (f'M{n(x)},{n(y)}'
            f'c{n(1.6*s)},{n(2.3*s)} {n(2.4*s)},{n(3.7*s)} {n(2.4*s)},{n(4.9*s)}'
            f'a{n(2.4*s)},{n(2.4*s)} 0 0 1 {n(-4.8*s)},0'
            f'c0,{n(-1.2*s)} {n(0.8*s)},{n(-2.6*s)} {n(2.4*s)},{n(-4.9*s)}Z')

def raggi(cx, cy, r0, r1, giro, quanti=8):
    """Raggi del sole: segmenti radiali, ruotati di 'giro' gradi."""
    d = []
    for i in range(quanti):
        a = math.radians(giro + i * 360.0 / quanti)
        d.append(f'M{n(cx + r0*math.cos(a))},{n(cy + r0*math.sin(a))}'
                 f'L{n(cx + r1*math.cos(a))},{n(cy + r1*math.sin(a))}')
    return ''.join(d)

def fiocco(cx, cy, r, giro):
    """Fiocco di neve: tre assi incrociati, ruotati."""
    d = []
    for i in range(3):
        a = math.radians(giro + i * 60.0)
        dx, dy = r*math.cos(a), r*math.sin(a)
        d.append(f'M{n(cx-dx)},{n(cy-dy)}L{n(cx+dx)},{n(cy+dy)}')
    return ''.join(d)

def falce(cx, cy, R, dx, dy, punti=72):
    """
    Falce di luna: parte interna al cerchio grande ed esterna al cerchio
    spostato. Il contorno e' campionato punto per punto, cosi la forma e'
    esatta anche senza archi.
    """
    r = R
    c2 = (cx + dx, cy + dy)
    d = math.hypot(dx, dy)
    # angolo, visto dal centro del cerchio grande, dei due punti di incontro
    base = math.atan2(dy, dx)
    mezzo = math.acos(d / (2 * R))            # cerchi di raggio uguale
    a1, a2 = base + mezzo, base - mezzo
    bordo = []
    # arco del cerchio grande, dalla parte opposta al cerchio spostato
    passo = (2*math.pi - 2*mezzo) / punti
    for i in range(punti + 1):
        a = a1 + i * passo
        bordo.append((cx + R*math.cos(a), cy + R*math.sin(a)))
    # arco del cerchio spostato, che scava la falce
    b1 = math.atan2(bordo[-1][1] - c2[1], bordo[-1][0] - c2[0])
    b2 = math.atan2(bordo[0][1] - c2[1], bordo[0][0] - c2[0])
    while b2 - b1 > math.pi:  b2 -= 2*math.pi
    while b2 - b1 < -math.pi: b2 += 2*math.pi
    for i in range(1, punti + 1):
        b = b1 + (b2 - b1) * i / punti
        bordo.append((c2[0] + r*math.cos(b), c2[1] + r*math.sin(b)))
    return 'M' + 'L'.join(f'{n(x)},{n(y)}' for x, y in bordo) + 'Z'

def stella(cx, cy, r):
    """Stellina a quattro punte."""
    return (f'M{n(cx)},{n(cy-r)}'
            f'Q{n(cx)},{n(cy-0.28*r)} {n(cx+r)},{n(cy)}'
            f'Q{n(cx)},{n(cy+0.28*r)} {n(cx)},{n(cy+r)}'
            f'Q{n(cx)},{n(cy+0.28*r)} {n(cx-r)},{n(cy)}'
            f'Q{n(cx)},{n(cy-0.28*r)} {n(cx)},{n(cy-r)}Z')

def fulmine(x, y, s):
    return (f'M{n(x)},{n(y)}'
            f'l{n(-4.4*s)},{n(7.4*s)}h{n(3.6*s)}l{n(-1.8*s)},{n(6.6*s)}'
            f'l{n(7.4*s)},{n(-9.2*s)}h{n(-4.0*s)}l{n(2.4*s)},{n(-4.8*s)}Z')

def pieno(d, colore, tipo='nonZero'):
    return ('  <path android:fillColor="%s" android:fillType="%s"\n'
            '        android:pathData="%s"/>\n' % (colore, tipo, d))

def tratto(d, colore, largh, alpha=None):
    a = '' if alpha is None else ' android:strokeAlpha="%s"' % n(alpha)
    return ('  <path android:strokeColor="%s" android:strokeWidth="%s"\n'
            '        android:strokeLineCap="round" android:fillColor="#00000000"%s\n'
            '        android:pathData="%s"/>\n' % (colore, n(largh), a, d))

def scrivi(nome, corpo):
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- generato da meteo-android/strumenti/genera_icone.py: non si modifica a mano -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="48dp" android:height="48dp"\n'
           '    android:viewportWidth="48" android:viewportHeight="48">\n'
           + corpo +
           '</vector>\n')
    open(os.path.join(DEST, nome + '.xml'), 'w', encoding='utf-8').write(xml)

N = 6  # fotogrammi per condizione

def onda(i, fasi=N):
    """Valore fra -1 e 1 che torna al punto di partenza: ciclo senza scatti."""
    return math.sin(2 * math.pi * i / fasi)

# ---------------------------------------------------------------- sole
for i in range(N):
    giro = i * 45.0 / N                      # 45 gradi = un passo fra due raggi
    lung = 19.0 + 0.9 * onda(i)
    c = pieno(cerchio(24, 24, 8.6), GIALLO)
    c += tratto(raggi(24, 24, 12.6, lung, giro), GIALLO_S, 2.6)
    scrivi('ic_meteo_sole_%d' % i, c)

# ---------------------------------------------------------------- luna
for i in range(N):
    c = pieno(falce(23, 25, 11.4, 6.6, -5.4), GIALLO)
    for k, (sx, sy, sr) in enumerate([(38, 13, 2.6), (12, 12, 2.0), (41, 24, 1.7)]):
        r = sr * (0.72 + 0.28 * abs(math.sin(math.pi * (i + k * 2) / N)))
        c += pieno(stella(sx, sy, r), GIALLO_S)
    scrivi('ic_meteo_luna_%d' % i, c)

# ------------------------------------------------- sole e luna con nuvole
for i in range(N):
    sp = 1.3 * onda(i)
    giro = i * 45.0 / N
    c = pieno(cerchio(18.5, 17.5, 6.6), GIALLO)
    c += tratto(raggi(18.5, 17.5, 10.0, 14.6, giro), GIALLO_S, 2.2)
    c += pieno(nuvola(26.5 + sp, 31.0, 0.78), NUVOLA)
    scrivi('ic_meteo_sole_nuvole_%d' % i, c)

    c = pieno(falce(18.0, 17.0, 8.4, 4.9, -4.0), GIALLO)
    c += pieno(stella(39, 12, 2.2 * (0.72 + 0.28 * abs(math.sin(math.pi * i / N)))), GIALLO_S)
    c += pieno(nuvola(26.5 + sp, 31.0, 0.78), NUVOLA)
    scrivi('ic_meteo_luna_nuvole_%d' % i, c)

# ---------------------------------------------------------------- nuvole
for i in range(N):
    c = pieno(nuvola(22.0 + 1.5 * onda(i), 19.0, 0.72), NUVOLA_S)
    c += pieno(nuvola(26.0 - 1.5 * onda(i), 31.0, 0.86), NUVOLA)
    scrivi('ic_meteo_nuvole_%d' % i, c)

# ---------------------------------------------------------------- nebbia
for i in range(N):
    c = pieno(nuvola(24.0, 17.5, 0.78), NUVOLA)
    for k, (y, mezza) in enumerate([(29.5, 13.0), (35.0, 11.0), (40.5, 9.0)]):
        sp = 2.6 * math.sin(2 * math.pi * (i + k * 1.4) / N)
        c += tratto(f'M{n(24 - mezza + sp)},{n(y)}L{n(24 + mezza + sp)},{n(y)}',
                    NEBBIA, 3.0, 0.9 - 0.12 * k)
    scrivi('ic_meteo_nebbia_%d' % i, c)

# --------------------------------------------- pioviggine, pioggia, temporale
def cadute(i, gocce, y0, y1, s):
    """Gocce che scendono: ognuna sfasata, il ciclo si chiude."""
    d = ''
    for k, x in enumerate(gocce):
        t = ((i / N) + k / len(gocce)) % 1.0
        d += goccia(x, y0 + (y1 - y0) * t, s)
    return d

for i in range(N):
    c = pieno(nuvola(24.0, 17.0, 0.8), NUVOLA)
    c += pieno(cadute(i, [17.0, 29.0], 28.0, 39.0, 0.82), PIOGGIA)
    scrivi('ic_meteo_pioviggine_%d' % i, c)

    c = pieno(nuvola(24.0, 16.0, 0.8), NUVOLA_S)
    c += pieno(cadute(i, [13.5, 21.0, 28.5, 36.0], 26.5, 40.0, 1.0), PIOGGIA)
    scrivi('ic_meteo_pioggia_%d' % i, c)

    c = pieno(nuvola(24.0, 16.0, 0.8), NUVOLA_S)
    c += pieno(cadute(i, [14.0, 34.0], 27.0, 39.0, 0.86), PIOGGIA)
    if i in (0, 1):
        c += pieno(fulmine(25.6, 26.0, 1.0), FULMINE)
    scrivi('ic_meteo_temporale_%d' % i, c)

# ---------------------------------------------------------------- neve
for i in range(N):
    c = pieno(nuvola(24.0, 16.5, 0.8), NUVOLA_S)
    for k, x in enumerate([15.0, 24.0, 33.0]):
        t = ((i / N) + k / 3.0) % 1.0
        y = 28.0 + 11.0 * t
        c += tratto(fiocco(x, y, 3.2, 30.0 * t * 2), NEVE, 1.8)
    scrivi('ic_meteo_neve_%d' % i, c)

famiglie = ['sole', 'luna', 'sole_nuvole', 'luna_nuvole', 'nuvole',
            'nebbia', 'pioviggine', 'pioggia', 'neve', 'temporale']
print('condizioni:', len(famiglie), 'fotogrammi totali:', len(famiglie) * N)
print('file scritti:', len([f for f in os.listdir(DEST) if f.startswith('ic_meteo_')]))

# ------------------------------------------------ icona dell'applicazione
# Sole e nuvola sul blu del sito. La grafica sta nel quadrato interno di
# 72dp su 108dp, la zona che ogni lanciatore mostra per intero.
corpo = ('  <group android:scaleX="1.5" android:scaleY="1.5"\n'
         '         android:translateX="18" android:translateY="18">\n')
corpo += pieno(cerchio(19.0, 18.0, 7.4), GIALLO)
corpo += tratto(raggi(19.0, 18.0, 11.2, 16.4, 0.0), GIALLO, 2.6)
corpo += pieno(nuvola(26.0, 31.5, 0.84), '#FFF1EFE9')
corpo += '  </group>\n'
xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
       '<!-- generato da meteo-android/strumenti/genera_icone.py: non si modifica a mano -->\n'
       '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
       '    android:width="108dp" android:height="108dp"\n'
       '    android:viewportWidth="108" android:viewportHeight="108">\n'
       + corpo +
       '</vector>\n')
open(os.path.join(DEST, 'ic_launcher_foreground.xml'), 'w', encoding='utf-8').write(xml)
print('icona dell applicazione: scritta')
