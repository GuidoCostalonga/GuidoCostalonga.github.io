// Esportazione in PNG e SVG ad alta risoluzione, con cornice da manifesto facoltativa.
import { fitProjection, drawSceneCanvas, sceneToSvg } from './scene.js';
import { esc, relLuminance } from './util.js';

export const SIZE_PRESETS = {
  quadrato: { label: 'Quadrato 3000 × 3000', w: 3000, h: 3000 },
  a4: { label: 'A4 a 300 dpi (2480 × 3508)', w: 2480, h: 3508 },
  a3: { label: 'A3 a 300 dpi (3508 × 4961)', w: 3508, h: 4961 },
  social: { label: 'Riquadro social 1080 × 1350', w: 1080, h: 1350 },
  schermo: { label: 'Sfondo schermo 3840 × 2160', w: 3840, h: 2160 },
  personalizzato: { label: 'Personalizzato', w: 2000, h: 2000 },
};

export function resolveSize(opts) {
  let { w, h } = opts.size === 'personalizzato' ? { w: opts.w, h: opts.h } : SIZE_PRESETS[opts.size] || SIZE_PRESETS.quadrato;
  w = Math.max(200, Math.min(10000, Math.round(w)));
  h = Math.max(200, Math.min(10000, Math.round(h)));
  const landscape = opts.orientation === 'orizzontale', portrait = opts.orientation === 'verticale';
  if ((landscape && h > w) || (portrait && w > h)) [w, h] = [h, w];
  if (opts.orientation === 'quadrato') w = h = Math.max(w, h);
  return { W: w, H: h };
}

function layout(W, H, opts) {
  const u = Math.min(W, H) / 100; // unità tipografica
  const frame = opts.poster;
  const margin = frame ? 6 * u : 0;
  const textH = frame && (opts.title || opts.subtitle || opts.date) ? 16 * u : 0;
  // Due colonne solo se le voci sono brevi, altrimenti i testi si sovrapporrebbero.
  const cols = opts.legendEntries.some((e) => e.label.length > 30) || W < H * 0.6 ? 1 : 2;
  const legendH = opts.legend && opts.legendEntries.length ? Math.min(34 * u, (Math.ceil(opts.legendEntries.length / cols) * 3.2 + 2) * u) : 0;
  const mapRect = { x: margin, y: margin, w: W - 2 * margin, h: H - 2 * margin - textH - (frame ? legendH : 0) };
  return { u, margin, textH, legendH, mapRect, frame, cols };
}

function inkFor(bg) {
  return relLuminance(bg) > 0.4 ? '#1d1d1f' : '#f5f5f5';
}

export function renderCanvas(groups, bbox, opts) {
  const { W, H } = resolveSize(opts);
  const c = document.createElement('canvas');
  c.width = W; c.height = H;
  const ctx = c.getContext('2d');
  const L = layout(W, H, opts);
  const bg = opts.background === 'trasparente' ? null : opts.background === 'bianco' ? '#ffffff' : opts.bg;
  if (bg) { ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H); }
  const ink = inkFor(bg || '#ffffff');

  // Mappa nel suo riquadro.
  const mr = L.mapRect;
  const sub = document.createElement('canvas');
  sub.width = Math.round(mr.w); sub.height = Math.round(mr.h);
  const sctx = sub.getContext('2d');
  const proj = fitProjection(bbox, sub.width, sub.height, Math.min(sub.width, sub.height) * 0.02);
  const k = opts.lineScale * Math.max(sub.width, sub.height) / 1100;
  drawSceneCanvas(sctx, groups, proj, { k, tol: 0.35 });
  ctx.drawImage(sub, mr.x, mr.y);

  const u = L.u;
  ctx.fillStyle = ink;
  ctx.textBaseline = 'alphabetic';
  let y = mr.y + mr.h;
  if (L.frame && L.legendH) {
    drawLegendCanvas(ctx, opts.legendEntries, { x: L.margin, y: y + 2 * u, w: W - 2 * L.margin, u, ink, columns: L.cols });
    y += L.legendH;
  }
  if (L.textH) {
    ctx.textAlign = 'center';
    if (opts.title) { ctx.font = `700 ${Math.round(7 * u)}px Georgia, 'Times New Roman', serif`; ctx.fillText(opts.title.toUpperCase(), W / 2, y + 8 * u); }
    const line2 = [opts.subtitle, opts.date].filter(Boolean).join('  ·  ');
    if (line2) { ctx.font = `400 ${Math.round(2.6 * u)}px system-ui, sans-serif`; ctx.fillText(line2, W / 2, y + 12.5 * u); }
  } else if (opts.legend && opts.legendEntries.length) {
    // Senza cornice la legenda va sopra la mappa, in basso a sinistra, su un fondo leggibile.
    const lw = Math.min(W * 0.5, 60 * u), lh = (opts.legendEntries.length * 3.2 + 2) * u;
    ctx.globalAlpha = 0.85;
    ctx.fillStyle = bg ? bg : '#ffffff';
    ctx.fillRect(2 * u, H - lh - 6 * u, lw, lh);
    ctx.globalAlpha = 1;
    drawLegendCanvas(ctx, opts.legendEntries, { x: 3 * u, y: H - lh - 5 * u, w: lw - 2 * u, u, ink, columns: 1 });
  }
  ctx.textAlign = 'right';
  ctx.font = `400 ${Math.max(10, Math.round(1.3 * u))}px system-ui, sans-serif`;
  ctx.fillStyle = ink;
  ctx.globalAlpha = 0.75;
  ctx.fillText(opts.attribution, W - Math.max(L.margin, 1.5 * u), H - Math.max(L.margin / 3, 1.2 * u));
  ctx.globalAlpha = 1;
  return c;
}

