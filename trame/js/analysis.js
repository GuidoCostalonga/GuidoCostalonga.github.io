// Statistiche, grafici e strumenti creativi calcolati sui dati caricati.
import { ROAD_CLASSES, CLASS_BY_KEY, ORIENTATION_COLORS } from './styles.js';
import { haversine, bearing, bboxIntersects, fmt, esc, adjustBrightness } from './util.js';
import { buildGraph, nearestNode, shortestTree, pathTo } from './graph.js';

export function networkStats(roads, viewBbox) {
  const list = viewBbox ? roads.filter((r) => bboxIntersects(r.bbox, viewBbox)) : roads;
  const byClass = new Map();
  let total = 0;
  for (const r of list) {
    total += r.len;
    const c = byClass.get(r.cls) || { count: 0, len: 0 };
    c.count++; c.len += r.len;
    byClass.set(r.cls, c);
  }
  const rows = ROAD_CLASSES.filter((c) => byClass.has(c.key)).map((c) => ({ key: c.key, label: c.label, ...byClass.get(c.key) }));
  rows.sort((a, b) => b.len - a.len);
  const named = list.filter((r) => r.tags.name);
  const names = new Set(named.map((r) => r.tags.name));
  return { count: list.length, total, rows, streets: names.size, partial: !!viewBbox };
}

// Istogramma degli orientamenti: 36 settori da 10° su tutto il giro, simmetrico.
export function orientationHistogram(roads, viewBbox) {
  const bins = new Float64Array(36);
  for (const r of roads) {
    if (viewBbox && !bboxIntersects(r.bbox, viewBbox)) continue;
    for (let i = 1; i < r.coords.length; i++) {
      const d = haversine(r.coords[i - 1], r.coords[i]);
      if (!d) continue;
      const b = bearing(r.coords[i - 1], r.coords[i]);
      bins[Math.floor(b / 10) % 36] += d;
      bins[Math.floor(((b + 180) % 360) / 10) % 36] += d;
    }
  }
  // Entropia normalizzata: 1 = orientamenti dispersi, valori bassi = griglia ordinata.
  const sum = bins.reduce((a, b) => a + b, 0);
  let H = 0;
  for (const v of bins) if (v > 0) { const p = v / sum; H -= p * Math.log(p); }
  const order = sum ? 1 - ((H - Math.log(4)) / (Math.log(36) - Math.log(4))) ** 2 : 0;
  return { bins: Array.from(bins), total: sum, order: Math.max(0, Math.min(1, order)) };
}

// Densità: lunghezza di rete per cella (circa 250 m), smussata sulle celle vicine e normalizzata.
export function densityByRoad(roads) {
  const cell = 0.0025;
  const grid = new Map();
  const key = (x, y) => `${x}:${y}`;
  for (const r of roads) {
    const m = r.coords[Math.floor(r.coords.length / 2)];
    const k = key(Math.floor(m[0] / cell), Math.floor(m[1] / cell));
    grid.set(k, (grid.get(k) || 0) + r.len);
  }
  const smooth = new Map();
  for (const k of grid.keys()) {
    const [x, y] = k.split(':').map(Number);
    let s = 0;
    for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) s += (grid.get(key(x + i, y + j)) || 0) * (i || j ? 0.5 : 1);
    smooth.set(k, s);
  }
  const vals = [...smooth.values()].sort((a, b) => a - b);
  const p95 = vals[Math.floor(vals.length * 0.95)] || 1;
  const out = new Map();
  for (const r of roads) {
    const m = r.coords[Math.floor(r.coords.length / 2)];
    const v = smooth.get(key(Math.floor(m[0] / cell), Math.floor(m[1] / cell))) || 0;
    out.set(r.id, Math.min(1, v / p95));
  }
  return out;
}

/* ---------- grafici SVG ---------- */

export function classBarChart(stats, style) {
  if (!stats.rows.length) return '';
  const max = Math.max(...stats.rows.map((r) => r.len));
  const rowH = 22, labelW = 150, W = 320;
  const H = stats.rows.length * rowH + 4;
  const bars = stats.rows.map((r, i) => {
    const w = Math.max(2, ((W - labelW - 60) * r.len) / max);
    const y = i * rowH + 2;
    const color = adjustBrightness(style.colorMode === 'class' ? style.classColors[r.key] : style.roadColor, 1);
    const dash = CLASS_BY_KEY[r.key]?.dash;
    return `<g><title>${esc(r.label)}: ${fmt.km(r.len)}, ${fmt.int(r.count)} tratti</title>
      <text x="0" y="${y + 14}" font-size="11" fill="currentColor">${esc(r.label)}</text>
      <rect x="${labelW}" y="${y + 3}" width="${w}" height="14" rx="2" fill="${color}" ${dash ? 'fill-opacity="0.75" stroke="currentColor" stroke-dasharray="3 2" stroke-width="1"' : ''}/>
      <text x="${labelW + w + 4}" y="${y + 14}" font-size="11" fill="currentColor">${fmt.dec((r.len / stats.total) * 100)}%</text></g>`;
  }).join('');
  return `<svg viewBox="0 0 ${W} ${H}" width="100%" role="img" aria-label="Distribuzione della lunghezza della rete per tipo di strada">${bars}</svg>`;
}

