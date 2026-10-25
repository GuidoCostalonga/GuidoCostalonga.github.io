/* Atlante dei dati: viste Mappa, Bolle, Linee, Barre, Tabella e Confronta.
   Tre famiglie di dati: Banca Mondiale (wb), regioni Eurostat NUTS (eu), archivi parziali FAOSTAT e OCSE (arc). */
(function () {
  'use strict';
  document.documentElement.lang = 'it';

  const $ = (s) => document.querySelector(s);
  const VISTE = ['mappa', 'bolle', 'linee', 'barre', 'tabella', 'confronta'];
  const RITARDO_MAX = 10;
  const PREDEFINITI = {
    ind: 'SP.DYN.LE00.IN', indX: 'SP.DYN.TFRT.IN', indR: 'SP.POP.TOTL', sel: ['ITA', 'CHN', 'IND', 'USA', 'NGA', 'BRA'],
    eu: { ind: 'EU_QUOTA_Y_GE65', indX: 'EU_TOTFERRT', indR: 'EU_POP_T_TOTAL' },
  };
  const LIVELLI = ['Paesi (NUTS 0)', 'Macroregioni (NUTS 1)', 'Regioni (NUTS 2)', 'Province e aree (NUTS 3)'];
  const AREE = {
    mondo: { n: 'Mondo' },
    europa: { n: 'Europa', b: [-25, 34, 45, 71] },
    africa: { n: 'Africa', b: [-19, -36, 53, 38] },
    asia: { n: 'Asia', b: [26, -11, 150, 56] },
    nordamerica: { n: 'America settentrionale e centrale', b: [-168, 7, -52, 72] },
    sudamerica: { n: 'America meridionale', b: [-92, -56, -33, 13] },
    oceania: { n: 'Oceania', b: [110, -48, 180, 0] },
  };
  const MESI = ['gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno', 'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre'];
  const movimentoRidotto = matchMedia('(prefers-reduced-motion: reduce)');

  const S = {
    vista: 'mappa', ind: PREDEFINITI.ind, indX: PREDEFINITI.indX, indR: PREDEFINITI.indR,
    anno: 2024, sel: PREDEFINITI.sel.slice(), regione: null, ultimo: false, aggregati: false,
    area: 'mondo', livello: 2, paeseEU: '', temaConf: 'sal',
    log: {}, ordineBarre: 'desc', tutteBarre: false, ordTab: { col: 'v', dir: 'desc' }, inRiproduzione: false,
  };

  let META, T = {}, PAESI = [], IND = {}, REG = {}, ARC = {}, NUTS0 = {}, colori = {}, geo = null, geoNuts = null;
  const cache = {}, cacheNote = {}, notaRisolta = {}, statistiche = {}, cacheArchivi = {};
  let vista = null;

  /* ---------- memoria locale (facoltativa) ---------- */
  const CHIAVI = ['ind', 'indX', 'indR', 'sel', 'ultimo', 'aggregati', 'area', 'livello', 'paeseEU', 'temaConf'];
  function leggiStato() {
    try {
      const s = JSON.parse(localStorage.getItem('atlante-dati') || 'null');
      if (s) for (const k of CHIAVI) if (s[k] !== undefined) S[k] = s[k];
    } catch (e) { /* memoria non disponibile */ }
  }
  function salvaStato() {
    try { const o = {}; CHIAVI.forEach((k) => { o[k] = S[k]; }); localStorage.setItem('atlante-dati', JSON.stringify(o)); } catch (e) { /* ignora */ }
  }

  /* ---------- formati ---------- */
  const nf = (o) => new Intl.NumberFormat('it-IT', o);
  const F = { int: nf({ maximumFractionDigits: 0 }), d1: nf({ maximumFractionDigits: 1 }), d2: nf({ maximumFractionDigits: 2 }), s3: nf({ maximumSignificantDigits: 3 }) };
  function fmt(v) {
    if (v == null || Number.isNaN(v)) return 'n.d.';
    const a = Math.abs(v);
    if (a >= 1e9) { const t = F.s3.format(v / 1e9); return t + (t === '1' ? ' miliardo' : ' miliardi'); }
    if (a >= 1e6) { const t = F.s3.format(v / 1e6); return t + (t === '1' ? ' milione' : ' milioni'); }
    if (a >= 1000) return F.int.format(v);
    if (a >= 10) return F.d1.format(v);
    if (a >= 1) return F.d2.format(v);
    if (a === 0) return '0';
    return F.s3.format(v);
  }
  function fmtAsse(v) {
    const a = Math.abs(v);
    if (a >= 1e9) return F.s3.format(v / 1e9) + ' mld';
    if (a >= 1e6) return F.s3.format(v / 1e6) + ' mln';
    if (a >= 1000) return F.int.format(v);
    return F.s3.format(v);
  }
  function data(s) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s || '');
    return m ? `${+m[3]} ${MESI[+m[2] - 1]} ${m[1]}` : (s || 'n.d.');
  }
  const norm = (s) => (s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  /* ---------- colori dal tema ---------- */
  function leggiColori() {
    const cs = getComputedStyle(document.documentElement);
    const g = (n) => cs.getPropertyValue(n).trim();
    colori = { seq0: g('--seq-0'), seq1: g('--seq-1'), grafite: g('--grafite'), carta: g('--carta'), vuoto: g('--vuoto'), trama: g('--vuoto-trama'), altro: g('--carta-3'), neutro: g('--grafite-3') };
    META.regioni.forEach((r, i) => { colori[r.k] = g('--r' + (i + 1)); });
  }
  function coloreRegione(id) {
    const t = T[id]; if (!t) return colori.grafite;
    if (t.r) return colori[t.r];
    return t.k === 'agg' || t.k === 'agg_eu' ? colori.grafite : colori.neutro;
  }
  const eAggregato = (id) => T[id] && (T[id].k === 'agg' || T[id].k === 'agg_eu');

  /* ---------- famiglie e territori ---------- */
  const fam = (cod) => (IND[cod] ? IND[cod].fam : 'wb');
  const famTerr = (cod) => (fam(cod) === 'eu' ? 'nuts' : 'paesi');
  function universo(cod) {
    const f = fam(cod);
    if (f === 'eu') return META.territori.filter((t) => t.k === 'nuts' && t.l === S.livello && (!S.paeseEU || t.p === S.paeseEU));
    if (f === 'arc') return IND[cod].arch.terr.map((id) => T[id]).filter(Boolean);
    return PAESI;
  }
  // traduce un territorio selezionato nel territorio equivalente della famiglia dell'indicatore
  function idPer(cod, id) {
    const t = T[id]; if (!t) return null;
    if (fam(cod) === 'eu') {
      if (t.k === 'nuts' || t.k === 'agg_eu') return id;
      if (t.k === 'paese') return NUTS0[id] || null;
      if (id === 'EUU') return 'EU:EU27_2020';
      return null;
    }
    if (t.k === 'nuts') return t.l === 0 ? t.p : null;
    if (t.k === 'agg_eu') return t.cod === 'EU27_2020' ? 'EUU' : null;
    return id;
  }
  const selezionato = (cod, id) => S.sel.some((s) => idPer(cod, s) === id);
  function descriviTerritorio(t) {
    if (t.k === 'nuts') return `NUTS ${t.l}${t.l ? ' · ' + (T[t.p] ? T[t.p].n : t.p) : ''}`;
    if (t.k === 'agg') return 'aggregato Banca Mondiale';
    if (t.k === 'agg_eu') return 'aggregato Eurostat';
    if (t.k === 'storico') return 'codice FAOSTAT';
    return t.r ? REG[t.r].n : 'senza regione';
  }

  /* ---------- dati ---------- */
  function percorso(cod) { return fam(cod) === 'eu' ? `dati/eurostat/${cod}.json` : `dati/indicatori/${cod}.json`; }
  async function carica(cod) {
    if (!cache[cod]) cache[cod] = fetch(percorso(cod)).then((r) => { if (!r.ok) throw new Error(cod); return r.json(); });
    return cache[cod];
  }
  function stat(cod) {
    const chiave = fam(cod) === 'eu' ? `${cod}|${S.livello}|${S.paeseEU}` : cod;
    if (!statistiche[chiave]) statistiche[chiave] = calcolaStatistiche(cache[cod + '_r'], universo(cod));
    return statistiche[chiave];
  }
  function caricaNote(cod) {
    if (!IND[cod] || !IND[cod].note) return Promise.resolve(null);
    if (!cacheNote[cod]) cacheNote[cod] = fetch(`dati/note/${cod}.json`).then((r) => (r.ok ? r.json() : null)).catch(() => null).then((n) => { notaRisolta[cod] = n; return n; });
    return cacheNote[cod];
  }
  function calcolaStatistiche(d, univ) {
    const vals = [], perAnno = new Array(d.y1 - d.y0 + 1).fill(0);
    for (const p of univ) {
      const a = d.d[p.id]; if (!a) continue;
      a.forEach((v, i) => { if (v != null) { vals.push(v); perAnno[i]++; } });
    }
    if (!vals.length) return { min: 0, max: 1, p02: 0, p98: 1, positivi: false, perAnno, logAuto: false, vuoto: true };
    vals.sort((x, y) => x - y);
    const q = (p) => vals[Math.min(vals.length - 1, Math.max(0, Math.round(p * (vals.length - 1))))];
    const min = vals[0], max = vals[vals.length - 1];
    const positivi = min > 0;
    return { min, max: max === min ? min + 1 : max, p02: q(0.02), p98: q(0.98), positivi, perAnno, logAuto: positivi && q(0.95) / q(0.05) > 150 };
  }
  function valore(d, id, anno) {
    const a = d && id && d.d[id]; if (!a) return null;
    const i = anno - d.y0;
    if (i >= 0 && i < a.length && a[i] != null) return { v: a[i], y: anno };
    if (S.ultimo) for (let k = 1; k <= RITARDO_MAX; k++) { const j = i - k; if (j >= 0 && j < a.length && a[j] != null) return { v: a[j], y: anno - k }; }
    return null;
  }
  function usaLog(cod) {
    const st = stat(cod); if (!st || !st.positivi) return false;
    return S.log[cod] != null ? S.log[cod] : st.logAuto;
  }
  // archivi parziali: un file per dataset, una serie diventa un indicatore con codice A:file:indice
  async function caricaArchivio(f) {
    if (!cacheArchivi[f]) cacheArchivi[f] = fetch(`dati/archivi/${f}.json`).then((r) => { if (!r.ok) throw new Error(f); return r.json(); });
    return cacheArchivi[f];
  }
  async function registraSerie(id) {
    if (IND[id]) return true;
    const m = /^A:([^:]+):(\d+)$/.exec(id); if (!m || !ARC[m[1]]) return false;
    const a = await caricaArchivio(m[1]); const s = a.s[+m[2]]; if (!s) return false;
    const c = ARC[m[1]];
    IND[id] = { c: id, fam: 'arc', t: 'arc', n: s.l, u: s.u || 'unità non indicata', en: `${c.en}: ${s.l}`, y0: s.y0, y1: s.y1, arch: c, interi: s.i, lotto: c.lotto, agg: c.agg, url: c.url };
    cache[id] = Promise.resolve({ c: id, y0: s.y0, y1: s.y1, d: s.d });
    return true;
  }

  /* ---------- avvio ---------- */
  async function avvia() {
    $('#carico').hidden = false;
    try {
      [META, geo, geoNuts] = await Promise.all(['dati/meta.json', 'dati/mondo.topo.json', 'dati/nuts.topo.json'].map((u) => fetch(u).then((r) => r.json())));
    } catch (e) {
      $('#carico').textContent = 'Impossibile leggere i file di dati. Apri il sito da un server web, non direttamente dal disco.';
      return;
    }
    META.territori.forEach((t) => { T[t.id] = t; if (t.k === 'nuts' && t.l === 0) NUTS0[t.p] = t.id; });
    PAESI = META.territori.filter((t) => t.k === 'paese');
    META.indicatori.forEach((i) => { IND[i.c] = Object.assign({ fam: 'wb' }, i); });
    META.regioni.forEach((r, i) => { REG[r.k] = Object.assign({ i }, r); });
    (META.archivi || []).forEach((a) => { ARC[a.f] = a; });
    leggiStato();
    S.sel = S.sel.filter((id) => T[id]);
    for (const k of ['ind', 'indX', 'indR']) {
      if (String(S[k]).startsWith('A:')) { try { if (!(await registraSerie(S[k]))) S[k] = PREDEFINITI[k]; } catch (e) { S[k] = PREDEFINITI[k]; } }
      if (!IND[S[k]]) S[k] = PREDEFINITI[k];
    }
    if (!AREE[S.area]) S.area = 'mondo';
    const h = location.hash.replace('#', '');
    if (VISTE.includes(h)) S.vista = h;
    leggiColori();
    preparaControlli();
    await cambia();
    $('#carico').hidden = true;
    const ro = new ResizeObserver(debounce(() => cambia(), 150));
    ro.observe($('#scena'));
    const ricolora = () => { leggiColori(); cambia(); };
    matchMedia('(prefers-color-scheme: dark)').addEventListener('change', ricolora);
    new MutationObserver(ricolora).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  }
  function debounce(f, ms) { let t; return () => { clearTimeout(t); t = setTimeout(f, ms); }; }

  /* ---------- controlli ---------- */
  function riempiSelect(sel, valore, filtro) {
    sel.innerHTML = '';
    const temi = Object.entries(META.temi);
    for (const [k, nome] of temi) {
      const voci = Object.values(IND).filter((i) => i.t === k && (!filtro || filtro(i))).sort((a, b) => a.n.localeCompare(b.n, 'it'));
      if (!voci.length) continue;
      const g = document.createElement('optgroup'); g.label = nome;
      voci.forEach((i) => { const o = document.createElement('option'); o.value = i.c; o.textContent = `${i.n} (${i.u})`; g.appendChild(o); });
      sel.appendChild(g);
    }
    const arc = Object.values(IND).filter((i) => i.fam === 'arc' && (!filtro || filtro(i)));
    if (arc.length) {
      const g = document.createElement('optgroup'); g.label = 'Serie aperte dagli archivi parziali';
      arc.forEach((i) => { const o = document.createElement('option'); o.value = i.c; o.textContent = `${i.n} (${i.u})`; g.appendChild(o); });
      sel.appendChild(g);
    }
    sel.value = valore;
  }
  function riempiSelezioni() {
    const ft = famTerr(S.ind);
    riempiSelect($('#ind-principale'), S.ind);
    riempiSelect($('#ind-x'), S.indX, (i) => (i.fam === 'eu' ? 'nuts' : 'paesi') === ft);
    riempiSelect($('#ind-r'), S.indR, (i) => (i.fam === 'eu' ? 'nuts' : 'paesi') === ft);
  }
  function allineaFamiglie() {
    // le bolle richiedono indicatori sullo stesso tipo di territorio
    const ft = famTerr(S.ind);
    if (famTerr(S.indX) !== ft) S.indX = ft === 'nuts' ? PREDEFINITI.eu.indX : PREDEFINITI.indX;
    if (famTerr(S.indR) !== ft) S.indR = ft === 'nuts' ? PREDEFINITI.eu.indR : PREDEFINITI.indR;
    if (S.indX === S.ind) S.indX = ft === 'nuts' ? (S.ind === PREDEFINITI.eu.indX ? PREDEFINITI.eu.ind : PREDEFINITI.eu.indX) : (S.ind === PREDEFINITI.indX ? PREDEFINITI.ind : PREDEFINITI.indX);
  }
  function preparaControlli() {
    allineaFamiglie();
    riempiSelezioni();
    $('#ind-principale').addEventListener('change', (e) => { S.ind = e.target.value; allineaFamiglie(); salvaStato(); cambia(); });
    $('#ind-x').addEventListener('change', (e) => { S.indX = e.target.value; salvaStato(); cambia(); });
    $('#ind-r').addEventListener('change', (e) => { S.indR = e.target.value; salvaStato(); cambia(); });
    $('#opt-ultimo').checked = S.ultimo;
    $('#opt-aggregati').checked = S.aggregati;
    $('#opt-ultimo').addEventListener('change', (e) => { S.ultimo = e.target.checked; salvaStato(); cambia(); });
    $('#opt-aggregati').addEventListener('change', (e) => { S.aggregati = e.target.checked; salvaStato(); cambia(); });
    $('#opt-log').addEventListener('change', (e) => { S.log[codiceLog()] = e.target.checked; cambia(); });

    // area della mappa
    const area = $('#area-mappa');
    Object.entries(AREE).forEach(([k, a]) => { const o = document.createElement('option'); o.value = k; o.textContent = a.n; area.appendChild(o); });
    area.value = S.area;
    area.addEventListener('change', () => { S.area = area.value; salvaStato(); cambia(); });
    // livello e paese per i dati regionali
    const liv = $('#livello-nuts');
    LIVELLI.forEach((n, i) => { const o = document.createElement('option'); o.value = i; o.textContent = n; liv.appendChild(o); });
    liv.value = S.livello;
    liv.addEventListener('change', () => { S.livello = +liv.value; salvaStato(); cambia(); });
    const pe = $('#paese-nuts');
    const paesiEU = [...new Set(META.territori.filter((t) => t.k === 'nuts').map((t) => t.p))].filter((p) => T[p]).sort((a, b) => T[a].n.localeCompare(T[b].n, 'it'));
    pe.innerHTML = '<option value="">Tutti i paesi</option>' + paesiEU.map((p) => `<option value="${p}">${esc(T[p].n)}</option>`).join('');
    pe.value = S.paeseEU;
    pe.addEventListener('change', () => { S.paeseEU = pe.value; salvaStato(); cambia(); });
    // gruppo di indicatori per la vista Confronta
    const tc = $('#tema-confronto');
    tc.innerHTML = Object.entries(META.temi).map(([k, n]) => `<option value="${k}">${esc(n)}</option>`).join('');
    if (!META.temi[S.temaConf]) S.temaConf = 'sal';
    tc.value = S.temaConf;
    tc.addEventListener('change', () => { S.temaConf = tc.value; salvaStato(); cambia(); });

    const tabs = [...document.querySelectorAll('.schede [role="tab"]')];
    tabs.forEach((b, i) => {
      b.addEventListener('click', () => scegliVista(b.dataset.vista));
      b.addEventListener('keydown', (e) => {
        const d = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
        if (e.key === 'Home' || e.key === 'End' || d) {
          e.preventDefault();
          const j = e.key === 'Home' ? 0 : e.key === 'End' ? tabs.length - 1 : (i + d + tabs.length) % tabs.length;
          tabs[j].focus(); scegliVista(tabs[j].dataset.vista);
        }
      });
    });
    window.addEventListener('hashchange', () => { const h = location.hash.replace('#', ''); if (VISTE.includes(h) && h !== S.vista) scegliVista(h); });

    // linea del tempo
    const cur = $('#cursore-anno');
    cur.addEventListener('input', () => { ferma(); impostaAnno(+cur.value, false); });
    $('#anno-indietro').addEventListener('click', () => { ferma(); impostaAnno(S.anno - 1, true); });
    $('#anno-avanti').addEventListener('click', () => { ferma(); impostaAnno(S.anno + 1, true); });
    $('#riproduci').addEventListener('click', () => (S.inRiproduzione ? ferma() : riproduci()));
    document.addEventListener('keydown', (e) => {
      if (e.target.closest('input, select, textarea, [role="tab"], button, dialog, summary')) return;
      if (e.key === 'ArrowLeft') { ferma(); impostaAnno(S.anno - 1, true); }
      if (e.key === 'ArrowRight') { ferma(); impostaAnno(S.anno + 1, true); }
    });

    preparaRicerca();
    preparaArchivi();
    $('#svuota-selezione').addEventListener('click', () => { S.sel = []; salvaStato(); dopoSelezione(); });
    document.querySelectorAll('[data-preset]').forEach((b) => b.addEventListener('click', () => applicaPreset(b.dataset.preset)));

    const dlg = $('#metodo');
    $('#apri-metodo').addEventListener('click', () => (dlg.showModal ? dlg.showModal() : dlg.setAttribute('open', '')));
    $('#chiudi-metodo').addEventListener('click', () => dlg.close());
    dlg.addEventListener('click', (e) => { if (e.target === dlg) dlg.close(); });
  }

  function scegliVista(v) {
    if (!VISTE.includes(v)) return;
    S.vista = v;
    try { history.replaceState(null, '', '#' + v); } catch (e) { /* ignora */ }
    cambia();
  }

  function preparaRicerca() {
    const inp = $('#cerca-territorio'), ul = $('#risultati-territori');
    let risultati = [], attivo = -1;
    const chiudi = () => { ul.hidden = true; inp.setAttribute('aria-expanded', 'false'); attivo = -1; inp.removeAttribute('aria-activedescendant'); };
    const ordine = { paese: 0, nuts: 1, agg: 2, agg_eu: 3, storico: 4 };
    const disegna = () => {
      ul.innerHTML = '';
      if (!risultati.length) { const li = document.createElement('li'); li.textContent = 'Nessun territorio trovato'; li.setAttribute('aria-disabled', 'true'); ul.appendChild(li); }
      risultati.forEach((t, i) => {
        const li = document.createElement('li');
        li.id = 'ris-' + t.id.replace(/[^A-Za-z0-9_-]/g, '_'); li.setAttribute('role', 'option'); li.setAttribute('aria-selected', i === attivo ? 'true' : 'false');
        const gia = S.sel.includes(t.id);
        li.innerHTML = `<span class="pallino" style="background:${coloreRegione(t.id)}"></span><span>${esc(t.n)}</span>${gia ? '<span class="gia">già selezionato</span>' : ''}<span class="tipo">${esc(descriviTerritorio(t))}</span>`;
        li.addEventListener('mousedown', (e) => { e.preventDefault(); scegli(t); });
        ul.appendChild(li);
      });
      ul.hidden = false; inp.setAttribute('aria-expanded', 'true');
      if (attivo >= 0) inp.setAttribute('aria-activedescendant', ul.children[attivo].id);
    };
    const scegli = (t) => { attivaSelezione(t.id, true); inp.value = ''; chiudi(); };
    inp.addEventListener('input', () => {
      const q = norm(inp.value.trim());
      if (!q) { chiudi(); return; }
      risultati = META.territori.filter((t) => norm(t.n).includes(q) || norm(t.en).includes(q) || norm(t.cod || t.id) === q)
        .sort((a, b) => (norm(a.n).startsWith(q) ? 0 : 1) - (norm(b.n).startsWith(q) ? 0 : 1) || ordine[a.k] - ordine[b.k] || (a.l || 0) - (b.l || 0) || a.n.localeCompare(b.n, 'it'))
        .slice(0, 14);
      attivo = risultati.length ? 0 : -1; disegna();
    });
    inp.addEventListener('keydown', (e) => {
      if (ul.hidden) return;
      if (e.key === 'ArrowDown') { e.preventDefault(); attivo = Math.min(risultati.length - 1, attivo + 1); disegna(); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); attivo = Math.max(0, attivo - 1); disegna(); }
      else if (e.key === 'Enter' && attivo >= 0) { e.preventDefault(); scegli(risultati[attivo]); }
      else if (e.key === 'Escape') { chiudi(); }
    });
    inp.addEventListener('blur', () => setTimeout(chiudi, 120));
  }

  function preparaArchivi() {
    const sel = $('#archivio-dataset'), cerca = $('#archivio-cerca'), ul = $('#archivio-serie'), info = $('#archivio-info');
    const elenco = META.archivi || [];
    if (!elenco.length) { $('#blocco-archivi').hidden = true; return; }
    sel.innerHTML = '<option value="">Scegli un insieme di dati</option>' + elenco.map((a) =>
      `<option value="${a.f}">${esc(a.fonte.replace(/ \(.*/, ''))} · ${esc(a.n)} · lotto ${a.lotto}</option>`).join('');
    let serie = [];
    const disegna = () => {
      const q = norm(cerca.value.trim());
      const trovate = serie.map((s, i) => ({ s, i, n: Object.keys(s.d).length })).filter((o) => !q || norm(o.s.l).includes(q) || norm(o.s.u).includes(q)).sort((x, y) => y.n - x.n || x.s.l.localeCompare(y.s.l));
      ul.innerHTML = trovate.slice(0, 60).map((o) => `<li><button type="button" data-i="${o.i}"><span>${esc(o.s.l)}</span><span class="tipo">${esc(o.s.u)} · dal ${o.s.y0} al ${o.s.y1} · ${o.n} territori</span></button></li>`).join('')
        + (trovate.length > 60 ? `<li class="nota-piccola">Altre ${trovate.length - 60} serie: restringi la ricerca.</li>` : '')
        + (!trovate.length ? '<li class="nota-piccola">Nessuna serie corrisponde alla ricerca.</li>' : '');
      ul.querySelectorAll('button').forEach((b) => b.addEventListener('click', async () => {
        const id = `A:${sel.value}:${b.dataset.i}`;
        await registraSerie(id);
        S.ind = id; allineaFamiglie(); riempiSelezioni(); salvaStato();
        if (S.vista === 'confronta' || S.vista === 'bolle') S.vista = 'mappa';
        S.area = 'mondo';
        const sd = (await carica(id)), conta = {};
        Object.values(sd.d).forEach((a) => a.forEach((v, k) => { if (v != null) conta[sd.y0 + k] = (conta[sd.y0 + k] || 0) + 1; }));
        S.anno = +Object.entries(conta).sort((a, b) => b[1] - a[1] || b[0] - a[0])[0][0];
        cambia();
        $('#scena').scrollIntoView({ behavior: movimentoRidotto.matches ? 'auto' : 'smooth', block: 'center' });
      }));
    };
    sel.addEventListener('change', async () => {
      ul.innerHTML = ''; serie = []; cerca.value = '';
      const a = ARC[sel.value]; if (!a) { info.textContent = ''; cerca.disabled = true; return; }
      info.innerHTML = `${esc(a.fonte)}, lotto ${a.lotto}, <strong>${esc(a.archivio)}</strong>: nel repository ci sono ${a.terr.length} territori (${esc(a.terr.map((id) => T[id] ? T[id].n : id).slice(0, 4).join(', '))}${a.terr.length > 4 ? '…' : ''}), ${F.int.format(a.serie)} serie, dal ${a.y0} al ${a.y1}. Testi delle serie in inglese, come nella fonte.`;
      ul.innerHTML = '<li class="nota-piccola">Carico le serie…</li>';
      try { serie = (await caricaArchivio(a.f)).s; } catch (e) { ul.innerHTML = '<li class="nota-piccola">Non riesco a caricare questo insieme di dati.</li>'; return; }
      cerca.disabled = false; disegna();
    });
    cerca.addEventListener('input', debounce(disegna, 120));
  }

  // un confronto pronto: con sole regioni europee passa ai dati Eurostat, con soli paesi o aggregati ai dati nazionali
  function applicaPreset(elenco) {
    S.sel = elenco.split(',').filter((id) => T[id]);
    const regioni = S.sel.filter((id) => T[id].k === 'nuts' && T[id].l > 0).length;
    if (S.vista === 'confronta') {
      if (regioni && S.temaConf !== 'eur') S.temaConf = 'eur';
      else if (!regioni && S.temaConf === 'eur') S.temaConf = 'sal';
      $('#tema-confronto').value = S.temaConf;
    } else if (regioni && fam(S.ind) !== 'eu') {
      S.ind = PREDEFINITI.eu.ind; S.paeseEU = ''; S.livello = Math.max(...S.sel.filter((id) => T[id].k === 'nuts').map((id) => T[id].l)); allineaFamiglie();
    } else if (!regioni && fam(S.ind) === 'eu') {
      S.ind = PREDEFINITI.ind; allineaFamiglie();
    }
    salvaStato(); cambia();
  }

  function attivaSelezione(id, soloAggiungi) {
    const i = S.sel.indexOf(id);
    if (i >= 0) { if (soloAggiungi) return; S.sel.splice(i, 1); } else S.sel.push(id);
    salvaStato(); dopoSelezione();
  }
  // un clic su un territorio della vista: se è rappresentato da un altro codice selezionato, toglie quello
  function attivaDaVista(id) {
    const gia = S.sel.find((s) => idPer(S.ind, s) === id);
    if (gia) attivaSelezione(gia); else attivaSelezione(id);
  }
  function dopoSelezione() { disegnaSelezionati(); if (vista && vista.selezione) vista.selezione(); else cambia(); aggiornaEvidenza(); }

  function disegnaSelezionati() {
    const ul = $('#selezionati'); ul.innerHTML = '';
    if (!S.sel.length) { const li = document.createElement('li'); li.className = 'selezionati__vuoto'; li.textContent = 'Nessun territorio selezionato'; ul.appendChild(li); return; }
    S.sel.forEach((id) => {
      const t = T[id]; const li = document.createElement('li');
      const sw = eAggregato(id) ? `<span class="pallino pallino--agg"></span>` : `<span class="pallino" style="background:${coloreRegione(id)};margin:0"></span>`;
      const fuori = idPer(S.ind, id) == null && S.vista !== 'confronta';
      li.innerHTML = `${sw}<span>${esc(t.n)}${t.k === 'nuts' && t.l ? `<span class="selezionati__tipo"> NUTS ${t.l}</span>` : ''}</span><button type="button" aria-label="Togli ${esc(t.n)}"><svg viewBox="0 0 12 12"><path d="M2.5 2.5l7 7M9.5 2.5l-7 7"/></svg></button>`;
      if (fuori) { li.classList.add('selezionati--fuori'); li.title = 'Non presente nei dati dell’indicatore scelto'; }
      li.querySelector('button').addEventListener('click', () => attivaSelezione(id));
      ul.appendChild(li);
    });
  }

  /* ---------- tempo ---------- */
  let timer = null;
  function intervalloAnni() {
    if (S.vista === 'bolle') { const x = IND[S.indX], y = IND[S.ind]; return [Math.max(x.y0, y.y0), Math.max(Math.max(x.y0, y.y0), Math.min(x.y1, y.y1))]; }
    if (S.vista === 'confronta') { const v = Object.values(IND).filter((i) => i.t === S.temaConf); return [d3.min(v, (i) => i.y0), d3.max(v, (i) => i.y1)]; }
    return [IND[S.ind].y0, IND[S.ind].y1];
  }
  function impostaAnno(y, anima) {
    const [a0, a1] = intervalloAnni();
    y = Math.max(a0, Math.min(a1, y));
    if (y === S.anno && !anima) { aggiornaTempo(); return; }
    S.anno = y; aggiornaTempo();
    if (vista && vista.aggiorna) vista.aggiorna(anima);
    aggiornaMancanti(); aggiornaEvidenza();
  }
  function durata() { return movimentoRidotto.matches ? 0 : S.inRiproduzione ? +$('#velocita').value * 0.92 : 550; }
  function riproduci() {
    const [a0, a1] = intervalloAnni();
    if (S.anno >= a1) impostaAnno(a0, false);
    S.inRiproduzione = true;
    const b = $('#riproduci'); b.setAttribute('aria-pressed', 'true'); b.setAttribute('aria-label', 'Pausa');
    const passo = () => {
      if (!S.inRiproduzione) return;
      const [, fine] = intervalloAnni();
      if (S.anno >= fine) { ferma(); return; }
      impostaAnno(S.anno + 1, true);
      timer = setTimeout(passo, +$('#velocita').value);
    };
    timer = setTimeout(passo, 120);
  }
  function ferma() {
    if (!S.inRiproduzione) return;
    S.inRiproduzione = false; clearTimeout(timer);
    const b = $('#riproduci'); b.setAttribute('aria-pressed', 'false'); b.setAttribute('aria-label', 'Riproduci');
  }
  function aggiornaTempo() {
    const [a0, a1] = intervalloAnni();
    const cur = $('#cursore-anno');
    cur.min = a0; cur.max = a1; cur.value = S.anno;
    cur.setAttribute('aria-valuetext', String(S.anno));
    $('#anno-grande').textContent = S.anno;
    $('#anno-filigrana').textContent = S.anno;
    $('#anno-min').textContent = a0; $('#anno-max').textContent = a1;
    $('#anno-indietro').disabled = S.anno <= a0; $('#anno-avanti').disabled = S.anno >= a1;
    disegnaCopertura(a0, a1);
  }
  function disegnaCopertura(a0, a1) {
    const svg = d3.select('#copertura');
    const w = svg.node().getBoundingClientRect().width || 300, h = 24;
    const univ = S.vista === 'confronta' ? [] : universo(S.ind);
    const conteggi = [];
    for (let y = a0; y <= a1; y++) conteggi.push({ y, n: S.vista === 'confronta' ? 0 : contaConDato(y, univ) });
    const x = d3.scaleLinear().domain([a0, a1]).range([0, w]);
    const bw = Math.max(1, Math.min(8, w / (a1 - a0 + 1) - 1.5));
    const hy = d3.scaleLinear().domain([0, Math.max(1, univ.length)]).range([0, h - 2]);
    svg.attr('viewBox', `0 0 ${w} ${h}`);
    svg.selectAll('rect').data(conteggi, (d) => d.y).join('rect')
      .attr('x', (d) => (a1 === a0 ? w / 2 : x(d.y)) - bw / 2).attr('width', bw)
      .attr('y', (d) => h - Math.max(d.n ? 1.5 : 0, hy(d.n))).attr('height', (d) => Math.max(d.n ? 1.5 : 0, hy(d.n)))
      .attr('rx', 1).classed('attivo', (d) => d.y === S.anno);
    $('.tempo__copertura-nota').textContent = S.vista === 'confronta' ? '' : `Altezza delle barrette: ${famTerr(S.ind) === 'nuts' ? 'territori' : 'paesi'} con dato in ciascun anno`;
  }
  function contaConDato(y, univ) {
    const dy = cache[S.ind + '_r']; if (!dy) return 0;
    const dx = S.vista === 'bolle' ? cache[S.indX + '_r'] : null;
    let n = 0;
    for (const p of univ) if (valore(dy, p.id, y) && (!dx || valore(dx, p.id, y))) n++;
    return n;
  }

  /* ---------- aggiornamento generale ---------- */
  async function cambia() {
    ferma();
    let caricare = [S.ind];
    if (S.vista === 'bolle') caricare.push(S.indX, S.indR);
    if (S.vista === 'confronta') caricare = Object.values(IND).filter((i) => i.t === S.temaConf).map((i) => i.c);
    const lento = setTimeout(() => { $('#carico').hidden = false; }, 200);
    let dati;
    try { dati = await Promise.all(caricare.map(carica)); } catch (e) {
      clearTimeout(lento); $('#carico').hidden = false; $('#carico').textContent = 'Non riesco a caricare l’indicatore richiesto. Riprova tra poco.'; return;
    }
    clearTimeout(lento); $('#carico').hidden = true; $('#carico').textContent = 'Carico i dati…';
    caricare.forEach((c, i) => { cache[c + '_r'] = dati[i]; });
    if (S.vista !== 'confronta') caricaNote(S.ind);
    if (S.vista !== 'confronta' && !cache[S.ind + '_r']) { cache[S.ind + '_r'] = await carica(S.ind); }

    const [a0, a1] = intervalloAnni();
    S.anno = Math.max(a0, Math.min(a1, S.anno));

    const app = document.querySelector('.app');
    app.dataset.vista = S.vista; app.dataset.fam = fam(S.ind);
    $('#scena').dataset.vista = S.vista;
    document.querySelectorAll('.schede [role="tab"]').forEach((b) => {
      const on = b.dataset.vista === S.vista;
      b.setAttribute('aria-selected', on ? 'true' : 'false'); b.tabIndex = on ? 0 : -1;
    });
    $('#scena').setAttribute('aria-labelledby', 'tab-' + S.vista + ' titolo-vista');
    $('#etichetta-principale').textContent = S.vista === 'bolle' ? 'Asse verticale' : 'Indicatore';
    riempiSelezioni();
    if (S.vista !== 'confronta') {
      const cl = codiceLog(), st = stat(cl);
      $('#opt-log').disabled = !st.positivi;
      $('#opt-log').checked = usaLog(cl);
      $('#riga-log').querySelector('span').textContent = S.vista === 'bolle' ? 'Scala logaritmica sull’asse orizzontale' : 'Scala logaritmica';
      $('#riga-log').title = st.positivi ? '' : 'Non disponibile: l’indicatore contiene valori nulli o negativi';
    }
    $('#riga-log').hidden = !['mappa', 'bolle', 'linee'].includes(S.vista);
    $('#riga-aggregati').hidden = S.vista !== 'tabella';
    $('#campo-area').hidden = !(S.vista === 'mappa' && famTerr(S.ind) === 'paesi');
    $('#campi-nuts').hidden = !(famTerr(S.ind) === 'nuts' && S.vista !== 'confronta');
    $('#livello-nuts').value = S.livello; $('#paese-nuts').value = S.paeseEU; $('#area-mappa').value = S.area;

    intestazione();
    aggiornaTempo();
    disegnaSelezionati();
    nascondiSuggerimento();
    const corpo = $('#corpo-vista'); corpo.innerHTML = '';
    vista = VISTE_IMPL[S.vista](corpo);
    disegnaLegenda();
    aggiornaMancanti(); aggiornaEvidenza();
  }
  const codiceLog = () => (S.vista === 'bolle' ? S.indX : S.ind);

  function fonteDi(i) {
    if (i.fam === 'eu') return `Eurostat, <a href="${i.url}" target="_blank" rel="noopener">${i.ds}</a>`;
    if (i.fam === 'arc') return `${esc(i.arch.fonte.replace(/ \(.*/, ''))} ${esc(i.arch.cod)}, lotto ${i.lotto}, ${esc(i.arch.archivio)} · <a href="${i.url}" target="_blank" rel="noopener">archivio fonte</a>`;
    return `Banca Mondiale, WDI · <a href="${i.url}" target="_blank" rel="noopener">${i.c}</a>`;
  }
  function intestazione() {
    const sd = (id, html) => { $(id).innerHTML = html; };
    if (S.vista === 'confronta') {
      $('#titolo-vista').textContent = `Confronto · ${META.temi[S.temaConf]}`;
      $('#unita-vista').textContent = 'valori nell’anno scelto';
      sd('#sd-unita', 'indicata in ogni riga');
      const [a0, a1] = intervalloAnni();
      sd('#sd-periodo', `dal ${a0} al ${a1}`);
      sd('#sd-fonte', S.temaConf === 'eur' ? 'Eurostat, dati regionali' : 'Banca Mondiale, WDI');
      const v = Object.values(IND).filter((i) => i.t === S.temaConf);
      sd('#sd-agg', `fonte: ${data(d3.max(v, (i) => i.agg))}<br>acquisizione: ${data(META.acquisizione['1'])}`);
      $('#def-testo').textContent = 'Ogni riga è un indicatore; ogni colonna un territorio selezionato. Le barre confrontano i territori fra loro nella stessa riga: la più lunga è il valore più alto fra quelli selezionati.';
      $('#def-fonte').textContent = 'Un paese selezionato mostra anche i dati Eurostat nazionali (NUTS 0); una regione mostra solo i dati Eurostat, perché la Banca Mondiale pubblica dati nazionali.';
      return;
    }
    const i = IND[S.ind];
    if (S.vista === 'bolle') {
      $('#titolo-vista').textContent = `${i.n} e ${IND[S.indX].n.toLowerCase()}`;
      $('#unita-vista').textContent = `dimensione: ${IND[S.indR].n.toLowerCase()}`;
    } else {
      $('#titolo-vista').textContent = i.n;
      const liv = LIVELLI[S.livello];
      $('#unita-vista').textContent = i.u + (i.fam === 'eu' ? ` · ${liv[0].toLowerCase() + liv.slice(1)}${S.paeseEU ? ', ' + T[S.paeseEU].n : ''}` : '');
    }
    const usati = S.vista === 'bolle' ? [S.ind, S.indX, S.indR] : [S.ind];
    sd('#sd-unita', S.vista === 'bolle' ? `verticale: ${esc(IND[S.ind].u)}<br>orizzontale: ${esc(IND[S.indX].u)}<br>dimensione: ${esc(IND[S.indR].u)}` : esc(i.u));
    sd('#sd-periodo', usati.map((c) => `${S.vista === 'bolle' ? esc(IND[c].n) + ': ' : ''}dal ${IND[c].y0} al ${IND[c].y1}`).join('<br>'));
    sd('#sd-fonte', [...new Set(usati.map((c) => fonteDi(IND[c])))].join('<br>'));
    const acq = i.fam === 'arc' ? '25 ottobre 2026 (indice del lotto)' : data(META.acquisizione[String(i.lotto)]);
    sd('#sd-agg', `fonte: ${data(i.agg)}<br>acquisizione: ${i.fam === 'arc' ? 'ottobre 2026' : acq}, lotto ${i.lotto}`);
    if (i.fam === 'arc') {
      $('#def-testo').textContent = `Serie originale: ${i.en}.`;
      $('#def-fonte').innerHTML = `<strong>Archivio parziale.</strong> Il repository contiene solo l’${esc(i.arch.archivio)} del lotto ${i.lotto}: ${i.arch.terr.length} territori. Gli altri paesi non mancano nella fonte, mancano nel repository; per questo la mappa li mostra come “fuori dai dati”.`;
    } else {
      $('#def-testo').textContent = i.def ? `“${i.def}”` : 'Definizione non presente nella fonte.';
      $('#def-fonte').innerHTML = i.fam === 'eu'
        ? `Fonte: Eurostat, dataset ${esc(i.ds)} (${esc(i.en)}). ${i.calc ? `<strong>${esc(i.calc)}</strong> ` : ''}I codici NUTS sono conservati come pubblicati: i codici storici compaiono in tabella ma non sulla mappa, disegnata con i confini NUTS 2024. Osservazioni: ${F.int.format(i.valori)}.`
        : `Testo originale in inglese della Banca Mondiale per <strong>${esc(i.en)}</strong>. Fonte originaria dichiarata: ${esc((i.org || 'n.d.').replace(/, uri: \S+/g, '').replace(/\n/g, '; '))}. Osservazioni nei file: ${F.int.format(i.righe)} righe, ${F.int.format(i.valori)} con valore numerico.`;
    }
  }

  function mancanti() {
    const out = [];
    const dy = cache[S.ind + '_r'];
    const dx = S.vista === 'bolle' ? cache[S.indX + '_r'] : null;
    for (const p of universo(S.ind)) if (!(valore(dy, p.id, S.anno) && (!dx || valore(dx, p.id, S.anno)))) out.push(p);
    return out;
  }
  function aggiornaMancanti() {
    if (S.vista === 'confronta') {
      $('#sd-mancanti').innerHTML = '<span class="campione-vuoto" aria-hidden="true"></span><span>indicati con “n.d.” nella griglia</span>';
      $('#def-mancanti').textContent = 'Vedi le celle “n.d.” della griglia.';
      disegnaCopertura(...intervalloAnni()); return;
    }
    const m = mancanti(), tot = universo(S.ind).length, i = IND[S.ind];
    const cosa = famTerr(S.ind) === 'nuts' ? 'territori' : 'paesi';
    let t = `${m.length} ${cosa} su ${tot} senza dato nel ${S.anno}${S.ultimo ? ' (anche cercando fino a 10 anni prima)' : ''}`;
    if (i.fam === 'arc') t += `; archivio parziale: altri paesi non sono nel repository`;
    $('#sd-mancanti').innerHTML = `<span class="campione-vuoto" aria-hidden="true"></span><span>${t}</span>`;
    $('#def-mancanti').textContent = m.length ? m.map((p) => p.n).sort((a, b) => a.localeCompare(b, 'it')).join(', ') + '.' : 'Nessuno: tutti hanno un valore.';
    disegnaCopertura(...intervalloAnni());
  }
  function aggiornaEvidenza() {
    const el = $('#evidenza');
    if (S.vista === 'confronta') {
      const n = S.sel.length;
      el.innerHTML = n < 2 ? 'Seleziona almeno <strong>due territori</strong> nel pannello: paesi, aggregati o regioni europee. Oppure usa uno dei confronti pronti.' : `Nel <strong>${S.anno}</strong>: ${n} territori a confronto.`;
      return;
    }
    const d = cache[S.ind + '_r']; if (!d) return;
    const univ = universo(S.ind);
    const righe = univ.map((p) => ({ p, r: valore(d, p.id, S.anno) })).filter((x) => x.r).sort((a, b) => b.r.v - a.r.v);
    const cosa = famTerr(S.ind) === 'nuts' ? 'territori' : 'paesi';
    if (!righe.length) { el.textContent = `Nel ${S.anno} nessun territorio ha un valore per questo indicatore. Prova un altro anno o attiva l’ultimo dato disponibile.`; return; }
    const alto = righe[0], basso = righe[righe.length - 1];
    let t = `Nel <strong>${S.anno}</strong>: valore più alto <strong>${esc(alto.p.n)}</strong> (${fmt(alto.r.v)}), più basso <strong>${esc(basso.p.n)}</strong> (${fmt(basso.r.v)}), su ${righe.length} ${cosa} con dato.`;
    const primo = S.sel.map((s) => idPer(S.ind, s)).find((id) => id && univ.some((u) => u.id === id));
    if (primo) {
      const k = righe.findIndex((x) => x.p.id === primo);
      t += k >= 0 ? ` ${esc(T[primo].n)}: <strong>${fmt(righe[k].r.v)}</strong>, ${k + 1}° posto su ${righe.length}${righe[k].r.y !== S.anno ? ` (dato del ${righe[k].r.y})` : ''}.` : ` ${esc(T[primo].n)}: nessun dato nel ${S.anno}.`;
    }
    el.innerHTML = t;
  }

  /* ---------- legenda ---------- */
  function disegnaLegenda() {
    const lv = $('#legenda-valori'); lv.innerHTML = '';
    const nota = $('#legenda-regioni-nota');
    if (S.vista === 'mappa' && vista.scala) {
      const sc = vista.scala, w = 300;
      const svg = d3.select(lv).append('svg').attr('viewBox', `0 0 ${w} 94`).attr('role', 'img').attr('aria-label', `Scala dei colori da ${fmt(sc.domain()[0])} a ${fmt(sc.domain()[1])} ${IND[S.ind].u}`);
      const defs = svg.append('defs');
      const id = 'grad-' + Math.random().toString(36).slice(2, 7);
      const g = defs.append('linearGradient').attr('id', id);
      d3.range(0, 1.0001, 0.1).forEach((t) => g.append('stop').attr('offset', t).attr('stop-color', sc.interpolator()(t)));
      svg.append('text').attr('x', 0).attr('y', 11).text(IND[S.ind].u);
      svg.append('rect').attr('x', 0).attr('y', 18).attr('width', w).attr('height', 12).attr('rx', 3).style('fill', `url(#${id})`);
      const xs = (usaLog(S.ind) ? d3.scaleLog() : d3.scaleLinear()).domain(sc.domain()).range([0, w]);
      const ticks = usaLog(S.ind) ? xs.ticks(4).filter((v) => /^[125]/.test(String(v).replace(/^0\.0*/, ''))).slice(0, 6) : xs.ticks(4);
      ticks.forEach((v) => {
        const x = xs(v); if (x < 0 || x > w) return;
        svg.append('line').attr('x1', x).attr('x2', x).attr('y1', 30).attr('y2', 34).style('stroke', colori.grafite);
        svg.append('text').attr('x', x).attr('y', 46).attr('text-anchor', x < 15 ? 'start' : x > w - 15 ? 'end' : 'middle').text(fmtAsse(v));
      });
      trama(defs, 'trama-legenda');
      svg.append('rect').attr('x', 0).attr('y', 56).attr('width', 18).attr('height', 12).attr('rx', 2).style('fill', 'url(#trama-legenda)');
      svg.append('text').attr('x', 24).attr('y', 66).text('Dato mancante');
      svg.append('rect').attr('x', 130).attr('y', 56).attr('width', 18).attr('height', 12).attr('rx', 2).style('fill', colori.altro);
      svg.append('text').attr('x', 154).attr('y', 66).text(fam(S.ind) === 'arc' ? 'Non nel repository' : 'Fuori dai dati');
      svg.append('text').attr('x', 0).attr('y', 88).text('Scala fissa per tutti gli anni; oltre gli estremi il colore resta pieno.');
      nota.textContent = 'Tocca una regione per metterla in risalto sulla mappa.';
    } else if (S.vista === 'bolle' && vista.raggio) {
      const r = vista.raggio, vals = niceSizes(r.domain()[1]);
      const maxR = r(vals[0]), w = 300, h = maxR * 2 + 26;
      const svg = d3.select(lv).append('svg').attr('viewBox', `0 0 ${w} ${h}`).attr('class', 'legenda-dimensione').attr('role', 'img').attr('aria-label', 'Dimensione delle bolle');
      svg.append('text').attr('x', 0).attr('y', 11).text(`Dimensione: ${IND[S.indR].n.toLowerCase()}`);
      vals.forEach((v) => {
        const rr = r(v), cy = 18 + maxR * 2 - rr;
        svg.append('circle').attr('cx', maxR + 2).attr('cy', cy).attr('r', rr);
        svg.append('line').attr('x1', maxR + 2).attr('x2', maxR * 2 + 16).attr('y1', cy - rr).attr('y2', cy - rr).style('stroke', colori.grafite).style('stroke-opacity', 0.3);
        svg.append('text').attr('x', maxR * 2 + 20).attr('y', cy - rr + 4).text(fmt(v));
      });
      nota.textContent = 'Colore = regione del mondo. Tocca una regione per metterla in risalto.';
    } else if (S.vista === 'linee') {
      const rif = fam(S.ind) === 'eu' ? 'Unione europea a 27' : fam(S.ind) === 'wb' ? 'Mondo, media della Banca Mondiale' : '';
      lv.innerHTML = `<svg viewBox="0 0 300 40" role="img" aria-label="Legenda delle linee">${rif ? `<line x1="0" x2="26" y1="10" y2="10" class="linea linea--mondo"></line><text x="34" y="14">${rif}</text>` : ''}<line x1="0" x2="26" y1="30" y2="30" style="stroke:${colori.grafite};stroke-opacity:.25"></line><text x="34" y="34">Tutti gli altri territori</text></svg>`;
      nota.textContent = 'Colore = regione del mondo dei territori selezionati. Tocca una regione per metterla in risalto.';
    } else {
      nota.textContent = 'Colore = regione del mondo. Tocca una regione per metterla in risalto.';
    }
    const ul = $('#legenda-regioni'); ul.innerHTML = '';
    ul.classList.toggle('attiva', !!S.regione);
    META.regioni.forEach((r) => {
      const n = PAESI.filter((p) => p.r === r.k).length;
      const li = document.createElement('li');
      li.innerHTML = `<button type="button" aria-pressed="${S.regione === r.k}"><span class="pallino" style="background:${colori[r.k]}"></span><span>${r.n}</span><span class="conta">${n} paesi</span></button>`;
      li.querySelector('button').addEventListener('click', () => { S.regione = S.regione === r.k ? null : r.k; disegnaLegenda(); if (vista.selezione) vista.selezione(); });
      ul.appendChild(li);
    });
    const li = document.createElement('li');
    li.innerHTML = `<button type="button" disabled style="cursor:default"><span class="pallino" style="background:${colori.neutro}"></span><span>Senza regione o codice storico</span></button>`;
    ul.appendChild(li);
  }
  function niceSizes(max) {
    const p = Math.pow(10, Math.floor(Math.log10(max)));
    const top = max / p >= 5 ? 5 * p : max / p >= 2 ? 2 * p : p;
    return [top, top / 5, top / 50].filter((v) => v > 0);
  }
  function trama(defs, id) {
    const p = defs.append('pattern').attr('id', id).attr('patternUnits', 'userSpaceOnUse').attr('width', 5).attr('height', 5).attr('patternTransform', 'rotate(45)');
    p.append('rect').attr('width', 5).attr('height', 5).style('fill', colori.vuoto);
    p.append('line').attr('x1', 0).attr('x2', 0).attr('y1', 0).attr('y2', 5).style('stroke', colori.trama).style('stroke-width', 1.6);
  }

  /* ---------- suggerimento ---------- */
  const sug = $('#suggerimento');
  let sugTimer;
  function mostraSuggerimento(ev, html) {
    clearTimeout(sugTimer);
    sug.innerHTML = html; sug.hidden = false;
    const box = $('#scena').getBoundingClientRect();
    const x = ev.clientX - box.left, y = ev.clientY - box.top;
    const w = sug.offsetWidth, h = sug.offsetHeight;
    let lx = x + 14, ly = y + 14;
    if (lx + w > box.width - 6) lx = x - w - 14;
    if (ly + h > box.height - 6) ly = y - h - 14;
    sug.style.left = Math.max(6, lx) + 'px'; sug.style.top = Math.max(6, ly) + 'px';
    if (ev.pointerType === 'touch') sugTimer = setTimeout(nascondiSuggerimento, 2800);
  }
  function nascondiSuggerimento() { sug.hidden = true; }
  function notaDi(cod, id, y) {
    const n = notaRisolta[cod];
    const x = y && n && n[id] && n[id][y];
    return x ? [x[0], x[1]].filter(Boolean).join(' · ') : null;
  }
  function rigaValore(cod, id) {
    const d = cache[cod + '_r'], r = valore(d, id, S.anno), i = IND[cod];
    const nota = notaDi(cod, id, r && r.y);
    return `${r ? `<div class="suggerimento__valore">${fmt(r.v)}</div><div class="suggerimento__riga"><span>${esc(i.u)}</span></div>${r.y !== S.anno ? `<div class="suggerimento__nota">Ultimo dato disponibile: ${r.y}</div>` : ''}` : `<div class="suggerimento__vuoto">Dato mancante nel ${S.anno}</div>`}
      ${nota ? `<div class="suggerimento__nota">Nota della fonte: ${esc(nota)}</div>` : ''}`;
  }
  function intestazioneSug(id) {
    const t = T[id];
    return `<div class="suggerimento__nome"><span class="pallino" style="background:${coloreRegione(id)};margin:0"></span>${esc(t.n)}</div><div class="suggerimento__riga"><span>${esc(descriviTerritorio(t))}</span></div>`;
  }
  const spento = (id) => S.regione && T[id] && T[id].r !== S.regione;
  const nomeRif = (id) => (id === 'WLD' ? 'Mondo' : id === 'EU:EU27_2020' ? 'Unione europea a 27' : T[id].n);

  /* ---------- viste ---------- */
  const VISTE_IMPL = {};

  // MAPPA
  function puntiRiquadro(b) {
    const [x0, y0, x1, y1] = b, pts = [];
    for (let k = 0; k <= 10; k++) { const x = x0 + (x1 - x0) * k / 10, y = y0 + (y1 - y0) * k / 10; pts.push([x, y0], [x, y1], [x0, y], [x1, y]); }
    return { type: 'MultiPoint', coordinates: pts };
  }
  VISTE_IMPL.mappa = function (el) {
    const cod = S.ind, d = cache[cod + '_r'], st = stat(cod), eu = fam(cod) === 'eu';
    const w = el.clientWidth, h = el.clientHeight;
    const log = usaLog(cod);
    const interp = d3.interpolateLab(colori.seq0, colori.seq1);
    const scala = (log ? d3.scaleSequentialLog(interp) : d3.scaleSequential(interp)).domain(st.p02 < st.p98 ? [st.p02, st.p98] : [st.min, st.max]).clamp(true);
    const mondo = topojson.feature(geo, geo.objects[Object.keys(geo.objects)[0]]);
    mondo.features = mondo.features.filter((f) => f.properties.id !== 'NE_ATA');
    let regioni = null, proj;
    const inUniverso = new Set(universo(cod).map((t) => t.id));
    if (eu) {
      const tutte = topojson.feature(geoNuts, geoNuts.objects[Object.keys(geoNuts.objects)[0]]).features;
      regioni = tutte.filter((f) => f.properties.l === S.livello && (!S.paeseEU || f.properties.c === S.paeseEU));
      regioni.forEach((f) => { f.properties.key = 'EU:' + f.properties.id; });
      proj = d3.geoAzimuthalEqualArea().rotate([-10, -52]);
      if (S.paeseEU) {
        // esclude le regioni ultraperiferiche dall'inquadratura del paese
        const vicine = regioni.filter((f) => { const c = d3.geoCentroid(f); return c[0] > -25 && c[1] > 27; });
        proj.fitExtent([[10, 10], [w - 10, h - 10]], { type: 'FeatureCollection', features: vicine.length ? vicine : regioni });
      } else proj.fitExtent([[6, 6], [w - 6, h - 6]], puntiRiquadro([-24, 34.5, 42, 70.5]));
    } else {
      proj = d3.geoEqualEarth();
      const a = AREE[S.area];
      if (a.b) { if (S.area === 'oceania') proj.rotate([-150, 0]); proj.fitExtent([[6, 6], [w - 6, h - 6]], puntiRiquadro(a.b)); }
      else proj.fitExtent([[6, 6], [w - 6, h - 6]], mondo);
    }
    const path = d3.geoPath(proj);
    const svg = d3.select(el).append('svg').attr('viewBox', `0 0 ${w} ${h}`).attr('role', 'img')
      .attr('aria-label', `Mappa${eu ? ' delle regioni europee' : ' ' + AREE[S.area].n.toLowerCase()}: ${IND[cod].n}. La tabella offre gli stessi valori in forma testuale.`);
    trama(svg.append('defs'), 'trama');
    const g = svg.append('g');
    // i territori senza codice Banca Mondiale (es. Taiwan) hanno nella mappa il codice NE_ seguito dal codice ISO
    const chiave = (f) => { if (eu) return f.properties.key; const k = f.properties.id; return !T[k] && k.startsWith('NE_') && T[k.slice(3)] ? k.slice(3) : k; };
    // sfondo: i paesi del mondo; con i dati regionali restano in grigio come contesto
    const sfondo = g.append('g').selectAll('path').data(mondo.features).join('path').attr('d', path)
      .attr('class', (f) => 'paese' + (!eu && inUniverso.has(chiave(f)) ? '' : ' paese--altro'))
      .style('vector-effect', 'non-scaling-stroke');
    const aree = eu ? g.append('g').selectAll('path').data(regioni).join('path').attr('d', path).attr('class', 'paese').style('vector-effect', 'non-scaling-stroke') : sfondo.filter((f) => inUniverso.has(chiave(f)));
    if (eu) sfondo.style('pointer-events', 'none');
    const colora = (anima) => {
      aree.each(function (f) {
        const r = valore(d, chiave(f), S.anno);
        const s = d3.select(this);
        const c = r ? scala(r.v) : 'url(#trama)';
        if (anima && r && durata()) s.transition().duration(durata()).ease(S.inRiproduzione ? d3.easeLinear : d3.easeCubicOut).style('fill', c);
        else s.interrupt().style('fill', c);
      });
    };
    const selezione = () => {
      aree.classed('paese--sel', (f) => selezionato(cod, chiave(f))).style('opacity', (f) => (spento(chiave(f)) ? 0.22 : 1));
      aree.filter((f) => selezionato(cod, chiave(f))).raise();
    };
    aree.on('pointermove', (ev, f) => {
      const id = chiave(f);
      if (!T[id]) { mostraSuggerimento(ev, `<div class="suggerimento__nome">${esc(f.properties.n)}</div><div class="suggerimento__vuoto">Codice non presente nei dati</div>`); return; }
      mostraSuggerimento(ev, intestazioneSug(id) + rigaValore(cod, id) + `<div class="suggerimento__nota">${selezionato(cod, id) ? 'Selezionato. Tocca per togliere.' : 'Tocca per selezionare.'}</div>`);
    }).on('pointerleave', (ev) => { if (ev.pointerType !== 'touch') nascondiSuggerimento(); })
      .on('click', (ev, f) => { if (T[chiave(f)]) attivaDaVista(chiave(f)); });
    if (!eu) sfondo.filter((f) => !inUniverso.has(chiave(f))).on('pointermove', (ev, f) => mostraSuggerimento(ev, `<div class="suggerimento__nome">${esc(f.properties.n)}</div><div class="suggerimento__vuoto">${fam(cod) === 'arc' ? 'Non presente nell’archivio parziale del repository' : 'Territorio non presente nei dati della Banca Mondiale'}</div>`)).on('pointerleave', nascondiSuggerimento);

    const zoom = d3.zoom().scaleExtent([1, 12]).translateExtent([[-w * 0.5, -h * 0.5], [w * 1.5, h * 1.5]]).on('zoom', (e) => g.attr('transform', e.transform));
    svg.call(zoom).on('dblclick.zoom', null);
    const com = d3.select(el).append('div').attr('class', 'comandi-mappa');
    const bottone = (et, ico, f) => com.append('button').attr('class', 'pulsante').attr('type', 'button').attr('aria-label', et).attr('title', et).html(`<svg viewBox="0 0 20 20" aria-hidden="true"><path d="${ico}"/></svg>`).on('click', f);
    bottone('Ingrandisci', 'M10 4v12 M4 10h12', () => svg.transition().duration(300).call(zoom.scaleBy, 1.6));
    bottone('Riduci', 'M4 10h12', () => svg.transition().duration(300).call(zoom.scaleBy, 1 / 1.6));
    bottone('Torna all’inquadratura iniziale', 'M3 8V3h5 M12 3h5v5 M17 12v5h-5 M8 17H3v-5', () => svg.transition().duration(400).call(zoom.transform, d3.zoomIdentity));

    colora(false); selezione();
    return { aggiorna: colora, selezione, scala };
  };

  // BOLLE
  VISTE_IMPL.bolle = function (el) {
    const dx = cache[S.indX + '_r'], dy = cache[S.ind + '_r'], dr = cache[S.indR + '_r'];
    const sx = stat(S.indX), sy = stat(S.ind), sr = stat(S.indR);
    const univ = universo(S.ind);
    const W = el.clientWidth, H = el.clientHeight;
    const stretto = W < 560;
    const m = { t: 18, r: 18, b: 50, l: stretto ? 48 : 64 };
    const w = W - m.l - m.r, h = H - m.t - m.b;
    const pad = (a, b, lg) => (lg ? [a / 1.15, b * 1.15] : [a - (b - a) * 0.04, b + (b - a) * 0.04]);
    const lx = usaLog(S.indX), ly = sy.positivi && sy.logAuto;
    const x = (lx ? d3.scaleLog() : d3.scaleLinear()).domain(pad(sx.min, sx.max, lx)).range([0, w]);
    const y = (ly ? d3.scaleLog() : d3.scaleLinear()).domain(pad(sy.min, sy.max, ly)).range([h, 0]);
    const rMax = Math.max(14, Math.min(w, h) / (stretto ? 9 : 11)) * (univ.length > 400 ? 0.55 : 1);
    const r = d3.scaleSqrt().domain([0, Math.max(sr.max, 1)]).range([0, rMax]);
    const svg = d3.select(el).append('svg').attr('viewBox', `0 0 ${W} ${H}`).attr('role', 'img')
      .attr('aria-label', `Grafico a bolle: in verticale ${IND[S.ind].n}, in orizzontale ${IND[S.indX].n}, dimensione ${IND[S.indR].n}.`);
    const g = svg.append('g').attr('transform', `translate(${m.l},${m.t})`);
    const tickX = lx ? x.ticks(6).filter((v) => /^[125]/.test(String(v).replace(/^0\.0*/, ''))) : x.ticks(stretto ? 5 : 8);
    g.append('g').attr('class', 'asse').attr('transform', `translate(0,${h})`).call(d3.axisBottom(x).tickValues(tickX).tickFormat(fmtAsse).tickSize(-h).tickPadding(8));
    g.append('g').attr('class', 'asse').call(d3.axisLeft(y).ticks(stretto ? 5 : 7).tickFormat(fmtAsse).tickSize(-w).tickPadding(8));
    g.append('text').attr('class', 'etichetta-asse').attr('x', w).attr('y', h + 40).attr('text-anchor', 'end').text(`${IND[S.indX].n} (${IND[S.indX].u})${lx ? ', scala logaritmica' : ''} →`);
    g.append('text').attr('class', 'etichetta-asse').attr('x', 6).attr('y', 4).attr('dy', '.7em').text(`↑ ${IND[S.ind].n} (${IND[S.ind].u})${ly ? ', scala logaritmica' : ''}`);
    const strato = g.append('g'), etich = g.append('g');
    const punti = () => univ.map((p) => {
      const vx = valore(dx, p.id, S.anno), vy = valore(dy, p.id, S.anno);
      if (!vx || !vy) return null;
      return { id: p.id, x: vx, y: vy, r: valore(dr, p.id, S.anno) };
    }).filter(Boolean).sort((a, b) => (b.r ? b.r.v : 0) - (a.r ? a.r.v : 0));
    let sel = strato.selectAll('circle');
    const aggiorna = (anima) => {
      const dati = punti();
      const t = d3.transition().duration(anima ? durata() : 0).ease(S.inRiproduzione ? d3.easeLinear : d3.easeCubicOut);
      sel = strato.selectAll('circle').data(dati, (d) => d.id).join(
        (en) => en.append('circle').attr('class', 'bolla').attr('r', 0).attr('cx', (d) => x(d.x.v)).attr('cy', (d) => y(d.y.v))
          .style('fill', (d) => coloreRegione(d.id)).style('fill-opacity', 0.86),
        (up) => up,
        (ex) => ex.transition(t).attr('r', 0).remove()
      );
      sel.order();
      sel.transition(t).attr('cx', (d) => x(d.x.v)).attr('cy', (d) => y(d.y.v)).attr('r', (d) => (d.r ? Math.max(2.5, r(d.r.v)) : 3));
      sel.on('pointermove', (ev, d) => {
        mostraSuggerimento(ev, intestazioneSug(d.id) +
          `<div class="suggerimento__riga"><span>${esc(IND[S.ind].n)}</span><strong>${fmt(d.y.v)}</strong></div>` +
          `<div class="suggerimento__riga"><span>${esc(IND[S.indX].n)}</span><strong>${fmt(d.x.v)}</strong></div>` +
          `<div class="suggerimento__riga"><span>${esc(IND[S.indR].n)}</span><strong>${d.r ? fmt(d.r.v) : 'n.d.'}</strong></div>` +
          (d.x.y !== S.anno || d.y.y !== S.anno ? `<div class="suggerimento__nota">Anni dei dati: verticale ${d.y.y}, orizzontale ${d.x.y}</div>` : ''));
      }).on('pointerleave', (ev) => { if (ev.pointerType !== 'touch') nascondiSuggerimento(); })
        .on('click', (ev, d) => attivaDaVista(d.id));
      selezione(dati, t);
    };
    const selezione = (dati, t) => {
      dati = dati || sel.data();
      sel.classed('bolla--sel', (d) => selezionato(S.ind, d.id)).classed('bolla--spenta', (d) => spento(d.id));
      sel.filter((d) => selezionato(S.ind, d.id)).raise();
      const lab = dati.filter((d) => selezionato(S.ind, d.id));
      const ts = etich.selectAll('text').data(lab, (d) => d.id).join((en) => en.append('text').attr('class', 'etichetta-dato').attr('x', (d) => x(d.x.v)).attr('y', (d) => y(d.y.v)));
      ts.text((d) => T[d.id].n);
      (t ? ts.transition(t) : ts).attr('x', (d) => x(d.x.v) + (d.r ? r(d.r.v) : 3) * 0.75 + 4).attr('y', (d) => y(d.y.v) - (d.r ? r(d.r.v) : 3) * 0.75);
    };
    aggiorna(false);
    return { aggiorna, selezione: () => selezione(), raggio: r };
  };

  // LINEE
  VISTE_IMPL.linee = function (el) {
    const cod = S.ind, d = cache[cod + '_r'];
    const univ = universo(cod);
    const W = el.clientWidth, H = el.clientHeight, stretto = W < 560;
    const m = { t: 20, r: stretto ? 86 : 150, b: 34, l: stretto ? 46 : 62 };
    const w = W - m.l - m.r, h = H - m.t - m.b;
    const lg = usaLog(cod);
    const rif = fam(cod) === 'wb' ? 'WLD' : fam(cod) === 'eu' ? 'EU:EU27_2020' : null;
    const scelti = [...new Set(S.sel.map((s) => idPer(cod, s)).filter((id) => id && d.d[id]))];
    const ids = () => scelti.concat(rif && !scelti.includes(rif) && d.d[rif] ? [rif] : []);
    let lo = Infinity, hi = -Infinity;
    univ.concat(ids().map((id) => T[id])).forEach((t) => (d.d[t.id] || []).forEach((v) => { if (v != null) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }));
    if (!isFinite(lo)) { lo = 0; hi = 1; }
    const x = d3.scaleLinear().domain([d.y0, Math.max(d.y1, d.y0 + 1)]).range([0, w]);
    const y = (lg ? d3.scaleLog().domain([lo / 1.1, hi * 1.1]) : d3.scaleLinear().domain([lo, hi]).nice()).range([h, 0]);
    const svg = d3.select(el).append('svg').attr('viewBox', `0 0 ${W} ${H}`).attr('role', 'img').attr('aria-label', `Andamento nel tempo: ${IND[cod].n}, dal ${d.y0} al ${d.y1}.`);
    const g = svg.append('g').attr('transform', `translate(${m.l},${m.t})`);
    g.append('g').attr('class', 'asse asse--base').attr('transform', `translate(0,${h})`).call(d3.axisBottom(x).ticks(Math.min(stretto ? 4 : 9, d.y1 - d.y0 || 1)).tickFormat(d3.format('d')).tickSizeOuter(0));
    const ty = lg ? y.ticks(6).filter((v) => /^[125]/.test(String(v).replace(/^0\.0*/, ''))) : y.ticks(stretto ? 5 : 7);
    g.append('g').attr('class', 'asse').call(d3.axisLeft(y).tickValues(ty).tickFormat(fmtAsse).tickSize(-w).tickPadding(8));
    const linea = d3.line().defined((v) => v != null).x((v, i) => x(d.y0 + i)).y((v) => y(v));
    const area = g.append('rect').attr('width', w).attr('height', h).style('fill', 'transparent');
    const fondo = g.append('g');
    fondo.selectAll('path').data(univ.filter((p) => d.d[p.id] && !scelti.includes(p.id))).join('path')
      .attr('class', 'linea linea--fondo').attr('d', (p) => linea(d.d[p.id]))
      .on('pointermove', (ev, p) => mostraSuggerimento(ev, intestazioneSug(p.id) + `<div class="suggerimento__nota">Tocca per selezionare</div>`))
      .on('pointerleave', nascondiSuggerimento).on('click', (ev, p) => attivaDaVista(p.id));
    const cursore = g.append('line').attr('class', 'cursore-anno').attr('y1', 0).attr('y2', h);
    const primo = g.append('g'), punti = g.append('g'), etich = g.append('g');
    const colore = (id) => (eAggregato(id) ? colori.grafite : coloreRegione(id));
    primo.selectAll('path').data(ids(), (id) => id).join('path')
      .attr('class', (id) => 'linea' + (eAggregato(id) ? ' linea--mondo' : '')).attr('d', (id) => linea(d.d[id]))
      .style('stroke', colore).style('opacity', (id) => (spento(id) && !eAggregato(id) ? 0.2 : 1));
    const fine = ids().map((id) => {
      const a = d.d[id]; let k = a.length - 1; while (k >= 0 && a[k] == null) k--;
      return k < 0 ? null : { id, x: x(d.y0 + k), y: y(a[k]), ty: y(a[k]) };
    }).filter(Boolean).sort((a, b) => a.y - b.y);
    for (let k = 1; k < fine.length; k++) if (fine[k].ty - fine[k - 1].ty < 14) fine[k].ty = fine[k - 1].ty + 14;
    etich.selectAll('text').data(fine).join('text').attr('class', (f) => 'etichetta-dato' + (eAggregato(f.id) ? ' etichetta-dato--tenue' : ''))
      .attr('x', (f) => f.x + 6).attr('y', (f) => f.ty + 4).text((f) => nomeRif(f.id))
      .style('opacity', (f) => (spento(f.id) && !eAggregato(f.id) ? 0.3 : 1));
    const aggiorna = (anima) => {
      const cx = x(S.anno);
      (anima && durata() ? cursore.transition().duration(durata()).ease(d3.easeLinear) : cursore).attr('x1', cx).attr('x2', cx);
      const p = ids().map((id) => ({ id, r: valore(d, id, S.anno) })).filter((o) => o.r && o.r.y === S.anno);
      punti.selectAll('circle').data(p, (o) => o.id).join('circle').attr('r', 4.5).style('stroke', colori.carta).style('stroke-width', 2)
        .style('fill', (o) => colore(o.id)).attr('cx', cx).attr('cy', (o) => y(o.r.v));
    };
    const mirino = g.append('line').attr('class', 'mirino').attr('y1', 0).attr('y2', h).style('opacity', 0);
    area.on('pointermove', (ev) => {
      const [px] = d3.pointer(ev, g.node());
      const anno = Math.round(x.invert(px)); if (anno < d.y0 || anno > d.y1) return;
      mirino.attr('x1', x(anno)).attr('x2', x(anno)).style('opacity', 1);
      const righe = ids().map((id) => ({ id, v: d.d[id][anno - d.y0] })).sort((a, b) => (b.v ?? -Infinity) - (a.v ?? -Infinity));
      mostraSuggerimento(ev, `<div class="suggerimento__nome">${anno}</div>` + righe.map((o) => `<div class="suggerimento__riga"><span><span class="pallino" style="background:${colore(o.id)}"></span>${esc(nomeRif(o.id))}</span><strong>${o.v == null ? 'n.d.' : fmt(o.v)}</strong></div>`).join('') + `<div class="suggerimento__nota">${esc(IND[cod].u)}. Tocca per portare la linea del tempo al ${anno}.</div>`);
    }).on('pointerleave', () => { mirino.style('opacity', 0); nascondiSuggerimento(); })
      .on('click', (ev) => { const [px] = d3.pointer(ev, g.node()); ferma(); impostaAnno(Math.round(x.invert(px)), true); });
    aggiorna(false);
    return { aggiorna };
  };

  // BARRE
  VISTE_IMPL.barre = function (el) {
    const cod = S.ind, d = cache[cod + '_r'], st = stat(cod), univ = universo(cod);
    const box = document.createElement('div'); box.className = 'barre'; el.appendChild(box);
    const com = document.createElement('div'); com.className = 'barre__ordine';
    com.innerHTML = `<button class="pulsante" type="button" data-o="desc" aria-pressed="${S.ordineBarre === 'desc'}">Valori più alti</button><button class="pulsante" type="button" data-o="asc" aria-pressed="${S.ordineBarre === 'asc'}">Valori più bassi</button><button class="pulsante" type="button" data-tutti aria-pressed="${S.tutteBarre}">${S.tutteBarre ? 'Mostra i primi 25' : 'Mostra tutti'}</button>`;
    box.appendChild(com);
    com.querySelectorAll('[data-o]').forEach((b) => b.addEventListener('click', () => { S.ordineBarre = b.dataset.o; cambia(); }));
    com.querySelector('[data-tutti]').addEventListener('click', () => { S.tutteBarre = !S.tutteBarre; cambia(); });
    const ol = document.createElement('ol'); ol.className = 'barre__lista'; ol.setAttribute('aria-label', `${IND[cod].n}, classifica`); box.appendChild(ol);
    const lo = Math.min(0, st.min), hi = Math.max(0, st.max);
    const pct = (v) => ((v - lo) / (hi - lo || 1)) * 100;
    const righe = new Map();
    const PASSO = 30;
    const sep = document.createElement('li'); sep.className = 'barre__separatore'; sep.setAttribute('aria-hidden', 'true'); sep.innerHTML = '<span>Selezionati fuori classifica</span>';
    const aggiorna = () => {
      const tutti = univ.map((p) => ({ id: p.id, r: valore(d, p.id, S.anno) })).filter((o) => o.r)
        .sort((a, b) => (S.ordineBarre === 'desc' ? b.r.v - a.r.v : a.r.v - b.r.v));
      const N = S.tutteBarre ? tutti.length : 25;
      const vis = tutti.slice(0, N);
      const extra = tutti.slice(N).filter((o) => selezionato(cod, o.id));
      const elenco = vis.map((o, k) => ({ ...o, pos: k, rank: k + 1 })).concat(extra.map((o, k) => ({ ...o, pos: N + 1 + k, rank: tutti.indexOf(o) + 1 })));
      const vivi = new Set(elenco.map((o) => o.id));
      for (const [id, li] of righe) if (!vivi.has(id)) { li.remove(); righe.delete(id); }
      elenco.forEach((o) => {
        let li = righe.get(o.id);
        if (!li) {
          li = document.createElement('li'); li.className = 'barra';
          li.innerHTML = `<button type="button"><span class="barra__nome"></span><span class="barra__traccia"><span class="barra__riempimento"></span><span class="barra__valore"></span></span></button>`;
          li.style.transform = `translateY(${o.pos * PASSO}px)`;
          li.querySelector('button').addEventListener('click', () => attivaDaVista(o.id));
          li.querySelector('button').addEventListener('pointermove', (ev) => mostraSuggerimento(ev, intestazioneSug(o.id) + rigaValore(cod, o.id)));
          li.querySelector('button').addEventListener('pointerleave', nascondiSuggerimento);
          ol.appendChild(li); righe.set(o.id, li);
        }
        const sel = selezionato(cod, o.id);
        li.classList.toggle('barra--sel', sel); li.classList.toggle('barra--spenta', !!spento(o.id));
        li.querySelector('button').setAttribute('aria-pressed', sel ? 'true' : 'false');
        li.querySelector('button').setAttribute('aria-label', `${o.rank}° ${T[o.id].n}: ${fmt(o.r.v)} ${IND[cod].u}${o.r.y !== S.anno ? `, dato del ${o.r.y}` : ''}. ${sel ? 'Selezionato' : 'Seleziona'}`);
        li.querySelector('.barra__nome').textContent = `${o.rank}. ${T[o.id].n}`;
        const f = li.querySelector('.barra__riempimento');
        const z = pct(0), p = pct(o.r.v);
        f.style.marginLeft = `calc((100% - 76px) * ${Math.min(z, p) / 100})`; f.style.width = `calc((100% - 76px) * ${Math.abs(p - z) / 100})`;
        f.style.backgroundColor = coloreRegione(o.id);
        li.querySelector('.barra__valore').textContent = fmt(o.r.v) + (o.r.y !== S.anno ? ` (${o.r.y})` : '');
        li.style.transform = `translateY(${o.pos * PASSO}px)`;
        li.style.transitionDuration = durata() + 'ms';
        f.style.transitionDuration = durata() + 'ms';
      });
      if (extra.length) { sep.style.top = (N * PASSO + PASSO / 2 - 2) + 'px'; ol.appendChild(sep); } else sep.remove();
      ol.style.height = (elenco.length + (extra.length ? 1 : 0)) * PASSO + 'px';
      if (!elenco.length) ol.innerHTML = `<li class="nota-piccola" style="padding:24px">Nessun territorio ha un valore nel ${S.anno}.</li>`;
    };
    aggiorna();
    return { aggiorna, selezione: aggiorna };
  };

  // TABELLA
  VISTE_IMPL.tabella = function (el) {
    const cod = S.ind, d = cache[cod + '_r'], eu = fam(cod) === 'eu';
    const box = document.createElement('div'); box.className = 'tabella'; box.tabIndex = 0; box.setAttribute('role', 'region'); box.setAttribute('aria-label', 'Tabella dei valori'); el.appendChild(box);
    const conf = () => (S.anno - 10 >= d.y0 ? S.anno - 10 : d.y0);
    const cols = [
      { k: 'sel', t: '<span class="visivo-nascosto">Selezione</span>' },
      { k: 'n', t: 'Territorio' }, { k: 'r', t: eu ? 'Paese' : 'Regione' },
      { k: 'v', t: () => `Valore ${S.anno}`, num: true }, { k: 'y', t: 'Anno del dato', num: true },
      { k: 'c', t: () => `Valore ${conf()}`, num: true }, { k: 'dv', t: 'Variazione', num: true }, { k: 'pos', t: 'Posizione', num: true },
    ];
    const tab = document.createElement('table');
    const cap = document.createElement('caption'); cap.className = 'visivo-nascosto'; tab.appendChild(cap);
    const thead = tab.createTHead(), tbody = tab.createTBody(); box.appendChild(tab);
    const gruppo = (t) => (eu ? (t.p && T[t.p] ? T[t.p].n : 'aggregato') : t.k === 'agg' ? 'aggregato' : t.r ? REG[t.r].n : 'non classificata');
    const aggiorna = () => {
      cap.textContent = `${IND[cod].n}, ${IND[cod].u}, anno ${S.anno}`;
      let insieme = universo(cod);
      if (S.aggregati) insieme = insieme.concat(META.territori.filter((t) => (eu ? t.k === 'agg_eu' : fam(cod) === 'wb' && t.k === 'agg')));
      const righe = insieme.map((t) => {
        const r = valore(d, t.id, S.anno), c = d.d[t.id] ? d.d[t.id][conf() - d.y0] : null;
        return { t, v: r ? r.v : null, y: r ? r.y : null, c: c ?? null, dv: r && c != null ? r.v - c : null };
      });
      righe.filter((o) => o.v != null && !eAggregato(o.t.id)).sort((a, b) => b.v - a.v).forEach((o, k) => { o.pos = k + 1; });
      const { col, dir } = S.ordTab, s = dir === 'asc' ? 1 : -1;
      const chiave = (o) => (col === 'n' ? o.t.n : col === 'r' ? gruppo(o.t) : col === 'sel' ? (selezionato(cod, o.t.id) ? 0 : 1) : o[col]);
      righe.sort((a, b) => {
        const x = chiave(a), y = chiave(b);
        if (x == null && y == null) return a.t.n.localeCompare(b.t.n, 'it');
        if (x == null) return 1; if (y == null) return -1;
        return typeof x === 'string' ? s * x.localeCompare(y, 'it') : s * (x - y);
      });
      thead.innerHTML = '<tr>' + cols.map((c) => `<th scope="col" class="${c.num ? 'num' : ''}" ${S.ordTab.col === c.k ? `aria-sort="${dir === 'asc' ? 'ascending' : 'descending'}"` : ''}><button type="button" data-k="${c.k}">${typeof c.t === 'function' ? c.t() : c.t}</button></th>`).join('') + '</tr>';
      thead.querySelectorAll('button').forEach((b) => b.addEventListener('click', () => {
        const k = b.dataset.k;
        S.ordTab = { col: k, dir: S.ordTab.col === k ? (S.ordTab.dir === 'desc' ? 'asc' : 'desc') : (k === 'n' || k === 'r' || k === 'pos' ? 'asc' : 'desc') };
        aggiorna(); thead.querySelector(`[data-k="${k}"]`).focus();
      }));
      const nd = '<span class="nd">n.d.</span>';
      tbody.innerHTML = righe.map((o) => {
        const sel = selezionato(cod, o.t.id);
        const dv = o.dv == null ? nd : (o.dv > 0 ? '+' : o.dv < 0 ? '−' : '') + fmt(Math.abs(o.dv));
        return `<tr class="${sel ? 'riga--sel' : ''} ${eAggregato(o.t.id) ? 'agg' : ''}" style="${spento(o.t.id) ? 'opacity:.4' : ''}">
          <td><input type="checkbox" data-id="${esc(o.t.id)}" ${sel ? 'checked' : ''} aria-label="Seleziona ${esc(o.t.n)}"></td>
          <td><span class="pallino" style="background:${coloreRegione(o.t.id)}"></span>${esc(o.t.n)}${eu && o.t.cod ? ` <span class="anno-dato">${esc(o.t.cod)}</span>` : ''}</td>
          <td>${esc(gruppo(o.t))}</td>
          <td class="num"><strong>${o.v == null ? nd : fmt(o.v)}</strong></td>
          <td class="num anno-dato">${o.y ?? nd}</td>
          <td class="num">${o.c == null ? nd : fmt(o.c)}</td>
          <td class="num">${dv}</td>
          <td class="num">${o.pos ?? '<span class="nd">·</span>'}</td></tr>`;
      }).join('');
      tbody.querySelectorAll('input[type="checkbox"]').forEach((c) => c.addEventListener('change', () => attivaDaVista(c.dataset.id)));
    };
    aggiorna();
    return { aggiorna, selezione: aggiorna };
  };

  // CONFRONTA: righe = indicatori del gruppo scelto, colonne = territori selezionati
  VISTE_IMPL.confronta = function (el) {
    const box = document.createElement('div'); box.className = 'confronto'; el.appendChild(box);
    const aggiorna = () => {
      const terr = S.sel.filter((id) => T[id]);
      if (terr.length < 2) {
        box.innerHTML = `<div class="confronto__vuoto"><p>Per confrontare servono almeno due territori. Cercali nel pannello, toccali sulla mappa oppure parti da qui:</p><div class="confronto__preset">${PRESET.map((p) => `<button class="pulsante" type="button" data-p="${p.ids.join(',')}">${esc(p.n)}</button>`).join('')}</div></div>`;
        box.querySelectorAll('[data-p]').forEach((b) => b.addEventListener('click', () => applicaPreset(b.dataset.p)));
        return;
      }
      const inds = Object.values(IND).filter((i) => i.t === S.temaConf).sort((a, b) => a.n.localeCompare(b.n, 'it'));
      const righe = inds.map((i) => {
        const d = cache[i.c + '_r'];
        const celle = terr.map((id) => { const k = idPer(i.c, id); return { id, k, r: k ? valore(d, k, S.anno) : null }; });
        const vals = celle.filter((c) => c.r).map((c) => c.r.v);
        return { i, celle, max: vals.length ? d3.max(vals, Math.abs) : 0, conDati: vals.length };
      }).filter((r) => r.conDati > 0);
      const vuote = inds.length - righe.length;
      box.innerHTML = `<table class="confronto__tabella"><caption class="visivo-nascosto">Confronto fra territori, anno ${S.anno}</caption>
        <thead><tr><th scope="col">Indicatore</th>${terr.map((id) => `<th scope="col"><span class="pallino${eAggregato(id) ? ' pallino--agg' : ''}" style="${eAggregato(id) ? '' : `background:${coloreRegione(id)}`}"></span>${esc(T[id].n)}<span class="confronto__tipo">${esc(descriviTerritorio(T[id]))}</span></th>`).join('')}</tr></thead>
        <tbody>${righe.map((rg) => `<tr><th scope="row"><span class="confronto__ind">${esc(rg.i.n)}</span><span class="confronto__unita">${esc(rg.i.u)}</span></th>${rg.celle.map((c) => {
          if (!c.k) return `<td class="nd" title="Indicatore non disponibile per questo tipo di territorio">non previsto</td>`;
          if (!c.r) return `<td class="nd">n.d.</td>`;
          const larg = rg.max ? Math.abs(c.r.v) / rg.max * 100 : 0;
          const top = rg.conDati > 1 && Math.abs(c.r.v) === rg.max;
          return `<td><span class="confronto__valore${top ? ' confronto__valore--max' : ''}">${fmt(c.r.v)}</span>${c.r.y !== S.anno ? `<span class="confronto__anno">dato del ${c.r.y}</span>` : ''}<span class="confronto__barra"><span style="width:${larg}%;background:${eAggregato(c.id) ? colori.grafite : coloreRegione(c.id)}"></span></span></td>`;
        }).join('')}</tr>`).join('')}</tbody></table>
        <p class="nota-piccola confronto__nota">▲ valore più alto fra i territori selezionati. “Non previsto”: l’indicatore non esiste per quel tipo di territorio.${vuote ? ` ${vuote} indicatori del gruppo non hanno dati per questi territori nel ${S.anno} e non sono mostrati.` : ''}</p>`;
    };
    aggiorna();
    return { aggiorna, selezione: aggiorna };
  };
  const PRESET = [
    { n: 'Friuli Venezia Giulia e vicini', ids: ['EU:ITH4', 'EU:ITH3', 'EU:AT21', 'EU:SI04', 'EU:IT'] },
    { n: 'Province del Friuli Venezia Giulia', ids: ['EU:ITH41', 'EU:ITH42', 'EU:ITH43', 'EU:ITH44', 'EU:ITH4'] },
    { n: 'Italia e grandi paesi europei', ids: ['ITA', 'DEU', 'FRA', 'ESP', 'EUU'] },
    { n: 'Regioni del mondo', ids: ['SSF', 'ECS', 'SAS', 'EAS', 'LCN', 'NAC', 'MEA', 'WLD'] },
  ];

  avvia();
})();
