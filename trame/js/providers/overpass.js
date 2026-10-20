// Dati cartografici da OpenStreetMap tramite Overpass API.
// Le richieste partono una alla volta, con ritentativi e server di riserva.
import { CONFIG } from '../config.js';
import { fetchWithProgress, HttpError, sleep, isAbort, lineLength, bearing, haversine, bboxOfCoords } from '../util.js';
import { classify } from '../styles.js';

// Filtro geografico per l'area scelta.
export function areaFilter(area) {
  if (area.kind === 'relation') return { pre: `area(id:${3600000000 + Number(area.osmId)})->.a;`, f: '(area.a)' };
  if (area.kind === 'polygon') {
    const pts = area.ring.slice(0, -1).map(([x, y]) => `${y.toFixed(6)} ${x.toFixed(6)}`).join(' ');
    return { pre: '', f: `(poly:"${pts}")` };
  }
  const [w, s, e, n] = area.bbox;
  return { pre: '', f: `(${s.toFixed(6)},${w.toFixed(6)},${n.toFixed(6)},${e.toFixed(6)})` };
}

function clipBox(area) {
  const [w, s, e, n] = area.bbox;
  return `(${s.toFixed(6)},${w.toFixed(6)},${n.toFixed(6)},${e.toFixed(6)})`;
}

const EXCLUDED_HIGHWAYS = 'proposed|construction|abandoned|platform|razed|disused|planned|elevator|bus_stop|services|rest_area|emergency_bay';

const QUERIES = {
  roads: ({ f }) => `way["highway"]["highway"!~"^(${EXCLUDED_HIGHWAYS})$"]${f};out geom qt;`,
  rail: ({ f }) => `way["railway"~"^(rail|light_rail|subway|tram|narrow_gauge|monorail|funicular)$"]${f};out geom qt;`,
  transit: ({ f, clip }) => `relation["route"~"^(bus|trolleybus|tram|share_taxi|light_rail)$"]${f};out geom${clip} qt;`,
  cycleroutes: ({ f, clip }) => `relation["route"="bicycle"]${f};out geom${clip} qt;`,
  buildings: ({ f }) => `way["building"]${f};out geom qt;`,
  green: ({ f }) => `(
    way["leisure"~"^(park|garden|playground|nature_reserve|recreation_ground|pitch)$"]${f};
    way["landuse"~"^(grass|forest|meadow|recreation_ground|village_green|cemetery|orchard|vineyard|allotments)$"]${f};
    way["natural"~"^(wood|scrub|grassland|heath)$"]${f};
    relation["type"="multipolygon"]["leisure"~"^(park|garden|nature_reserve)$"]${f};
    relation["type"="multipolygon"]["landuse"~"^(grass|forest|meadow|recreation_ground)$"]${f};
    relation["type"="multipolygon"]["natural"~"^(wood|scrub)$"]${f};
  );out geom qt;`,
  water: ({ f }) => `(
    way["natural"="water"]${f};
    way["landuse"~"^(reservoir|basin)$"]${f};
    way["waterway"~"^(river|stream|canal|ditch|drain|riverbank)$"]${f};
    relation["type"="multipolygon"]["natural"="water"]${f};
  );out geom qt;`,
  boundaries: ({ f, clip }) => `relation["boundary"="administrative"]["admin_level"="8"]${f};out geom${clip} qt;`,
  poi: ({ f }) => `(
    nwr["name"]["amenity"~"^(townhall|school|library|hospital|pharmacy|police|fire_station|place_of_worship|post_office|theatre|cinema|marketplace|bus_station|community_centre|kindergarten|university|college|clinic|doctors|social_facility|arts_centre)$"]${f};
    nwr["name"]["tourism"~"^(museum|attraction|viewpoint|information|gallery)$"]${f};
    nwr["name"]["historic"]${f};
    node["name"]["railway"="station"]${f};
  );out center tags qt 4000;`,
};

function budgetFor(area) {
  return CONFIG.overpass.budget.find((b) => (area.km2 || 0) <= b.km2);
}

export function buildQuery(layer, area) {
  const { pre, f } = areaFilter(area);
  const clip = clipBox(area);
  const { timeout, maxsize } = budgetFor(area);
  return `[out:json][timeout:${timeout}][maxsize:${maxsize}];${pre}${QUERIES[layer]({ f, clip })}`;
}

/* ---------- memoria locale dei dati scaricati (Cache API) ---------- */

const CACHE_NAME = 'trame-osm-v1';

async function cacheKey(query) {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(query));
  const hex = [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('');
  return `${location.origin}/__trame_cache__/${hex}`;
}

async function cacheGet(query) {
  try {
    const cache = await caches.open(CACHE_NAME);
    const res = await cache.match(await cacheKey(query));
    if (!res) return null;
    const saved = Number(res.headers.get('x-salvato') || 0);
    if (Date.now() - saved > CONFIG.overpass.cacheHours * 3600e3) return null;
    return { json: await res.json(), saved };
  } catch {
    return null; // memoria non disponibile (navigazione privata, spazio esaurito)
  }
}