export function roseChart(hist, size = 260) {
  const c = size / 2, R = c - 22;
  const max = Math.max(...hist.bins) || 1;
  const wedges = hist.bins.map((v, i) => {
    const a0 = ((i * 10 - 90 - 5) * Math.PI) / 180, a1 = ((i * 10 - 90 + 5) * Math.PI) / 180;
    const r = R * Math.sqrt(v / max);
    const p = (a, rr) => `${(c + rr * Math.cos(a)).toFixed(1)} ${(c + rr * Math.sin(a)).toFixed(1)}`;
    const color = ORIENTATION_COLORS[Math.floor(((i * 10) % 180) / 10)];
    return `<path d="M${c} ${c}L${p(a0, r)}A${r.toFixed(1)} ${r.toFixed(1)} 0 0 1 ${p(a1, r)}Z" fill="${color}" fill-opacity="0.85" stroke="currentColor" stroke-opacity="0.25" stroke-width="0.5"><title>${i * 10}°: ${fmt.dec((v / hist.total) * 100)}% della lunghezza</title></path>`;
  }).join('');
  const rings = [0.25, 0.5, 0.75, 1].map((f) => `<circle cx="${c}" cy="${c}" r="${(R * Math.sqrt(f)).toFixed(1)}" fill="none" stroke="currentColor" stroke-opacity="0.15"/>`).join('');
  const labels = [['N', 0, -1], ['E', 1, 0], ['S', 0, 1], ['O', -1, 0]].map(([t, x, y]) => `<text x="${c + x * (R + 12)}" y="${c + y * (R + 12) + 4}" font-size="11" font-weight="700" text-anchor="middle" fill="currentColor">${t}</text>`).join('');
  return `<svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" role="img" aria-label="Rosa degli orientamenti delle strade">${rings}${wedges}${labels}</svg>`;
}

export function elevationProfileChart(profile, W = 320, H = 110) {
  if (!profile) return '';
  const { heights, min, max } = profile;
  const n = heights.length;
  const span = Math.max(10, max - min);
  const pts = heights.map((h, i) => (h == null ? null : [(i / (n - 1)) * (W - 30) + 28, H - 16 - ((h - min) / span) * (H - 28)])).filter(Boolean);
  if (pts.length < 2) return '';
  const d = 'M' + pts.map(([x, y]) => `${x.toFixed(1)} ${y.toFixed(1)}`).join('L');
  const area = `${d}L${pts[pts.length - 1][0].toFixed(1)} ${H - 16}L${pts[0][0].toFixed(1)} ${H - 16}Z`;
  return `<svg viewBox="0 0 ${W} ${H}" width="100%" role="img" aria-label="Profilo altimetrico da ${Math.round(min)} a ${Math.round(max)} metri">
    <path d="${area}" fill="currentColor" fill-opacity="0.12"/><path d="${d}" fill="none" stroke="currentColor" stroke-width="1.5"/>
    <text x="0" y="12" font-size="10" fill="currentColor">${Math.round(max)} m</text>
    <text x="0" y="${H - 18}" font-size="10" fill="currentColor">${Math.round(min)} m</text></svg>`;
}

/* ---------- percorsi generati ---------- */

// Genera molti percorsi brevi sulla rete caricata, senza servizi esterni.
// "origin" facoltativa: con un'origine si usa un solo albero dei cammini (molto rapido).
export function generatePaths(roads, { mode = 'foot', count = 100, origin = null, maxDist = 0, seed = Date.now() } = {}) {
  const g = buildGraph(roads, mode, {});
  const live = [];
  for (let i = 0; i < g.n; i++) if (g.offs[i + 1] > g.offs[i]) live.push(i);
  if (live.length < 2) return { paths: [], reason: 'Rete troppo piccola o non percorribile con questo mezzo.' };
  let s = seed % 2147483647 || 1;
  const rand = () => (s = (s * 48271) % 2147483647) / 2147483647;
  const pick = () => live[Math.floor(rand() * live.length)];
  const paths = [];
  if (origin) {
    const o = nearestNode(g, origin);
    if (!o) return { paths: [], reason: 'Il punto scelto è lontano dalla rete caricata.' };
    const tree = shortestTree(g, o.node);
    let tries = 0;
    while (paths.length < count && tries++ < count * 6) {
      const t = pick();
      if (!isFinite(tree.dist[t])) continue;
      const p = pathTo(g, tree, t);
      if (p && p.coords.length > 1 && (!maxDist || p.length <= maxDist)) paths.push(p.coords);
    }
  } else {
    let tries = 0;
    while (paths.length < count && tries++ < count * 4) {
      const a = pick(), b = pick();
      if (a === b) continue;
      const tree = shortestTree(g, a, b);
      const p = pathTo(g, tree, b);
      if (p && p.coords.length > 1 && (!maxDist || p.length <= maxDist)) paths.push(p.coords);
    }
  }
  return { paths, reason: paths.length ? null : 'Nessun collegamento trovato: prova un altro mezzo o un\'area più ampia.' };
}

export function sampleMidpoints(roads, max) {
  // Le strade più lunghe e importanti hanno la precedenza: campione dichiarato come tale.
  const sorted = [...roads].sort((a, b) => b.len - a.len);
  return sorted.slice(0, max).map((r) => ({ id: r.id, coord: r.coords[Math.floor(r.coords.length / 2)] }));
}
