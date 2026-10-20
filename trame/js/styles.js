// Classi stradali, livelli, tavolozze e stili pronti.

// Classi della rete: ogni valore OSM "highway" finisce in una sola classe.
// Il tratteggio distingue i percorsi non carrabili anche senza colori.
export const ROAD_CLASSES = [
  { key: 'motorway', label: 'Autostrade e superstrade', values: ['motorway', 'motorway_link', 'trunk', 'trunk_link'], width: 3.2, dash: null, rank: 9 },
  { key: 'primary', label: 'Strade principali', values: ['primary', 'primary_link'], width: 2.6, dash: null, rank: 8 },
  { key: 'secondary', label: 'Strade secondarie', values: ['secondary', 'secondary_link'], width: 2.1, dash: null, rank: 7 },
  { key: 'tertiary', label: 'Strade di collegamento locale', values: ['tertiary', 'tertiary_link'], width: 1.7, dash: null, rank: 6 },
  { key: 'residential', label: 'Strade residenziali', values: ['residential', 'unclassified', 'living_street', 'road'], width: 1.2, dash: null, rank: 5 },
  { key: 'service', label: 'Strade di servizio', values: ['service'], width: 0.8, dash: null, rank: 4 },
  { key: 'track', label: 'Strade agricole e sterrate', values: ['track'], width: 0.8, dash: [3, 1.5], rank: 3 },
  { key: 'pedestrian', label: 'Vie e aree pedonali', values: ['pedestrian', 'footway', 'steps', 'corridor'], width: 0.7, dash: [2, 1.2], rank: 2 },
  { key: 'path', label: 'Sentieri', values: ['path', 'bridleway'], width: 0.7, dash: [1, 1.2], rank: 1 },
  { key: 'cycleway', label: 'Piste ciclabili', values: ['cycleway'], width: 0.9, dash: [4, 1.2], rank: 2 },
  { key: 'other', label: 'Altre vie', values: [], width: 0.6, dash: null, rank: 0 },
];

export const CLASS_BY_KEY = Object.fromEntries(ROAD_CLASSES.map((c) => [c.key, c]));

const HIGHWAY_TO_CLASS = {};
for (const c of ROAD_CLASSES) for (const v of c.values) HIGHWAY_TO_CLASS[v] = c.key;

export function classify(tags) {
  const h = tags.highway;
  if ((h === 'path' || h === 'footway') && (tags.bicycle === 'designated')) return 'cycleway';
  return HIGHWAY_TO_CLASS[h] || 'other';
}

// Livelli aggiuntivi, caricati solo quando vengono attivati.
export const LAYERS = [
  { key: 'roads', label: 'Rete stradale', kind: 'line', always: true },
  { key: 'rail', label: 'Ferrovie e tranvie', kind: 'line' },
  { key: 'transit', label: 'Linee di trasporto pubblico', kind: 'line' },
  { key: 'cycleroutes', label: 'Itinerari ciclabili', kind: 'line' },
  { key: 'buildings', label: 'Edifici', kind: 'fill' },
  { key: 'green', label: 'Parchi e aree verdi', kind: 'fill' },
  { key: 'water', label: 'Acque e corsi d\'acqua', kind: 'fill' },
  { key: 'boundaries', label: 'Confini comunali', kind: 'line' },
  { key: 'poi', label: 'Punti di interesse', kind: 'point' },
];

export const LAYER_BY_KEY = Object.fromEntries(LAYERS.map((l) => [l.key, l]));

// Attributi OSM filtrabili: compaiono solo se presenti nei dati caricati.
export const ATTRIBUTES = [
  { key: 'oneway', label: 'Senso unico' },
  { key: 'surface', label: 'Superficie' },
  { key: 'access', label: 'Accesso' },
  { key: 'lit', label: 'Illuminazione' },
  { key: 'maxspeed', label: 'Limite di velocità' },
];

export const VALUE_LABELS = {
  oneway: { yes: 'sì', no: 'no', '-1': 'sì, verso opposto', reversible: 'reversibile', alternating: 'alternato' },
  lit: { yes: 'sì', no: 'no', automatic: 'automatica', limited: 'limitata', '24/7': 'sempre accesa' },
  access: { yes: 'libero', no: 'vietato', private: 'privato', permissive: 'consentito dal proprietario', destination: 'solo destinatari', delivery: 'solo consegne', customers: 'solo clienti', agricultural: 'solo mezzi agricoli', forestry: 'solo mezzi forestali', permit: 'con permesso', designated: 'riservato', discouraged: 'sconsigliato', emergency: 'solo emergenze' },
  surface: { asphalt: 'asfalto', paved: 'pavimentata', unpaved: 'non pavimentata', concrete: 'cemento', paving_stones: 'masselli', sett: 'cubetti di pietra', cobblestone: 'acciottolato', gravel: 'ghiaia', fine_gravel: 'ghiaino', compacted: 'terra battuta compatta', ground: 'terra', dirt: 'terra', grass: 'erba', sand: 'sabbia', wood: 'legno', metal: 'metallo', pebblestone: 'ciottoli', mud: 'fango', 'concrete:plates': 'lastre di cemento', 'concrete:lanes': 'corsie di cemento', grass_paver: 'grigliato erboso', unhewn_cobblestone: 'pietra grezza', rock: 'roccia', tartan: 'materiale sintetico', artificial_turf: 'erba sintetica', clay: 'argilla' },
};