async function cachePut(query, text) {
  try {
    const cache = await caches.open(CACHE_NAME);
    await cache.put(await cacheKey(query), new Response(text, { headers: { 'Content-Type': 'application/json', 'x-salvato': String(Date.now()) } }));
  } catch { /* memoria non disponibile: si continua senza */ }
}

function parseOverpassText(text) {
  let json;
  try { json = JSON.parse(text); } catch {
    // Overpass a volte risponde in HTML con il motivo del rifiuto.
    if (/rate_limited|Too Many/i.test(text)) throw new HttpError(429, 'Troppe richieste');
    if (/timeout|runtime error/i.test(text)) throw new HttpError(504, 'Richiesta troppo pesante');
    throw new HttpError(502, 'Risposta non leggibile');
  }
  if (json.remark && /runtime error|timed out|out of memory/i.test(json.remark)) throw new HttpError(413, json.remark);
  return json;
}

// Esegue una richiesta Overpass. Un server che non risponde o è occupato viene
// lasciato subito per il successivo; solo se tutti sono occupati si attende e si riprova.
export async function runOverpass(query, { signal, onProgress, onStatus, force = false } = {}) {
  if (!force) {
    const hit = await cacheGet(query);
    if (hit) {
      const h = Math.max(1, Math.round((Date.now() - hit.saved) / 3600e3));
      onStatus?.(`Dati già scaricati (meno di ${h} ${h === 1 ? 'ora' : 'ore'} fa)`);
      hit.json.__daMemoria = hit.saved;
      return hit.json;
    }
  }
  const { endpoints } = CONFIG.overpass;
  const declared = Number(/\[timeout:(\d+)\]/.exec(query)?.[1] || 90);
  let lastErr;
  for (let round = 0; round < 2; round++) {
    let allBusy = true;
    for (let ei = 0; ei < endpoints.length; ei++) {
      const url = endpoints[ei];
      const host = new URL(url).host;
      try {
        onStatus?.(`Il server ${host} sta preparando i dati`);
        const text = await fetchWithProgress(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
          body: 'data=' + encodeURIComponent(query),
          signal,
          timeout: (declared + 15) * 1000,
          onProgress: (b) => { onStatus?.(`Ricezione da ${host}`); onProgress?.(b); },
        });
        const json = parseOverpassText(text);
        cachePut(query, text);
        return json;
      } catch (e) {
        if (isAbort(e)) throw e;
        lastErr = e;
        if (e instanceof HttpError && (e.status === 413 || e.status === 400)) throw e; // inutile insistere altrove
        if (!(e instanceof HttpError && e.status === 429)) allBusy = false;
        if (ei < endpoints.length - 1) onStatus?.(`${host} non disponibile, passo a un server di riserva`);
      }
    }
    if (!allBusy || round === 1) break;
    onStatus?.('Tutti i server sono occupati, nuovo tentativo tra 10 s');
    await sleep(10000, signal);
  }
  throw lastErr || new Error('Nessun server Overpass disponibile');
}

export function overpassErrorMessage(e) {
  if (e instanceof HttpError) {
    if (e.status === 429) return 'I server OpenStreetMap stanno ricevendo troppe richieste. Attendi un minuto e riprova.';
    if (e.status === 413 || e.status === 504) return 'L\'area è troppo grande o i dati sono troppi per il server. Restringi l\'area e riprova.';
    if (e.status === 408) return 'Il server non ha risposto in tempo. Riprova o restringi l\'area.';
    if (e.status === 400) return 'La richiesta non è stata accettata dal server (area non valida).';
    return `Il server dei dati ha risposto con un errore (${e.status}).`;
  }
  return 'Impossibile raggiungere i server dei dati OpenStreetMap. Controlla la connessione e riprova.';
}

/* ---------- trasformazione in elementi geografici ---------- */

const geomToCoords = (g) => {
  // Con il ritaglio sul riquadro, i nodi esterni arrivano come null: si spezza la linea.
  const parts = [];
  let cur = [];
  for (const p of g || []) {
    if (!p) { if (cur.length > 1) parts.push(cur); cur = []; continue; }
    cur.push([p.lon, p.lat]);
  }
  if (cur.length > 1) parts.push(cur);
  return parts;
};

const isClosed = (c) => c.length > 3 && c[0][0] === c[c.length - 1][0] && c[0][1] === c[c.length - 1][1];

// Unisce i tratti dei membri di un multipoligono in anelli chiusi.
function assembleRings(lines) {
  const rings = [];
  const pool = lines.map((l) => l.slice());
  while (pool.length) {
    let ring = pool.shift();
    let guard = 0;
    while (!isClosed(ring) && guard++ < 10000) {
      const end = ring[ring.length - 1];
      const idx = pool.findIndex((l) => sameP(l[0], end) || sameP(l[l.length - 1], end));
      if (idx < 0) break;
      let next = pool.splice(idx, 1)[0];
      if (!sameP(next[0], end)) next = next.reverse();
      ring = ring.concat(next.slice(1));
    }
    if (isClosed(ring)) rings.push(ring);
  }
  return rings;
}
const sameP = (a, b) => a[0] === b[0] && a[1] === b[1];

