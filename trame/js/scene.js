// Scena indipendente dalla libreria cartografica: serve per l'esportazione PNG e SVG,
// per il manifesto e per la visualizzazione alternativa quando WebGL manca.
import { ROAD_CLASSES, CLASS_BY_KEY, ORIENTATION_COLORS, ELEVATION_RAMP, DENSITY_RAMP, LAYER_BY_KEY, ROUTE_DASHES } from './styles.js';
import { adjustBrightness, rampColor, mercX, mercY, simplify, esc } from './util.js';

export function orientationBin(ori) {
  return Math.floor(((ori % 180) + 180) % 180 / 10) % 18;
}

// Colore di una strada secondo la modalità scelta. Dove il dato manca si usa un grigio neutro.
export function roadColor(road, style, extra = {}) {
  let c;
  switch (style.colorMode) {
    case 'uniform': c = style.roadColor; break;
    case 'orientation': c = ORIENTATION_COLORS[orientationBin(road.ori)]; break;
    case 'elevation': {
      const e = extra.elev?.get(road.id);
      c = e == null || !extra.elevRange ? '#8a8a8a' : rampColor(ELEVATION_RAMP, (e - extra.elevRange[0]) / Math.max(1, extra.elevRange[1] - extra.elevRange[0]));
      break;
    }
    case 'density': {
      const d = extra.density?.get(road.id);
      c = d == null ? '#8a8a8a' : rampColor(DENSITY_RAMP.slice(1), d);
      break;
    }
    default: c = style.classColors[road.cls] || style.roadColor;
  }
  return style.brightness !== 1 ? adjustBrightness(c, style.brightness) : c;
}

export function roadWidth(road, style, layerW = 1) {
  return (CLASS_BY_KEY[road.cls]?.width || 1) * style.width * layerW;
}

// Costruisce i gruppi da disegnare, in ordine dal basso verso l'alto.
export function buildScene({ roads, layerData, routes, creative, area }, style, layers, extra = {}) {
  const groups = [];
  const L = (k) => layers[k] || { on: false, opacity: 1, width: 1 };
  const lc = style.layerColors;
  const dimRoads = extra.dimRoads ? 0.3 : 1;

  const addFill = (key, color, filterFn = () => true, outline) => {
    const feats = (layerData[key] || []).filter((f) => f.properties.shape === 'area' && filterFn(f));
    if (!feats.length) return;
    groups.push({ kind: 'fill', color, opacity: L(key).opacity * 0.9, outline, items: feats.map((f) => f.geometry) });
  };
  const addLines = (key, color, width, dash, filterFn = () => true) => {
    const items = [];
    for (const f of layerData[key] || []) {
      if (f.properties.shape !== 'line' || !filterFn(f)) continue;
      if (f.geometry.type === 'LineString') items.push(f.geometry.coordinates);
      else if (f.geometry.type === 'MultiLineString') items.push(...f.geometry.coordinates);
    }
    if (items.length) groups.push({ kind: 'line', color, width: width * L(key).width, opacity: L(key).opacity, dash, items });
  };

  if (L('green').on) addFill('green', lc.green);
  if (L('water').on) {
    addFill('water', lc.water);
    addLines('water', lc.water, 1.4, null);
  }
  if (L('buildings').on) addFill('buildings', lc.buildings);
  if (L('boundaries').on) addLines('boundaries', lc.boundaries, 1.4, [4, 2]);

  if (L('roads').on && roads?.length) {
    // Raggruppa per colore e larghezza: meno cambi di stile, disegno più rapido.
    const byKey = new Map();
    const sorted = [...roads].sort((a, b) => (CLASS_BY_KEY[a.cls]?.rank || 0) - (CLASS_BY_KEY[b.cls]?.rank || 0));
    for (const r of sorted) {
      const color = roadColor(r, style, extra);
      const width = roadWidth(r, style, L('roads').width);
      const dash = CLASS_BY_KEY[r.cls]?.dash || null;
      const k = `${CLASS_BY_KEY[r.cls]?.rank}|${color}|${width}|${dash}`;
      let g = byKey.get(k);
      if (!g) {
        g = { kind: 'line', color, width, opacity: style.opacity * L('roads').opacity * dimRoads, dash, glow: style.glow, items: [] };
        byKey.set(k, g);
        groups.push(g);
      }
      g.items.push(r.coords);
    }
  }

  if (L('rail').on) addLines('rail', lc.rail, 1.6, [3, 1.5]);
  if (L('transit').on) addLines('transit', lc.transit, 1.3, [1, 1]);
  if (L('cycleroutes').on) addLines('cycleroutes', lc.cycleroutes, 1.8, [2, 1]);

  if (creative?.paths?.length) {
    groups.push({ kind: 'line', color: creative.color, width: 1.1 * style.width, opacity: 0.55, glow: style.glow, items: creative.paths });
  }

  if (area?.ring && extra.showArea) {
    groups.push({ kind: 'line', color: style.colorMode === 'uniform' ? style.roadColor : '#6d4c9f', width: 1.2, opacity: 0.7, dash: [2, 2], items: [area.ring] });
  }

  for (const r of routes || []) {
    if (!r.visible) continue;
    groups.push({ kind: 'line', color: r.color, width: 8, opacity: 0.25, items: [r.coords], halo: true });
    groups.push({ kind: 'line', color: '#ffffff', width: 6, opacity: 0.9, items: [r.coords] });
    groups.push({ kind: 'line', color: r.color, width: 4.2, opacity: 1, dash: ROUTE_DASHES[r.dashIndex || 0], items: [r.coords] });
  }
  for (const m of extra.markers || []) {
    groups.push({ kind: 'point', color: m.color, r: 9, label: m.label, items: [m.coord] });
  }
  if (L('poi').on) {
    const pts = (layerData.poi || []).map((f) => f.geometry.coordinates);
    if (pts.length) groups.push({ kind: 'point', color: lc.poi, r: 2.6, opacity: L('poi').opacity, items: pts });
  }
  return groups;
}

