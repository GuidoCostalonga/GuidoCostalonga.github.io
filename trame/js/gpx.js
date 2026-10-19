// Importazione ed esportazione di tracce GPX e GeoJSON.
import { esc } from './util.js';

export function parseGpx(text) {
  const doc = new DOMParser().parseFromString(text, 'application/xml');
  if (doc.querySelector('parsererror')) throw new Error('Il file GPX non è leggibile.');
  const tracks = [];
  const read = (pts) => {
    const coords = [], ele = [];
    for (const p of pts) {
      const lat = parseFloat(p.getAttribute('lat')), lon = parseFloat(p.getAttribute('lon'));
      if (!isFinite(lat) || !isFinite(lon)) continue;
      coords.push([lon, lat]);
      const e = p.getElementsByTagName('ele')[0];
      ele.push(e ? parseFloat(e.textContent) : null);
    }
    return { coords, elevations: ele.some((x) => x != null && isFinite(x)) ? ele : null };
  };
  for (const trk of doc.getElementsByTagName('trk')) {
    const name = trk.getElementsByTagName('name')[0]?.textContent?.trim();
    for (const seg of trk.getElementsByTagName('trkseg')) {
      const t = read(seg.getElementsByTagName('trkpt'));
      if (t.coords.length > 1) tracks.push({ name: name || 'Traccia GPX', ...t });
    }
  }
  for (const rte of doc.getElementsByTagName('rte')) {
    const name = rte.getElementsByTagName('name')[0]?.textContent?.trim();
    const t = read(rte.getElementsByTagName('rtept'));
    if (t.coords.length > 1) tracks.push({ name: name || 'Itinerario GPX', ...t });
  }
  if (!tracks.length) throw new Error('Il file GPX non contiene tracce o itinerari.');
  return tracks;
}

export function parseGeoJsonTracks(text) {
  let gj;
  try { gj = JSON.parse(text); } catch { throw new Error('Il file GeoJSON non è leggibile.'); }
  const feats = gj.type === 'FeatureCollection' ? gj.features : gj.type === 'Feature' ? [gj] : [{ type: 'Feature', geometry: gj, properties: {} }];
  const tracks = [];
  for (const f of feats) {
    const g = f?.geometry;
    if (!g) continue;
    const name = f.properties?.name || f.properties?.nome || 'Traccia GeoJSON';
    const toTrack = (coords) => {
      if (coords.length > 1) tracks.push({ name, coords: coords.map((c) => [c[0], c[1]]), elevations: coords[0].length > 2 ? coords.map((c) => c[2]) : null });
    };
    if (g.type === 'LineString') toTrack(g.coordinates);
    if (g.type === 'MultiLineString') g.coordinates.forEach(toTrack);
  }
  if (!tracks.length) throw new Error('Il file GeoJSON non contiene linee da mostrare come percorso.');
  return tracks;
}

export function routesToGpx(routes, meta = {}) {
  const now = new Date().toISOString();
  const trks = routes.map((r) => {
    const pts = r.coords.map((c, i) => {
      const e = r.elevations?.[i];
      return `      <trkpt lat="${c[1].toFixed(7)}" lon="${c[0].toFixed(7)}">${e != null && isFinite(e) ? `<ele>${e.toFixed(1)}</ele>` : ''}</trkpt>`;
    }).join('\n');
    const desc = [r.kindLabel, r.distance ? `${(r.distance / 1000).toFixed(2)} km` : null].filter(Boolean).join(', ');
    return `  <trk>\n    <name>${esc(r.name)}</name>\n    <desc>${esc(desc)}</desc>\n    <trkseg>\n${pts}\n    </trkseg>\n  </trk>`;
  }).join('\n');
  return `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Trame, costalonga.org" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <name>${esc(meta.name || 'Percorso')}</name>
    <desc>${esc(meta.desc || '')}</desc>
    <time>${now}</time>
  </metadata>
${trks}
</gpx>
`;
}

export function routesToGeoJson(routes) {
  return {
    type: 'FeatureCollection',
    features: routes.map((r) => ({
      type: 'Feature',
      properties: {
        nome: r.name, tipo: r.kindLabel, distanza_m: r.distance != null ? Math.round(r.distance) : null,
        durata_s: r.duration != null ? Math.round(r.duration) : null, durata_stimata: !!r.estimated,
        salita_m: r.ascent != null ? Math.round(r.ascent) : null, discesa_m: r.descent != null ? Math.round(r.descent) : null,
        fonte: r.source,
      },
      geometry: { type: 'LineString', coordinates: r.coords.map((c, i) => (r.elevations?.[i] != null ? [c[0], c[1], r.elevations[i]] : [c[0], c[1]])) },
    })),
  };
}
