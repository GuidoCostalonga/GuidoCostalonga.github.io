// Trame: la rete stradale come disegno. Collega dati, vista, interfaccia e servizi.
/* global maplibregl */
import { CONFIG, ATTRIBUTION_OSM } from './config.js';
import {
  store, fmt, esc, debounce, downloadBlob, slug, encodeState, decodeState, isAbort, uid,
  bboxAreaKm2, geometryAreaKm2, bboxOfCoords, bboxOfFeatures, lineLength, simplify, mixColor, haversine, relLuminance, hexToRgb,
} from './util.js';
import {
  ROAD_CLASSES, CLASS_BY_KEY, LAYERS, LAYER_BY_KEY, ATTRIBUTES, valueLabel, PRESETS, ROUTE_COLORS,
  defaultStyle, applyPreset, defaultLayers, defaultFilters,
} from './styles.js';
import { createGeocoder, geocodeErrorMessage } from './providers/geocoding.js';
import { buildQuery, runOverpass, parseRoads, parseLayer, overpassErrorMessage } from './providers/overpass.js';
import { createRouter, routingErrorMessage, MODES, OPTION_LABELS } from './providers/routing.js';
import { createElevation, profileStats } from './providers/elevation.js';
import { parseGpx, parseGeoJsonTracks, routesToGpx, routesToGeoJson } from './gpx.js';
import { buildScene, roadColor, legendEntries } from './scene.js';
import { networkStats, orientationHistogram, densityByRoad, classBarChart, roseChart, elevationProfileChart, generatePaths, sampleMidpoints } from './analysis.js';
import { renderCanvas, renderSvg, SIZE_PRESETS } from './exporter.js';
import { MapView, webglAvailable } from './mapview.js';
import { CanvasView } from './canvasview.js';

const $ = (s, r = document) => r.querySelector(s);

// Colore dei percorsi generati: il più lontano da quello del percorso principale.
function creativeColor(routeColor) {
  const [r0, g0, b0] = hexToRgb(routeColor);
  let best = ROUTE_COLORS[1], bestD = -1;
  for (const c of ROUTE_COLORS) {
    const [r, g, b] = hexToRgb(c);
    const d = (r - r0) ** 2 + (g - g0) ** 2 + (b - b0) ** 2;
    if (d > bestD) { bestD = d; best = c; }
  }
  return best;
}
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');

/* =================== stato =================== */

const saved = store.get('impostazioni', {});
const S = {
  area: null,
  roads: [],
  visibleRoads: [],
  layerData: {},
  loadedFor: {}, // livello -> chiave dell'area caricata
  style: { ...defaultStyle(), ...(saved.style || {}) },
  layers: { ...defaultLayers(), ...(saved.layers || {}) },
  filters: { ...defaultFilters(), ...(saved.filters || {}) },
  ui: { legend: true, panel: true, theme: 'auto', ...(saved.ui || {}) },
  routeSettings: { provider: 'valhalla', customUrl: '', mode: 'car', options: {}, alternatives: true, ...(saved.routeSettings || {}), ghKey: store.get('chiave-gh', '') },
  mode: 'arte',
  waypoints: [{ id: uid(), coord: null, label: '' }, { id: uid(), coord: null, label: '' }],
  routes: [],
  pick: null, // { type: 'waypoint', id } | { type: 'origin' }
  draw: null, // { type: 'bbox' | 'polygon' | 'manual', pts: [] }
  creative: { paths: [], color: '#ff3d71' },
  genOrigin: null,
  elev: null,
  elevRange: null,
  density: null,
  position: null,
  selection: null,
};
// Impostazioni salvate con le vecchie mappe CARTO, che ora richiedono una chiave.
if (!CONFIG.basemaps[S.style.basemap]) S.style.basemap = /dark/.test(S.style.basemap) ? 'osm-scura' : 'osm-chiara';
S.style.animate = S.style.animate && !reducedMotion.matches;
S.creative.color = creativeColor(S.style.routeColor);

const geocoder = createGeocoder('nominatim');
const elevation = createElevation();
let view = null; // MapView oppure CanvasView
let isGL = false;

/* =================== avvisi e dialoghi =================== */

function toast(msg, kind = 'info', ms = 6000) {
  const box = document.createElement('div');
  box.className = `avviso-box ${kind}`;
  box.setAttribute('role', kind === 'errore' ? 'alert' : 'status');
  box.innerHTML = `<p>${msg}</p><button type="button" aria-label="Chiudi l'avviso"><svg class="ic"><use href="#i-chiudi"/></svg></button>`;
  box.querySelector('button').onclick = () => box.remove();
  $('#avvisi').appendChild(box);
  if (ms) setTimeout(() => box.remove(), ms);
  return box;
}

function confirmDialog(title, html, okLabel = 'Procedi') {
  const dlg = $('#dialogo');
  $('#dialogo-titolo').textContent = title;
  $('#dialogo-testo').innerHTML = html;
  $('#dialogo-si').textContent = okLabel;
  return new Promise((resolve) => {
    dlg.returnValue = '';
    dlg.addEventListener('close', () => resolve(dlg.returnValue === 'ok'), { once: true });
    dlg.showModal();
  });
}

/* =================== tema e pannelli =================== */

function applyTheme() {
  document.documentElement.dataset.theme = S.ui.theme;
  const dark = isDarkUi();
  $('#t-tema').setAttribute('aria-label', `Tema dell'interfaccia: ${S.ui.theme === 'auto' ? 'come il sistema' : S.ui.theme === 'dark' ? 'scuro' : 'chiaro'}`);
  $('meta[name="theme-color"]').content = dark ? '#161b22' : '#ffffff';
}
const isDarkUi = () => S.ui.theme === 'dark' || (S.ui.theme === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);

function applyPanels() {
  const panel = $('#pannello');
  panel.classList.toggle('chiuso', !S.ui.panel);
  $('#t-pannello').setAttribute('aria-pressed', String(S.ui.panel));
  document.body.classList.toggle('pannello-aperto', S.ui.panel && !panel.classList.contains('ridotto'));
  document.body.classList.toggle('pannello-chiuso', !S.ui.panel || panel.classList.contains('ridotto'));
  $('#legenda').hidden = !S.ui.legend;
  $('#t-legenda').setAttribute('aria-pressed', String(S.ui.legend));
  $('#t-etichette').setAttribute('aria-pressed', String(S.style.labels));
  $('#t-base').setAttribute('aria-pressed', String(S.style.backdrop !== 'solid'));
  setTimeout(() => view?.resize?.(), 30);
}

function setupTabs() {
  const tabs = $$('[role="tab"]');
  const select = (tab, focus) => {
    for (const t of tabs) {
      const on = t === tab;
      t.setAttribute('aria-selected', String(on));
      t.tabIndex = on ? 0 : -1;
      $('#' + t.getAttribute('aria-controls')).hidden = !on;
    }
    if (focus) tab.focus();
    if (tab.id === 'tab-esporta') updateExportUi();
    if (tab.id === 'tab-analisi') updateAnalysis();
    if (window.innerWidth <= 860) { $('#pannello').classList.remove('ridotto'); applyPanels(); }
  };
  tabs.forEach((t, i) => {
    t.addEventListener('click', () => select(t));
    t.addEventListener('keydown', (e) => {
      let j = null;
      if (e.key === 'ArrowRight') j = (i + 1) % tabs.length;
      if (e.key === 'ArrowLeft') j = (i - 1 + tabs.length) % tabs.length;
      if (e.key === 'Home') j = 0;
      if (e.key === 'End') j = tabs.length - 1;
      if (j != null) { e.preventDefault(); select(tabs[j], true); }
    });
  });
  return (id) => select($('#' + id));
}
let selectTab = () => {};

function setMode(mode) {
  S.mode = mode;
  for (const b of $$('[data-modalita]')) b.setAttribute('aria-checked', String(b.dataset.modalita === mode));
  if (mode === 'percorsi') {
    selectTab('tab-percorso');
    if (!S.ui.panel) { S.ui.panel = true; applyPanels(); }
    toast('Modalità Percorsi: clic sulla mappa per fissare la partenza, poi l\'arrivo. Puoi anche cercarli per nome.', 'info', 5000);
  } else {
    S.pick = null;
    updatePickUi();
  }
  setMapCursor();
}

/* =================== vista cartografica =================== */

function baseAttribution() {
  const parts = [ATTRIBUTION_OSM];
  if (S.style.backdrop !== 'solid') {
    const bm = CONFIG.basemaps[S.style.basemap];
    if (bm) parts.push('Mappa di base: tessere © OpenStreetMap');
  }
  if (S.routes.some((r) => r.kind === 'computed' || r.kind === 'alt')) parts.push(esc(S.routes.find((r) => r.source)?.source || ''));
  return parts.filter(Boolean).join(' · ');
}

function initView() {
  const container = $('#mappa');
  const useGL = typeof maplibregl !== 'undefined' && webglAvailable();
  if (useGL) {
    try {
      view = new MapView(container, {
        attribution: baseAttribution(),
        onReady: () => { renderAll(true); afterViewReady(); },
        onError: (e) => {
          const msg = String(e?.message || e);
          if (/tile|basemap|Failed to fetch|404|openstreetmap/i.test(msg)) {
            if (!S._basemapWarned) { S._basemapWarned = true; toast('La mappa di base non risponde: la rete stradale resta visibile su sfondo pieno.', 'attenzione'); }
          } else if (/glyph|font/i.test(msg)) {
            if (!S._glyphWarned) { S._glyphWarned = true; toast('I caratteri per le etichette non sono disponibili: i nomi delle strade non possono essere mostrati.', 'attenzione'); }
          } else console.warn(e);
        },
      });
      isGL = true;
      view.kind = 'gl';
      view.on('click', (e) => onMapClick([e.lngLat.lng, e.lngLat.lat], e.point));
      view.on('dblclick', (e) => { if (S.draw) { e.preventDefault(); finishDraw(); } });
      view.on('moveend', onMoveEnd);
      view.on('rotate', () => { $('.bussola').style.transform = `rotate(${-view.map.getBearing()}deg)`; });
      view.on('zoom', updateStatus);
      view.map.getCanvas().addEventListener('webglcontextlost', () => toast('Il contesto grafico WebGL si è interrotto: ricarica la pagina per ripristinare la mappa.', 'errore', 0));
      return;
    } catch (e) {
      console.warn('WebGL non utilizzabile', e);
      container.innerHTML = '';
    }
  }
  view = new CanvasView(container);
  isGL = false;
  view.on('click', (e) => onMapClick([e.lngLat.lng, e.lngLat.lat], e.point));
  view.on('moveend', onMoveEnd);
  view.on('move', updateStatus);
  view.resize = () => CanvasView.prototype.resize.call(view);
  toast(typeof maplibregl === 'undefined'
    ? 'La libreria cartografica non si è caricata (connessione o blocco dei contenuti). Uso una vista semplificata: tutte le funzioni restano disponibili, senza mappa di base.'
    : 'Questo dispositivo non supporta WebGL. Uso una vista semplificata: tutte le funzioni restano disponibili, senza mappa di base e senza rotazione.', 'attenzione', 12000);
  setTimeout(() => { renderAll(true); afterViewReady(); }, 0);
}

const zoomLevel = () => (isGL ? view.map.getZoom() : view.zoom);

function fitTo(bbox, padding) {
  if (!bbox) return;
  let pad = padding ?? (window.innerWidth <= 860 ? 30 : 50);
  // Su schermi stretti il pannello copre la parte bassa della mappa: si lascia spazio.
  const panel = $('#pannello');
  if (padding == null && isGL && window.innerWidth <= 860 && S.ui.panel) {
    const h = panel.offsetHeight;
    const maxH = $('#mappa').clientHeight;
    pad = { top: 60, left: 20, right: 60, bottom: Math.min(maxH * 0.65, h + 20) };
  }
  view.fit(bbox, { padding: pad });
}

function onMoveEnd() {
  updateStatus();
  if (S.mode === 'arte' && $('input[name="ambito"]:checked')?.value === 'vista' && !$('#sez-analisi').hidden) updateAnalysisDebounced();
}

function setMapCursor() {
  $('#mappa').classList.toggle('puntatore', !!(S.draw || S.pick || S.mode === 'percorsi'));
}

/* =================== disegno della mappa =================== */

let renderQueued = false;
let renderData = false;
function renderAll(withData = false) {
  renderData = renderData || withData;
  if (renderQueued) return;
  renderQueued = true;
  requestAnimationFrame(() => {
    renderQueued = false;
    const d = renderData;
    renderData = false;
    try { doRender(d); } catch (e) { console.error(e); toast('Errore nel disegno della mappa: ' + esc(e.message), 'errore'); }
  });
}

const colorExtra = () => ({ elev: S.elev, elevRange: S.elevRange, density: S.density });

function routeDimActive() {
  return S.style.dimOthers && S.routes.some((r) => r.visible && r.kind !== 'import');
}

function markersData() {
  const list = [];
  const n = S.waypoints.length;
  S.waypoints.forEach((w, i) => {
    if (!w.coord) return;
    const kind = i === 0 ? 'partenza' : i === n - 1 ? 'arrivo' : 'via';
    const label = i === 0 ? 'A' : i === n - 1 ? 'B' : String(i);
    const aria = i === 0 ? 'Partenza' : i === n - 1 ? 'Arrivo' : `Tappa ${i}`;
    list.push({
      kind, label, coord: w.coord, aria: `${aria}${w.label ? ': ' + w.label : ''}`,
      color: i === 0 ? S.style.startColor : i === n - 1 ? S.style.endColor : '#6554c0',
      draggable: true,
      onDrag: (c) => { w.coord = c; w.label = coordLabel(c); reverseLabel(w); renderWaypoints(); renderAll(); autoRoute(); },
      onClick: () => { selectTab('tab-percorso'); $(`#tp-${w.id}`)?.focus(); },
    });
  });
  if (S.genOrigin) list.push({ kind: 'origine', label: '', coord: S.genOrigin, aria: 'Origine dei percorsi generati', color: S.creative.color });
  if (S.position) list.push({ kind: 'posizione', label: '', coord: S.position, aria: 'La tua posizione', color: '#1a73e8' });
  return list;
}

