// Funzioni di servizio: geometria, rete, memoria locale, formattazione.

const R = 6371008.8; // raggio medio terrestre in metri
const toRad = (d) => (d * Math.PI) / 180;
const toDeg = (r) => (r * 180) / Math.PI;

export function haversine(a, b) {
  const dLat = toRad(b[1] - a[1]);
  const dLon = toRad(b[0] - a[0]);
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a[1])) * Math.cos(toRad(b[1])) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(s)));
}

export function lineLength(coords) {
  let d = 0;
  for (let i = 1; i < coords.length; i++) d += haversine(coords[i - 1], coords[i]);
  return d;
}

// Direzione iniziale da a verso b, in gradi da nord (0..360).
export function bearing(a, b) {
  const y = Math.sin(toRad(b[0] - a[0])) * Math.cos(toRad(b[1]));
  const x =
    Math.cos(toRad(a[1])) * Math.sin(toRad(b[1])) -
    Math.sin(toRad(a[1])) * Math.cos(toRad(b[1])) * Math.cos(toRad(b[0] - a[0]));
  return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

// Superficie di un anello in km² (approssimazione sferica, sufficiente per le soglie).
export function ringAreaKm2(ring) {
  let area = 0;
  for (let i = 0, n = ring.length; i < n; i++) {
    const p1 = ring[i];
    const p2 = ring[(i + 1) % n];
    area += toRad(p2[0] - p1[0]) * (2 + Math.sin(toRad(p1[1])) + Math.sin(toRad(p2[1])));
  }
  return Math.abs((area * R * R) / 2) / 1e6;
}

export function geometryAreaKm2(geom) {
  if (!geom) return 0;
  if (geom.type === 'Polygon') {
    const [outer, ...holes] = geom.coordinates;
    return ringAreaKm2(outer) - holes.reduce((s, h) => s + ringAreaKm2(h), 0);
  }
  if (geom.type === 'MultiPolygon') {
    return geom.coordinates.reduce((s, p) => s + geometryAreaKm2({ type: 'Polygon', coordinates: p }), 0);
  }
  return 0;
}

export function bboxAreaKm2([w, s, e, n]) {
  return ringAreaKm2([[w, s], [e, s], [e, n], [w, n]]);
}

export function bboxOfCoords(coords, b = [Infinity, Infinity, -Infinity, -Infinity]) {
  for (const c of coords) {
    if (c[0] < b[0]) b[0] = c[0];
    if (c[1] < b[1]) b[1] = c[1];
    if (c[0] > b[2]) b[2] = c[0];
    if (c[1] > b[3]) b[3] = c[1];
  }
  return b;
}

export function bboxOfGeometry(geom, b = [Infinity, Infinity, -Infinity, -Infinity]) {
  if (!geom) return b;
  switch (geom.type) {
    case 'Point': return bboxOfCoords([geom.coordinates], b);
    case 'LineString': case 'MultiPoint': return bboxOfCoords(geom.coordinates, b);
    case 'Polygon': case 'MultiLineString':
      for (const r of geom.coordinates) bboxOfCoords(r, b);
      return b;
    case 'MultiPolygon':
      for (const p of geom.coordinates) for (const r of p) bboxOfCoords(r, b);
      return b;
    default: return b;
  }
}

export function bboxOfFeatures(features) {
  const b = [Infinity, Infinity, -Infinity, -Infinity];
  for (const f of features) bboxOfGeometry(f.geometry, b);
  return isFinite(b[0]) ? b : null;
}

export function bboxIntersects(a, b) {
  return !(a[2] < b[0] || a[0] > b[2] || a[3] < b[1] || a[1] > b[3]);
}

export function pointInRing(pt, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const xi = ring[i][0], yi = ring[i][1], xj = ring[j][0], yj = ring[j][1];
    if (yi > pt[1] !== yj > pt[1] && pt[0] < ((xj - xi) * (pt[1] - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

// Proiezione di Mercatore normalizzata (0..1), come le tessere web.
export function mercX(lon) { return (lon + 180) / 360; }
export function mercY(lat) {
  const s = Math.sin(toRad(Math.max(-85.0511, Math.min(85.0511, lat))));
  return 0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI);
}
export function invMercX(x) { return x * 360 - 180; }
export function invMercY(y) { return toDeg(Math.atan(Math.sinh(Math.PI * (1 - 2 * y)))); }

// Semplificazione di Douglas e Peucker su coordinate già proiettate.
export function simplify(points, tol) {
  if (points.length < 3 || tol <= 0) return points;
  const sq = tol * tol;
  const keep = new Uint8Array(points.length);
  keep[0] = keep[points.length - 1] = 1;
  const stack = [[0, points.length - 1]];
  while (stack.length) {
    const [a, b] = stack.pop();
    let maxD = 0, idx = -1;
    const [ax, ay] = points[a], [bx, by] = points[b];
    const dx = bx - ax, dy = by - ay;
    const len = dx * dx + dy * dy;
    for (let i = a + 1; i < b; i++) {
      const [px, py] = points[i];
      let t = len ? ((px - ax) * dx + (py - ay) * dy) / len : 0;
      t = Math.max(0, Math.min(1, t));
      const ex = ax + t * dx - px, ey = ay + t * dy - py;
      const d = ex * ex + ey * ey;
      if (d > maxD) { maxD = d; idx = i; }
    }
    if (maxD > sq && idx > 0) {
      keep[idx] = 1;
      stack.push([a, idx], [idx, b]);
    }
  }
  return points.filter((_, i) => keep[i]);
}

// Decodifica delle polilinee compresse (Valhalla usa precisione 6).
export function decodePolyline(str, precision = 6) {
  let index = 0, lat = 0, lng = 0;
  const coords = [];
  const factor = 10 ** precision;
  while (index < str.length) {
    let result = 0, shift = 0, byte;
    do { byte = str.charCodeAt(index++) - 63; result |= (byte & 0x1f) << shift; shift += 5; } while (byte >= 0x20);
    lat += result & 1 ? ~(result >> 1) : result >> 1;
    result = 0; shift = 0;
    do { byte = str.charCodeAt(index++) - 63; result |= (byte & 0x1f) << shift; shift += 5; } while (byte >= 0x20);
    lng += result & 1 ? ~(result >> 1) : result >> 1;
    coords.push([lng / factor, lat / factor]);
  }
  return coords;
}

// Ricampiona una linea a passo costante, al massimo maxPoints punti.
export function resampleLine(coords, maxPoints) {
  if (coords.length <= maxPoints) return coords.slice();
  const total = lineLength(coords);
  const step = total / (maxPoints - 1);
  const out = [coords[0]];
  let acc = 0, next = step;
  for (let i = 1; i < coords.length; i++) {
    const seg = haversine(coords[i - 1], coords[i]);
    while (seg > 0 && acc + seg >= next && out.length < maxPoints - 1) {
      const t = (next - acc) / seg;
      out.push([
        coords[i - 1][0] + t * (coords[i][0] - coords[i - 1][0]),
        coords[i - 1][1] + t * (coords[i][1] - coords[i - 1][1]),
      ]);
      next += step;
    }
    acc += seg;
  }
  out.push(coords[coords.length - 1]);
  return out;
}

/* ---------- rete ---------- */

export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export const sleep = (ms, signal) =>
  new Promise((resolve, reject) => {
    const t = setTimeout(resolve, ms);
    signal?.addEventListener('abort', () => { clearTimeout(t); reject(abortError()); }, { once: true });
  });

export function abortError() {
  const e = new Error('Richiesta annullata');
  e.name = 'AbortError';
  return e;
}

export const isAbort = (e) => e?.name === 'AbortError';

// Richiesta con limite di tempo, annullamento e lettura progressiva del corpo.
export async function fetchWithProgress(url, { signal, timeout = 60000, onProgress, ...init } = {}) {
  const ctrl = new AbortController();
  const onAbort = () => ctrl.abort();
  signal?.addEventListener('abort', onAbort, { once: true });
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; ctrl.abort(); }, timeout);
  try {
    const res = await fetch(url, { ...init, signal: ctrl.signal });
    if (!res.ok) {
      let text = '';
      try { text = await res.text(); } catch { /* corpo non leggibile */ }
      throw new HttpError(res.status, text.slice(0, 400));
    }
    if (!onProgress || !res.body) return await res.text();
    const reader = res.body.getReader();
    const chunks = [];
    let received = 0;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
      received += value.length;
      onProgress(received);
    }
    const all = new Uint8Array(received);
    let off = 0;
    for (const c of chunks) { all.set(c, off); off += c.length; }
    return new TextDecoder('utf-8').decode(all);
  } catch (e) {
    if (timedOut) throw new HttpError(408, 'Tempo scaduto');
    if (signal?.aborted) throw abortError();
    throw e;
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', onAbort);
  }
}

export async function fetchJson(url, opts = {}) {
  const text = await fetchWithProgress(url, opts);
  try {
    return JSON.parse(text);
  } catch {
    throw new Error('Risposta non valida dal servizio');
  }
}

// Coda che rispetta un intervallo minimo tra le richieste verso lo stesso servizio.
export function makeThrottle(minInterval) {
  let last = 0;
  let chain = Promise.resolve();
  return (fn) => {
    const run = chain.then(async () => {
      const wait = last + minInterval - Date.now();
      if (wait > 0) await sleep(wait);
      last = Date.now();
      return fn();
    });
    chain = run.catch(() => {});
    return run;
  };
}

/* ---------- memoria locale ---------- */

const PREFIX = 'trame:';
export const store = {
  get(key, fallback) {
    try {
      const v = localStorage.getItem(PREFIX + key);
      return v == null ? fallback : JSON.parse(v);
    } catch {
      return fallback;
    }
  },
  set(key, value) {
    try { localStorage.setItem(PREFIX + key, JSON.stringify(value)); } catch { /* memoria non disponibile */ }
  },
  remove(key) {
    try { localStorage.removeItem(PREFIX + key); } catch { /* memoria non disponibile */ }
  },
};

/* ---------- formattazione ---------- */

const nf1 = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 1 });
const nf0 = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 0 });
const nf2 = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 2 });