function multipolygonFromRelation(rel) {
  const outers = [], inners = [];
  for (const m of rel.members || []) {
    if (m.type !== 'way' || !m.geometry) continue;
    const parts = geomToCoords(m.geometry);
    for (const p of parts) (m.role === 'inner' ? inners : outers).push(p);
  }
  const outerRings = assembleRings(outers);
  const innerRings = assembleRings(inners);
  if (!outerRings.length) return null;
  if (outerRings.length === 1) return { type: 'Polygon', coordinates: [outerRings[0], ...innerRings] };
  // Ogni foro va nel primo anello esterno che lo contiene (controllo sul primo vertice).
  const polys = outerRings.map((r) => [r]);
  for (const h of innerRings) {
    const host = polys.find((p) => pointInRingFast(h[0], p[0]));
    (host || polys[0]).push(h);
  }
  return { type: 'MultiPolygon', coordinates: polys };
}

function pointInRingFast(pt, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const xi = ring[i][0], yi = ring[i][1], xj = ring[j][0], yj = ring[j][1];
    if (yi > pt[1] !== yj > pt[1] && pt[0] < ((xj - xi) * (pt[1] - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

const ROAD_TAGS = ['name', 'ref', 'oneway', 'surface', 'access', 'lit', 'maxspeed', 'toll', 'bicycle', 'foot',
  'motor_vehicle', 'motorcar', 'junction', 'lanes', 'bridge', 'tunnel', 'sidewalk', 'width', 'smoothness', 'tracktype', 'segregated'];

function pickTags(tags, keys) {
  const out = {};
  for (const k of keys) if (tags[k] != null) out[k] = tags[k];
  return out;
}

// Orientamento assiale (0..180) pesato sulla lunghezza dei tratti.
function axialOrientation(coords) {
  let sx = 0, sy = 0;
  for (let i = 1; i < coords.length; i++) {
    const d = haversine(coords[i - 1], coords[i]);
    if (!d) continue;
    const a = (bearing(coords[i - 1], coords[i]) % 180) * (Math.PI / 90); // angolo raddoppiato
    sx += Math.cos(a) * d;
    sy += Math.sin(a) * d;
  }
  let deg = (Math.atan2(sy, sx) * 90) / Math.PI;
  if (deg < 0) deg += 180;
  return deg;
}

export function parseRoads(json) {
  const roads = [];
  for (const el of json.elements || []) {
    if (el.type !== 'way' || !el.geometry || !el.tags?.highway) continue;
    const coords = el.geometry.map((p) => (p ? [p.lon, p.lat] : null));
    if (coords.some((c) => !c) || coords.length < 2) continue;
    const tags = pickTags(el.tags, ROAD_TAGS);
    tags.highway = el.tags.highway;
    roads.push({
      id: el.id,
      tags,
      cls: classify(el.tags),
      coords,
      nodes: el.nodes || null,
      len: lineLength(coords),
      ori: axialOrientation(coords),
      bbox: bboxOfCoords(coords),
    });
  }
  return roads;
}

const LAYER_TAGS = ['name', 'ref', 'railway', 'route', 'network', 'operator', 'building', 'leisure', 'landuse', 'natural',
  'waterway', 'water', 'admin_level', 'amenity', 'tourism', 'historic', 'shop', 'opening_hours', 'website', 'colour', 'from', 'to', 'levels', 'height', 'addr:street', 'addr:housenumber'];

// Converte la risposta di un livello aggiuntivo in GeoJSON.
export function parseLayer(layer, json) {
  const features = [];
  const push = (geometry, el, extra = {}) => {
    features.push({
      type: 'Feature',
      geometry,
      properties: { osmType: el.type, osmId: el.id, layer, ...pickTags(el.tags || {}, LAYER_TAGS), ...extra },
    });
  };
  for (const el of json.elements || []) {
    if (layer === 'poi') {
      const c = el.type === 'node' ? [el.lon, el.lat] : el.center ? [el.center.lon, el.center.lat] : null;
      if (c) push({ type: 'Point', coordinates: c }, el);
      continue;
    }
    if (el.type === 'way') {
      const parts = geomToCoords(el.geometry);
      if (!parts.length) continue;
      const fillish = layer === 'buildings' || layer === 'green' || (layer === 'water' && !el.tags?.waterway) || el.tags?.waterway === 'riverbank';
      if (fillish && parts.length === 1 && isClosed(parts[0])) push({ type: 'Polygon', coordinates: [parts[0]] }, el, { shape: 'area' });
      else if (!fillish) push(parts.length === 1 ? { type: 'LineString', coordinates: parts[0] } : { type: 'MultiLineString', coordinates: parts }, el, { shape: 'line' });
    } else if (el.type === 'relation') {
      if (layer === 'green' || layer === 'water') {
        const g = multipolygonFromRelation(el);
        if (g) push(g, el, { shape: 'area' });
      } else {
        const lines = [];
        for (const m of el.members || []) if (m.type === 'way' && m.geometry) lines.push(...geomToCoords(m.geometry));
        if (lines.length) push({ type: 'MultiLineString', coordinates: lines }, el, { shape: 'line' });
      }
    }
  }
  return features;
}