function doRender(withData) {
  if (!view) return;
  const dim = routeDimActive();
  if (isGL) {
    if (!view.ready) return;
    if (withData) {
      const ex = colorExtra();
      view.setSource('roads', {
        type: 'FeatureCollection',
        features: S.visibleRoads.map((r, i) => ({
          type: 'Feature', id: i,
          geometry: r.geom || (r.geom = { type: 'LineString', coordinates: r.coords }),
          properties: { c: r.cls, col: roadColor(r, S.style, ex), n: r.tags.name || '', r: CLASS_BY_KEY[r.cls]?.rank || 0, i },
        })),
      });
      for (const l of LAYERS) if (l.key !== 'roads') view.setSource(l.key, { type: 'FeatureCollection', features: S.layerData[l.key] || [] });
      view.setSource('area', S.area?.ring ? { type: 'Feature', geometry: { type: 'LineString', coordinates: S.area.ring }, properties: {} } : null);
      view.setSource('creative', { type: 'MultiLineString', coordinates: S.creative.paths });
    }
    renderRoutesGL();
    // Il colore delle etichette segue lo sfondo della mappa, non il tema dell'interfaccia.
    const darkMap = S.style.backdrop === 'solid' ? relLuminance(S.style.bg) < 0.3 : S.style.basemap === 'osm-scura';
    view.applyStyle(S.style, S.layers, { dimRoads: dim, labelsOn: S.style.labels, routeArrows: S.style.arrows && S.routes.some((r) => r.visible), darkUi: darkMap });
    view.paint('creative-line', 'line-color', S.creative.color);
    view.setRouteAnimation(S.style.animate && S.routes.some((r) => r.visible && r.main));
    view.setMarkers(markersData());
    view.setAttribution(baseAttribution());
  } else {
    const groups = buildScene(sceneModel(), S.style, S.layers, { ...colorExtra(), dimRoads: dim, markers: markersData(), showArea: true });
    if (S.draw?.pts.length) groups.push({ kind: 'line', color: '#ff6f00', width: 2.5, opacity: 1, dash: [2, 1], items: [drawPreviewCoords()] });
    view.setScene(groups, S.style.bg);
  }
  renderLegend();
  updateStatus();
}

function sceneModel() {
  return { roads: S.visibleRoads, layerData: S.layerData, routes: S.routes, creative: S.creative, area: S.area };
}

function renderRoutesGL() {
  const feats = [];
  for (const r of S.routes) {
    if (!r.visible) continue;
    feats.push({ type: 'Feature', geometry: { type: 'LineString', coordinates: r.coords }, properties: { col: r.color, di: r.dashIndex || 0, op: r.kind === 'alt' ? 0.85 : 1, main: r.main ? 1 : 0 } });
  }
  // La linea principale va disegnata per ultima, sopra le alternative.
  feats.sort((a, b) => a.properties.main - b.properties.main);
  view.setSource('routes', { type: 'FeatureCollection', features: feats });
  if (S.draw) view.setSource('draw', drawGeoJson());
  else view.setSource('draw', null);
}

function renderLegend() {
  const box = $('#legenda');
  const entries = legendEntries(sceneModel(), S.style, S.layers, S.filters, colorExtra());
  const hasWp = S.waypoints.some((w) => w.coord);
  if (!entries.length && !hasWp) { box.innerHTML = ''; return; }
  const li = entries.map((e) => {
    let sw = '';
    if (e.kind === 'line') sw = `<svg width="34" height="12" aria-hidden="true"><line x1="2" y1="6" x2="32" y2="6" stroke="${e.color}" stroke-width="${Math.max(1.5, Math.min(6, (e.width || 1.5) * 1.3))}" ${e.dash ? `stroke-dasharray="${e.dash.map((d) => d * 2.2).join(' ')}"` : ''} stroke-linecap="butt"/></svg>`;
    else if (e.kind === 'fill') sw = `<span class="campione" style="background:${e.color}"></span>`;
    else if (e.kind === 'point') sw = `<svg width="34" height="12" aria-hidden="true"><circle cx="17" cy="6" r="4" fill="${e.color}"/></svg>`;
    else if (e.kind === 'ramp') return `<li style="flex-direction:column;align-items:stretch"><span>${esc(e.label)}</span><span class="scala" style="width:100%;background:linear-gradient(90deg,${e.colors.join(',')})"></span><span class="estremi"><span>${esc(e.min)}</span><span>${esc(e.max)}</span></span></li>`;
    return `<li>${sw}<span>${esc(e.label)}</span></li>`;
  });
  if (hasWp) {
    li.push(`<li><span class="marcatore" style="--c:${S.style.startColor};width:20px;height:20px;font-size:10px;border-width:2px">A</span><span>Partenza</span></li>`);
    li.push(`<li><span class="marcatore marcatore-arrivo" style="--c:${S.style.endColor};width:20px;height:20px;font-size:10px;border-width:2px">B</span><span>Arrivo</span></li>`);
  }
  box.innerHTML = `<h2>Legenda</h2><ul>${li.join('')}</ul>`;
}

function updateStatus() {
  $('#stato-area').textContent = S.area ? `${S.area.name} · ${fmt.area(S.area.km2)}` : 'Nessuna area';
  $('#stato-zoom').textContent = view ? `Ingrandimento ${fmt.dec(zoomLevel())}` : 'Ingrandimento non disponibile';
  const parts = [ATTRIBUTION_OSM];
  if (S.style.backdrop !== 'solid' && isGL) parts.push(`mappa di base ${esc(CONFIG.basemaps[S.style.basemap]?.name || '')}`);
  const src = S.routes.find((r) => r.source)?.source;
  if (src) parts.push(esc(src));
  $('#stato-fonte').innerHTML = parts.join(' · ');
}

/* =================== ricerca =================== */

let searchCtrl = null;
let lastResults = [];

function showResults(html) {
  const box = $('#risultati');
  box.innerHTML = html;
  box.hidden = !html;
  $('#campo-cerca').setAttribute('aria-expanded', String(!!html));
}

function recentHtml() {
  const rec = store.get('recenti', []);
  if (!rec.length) return '';
  return `<h3>Luoghi recenti</h3>` + rec.map((r, i) => `<button type="button" class="risultato" data-recente="${i}"><strong>${esc(r.name)}</strong><small>${esc(r.label || '')}</small></button>`).join('');
}

async function doSearch(q) {
  q = q.trim();
  if (!q) { showResults(recentHtml()); return; }
  // Coordinate scritte a mano: "46.01, 12.62"
  const m = /^\s*(-?\d+(?:[.,]\d+)?)\s*[,;\s]\s*(-?\d+(?:[.,]\d+)?)\s*$/.exec(q);
  if (m) {
    const lat = parseFloat(m[1].replace(',', '.')), lon = parseFloat(m[2].replace(',', '.'));
    if (Math.abs(lat) <= 90 && Math.abs(lon) <= 180) {
      choosePlace({ id: 'coord', name: `Punto ${lat.toFixed(4)}, ${lon.toFixed(4)}`, label: 'Coordinate inserite', center: [lon, lat], bbox: null, osm: null, geojson: null, typeLabel: 'punto' });
      return;
    }
  }
  searchCtrl?.abort();
  searchCtrl = new AbortController();
  showResults('<p class="nota" style="margin:8px">Ricerca in corso…</p>');
  try {
    lastResults = await geocoder.search(q, { signal: searchCtrl.signal });
    if (!lastResults.length) { showResults(`<p class="nota" style="margin:8px">Nessun risultato per «${esc(q)}». Prova a scrivere anche la provincia o la regione.</p>`); return; }
    showResults(`<h3>${lastResults.length === 1 ? '1 risultato' : lastResults.length + ' risultati'}: scegli quello giusto</h3>` + lastResults.map((r, i) => `<button type="button" class="risultato" data-ris="${i}"><strong>${esc(r.name)}<span class="tipo">${esc(r.typeLabel)}</span></strong><small>${esc(r.label)}</small></button>`).join(''));
    $('#risultati .risultato')?.focus();
  } catch (e) {
    if (isAbort(e)) return;
    showResults(`<p class="esito errore" style="margin:8px">${geocodeErrorMessage(e)}</p>`);
  }
}

function setupSearch() {
  const input = $('#campo-cerca');
  $('#form-cerca').addEventListener('submit', (e) => { e.preventDefault(); doSearch(input.value); });
  input.addEventListener('focus', () => { if (!input.value.trim()) showResults(recentHtml()); });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); $('#risultati .risultato')?.focus(); }
    if (e.key === 'Escape') showResults('');
  });
  $('#risultati').addEventListener('click', (e) => {
    const b = e.target.closest('.risultato');
    if (!b) return;
    if (b.dataset.ris != null) choosePlace(lastResults[+b.dataset.ris]);
    else if (b.dataset.recente != null) chooseRecent(store.get('recenti', [])[+b.dataset.recente]);
  });
  $('#risultati').addEventListener('keydown', (e) => {
    const items = $$('#risultati .risultato');
    const i = items.indexOf(document.activeElement);
    if (e.key === 'ArrowDown') { e.preventDefault(); items[Math.min(items.length - 1, i + 1)]?.focus(); }
    if (e.key === 'ArrowUp') { e.preventDefault(); if (i <= 0) input.focus(); else items[i - 1].focus(); }
    if (e.key === 'Escape') { showResults(''); input.focus(); }
  });
  document.addEventListener('click', (e) => { if (!e.target.closest('.ricerca')) showResults(''); });
}

/* =================== area =================== */

function areaKey(a) {
  if (!a) return '';
  if (a.kind === 'relation') return `r${a.osmId}`;
  if (a.kind === 'polygon') return 'p' + a.ring.map((c) => c.map((v) => v.toFixed(5)).join(',')).join(';').slice(0, 400) + a.ring.length;
  return 'b' + a.bbox.map((v) => v.toFixed(5)).join(',');
}

function outerRing(geojson) {
  if (geojson.type === 'Polygon') return geojson.coordinates[0];
  let best = null, bestA = -1;
  for (const p of geojson.coordinates) {
    const a = geometryAreaKm2({ type: 'Polygon', coordinates: p });
    if (a > bestA) { bestA = a; best = p[0]; }
  }
  return best;
}

function simplifyRing(ring, maxPts = 300) {
  let tol = 0.00005, out = ring;
  while (out.length > maxPts && tol < 0.05) { out = simplify(ring, tol); tol *= 2; }
  if (out.length > 3 && (out[0][0] !== out[out.length - 1][0] || out[0][1] !== out[out.length - 1][1])) out = [...out, out[0]];
  return out;
}

function bboxAround([lon, lat], meters) {
  const dLat = meters / 111320, dLon = meters / (111320 * Math.cos((lat * Math.PI) / 180));
  return [lon - dLon, lat - dLat, lon + dLon, lat + dLat];
}

function choosePlace(r) {
  showResults('');
  $('#campo-cerca').value = r.name;
  let area;
  if (r.geojson && r.osm?.type === 'relation') {
    const ring = simplifyRing(outerRing(r.geojson));
    area = { kind: 'relation', osmId: r.osm.id, name: r.name, label: r.label, bbox: r.bbox || bboxOfCoords(ring), ring, km2: geometryAreaKm2(r.geojson), source: 'ricerca' };
  } else if (r.geojson) {
    const ring = simplifyRing(outerRing(r.geojson), 200);
    area = { kind: 'polygon', name: r.name, label: r.label, bbox: bboxOfCoords(ring), ring, km2: geometryAreaKm2({ type: 'Polygon', coordinates: [ring] }), source: 'ricerca' };
  } else {
    const radius = +$('#raggio').value;
    const bbox = bboxAround(r.center, radius);
    area = { kind: 'bbox', name: `${r.name} e dintorni`, label: r.label, bbox, center: r.center, radius, km2: bboxAreaKm2(bbox), source: 'punto' };
  }
  setArea(area, { autoload: true });
}

function chooseRecent(a) {
  showResults('');
  if (!a) return;
  $('#campo-cerca').value = a.name;
  setArea({ ...a, km2: a.km2 ?? bboxAreaKm2(a.bbox) }, { autoload: true });
}

function rememberArea(a) {
  const compact = { kind: a.kind, osmId: a.osmId, name: a.name, label: a.label, bbox: a.bbox, ring: a.ring ? simplifyRing(a.ring, 150) : undefined, km2: a.km2, center: a.center, radius: a.radius };
  const rec = store.get('recenti', []).filter((x) => areaKey(x) !== areaKey(a) && x.name !== a.name);
  rec.unshift(compact);
  store.set('recenti', rec.slice(0, 8));
  renderRecents();
}

function renderRecents() {
  const rec = store.get('recenti', []);
  $('#recenti').innerHTML = rec.length
    ? `<div class="etichetta-gruppo" style="width:100%">Recenti</div>` + rec.map((r, i) => `<button type="button" class="btn piccolo" data-recente="${i}">${esc(r.name)}</button>`).join('') + `<button type="button" class="btn piccolo" id="b-svuota-recenti">Svuota l'elenco</button>`
    : '';
}

function areaWarning(a, layer = 'roads') {
  const lim = CONFIG.limits[layer] || CONFIG.limits.roads;
  if (a.km2 > lim.max) return { level: 'max', lim };
  if (a.km2 > lim.warn) return { level: 'warn', lim };
  return null;
}

function renderAreaInfo() {
  const a = S.area;
  const box = $('#area-info');
  if (!a) { box.textContent = 'Nessuna area scelta. Cerca un luogo qui sopra oppure usa uno dei comandi seguenti.'; $('#b-carica').disabled = true; return; }
  const w = areaWarning(a);
  const kindLabel = { relation: 'confine amministrativo', polygon: 'area disegnata o del luogo', bbox: a.source === 'punto' ? `riquadro di ${fmt.dist(a.radius)} attorno al punto` : 'riquadro' }[a.kind];
  let html = `<strong>${esc(a.name)}</strong>${esc(kindLabel)} · superficie ${fmt.area(a.km2)}`;
  if (S.roads.length && S.loadedFor.roads === areaKey(a)) html += `<br>${fmt.int(S.roads.length)} strade caricate, ${fmt.km(S.roads.reduce((s, r) => s + r.len, 0))}`;
  if (w?.level === 'max') html += `<br><span class="errore">Area troppo grande per la rete stradale (massimo ${fmt.int(w.lim.max)} km²). Restringi la selezione: cerca un comune, disegna un riquadro più piccolo o ingrandisci e usa la vista attuale.</span>`;
  else if (w?.level === 'warn') html += `<br><span class="avviso">Area estesa: il caricamento può richiedere più di un minuto e molti dati.</span>`;
  box.innerHTML = html;
  $('#b-carica').disabled = w?.level === 'max';
  $('#b-carica').textContent = S.loadedFor.roads === areaKey(a) ? 'Ricarica la rete stradale' : 'Carica la rete stradale';
  $('#campo-raggio').hidden = a.source !== 'punto';
}

function setArea(area, { autoload = false, fit = true } = {}) {
  const changed = areaKey(area) !== areaKey(S.area);
  S.area = area;
  if (changed) {
    S.selection = null;
    $('#scheda').hidden = true;
  }
  renderAreaInfo();
  if (fit) fitTo(area.bbox);
  renderAll(true);
  updateStatus();
  if (autoload) loadArea();
}

/* =================== caricamento dati =================== */

let jobCtrl = null;
let jobQueue = Promise.resolve();
let progressTimer = null;

function showProgress(title) {
  const box = $('#avanzamento');
  box.hidden = false;
  $('#av-titolo').textContent = title;
  $('#av-dettaglio').textContent = 'Preparazione della richiesta';
  const start = Date.now();
  let bytes = 0, status = '';
  clearInterval(progressTimer);
  const paint = () => {
    const s = Math.round((Date.now() - start) / 1000);
    $('#av-dettaglio').textContent = [bytes ? `${fmt.bytes(bytes)} ricevuti` : null, `${s} s`, status].filter(Boolean).join(' · ');
  };
  progressTimer = setInterval(paint, 500);
  return {
    bytes: (b) => { bytes = b; },
    status: (t) => { status = t; paint(); },
    done: () => { clearInterval(progressTimer); box.hidden = true; },
  };
}

function cancelJobs() {
  jobCtrl?.abort();
}

