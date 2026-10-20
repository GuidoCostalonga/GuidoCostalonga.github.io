// Configurazione dei fornitori di servizi.
// Ogni voce si può sostituire con un servizio compatibile o autogestito
// senza toccare il resto del codice. Nessuna chiave segreta sta qui: la
// chiave GraphHopper, se serve, la inserisce l'utente e resta nel suo browser.

export const CONFIG = {
  geocoding: {
    // Nominatim vieta il completamento automatico: si cerca solo su invio,
    // con al massimo una richiesta al secondo.
    nominatim: {
      name: 'Nominatim (OpenStreetMap)',
      url: 'https://nominatim.openstreetmap.org',
      minInterval: 1100,
      attribution: 'Ricerca: Nominatim, © OpenStreetMap',
    },
    photon: {
      name: 'Photon (komoot)',
      url: 'https://photon.komoot.io',
      minInterval: 600,
      attribution: 'Ricerca: Photon di komoot, © OpenStreetMap',
    },
  },

  overpass: {
    // Server pubblici, usati in ordine: se uno è occupato si passa al successivo.
    endpoints: [
      'https://overpass-api.de/api/interpreter',
      'https://overpass.private.coffee/api/interpreter',
      'https://maps.mail.ru/osm/tools/overpass/api/interpreter',
      'https://overpass.kumi.systems/api/interpreter',
    ],
    // Tempo massimo (secondi) e memoria dichiarati al server in base alla superficie:
    // richieste piccole e "leggere" vengono servite prima quando il server è affollato.
    budget: [
      { km2: 30, timeout: 45, maxsize: 64 * 1024 * 1024 },
      { km2: 250, timeout: 90, maxsize: 256 * 1024 * 1024 },
      { km2: Infinity, timeout: 180, maxsize: 512 * 1024 * 1024 },
    ],
    cacheHours: 24, // i dati già scaricati restano nel browser per un giorno
  },

  routing: {
    valhalla: {
      name: 'Valhalla (FOSSGIS)',
      url: 'https://valhalla1.openstreetmap.de',
      attribution: 'Percorsi: Valhalla, server FOSSGIS, © OpenStreetMap',
    },
    osrm: {
      name: 'OSRM (FOSSGIS)',
      // {profile} diventa routed-car, routed-bike o routed-foot
      url: 'https://routing.openstreetmap.de/{profile}',
      attribution: 'Percorsi: OSRM, server FOSSGIS, © OpenStreetMap',
    },
    graphhopper: {
      name: 'GraphHopper (chiave personale)',
      url: 'https://graphhopper.com/api/1',
      attribution: 'Percorsi: GraphHopper, © OpenStreetMap',
    },
  },

  elevation: {
    valhalla: {
      name: 'Valhalla (FOSSGIS)',
      url: 'https://valhalla1.openstreetmap.de',
      maxPoints: 400,
      attribution: 'Quote: modello altimetrico di Valhalla (FOSSGIS)',
    },
  },

  basemaps: {
    osm: {
      name: 'OpenStreetMap',
      tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
      maxzoom: 19,
      attribution: '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">contributori OpenStreetMap</a>',
    },
    'carto-light': {
      name: 'CARTO chiara',
      tiles: ['a', 'b', 'c'].map((s) => `https://${s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}@2x.png`),
      maxzoom: 20,
      attribution: '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">contributori OpenStreetMap</a>, © <a href="https://carto.com/attributions" target="_blank" rel="noopener">CARTO</a>',
    },
    'carto-dark': {
      name: 'CARTO scura',
      tiles: ['a', 'b', 'c'].map((s) => `https://${s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png`),
      maxzoom: 20,
      attribution: '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">contributori OpenStreetMap</a>, © <a href="https://carto.com/attributions" target="_blank" rel="noopener">CARTO</a>',
    },
  },

  glyphs: 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf',
  font: ['Noto Sans Regular'],

  // Soglie di superficie (km²) per livello: oltre "avviso" si chiede conferma,
  // oltre "massimo" la richiesta non parte.
  limits: {
    roads: { warn: 120, max: 1500 },
    rail: { warn: 800, max: 5000 },
    transit: { warn: 150, max: 800 },
    buildings: { warn: 30, max: 150 },
    green: { warn: 150, max: 1500 },
    water: { warn: 300, max: 3000 },
    boundaries: { warn: 800, max: 5000 },
    poi: { warn: 60, max: 400 },
    cycleroutes: { warn: 400, max: 2500 },
  },

  // Oltre questo numero di strade la rete viene semplificata a bassi ingrandimenti.
  heavyFeatureCount: 60000,
};

export const ATTRIBUTION_OSM =
  'Dati © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">contributori OpenStreetMap</a> (ODbL)';