function drawLegendCanvas(ctx, entries, { x, y, w, u, ink, columns = 2 }) {
  const colW = w / columns;
  ctx.textAlign = 'left';
  ctx.font = `400 ${Math.round(1.9 * u)}px system-ui, sans-serif`;
  entries.forEach((e, i) => {
    const cx = x + (i % columns) * colW;
    const cy = y + Math.floor(i / columns) * 3.2 * u + 1.6 * u;
    drawSwatch(ctx, e, cx, cy, u);
    ctx.fillStyle = ink;
    ctx.fillText(fitText(ctx, e.label, colW - 8 * u), cx + 7 * u, cy + 0.7 * u);
  });
}

function fitText(ctx, text, max) {
  if (ctx.measureText(text).width <= max) return text;
  let t = text;
  while (t.length > 3 && ctx.measureText(t + '…').width > max) t = t.slice(0, -1);
  return t + '…';
}

function drawSwatch(ctx, e, x, y, u) {
  ctx.save();
  if (e.kind === 'line') {
    ctx.strokeStyle = e.color;
    ctx.lineWidth = Math.max(1, Math.min(2.6, e.width || 1.5) * 0.45 * u);
    ctx.setLineDash(e.dash ? e.dash.map((d) => d * 0.8 * u) : []);
    ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + 5.5 * u, y); ctx.stroke();
  } else if (e.kind === 'fill') {
    ctx.fillStyle = e.color; ctx.fillRect(x, y - 1 * u, 5.5 * u, 2 * u);
  } else if (e.kind === 'point') {
    ctx.fillStyle = e.color; ctx.beginPath(); ctx.arc(x + 2.75 * u, y, 0.8 * u, 0, Math.PI * 2); ctx.fill();
  } else if (e.kind === 'ramp') {
    const g = ctx.createLinearGradient(x, 0, x + 5.5 * u, 0);
    e.colors.forEach((c, i) => g.addColorStop(i / (e.colors.length - 1), c));
    ctx.fillStyle = g; ctx.fillRect(x, y - 1 * u, 5.5 * u, 2 * u);
  }
  ctx.restore();
}