async function fetchLayer(layer, area, label, force = false) {
  const ctrl = new AbortController();
  jobCtrl = ctrl;
  const p = showProgress(label);
  try {
    const q = buildQuery(layer, area);
    const json = await runOverpass(q, { signal: ctrl.signal, onProgress: p.bytes, onStatus: p.status, force });
    p.status('Elaborazione dei dati');
    await new Promise((r) => setTimeout(r, 0));
    return json;
  } finally {
    p.done();
    if (jobCtrl === ctrl) jobCtrl = null;
  }
}

async function loadArea({ force = false } = {}) {
  const a = S.area;
  if (!a) return;
  const w = areaWarning(a);
  if (w?.level === 'max') { renderAreaInfo(); toast('L\'area scelta è troppo grande: restringi la selezione per caricare la rete.', 'attenzione'); return; }
  if (w?.level === 'warn') {
    const ok = await confirmDialog('Area estesa', `<p>«${esc(a.name)}» misura circa <strong>${fmt.area(a.km2)}</strong>. Scaricare tutte le strade può richiedere qualche minuto e decine di megabyte, e il disegno può rallentare sui dispositivi meno potenti.</p><p>Puoi restringere l'area oppure procedere: lo stato del caricamento resta visibile e puoi annullarlo in ogni momento.</p>`, 'Carica comunque');
    if (!ok) return;
  }
  enqueue(async () => {
    const json = await fetchLayer('roads', a, `Scarico le strade di ${a.name}`, force);
    if (areaKey(S.area) !== areaKey(a)) return;
    const roads = parseRoads(json);
    roads._version = (S.roads._version || 0) + 1;
    S.roads = roads;
    S.loadedFor.roads = areaKey(a);
    S.elev = null; S.elevRange = null; S.density = null;
    S.creative.paths = []; S.genOrigin = null;
    if (S.style.colorMode === 'elevation') S.style.colorMode = 'class';
    $('#b-colora-quote').disabled = true;
    $('#esito-quote').textContent = '';
    if (!roads.length) {
      toast(a.kind === 'relation' ? 'Nessuna strada trovata in quest\'area. Il server potrebbe non avere ancora l\'area amministrativa indicizzata: prova con «Usa la vista attuale».' : 'Nessuna strada trovata in quest\'area.', 'attenzione', 10000);
    } else {
      const total = roads.reduce((s, r) => s + r.len, 0);
      toast(`Caricate ${fmt.int(roads.length)} strade, ${fmt.km(total)} di rete.${json.__daMemoria ? ' Dati ripresi dalla memoria del browser: «Ricarica la rete stradale» li scarica di nuovo.' : ''}`, 'ok', json.__daMemoria ? 6000 : 4000);
      if (roads.length > CONFIG.heavyFeatureCount) toast('Rete molto ampia: sui dispositivi meno potenti il disegno e le esportazioni possono rallentare. Usa i filtri per alleggerire.', 'attenzione', 9000);
    }
    if (S.style.colorMode === 'density') S.density = densityByRoad(roads);
    rememberArea(a);
    buildFilterUi();
    applyFilters();
    renderAreaInfo();
    renderLayerList();
    fitTo(a.bbox);
    updateExportDefaults();
    if (!$('#sez-analisi').hidden) updateAnalysis();
    autoRoute();
    // Livelli aggiuntivi già attivi: si caricano in coda.
    for (const l of LAYERS) if (l.key !== 'roads' && S.layers[l.key].on) loadLayer(l.key, true);
  });
}

function enqueue(fn) {
  jobQueue = jobQueue.then(fn).catch((e) => {
    if (isAbort(e)) { toast('Caricamento annullato.', 'info', 3000); return; }
    console.error(e);
    toast(e.status || e instanceof TypeError ? overpassErrorMessage(e) : esc(e.message || String(e)), 'errore', 12000);
  });
  return jobQueue;
}

async function loadLayer(key, silent = false) {
  const a = S.area;
  if (!a || S.loadedFor[key] === areaKey(a)) { renderAll(true); return; }
  const w = areaWarning(a, key);
  const label = LAYER_BY_KEY[key].label.toLowerCase();
  if (w?.level === 'max') {
    toast(`L'area è troppo grande per il livello «${esc(label)}» (massimo ${fmt.int(w.lim.max)} km²). Restringi l'area per attivarlo.`, 'attenzione', 9000);
    S.layers[key].on = false;
    renderLayerList();
    return;
  }
  if (w?.level === 'warn' && !silent) {
    const ok = await confirmDialog('Livello pesante', `<p>Il livello «${esc(label)}» su ${fmt.area(a.km2)} può contenere molti elementi e richiedere tempo.</p>`, 'Carica comunque');
    if (!ok) { S.layers[key].on = false; renderLayerList(); return; }
  }
  return enqueue(async () => {
    const json = await fetchLayer(key, a, `Scarico: ${label}`);
    if (areaKey(S.area) !== areaKey(a)) return;
    S.layerData[key] = parseLayer(key, json);
    S.loadedFor[key] = areaKey(a);
    if (!S.layerData[key].length) toast(`Nessun elemento «${esc(label)}» nell'area.`, 'info', 4000);
    renderLayerList();
    renderAll(true);
  });
}

/* =================== livelli =================== */

function layerCount(key) {
  if (key === 'roads') return S.loadedFor.roads === areaKey(S.area) && S.area ? `${fmt.int(S.visibleRoads.length)} di ${fmt.int(S.roads.length)}` : 'non caricato';
  if (S.loadedFor[key] === areaKey(S.area) && S.area) return `${fmt.int(S.layerData[key]?.length || 0)} elementi`;
  return S.layers[key].on && S.area ? 'in coda' : 'non caricato';
}

function renderLayerList() {
  const ul = $('#lista-livelli');
  ul.innerHTML = LAYERS.map((l) => {
    const st = S.layers[l.key];
    const col = l.key === 'roads' ? (S.style.colorMode === 'uniform' ? S.style.roadColor : S.style.classColors.residential) : S.style.layerColors[l.key];
    const wSlider = l.kind !== 'fill' && l.kind !== 'point' ? `<label class="cursore">Spessore <output>${fmt.dec(st.width)}</output><input type="range" min="0.2" max="4" step="0.1" value="${st.width}" data-liv-w="${l.key}" /></label>` : '';
    return `<li>
      <div class="testa">
        <label><input type="checkbox" data-liv="${l.key}" ${st.on ? 'checked' : ''} /> <span class="campione" style="background:${col}"></span>${esc(l.label)}</label>
        <span class="conteggio">${layerCount(l.key)}</span>
      </div>
      <details><summary>Regola ${esc(l.label.toLowerCase())}</summary>
        <label class="cursore">Opacità <output>${fmt.dec2(st.opacity)}</output><input type="range" min="0.05" max="1" step="0.05" value="${st.opacity}" data-liv-op="${l.key}" /></label>
        ${wSlider}
      </details>
    </li>`;
  }).join('');
}

function setupLayerList() {
  const ul = $('#lista-livelli');
  ul.addEventListener('change', (e) => {
    const k = e.target.dataset.liv;
    if (!k) return;
    S.layers[k].on = e.target.checked;
    pushHistory();
    if (e.target.checked && k !== 'roads' && S.area) loadLayer(k);
    renderLayerList();
    renderAll(true);
  });
  ul.addEventListener('input', (e) => {
    const t = e.target;
    const k = t.dataset.livOp || t.dataset.livW;
    if (!k) return;
    S.layers[k][t.dataset.livOp ? 'opacity' : 'width'] = +t.value;
    t.previousElementSibling.textContent = fmt.dec2(+t.value);
    pushHistory();
    renderAll(!isGL);
  });
}

/* =================== filtri =================== */

function parseSpeed(v) {
  if (!v) return null;
  const m = /^(\d+)\s*(mph)?/.exec(v);
  return m ? (m[2] ? Math.round(+m[1] * 1.609) : +m[1]) : null;
}

function roadPasses(r, f) {
  if (!f.classes[r.cls]) return false;
  const t = r.tags;
  const miss = f.includeMissing;
  if (f.oneway !== 'all') {
    if (t.oneway == null) { if (!miss) return false; }
    else {
      const yes = ['yes', '1', 'true', '-1', 'reversible', 'alternating'].includes(t.oneway);
      if ((f.oneway === 'yes') !== yes) return false;
    }
  }
  if (f.lit !== 'all') {
    if (t.lit == null) { if (!miss) return false; }
    else if ((f.lit === 'yes') !== (t.lit !== 'no')) return false;
  }
  if (f.surface.length) {
    if (t.surface == null) { if (!miss) return false; } else if (!f.surface.includes(t.surface)) return false;
  }
  if (f.access.length) {
    if (t.access == null) { if (!miss) return false; } else if (!f.access.includes(t.access)) return false;
  }
  if (f.maxspeedMin != null || f.maxspeedMax != null) {
    const s = parseSpeed(t.maxspeed);
    if (s == null) { if (!miss) return false; }
    else if ((f.maxspeedMin != null && s < f.maxspeedMin) || (f.maxspeedMax != null && s > f.maxspeedMax)) return false;
  }
  return true;
}

function applyFilters() {
  S.visibleRoads = S.roads.filter((r) => roadPasses(r, S.filters));
  const n = S.visibleRoads.length, tot = S.roads.length;
  $('#riepilogo-filtri').textContent = tot ? `Mostrate ${fmt.int(n)} strade su ${fmt.int(tot)} (${fmt.km(S.visibleRoads.reduce((s, r) => s + r.len, 0))}).` : 'Carica un\'area per usare i filtri.';
  renderLayerList();
  renderAll(true);
  if (!$('#sez-analisi').hidden) updateAnalysisDebounced();
}

function buildFilterUi() {
  const counts = {};
  for (const r of S.roads) counts[r.cls] = (counts[r.cls] || 0) + 1;
  $('#filtri-classi').innerHTML = ROAD_CLASSES.filter((c) => counts[c.key]).map((c) => {
    const col = S.style.colorMode === 'class' ? S.style.classColors[c.key] : S.style.roadColor;
    return `<li><label><input type="checkbox" data-classe="${c.key}" ${S.filters.classes[c.key] ? 'checked' : ''}/>
      <svg width="30" height="10" aria-hidden="true"><line x1="1" y1="5" x2="29" y2="5" stroke="${col}" stroke-width="${Math.min(5, c.width * 1.4)}" ${c.dash ? `stroke-dasharray="${c.dash.map((d) => d * 2.5).join(' ')}"` : ''}/></svg>
      ${esc(c.label)}<span class="conteggio">${fmt.int(counts[c.key])}</span></label></li>`;
  }).join('') || '<li class="nota">Nessuna strada caricata.</li>';

  // Valori presenti per ciascun attributo.
  const vals = {};
  for (const a of ATTRIBUTES) vals[a.key] = new Map();
  for (const r of S.roads) for (const a of ATTRIBUTES) {
    const v = r.tags[a.key];
    if (v != null) vals[a.key].set(v, (vals[a.key].get(v) || 0) + 1);
  }
  const f = S.filters;
  const html = [];
  const withData = (k) => [...vals[k].values()].reduce((s, x) => s + x, 0);
  for (const a of ATTRIBUTES) {
    const m = vals[a.key];
    if (!m.size) continue;
    const n = withData(a.key);
    const head = `<h4>${esc(a.label)} <small>dato presente su ${fmt.int(n)} strade su ${fmt.int(S.roads.length)}</small></h4>`;
    if (a.key === 'oneway' || a.key === 'lit') {
      const opts = [['all', 'Indifferente'], ['yes', 'Sì'], ['no', 'No']];
      html.push(`<div class="attr">${head}<div class="valori" role="radiogroup" aria-label="${esc(a.label)}">${opts.map(([v, l]) => `<label><input type="radio" name="f-${a.key}" value="${v}" data-filtro="${a.key}" ${f[a.key] === v ? 'checked' : ''}/> ${l}</label>`).join('')}</div></div>`);
    } else if (a.key === 'maxspeed') {
      const speeds = [...m.keys()].map(parseSpeed).filter((x) => x != null);
      if (!speeds.length) continue;
      const lo = Math.min(...speeds), hi = Math.max(...speeds);
      html.push(`<div class="attr">${head}<div class="due"><label class="campo">Da (km/h)<input type="number" min="0" max="200" step="5" placeholder="${lo}" value="${f.maxspeedMin ?? ''}" data-filtro="maxspeedMin"/></label><label class="campo">A (km/h)<input type="number" min="0" max="200" step="5" placeholder="${hi}" value="${f.maxspeedMax ?? ''}" data-filtro="maxspeedMax"/></label></div></div>`);
    } else {
      const sorted = [...m.entries()].sort((x, y) => y[1] - x[1]).slice(0, 24);
      html.push(`<div class="attr">${head}<p class="nota">Nessuna casella spuntata = tutti i valori.</p><div class="valori">${sorted.map(([v, c]) => `<label><input type="checkbox" value="${esc(v)}" data-filtro-lista="${a.key}" ${f[a.key].includes(v) ? 'checked' : ''}/> ${esc(valueLabel(a.key, v))} <small>(${fmt.int(c)})</small></label>`).join('')}</div></div>`);
    }
  }
  $('#filtri-attributi').innerHTML = html.join('') || '<p class="nota">I dati caricati non contengono attributi filtrabili.</p>';
}

function setupFilters() {
  $('#filtri-classi').addEventListener('change', (e) => {
    const k = e.target.dataset.classe;
    if (!k) return;
    S.filters.classes[k] = e.target.checked;
    pushHistory();
    applyFilters();
  });
  $('#filtri-attributi').addEventListener('change', (e) => {
    const t = e.target;
    if (t.dataset.filtro === 'oneway' || t.dataset.filtro === 'lit') S.filters[t.dataset.filtro] = t.value;
    else if (t.dataset.filtro) S.filters[t.dataset.filtro] = t.value === '' ? null : +t.value;
    else if (t.dataset.filtroLista) {
      const k = t.dataset.filtroLista;
      S.filters[k] = $$(`[data-filtro-lista="${k}"]:checked`).map((x) => x.value);
    }
    pushHistory();
    applyFilters();
  });
  const setClasses = (fn) => { for (const c of ROAD_CLASSES) S.filters.classes[c.key] = fn(c.key); buildFilterUi(); pushHistory(); applyFilters(); };
  $('#b-tutte-classi').onclick = () => setClasses(() => true);
  $('#b-solo-carrabili').onclick = () => setClasses((k) => ['motorway', 'primary', 'secondary', 'tertiary', 'residential', 'service'].includes(k));
  $('#b-solo-lente').onclick = () => setClasses((k) => ['pedestrian', 'path', 'cycleway', 'track'].includes(k));
  $('#b-ripristina-filtri').onclick = () => { S.filters = defaultFilters(); buildFilterUi(); syncBindings(); pushHistory(); applyFilters(); };
}

/* =================== stile e collegamenti ai comandi =================== */

function getPath(path) { return path.split('.').reduce((o, k) => o?.[k], S); }
function setPath(path, v) {
  const keys = path.split('.');
  const last = keys.pop();
  keys.reduce((o, k) => o[k], S)[last] = v;
}