export const valueLabel = (attr, v) => VALUE_LABELS[attr]?.[v] || v;

// Tavolozza per orientamento: 18 settori da 10°, ciclica (0° e 180° coincidono).
export const ORIENTATION_COLORS = [
  '#e6194b', '#f05a28', '#f58231', '#f9a825', '#ffe119', '#bfef45', '#3cb44b', '#16a085', '#42d4f4',
  '#2b9cd8', '#4363d8', '#6a3fc7', '#911eb4', '#b03fa8', '#f032e6', '#e8459a', '#d6336c', '#c2185b',
];

export const ELEVATION_RAMP = ['#2c7bb6', '#00a6ca', '#00ccbc', '#90eb9d', '#ffff8c', '#f9d057', '#f29e2e', '#e76818', '#d7191c'];
export const DENSITY_RAMP = ['#000004', '#3b0f70', '#8c2981', '#de4968', '#fe9f6d', '#fcfdbf'];

// Colori distinti per i percorsi multipli, con tratteggi diversi per chi non distingue i colori.
export const ROUTE_COLORS = ['#ff3d71', '#00b8d9', '#ffab00', '#36b37e', '#6554c0', '#ff7a00', '#00875a', '#de350b'];
export const ROUTE_DASHES = [null, [2, 1.2], [0.6, 1.4], [4, 1.5, 0.8, 1.5]];

const CLASS_LIGHT = {
  motorway: '#c0392b', primary: '#d35400', secondary: '#b9770e', tertiary: '#7d6608',
  residential: '#34495e', service: '#7f8c8d', track: '#8d6e63', pedestrian: '#16a085',
  path: '#558b2f', cycleway: '#1565c0', other: '#95a5a6',
};
const CLASS_DARK = {
  motorway: '#ff6b6b', primary: '#ffa94d', secondary: '#ffd43b', tertiary: '#e9ecef',
  residential: '#ced4da', service: '#868e96', track: '#a1887f', pedestrian: '#63e6be',
  path: '#a9e34b', cycleway: '#74c0fc', other: '#6c757d',
};
const CLASS_NEON = {
  motorway: '#ff2e88', primary: '#ff6ec7', secondary: '#b967ff', tertiary: '#05ffa1',
  residential: '#01cdfe', service: '#3d7bff', track: '#ffb86c', pedestrian: '#fffb96',
  path: '#7dff6b', cycleway: '#00fff0', other: '#5c6bc0',
};

const mono = (c) => Object.fromEntries(Object.keys(CLASS_LIGHT).map((k) => [k, c]));

const BASE_LAYER_COLORS_LIGHT = {
  rail: '#5d4037', transit: '#7b1fa2', cycleroutes: '#1e88e5', buildings: '#cfc6b8',
  green: '#b7dfa5', water: '#9cc9ee', boundaries: '#6d4c9f', poi: '#c2185b',
};
const BASE_LAYER_COLORS_DARK = {
  rail: '#bcaaa4', transit: '#ce93d8', cycleroutes: '#64b5f6', buildings: '#2d3440',
  green: '#1f4d2e', water: '#173b5c', boundaries: '#b39ddb', poi: '#f48fb1',
};

