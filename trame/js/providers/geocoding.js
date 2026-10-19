// Adattatori di geocodifica. Ognuno espone search() e reverse() con lo stesso formato.
import { CONFIG } from '../config.js';
import { fetchJson, makeThrottle, HttpError } from '../util.js';

const TYPE_LABELS = {
  city: 'città', town: 'cittadina', village: 'paese', hamlet: 'frazione', municipality: 'comune',
  county: 'provincia', state: 'regione', region: 'regione', country: 'stato', suburb: 'quartiere',
  quarter: 'quartiere', neighbourhood: 'rione', city_district: 'circoscrizione', road: 'strada',
  house: 'indirizzo', building: 'edificio', postcode: 'codice postale', locality: 'località',
  isolated_dwelling: 'casa isolata', farm: 'fattoria', island: 'isola', peak: 'cima', amenity: 'servizio',
  shop: 'negozio', tourism: 'luogo turistico', railway: 'ferrovia', leisure: 'svago', place: 'luogo',
  administrative: 'area amministrativa', street: 'strada', district: 'distretto', other: 'luogo',
};
const typeLabel = (t) => TYPE_LABELS[t] || t || 'luogo';

function nominatimAdapter(cfg) {
  const throttle = makeThrottle(cfg.minInterval);
  const base = cfg.url.replace(/\/$/, '');
  return {
    id: 'nominatim',
    name: cfg.name,
    attribution: cfg.attribution,
    autocomplete: false,
    async search(q, { signal } = {}) {
      const params = new URLSearchParams({
        q, format: 'jsonv2', limit: '8', polygon_geojson: '1', polygon_threshold: '0.0003',
        'accept-language': 'it', addressdetails: '0',
      });
      const data = await throttle(() => fetchJson(`${base}/search?${params}`, { signal, timeout: 20000 }));
      return data.map((r) => {
        const bb = r.boundingbox?.map(Number); // s, n, w, e
        const hasArea = r.geojson && (r.geojson.type === 'Polygon' || r.geojson.type === 'MultiPolygon');
        return {
          id: `${r.osm_type}/${r.osm_id}`,
          name: r.name || r.display_name.split(',')[0],
          label: r.display_name,
          type: r.addresstype || r.type,
          typeLabel: typeLabel(r.addresstype || r.type),
          center: [Number(r.lon), Number(r.lat)],
          bbox: bb ? [bb[2], bb[0], bb[3], bb[1]] : null,
          osm: { type: r.osm_type, id: r.osm_id },
          geojson: hasArea ? r.geojson : null,
        };
      });
    },
    async reverse([lon, lat], { signal } = {}) {
      const params = new URLSearchParams({ lat, lon, format: 'jsonv2', zoom: '17', 'accept-language': 'it' });
      const r = await throttle(() => fetchJson(`${base}/reverse?${params}`, { signal, timeout: 15000 }));
      if (r.error) return null;
      const a = r.address || {};
      const road = a.road || a.pedestrian || a.footway || a.path || r.name;
      const place = a.village || a.town || a.city || a.hamlet || a.suburb;
      return [road && `${road}${a.house_number ? ' ' + a.house_number : ''}`, place].filter(Boolean).join(', ') || r.display_name;
    },
  };
}

function photonAdapter(cfg) {
  const throttle = makeThrottle(cfg.minInterval);
  const base = cfg.url.replace(/\/$/, '');
  return {
    id: 'photon',
    name: cfg.name,
    attribution: cfg.attribution,
    autocomplete: true,
    async search(q, { signal } = {}) {
      const params = new URLSearchParams({ q, limit: '8' });
      const data = await throttle(() => fetchJson(`${base}/api?${params}`, { signal, timeout: 15000 }));
      return (data.features || []).map((f) => {
        const p = f.properties;
        const ext = p.extent; // w, n, e, s
        const parts = [p.name, p.street && `${p.street}${p.housenumber ? ' ' + p.housenumber : ''}`, p.city, p.county, p.state, p.country];
        return {
          id: `${p.osm_type}/${p.osm_id}`,
          name: p.name || p.street || p.city,
          label: [...new Set(parts.filter(Boolean))].join(', '),
          type: p.type || p.osm_value,
          typeLabel: typeLabel(p.type || p.osm_value),
          center: f.geometry.coordinates,
          bbox: ext ? [ext[0], ext[3], ext[2], ext[1]] : null,
          osm: { type: { N: 'node', W: 'way', R: 'relation' }[p.osm_type] || p.osm_type, id: p.osm_id },
          geojson: null,
        };
      });
    },
    async reverse([lon, lat], { signal } = {}) {
      const data = await throttle(() => fetchJson(`${base}/reverse?lat=${lat}&lon=${lon}`, { signal, timeout: 15000 }));
      const p = data.features?.[0]?.properties;
      if (!p) return null;
      return [p.street && `${p.street}${p.housenumber ? ' ' + p.housenumber : ''}`, p.name, p.city].filter(Boolean).join(', ');
    },
  };
}

export function createGeocoder(id = 'nominatim') {
  if (id === 'photon') return photonAdapter(CONFIG.geocoding.photon);
  return nominatimAdapter(CONFIG.geocoding.nominatim);
}

export function geocodeErrorMessage(e) {
  if (e instanceof HttpError) {
    if (e.status === 429) return 'Il servizio di ricerca ha ricevuto troppe richieste. Attendi qualche secondo e riprova.';
    if (e.status === 408) return 'Il servizio di ricerca non ha risposto in tempo. Riprova.';
    return `Il servizio di ricerca ha risposto con un errore (${e.status}).`;
  }
  return 'Impossibile raggiungere il servizio di ricerca. Controlla la connessione e riprova.';
}