function syncBindings() {
  for (const el of $$('[data-bind]')) {
    const v = getPath(el.dataset.bind);
    if (el.type === 'checkbox') el.checked = !!v;
    else if (el.type === 'radio') el.checked = el.value === v;
    else el.value = v;
  }
  for (const o of $$('[data-out]')) o.textContent = fmt.dec2(getPath(o.dataset.out));
  renderClassColors();
  renderPresets();
  updateColorModeUi();
  applyPanels();
}

function setupBindings() {
  document.addEventListener('input', onBound);
  document.addEventListener('change', onBound);
  function onBound(e) {
    const el = e.target;
    const path = el.dataset?.bind;
    if (!path) return;
    if (el.type === 'radio' && !el.checked) return;
    if (e.type === 'input' && (el.type === 'checkbox' || el.type === 'radio' || el.tagName === 'SELECT')) return;
    let v = el.value;
    if (el.type === 'checkbox') v = el.checked;
    else if (el.type === 'range' || el.type === 'number') v = +v;
    if (getPath(path) === v) return;
    setPath(path, v);
    const out = $(`[data-out="${path}"]`);
    if (out) out.textContent = fmt.dec2(v);
    onSettingChanged(path);
  }
}

function onSettingChanged(path) {
  pushHistory();
  if (path.startsWith('filters.')) { applyFilters(); return; }
  if (path === 'style.colorMode') return changeColorMode(S.style.colorMode);
  if (path === 'style.animate' && S.style.animate && reducedMotion.matches) $('#nota-movimento').hidden = false;
  const needsData = ['style.roadColor', 'style.brightness'].includes(path) || !isGL;
  if (path === 'style.routeColor') recolorMainRoute();
  if (['style.bg', 'style.roadColor', 'style.colorMode'].includes(path)) S.style.preset = null;
  if (path === 'style.labels' || path === 'style.backdrop') applyPanels();
  renderPresets();
  if (path === 'style.roadColor') buildFilterUi();
  renderAll(needsData);
  renderLayerList();
}

function changeColorMode(mode) {
  if (mode === 'elevation' && !S.elev) {
    S.style.colorMode = 'class';
    syncBindings();
    toast('Per colorare per altimetria servono le quote: apri la scheda Analisi e premi «Leggi le quote».', 'attenzione', 8000);
    selectTab('tab-analisi');
    $('#b-quote').closest('details').open = true;
    return;
  }
  if (mode === 'density' && !S.density && S.roads.length) S.density = densityByRoad(S.roads);
  updateColorModeUi();
  buildFilterUi();
  renderAll(true);
  renderLayerList();
}

function updateColorModeUi() {
  const m = S.style.colorMode;
  $('#lab-colore-unico').hidden = m !== 'uniform';
  const nota = $('#nota-colore');
  const msg = {
    orientation: 'Ogni strada prende il colore della sua direzione prevalente: strade parallele hanno lo stesso colore.',
    density: 'Colore dalla densità della rete nei dintorni (celle di circa 250 metri).',
    elevation: S.elev ? `Quote lette per ${fmt.int(S.elev.size)} strade: le altre restano in grigio.` : '',
  }[m];
  nota.hidden = !msg;
  nota.textContent = msg || '';
}

function renderClassColors() {
  $('#colori-classi').innerHTML = ROAD_CLASSES.map((c) => `<label>${esc(c.label)}<input type="color" value="${S.style.classColors[c.key]}" data-colore-classe="${c.key}" /></label>`).join('');
  $('#colori-livelli').innerHTML = LAYERS.filter((l) => l.key !== 'roads').map((l) => `<label>${esc(l.label)}<input type="color" value="${S.style.layerColors[l.key]}" data-colore-livello="${l.key}" /></label>`).join('');
}

function renderPresets() {
  $('#preset').innerHTML = Object.entries(PRESETS).map(([k, p]) => {
    const cols = Object.values(p.classColors).slice(0, 6);
    const grad = p.colorMode === 'uniform' ? `linear-gradient(90deg, transparent 0 20%, ${p.roadColor} 20% 24%, transparent 24% 50%, ${p.roadColor} 50% 52%, transparent 52% 78%, ${p.roadColor} 78% 81%, transparent 81%)` : `linear-gradient(90deg, ${cols.map((c, i) => `${c} ${i * 16}% ${i * 16 + 6}%, transparent ${i * 16 + 6}% ${(i + 1) * 16}%`).join(',')})`;
    return `<button type="button" aria-pressed="${S.style.preset === k}" data-preset="${k}"><span class="prova" style="background:${grad}, ${p.bg}"></span><span class="nome">${esc(p.label)}</span></button>`;
  }).join('');
  const personal = store.get('stili', []);
  $('#stili-personali').innerHTML = personal.length ? `<div class="etichetta-gruppo">Stili personali</div>` + personal.map((p, i) => `<div class="stile-personale"><span>${esc(p.name)}</span><button type="button" class="btn piccolo" data-stile-applica="${i}">Applica</button><button type="button" class="btn piccolo" data-stile-elimina="${i}" aria-label="Elimina lo stile ${esc(p.name)}"><svg class="ic"><use href="#i-cestino"/></svg></button></div>`).join('') : '';
}

function setupStyle() {
  $('#preset').addEventListener('click', (e) => {
    const b = e.target.closest('[data-preset]');
    if (!b) return;
    S.style = applyPreset(S.style, b.dataset.preset);
    S.creative.color = creativeColor(S.style.routeColor);
    if (b.dataset.preset === 'notte' || b.dataset.preset === 'neon' || b.dataset.preset === 'monocromatico') { if (S.ui.theme === 'auto' && !isDarkUi()) { /* l'interfaccia resta come scelta dall'utente */ } }
    recolorMainRoute();
    syncBindings();
    buildFilterUi();
    pushHistory();
    renderAll(true);
    renderLayerList();
  });
  $('#colori-classi').addEventListener('input', (e) => {
    const k = e.target.dataset.coloreClasse;
    if (!k) return;
    S.style.classColors[k] = e.target.value;
    S.style.preset = null;
    pushHistory();
    renderAllDebounced();
  });
  $('#colori-livelli').addEventListener('input', (e) => {
    const k = e.target.dataset.coloreLivello;
    if (!k) return;
    S.style.layerColors[k] = e.target.value;
    pushHistory();
    renderAll(!isGL);
  });
  $('#b-ripristina-stile').onclick = () => {
    S.style = { ...defaultStyle(), labels: S.style.labels };
    syncBindings(); buildFilterUi(); pushHistory(); renderAll(true); renderLayerList();
    toast('Stile riportato ai valori predefiniti.', 'ok', 3000);
  };
  $('#b-salva-stile').onclick = async () => {
    const name = prompt('Nome dello stile personale:', 'Il mio stile');
    if (!name) return;
    const list = store.get('stili', []).filter((s) => s.name !== name.trim());
    list.unshift({ name: name.trim().slice(0, 40), style: JSON.parse(JSON.stringify(S.style)) });
    store.set('stili', list.slice(0, 20));
    renderPresets();
    toast(`Stile «${esc(name)}» salvato in questo browser.`, 'ok', 3000);
  };
  $('#stili-personali').addEventListener('click', (e) => {
    const list = store.get('stili', []);
    const a = e.target.closest('[data-stile-applica]'), d = e.target.closest('[data-stile-elimina]');
    if (a) {
      S.style = { ...defaultStyle(), ...list[+a.dataset.stileApplica].style };
      syncBindings(); buildFilterUi(); pushHistory(); renderAll(true); renderLayerList();
    } else if (d) {
      list.splice(+d.dataset.stileElimina, 1);
      store.set('stili', list);
      renderPresets();
    }
  });
}
const renderAllDebounced = debounce(() => renderAll(true), 90);

/* =================== annulla e ripristina =================== */

const history = { stack: [], index: -1, applying: false };
const snapshot = () => JSON.stringify({ style: S.style, layers: S.layers, filters: S.filters });

const commitHistory = debounce(() => {
  if (history.applying) return;
  const snap = snapshot();
  if (history.stack[history.index] === snap) return;
  history.stack = history.stack.slice(0, history.index + 1);
  history.stack.push(snap);
  if (history.stack.length > 80) history.stack.shift();
  history.index = history.stack.length - 1;
  updateHistoryButtons();
  persist();
}, 350);

function pushHistory() { commitHistory(); }

function applySnapshot(i) {
  history.applying = true;
  const s = JSON.parse(history.stack[i]);
  const layersBefore = S.layers;
  S.style = s.style; S.layers = s.layers; S.filters = s.filters;
  history.index = i;
  syncBindings();
  buildFilterUi();
  applyFilters();
  for (const l of LAYERS) if (l.key !== 'roads' && S.layers[l.key].on && !layersBefore[l.key]?.on && S.area) loadLayer(l.key, true);
  renderLayerList();
  renderAll(true);
  updateHistoryButtons();
  persist();
  setTimeout(() => { history.applying = false; }, 400);
}

function undo() { if (history.index > 0) applySnapshot(history.index - 1); }
function redo() { if (history.index < history.stack.length - 1) applySnapshot(history.index + 1); }
function updateHistoryButtons() {
  $('#m-annulla').disabled = history.index <= 0;
  $('#m-ripristina').disabled = history.index >= history.stack.length - 1;
}

function persist() {
  const { ghKey, ...rs } = S.routeSettings;
  store.set('impostazioni', { style: S.style, layers: S.layers, filters: S.filters, ui: S.ui, routeSettings: rs });
}

/* =================== clic e disegno sulla mappa =================== */

function coordLabel([x, y]) { return `${y.toFixed(5)}, ${x.toFixed(5)}`; }

async function reverseLabel(w) {
  const c = w.coord;
  try {
    const name = await geocoder.reverse(c);
    if (name && w.coord === c) { w.label = name; renderWaypoints(); }
  } catch { /* resta l'etichetta con le coordinate */ }
}

function onMapClick(coord, point) {
  if (S.draw) { addDrawPoint(coord); return; }
  if (S.pick?.type === 'origin') {
    S.genOrigin = coord;
    S.pick = null;
    updatePickUi();
    renderAll();
    $('#gen-punto-info').textContent = `Partenza dei percorsi generati: ${coordLabel(coord)}.`;
    return;
  }
  if (S.pick?.type === 'waypoint') {
    const w = S.waypoints.find((x) => x.id === S.pick.id);
    S.pick = null;
    updatePickUi();
    if (w) setWaypoint(w, coord);
    return;
  }
  if (S.mode === 'percorsi') {
    const empty = S.waypoints.find((w) => !w.coord);
    if (empty) { setWaypoint(empty, coord); return; }
    toast('Partenza e arrivo sono già fissati: trascina i segnaposto, usa «Scegli sulla mappa» accanto a un punto oppure aggiungi una tappa.', 'info', 6000);
    return;
  }
  inspectAt(coord, point);
}

function startDraw(type) {
  S.draw = { type, pts: [] };
  S.pick = null;
  if (isGL) { view.map.doubleClickZoom.disable(); }
  const msg = {
    bbox: 'Clic su un angolo del riquadro, poi sull\'angolo opposto.',
    polygon: 'Clic per aggiungere i vertici dell\'area. Doppio clic o «Fine» per chiudere. Esc per annullare.',
    manual: 'Clic per aggiungere i punti del tracciato. Doppio clic o «Fine» per terminare. Il tracciato a mano non segue le strade.',
  }[type];
  $('#testo-disegno').textContent = msg;
  $('#barra-disegno').hidden = false;
  $('#b-fine-disegno').hidden = type === 'bbox';
  setMapCursor();
  if (window.innerWidth <= 860) { $('#pannello').classList.add('ridotto'); applyPanels(); }
  $('#b-annulla-disegno').focus();
}

function addDrawPoint(c) {
  const d = S.draw;
  d.pts.push(c);
  if (d.type === 'bbox' && d.pts.length === 2) { finishDraw(); return; }
  renderDraw();
}

function drawPreviewCoords() {
  const d = S.draw;
  if (d.type === 'bbox' && d.pts.length === 1) return [d.pts[0], d.pts[0]];
  return d.type === 'polygon' && d.pts.length > 2 ? [...d.pts, d.pts[0]] : d.pts;
}

function drawGeoJson() {
  const d = S.draw;
  const feats = d.pts.map((p) => ({ type: 'Feature', geometry: { type: 'Point', coordinates: p }, properties: {} }));
  if (d.pts.length > 1) {
    const geom = d.type === 'polygon' && d.pts.length > 2 ? { type: 'Polygon', coordinates: [[...d.pts, d.pts[0]]] } : { type: 'LineString', coordinates: d.pts };
    feats.push({ type: 'Feature', geometry: geom, properties: {} });
  }
  return { type: 'FeatureCollection', features: feats };
}

function renderDraw() {
  if (isGL) view.setSource('draw', S.draw ? drawGeoJson() : null);
  else renderAll();
}

function endDraw() {
  S.draw = null;
  $('#barra-disegno').hidden = true;
  if (isGL) { view.map.doubleClickZoom.enable(); view.setSource('draw', null); }
  setMapCursor();
  renderAll();
}

function finishDraw() {
  const d = S.draw;
  if (!d) return;
  // Il doppio clic aggiunge due punti uguali: si eliminano i duplicati consecutivi.
  const pts = d.pts.filter((p, i) => i === 0 || haversine(p, d.pts[i - 1]) > 0.5);
  if (d.type === 'bbox') {
    if (pts.length < 2) { endDraw(); return; }
    const bbox = [Math.min(pts[0][0], pts[1][0]), Math.min(pts[0][1], pts[1][1]), Math.max(pts[0][0], pts[1][0]), Math.max(pts[0][1], pts[1][1])];
    endDraw();
    setArea({ kind: 'bbox', name: 'Riquadro disegnato', bbox, km2: bboxAreaKm2(bbox), source: 'riquadro' }, { fit: false });
    afterAreaPicked();
  } else if (d.type === 'polygon') {
    if (pts.length < 3) { toast('Servono almeno tre vertici per un\'area.', 'attenzione', 4000); return; }
    const ring = [...pts, pts[0]];
    endDraw();
    setArea({ kind: 'polygon', name: 'Area disegnata', bbox: bboxOfCoords(ring), ring, km2: geometryAreaKm2({ type: 'Polygon', coordinates: [ring] }), source: 'disegno' }, { fit: false });
    afterAreaPicked();
  } else if (d.type === 'manual') {
    if (pts.length < 2) { toast('Servono almeno due punti per un tracciato.', 'attenzione', 4000); return; }
    endDraw();
    addRoute({ kind: 'manual', name: `Tracciato a mano ${S.routes.filter((r) => r.kind === 'manual').length + 1}`, coords: pts, distance: lineLength(pts) });
    selectTab('tab-percorso');
  }
}

function afterAreaPicked() {
  const w = areaWarning(S.area);
  if (w?.level === 'max') toast('L\'area disegnata è troppo grande: ridisegnala più piccola.', 'attenzione', 7000);
  else toast(`Area di ${fmt.area(S.area.km2)} pronta. Premi «Carica la rete stradale».`, 'info', 5000);
  selectTab('tab-mappa');
  if (window.innerWidth <= 860) { $('#pannello').classList.remove('ridotto'); applyPanels(); }
  $('#b-carica').focus();
}

/* =================== scheda dell'elemento selezionato =================== */