export function renderSvg(groups, bbox, opts) {
  const { W, H } = resolveSize(opts);
  const L = layout(W, H, opts);
  const bg = opts.background === 'trasparente' ? null : opts.background === 'bianco' ? '#ffffff' : opts.bg;
  const ink = inkFor(bg || '#ffffff');
  const mr = L.mapRect;
  const proj = fitProjection(bbox, mr.w, mr.h, Math.min(mr.w, mr.h) * 0.02);
  const k = opts.lineScale * Math.max(mr.w, mr.h) / 1100;
  const u = L.u;
  const out = [];
  out.push(`<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
<title>${esc(opts.title || 'Mappa')}</title>
<desc>${esc(opts.attribution)}</desc>
<defs><filter id="sfoca"><feGaussianBlur stdDeviation="${(2 * k).toFixed(1)}"/></filter><clipPath id="riquadro"><rect x="0" y="0" width="${mr.w}" height="${mr.h}"/></clipPath></defs>`);
  if (bg) out.push(`<rect width="${W}" height="${H}" fill="${bg}"/>`);
  out.push(`<g transform="translate(${mr.x} ${mr.y})" clip-path="url(#riquadro)">`);
  out.push(sceneToSvg(groups, proj, { k, tol: 0.35 }));
  out.push('</g>');
  let y = mr.y + mr.h;
  const legend = (x0, y0, w, columns) => {
    opts.legendEntries.forEach((e, i) => {
      const cx = x0 + (i % columns) * (w / columns);
      const cy = y0 + Math.floor(i / columns) * 3.2 * u + 1.6 * u;
      out.push(svgSwatch(e, cx, cy, u, i));
      out.push(`<text x="${(cx + 7 * u).toFixed(1)}" y="${(cy + 0.7 * u).toFixed(1)}" font-family="system-ui, sans-serif" font-size="${(1.9 * u).toFixed(1)}" fill="${ink}">${esc(e.label)}</text>`);
    });
  };
  if (L.frame && L.legendH) { legend(L.margin, y + 2 * u, W - 2 * L.margin, L.cols); y += L.legendH; }
  if (L.textH) {
    if (opts.title) out.push(`<text x="${W / 2}" y="${(y + 8 * u).toFixed(1)}" text-anchor="middle" font-family="Georgia, 'Times New Roman', serif" font-weight="700" font-size="${(7 * u).toFixed(1)}" fill="${ink}">${esc(opts.title.toUpperCase())}</text>`);
    const line2 = [opts.subtitle, opts.date].filter(Boolean).join('  ·  ');
    if (line2) out.push(`<text x="${W / 2}" y="${(y + 12.5 * u).toFixed(1)}" text-anchor="middle" font-family="system-ui, sans-serif" font-size="${(2.6 * u).toFixed(1)}" fill="${ink}">${esc(line2)}</text>`);
  } else if (opts.legend && opts.legendEntries.length) {
    const lw = Math.min(W * 0.5, 60 * u), lh = (opts.legendEntries.length * 3.2 + 2) * u;
    out.push(`<rect x="${2 * u}" y="${H - lh - 6 * u}" width="${lw}" height="${lh}" fill="${bg || '#ffffff'}" fill-opacity="0.85"/>`);
    legend(3 * u, H - lh - 5 * u, lw - 2 * u, 1);
  }
  out.push(`<text x="${W - Math.max(L.margin, 1.5 * u)}" y="${H - Math.max(L.margin / 3, 1.2 * u)}" text-anchor="end" font-family="system-ui, sans-serif" font-size="${Math.max(10, 1.3 * u).toFixed(1)}" fill="${ink}" fill-opacity="0.75">${esc(opts.attribution)}</text>`);
  out.push('</svg>');
  return out.join('\n');
}

function svgSwatch(e, x, y, u, i) {
  const f = (n) => n.toFixed(1);
  if (e.kind === 'line') {
    const dash = e.dash ? ` stroke-dasharray="${e.dash.map((d) => f(d * 0.8 * u)).join(' ')}"` : '';
    return `<line x1="${f(x)}" y1="${f(y)}" x2="${f(x + 5.5 * u)}" y2="${f(y)}" stroke="${e.color}" stroke-width="${f(Math.max(1, Math.min(2.6, e.width || 1.5) * 0.45 * u))}"${dash}/>`;
  }
  if (e.kind === 'fill') return `<rect x="${f(x)}" y="${f(y - u)}" width="${f(5.5 * u)}" height="${f(2 * u)}" fill="${e.color}"/>`;
  if (e.kind === 'point') return `<circle cx="${f(x + 2.75 * u)}" cy="${f(y)}" r="${f(0.8 * u)}" fill="${e.color}"/>`;
  if (e.kind === 'ramp') {
    const stops = e.colors.map((c, j) => `<stop offset="${(j / (e.colors.length - 1)).toFixed(2)}" stop-color="${c}"/>`).join('');
    return `<defs><linearGradient id="scala${i}">${stops}</linearGradient></defs><rect x="${f(x)}" y="${f(y - u)}" width="${f(5.5 * u)}" height="${f(2 * u)}" fill="url(#scala${i})"/>`;
  }
  return '';
}
