// Motore di percorso locale: costruisce un grafo dalla rete caricata e usa Dijkstra.
// Non conosce divieti di svolta né orari: i tempi sono stime dichiarate come tali.
import { haversine } from './util.js';

const CAR_SPEED = {
  motorway: 110, motorway_link: 60, trunk: 90, trunk_link: 50, primary: 70, primary_link: 45,
  secondary: 60, secondary_link: 40, tertiary: 50, tertiary_link: 35, residential: 30, unclassified: 40,
  living_street: 10, service: 15, road: 30, track: 15,
};
const NO_ACCESS = new Set(['no', 'private', 'agricultural', 'forestry', 'delivery', 'emergency']);
const YES = new Set(['yes', 'designated', 'permissive', 'destination']);
const UNPAVED = new Set(['unpaved', 'gravel', 'fine_gravel', 'compacted', 'ground', 'dirt', 'grass', 'sand', 'mud', 'pebblestone', 'rock', 'earth']);

function parseMaxspeed(v) {
  if (!v) return null;
  const zones = { 'IT:urban': 50, 'IT:rural': 90, 'IT:trunk': 110, 'IT:motorway': 130, 'IT:living_street': 10 };
  if (zones[v]) return zones[v];
  const m = /^(\d+)\s*(mph)?/.exec(v);
  if (!m) return null;
  return m[2] ? Number(m[1]) * 1.609 : Number(m[1]);
}

// Restituisce { fwd, bwd, speed } in km/h oppure null se la via non è percorribile.
function edgeRules(road, mode, opt) {
  const t = road.tags;
  const h = t.highway;
  const acc = t.access;
  if (opt.avoidToll && t.toll === 'yes') return null;
  if (opt.avoidUnpaved && (UNPAVED.has(t.surface) || (h === 'track' && !t.surface))) return null;
  if (opt.avoidMotorway && (h === 'motorway' || h === 'motorway_link' || h === 'trunk' || h === 'trunk_link')) return null;

  if (mode === 'car') {
    if (!(h in CAR_SPEED)) return null;
    const mv = t.motor_vehicle || t.motorcar;
    if (h === 'track' && !YES.has(mv)) return null;
    if ((NO_ACCESS.has(acc) || NO_ACCESS.has(mv)) && !YES.has(mv)) return null;
    let speed = CAR_SPEED[h];
    const ms = parseMaxspeed(t.maxspeed);
    if (ms) speed = Math.min(ms * 0.9, speed * 1.3);
    let fwd = true, bwd = true;
    const ow = t.oneway;
    if (ow === 'yes' || ow === '1' || ow === 'true' || t.junction === 'roundabout' || (h === 'motorway' && ow !== 'no')) bwd = false;
    if (ow === '-1') { fwd = false; bwd = true; }
    return { fwd, bwd, speed };
  }

  if (mode === 'bike') {
    if (h === 'motorway' || h === 'motorway_link' || h === 'trunk' || h === 'trunk_link') return null;
    if (t.bicycle === 'no' || t.bicycle === 'use_sidepath') return null;
    if (NO_ACCESS.has(acc) && !YES.has(t.bicycle)) return null;
    let speed = 16;
    if (h === 'steps') speed = 2;
    else if ((h === 'footway' || h === 'pedestrian' || h === 'corridor') && !YES.has(t.bicycle)) speed = 5; // a piedi accanto alla bici
    else if (UNPAVED.has(t.surface) || h === 'track' || h === 'path') speed = 11;
    else if (h === 'cycleway' || t.bicycle === 'designated') speed = 18;
    let fwd = true, bwd = true;
    const ow = t.oneway;
    if (h !== 'cycleway' && h !== 'path' && h !== 'footway') {
      if (ow === 'yes' || ow === '1' || t.junction === 'roundabout') bwd = false;
      if (ow === '-1') { fwd = false; }
    }
    return { fwd, bwd, speed };
  }

  // a piedi
  if (h === 'motorway' || h === 'motorway_link' || h === 'trunk' || h === 'trunk_link') return null;
  if (t.foot === 'no' || t.foot === 'use_sidepath') return null;
  if (NO_ACCESS.has(acc) && !YES.has(t.foot)) return null;
  const speed = h === 'steps' ? 3 : 4.8;
  return { fwd: true, bwd: true, speed };
}

export function buildGraph(roads, mode, opt = {}) {
  const index = new Map();
  const lon = [], lat = [];
  const nodeOf = (c) => {
    const k = `${c[0].toFixed(7)},${c[1].toFixed(7)}`;
    let i = index.get(k);
    if (i === undefined) {
      i = lon.length;
      index.set(k, i);
      lon.push(c[0]);
      lat.push(c[1]);
    }
    return i;
  };
  const from = [], to = [], len = [], time = [], road = [];
  roads.forEach((r, ri) => {
    const rule = edgeRules(r, mode, opt);
    if (!rule) return;
    const mps = (rule.speed * 1000) / 3600;
    let prev = nodeOf(r.coords[0]);
    for (let i = 1; i < r.coords.length; i++) {
      const cur = nodeOf(r.coords[i]);
      const d = haversine(r.coords[i - 1], r.coords[i]);
      if (rule.fwd) { from.push(prev); to.push(cur); len.push(d); time.push(d / mps); road.push(ri); }
      if (rule.bwd) { from.push(cur); to.push(prev); len.push(d); time.push(d / mps); road.push(ri); }
      prev = cur;
    }
  });
  const n = lon.length;
  // Struttura compatta delle adiacenze (CSR).
  const offs = new Uint32Array(n + 1);
  for (const f of from) offs[f + 1]++;
  for (let i = 0; i < n; i++) offs[i + 1] += offs[i];
  const m = from.length;
  const eTo = new Uint32Array(m), eLen = new Float32Array(m), eTime = new Float32Array(m), eRoad = new Uint32Array(m);
  const fill = offs.slice(0, n);
  for (let e = 0; e < m; e++) {
    const p = fill[from[e]]++;
    eTo[p] = to[e]; eLen[p] = len[e]; eTime[p] = time[e]; eRoad[p] = road[e];
  }
  const g = { n, lon: Float64Array.from(lon), lat: Float64Array.from(lat), offs, eTo, eLen, eTime, eRoad, mode, shortest: !!opt.shortest };
  g.grid = buildGrid(g);
  return g;
}