const ATTR_ROWS = [
  ['highway', 'Tipo OSM'], ['ref', 'Numero'], ['oneway', 'Senso unico'], ['surface', 'Superficie'], ['access', 'Accesso'],
  ['lit', 'Illuminazione'], ['maxspeed', 'Limite di velocità'], ['lanes', 'Corsie'], ['toll', 'Pedaggio'], ['bridge', 'Ponte'],
  ['tunnel', 'Galleria'], ['bicycle', 'Biciclette'], ['foot', 'Pedoni'], ['sidewalk', 'Marciapiede'], ['width', 'Larghezza'],
  ['smoothness', 'Regolarità del fondo'], ['tracktype', 'Tipo di sterrato'], ['junction', 'Incrocio'],
  ['railway', 'Ferrovia'], ['route', 'Linea'], ['network', 'Rete'], ['operator', 'Gestore'], ['from', 'Da'], ['to', 'A'],
  ['building', 'Edificio'], ['levels', 'Piani'], ['height', 'Altezza'], ['leisure', 'Svago'], ['landuse', 'Uso del suolo'],
  ['natural', 'Natura'], ['waterway', 'Corso d\'acqua'], ['water', 'Acqua'], ['admin_level', 'Livello amministrativo'],
  ['amenity', 'Servizio'], ['tourism', 'Turismo'], ['historic', 'Storico'], ['opening_hours', 'Orari'], ['website', 'Sito'],
  ['addr:street', 'Via'], ['addr:housenumber', 'Civico'],
];
const YESNO = { yes: 'sì', no: 'no' };

function inspectAt(coord, point) {
  let hit = null;
  if (isGL) {
    const p = point;
    const layers = ['poi-circle', 'roads-solid', ...ROAD_CLASSES.filter((c) => c.dash).map((c) => `roads-${c.key}`), 'rail-line', 'transit-line', 'cycleroutes-line', 'water-line', 'boundaries-line', 'buildings-fill', 'water-fill', 'green-fill']
      .filter((id) => view.map.getLayer(id) && view.map.getLayoutProperty(id, 'visibility') !== 'none');
    const feats = view.map.queryRenderedFeatures([[p.x - 5, p.y - 5], [p.x + 5, p.y + 5]], { layers });
    const f = feats[0];
    if (f) {
      if (f.source === 'roads') hit = { road: S.visibleRoads[f.properties.i] };
      else {
        const src = S.layerData[f.source]?.find((x) => x.properties.osmId === f.properties.osmId);
        hit = { feature: src || { properties: f.properties, geometry: f.geometry } };
      }
    }
  } else {
    hit = nearestRoad(coord);
  }
  if (!hit) { $('#scheda').hidden = true; S.selection = null; if (isGL) view.setSource('selection', null); return; }
  showCard(hit);
}

function nearestRoad(c) {
  const tol = 0.0008 * 2 ** (14 - Math.min(18, Math.max(10, zoomLevel())));
  let best = null, bestD = Infinity;
  for (const r of S.visibleRoads) {
    if (c[0] < r.bbox[0] - tol || c[0] > r.bbox[2] + tol || c[1] < r.bbox[1] - tol || c[1] > r.bbox[3] + tol) continue;
    for (let i = 1; i < r.coords.length; i++) {
      const d = segDist(c, r.coords[i - 1], r.coords[i]);
      if (d < bestD) { bestD = d; best = r; }
    }
  }
  return best && bestD < tol ? { road: best } : null;
}

function segDist(p, a, b) {
  const dx = b[0] - a[0], dy = b[1] - a[1];
  const l = dx * dx + dy * dy;
  let t = l ? ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / l : 0;
  t = Math.max(0, Math.min(1, t));
  return Math.hypot(a[0] + t * dx - p[0], a[1] + t * dy - p[1]);
}

