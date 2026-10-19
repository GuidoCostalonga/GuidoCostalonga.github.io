// Adattatori di calcolo percorso. Tutti restituiscono lo stesso formato:
// { routes: [{ coords, distance, duration, steps, ascent?, descent? }], notes: [] }
// Le capacità dichiarate guidano l'interfaccia: un'opzione non supportata viene
// disattivata con la spiegazione, non simulata.
import { CONFIG } from '../config.js';
import { fetchJson, HttpError, decodePolyline, lineLength } from '../util.js';
import { buildGraph, localRoute } from '../graph.js';

export const MODES = { car: 'In auto', bike: 'In bicicletta', foot: 'A piedi' };

const ALL_OPTS = ['shortest', 'avoidMotorway', 'avoidToll', 'avoidFerry', 'avoidUnpaved'];
export const OPTION_LABELS = {
  shortest: 'Percorso più breve (invece del più rapido)',
  avoidMotorway: 'Evita autostrade e superstrade',
  avoidToll: 'Evita strade a pedaggio',
  avoidFerry: 'Evita traghetti',
  avoidUnpaved: 'Evita strade non asfaltate',
};

/* ---------- Valhalla ---------- */

function valhallaAdapter(url) {
  const base = url.replace(/\/$/, '');
  const costing = { car: 'auto', bike: 'bicycle', foot: 'pedestrian' };
  return {
    id: 'valhalla',
    name: CONFIG.routing.valhalla.name,
    attribution: CONFIG.routing.valhalla.attribution,
    modes: ['car', 'bike', 'foot'],
    capabilities(mode) {
      const opts = { shortest: true, avoidFerry: true };
      if (mode === 'car') Object.assign(opts, { avoidMotorway: true, avoidToll: true, avoidUnpaved: true });
      if (mode === 'bike') Object.assign(opts, { avoidUnpaved: true });
      return { alternatives: true, steps: true, options: opts, note: 'Le opzioni "evita" riducono al minimo quelle strade, ma il servizio può usarle se non esistono alternative.' };
    },
    async route({ points, mode, options = {}, alternatives, signal }) {
      const c = costing[mode];
      const co = {};
      if (options.shortest) co.shortest = true;
      if (options.avoidFerry) co.use_ferry = 0;
      if (mode === 'car') {
        if (options.avoidMotorway) co.use_highways = 0;
        if (options.avoidToll) co.use_tolls = 0;
        if (options.avoidUnpaved) co.exclude_unpaved = true;
      }
      if (mode === 'bike' && options.avoidUnpaved) co.avoid_bad_surfaces = 1;
      const body = {
        locations: points.map(([lon, lat], i) => ({ lat, lon, type: i === 0 || i === points.length - 1 ? 'break' : 'through' })),
        costing: c,
        costing_options: { [c]: co },
        directions_options: { units: 'kilometers', language: 'it-IT' },
      };
      if (alternatives && points.length === 2) body.alternates = 2;
      // GET evita la richiesta preliminare CORS.
      const data = await fetchJson(`${base}/route?json=${encodeURIComponent(JSON.stringify(body))}`, { signal, timeout: 30000 });
      const trips = [data.trip, ...(data.alternates || []).map((a) => a.trip)].filter(Boolean);
      return {
        routes: trips.map((t) => {
          let coords = [];
          const steps = [];
          for (const leg of t.legs) {
            const shape = decodePolyline(leg.shape, 6);
            const off = coords.length ? coords.length - 1 : 0;
            coords = coords.length ? coords.concat(shape.slice(1)) : shape;
            for (const m of leg.maneuvers) {
              steps.push({ text: m.instruction, distance: m.length * 1000, duration: m.time, coord: coords[off + m.begin_shape_index] || shape[m.begin_shape_index], type: m.type });
            }
          }
          return { coords, distance: t.summary.length * 1000, duration: t.summary.time, steps };
        }),
        notes: [],
      };
    },
  };
}

/* ---------- OSRM ---------- */

const MOD_IT = {
  uturn: 'fai inversione', 'sharp right': 'svolta decisamente a destra', right: 'svolta a destra',
  'slight right': 'tieni la destra', straight: 'prosegui dritto', 'slight left': 'tieni la sinistra',
  left: 'svolta a sinistra', 'sharp left': 'svolta decisamente a sinistra',
};
const ORD = ['', 'prima', 'seconda', 'terza', 'quarta', 'quinta', 'sesta', 'settima', 'ottava'];