export const fmt = {
  int: (n) => nf0.format(n),
  dec: (n) => nf1.format(n),
  dec2: (n) => nf2.format(n),
  dist(m) {
    if (m == null || !isFinite(m)) return '';
    return m < 1000 ? `${nf0.format(m)} m` : `${nf1.format(m / 1000)} km`;
  },
  km: (m) => `${nf1.format(m / 1000)} km`,
  dur(s) {
    if (s == null || !isFinite(s)) return '';
    const min = Math.round(s / 60);
    if (min < 60) return `${min} min`;
    const h = Math.floor(min / 60);
    return `${h} h ${min % 60} min`;
  },
  bytes(b) {
    if (b < 1024) return `${b} B`;
    if (b < 1024 * 1024) return `${nf0.format(b / 1024)} kB`;
    return `${nf1.format(b / 1024 / 1024)} MB`;
  },
  area: (km2) => (km2 < 1 ? `${nf2.format(km2)} km²` : `${nf0.format(km2)} km²`),
  date: (d = new Date()) => d.toLocaleDateString('it-IT', { day: 'numeric', month: 'long', year: 'numeric' }),
};

export function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

export function debounce(fn, ms) {
  let t;
  return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
}

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

export function slug(s) {
  return String(s || 'mappa')
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '')
    .slice(0, 50) || 'mappa';
}