// Ogni stile pronto imposta colori e sfondo; larghezze e livelli restano dell'utente.
export const PRESETS = {
  classico: {
    label: 'Classico', bg: '#f7f5f0', colorMode: 'class', roadColor: '#2b2b2b', classColors: CLASS_LIGHT,
    layerColors: BASE_LAYER_COLORS_LIGHT, routeColor: '#ff3d71', startColor: '#1b8a3a', endColor: '#c62828',
    glow: false, backdrop: 'solid', basemap: 'osm-chiara', opacity: 0.95, brightness: 1,
  },
  notte: {
    label: 'Notte', bg: '#0b1021', colorMode: 'class', roadColor: '#e0e6f5', classColors: CLASS_DARK,
    layerColors: BASE_LAYER_COLORS_DARK, routeColor: '#ffd43b', startColor: '#51cf66', endColor: '#ff6b6b',
    glow: true, backdrop: 'solid', basemap: 'osm-scura', opacity: 0.9, brightness: 1,
  },
  essenziale: {
    label: 'Essenziale', bg: '#ffffff', colorMode: 'uniform', roadColor: '#1d1d1f', classColors: mono('#1d1d1f'),
    layerColors: { ...BASE_LAYER_COLORS_LIGHT, buildings: '#ececec', green: '#e3efe0', water: '#dceaf5' },
    routeColor: '#e03131', startColor: '#1d1d1f', endColor: '#e03131',
    glow: false, backdrop: 'solid', basemap: 'osm-chiara', opacity: 0.9, brightness: 1,
  },
  neon: {
    label: 'Neon', bg: '#0d0221', colorMode: 'class', roadColor: '#01cdfe', classColors: CLASS_NEON,
    layerColors: { ...BASE_LAYER_COLORS_DARK, buildings: '#1a0b3b', green: '#0b2f2a', water: '#0a1f4d' },
    routeColor: '#fffb96', startColor: '#05ffa1', endColor: '#ff2e88',
    glow: true, backdrop: 'solid', basemap: 'osm-scura', opacity: 1, brightness: 1.1,
  },
  carta: {
    label: 'Carta', bg: '#efe6d2', colorMode: 'uniform', roadColor: '#4a3b2a', classColors: mono('#4a3b2a'),
    layerColors: { ...BASE_LAYER_COLORS_LIGHT, buildings: '#d8c9ad', green: '#cfd8b0', water: '#aac4c9' },
    routeColor: '#b23a2f', startColor: '#2f5d3a', endColor: '#b23a2f',
    glow: false, backdrop: 'solid', basemap: 'osm-chiara', opacity: 0.85, brightness: 1,
  },
  monocromatico: {
    label: 'Monocromatico', bg: '#161616', colorMode: 'uniform', roadColor: '#f2f2f2', classColors: mono('#f2f2f2'),
    layerColors: { ...BASE_LAYER_COLORS_DARK, buildings: '#262626', green: '#222', water: '#2e2e2e' },
    routeColor: '#ffffff', startColor: '#ffffff', endColor: '#ffffff',
    glow: false, backdrop: 'solid', basemap: 'osm-scura', opacity: 0.85, brightness: 1,
  },
  manifesto: {
    label: 'Manifesto', bg: '#f4efe6', colorMode: 'class',
    roadColor: '#1b263b',
    classColors: { motorway: '#9b2226', primary: '#ae2012', secondary: '#bb3e03', tertiary: '#ca6702', residential: '#1b263b', service: '#415a77', track: '#6b705c', pedestrian: '#0a9396', path: '#386641', cycleway: '#005f73', other: '#778da9' },
    layerColors: { ...BASE_LAYER_COLORS_LIGHT, buildings: '#e2d9c8', green: '#d5e2c5', water: '#a8c5d6' },
    routeColor: '#e63946', startColor: '#2a9d8f', endColor: '#e63946',
    glow: false, backdrop: 'solid', basemap: 'osm-chiara', opacity: 1, brightness: 1,
  },
};

export function defaultStyle(presetKey = 'classico') {
  const p = PRESETS[presetKey];
  return {
    preset: presetKey,
    bg: p.bg,
    colorMode: p.colorMode,
    roadColor: p.roadColor,
    classColors: { ...p.classColors },
    layerColors: { ...p.layerColors },
    routeColor: p.routeColor,
    startColor: p.startColor,
    endColor: p.endColor,
    glow: p.glow,
    backdrop: p.backdrop,
    basemap: p.basemap,
    opacity: p.opacity,
    brightness: p.brightness,
    width: 1,
    labels: false,
    dimOthers: true,
    arrows: true,
    animate: false,
  };
}

export function applyPreset(style, key) {
  const p = PRESETS[key];
  if (!p) return style;
  return {
    ...style,
    preset: key,
    bg: p.bg,
    colorMode: style.colorMode === 'orientation' || style.colorMode === 'elevation' || style.colorMode === 'density' ? style.colorMode : p.colorMode,
    roadColor: p.roadColor,
    classColors: { ...p.classColors },
    layerColors: { ...p.layerColors },
    routeColor: p.routeColor,
    startColor: p.startColor,
    endColor: p.endColor,
    glow: p.glow,
    basemap: p.basemap,
    opacity: p.opacity,
    brightness: p.brightness,
  };
}

export function defaultLayers() {
  const out = {};
  for (const l of LAYERS) out[l.key] = { on: l.key === 'roads', opacity: 1, width: 1 };
  return out;
}

export function defaultFilters() {
  return {
    classes: Object.fromEntries(ROAD_CLASSES.map((c) => [c.key, true])),
    oneway: 'all', // all | yes | no
    lit: 'all',
    surface: [], // vuoto = tutte
    access: [],
    maxspeedMin: null,
    maxspeedMax: null,
    includeMissing: true,
  };
}