function osrmInstruction(step) {
  const m = step.maneuver;
  const name = step.name || step.ref || '';
  const su = name ? ` su ${name}` : '';
  const mod = MOD_IT[m.modifier] || 'prosegui';
  const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);
  switch (m.type) {
    case 'depart': return `Parti${su}`;
    case 'arrive': return m.modifier === 'left' ? 'Sei arrivato: la destinazione è a sinistra' : m.modifier === 'right' ? 'Sei arrivato: la destinazione è a destra' : 'Sei arrivato a destinazione';
    case 'roundabout': case 'rotary': case 'exit roundabout': case 'exit rotary':
      return `Alla rotonda prendi la ${ORD[m.exit] || m.exit + 'ª'} uscita${su}`.replace('la undefinedª uscita', "l'uscita");
    case 'merge': return `Immettiti${su}`;
    case 'on ramp': return `Prendi la rampa${su}`;
    case 'off ramp': return `Esci${su}`;
    case 'fork': return `Al bivio ${m.modifier?.includes('left') ? 'tieni la sinistra' : 'tieni la destra'}${su}`;
    case 'end of road': return `In fondo alla strada ${mod}${su}`;
    case 'new name': case 'continue': return `${m.modifier && m.modifier !== 'straight' ? cap(mod) : 'Continua'}${su}`;
    default: return `${cap(mod)}${su}`;
  }
}

function osrmAdapter(urlTemplate) {
  const profile = { car: 'routed-car', bike: 'routed-bike', foot: 'routed-foot' };
  const isFossgis = urlTemplate.includes('{profile}');
  return {
    id: 'osrm',
    name: isFossgis ? CONFIG.routing.osrm.name : 'OSRM (server personalizzato)',
    attribution: CONFIG.routing.osrm.attribution,
    modes: isFossgis ? ['car', 'bike', 'foot'] : ['car'],
    capabilities() {
      return { alternatives: true, steps: true, options: {}, note: 'Il server OSRM pubblico non accetta esclusioni né la scelta tra percorso breve e rapido. Per queste opzioni scegli Valhalla.' };
    },
    async route({ points, mode, alternatives, signal }) {
      const base = urlTemplate.replace('{profile}', profile[mode]).replace(/\/$/, '');
      const coordStr = points.map(([x, y]) => `${x.toFixed(6)},${y.toFixed(6)}`).join(';');
      const params = new URLSearchParams({ overview: 'full', geometries: 'geojson', steps: 'true', alternatives: alternatives && points.length === 2 ? '2' : 'false' });
      const data = await fetchJson(`${base}/route/v1/driving/${coordStr}?${params}`, { signal, timeout: 30000 });
      if (data.code !== 'Ok') throw new HttpError(422, data.message || data.code);
      return {
        routes: data.routes.map((r) => ({
          coords: r.geometry.coordinates,
          distance: r.distance,
          duration: r.duration,
          steps: r.legs.flatMap((l) => l.steps.map((s) => ({ text: osrmInstruction(s), distance: s.distance, duration: s.duration, coord: s.maneuver.location }))),
        })),
        notes: [],
      };
    },
  };
}

/* ---------- GraphHopper (chiave inserita dall'utente) ---------- */

