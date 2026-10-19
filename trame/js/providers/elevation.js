// Quote del terreno. Valhalla espone /height, gratuito e senza chiave sul server FOSSGIS.
import { CONFIG } from '../config.js';
import { fetchJson, sleep, resampleLine } from '../util.js';

export function createElevation() {
  const cfg = CONFIG.elevation.valhalla;
  const base = cfg.url.replace(/\/$/, '');
  async function heights(points, { signal } = {}) {
    const out = [];
    for (let i = 0; i < points.length; i += cfg.maxPoints) {
      const chunk = points.slice(i, i + cfg.maxPoints);
      const body = { shape: chunk.map(([lon, lat]) => ({ lat: +lat.toFixed(6), lon: +lon.toFixed(6) })) };
      // POST: con centinaia di punti l'indirizzo GET diventerebbe troppo lungo.
      const data = await fetchJson(`${base}/height`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal, timeout: 30000 });
      out.push(...data.height.map((h) => (h == null || h < -500 ? null : h)));
      if (i + cfg.maxPoints < points.length) await sleep(400, signal);
    }
    return out;
  }
  return {
    name: cfg.name,
    attribution: cfg.attribution,
    maxPoints: cfg.maxPoints,
    heights,
    // Profilo di un percorso: ricampionato, con dislivelli calcolati ignorando oscillazioni sotto i 2 m.
    async profile(coords, { signal } = {}) {
      const pts = resampleLine(coords, cfg.maxPoints);
      const h = await heights(pts, { signal });
      return profileStats(pts, h);
    },
  };
}

export function profileStats(pts, h) {
  let ascent = 0, descent = 0, ref = null, min = Infinity, max = -Infinity;
  const valid = [];
  for (let i = 0; i < h.length; i++) {
    if (h[i] == null) continue;
    valid.push([i, h[i]]);
    min = Math.min(min, h[i]); max = Math.max(max, h[i]);
    if (ref == null) { ref = h[i]; continue; }
    const d = h[i] - ref;
    if (d >= 2) { ascent += d; ref = h[i]; } else if (d <= -2) { descent -= d; ref = h[i]; }
  }
  if (!valid.length) return null;
  return { points: pts, heights: h, ascent, descent, min, max };
}