// Codifica compatta per i collegamenti condivisibili.
export function encodeState(obj) {
  const bytes = new TextEncoder().encode(JSON.stringify(obj));
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function decodeState(str) {
  const b64 = str.replace(/-/g, '+').replace(/_/g, '/');
  const bin = atob(b64 + '==='.slice((b64.length + 3) % 4));
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
  return JSON.parse(new TextDecoder().decode(bytes));
}

export function hexToRgb(hex) {
  const h = hex.replace('#', '');
  const v = h.length === 3 ? h.split('').map((c) => c + c).join('') : h;
  const n = parseInt(v, 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

export function rgbToHex([r, g, b]) {
  return '#' + [r, g, b].map((x) => Math.max(0, Math.min(255, Math.round(x))).toString(16).padStart(2, '0')).join('');
}

// Luminosità: 1 = invariato, <1 più scuro, >1 più chiaro (verso il bianco).
export function adjustBrightness(hex, k) {
  const rgb = hexToRgb(hex);
  if (k >= 1) return rgbToHex(rgb.map((c) => c + (255 - c) * (k - 1)));
  return rgbToHex(rgb.map((c) => c * k));
}

export function mixColor(a, b, t) {
  const A = hexToRgb(a), B = hexToRgb(b);
  return rgbToHex(A.map((v, i) => v + (B[i] - v) * t));
}

// Scala cromatica continua (da 0 a 1) su una serie di colori.
export function rampColor(stops, t) {
  t = Math.max(0, Math.min(1, t));
  const n = stops.length - 1;
  const i = Math.min(n - 1, Math.floor(t * n));
  return mixColor(stops[i], stops[i + 1], t * n - i);
}

export function relLuminance(hex) {
  const [r, g, b] = hexToRgb(hex).map((c) => {
    c /= 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export const uid = () => Math.random().toString(36).slice(2, 9);