function buildGrid(g) {
  const cell = 0.002; // circa 150-200 m
  const map = new Map();
  for (let i = 0; i < g.n; i++) {
    if (g.offs[i + 1] === g.offs[i]) continue; // nodi senza uscite non servono come partenza
    const k = `${Math.floor(g.lon[i] / cell)}:${Math.floor(g.lat[i] / cell)}`;
    let a = map.get(k);
    if (!a) map.set(k, (a = []));
    a.push(i);
  }
  return { cell, map };
}

export function nearestNode(g, [x, y], maxRing = 25) {
  const { cell, map } = g.grid;
  const cx = Math.floor(x / cell), cy = Math.floor(y / cell);
  let best = -1, bestD = Infinity;
  for (let r = 0; r <= maxRing; r++) {
    for (let i = cx - r; i <= cx + r; i++) {
      for (let j = cy - r; j <= cy + r; j++) {
        if (Math.max(Math.abs(i - cx), Math.abs(j - cy)) !== r) continue;
        const a = map.get(`${i}:${j}`);
        if (!a) continue;
        for (const n of a) {
          const d = haversine([x, y], [g.lon[n], g.lat[n]]);
          if (d < bestD) { bestD = d; best = n; }
        }
      }
    }
    if (best >= 0 && r >= 1) break;
  }
  return best >= 0 ? { node: best, dist: bestD } : null;
}

// Coda di priorità binaria su indici di nodo.
class Heap {
  constructor() { this.k = []; this.v = []; }
  push(node, pri) {
    const k = this.k, v = this.v;
    let i = k.length;
    k.push(node); v.push(pri);
    while (i > 0) {
      const p = (i - 1) >> 1;
      if (v[p] <= pri) break;
      k[i] = k[p]; v[i] = v[p]; i = p;
    }
    k[i] = node; v[i] = pri;
  }
  pop() {
    const k = this.k, v = this.v;
    const top = k[0];
    const lk = k.pop(), lv = v.pop();
    if (k.length) {
      let i = 0;
      const n = k.length;
      for (;;) {
        let c = 2 * i + 1;
        if (c >= n) break;
        if (c + 1 < n && v[c + 1] < v[c]) c++;
        if (v[c] >= lv) break;
        k[i] = k[c]; v[i] = v[c]; i = c;
      }
      k[i] = lk; v[i] = lv;
    }
    return top;
  }
  get size() { return this.k.length; }
}

// Albero dei cammini minimi da una sorgente (Dijkstra). target facoltativo per fermarsi prima.
export function shortestTree(g, source, target = -1) {
  const dist = new Float64Array(g.n).fill(Infinity);
  const prevEdge = new Int32Array(g.n).fill(-1);
  const done = new Uint8Array(g.n);
  const w = g.shortest ? g.eLen : g.eTime;
  dist[source] = 0;
  const h = new Heap();
  h.push(source, 0);
  while (h.size) {
    const u = h.pop();
    if (done[u]) continue;
    done[u] = 1;
    if (u === target) break;
    for (let e = g.offs[u]; e < g.offs[u + 1]; e++) {
      const v = g.eTo[e];
      const nd = dist[u] + w[e];
      if (nd < dist[v]) { dist[v] = nd; prevEdge[v] = e; h.push(v, nd); }
    }
  }
  // Per ricostruire il cammino serve sapere da quale nodo parte ogni arco.
  return { dist, prevEdge, source };
}

function edgeSource(g) {
  if (g._src) return g._src;
  const src = new Uint32Array(g.eTo.length);
  for (let u = 0; u < g.n; u++) for (let e = g.offs[u]; e < g.offs[u + 1]; e++) src[e] = u;
  g._src = src;
  return src;
}

export function pathTo(g, tree, target) {
  if (!isFinite(tree.dist[target])) return null;
  const src = edgeSource(g);
  const nodes = [target];
  let length = 0, time = 0;
  let cur = target;
  let guard = 0;
  while (cur !== tree.source && guard++ < 1e7) {
    const e = tree.prevEdge[cur];
    if (e < 0) return null;
    length += g.eLen[e];
    time += g.eTime[e];
    cur = src[e];
    nodes.push(cur);
  }
  nodes.reverse();
  return { coords: nodes.map((n) => [g.lon[n], g.lat[n]]), length, time };
}

export function localRoute(g, points) {
  const snapped = points.map((p) => nearestNode(g, p));
  if (snapped.some((s) => !s)) return { error: 'far' };
  if (snapped.some((s) => s.dist > 1500)) return { error: 'far' };
  let coords = [], length = 0, time = 0;
  for (let i = 1; i < snapped.length; i++) {
    const tree = shortestTree(g, snapped[i - 1].node, snapped[i].node);
    const p = pathTo(g, tree, snapped[i].node);
    if (!p) return { error: 'disconnected' };
    coords = coords.length ? coords.concat(p.coords.slice(1)) : p.coords;
    length += p.length;
    time += p.time;
  }
  return { coords, length, time };
}