function graphhopperAdapter(key) {
  const base = CONFIG.routing.graphhopper.url;
  const profile = { car: 'car', bike: 'bike', foot: 'foot' };
  return {
    id: 'graphhopper',
    name: CONFIG.routing.graphhopper.name,
    attribution: CONFIG.routing.graphhopper.attribution,
    modes: ['car', 'bike', 'foot'],
    requiresKey: true,
    capabilities(mode) {
      const opts = { avoidFerry: true, avoidUnpaved: true };
      if (mode === 'car') Object.assign(opts, { avoidMotorway: true, avoidToll: true });
      return { alternatives: true, steps: true, elevation: true, options: opts, note: 'Le esclusioni usano un modello personalizzato: alcuni piani GraphHopper potrebbero non consentirlo.' };
    },
    async route({ points, mode, options = {}, alternatives, signal }) {
      if (!key) throw new HttpError(401, 'Chiave mancante');
      const priority = [];
      if (options.avoidMotorway) priority.push({ if: 'road_class == MOTORWAY || road_class == TRUNK', multiply_by: '0' });
      if (options.avoidToll) priority.push({ if: 'toll != NO', multiply_by: '0' });
      if (options.avoidFerry) priority.push({ if: 'road_environment == FERRY', multiply_by: '0' });
      if (options.avoidUnpaved) priority.push({ if: 'surface == UNPAVED || surface == GRAVEL || surface == DIRT || surface == GROUND', multiply_by: '0.05' });
      const body = {
        points: points.map(([x, y]) => [x, y]), profile: profile[mode], locale: 'it', instructions: true,
        points_encoded: false, elevation: true, calc_points: true,
      };
      if (priority.length) { body.custom_model = { priority }; body['ch.disable'] = true; }
      if (alternatives && points.length === 2) { body.algorithm = 'alternative_route'; body['alternative_route.max_paths'] = 3; body['ch.disable'] = true; }
      const data = await fetchJson(`${base}/route?key=${encodeURIComponent(key)}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal, timeout: 30000,
      });
      return {
        routes: (data.paths || []).map((p) => {
          const coords3 = p.points.coordinates;
          return {
            coords: coords3.map((c) => [c[0], c[1]]),
            elevations: coords3[0]?.length > 2 ? coords3.map((c) => c[2]) : null,
            distance: p.distance,
            duration: p.time / 1000,
            ascent: p.ascend,
            descent: p.descend,
            steps: (p.instructions || []).map((s) => ({ text: s.text, distance: s.distance, duration: s.time / 1000, coord: coords3[s.interval[0]]?.slice(0, 2) })),
          };
        }),
        notes: [],
      };
    },
  };
}

/* ---------- Motore locale sulla rete caricata ---------- */

function localAdapter(getRoads) {
  const cache = new Map();
  return {
    id: 'local',
    name: 'Motore locale (rete caricata)',
    attribution: 'Percorsi: calcolo locale sui dati © OpenStreetMap caricati',
    modes: ['car', 'bike', 'foot'],
    local: true,
    capabilities() {
      return { alternatives: false, steps: false, options: { shortest: true, avoidMotorway: true, avoidToll: true, avoidUnpaved: true }, note: 'Calcolo eseguito nel browser sulla sola rete caricata: non considera divieti di svolta, traffico o orari. I tempi sono stime basate su limiti e tipo di strada.' };
    },
    graph(mode, options = {}) {
      const roads = getRoads();
      if (!roads?.length) return null;
      const key = `${mode}|${ALL_OPTS.map((o) => (options[o] ? 1 : 0)).join('')}|${roads.length}|${roads._version || 0}`;
      if (!cache.has(key)) {
        cache.clear();
        cache.set(key, buildGraph(roads, mode, options));
      }
      return cache.get(key);
    },
    async route({ points, mode, options = {} }) {
      const g = this.graph(mode, options);
      if (!g) throw new HttpError(412, 'Carica prima la rete stradale dell\'area: il motore locale lavora solo su quella.');
      const r = localRoute(g, points);
      if (r.error === 'far') throw new HttpError(412, 'Uno dei punti è fuori dalla rete caricata (o a più di 1,5 km da una strada percorribile).');
      if (r.error === 'disconnected') throw new HttpError(404, 'Nella rete caricata non esiste un collegamento percorribile tra i punti con queste impostazioni.');
      return {
        routes: [{ coords: r.coords, distance: r.length, duration: r.time, steps: [], estimated: true }],
        notes: ['Durata stimata dal motore locale, senza traffico né semafori.'],
      };
    },
  };
}

export function createRouter(settings, getRoads) {
  switch (settings.provider) {
    case 'osrm': return osrmAdapter(settings.customUrl || CONFIG.routing.osrm.url);
    case 'graphhopper': return graphhopperAdapter(settings.ghKey);
    case 'local': return localAdapter(getRoads);
    case 'valhalla':
    default: return valhallaAdapter(settings.customUrl || CONFIG.routing.valhalla.url);
  }
}

export function routingErrorMessage(e, router) {
  if (e instanceof HttpError) {
    if (e.status === 401 || e.status === 403) return `${router.name}: chiave mancante o non valida. Inseriscila nelle impostazioni del percorso, oppure scegli Valhalla, che non ne richiede.`;
    if (e.status === 429) return `${router.name}: troppe richieste in poco tempo. Attendi qualche secondo e riprova.`;
    if (e.status === 408) return `${router.name}: il servizio non ha risposto in tempo. Riprova.`;
    if (e.status === 412 || e.status === 404) return e.message;
    let msg = e.message;
    try { msg = JSON.parse(e.message).error || JSON.parse(e.message).message || msg; } catch { /* testo semplice */ }
    if (/No path|No route|Impossible route|couldn't find|NoRoute/i.test(msg)) return 'Nessun percorso trovato tra questi punti con il mezzo e le opzioni scelti.';
    return `${router.name}: errore ${e.status}${msg ? ' (' + String(msg).slice(0, 140) + ')' : ''}.`;
  }
  return `${router.name}: servizio non raggiungibile. Controlla la connessione o scegli un altro motore di calcolo.`;
}

export { lineLength };
