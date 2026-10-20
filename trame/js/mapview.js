// Vista cartografica con MapLibre GL (WebGL). Tutti i livelli sono sorgenti GeoJSON locali.
/* global maplibregl */
import { CONFIG } from './config.js';
import { ROAD_CLASSES } from './styles.js';

const EMPTY = { type: 'FeatureCollection', features: [] };
const DASHED = ROAD_CLASSES.filter((c) => c.dash);
export const ROUTE_DASH_LAYERS = 4;

export function webglAvailable() {
  try {
    const c = document.createElement('canvas');
    return !!(c.getContext('webgl2') || c.getContext('webgl'));
  } catch {
    return false;
  }
}

function classWidthMatch(scale) {
  const m = ['match', ['get', 'c']];
  for (const c of ROAD_CLASSES) m.push(c.key, c.width * scale);
  m.push(1 * scale);
  return m;
}

// Larghezza che cresce con l'ingrandimento, come su una carta stampata.
function zoomWidth(base) {
  return ['interpolate', ['exponential', 1.5], ['zoom'],
    8, ['*', base, 0.25], 12, ['*', base, 0.6], 15, ['*', base, 1.4], 18, ['*', base, 4]];
}

export class MapView {
  constructor(container, { onReady, onError, attribution } = {}) {
    this.container = container;
    this.listeners = {};
    this.markers = [];
    this.animFrame = null;
    this.ready = false;
    const style = {
      version: 8,
      glyphs: CONFIG.glyphs,
      sources: {
        basemap: { type: 'raster', tiles: CONFIG.basemaps['carto-light'].tiles, tileSize: 256, maxzoom: 19 },
      },
      layers: [{ id: 'bg', type: 'background', paint: { 'background-color': '#f7f5f0' } }],
    };
    this.map = new maplibregl.Map({
      container,
      style,
      center: [12.62, 46.01],
      zoom: 12,
      attributionControl: false,
      preserveDrawingBuffer: true, // serve per l'istantanea PNG della vista
      dragRotate: true,
      pitchWithRotate: false,
      maxPitch: 0,
      fadeDuration: 0,
    });
    this.map.touchZoomRotate.enableRotation();
    this.attrib = new maplibregl.AttributionControl({ compact: true, customAttribution: attribution || '' });
    this.map.addControl(this.attrib, 'bottom-right');
    this.map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left');
    this.map.on('load', () => {
      this._setupLayers();
      this.ready = true;
      onReady?.();
    });
    this.map.on('error', (e) => onError?.(e.error || e));
  }

  on(ev, fn) { this.map.on(ev, fn); }