/* ---------- proiezione ---------- */

export function fitProjection(bbox, W, H, margin = 0) {
  const x0 = mercX(bbox[0]), x1 = mercX(bbox[2]);
  const y0 = mercY(bbox[3]), y1 = mercY(bbox[1]);
  const s = Math.min((W - 2 * margin) / Math.max(1e-12, x1 - x0), (H - 2 * margin) / Math.max(1e-12, y1 - y0));
  const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
  return makeProjection(cx, cy, s, W, H);
}

export function makeProjection(cx, cy, scale, W, H) {
  return {
    cx, cy, scale, W, H,
    p: (c) => [(mercX(c[0]) - cx) * scale + W / 2, (mercY(c[1]) - cy) * scale + H / 2],
  };
}

function projectLine(coords, proj, tol) {
  const pts = coords.map(proj.p);
  return tol > 0 ? simplify(pts, tol) : pts;
}

function lineVisible(pts, W, H) {
  let minx = Infinity, miny = Infinity, maxx = -Infinity, maxy = -Infinity;
  for (const [x, y] of pts) { if (x < minx) minx = x; if (x > maxx) maxx = x; if (y < miny) miny = y; if (y > maxy) maxy = y; }
  return !(maxx < -20 || minx > W + 20 || maxy < -20 || miny > H + 20);
}

/* ---------- disegno su canvas ---------- */

export function drawSceneCanvas(ctx, groups, proj, { k = 1, bg, tol = 0.4 } = {}) {
  const { W, H } = proj;
  if (bg) { ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H); }
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  for (const g of groups) {
    ctx.globalAlpha = g.opacity ?? 1;
    if (g.kind === 'fill') {
      ctx.fillStyle = g.color;
      ctx.beginPath();
      for (const geom of g.items) {
        const polys = geom.type === 'Polygon' ? [geom.coordinates] : geom.coordinates;
        for (const poly of polys) for (const ring of poly) {
          const pts = projectLine(ring, proj, tol);
          if (pts.length < 3 || !lineVisible(pts, W, H)) continue;
          ctx.moveTo(pts[0][0], pts[0][1]);
          for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0], pts[i][1]);
          ctx.closePath();
        }
      }
      ctx.fill('evenodd');
    } else if (g.kind === 'line') {
      ctx.strokeStyle = g.color;
      ctx.lineWidth = Math.max(0.3, g.width * k);
      ctx.setLineDash(g.dash ? g.dash.map((d) => d * g.width * k) : []);
      if (g.glow) { ctx.shadowColor = g.color; ctx.shadowBlur = 4 * k; } else ctx.shadowBlur = 0;
      if (g.halo) ctx.filter = `blur(${Math.max(1, 2 * k)}px)`;
      ctx.beginPath();
      for (const line of g.items) {
        const pts = projectLine(line, proj, tol);
        if (pts.length < 2 || !lineVisible(pts, W, H)) continue;
        ctx.moveTo(pts[0][0], pts[0][1]);
        for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0], pts[i][1]);
      }
      ctx.stroke();
      ctx.filter = 'none';
      ctx.shadowBlur = 0;
      ctx.setLineDash([]);
    } else if (g.kind === 'point') {
      for (const c of g.items) {
        const [x, y] = proj.p(c);
        if (x < -20 || x > W + 20 || y < -20 || y > H + 20) continue;
        ctx.beginPath();
        ctx.arc(x, y, g.r * k, 0, Math.PI * 2);
        ctx.fillStyle = g.color;
        ctx.fill();
        if (g.label) {
          ctx.lineWidth = 2 * k;
          ctx.strokeStyle = '#ffffff';
          ctx.stroke();
          ctx.fillStyle = '#ffffff';
          ctx.font = `bold ${Math.round(10 * k)}px system-ui, sans-serif`;
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.fillText(g.label, x, y + 0.5 * k);
        }
      }
    }
  }
  ctx.globalAlpha = 1;
}

/* ---------- disegno in SVG ---------- */

const r1 = (n) => Math.round(n * 10) / 10;