function showCard(hit) {
  const box = $('#scheda');
  let title, rows = [], osmType, osmId, geom, extra = '';
  if (hit.road) {
    const r = hit.road;
    title = r.tags.name || CLASS_BY_KEY[r.cls].label.replace(/e$/, 'a').replace(/i$/, 'o');
    if (!r.tags.name) title = `${CLASS_BY_KEY[r.cls].label} (senza nome)`;
    rows.push(['Categoria', CLASS_BY_KEY[r.cls].label]);
    for (const [k, l] of ATTR_ROWS) if (r.tags[k] != null) rows.push([l, valueLabel(k, r.tags[k]) === r.tags[k] ? (YESNO[r.tags[k]] || r.tags[k]) : valueLabel(k, r.tags[k])]);
    rows.push(['Lunghezza del tratto', `${fmt.dist(r.len)} (misurata sulla geometria)`]);
    if (S.elev?.has(r.id)) rows.push(['Quota stimata', `${Math.round(S.elev.get(r.id))} m (modello altimetrico)`]);
    osmType = 'way'; osmId = r.id; geom = { type: 'LineString', coordinates: r.coords };
  } else {
    const p = hit.feature.properties;
    title = p.name || LAYER_BY_KEY[p.layer]?.label || 'Elemento';
    rows.push(['Livello', LAYER_BY_KEY[p.layer]?.label || '']);
    for (const [k, l] of ATTR_ROWS) if (p[k] != null) rows.push([l, YESNO[p[k]] || p[k]]);
    osmType = p.osmType; osmId = p.osmId; geom = hit.feature.geometry;
  }
  const missing = hit.road ? ATTRIBUTES.filter((a) => hit.road.tags[a.key] == null).map((a) => a.label.toLowerCase()) : [];
  if (missing.length) extra = `<p class="fonte">Non indicato nei dati: ${esc(missing.join(', '))}.</p>`;
  box.innerHTML = `<button type="button" class="btn icona chiudi" aria-label="Chiudi la scheda"><svg class="ic"><use href="#i-chiudi"/></svg></button>
    <h2 id="scheda-titolo">${esc(title)}</h2>
    <dl>${rows.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${/^https?:\/\//.test(v) ? `<a href="${esc(v)}" target="_blank" rel="noopener">${esc(v)}</a>` : esc(v)}</dd>`).join('')}</dl>
    ${extra}
    <p class="fonte">Fonte: OpenStreetMap, <a href="https://www.openstreetmap.org/${osmType}/${osmId}" target="_blank" rel="noopener">${osmType === 'way' ? 'via' : osmType === 'node' ? 'nodo' : 'relazione'} ${osmId}</a>. ${ATTRIBUTION_OSM}.</p>`;
  box.hidden = false;
  box.querySelector('.chiudi').onclick = closeCard;
  S.selection = geom;
  if (isGL) view.setSource('selection', geom && geom.type !== 'Point' ? { type: 'Feature', geometry: geom, properties: {} } : null);
}

function closeCard() {
  $('#scheda').hidden = true;
  S.selection = null;
  if (isGL) view.setSource('selection', null);
}

/* =================== percorsi =================== */

let router = null;
let routeCtrl = null;

function getRouter() {
  router = createRouter(S.routeSettings, () => S.roads);
  return router;
}

function renderModes() {
  const r = getRouter();
  $('#mezzi').innerHTML = Object.entries(MODES).map(([k, l]) => {
    const ok = r.modes.includes(k);
    return `<button type="button" class="btn" role="radio" aria-checked="${S.routeSettings.mode === k}" data-mezzo="${k}" ${ok ? '' : 'aria-disabled="true"'} title="${ok ? l : `${l}: non disponibile con ${r.name}`}">${l}</button>`;
  }).join('');
  const nota = $('#nota-mezzo');
  if (!r.modes.includes(S.routeSettings.mode)) {
    nota.hidden = false;
    nota.textContent = `${r.name} non calcola percorsi «${MODES[S.routeSettings.mode].toLowerCase()}». Scegli ${r.modes.map((m) => MODES[m].toLowerCase()).join(' o ')}, oppure cambia motore di calcolo.`;
  } else nota.hidden = true;
  renderRouteOptions();
}

function renderRouteOptions() {
  const r = router || getRouter();
  const cap = r.capabilities(S.routeSettings.mode);
  $('#opzioni').innerHTML = Object.entries(OPTION_LABELS).map(([k, l]) => {
    const ok = !!cap.options[k];
    return `<label class="spunta ${ok ? '' : 'disattivo'}"><input type="checkbox" data-opzione="${k}" ${S.routeSettings.options[k] && ok ? 'checked' : ''} ${ok ? '' : 'disabled'}/> ${esc(l)}${ok ? '' : ' <small>(non supportato da questo motore)</small>'}</label>`;
  }).join('');
  const alt = $('#alternative');
  alt.disabled = !cap.alternatives;
  alt.checked = S.routeSettings.alternatives && cap.alternatives;
  alt.closest('label').classList.toggle('disattivo', !cap.alternatives);
  $('#nota-motore').textContent = cap.note || '';
  $('#nota-servizio').textContent = `${r.name}. ${cap.steps ? 'Fornisce le istruzioni svolta per svolta.' : 'Non fornisce istruzioni svolta per svolta.'} ${cap.alternatives ? 'Può proporre alternative.' : 'Non propone alternative.'}`;
  $('#campo-chiave').hidden = S.routeSettings.provider !== 'graphhopper';
  $('#campo-url').hidden = S.routeSettings.provider === 'graphhopper' || S.routeSettings.provider === 'local';
}

function renderWaypoints() {
  const n = S.waypoints.length;
  $('#tappe').innerHTML = S.waypoints.map((w, i) => {
    const isStart = i === 0, isEnd = i === n - 1;
    const name = isStart ? 'Partenza' : isEnd ? 'Arrivo' : `Tappa ${i}`;
    const color = isStart ? S.style.startColor : isEnd ? S.style.endColor : '#6554c0';
    const picking = S.pick?.type === 'waypoint' && S.pick.id === w.id;
    return `<li class="tappa" data-tappa="${w.id}">
      <span class="segno ${!isStart && !isEnd ? 'via' : ''}" style="--c:${color}" aria-hidden="true">${isStart ? 'A' : isEnd ? 'B' : i}</span>
      <input id="tp-${w.id}" type="text" value="${esc(w.label)}" placeholder="${name}: cerca o scegli sulla mappa" aria-label="${name}" data-tappa-input="${w.id}" />
      <button type="button" class="btn icona piccolo ${picking ? 'attivo' : ''}" data-tappa-mappa="${w.id}" aria-pressed="${picking}" data-tip="Scegli sulla mappa" aria-label="Scegli ${name.toLowerCase()} sulla mappa"><svg class="ic"><use href="#i-punto"/></svg></button>
      <button type="button" class="btn icona piccolo" data-tappa-togli="${w.id}" data-tip="${isStart || isEnd ? 'Svuota' : 'Rimuovi la tappa'}" aria-label="${isStart || isEnd ? 'Svuota' : 'Rimuovi'} ${name.toLowerCase()}"><svg class="ic"><use href="#i-chiudi"/></svg></button>
      <div class="risultati" hidden data-tappa-ris="${w.id}" style="position:static;grid-column:2/-1;margin-top:4px"></div>
    </li>`;
  }).join('');
}

function setWaypoint(w, coord, label) {
  w.coord = coord;
  w.label = label || coordLabel(coord);
  if (!label) reverseLabel(w);
  renderWaypoints();
  renderAll();
  autoRoute();
}

function updatePickUi() {
  renderWaypoints();
  $('#gen-punto-info').hidden = !(S.pick?.type === 'origin' || S.genOrigin || $('input[name="gen-origine"]:checked')?.value === 'punto');
  setMapCursor();
}

const autoRoute = debounce(() => {
  if (S.waypoints[0].coord && S.waypoints[S.waypoints.length - 1].coord) computeRoute();
}, 250);

async function computeRoute() {
  const pts = S.waypoints.filter((w) => w.coord).map((w) => w.coord);
  const out = $('#esito-percorso');
  if (!S.waypoints[0].coord || !S.waypoints[S.waypoints.length - 1].coord) {
    out.className = 'esito errore';
    out.textContent = 'Indica almeno la partenza e l\'arrivo.';
    return;
  }
  const r = getRouter();
  const mode = S.routeSettings.mode;
  if (!r.modes.includes(mode)) {
    out.className = 'esito errore';
    out.textContent = `${r.name} non supporta il mezzo scelto. Disponibili: ${r.modes.map((m) => MODES[m].toLowerCase()).join(', ')}.`;
    return;
  }
  if (r.requiresKey && !S.routeSettings.ghKey) {
    out.className = 'esito errore';
    out.textContent = 'GraphHopper richiede una chiave personale: inseriscila in «Motore di calcolo» oppure scegli Valhalla, gratuito e senza chiave.';
    $('#motore').closest('details').open = true;
    return;
  }
  const cap = r.capabilities(mode);
  const options = Object.fromEntries(Object.entries(S.routeSettings.options).filter(([k, v]) => v && cap.options[k]));
  routeCtrl?.abort();
  routeCtrl = new AbortController();
  const ctrl = routeCtrl;
  out.className = 'esito';
  out.textContent = `Calcolo in corso con ${r.name}…`;
  $('#b-calcola').disabled = true;
  try {
    const res = await r.route({ points: pts, mode, options, alternatives: S.routeSettings.alternatives && cap.alternatives, signal: ctrl.signal });
    if (ctrl !== routeCtrl) return;
    if (!res.routes.length) throw Object.assign(new Error('Nessun percorso trovato'), { status: 404 });
    S.routes = S.routes.filter((x) => x.kind !== 'computed' && x.kind !== 'alt');
    const nVia = pts.length - 2;
    res.routes.forEach((rt, i) => {
      addRoute({
        kind: i === 0 ? 'computed' : 'alt',
        name: i === 0 ? `Percorso ${MODES[mode].toLowerCase()}` : `Alternativa ${i}`,
        coords: rt.coords, distance: rt.distance, duration: rt.duration, steps: rt.steps, estimated: rt.estimated,
        ascent: rt.ascent, descent: rt.descent, elevations: rt.elevations, vias: nVia, source: r.attribution, mode,
      }, { silent: true, fit: false });
    });
    const main = S.routes.find((x) => x.kind === 'computed');
    out.className = 'esito ok';
    out.textContent = `Percorso calcolato: ${fmt.dist(main.distance)}, ${main.estimated ? 'circa ' : ''}${fmt.dur(main.duration)}.` + (res.routes.length === 2 ? ' Trovata anche 1 alternativa.' : res.routes.length > 2 ? ` Trovate anche ${res.routes.length - 1} alternative.` : '') + (res.notes.length ? ' ' + res.notes.join(' ') : '');
    renderRoutes();
    renderAll();
    fitTo(bboxOfCoords(main.coords));
    if (main.ascent == null) addElevationProfile(main);
  } catch (e) {
    if (isAbort(e)) return;
    out.className = 'esito errore';
    out.textContent = routingErrorMessage(e, r) + (r.id !== 'local' && S.roads.length ? ' In alternativa puoi usare il motore locale sulla rete caricata.' : '');
  } finally {
    if (ctrl === routeCtrl) $('#b-calcola').disabled = false;
  }
}

async function addElevationProfile(route) {
  try {
    const prof = await elevation.profile(route.coords);
    if (!prof) return;
    route.ascent = prof.ascent;
    route.descent = prof.descent;
    route.profile = prof;
    route.elevSource = elevation.attribution;
    renderRoutes();
  } catch {
    route.elevError = true;
    renderRoutes();
  }
}

function addRoute(data, { silent = false, fit = true } = {}) {
  const others = S.routes.filter((r) => r.kind === 'manual' || r.kind === 'import').length;
  let color, dashIndex = 0;
  if (data.kind === 'computed') color = S.style.routeColor;
  else if (data.kind === 'alt') { color = mixColor(S.style.routeColor, '#7a869a', 0.45); dashIndex = 1; }
  else { color = ROUTE_COLORS[(others + 1) % ROUTE_COLORS.length]; dashIndex = data.kind === 'manual' ? 2 : 3; }
  const r = { id: uid(), visible: true, color, dashIndex, main: data.kind === 'computed', kindLabel: { computed: 'Calcolato', alt: 'Alternativa', manual: 'Tracciato a mano', import: 'Importato' }[data.kind], ...data };
  if (r.elevations && r.ascent == null) {
    const st = profileStats(r.coords, r.elevations);
    if (st) { r.ascent = st.ascent; r.descent = st.descent; r.profile = st; r.elevSource = 'quote contenute nel file'; }
  }
  S.routes.push(r);
  if (!silent) {
    renderRoutes();
    renderAll();
    if (fit) fitTo(bboxOfCoords(r.coords));
  }
  return r;
}

function recolorMainRoute() {
  for (const r of S.routes) {
    if (r.kind === 'computed') r.color = S.style.routeColor;
    if (r.kind === 'alt') r.color = mixColor(S.style.routeColor, '#7a869a', 0.45);
  }
  S.creative.color = creativeColor(S.style.routeColor);
  renderRoutes();
}

function renderRoutes() {
  const box = $('#lista-percorsi');
  if (!S.routes.length) { box.innerHTML = '<p class="nota">Nessun percorso ancora.</p>'; updateExportUi(); return; }
  box.innerHTML = S.routes.map((r) => {
    const data = [];
    if (r.distance != null) data.push(`<span>${r.kind === 'manual' || r.kind === 'import' ? 'Lunghezza' : 'Distanza'} <b>${fmt.dist(r.distance)}</b></span>`);
    if (r.duration != null) data.push(`<span>Durata ${r.estimated ? 'stimata ' : ''}<b>${fmt.dur(r.duration)}</b></span>`);
    if (r.ascent != null) data.push(`<span>Salita <b>${fmt.int(r.ascent)} m</b></span><span>Discesa <b>${fmt.int(r.descent)} m</b></span>`);
    if (r.vias != null) data.push(`<span>Tappe intermedie <b>${r.vias}</b></span>`);
    const notes = [];
    if (r.kind === 'manual') notes.push('Linea disegnata a mano tra i punti: non è un itinerario calcolato sulle strade. Non ha durata.');
    if (r.kind === 'import' && r.duration == null) notes.push('Traccia importata: durata non disponibile nel file.');
    if (r.ascent != null && r.elevSource) notes.push(`Dislivello: ${esc(r.elevSource)}${r.elevSource.includes('modello') ? ', stima' : ''}.`);
    if (r.elevError) notes.push('Dislivello non disponibile: il servizio delle quote non ha risposto.');
    if (r.source) notes.push(esc(r.source));
    const steps = r.steps?.length ? `<details><summary>Istruzioni svolta per svolta (${r.steps.length})</summary><ol class="istruzioni">${r.steps.map((s, i) => `<li tabindex="0" data-passo="${r.id}:${i}">${esc(s.text)} ${s.distance ? `<span class="dist">${fmt.dist(s.distance)}</span>` : ''}</li>`).join('')}</ol></details>` : (r.kind === 'computed' || r.kind === 'alt') ? '<p class="nota">Il motore scelto non fornisce istruzioni svolta per svolta.</p>' : '';
    return `<div class="percorso ${r.visible ? '' : 'nascosto'}" style="--c:${r.color}">
      <div class="titolo"><span class="linea t${r.dashIndex}" aria-hidden="true"></span><span>${esc(r.name)}</span><span class="etichetta ${r.kind === 'manual' ? 'manuale' : ''}">${esc(r.kindLabel)}</span></div>
      <div class="dati-percorso">${data.join('')}</div>
      ${r.profile ? elevationProfileChart(r.profile) : ''}
      ${notes.length ? `<p class="nota">${notes.join(' ')}</p>` : ''}
      ${steps}
      <div class="comandi">
        <button type="button" class="btn piccolo" data-r-vis="${r.id}" aria-pressed="${r.visible}"><svg class="ic"><use href="#i-occhio"/></svg>${r.visible ? 'Nascondi' : 'Mostra'}</button>
        <button type="button" class="btn piccolo" data-r-fit="${r.id}"><svg class="ic"><use href="#i-inquadra"/></svg>Inquadra</button>
        ${r.kind === 'alt' ? `<button type="button" class="btn piccolo" data-r-main="${r.id}">Usa questa</button>` : ''}
        <button type="button" class="btn piccolo" data-r-gpx="${r.id}">GPX</button>
        <button type="button" class="btn piccolo" data-r-gj="${r.id}">GeoJSON</button>
        <button type="button" class="btn piccolo" data-r-del="${r.id}" aria-label="Elimina ${esc(r.name)}"><svg class="ic"><use href="#i-cestino"/></svg></button>
      </div>
    </div>`;
  }).join('');
  updateExportUi();
}

function setupRouting() {
  $('#mezzi').addEventListener('click', (e) => {
    const b = e.target.closest('[data-mezzo]');
    if (!b) return;
    if (b.getAttribute('aria-disabled') === 'true') {
      toast(`${router.name} non calcola percorsi «${MODES[b.dataset.mezzo].toLowerCase()}». Cambia motore in «Motore di calcolo».`, 'attenzione', 6000);
      return;
    }
    S.routeSettings.mode = b.dataset.mezzo;
    persist();
    renderModes();
    autoRoute();
  });
  $('#opzioni').addEventListener('change', (e) => {
    const k = e.target.dataset.opzione;
    if (!k) return;
    S.routeSettings.options[k] = e.target.checked;
    persist();
    autoRoute();
  });
  $('#alternative').addEventListener('change', (e) => { S.routeSettings.alternatives = e.target.checked; persist(); autoRoute(); });
  $('#motore').value = S.routeSettings.provider;
  $('#motore-url').value = S.routeSettings.customUrl || '';
  $('#motore-chiave').value = S.routeSettings.ghKey || '';
  $('#motore').addEventListener('change', (e) => {
    S.routeSettings.provider = e.target.value;
    S.routeSettings.customUrl = '';
    $('#motore-url').value = '';
    persist();
    renderModes();
    if (e.target.value === 'local' && !S.roads.length) toast('Il motore locale lavora sulla rete caricata: carica prima l\'area che contiene partenza e arrivo.', 'info', 7000);
  });
  $('#motore-url').addEventListener('change', (e) => {
    const v = e.target.value.trim();
    if (v && !/^https:\/\//.test(v)) { toast('Indica un indirizzo che inizia con https://', 'attenzione'); return; }
    S.routeSettings.customUrl = v;
    persist();
    renderModes();
  });
  $('#motore-chiave').addEventListener('change', (e) => {
    S.routeSettings.ghKey = e.target.value.trim();
    store.set('chiave-gh', S.routeSettings.ghKey);
  });

  $('#tappe').addEventListener('click', (e) => {
    const pick = e.target.closest('[data-tappa-mappa]');
    const del = e.target.closest('[data-tappa-togli]');
    const res = e.target.closest('[data-tappa-scelta]');
    if (pick) {
      const id = pick.dataset.tappaMappa;
      S.pick = S.pick?.id === id ? null : { type: 'waypoint', id };
      updatePickUi();
      if (S.pick) {
        toast('Clic sulla mappa per scegliere il punto.', 'info', 3000);
        if (window.innerWidth <= 860) { $('#pannello').classList.add('ridotto'); applyPanels(); }
      }
    } else if (del) {
      const i = S.waypoints.findIndex((w) => w.id === del.dataset.tappaTogli);
      if (i === 0 || i === S.waypoints.length - 1) { S.waypoints[i].coord = null; S.waypoints[i].label = ''; }
      else S.waypoints.splice(i, 1);
      S.routes = S.routes.filter((r) => r.kind !== 'computed' && r.kind !== 'alt');
      renderWaypoints(); renderRoutes(); renderAll(); autoRoute();
    } else if (res) {
      const [id, idx] = res.dataset.tappaScelta.split(':');
      const w = S.waypoints.find((x) => x.id === id);
      const r = w?._results?.[+idx];
      if (r) setWaypoint(w, r.center, r.name);
    }
  });
  $('#tappe').addEventListener('keydown', async (e) => {
    const input = e.target.closest('[data-tappa-input]');
    if (!input || e.key !== 'Enter') return;
    e.preventDefault();
    const w = S.waypoints.find((x) => x.id === input.dataset.tappaInput);
    const q = input.value.trim();
    if (!q || !w) return;
    const box = $(`[data-tappa-ris="${w.id}"]`);
    box.hidden = false;
    box.innerHTML = '<p class="nota" style="margin:6px">Ricerca in corso…</p>';
    try {
      const vb = S.area?.bbox;
      const list = await geocoder.search(vb ? q : q);
      w._results = list.slice(0, 6);
      box.innerHTML = list.length ? w._results.map((r, i) => `<button type="button" class="risultato" data-tappa-scelta="${w.id}:${i}"><strong>${esc(r.name)}<span class="tipo">${esc(r.typeLabel)}</span></strong><small>${esc(r.label)}</small></button>`).join('') : `<p class="nota" style="margin:6px">Nessun risultato per «${esc(q)}».</p>`;
      box.querySelector('.risultato')?.focus();
    } catch (err) {
      box.innerHTML = `<p class="esito errore" style="margin:6px">${geocodeErrorMessage(err)}</p>`;
    }
  });
  $('#b-aggiungi-tappa').onclick = () => {
    const w = { id: uid(), coord: null, label: '' };
    S.waypoints.splice(S.waypoints.length - 1, 0, w);
    S.pick = { type: 'waypoint', id: w.id };
    updatePickUi();
    $(`#tp-${w.id}`)?.focus();
    toast('Nuova tappa: cercala per nome oppure fai clic sulla mappa.', 'info', 4000);
  };
  $('#b-inverti').onclick = () => {
    S.waypoints.reverse();
    renderWaypoints(); renderAll(); autoRoute();
  };
  $('#b-svuota-tappe').onclick = () => {
    S.waypoints = [{ id: uid(), coord: null, label: '' }, { id: uid(), coord: null, label: '' }];
    S.routes = S.routes.filter((r) => r.kind !== 'computed' && r.kind !== 'alt');
    $('#esito-percorso').textContent = '';
    renderWaypoints(); renderRoutes(); renderAll();
  };
  $('#b-calcola').onclick = computeRoute;
  $('#b-disegna-percorso').onclick = () => startDraw('manual');
  $('#importa-traccia').addEventListener('change', async (e) => {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;
    if (file.size > 30 * 1024 * 1024) { toast('Il file supera 30 MB: troppo grande da mostrare.', 'errore'); return; }
    try {
      const text = await file.text();
      const tracks = /\.gpx$/i.test(file.name) || /<gpx[\s>]/.test(text.slice(0, 2000)) ? parseGpx(text) : parseGeoJsonTracks(text);
      let all = [];
      for (const t of tracks.slice(0, 20)) {
        const r = addRoute({ kind: 'import', name: t.name, coords: t.coords, elevations: t.elevations, distance: lineLength(t.coords) }, { silent: true });
        all = all.concat(r.coords);
      }
      renderRoutes(); renderAll(); fitTo(bboxOfCoords(all));
      toast(`Importate ${Math.min(20, tracks.length)} tracce da «${esc(file.name)}».${tracks.length > 20 ? ' Mostrate solo le prime 20.' : ''}`, 'ok', 5000);
    } catch (err) {
      toast(esc(err.message), 'errore', 8000);
    }
  });
  $('#lista-percorsi').addEventListener('click', (e) => {
    const get = (attr) => { const b = e.target.closest(`[${attr}]`); return b ? S.routes.find((r) => r.id === b.getAttribute(attr)) : null; };
    let r;
    if ((r = get('data-r-vis'))) { r.visible = !r.visible; renderRoutes(); renderAll(); }
    else if ((r = get('data-r-fit'))) fitTo(bboxOfCoords(r.coords));
    else if ((r = get('data-r-del'))) { S.routes = S.routes.filter((x) => x !== r); renderRoutes(); renderAll(); }
    else if ((r = get('data-r-gpx'))) exportRoutes([r], 'gpx');
    else if ((r = get('data-r-gj'))) exportRoutes([r], 'geojson');
    else if ((r = get('data-r-main'))) {
      const main = S.routes.find((x) => x.kind === 'computed');
      if (main) { Object.assign(main, { kind: 'alt', main: false, dashIndex: 1, kindLabel: 'Alternativa', name: r.name }); }
      Object.assign(r, { kind: 'computed', main: true, dashIndex: 0, kindLabel: 'Calcolato', name: main ? main.name.replace(/^Alternativa.*/, 'Percorso') : 'Percorso' });
      if (main) main.name = 'Alternativa';
      recolorMainRoute();
      S.routes.sort((a, b) => (b.kind === 'computed') - (a.kind === 'computed'));
      renderRoutes(); renderAll();
      if (r.ascent == null) addElevationProfile(r);
    }
    const step = e.target.closest('[data-passo]');
    if (step) focusStep(step.dataset.passo);
  });
  $('#lista-percorsi').addEventListener('keydown', (e) => {
    const step = e.target.closest('[data-passo]');
    if (step && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); focusStep(step.dataset.passo); }
  });
}

function focusStep(key) {
  const [id, i] = key.split(':');
  const r = S.routes.find((x) => x.id === id);
  const s = r?.steps?.[+i];
  if (!s?.coord) return;
  if (isGL) {
    view.setSource('steps', { type: 'Feature', geometry: { type: 'Point', coordinates: s.coord }, properties: {} });
    view.map.easeTo({ center: s.coord, zoom: Math.max(16, view.map.getZoom()), duration: reducedMotion.matches ? 0 : 500 });
  } else {
    view.fit([s.coord[0] - 0.002, s.coord[1] - 0.0015, s.coord[0] + 0.002, s.coord[1] + 0.0015]);
  }
}

function exportRoutes(routes, fmtName) {
  if (!routes.length) { toast('Nessun percorso da esportare.', 'attenzione'); return; }
  const base = slug(routes.length === 1 ? routes[0].name : `percorsi_${S.area?.name || ''}`);
  if (fmtName === 'gpx') {
    downloadBlob(new Blob([routesToGpx(routes, { name: routes[0].name, desc: 'Dati © contributori OpenStreetMap (ODbL)' })], { type: 'application/gpx+xml' }), `${base}.gpx`);
  } else {
    const gj = routesToGeoJson(routes);
    gj.attribuzione = 'Dati © contributori OpenStreetMap (ODbL)';
    downloadBlob(new Blob([JSON.stringify(gj)], { type: 'application/geo+json' }), `${base}.geojson`);
  }
}

/* =================== analisi =================== */

const updateAnalysisDebounced = debounce(() => updateAnalysis(), 300);