  _setupLayers() {
    const m = this.map;
    m.addLayer({ id: 'basemap', type: 'raster', source: 'basemap', layout: { visibility: 'none' } });
    const src = (id, extra = {}) => m.addSource(id, { type: 'geojson', data: EMPTY, tolerance: 0.45, buffer: 64, ...extra });
    ['green', 'water', 'buildings', 'boundaries', 'rail', 'transit', 'cycleroutes', 'poi', 'roads', 'creative', 'area', 'routes', 'draw', 'selection', 'steps'].forEach((id) => src(id));

    m.addLayer({ id: 'green-fill', type: 'fill', source: 'green', filter: ['==', ['get', 'shape'], 'area'], paint: { 'fill-color': '#b7dfa5' } });
    m.addLayer({ id: 'water-fill', type: 'fill', source: 'water', filter: ['==', ['get', 'shape'], 'area'], paint: { 'fill-color': '#9cc9ee' } });
    m.addLayer({ id: 'water-line', type: 'line', source: 'water', filter: ['==', ['get', 'shape'], 'line'], paint: { 'line-color': '#9cc9ee', 'line-width': zoomWidth(1.4) } });
    m.addLayer({ id: 'buildings-fill', type: 'fill', source: 'buildings', paint: { 'fill-color': '#cfc6b8' } });
    m.addLayer({ id: 'boundaries-line', type: 'line', source: 'boundaries', paint: { 'line-color': '#6d4c9f', 'line-width': 1.6, 'line-dasharray': [4, 2] } });

    // Rete stradale: un livello pieno, uno per ogni tratteggio, uno per il bagliore.
    m.addLayer({ id: 'roads-glow', type: 'line', source: 'roads', layout: { 'line-cap': 'round', 'line-join': 'round', visibility: 'none' }, paint: { 'line-color': ['get', 'col'], 'line-blur': 4, 'line-opacity': 0.35 } });
    m.addLayer({ id: 'roads-solid', type: 'line', source: 'roads', filter: ['!', ['in', ['get', 'c'], ['literal', DASHED.map((c) => c.key)]]], layout: { 'line-cap': 'round', 'line-join': 'round', 'line-sort-key': ['get', 'r'] }, paint: { 'line-color': ['get', 'col'] } });
    for (const c of DASHED) {
      m.addLayer({ id: `roads-${c.key}`, type: 'line', source: 'roads', filter: ['==', ['get', 'c'], c.key], layout: { 'line-cap': 'butt', 'line-join': 'round' }, paint: { 'line-color': ['get', 'col'], 'line-dasharray': c.dash } });
    }
    m.addLayer({ id: 'rail-line', type: 'line', source: 'rail', paint: { 'line-color': '#5d4037', 'line-width': zoomWidth(1.6), 'line-dasharray': [3, 1.5] } });
    m.addLayer({ id: 'transit-line', type: 'line', source: 'transit', paint: { 'line-color': '#7b1fa2', 'line-width': zoomWidth(1.3), 'line-dasharray': [1, 1], 'line-opacity': 0.8 } });
    m.addLayer({ id: 'cycleroutes-line', type: 'line', source: 'cycleroutes', paint: { 'line-color': '#1e88e5', 'line-width': zoomWidth(1.8), 'line-dasharray': [2, 1], 'line-opacity': 0.85 } });
    m.addLayer({ id: 'creative-line', type: 'line', source: 'creative', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#ff3d71', 'line-width': zoomWidth(1.1), 'line-opacity': 0.5 } });
    m.addLayer({ id: 'area-line', type: 'line', source: 'area', paint: { 'line-color': '#6d4c9f', 'line-width': 2, 'line-dasharray': [2, 2], 'line-opacity': 0.8 } });
    m.addLayer({ id: 'selection-line', type: 'line', source: 'selection', layout: { 'line-cap': 'round' }, paint: { 'line-color': '#ffd400', 'line-width': 9, 'line-opacity': 0.55 } });

    // Percorsi: alone, bordo bianco, linea colorata (con tratteggi diversi), frecce.
    m.addLayer({ id: 'routes-halo', type: 'line', source: 'routes', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': ['get', 'col'], 'line-width': ['interpolate', ['linear'], ['zoom'], 10, 12, 16, 22], 'line-blur': 8, 'line-opacity': ['*', ['get', 'op'], 0.45] } });
    m.addLayer({ id: 'routes-casing', type: 'line', source: 'routes', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#ffffff', 'line-width': ['interpolate', ['linear'], ['zoom'], 10, 6, 16, 11], 'line-opacity': ['get', 'op'] } });
    const dashes = [null, [2, 1.2], [0.6, 1.4], [4, 1.5, 0.8, 1.5]];
    for (let i = 0; i < ROUTE_DASH_LAYERS; i++) {
      const paint = { 'line-color': ['get', 'col'], 'line-width': ['interpolate', ['linear'], ['zoom'], 10, 3.6, 16, 7], 'line-opacity': ['get', 'op'] };
      if (dashes[i]) paint['line-dasharray'] = dashes[i];
      m.addLayer({ id: `routes-line-${i}`, type: 'line', source: 'routes', filter: ['==', ['get', 'di'], i], layout: { 'line-cap': dashes[i] ? 'butt' : 'round', 'line-join': 'round' }, paint });
    }
    m.addLayer({ id: 'routes-anim', type: 'line', source: 'routes', filter: ['==', ['get', 'main'], 1], layout: { visibility: 'none', 'line-cap': 'butt' }, paint: { 'line-color': '#ffffff', 'line-width': ['interpolate', ['linear'], ['zoom'], 10, 1.6, 16, 3], 'line-opacity': 0.9, 'line-dasharray': [0, 4, 3] } });
    this._addArrowImage();
    m.addLayer({ id: 'routes-arrows', type: 'symbol', source: 'routes', layout: { 'symbol-placement': 'line', 'symbol-spacing': 90, 'icon-image': 'freccia', 'icon-size': ['interpolate', ['linear'], ['zoom'], 10, 0.55, 16, 0.9], 'icon-allow-overlap': true, 'icon-ignore-placement': true, 'icon-rotation-alignment': 'map' } });
    m.addLayer({ id: 'steps-point', type: 'circle', source: 'steps', paint: { 'circle-radius': 7, 'circle-color': '#ffffff', 'circle-stroke-color': '#111', 'circle-stroke-width': 3 } });

    m.addLayer({ id: 'draw-fill', type: 'fill', source: 'draw', filter: ['==', ['geometry-type'], 'Polygon'], paint: { 'fill-color': '#ffab00', 'fill-opacity': 0.15 } });
    m.addLayer({ id: 'draw-line', type: 'line', source: 'draw', filter: ['!=', ['geometry-type'], 'Point'], paint: { 'line-color': '#ff6f00', 'line-width': 2.5, 'line-dasharray': [2, 1] } });
    m.addLayer({ id: 'draw-points', type: 'circle', source: 'draw', filter: ['==', ['geometry-type'], 'Point'], paint: { 'circle-radius': 5, 'circle-color': '#ff6f00', 'circle-stroke-color': '#fff', 'circle-stroke-width': 2 } });

    m.addLayer({ id: 'poi-circle', type: 'circle', source: 'poi', paint: { 'circle-radius': ['interpolate', ['linear'], ['zoom'], 11, 2, 16, 5], 'circle-color': '#c2185b', 'circle-stroke-color': '#fff', 'circle-stroke-width': 1 } });
    m.addLayer({ id: 'poi-label', type: 'symbol', source: 'poi', minzoom: 14, layout: { 'text-field': ['get', 'name'], 'text-font': CONFIG.font, 'text-size': 11, 'text-offset': [0, 0.9], 'text-anchor': 'top', 'text-optional': true, visibility: 'none' }, paint: { 'text-color': '#222', 'text-halo-color': '#fff', 'text-halo-width': 1.4 } });
    m.addLayer({ id: 'roads-label', type: 'symbol', source: 'roads', minzoom: 13, filter: ['!=', ['get', 'n'], ''], layout: { 'symbol-placement': 'line', 'text-field': ['get', 'n'], 'text-font': CONFIG.font, 'text-size': ['interpolate', ['linear'], ['zoom'], 13, 10, 18, 14], 'text-max-angle': 30, 'symbol-spacing': 300, visibility: 'none' }, paint: { 'text-color': '#222', 'text-halo-color': '#fff', 'text-halo-width': 1.5 } });
  }

  _addArrowImage() {
    const s = 32;
    const c = document.createElement('canvas');
    c.width = c.height = s;
    const ctx = c.getContext('2d');
    ctx.fillStyle = '#ffffff';
    ctx.strokeStyle = 'rgba(0,0,0,0.75)';
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.moveTo(8, 7); ctx.lineTo(25, 16); ctx.lineTo(8, 25); ctx.lineTo(12, 16); ctx.closePath();
    ctx.stroke(); ctx.fill();
    this.map.addImage('freccia', ctx.getImageData(0, 0, s, s), { pixelRatio: 1 });
  }

  setSource(id, data) {
    const s = this.map.getSource(id);
    if (s) s.setData(data || EMPTY);
  }

  setVisible(layerId, on) {
    if (this.map.getLayer(layerId)) this.map.setLayoutProperty(layerId, 'visibility', on ? 'visible' : 'none');
  }

  paint(layerId, prop, value) {
    if (this.map.getLayer(layerId)) this.map.setPaintProperty(layerId, prop, value);
  }

  setBasemap(key) {
    const bm = CONFIG.basemaps[key];
    if (!bm) { this.setVisible('basemap', false); return; }
    const src = this.map.getSource('basemap');
    if (this._basemapKey !== key) {
      src.setTiles(bm.tiles);
      this._basemapKey = key;
    }
    this.setVisible('basemap', true);
  }

  // Applica stile e visibilità dei livelli. I colori delle strade arrivano già calcolati nei dati.
  applyStyle(style, layers, { dimRoads, labelsOn, routeArrows, darkUi }) {
    const lc = style.layerColors;
    this.paint('bg', 'background-color', style.bg);
    const backdrop = style.backdrop;
    if (backdrop === 'solid') this.setVisible('basemap', false);
    else {
      this.setBasemap(style.basemap);
      this.paint('basemap', 'raster-opacity', backdrop === 'dimmed' ? 0.35 : 1);
      this.paint('basemap', 'raster-saturation', backdrop === 'dimmed' ? -0.8 : 0);
    }
    const L = (k) => layers[k];
    const vis = (k, ids) => ids.forEach((id) => this.setVisible(id, !!L(k)?.on));
    vis('green', ['green-fill']);
    vis('water', ['water-fill', 'water-line']);
    vis('buildings', ['buildings-fill']);
    vis('boundaries', ['boundaries-line']);
    vis('rail', ['rail-line']);
    vis('transit', ['transit-line']);
    vis('cycleroutes', ['cycleroutes-line']);
    vis('poi', ['poi-circle']);
    this.setVisible('poi-label', !!L('poi')?.on && labelsOn);
    this.paint('green-fill', 'fill-color', lc.green); this.paint('green-fill', 'fill-opacity', 0.85 * L('green').opacity);
    this.paint('water-fill', 'fill-color', lc.water); this.paint('water-fill', 'fill-opacity', 0.9 * L('water').opacity);
    this.paint('water-line', 'line-color', lc.water); this.paint('water-line', 'line-opacity', L('water').opacity);
    this.paint('water-line', 'line-width', zoomWidth(1.4 * L('water').width));
    this.paint('buildings-fill', 'fill-color', lc.buildings); this.paint('buildings-fill', 'fill-opacity', 0.9 * L('buildings').opacity);
    this.paint('boundaries-line', 'line-color', lc.boundaries); this.paint('boundaries-line', 'line-opacity', L('boundaries').opacity);
    this.paint('boundaries-line', 'line-width', 1.6 * L('boundaries').width);
    for (const k of ['rail', 'transit', 'cycleroutes']) {
      this.paint(`${k}-line`, 'line-color', lc[k]);
      this.paint(`${k}-line`, 'line-opacity', L(k).opacity * (k === 'rail' ? 1 : 0.85));
      this.paint(`${k}-line`, 'line-width', zoomWidth({ rail: 1.6, transit: 1.3, cycleroutes: 1.8 }[k] * L(k).width));
    }
    this.paint('poi-circle', 'circle-color', lc.poi);
    this.paint('poi-circle', 'circle-opacity', L('poi').opacity);

    const rw = zoomWidth(classWidthMatch(style.width * L('roads').width));
    const op = style.opacity * L('roads').opacity * (dimRoads ? 0.28 : 1);
    const roadIds = ['roads-solid', ...DASHED.map((c) => `roads-${c.key}`)];
    for (const id of roadIds) {
      this.setVisible(id, !!L('roads').on);
      this.paint(id, 'line-width', rw);
      this.paint(id, 'line-opacity', op);
    }
    this.setVisible('roads-glow', !!L('roads').on && style.glow);
    this.paint('roads-glow', 'line-width', zoomWidth(classWidthMatch(style.width * L('roads').width * 3.2)));
    this.paint('roads-glow', 'line-opacity', 0.3 * op);
    this.setVisible('roads-label', !!L('roads').on && labelsOn);
    const textColor = darkUi ? '#f1f3f5' : '#1d1d1f';
    const halo = darkUi ? 'rgba(0,0,0,0.8)' : 'rgba(255,255,255,0.9)';
    for (const id of ['roads-label', 'poi-label']) { this.paint(id, 'text-color', textColor); this.paint(id, 'text-halo-color', halo); }
    this.setVisible('routes-arrows', routeArrows);
  }

  setRouteAnimation(on) {
    cancelAnimationFrame(this.animFrame);
    this.setVisible('routes-anim', on);
    if (!on) return;
    // Sequenza di tratteggi che dà l'impressione di movimento lungo il percorso.
    const seq = [[0, 4, 3], [0.5, 4, 2.5], [1, 4, 2], [1.5, 4, 1.5], [2, 4, 1], [2.5, 4, 0.5], [3, 4, 0], [0, 0.5, 3, 3.5], [0, 1, 3, 3], [0, 1.5, 3, 2.5], [0, 2, 3, 2], [0, 2.5, 3, 1.5], [0, 3, 3, 1], [0, 3.5, 3, 0.5]];
    let step = -1, last = 0;
    const tick = (t) => {
      if (t - last > 60) {
        last = t;
        step = (step + 1) % seq.length;
        this.paint('routes-anim', 'line-dasharray', seq[step]);
      }
      this.animFrame = requestAnimationFrame(tick);
    };
    this.animFrame = requestAnimationFrame(tick);
  }

  setMarkers(list) {
    for (const mk of this.markers) mk.remove();
    this.markers = list.map((d) => {
      const el = document.createElement('button');
      el.type = 'button';
      el.className = `marcatore marcatore-${d.kind}`;
      el.style.setProperty('--c', d.color);
      el.textContent = d.label;
      el.setAttribute('aria-label', d.aria);
      el.title = d.aria + (d.draggable ? ' (trascina per spostare)' : '');
      const mk = new maplibregl.Marker({ element: el, draggable: !!d.draggable, anchor: 'center' }).setLngLat(d.coord).addTo(this.map);
      if (d.onDrag) mk.on('dragend', () => { const ll = mk.getLngLat(); d.onDrag([ll.lng, ll.lat]); });
      el.addEventListener('click', (ev) => { ev.stopPropagation(); d.onClick?.(); });
      return mk;
    });
  }

  setAttribution(html) {
    this.attrib.options.customAttribution = html;
    this.attrib._updateAttributions?.();
  }

  fit(bbox, opts = {}) {
    if (!bbox) return;
    const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.map.fitBounds([[bbox[0], bbox[1]], [bbox[2], bbox[3]]], { padding: opts.padding ?? 40, duration: reduce ? 0 : 600, maxZoom: 17, bearing: this.map.getBearing() });
  }

  bounds() {
    const b = this.map.getBounds();
    return [b.getWest(), b.getSouth(), b.getEast(), b.getNorth()];
  }

  snapshot() {
    return this.map.getCanvas().toDataURL('image/png');
  }

  resize() { this.map.resize(); }
}