export function sceneToSvg(groups, proj, { k = 1, bg, tol = 0.4 } = {}) {
  const { W, H } = proj;
  const parts = [];
  if (bg) parts.push(`<rect width="${W}" height="${H}" fill="${bg}"/>`);
  for (const g of groups) {
    const op = g.opacity ?? 1;
    if (g.kind === 'fill') {
      let d = '';
      for (const geom of g.items) {
        const polys = geom.type === 'Polygon' ? [geom.coordinates] : geom.coordinates;
        for (const poly of polys) for (const ring of poly) {
          const pts = projectLine(ring, proj, tol);
          if (pts.length < 3 || !lineVisible(pts, W, H)) continue;
          d += 'M' + pts.map(([x, y]) => `${r1(x)} ${r1(y)}`).join('L') + 'Z';
        }
      }
      if (d) parts.push(`<path d="${d}" fill="${g.color}" fill-opacity="${op}" fill-rule="evenodd"/>`);
    } else if (g.kind === 'line') {
      let d = '';
      for (const line of g.items) {
        const pts = projectLine(line, proj, tol);
        if (pts.length < 2 || !lineVisible(pts, W, H)) continue;
        d += 'M' + pts.map(([x, y]) => `${r1(x)} ${r1(y)}`).join('L');
      }
      if (!d) continue;
      const w = r1(Math.max(0.3, g.width * k));
      const dash = g.dash ? ` stroke-dasharray="${g.dash.map((v) => r1(v * g.width * k)).join(' ')}"` : '';
      const filter = g.halo ? ' filter="url(#sfoca)"' : '';
      parts.push(`<path d="${d}" fill="none" stroke="${g.color}" stroke-opacity="${op}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"${dash}${filter}/>`);
    } else if (g.kind === 'point') {
      for (const c of g.items) {
        const [x, y] = proj.p(c);
        if (x < -20 || x > W + 20 || y < -20 || y > H + 20) continue;
        parts.push(`<circle cx="${r1(x)}" cy="${r1(y)}" r="${r1(g.r * k)}" fill="${g.color}" fill-opacity="${op}"${g.label ? ` stroke="#fff" stroke-width="${r1(2 * k)}"` : ''}/>`);
        if (g.label) parts.push(`<text x="${r1(x)}" y="${r1(y)}" fill="#fff" font-family="system-ui, sans-serif" font-weight="700" font-size="${r1(10 * k)}" text-anchor="middle" dominant-baseline="central">${esc(g.label)}</text>`);
      }
    }
  }
  return parts.join('\n');
}

// Voci della legenda, coerenti con livelli e filtri attivi.
export function legendEntries({ roads, layerData, routes, creative }, style, layers, filters, extra = {}) {
  const entries = [];
  if (layers.roads?.on && roads?.length) {
    const present = new Set(roads.map((r) => r.cls));
    if (style.colorMode === 'class' || style.colorMode === 'uniform') {
      for (const c of ROAD_CLASSES) {
        if (!present.has(c.key) || !filters.classes[c.key]) continue;
        entries.push({ kind: 'line', label: c.label, color: adjustBrightness(style.colorMode === 'class' ? style.classColors[c.key] : style.roadColor, style.brightness), width: c.width * style.width, dash: c.dash });
      }
    } else if (style.colorMode === 'orientation') {
      entries.push({ kind: 'ramp', label: 'Orientamento delle strade (0° nord, 90° est)', colors: ORIENTATION_COLORS, min: '0°', max: '180°' });
    } else if (style.colorMode === 'elevation' && extra.elevRange) {
      entries.push({ kind: 'ramp', label: 'Quota stimata del punto centrale', colors: ELEVATION_RAMP, min: `${Math.round(extra.elevRange[0])} m`, max: `${Math.round(extra.elevRange[1])} m` });
    } else if (style.colorMode === 'density') {
      entries.push({ kind: 'ramp', label: 'Densità della rete (lunghezza per superficie)', colors: DENSITY_RAMP.slice(1), min: 'bassa', max: 'alta' });
    }
  }
  const lc = style.layerColors;
  const extraLayer = (key, kind, color, dash) => {
    if (layers[key]?.on && layerData[key]?.length) entries.push({ kind, label: LAYER_BY_KEY[key].label, color, dash, width: 1.6 });
  };
  extraLayer('rail', 'line', lc.rail, [3, 1.5]);
  extraLayer('transit', 'line', lc.transit, [1, 1]);
  extraLayer('cycleroutes', 'line', lc.cycleroutes, [2, 1]);
  extraLayer('buildings', 'fill', lc.buildings);
  extraLayer('green', 'fill', lc.green);
  extraLayer('water', 'fill', lc.water);
  extraLayer('boundaries', 'line', lc.boundaries, [4, 2]);
  extraLayer('poi', 'point', lc.poi);
  if (creative?.paths?.length) entries.push({ kind: 'line', label: `Percorsi generati (${creative.paths.length})`, color: creative.color, width: 1.2 });
  const suffix = (r) => (r.kind === 'manual' && !/a mano/i.test(r.name) ? ' (tracciato a mano)' : r.kind === 'import' ? ' (importato)' : '');
  for (const r of routes || []) if (r.visible) entries.push({ kind: 'line', label: r.name + suffix(r), color: r.color, width: 4, dash: ROUTE_DASHES[r.dashIndex || 0] });
  return entries;
}