function updateAnalysis() {
  const box = $('#statistiche');
  if (!S.roads.length) {
    box.innerHTML = '<p class="nota">Carica un\'area per vedere le statistiche.</p>';
    $('#rosa').innerHTML = '';
    return;
  }
  const inView = $('input[name="ambito"]:checked').value === 'vista';
  const vb = inView ? view.bounds() : null;
  const st = networkStats(S.visibleRoads, vb);
  const otherCounts = LAYERS.filter((l) => l.key !== 'roads' && S.layers[l.key].on && S.layerData[l.key]).map((l) => `${l.label}: ${fmt.int(S.layerData[l.key].length)}`);
  const filtered = S.visibleRoads.length !== S.roads.length;
  box.innerHTML = `
    <div class="cifre">
      <div class="cifra"><b>${fmt.int(st.count)}</b><span>tratti di strada</span></div>
      <div class="cifra"><b>${fmt.km(st.total)}</b><span>lunghezza totale</span></div>
      <div class="cifra"><b>${fmt.int(st.streets)}</b><span>nomi di via diversi</span></div>
    </div>
    ${classBarChart(st, S.style)}
    <table><caption class="vh">Rete per tipo di strada</caption><thead><tr><th scope="col">Tipo</th><th scope="col" class="n">Tratti</th><th scope="col" class="n">km</th><th scope="col" class="n">%</th></tr></thead>
    <tbody>${st.rows.map((r) => `<tr><td>${esc(r.label)}</td><td class="n">${fmt.int(r.count)}</td><td class="n">${fmt.dec(r.len / 1000)}</td><td class="n">${fmt.dec((r.len / st.total) * 100)}</td></tr>`).join('')}</tbody></table>
    ${otherCounts.length ? `<p class="nota">Altri livelli caricati: ${esc(otherCounts.join(' · '))}.</p>` : ''}
    <p class="nota">${inView ? 'Calcolo parziale: solo le strade che toccano la vista attuale.' : 'Calcolo limitato all\'area caricata.'}${filtered ? ' Sono esclusi gli elementi nascosti dai filtri.' : ''} Le lunghezze sono misurate sulle geometrie OpenStreetMap: le strade a carreggiate separate sono disegnate come due linee e contano due volte.</p>`;
  const hist = orientationHistogram(S.visibleRoads, vb);
  $('#rosa').innerHTML = hist.total ? `${roseChart(hist)}<p class="nota">Ordine della griglia: <strong>${fmt.dec2(hist.order)}</strong> (0 = direzioni disperse, 1 = griglia perfettamente ortogonale). Settori pesati sulla lunghezza dei tratti.</p>` : '<p class="nota">Nessun tratto da analizzare.</p>';
}

function setupAnalysis() {
  $$('input[name="ambito"]').forEach((r) => r.addEventListener('change', updateAnalysis));
  $('#b-colora-orientamento').onclick = () => { S.style.colorMode = 'orientation'; syncBindings(); pushHistory(); changeColorMode('orientation'); toast('Colori per orientamento applicati: la legenda spiega la scala.', 'ok', 3000); };
  $('#b-colora-densita').onclick = () => {
    if (!S.roads.length) { toast('Carica prima un\'area.', 'attenzione'); return; }
    S.style.colorMode = 'density'; syncBindings(); pushHistory(); changeColorMode('density');
  };
  let elevCtrl = null;
  $('#b-quote').onclick = async () => {
    if (!S.roads.length) { toast('Carica prima un\'area.', 'attenzione'); return; }
    const max = +$('#quota-max').value;
    const sample = sampleMidpoints(S.roads, max);
    const out = $('#esito-quote');
    elevCtrl?.abort();
    elevCtrl = new AbortController();
    const p = showProgress('Leggo le quote del terreno');
    $('#b-annulla-carico').onclick = () => elevCtrl.abort();
    try {
      const pts = sample.map((s) => s.coord);
      const h = [];
      for (let i = 0; i < pts.length; i += elevation.maxPoints) {
        p.status(`${fmt.int(Math.min(pts.length, i + elevation.maxPoints))} di ${fmt.int(pts.length)} punti`);
        h.push(...(await elevation.heights(pts.slice(i, i + elevation.maxPoints), { signal: elevCtrl.signal })));
      }
      S.elev = new Map();
      sample.forEach((s, i) => { if (h[i] != null) S.elev.set(s.id, h[i]); });
      const vals = [...S.elev.values()].sort((a, b) => a - b);
      if (!vals.length) throw new Error('Nessuna quota disponibile per quest\'area.');
      S.elevRange = [vals[Math.floor(vals.length * 0.02)], vals[Math.floor(vals.length * 0.98)]];
      out.className = 'esito ok';
      out.textContent = `Quote lette per ${fmt.int(S.elev.size)} strade su ${fmt.int(S.roads.length)}${S.elev.size < S.roads.length ? ' (campione: le strade più lunghe)' : ''}. Da ${Math.round(vals[0])} a ${Math.round(vals[vals.length - 1])} m.`;
      $('#b-colora-quote').disabled = false;
    } catch (e) {
      out.className = 'esito errore';
      out.textContent = isAbort(e) ? 'Lettura delle quote annullata.' : `Quote non disponibili: ${esc(e.message || 'servizio non raggiungibile')}.`;
    } finally {
      p.done();
      $('#b-annulla-carico').onclick = cancelJobs;
    }
  };
  $('#b-colora-quote').onclick = () => { S.style.colorMode = 'elevation'; syncBindings(); pushHistory(); changeColorMode('elevation'); };
  $('#gen-n').addEventListener('input', (e) => { $('#out-gen-n').textContent = e.target.value; });
  $$('input[name="gen-origine"]').forEach((r) => r.addEventListener('change', () => {
    const punto = $('input[name="gen-origine"]:checked').value === 'punto';
    if (punto && !S.genOrigin) { S.pick = { type: 'origin' }; toast('Clic sulla mappa per scegliere il punto di partenza.', 'info', 4000); }
    if (!punto) { S.genOrigin = null; S.pick = null; renderAll(); }
    updatePickUi();
  }));
  $('#b-genera').onclick = () => {
    const out = $('#esito-genera');
    if (!S.roads.length) { out.className = 'esito errore'; out.textContent = 'Carica prima un\'area.'; return; }
    const punto = $('input[name="gen-origine"]:checked').value === 'punto';
    if (punto && !S.genOrigin) { S.pick = { type: 'origin' }; updatePickUi(); out.className = 'esito'; out.textContent = 'Scegli prima il punto di partenza sulla mappa.'; return; }
    out.className = 'esito';
    out.textContent = 'Calcolo dei percorsi…';
    $('#b-genera').disabled = true;
    setTimeout(() => {
      try {
        const t0 = performance.now();
        const res = generatePaths(S.visibleRoads, { mode: $('#gen-mezzo').value, count: +$('#gen-n').value, origin: punto ? S.genOrigin : null });
        S.creative = { paths: res.paths, color: creativeColor(S.style.routeColor) };
        out.className = res.paths.length ? 'esito ok' : 'esito errore';
        out.textContent = res.paths.length ? `${fmt.int(res.paths.length)} percorsi generati in ${fmt.dec((performance.now() - t0) / 1000)} s sulla rete visibile (calcolo locale, ${$('#gen-mezzo').selectedOptions[0].text.toLowerCase()}).` : res.reason;
        renderAll(true);
      } catch (e) {
        out.className = 'esito errore';
        out.textContent = 'Errore nel calcolo: ' + esc(e.message);
      } finally {
        $('#b-genera').disabled = false;
      }
    }, 30);
  };
  $('#b-cancella-generati').onclick = () => { S.creative.paths = []; $('#esito-genera').textContent = ''; renderAll(true); };
}

/* =================== esportazione =================== */

function exportFormat() { return $('input[name="formato"]:checked').value; }

function exportOptions() {
  return {
    size: $('#exp-dim').value,
    w: +$('#exp-w').value, h: +$('#exp-h').value,
    orientation: $('#exp-orient').value,
    background: $('#exp-sfondo').value,
    bg: S.style.bg,
    poster: $('#exp-cornice').checked,
    title: $('#exp-titolo').value.trim(),
    subtitle: $('#exp-sottotitolo').value.trim(),
    date: $('#exp-data').checked ? fmt.date() : '',
    legend: $('#exp-legenda').checked,
    lineScale: +$('#exp-linee').value,
    legendEntries: legendEntries(sceneModel(), S.style, S.layers, S.filters, colorExtra()),
    attribution: ['Dati © contributori OpenStreetMap (ODbL)', S.routes.find((r) => r.visible && r.source)?.source?.replace(/, © OpenStreetMap$/, '')].filter(Boolean).join(' · '),
  };
}

function exportBbox() {
  if ($('#exp-estensione').value === 'vista') return view.bounds();
  const feats = [];
  if (S.area) return S.area.bbox;
  for (const r of S.routes) feats.push({ geometry: { type: 'LineString', coordinates: r.coords } });
  return bboxOfFeatures(feats);
}

function exportGroups() {
  return buildScene(sceneModel(), S.style, S.layers, { ...colorExtra(), dimRoads: routeDimActive(), markers: markersData().filter((m) => m.kind !== 'posizione') });
}

function updateExportDefaults() {
  if (S.area && !$('#exp-titolo').dataset.toccato) $('#exp-titolo').value = S.area.name.replace(/ e dintorni$/, '');
  if (S.area && !$('#exp-sottotitolo').dataset.toccato) {
    const c = S.area.center || [(S.area.bbox[0] + S.area.bbox[2]) / 2, (S.area.bbox[1] + S.area.bbox[3]) / 2];
    $('#exp-sottotitolo').value = `${fmt.dec2(Math.abs(c[1]))}° ${c[1] >= 0 ? 'N' : 'S'} · ${fmt.dec2(Math.abs(c[0]))}° ${c[0] >= 0 ? 'E' : 'O'}`;
  }
}

function updateExportUi() {
  const hasData = S.roads.length > 0 || Object.values(S.layerData).some((x) => x?.length);
  const hasRoutes = S.routes.length > 0;
  const reasons = {
    png: hasData || hasRoutes ? null : 'Carica un\'area o un percorso prima di esportare l\'immagine.',
    svg: hasData || hasRoutes ? null : 'Carica un\'area o un percorso prima di esportare il disegno.',
    vista: isGL ? null : 'Non disponibile senza WebGL: usa PNG ad alta risoluzione.',
    geojson: hasData ? null : 'Non ci sono dati geografici caricati da esportare.',
    gpx: hasRoutes ? null : 'Il formato GPX richiede almeno un percorso (calcolato, tracciato o importato).',
    rgeojson: hasRoutes ? null : 'Non ci sono percorsi da esportare.',
  };
  for (const input of $$('input[name="formato"]')) {
    const why = reasons[input.value];
    input.disabled = !!why;
    input.closest('label').classList.toggle('disattivo', !!why);
    input.closest('label').title = why || '';
  }
  let cur = $('input[name="formato"]:checked');
  if (cur.disabled) {
    const first = $$('input[name="formato"]').find((i) => !i.disabled);
    if (first) { first.checked = true; cur = first; }
  }
  const f = cur.value;
  const nota = Object.entries(reasons).filter(([, v]) => v).map(([k, v]) => `${{ png: 'PNG', svg: 'SVG', vista: 'Istantanea', geojson: 'GeoJSON', gpx: 'GPX', rgeojson: 'Percorso GeoJSON' }[k]}: ${v}`);
  const desc = {
    png: 'Immagine ridisegnata dai dati, nitida a qualsiasi dimensione. La mappa di base non è inclusa per rispetto delle licenze delle tessere.',
    svg: 'Disegno vettoriale modificabile con Inkscape o Illustrator. La mappa di base non è inclusa.',
    vista: 'Copia esatta di ciò che vedi, mappa di base compresa, alla risoluzione dello schermo.',
    geojson: 'Strade visibili dopo i filtri e livelli attivi, con gli attributi OpenStreetMap disponibili.',
    gpx: 'Tutti i percorsi visibili, uno per traccia.',
    rgeojson: 'Tutti i percorsi visibili come linee, con distanza, durata e dislivello quando disponibili.',
  }[f];
  $('#nota-formato').innerHTML = esc(desc) + (nota.length ? `<br><br>${nota.map(esc).join('<br>')}` : '');
  $('#opzioni-immagine').hidden = !(f === 'png' || f === 'svg');
  $('#b-esporta').disabled = cur.disabled;
  $('#b-stampa').disabled = !(hasData || hasRoutes);
  $('#exp-pers').hidden = $('#exp-dim').value !== 'personalizzato';
  if (f === 'png' || f === 'svg') updatePreviewDebounced();
}

const updatePreviewDebounced = debounce(updatePreview, 150);
function updatePreview() {
  const cv = $('#anteprima');
  const bbox = exportBbox();
  if (!bbox) { cv.width = 300; cv.height = 150; cv.getContext('2d').clearRect(0, 0, 300, 150); return; }
  const o = exportOptions();
  const { W, H } = (() => { const t = renderCanvasSize(o); return t; })();
  const scale = 300 / Math.max(W, H);
  const preview = renderCanvas(exportGroups(), bbox, { ...o, size: 'personalizzato', w: Math.max(200, W * scale), h: Math.max(200, H * scale), orientation: 'libero', lineScale: o.lineScale });
  cv.width = preview.width; cv.height = preview.height;
  cv.getContext('2d').drawImage(preview, 0, 0);
  cv.setAttribute('aria-label', `Anteprima ${W} per ${H} pixel`);
}

function renderCanvasSize(o) {
  let { w, h } = o.size === 'personalizzato' ? { w: o.w, h: o.h } : SIZE_PRESETS[o.size];
  if ((o.orientation === 'orizzontale' && h > w) || (o.orientation === 'verticale' && w > h)) [w, h] = [h, w];
  if (o.orientation === 'quadrato') w = h = Math.max(w, h);
  return { W: w, H: h };
}

async function doExport() {
  const f = exportFormat();
  const out = $('#esito-esporta');
  const name = slug(S.area?.name || 'mappa');
  out.className = 'esito';
  try {
    if (f === 'png' || f === 'svg') {
      const bbox = exportBbox();
      if (!bbox) throw new Error('niente da esportare');
      out.textContent = 'Preparazione del file…';
      await new Promise((r) => setTimeout(r, 30));
      const o = exportOptions();
      if (f === 'png') {
        const c = renderCanvas(exportGroups(), bbox, o);
        const blob = await new Promise((res) => c.toBlob(res, 'image/png'));
        if (!blob) throw new Error('il browser non è riuscito a creare l\'immagine (dimensioni eccessive?)');
        downloadBlob(blob, `${name}.png`);
        out.textContent = `PNG di ${c.width} × ${c.height} pixel scaricato (${fmt.bytes(blob.size)}).`;
      } else {
        const svg = renderSvg(exportGroups(), bbox, o);
        const blob = new Blob([svg], { type: 'image/svg+xml' });
        downloadBlob(blob, `${name}.svg`);
        out.textContent = `SVG scaricato (${fmt.bytes(blob.size)}).`;
      }
    } else if (f === 'vista') {
      const url = view.snapshot();
      const blob = await (await fetch(url)).blob();
      downloadBlob(blob, `${name}_vista.png`);
      out.textContent = `Istantanea scaricata (${fmt.bytes(blob.size)}). Contiene la mappa di base: rispetta l'attribuzione indicata in basso a destra.`;
    } else if (f === 'geojson') {
      const feats = S.visibleRoads.map((r) => ({ type: 'Feature', properties: { osm_id: `way/${r.id}`, categoria: CLASS_BY_KEY[r.cls].label, ...r.tags, lunghezza_m: Math.round(r.len) }, geometry: { type: 'LineString', coordinates: r.coords } }));
      for (const l of LAYERS) if (l.key !== 'roads' && S.layers[l.key].on) for (const ft of S.layerData[l.key] || []) feats.push({ type: 'Feature', properties: { livello: l.label, ...ft.properties }, geometry: ft.geometry });
      const gj = { type: 'FeatureCollection', name: S.area?.name, attribuzione: 'Dati © contributori OpenStreetMap, licenza ODbL 1.0 (https://www.openstreetmap.org/copyright)', features: feats };
      const blob = new Blob([JSON.stringify(gj)], { type: 'application/geo+json' });
      downloadBlob(blob, `${name}.geojson`);
      out.textContent = `GeoJSON con ${fmt.int(feats.length)} elementi scaricato (${fmt.bytes(blob.size)}). I dati restano soggetti alla licenza ODbL.`;
    } else if (f === 'gpx' || f === 'rgeojson') {
      const vis = S.routes.filter((r) => r.visible);
      exportRoutes(vis.length ? vis : S.routes, f === 'gpx' ? 'gpx' : 'geojson');
      out.textContent = 'Percorsi scaricati.';
    }
    out.className = 'esito ok';
  } catch (e) {
    out.className = 'esito errore';
    out.textContent = 'Esportazione non riuscita: ' + esc(e.message);
  }
}

async function doPrint() {
  const bbox = exportBbox();
  if (!bbox) return;
  const o = { ...exportOptions(), poster: true };
  if (o.size === 'quadrato' || o.size === 'schermo' || o.size === 'social') o.size = 'a4';
  const c = renderCanvas(exportGroups(), bbox, o);
  const img = $('#img-stampa');
  img.src = c.toDataURL('image/png');
  img.alt = `Manifesto: ${o.title}`;
  await img.decode().catch(() => {});
  window.print();
}

function setupExport() {
  $('#exp-dim').innerHTML = Object.entries(SIZE_PRESETS).map(([k, v]) => `<option value="${k}">${esc(v.label)}</option>`).join('');
  $('#exp-dim').value = 'a4';
  $('#formati').addEventListener('change', updateExportUi);
  for (const id of ['exp-dim', 'exp-w', 'exp-h', 'exp-orient', 'exp-sfondo', 'exp-estensione', 'exp-cornice', 'exp-titolo', 'exp-sottotitolo', 'exp-data', 'exp-legenda', 'exp-linee']) {
    $('#' + id).addEventListener('input', (e) => {
      if (id === 'exp-titolo' || id === 'exp-sottotitolo') e.target.dataset.toccato = '1';
      if (id === 'exp-linee') $('#out-exp-linee').textContent = fmt.dec(+e.target.value);
      $('#exp-pers').hidden = $('#exp-dim').value !== 'personalizzato';
      updatePreviewDebounced();
    });
  }
  $('#b-esporta').onclick = doExport;
  $('#b-stampa').onclick = doPrint;
  $('#b-collegamento').onclick = copyLink;
  $('#b-salva-config').onclick = () => {
    const { ghKey, ...rs } = S.routeSettings;
    const cfg = { app: 'trame', versione: 1, area: compactArea(S.area), stile: S.style, livelli: S.layers, filtri: S.filters, motore: rs };
    downloadBlob(new Blob([JSON.stringify(cfg, null, 2)], { type: 'application/json' }), `configurazione_${slug(S.area?.name || 'trame')}.json`);
  };
  $('#apri-config').addEventListener('change', async (e) => {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;
    try {
      const cfg = JSON.parse(await file.text());
      if (cfg.app !== 'trame') throw new Error('il file non è una configurazione di Trame');
      applyConfig({ a: cfg.area, st: cfg.stile, ly: cfg.livelli, fl: cfg.filtri });
      if (cfg.motore) { S.routeSettings = { ...S.routeSettings, ...cfg.motore }; $('#motore').value = S.routeSettings.provider; renderModes(); }
      toast('Configurazione aperta.', 'ok', 3000);
    } catch (err) {
      toast('Impossibile aprire la configurazione: ' + esc(err.message), 'errore');
    }
  });
}

/* =================== collegamento condivisibile =================== */

function compactArea(a) {
  if (!a) return null;
  const r5 = (v) => Math.round(v * 1e5) / 1e5;
  const out = { k: a.kind, n: a.name, b: a.bbox.map(r5) };
  if (a.kind === 'relation') out.id = a.osmId;
  if (a.kind === 'polygon' || a.kind === 'relation') out.r = simplifyRing(a.ring, a.kind === 'polygon' ? 120 : 80).map((c) => c.map(r5));
  if (a.source) out.s = a.source;
  return out;
}

function expandArea(c) {
  if (!c) return null;
  const a = { kind: c.k, name: c.n, bbox: c.b, source: c.s || 'collegamento' };
  if (c.k === 'relation') a.osmId = c.id;
  if (c.r) a.ring = c.r;
  a.km2 = c.r && c.k === 'polygon' ? geometryAreaKm2({ type: 'Polygon', coordinates: [c.r] }) : c.r ? geometryAreaKm2({ type: 'Polygon', coordinates: [c.r] }) : bboxAreaKm2(c.b);
  if (a.source === 'punto') a.source = 'riquadro';
  return a;
}

function copyLink() {
  const state = { v: 1, a: compactArea(S.area), st: S.style, ly: S.layers, fl: S.filters };
  const url = `${location.origin}${location.pathname}#c=${encodeState(state)}`;
  const done = () => toast('Collegamento copiato. Ricrea luogo, livelli, filtri e stile; non contiene la tua posizione né i percorsi.', 'ok', 5000);
  if (navigator.clipboard?.writeText) navigator.clipboard.writeText(url).then(done, () => fallbackCopy(url, done));
  else fallbackCopy(url, done);
  history_replace(url);
}
function history_replace(url) { try { window.history.replaceState(null, '', url); } catch { /* ignorato */ } }
function fallbackCopy(text, done) {
  const ta = document.createElement('textarea');
  ta.value = text;
  document.body.appendChild(ta);
  ta.select();
  try { document.execCommand('copy'); done(); } catch { prompt('Copia questo collegamento:', text); }
  ta.remove();
}

function applyConfig(cfg) {
  if (cfg.st) S.style = { ...defaultStyle(), ...cfg.st };
  if (cfg.ly) S.layers = { ...defaultLayers(), ...cfg.ly };
  if (cfg.fl) S.filters = { ...defaultFilters(), ...cfg.fl, classes: { ...defaultFilters().classes, ...(cfg.fl.classes || {}) } };
  S.style.animate = S.style.animate && !reducedMotion.matches;
  syncBindings();
  renderLayerList();
  pushHistory();
  const a = expandArea(cfg.a);
  if (a) {
    $('#campo-cerca').value = a.name;
    setArea(a, { autoload: true });
  } else renderAll(true);
}

function readHash() {
  const m = /[#&]c=([A-Za-z0-9_-]+)/.exec(location.hash);
  if (!m) return null;
  try { return decodeState(m[1]); } catch { toast('Il collegamento non è valido o è incompleto.', 'errore'); return null; }
}

/* =================== posizione del dispositivo =================== */

function locate(asArea) {
  if (!('geolocation' in navigator)) { toast('Questo browser non può fornire la posizione.', 'errore'); return; }
  toast('Richiesta della posizione al dispositivo…', 'info', 3000);
  navigator.geolocation.getCurrentPosition((pos) => {
    const c = [pos.coords.longitude, pos.coords.latitude];
    S.position = c; // resta in memoria solo in questa pagina, mai salvata né condivisa
    renderAll();
    if (asArea) {
      const radius = +$('#raggio').value;
      const bbox = bboxAround(c, radius);
      setArea({ kind: 'bbox', name: 'Dintorni della mia posizione', bbox, center: c, radius, km2: bboxAreaKm2(bbox), source: 'punto' }, { autoload: false });
      afterAreaPicked();
    } else if (isGL) view.map.easeTo({ center: c, zoom: Math.max(15, view.map.getZoom()), duration: reducedMotion.matches ? 0 : 600 });
    else view.fit(bboxAround(c, 600));
  }, (err) => {
    const msg = { 1: 'Permesso di posizione negato. Puoi cercare il luogo per nome.', 2: 'Posizione non disponibile in questo momento.', 3: 'Il dispositivo non ha fornito la posizione in tempo.' }[err.code] || 'Posizione non disponibile.';
    toast(msg, 'attenzione', 6000);
  }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 });
}

/* =================== comandi generali =================== */

function setupControls() {
  $$('[data-modalita]').forEach((b) => b.addEventListener('click', () => setMode(b.dataset.modalita)));
  $('.modalita').addEventListener('keydown', (e) => {
    if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') { e.preventDefault(); const next = S.mode === 'arte' ? 'percorsi' : 'arte'; setMode(next); $(`[data-modalita="${next}"]`).focus(); }
  });
  $('#t-pannello').onclick = () => { S.ui.panel = !S.ui.panel; $('#pannello').classList.remove('ridotto'); applyPanels(); persist(); };
  $('#t-legenda').onclick = () => { S.ui.legend = !S.ui.legend; applyPanels(); persist(); };
  $('#t-etichette').onclick = () => { S.style.labels = !S.style.labels; syncBindings(); pushHistory(); renderAll(); };
  $('#t-base').onclick = () => {
    if (!isGL) { toast('La mappa di base richiede WebGL, non disponibile su questo dispositivo.', 'attenzione'); return; }
    S.style.backdrop = S.style.backdrop === 'solid' ? 'basemap' : 'solid';
    syncBindings(); pushHistory(); renderAll();
  };
  $('#t-tema').onclick = () => {
    const order = ['auto', 'light', 'dark'];
    S.ui.theme = order[(order.indexOf(S.ui.theme) + 1) % 3];
    applyTheme();
    toast(`Tema dell'interfaccia: ${{ auto: 'come il sistema', light: 'chiaro', dark: 'scuro' }[S.ui.theme]}.`, 'info', 2000);
    persist();
    renderAll();
  };
  $('.maniglia').onclick = () => { $('#pannello').classList.toggle('ridotto'); applyPanels(); };

  $('#m-piu').onclick = () => (isGL ? view.map.zoomIn() : view.zoomBy(1.6));
  $('#m-meno').onclick = () => (isGL ? view.map.zoomOut() : view.zoomBy(1 / 1.6));
  $('#m-inquadra').onclick = () => {
    const main = S.mode === 'percorsi' && S.routes.find((r) => r.visible);
    if (main) fitTo(bboxOfCoords(main.coords));
    else if (S.area) fitTo(S.area.bbox);
    else toast('Nessuna area da inquadrare.', 'info', 3000);
  };
  $('#m-nord').onclick = () => (isGL ? view.map.easeTo({ bearing: 0, duration: reducedMotion.matches ? 0 : 400 }) : toast('La vista semplificata è sempre orientata a nord.', 'info', 3000));
  $('#m-annulla').onclick = undo;
  $('#m-ripristina').onclick = redo;
  $('#m-posizione').onclick = () => locate(false);
  $('#b-posizione-area').onclick = () => locate(true);

  $('#b-area-vista').onclick = () => {
    const b = view.bounds();
    setArea({ kind: 'bbox', name: 'Vista selezionata', bbox: b, km2: bboxAreaKm2(b), source: 'vista' }, { fit: false });
    afterAreaPicked();
  };
  $('#b-area-riquadro').onclick = () => startDraw('bbox');
  $('#b-area-poligono').onclick = () => startDraw('polygon');
  // Se l'area è già caricata, «Ricarica» scarica di nuovo ignorando la memoria locale.
  $('#b-carica').onclick = () => loadArea({ force: !!S.area && S.loadedFor.roads === areaKey(S.area) });
  $('#raggio').addEventListener('change', () => {
    const a = S.area;
    if (a?.source !== 'punto' || !a.center) return;
    const radius = +$('#raggio').value;
    const bbox = bboxAround(a.center, radius);
    setArea({ ...a, bbox, radius, km2: bboxAreaKm2(bbox) });
  });
  $('#recenti').addEventListener('click', (e) => {
    const b = e.target.closest('[data-recente]');
    if (b) chooseRecent(store.get('recenti', [])[+b.dataset.recente]);
    if (e.target.id === 'b-svuota-recenti') { store.remove('recenti'); renderRecents(); }
  });
  $('#b-fine-disegno').onclick = finishDraw;
  $('#b-annulla-disegno').onclick = () => { endDraw(); toast('Disegno annullato.', 'info', 2000); };
  $('#b-annulla-carico').onclick = cancelJobs;


  document.addEventListener('keydown', (e) => {
    const typing = /INPUT|TEXTAREA|SELECT/.test(document.activeElement?.tagName) && document.activeElement.type !== 'checkbox' && document.activeElement.type !== 'radio' && document.activeElement.type !== 'range';
    if (e.key === 'Escape') {
      if (S.draw) { endDraw(); return; }
      if (S.pick) { S.pick = null; updatePickUi(); return; }
      if (!$('#scheda').hidden) { closeCard(); return; }
    }
    if (typing) return;
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z' && !e.shiftKey) { e.preventDefault(); undo(); }
    else if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === 'y' || (e.key.toLowerCase() === 'z' && e.shiftKey))) { e.preventDefault(); redo(); }
    else if (e.key === '/') { e.preventDefault(); $('#campo-cerca').focus(); }
  });
  window.addEventListener('resize', debounce(() => { applyPanels(); }, 200));
  reducedMotion.addEventListener?.('change', () => { if (reducedMotion.matches) { S.style.animate = false; syncBindings(); renderAll(); } });
  matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', () => { applyTheme(); renderAll(); });
  window.addEventListener('afterprint', () => { $('#img-stampa').removeAttribute('src'); });
}

/* =================== avvio =================== */

function afterViewReady() {
  const fromLink = readHash();
  if (fromLink) applyConfig(fromLink);
  updateStatus();
}

function init() {
  applyTheme();
  selectTab = setupTabs();
  setupSearch();
  setupBindings();
  setupLayerList();
  setupFilters();
  setupStyle();
  setupRouting();
  setupAnalysis();
  setupExport();
  setupControls();
  syncBindings();
  renderLayerList();
  renderRecents();
  renderModes();
  renderWaypoints();
  renderRoutes();
  updateExportUi();
  if (reducedMotion.matches) $('#nota-movimento').hidden = false;
  history.stack = [snapshot()];
  history.index = 0;
  updateHistoryButtons();
  initView();
}

try {
  init();
} catch (e) {
  console.error(e);
  document.body.insertAdjacentHTML('beforeend', `<div class="noscript" role="alert">Si è verificato un errore all'avvio: ${esc(e.message)}. Ricarica la pagina; se il problema continua prova con un altro browser.</div>`);
}
