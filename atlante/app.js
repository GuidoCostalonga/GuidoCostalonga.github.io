/* Atlante dei dati: viste Mappa, Bolle, Linee, Barre e Tabella su dati Banca Mondiale (lotti 1 e 2). */
(function () {
  'use strict';
  document.documentElement.lang = 'it';

  const $ = (s) => document.querySelector(s);
  const VISTE = ['mappa', 'bolle', 'linee', 'barre', 'tabella'];
  const RITARDO_MAX = 10;
  const PREDEFINITI = { ind: 'SP.DYN.LE00.IN', indX: 'SP.DYN.TFRT.IN', indR: 'SP.POP.TOTL', sel: ['ITA', 'CHN', 'IND', 'USA', 'NGA', 'BRA'] };
  const MESI = ['gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno', 'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre'];
  const movimentoRidotto = matchMedia('(prefers-reduced-motion: reduce)');

  const S = {
    vista: 'mappa', ind: PREDEFINITI.ind, indX: PREDEFINITI.indX, indR: PREDEFINITI.indR,
    anno: 2024, sel: PREDEFINITI.sel.slice(), regione: null, ultimo: false, aggregati: false,
    log: {}, ordineBarre: 'desc', tutteBarre: false, ordTab: { col: 'v', dir: 'desc' }, inRiproduzione: false,
  };

  let META, T = {}, PAESI = [], IND = {}, REG = {}, colori = {}, geo = null;
  const cache = {}, cacheNote = {}, notaRisolta = {}, statistiche = {};
  let vista = null;

  /* ---------- memoria locale (facoltativa) ---------- */
  function leggiStato() {
    try {
      const s = JSON.parse(localStorage.getItem('atlante-dati') || 'null');
      if (s) for (const k of ['ind', 'indX', 'indR', 'sel', 'ultimo', 'aggregati']) if (s[k] !== undefined) S[k] = s[k];
    } catch (e) { /* memoria non disponibile */ }
  }
  function salvaStato() {
    try { localStorage.setItem('atlante-dati', JSON.stringify({ ind: S.ind, indX: S.indX, indR: S.indR, sel: S.sel, ultimo: S.ultimo, aggregati: S.aggregati })); } catch (e) { /* ignora */ }
  }

  /* ---------- formati ---------- */
  const nf = (o) => new Intl.NumberFormat('it-IT', o);
  const F = { int: nf({ maximumFractionDigits: 0 }), d1: nf({ maximumFractionDigits: 1 }), d2: nf({ maximumFractionDigits: 2 }), s3: nf({ maximumSignificantDigits: 3 }) };
  function fmt(v) {
    if (v == null || Number.isNaN(v)) return 'n.d.';
    const a = Math.abs(v);
    if (a >= 1e9) return F.s3.format(v / 1e9) + (a >= 2e9 ? ' miliardi' : ' miliardo');
    if (a >= 1e6) return F.s3.format(v / 1e6) + (a >= 2e6 ? ' milioni' : ' milione');
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

  /* ---------- colori dal tema ---------- */
  function leggiColori() {
    const cs = getComputedStyle(document.documentElement);
    const g = (n) => cs.getPropertyValue(n).trim();
    colori = { seq0: g('--seq-0'), seq1: g('--seq-1'), grafite: g('--grafite'), carta: g('--carta'), vuoto: g('--vuoto'), trama: g('--vuoto-trama'), altro: g('--carta-3'), neutro: g('--grafite-3') };
    META.regioni.forEach((r, i) => { colori[r.k] = g('--r' + (i + 1)); });
  }
  const coloreRegione = (id) => (T[id] && T[id].r ? colori[T[id].r] : T[id] && T[id].k === 'paese' ? colori.neutro : colori.grafite);

  /* ---------- dati ---------- */
  async function carica(cod) {
    if (!cache[cod]) cache[cod] = fetch(`dati/indicatori/${cod}.json`).then((r) => { if (!r.ok) throw new Error(cod); return r.json(); });
    const d = await cache[cod];
    if (!statistiche[cod]) statistiche[cod] = calcolaStatistiche(d);
    return d;
  }
  function caricaNote(cod) {
    if (!IND[cod].note) return Promise.resolve(null);
    if (!cacheNote[cod]) cacheNote[cod] = fetch(`dati/note/${cod}.json`).then((r) => (r.ok ? r.json() : null)).catch(() => null).then((n) => { notaRisolta[cod] = n; return n; });
    return cacheNote[cod];
  }
  function calcolaStatistiche(d) {
    const vals = [], perAnno = new Array(d.y1 - d.y0 + 1).fill(0);
    for (const p of PAESI) {
      const a = d.d[p.id]; if (!a) continue;
      a.forEach((v, i) => { if (v != null) { vals.push(v); perAnno[i]++; } });
    }
    vals.sort((x, y) => x - y);
    const q = (p) => vals[Math.min(vals.length - 1, Math.max(0, Math.round(p * (vals.length - 1))))];
    const min = vals[0], max = vals[vals.length - 1];
    const positivi = min > 0;
    return { min, max, p02: q(0.02), p98: q(0.98), positivi, perAnno, logAuto: positivi && q(0.95) / q(0.05) > 150 };
  }
  function valore(d, id, anno) {
    const a = d && d.d[id]; if (!a) return null;
    const i = anno - d.y0;
    if (i >= 0 && i < a.length && a[i] != null) return { v: a[i], y: anno };
    if (S.ultimo) for (let k = 1; k <= RITARDO_MAX; k++) { const j = i - k; if (j >= 0 && j < a.length && a[j] != null) return { v: a[j], y: anno - k }; }
    return null;
  }
  function usaLog(cod) {
    const st = statistiche[cod]; if (!st || !st.positivi) return false;
    return S.log[cod] != null ? S.log[cod] : st.logAuto;
  }

  /* ---------- avvio ---------- */
  async function avvia() {
    $('#carico').hidden = false;
    try {
      [META, geo] = await Promise.all([fetch('dati/meta.json').then((r) => r.json()), fetch('dati/mondo.topo.json').then((r) => r.json())]);
    } catch (e) {
      $('#carico').textContent = 'Impossibile leggere i file di dati. Apri il sito da un server web, non direttamente dal disco.';
      return;
    }
    META.territori.forEach((t) => { T[t.id] = t; });
    PAESI = META.territori.filter((t) => t.k === 'paese');
    META.indicatori.forEach((i) => { IND[i.c] = i; });
    META.regioni.forEach((r, i) => { REG[r.k] = Object.assign({ i }, r); });
    leggiStato();
    S.sel = S.sel.filter((id) => T[id]);
    for (const k of ['ind', 'indX', 'indR']) if (!IND[S[k]]) S[k] = PREDEFINITI[k];
    const h = location.hash.replace('#', '');
    if (VISTE.includes(h)) S.vista = h;
    leggiColori();
    preparaControlli();
    await cambia({ tutto: true });
    $('#carico').hidden = true;
    const ro = new ResizeObserver(debounce(() => cambia({ tutto: true }), 150));
    ro.observe($('#scena'));
    const ricolora = () => { leggiColori(); cambia({ tutto: true }); };
    matchMedia('(prefers-color-scheme: dark)').addEventListener('change', ricolora);
    new MutationObserver(ricolora).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  }
  function debounce(f, ms) { let t; return () => { clearTimeout(t); t = setTimeout(f, ms); }; }

  /* ---------- controlli ---------- */
  function riempiSelect(sel, valore) {
    sel.innerHTML = '';
    for (const [k, nome] of Object.entries(META.temi)) {
      const g = document.createElement('optgroup'); g.label = nome;
      META.indicatori.filter((i) => i.t === k).sort((a, b) => a.n.localeCompare(b.n, 'it')).forEach((i) => {
        const o = document.createElement('option'); o.value = i.c; o.textContent = `${i.n} (${i.u})`; g.appendChild(o);
      });
      sel.appendChild(g);
    }
    sel.value = valore;
  }
  function preparaControlli() {
    riempiSelect($('#ind-principale'), S.ind);
    riempiSelect($('#ind-x'), S.indX);
    riempiSelect($('#ind-r'), S.indR);
    $('#ind-principale').addEventListener('change', (e) => { S.ind = e.target.value; salvaStato(); cambia({ tutto: true }); });
    $('#ind-x').addEventListener('change', (e) => { S.indX = e.target.value; salvaStato(); cambia({ tutto: true }); });
    $('#ind-r').addEventListener('change', (e) => { S.indR = e.target.value; salvaStato(); cambia({ tutto: true }); });
    $('#opt-ultimo').checked = S.ultimo;
    $('#opt-aggregati').checked = S.aggregati;
    $('#opt-ultimo').addEventListener('change', (e) => { S.ultimo = e.target.checked; salvaStato(); cambia({ tutto: true }); });
    $('#opt-aggregati').addEventListener('change', (e) => { S.aggregati = e.target.checked; salvaStato(); cambia({ tutto: true }); });
    $('#opt-log').addEventListener('change', (e) => { S.log[codiceLog()] = e.target.checked; cambia({ tutto: true }); });

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
      if (e.target.closest('input, select, textarea, [role="tab"], button, dialog')) return;
      if (e.key === 'ArrowLeft') { ferma(); impostaAnno(S.anno - 1, true); }
      if (e.key === 'ArrowRight') { ferma(); impostaAnno(S.anno + 1, true); }
    });

    // ricerca territori
    preparaRicerca();
    $('#svuota-selezione').addEventListener('click', () => { S.sel = []; salvaStato(); dopoSelezione(); });

    // finestra metodo
    const dlg = $('#metodo');
    $('#apri-metodo').addEventListener('click', () => (dlg.showModal ? dlg.showModal() : dlg.setAttribute('open', '')));
    $('#chiudi-metodo').addEventListener('click', () => dlg.close());
    dlg.addEventListener('click', (e) => { if (e.target === dlg) dlg.close(); });
  }

  function scegliVista(v) {
    if (!VISTE.includes(v)) return;
    S.vista = v;
    try { history.replaceState(null, '', '#' + v); } catch (e) { /* ignora */ }
    cambia({ tutto: true });
  }

  function preparaRicerca() {
    const inp = $('#cerca-territorio'), ul = $('#risultati-territori');
    let risultati = [], attivo = -1;
    const chiudi = () => { ul.hidden = true; inp.setAttribute('aria-expanded', 'false'); attivo = -1; inp.removeAttribute('aria-activedescendant'); };
    const disegna = () => {
      ul.innerHTML = '';
      if (!risultati.length) {
        const li = document.createElement('li'); li.textContent = 'Nessun territorio trovato'; li.setAttribute('aria-disabled', 'true'); ul.appendChild(li);
      }
      risultati.forEach((t, i) => {
        const li = document.createElement('li');
        li.id = 'ris-' + t.id; li.setAttribute('role', 'option'); li.setAttribute('aria-selected', i === attivo ? 'true' : 'false');
        const gia = S.sel.includes(t.id);
        li.innerHTML = `<span class="pallino" style="background:${coloreRegione(t.id)}"></span><span>${t.n}</span>${gia ? '<span class="gia">già selezionato</span>' : ''}<span class="tipo">${t.k === 'agg' ? 'aggregato' : (t.r ? REG[t.r].n : 'senza regione')}</span>`;
        li.addEventListener('mousedown', (e) => { e.preventDefault(); scegli(t); });
        ul.appendChild(li);
      });
      ul.hidden = false; inp.setAttribute('aria-expanded', 'true');
      if (attivo >= 0) inp.setAttribute('aria-activedescendant', 'ris-' + risultati[attivo].id);
    };
    const scegli = (t) => { attivaSelezione(t.id, true); inp.value = ''; chiudi(); };
    inp.addEventListener('input', () => {
      const q = norm(inp.value.trim());
      if (!q) { chiudi(); return; }
      risultati = META.territori.filter((t) => norm(t.n).includes(q) || norm(t.en).includes(q) || t.id.toLowerCase() === q)
        .sort((a, b) => (norm(a.n).startsWith(q) ? 0 : 1) - (norm(b.n).startsWith(q) ? 0 : 1) || (a.k === 'agg') - (b.k === 'agg') || a.n.localeCompare(b.n, 'it'))
        .slice(0, 12);
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

  function attivaSelezione(id, soloAggiungi) {
    const i = S.sel.indexOf(id);
    if (i >= 0) { if (soloAggiungi) return; S.sel.splice(i, 1); } else S.sel.push(id);
    salvaStato(); dopoSelezione();
  }
  function dopoSelezione() { disegnaSelezionati(); if (vista && vista.selezione) vista.selezione(); else cambia({ tutto: true }); aggiornaEvidenza(); }

  function disegnaSelezionati() {
    const ul = $('#selezionati'); ul.innerHTML = '';
    if (!S.sel.length) { const li = document.createElement('li'); li.className = 'selezionati__vuoto'; li.style.border = '0'; li.style.padding = '0'; li.textContent = 'Nessun territorio selezionato'; ul.appendChild(li); return; }
    S.sel.forEach((id) => {
      const t = T[id]; const li = document.createElement('li');
      const sw = t.k === 'agg' ? `<span class="pallino" style="background:none;border:2px dashed ${colori.grafite};width:11px;height:11px;margin:0"></span>` : `<span class="pallino" style="background:${coloreRegione(id)};margin:0"></span>`;
      li.innerHTML = `${sw}<span>${t.n}</span><button type="button" aria-label="Togli ${t.n}"><svg viewBox="0 0 12 12"><path d="M2.5 2.5l7 7M9.5 2.5l-7 7"/></svg></button>`;
      li.querySelector('button').addEventListener('click', () => attivaSelezione(id));
      ul.appendChild(li);
    });
  }

  /* ---------- tempo ---------- */
  let timer = null;
  function intervalloAnni() {
    const a = IND[S.ind];
    if (S.vista === 'bolle') { const x = IND[S.indX], y = IND[S.ind]; return [Math.max(x.y0, y.y0), Math.min(x.y1, y.y1)]; }
    return [a.y0, a.y1];
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
    const conteggi = [];
    for (let y = a0; y <= a1; y++) conteggi.push({ y, n: contaConDato(y) });
    const x = d3.scaleLinear().domain([a0, a1]).range([0, w]);
    const bw = Math.max(1, Math.min(8, w / (a1 - a0 + 1) - 1.5));
    const hy = d3.scaleLinear().domain([0, PAESI.length]).range([0, h - 2]);
    svg.attr('viewBox', `0 0 ${w} ${h}`);
    svg.selectAll('rect').data(conteggi, (d) => d.y).join('rect')
      .attr('x', (d) => (a1 === a0 ? w / 2 : x(d.y)) - bw / 2).attr('width', bw)
      .attr('y', (d) => h - Math.max(d.n ? 1.5 : 0, hy(d.n))).attr('height', (d) => Math.max(d.n ? 1.5 : 0, hy(d.n)))
      .attr('rx', 1).classed('attivo', (d) => d.y === S.anno);
  }
  function contaConDato(y) {
    const st = statistiche[S.ind]; if (!st) return 0;
    if (S.vista === 'bolle') {
      const dx = cache[S.indX + '_r'], dy = cache[S.ind + '_r'];
      if (!dx || !dy) return 0;
      return PAESI.reduce((n, p) => n + (valore(dx, p.id, y) && valore(dy, p.id, y) ? 1 : 0), 0);
    }
    const d = cache[S.ind + '_r'];
    return d ? PAESI.reduce((n, p) => n + (valore(d, p.id, y) ? 1 : 0), 0) : 0;
  }

  /* ---------- aggiornamento generale ---------- */
  async function cambia() {
    ferma();
    const caricare = [S.ind]; if (S.vista === 'bolle') caricare.push(S.indX, S.indR);
    const lento = setTimeout(() => { $('#carico').hidden = false; }, 200);
    let dati;
    try { dati = await Promise.all(caricare.map(carica)); } catch (e) {
      clearTimeout(lento); $('#carico').hidden = false; $('#carico').textContent = 'Non riesco a caricare l’indicatore richiesto. Riprova tra poco.'; return;
    }
    clearTimeout(lento); $('#carico').hidden = true;
    caricare.forEach((c, i) => { cache[c + '_r'] = dati[i]; });
    caricaNote(S.ind);

    const [a0, a1] = intervalloAnni();
    S.anno = Math.max(a0, Math.min(a1, S.anno));

    // stato visivo dei controlli
    document.querySelector('.app').dataset.vista = S.vista;
    $('#scena').dataset.vista = S.vista;
    document.querySelectorAll('.schede [role="tab"]').forEach((b) => {
      const on = b.dataset.vista === S.vista;
      b.setAttribute('aria-selected', on ? 'true' : 'false'); b.tabIndex = on ? 0 : -1;
    });
    $('#scena').setAttribute('aria-labelledby', 'tab-' + S.vista + ' titolo-vista');
    $('#etichetta-principale').textContent = S.vista === 'bolle' ? 'Asse verticale' : 'Indicatore';
    for (const [id, v] of [['#ind-principale', S.ind], ['#ind-x', S.indX], ['#ind-r', S.indR]]) $(id).value = v;
    const cl = codiceLog(), st = statistiche[cl];
    $('#riga-log').hidden = !['mappa', 'bolle', 'linee'].includes(S.vista);
    $('#opt-log').disabled = !st.positivi;
    $('#opt-log').checked = usaLog(cl);
    $('#riga-log').querySelector('span').textContent = S.vista === 'bolle' ? 'Scala logaritmica sull’asse orizzontale' : 'Scala logaritmica';
    $('#riga-log').title = st.positivi ? '' : 'Non disponibile: l’indicatore contiene valori nulli o negativi';
    $('#riga-aggregati').hidden = S.vista !== 'tabella';

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

  function intestazione() {
    const i = IND[S.ind];
    if (S.vista === 'bolle') {
      $('#titolo-vista').textContent = `${i.n} e ${IND[S.indX].n.toLowerCase()}`;
      $('#unita-vista').textContent = `dimensione: ${IND[S.indR].n.toLowerCase()}`;
    } else {
      $('#titolo-vista').textContent = i.n;
      $('#unita-vista').textContent = i.u;
    }
    const usati = S.vista === 'bolle' ? [S.ind, S.indX, S.indR] : [S.ind];
    $('#sd-unita').innerHTML = S.vista === 'bolle'
      ? `verticale: ${IND[S.ind].u}<br>orizzontale: ${IND[S.indX].u}<br>dimensione: ${IND[S.indR].u}`
      : i.u;
    $('#sd-periodo').innerHTML = usati.map((c) => `${S.vista === 'bolle' ? IND[c].n + ': ' : ''}dal ${IND[c].y0} al ${IND[c].y1}`).join('<br>');
    $('#sd-fonte').innerHTML = `Banca Mondiale, WDI<br>${usati.map((c) => `<a href="${IND[c].url}" target="_blank" rel="noopener">${c}</a>`).join(' · ')}`;
    $('#sd-agg').innerHTML = `fonte: ${data(i.agg)}<br>acquisizione: ${data(META.acquisizione[String(i.lotto)])}, lotto ${i.lotto}`;
    $('#def-testo').textContent = i.def ? `“${i.def}”` : 'Definizione non presente nella fonte.';
    $('#def-fonte').innerHTML = `Testo originale in inglese della Banca Mondiale per <strong>${i.en}</strong>. Fonte originaria dichiarata: ${(i.org || 'n.d.').replace(/, uri: \S+/g, '').replace(/\n/g, '; ')}. Osservazioni nei file: ${F.int.format(i.righe)} righe, ${F.int.format(i.valori)} con valore numerico.`;
  }

  function territoriVista() { return PAESI; }
  function mancanti() {
    const out = [];
    const dy = cache[S.ind + '_r'];
    const dx = S.vista === 'bolle' ? cache[S.indX + '_r'] : null;
    for (const p of territoriVista()) {
      const ok = valore(dy, p.id, S.anno) && (!dx || valore(dx, p.id, S.anno));
      if (!ok) out.push(p);
    }
    return out;
  }
  function aggiornaMancanti() {
    const m = mancanti(), tot = PAESI.length;
    $('#sd-mancanti').innerHTML = `<span class="campione-vuoto" aria-hidden="true"></span><span>${m.length} paesi su ${tot} senza dato nel ${S.anno}${S.ultimo ? ' (anche cercando fino a 10 anni prima)' : ''}</span>`;
    $('#def-mancanti').textContent = m.length ? m.map((p) => p.n).sort((a, b) => a.localeCompare(b, 'it')).join(', ') + '.' : 'Nessuno: tutti i paesi hanno un valore.';
    disegnaCopertura(...intervalloAnni());
  }
  function aggiornaEvidenza() {
    const d = cache[S.ind + '_r'], i = IND[S.ind]; if (!d) return;
    const righe = PAESI.map((p) => ({ p, r: valore(d, p.id, S.anno) })).filter((x) => x.r).sort((a, b) => b.r.v - a.r.v);
    const el = $('#evidenza');
    if (!righe.length) { el.textContent = `Nel ${S.anno} nessun paese ha un valore per questo indicatore. Prova un altro anno o attiva l’ultimo dato disponibile.`; return; }
    const alto = righe[0], basso = righe[righe.length - 1];
    let t = `Nel <strong>${S.anno}</strong>: valore più alto <strong>${alto.p.n}</strong> (${fmt(alto.r.v)}), più basso <strong>${basso.p.n}</strong> (${fmt(basso.r.v)}), su ${righe.length} paesi con dato.`;
    const primo = S.sel.find((id) => T[id].k === 'paese');
    if (primo) {
      const k = righe.findIndex((x) => x.p.id === primo);
      t += k >= 0 ? ` ${T[primo].n}: <strong>${fmt(righe[k].r.v)}</strong>, ${k + 1}° posto su ${righe.length}${righe[k].r.y !== S.anno ? ` (dato del ${righe[k].r.y})` : ''}.` : ` ${T[primo].n}: nessun dato nel ${S.anno}.`;
    }
    el.innerHTML = t;
  }

  /* ---------- legenda ---------- */
  function disegnaLegenda() {
    const lv = $('#legenda-valori'); lv.innerHTML = '';
    const nota = $('#legenda-regioni-nota');
    if (S.vista === 'mappa' && vista.scala) {
      const sc = vista.scala, w = 300, h = 46;
      const svg = d3.select(lv).append('svg').attr('viewBox', `0 0 ${w} ${h + 48}`).attr('role', 'img').attr('aria-label', `Scala dei colori da ${fmt(sc.domain()[0])} a ${fmt(sc.domain()[1])} ${IND[S.ind].u}`);
      const id = 'grad-' + Math.random().toString(36).slice(2, 7);
      const g = svg.append('defs').append('linearGradient').attr('id', id);
      d3.range(0, 1.0001, 0.1).forEach((t) => g.append('stop').attr('offset', t).attr('stop-color', sc.interpolator()(t)));
      svg.append('text').attr('x', 0).attr('y', 11).text(IND[S.ind].u);
      svg.append('rect').attr('x', 0).attr('y', 18).attr('width', w).attr('height', 12).attr('rx', 3).style('fill', `url(#${id})`);
      const xs = (usaLog(S.ind) ? d3.scaleLog() : d3.scaleLinear()).domain(sc.domain()).range([0, w]);
      const usati = usaLog(S.ind) ? xs.ticks(4).filter((v) => /^[125]/.test(String(v).replace(/^0\.0*/, ''))).slice(0, 6) : xs.ticks(4);
      usati.forEach((v) => {
        const x = xs(v); if (x < 0 || x > w) return;
        svg.append('line').attr('x1', x).attr('x2', x).attr('y1', 30).attr('y2', 34).style('stroke', colori.grafite);
        svg.append('text').attr('x', x).attr('y', 46).attr('text-anchor', x < 15 ? 'start' : x > w - 15 ? 'end' : 'middle').text(fmtAsse(v));
      });
      svg.append('rect').attr('x', 0).attr('y', 56).attr('width', 18).attr('height', 12).attr('rx', 2).style('fill', 'url(#trama-legenda)');
      const defs = svg.select('defs');
      trama(defs, 'trama-legenda');
      svg.append('text').attr('x', 24).attr('y', 66).text('Dato mancante');
      svg.append('rect').attr('x', 130).attr('y', 56).attr('width', 18).attr('height', 12).attr('rx', 2).style('fill', colori.altro);
      svg.append('text').attr('x', 154).attr('y', 66).text('Fuori dai dati');
      svg.append('text').attr('x', 0).attr('y', 88).text('Scala fissa per tutti gli anni; oltre gli estremi il colore resta pieno.');
      nota.textContent = 'Tocca una regione per metterla in risalto sulla mappa.';
    } else if (S.vista === 'bolle' && vista.raggio) {
      const r = vista.raggio, vals = niceSizes(r.domain()[1]);
      const maxR = r(vals[0]), w = 300, h = maxR * 2 + 26;
      const svg = d3.select(lv).append('svg').attr('viewBox', `0 0 ${w} ${h}`).attr('class', 'legenda-dimensione').attr('role', 'img').attr('aria-label', 'Dimensione delle bolle');
      svg.append('text').attr('x', 0).attr('y', 11).text(`Dimensione: ${IND[S.indR].n.toLowerCase()}`);
      vals.forEach((v, k) => {
        const rr = r(v), cy = 18 + maxR * 2 - rr;
        svg.append('circle').attr('cx', maxR + 2).attr('cy', cy).attr('r', rr);
        svg.append('line').attr('x1', maxR + 2).attr('x2', maxR * 2 + 16).attr('y1', cy - rr).attr('y2', cy - rr).style('stroke', colori.grafite).style('stroke-opacity', .3);
        svg.append('text').attr('x', maxR * 2 + 20).attr('y', cy - rr + 4).text(fmt(v));
      });
      nota.textContent = 'Colore = regione. Tocca una regione per metterla in risalto.';
    } else if (S.vista === 'linee') {
      lv.innerHTML = `<svg viewBox="0 0 300 40" role="img" aria-label="Legenda delle linee"><line x1="0" x2="26" y1="10" y2="10" class="linea linea--mondo"></line><text x="34" y="14">Mondo, media della Banca Mondiale</text><line x1="0" x2="26" y1="30" y2="30" style="stroke:${colori.grafite};stroke-opacity:.25"></line><text x="34" y="34">Tutti gli altri paesi</text></svg>`;
      nota.textContent = 'Colore = regione dei paesi selezionati. Tocca una regione per metterla in risalto.';
    } else {
      nota.textContent = 'Colore = regione. Tocca una regione per metterla in risalto.';
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
    const altri = PAESI.filter((p) => !p.r).length;
    if (altri) { const li = document.createElement('li'); li.innerHTML = `<button type="button" disabled style="cursor:default"><span class="pallino" style="background:${colori.neutro}"></span><span>Senza regione</span><span class="conta">${altri} paese</span></button>`; ul.appendChild(li); }
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
  function rigaValore(cod, id, mostraNome) {
    const d = cache[cod + '_r'], r = valore(d, id, S.anno), i = IND[cod];
    const nota = notaDi(cod, id, r && r.y);
    return `${mostraNome ? `<div class="suggerimento__riga" style="margin-top:4px"><span>${i.n}</span></div>` : ''}
      ${r ? `<div class="suggerimento__valore">${fmt(r.v)}</div><div class="suggerimento__riga"><span>${i.u}</span></div>${r.y !== S.anno ? `<div class="suggerimento__nota">Ultimo dato disponibile: ${r.y}</div>` : ''}` : `<div class="suggerimento__vuoto">Dato mancante nel ${S.anno}</div>`}
      ${nota ? `<div class="suggerimento__nota">Nota della fonte: ${nota}</div>` : ''}`;
  }
  function notaDi(cod, id, y) {
    const n = notaRisolta[cod];
    const x = y && n && n[id] && n[id][y];
    return x ? [x[0], x[1]].filter(Boolean).join(' · ') : null;
  }
  function intestazioneSug(id) {
    const t = T[id];
    return `<div class="suggerimento__nome"><span class="pallino" style="background:${coloreRegione(id)};margin:0"></span>${t.n}</div><div class="suggerimento__riga"><span>${t.k === 'agg' ? 'Aggregato della Banca Mondiale' : t.r ? REG[t.r].n : 'Regione non classificata'}</span></div>`;
  }
  const spento = (id) => S.regione && T[id] && T[id].r !== S.regione;

  /* ---------- viste ---------- */
  const VISTE_IMPL = {};

  // MAPPA
  VISTE_IMPL.mappa = function (el) {
    const d = cache[S.ind + '_r'], st = statistiche[S.ind];
    const w = el.clientWidth, h = el.clientHeight;
    const log = usaLog(S.ind);
    const interp = d3.interpolateLab(colori.seq0, colori.seq1);
    const scala = (log ? d3.scaleSequentialLog(interp) : d3.scaleSequential(interp)).domain(st.p02 < st.p98 ? [st.p02, st.p98] : [st.min, st.max]).clamp(true);
    const fc = topojson.feature(geo, geo.objects[Object.keys(geo.objects)[0]]);
    fc.features = fc.features.filter((f) => f.properties.id !== 'NE_ATA');
    const proj = d3.geoEqualEarth().fitExtent([[6, 6], [w - 6, h - 6]], fc);
    const path = d3.geoPath(proj);
    const svg = d3.select(el).append('svg').attr('viewBox', `0 0 ${w} ${h}`).attr('role', 'img')
      .attr('aria-label', `Mappa del mondo: ${IND[S.ind].n}. La tabella offre gli stessi valori in forma testuale.`);
    trama(svg.append('defs'), 'trama');
    const g = svg.append('g');
    const paesi = g.selectAll('path').data(fc.features).join('path')
      .attr('d', path).attr('class', (f) => 'paese' + (T[f.properties.id] ? '' : ' paese--altro'))
      .style('vector-effect', 'non-scaling-stroke');
    const colora = (anima) => {
      paesi.filter((f) => T[f.properties.id]).each(function (f) {
        const r = valore(d, f.properties.id, S.anno);
        const s = d3.select(this);
        const c = r ? scala(r.v) : 'url(#trama)';
        if (anima && r && durata()) s.transition().duration(durata()).ease(S.inRiproduzione ? d3.easeLinear : d3.easeCubicOut).style('fill', c);
        else s.interrupt().style('fill', c);
      });
    };
    const selezione = () => {
      paesi.classed('paese--sel', (f) => S.sel.includes(f.properties.id))
        .style('opacity', (f) => (spento(f.properties.id) ? 0.22 : 1));
      paesi.filter((f) => S.sel.includes(f.properties.id)).raise();
    };
    paesi.on('pointermove', (ev, f) => {
      const id = f.properties.id;
      if (!T[id]) { mostraSuggerimento(ev, `<div class="suggerimento__nome">${f.properties.n}</div><div class="suggerimento__vuoto">Territorio non presente nei dati della Banca Mondiale</div>`); return; }
      mostraSuggerimento(ev, intestazioneSug(id) + rigaValore(S.ind, id) + `<div class="suggerimento__nota">${S.sel.includes(id) ? 'Selezionato. Tocca per togliere.' : 'Tocca per selezionare.'}</div>`);
    }).on('pointerleave', (ev) => { if (ev.pointerType !== 'touch') nascondiSuggerimento(); })
      .on('click', (ev, f) => { if (T[f.properties.id]) { attivaSelezione(f.properties.id); d3.select(ev.currentTarget).dispatch('pointermove'); } });

    const zoom = d3.zoom().scaleExtent([1, 10]).translateExtent([[0, 0], [w, h]]).on('zoom', (e) => g.attr('transform', e.transform));
    svg.call(zoom).on('dblclick.zoom', null);
    const com = d3.select(el).append('div').attr('class', 'comandi-mappa');
    const bottone = (et, ico, f) => com.append('button').attr('class', 'pulsante').attr('type', 'button').attr('aria-label', et).html(`<svg viewBox="0 0 20 20" aria-hidden="true"><path d="${ico}"/></svg>`).on('click', f);
    bottone('Ingrandisci', 'M10 4v12 M4 10h12', () => svg.transition().duration(300).call(zoom.scaleBy, 1.6));
    bottone('Riduci', 'M4 10h12', () => svg.transition().duration(300).call(zoom.scaleBy, 1 / 1.6));
    bottone('Mostra tutto il mondo', 'M3 8V3h5 M12 3h5v5 M17 12v5h-5 M8 17H3v-5', () => svg.transition().duration(400).call(zoom.transform, d3.zoomIdentity));

    colora(false); selezione();
    return { aggiorna: colora, selezione, scala };
  };

  // BOLLE
  VISTE_IMPL.bolle = function (el) {
    const dx = cache[S.indX + '_r'], dy = cache[S.ind + '_r'], dr = cache[S.indR + '_r'];
    const sx = statistiche[S.indX], sy = statistiche[S.ind], sr = statistiche[S.indR];
    const W = el.clientWidth, H = el.clientHeight;
    const stretto = W < 560;
    const m = { t: 18, r: 18, b: 50, l: stretto ? 48 : 64 };
    const w = W - m.l - m.r, h = H - m.t - m.b;
    const pad = (a, b, lg) => (lg ? [a / 1.15, b * 1.15] : [a - (b - a) * 0.04, b + (b - a) * 0.04]);
    const lx = usaLog(S.indX), ly = sy.positivi && sy.logAuto;
    const x = (lx ? d3.scaleLog() : d3.scaleLinear()).domain(pad(sx.min, sx.max, lx)).range([0, w]);
    const y = (ly ? d3.scaleLog() : d3.scaleLinear()).domain(pad(sy.min, sy.max, ly)).range([h, 0]);
    const rMax = Math.max(14, Math.min(w, h) / (stretto ? 9 : 11));
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
    const punti = () => PAESI.map((p) => {
      const vx = valore(dx, p.id, S.anno), vy = valore(dy, p.id, S.anno);
      if (!vx || !vy) return null;
      const vr = valore(dr, p.id, S.anno);
      return { id: p.id, x: vx, y: vy, r: vr };
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
          `<div class="suggerimento__riga"><span>${IND[S.ind].n}</span><strong>${fmt(d.y.v)}</strong></div>` +
          `<div class="suggerimento__riga"><span>${IND[S.indX].n}</span><strong>${fmt(d.x.v)}</strong></div>` +
          `<div class="suggerimento__riga"><span>${IND[S.indR].n}</span><strong>${d.r ? fmt(d.r.v) : 'n.d.'}</strong></div>` +
          (d.x.y !== S.anno || d.y.y !== S.anno ? `<div class="suggerimento__nota">Anni dei dati: verticale ${d.y.y}, orizzontale ${d.x.y}</div>` : ''));
      }).on('pointerleave', (ev) => { if (ev.pointerType !== 'touch') nascondiSuggerimento(); })
        .on('click', (ev, d) => attivaSelezione(d.id));
      selezione(dati, t);
    };
    const selezione = (dati, t) => {
      dati = dati || sel.data();
      sel.classed('bolla--sel', (d) => S.sel.includes(d.id)).classed('bolla--spenta', (d) => spento(d.id) || (S.sel.length > 0 && !S.sel.includes(d.id) && !S.regione && false));
      sel.filter((d) => S.sel.includes(d.id)).raise();
      const lab = dati.filter((d) => S.sel.includes(d.id));
      const ts = etich.selectAll('text').data(lab, (d) => d.id).join((en) => en.append('text').attr('class', 'etichetta-dato').attr('x', (d) => x(d.x.v)).attr('y', (d) => y(d.y.v)));
      ts.text((d) => T[d.id].n);
      (t ? ts.transition(t) : ts).attr('x', (d) => x(d.x.v) + (d.r ? r(d.r.v) : 3) * 0.75 + 4).attr('y', (d) => y(d.y.v) - (d.r ? r(d.r.v) : 3) * 0.75);
    };
    aggiorna(false);
    return { aggiorna, selezione: () => selezione(), raggio: r };
  };

  // LINEE
  VISTE_IMPL.linee = function (el) {
    const d = cache[S.ind + '_r'], st = statistiche[S.ind];
    const W = el.clientWidth, H = el.clientHeight, stretto = W < 560;
    const m = { t: 20, r: stretto ? 86 : 150, b: 34, l: stretto ? 46 : 62 };
    const w = W - m.l - m.r, h = H - m.t - m.b;
    const lg = usaLog(S.ind);
    const anni = d3.range(d.y0, d.y1 + 1);
    let lo = st.min, hi = st.max;
    S.sel.concat(['WLD']).forEach((id) => (d.d[id] || []).forEach((v) => { if (v != null) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }));
    const x = d3.scaleLinear().domain([d.y0, Math.max(d.y1, d.y0 + 1)]).range([0, w]);
    const y = (lg ? d3.scaleLog().domain([lo / 1.1, hi * 1.1]) : d3.scaleLinear().domain([lo, hi]).nice()).range([h, 0]);
    const svg = d3.select(el).append('svg').attr('viewBox', `0 0 ${W} ${H}`).attr('role', 'img').attr('aria-label', `Andamento nel tempo: ${IND[S.ind].n}, dal ${d.y0} al ${d.y1}.`);
    const g = svg.append('g').attr('transform', `translate(${m.l},${m.t})`);
    g.append('g').attr('class', 'asse asse--base').attr('transform', `translate(0,${h})`).call(d3.axisBottom(x).ticks(stretto ? 4 : 9).tickFormat(d3.format('d')).tickSizeOuter(0));
    const ty = lg ? y.ticks(6).filter((v) => /^[125]/.test(String(v).replace(/^0\.0*/, ''))) : y.ticks(stretto ? 5 : 7);
    g.append('g').attr('class', 'asse').call(d3.axisLeft(y).tickValues(ty).tickFormat(fmtAsse).tickSize(-w).tickPadding(8));
    const linea = d3.line().defined((v) => v != null).x((v, i) => x(d.y0 + i)).y((v) => y(v));
    const fondo = g.append('g');
    fondo.selectAll('path').data(PAESI.filter((p) => d.d[p.id] && !S.sel.includes(p.id))).join('path')
      .attr('class', 'linea linea--fondo').attr('d', (p) => linea(d.d[p.id]))
      .on('pointermove', (ev, p) => mostraSuggerimento(ev, intestazioneSug(p.id) + `<div class="suggerimento__nota">Tocca per selezionare</div>`))
      .on('pointerleave', nascondiSuggerimento).on('click', (ev, p) => attivaSelezione(p.id));
    const cursore = g.append('line').attr('class', 'cursore-anno').attr('y1', 0).attr('y2', h);
    const primo = g.append('g'), punti = g.append('g'), etich = g.append('g');
    const ids = () => S.sel.filter((id) => d.d[id]).concat(!S.sel.includes('WLD') && d.d.WLD ? ['WLD'] : []);
    const colore = (id) => (T[id].k === 'agg' ? colori.grafite : coloreRegione(id));
    primo.selectAll('path').data(ids(), (id) => id).join('path')
      .attr('class', (id) => 'linea' + (T[id].k === 'agg' ? ' linea--mondo' : '')).attr('d', (id) => linea(d.d[id]))
      .style('stroke', colore).style('opacity', (id) => (spento(id) && T[id].k !== 'agg' ? 0.2 : 1));
    // etichette a fine linea, distanziate
    const fine = ids().map((id) => {
      const a = d.d[id]; let k = a.length - 1; while (k >= 0 && a[k] == null) k--;
      return k < 0 ? null : { id, x: x(d.y0 + k), y: y(a[k]), ty: y(a[k]) };
    }).filter(Boolean).sort((a, b) => a.y - b.y);
    for (let k = 1; k < fine.length; k++) if (fine[k].ty - fine[k - 1].ty < 14) fine[k].ty = fine[k - 1].ty + 14;
    etich.selectAll('text').data(fine).join('text').attr('class', (f) => 'etichetta-dato' + (T[f.id].k === 'agg' ? ' etichetta-dato--tenue' : ''))
      .attr('x', (f) => f.x + 6).attr('y', (f) => f.ty + 4).text((f) => (T[f.id].k === 'agg' && f.id === 'WLD' ? 'Mondo' : T[f.id].n))
      .style('opacity', (f) => (spento(f.id) && T[f.id].k !== 'agg' ? 0.3 : 1));
    const aggiorna = (anima) => {
      const cx = x(S.anno);
      (anima && durata() ? cursore.transition().duration(durata()).ease(d3.easeLinear) : cursore).attr('x1', cx).attr('x2', cx);
      const p = ids().map((id) => ({ id, r: valore(d, id, S.anno) })).filter((o) => o.r && o.r.y === S.anno);
      punti.selectAll('circle').data(p, (o) => o.id).join('circle').attr('r', 4.5).style('stroke', colori.carta).style('stroke-width', 2)
        .style('fill', (o) => colore(o.id)).attr('cx', cx).attr('cy', (o) => y(o.r.v));
    };
    // mirino al passaggio
    const mirino = g.append('line').attr('class', 'mirino').attr('y1', 0).attr('y2', h).style('opacity', 0);
    const area = g.append('rect').attr('width', w).attr('height', h).style('fill', 'transparent').lower();
    fondo.raise(); primo.raise(); punti.raise(); etich.raise();
    area.on('pointermove', (ev) => {
      const [px] = d3.pointer(ev, g.node());
      const anno = Math.round(x.invert(px)); if (anno < d.y0 || anno > d.y1) return;
      mirino.attr('x1', x(anno)).attr('x2', x(anno)).style('opacity', 1);
      const righe = ids().map((id) => ({ id, v: d.d[id][anno - d.y0] })).sort((a, b) => (b.v ?? -Infinity) - (a.v ?? -Infinity));
      mostraSuggerimento(ev, `<div class="suggerimento__nome">${anno}</div>` + righe.map((o) => `<div class="suggerimento__riga"><span><span class="pallino" style="background:${colore(o.id)}"></span>${o.id === 'WLD' ? 'Mondo' : T[o.id].n}</span><strong>${o.v == null ? 'n.d.' : fmt(o.v)}</strong></div>`).join('') + `<div class="suggerimento__nota">${IND[S.ind].u}. Tocca per portare la linea del tempo al ${anno}.</div>`);
    }).on('pointerleave', () => { mirino.style('opacity', 0); nascondiSuggerimento(); })
      .on('click', (ev) => { const [px] = d3.pointer(ev, g.node()); ferma(); impostaAnno(Math.round(x.invert(px)), true); });
    // l'area è sotto le linee di fondo: il mirino resta attivo sugli spazi vuoti
    aggiorna(false);
    return { aggiorna };
  };

  // BARRE
  VISTE_IMPL.barre = function (el) {
    const d = cache[S.ind + '_r'], st = statistiche[S.ind];
    const box = document.createElement('div'); box.className = 'barre'; el.appendChild(box);
    const com = document.createElement('div'); com.className = 'barre__ordine';
    com.innerHTML = `<button class="pulsante" type="button" data-o="desc" aria-pressed="${S.ordineBarre === 'desc'}">Valori più alti</button><button class="pulsante" type="button" data-o="asc" aria-pressed="${S.ordineBarre === 'asc'}">Valori più bassi</button><button class="pulsante" type="button" data-tutti aria-pressed="${S.tutteBarre}">${S.tutteBarre ? 'Mostra i primi 25' : 'Mostra tutti'}</button>`;
    box.appendChild(com);
    com.querySelectorAll('[data-o]').forEach((b) => b.addEventListener('click', () => { S.ordineBarre = b.dataset.o; cambia({ tutto: true }); }));
    com.querySelector('[data-tutti]').addEventListener('click', () => { S.tutteBarre = !S.tutteBarre; cambia({ tutto: true }); });
    const ol = document.createElement('ol'); ol.className = 'barre__lista'; ol.setAttribute('aria-label', `${IND[S.ind].n}, classifica dei paesi`); box.appendChild(ol);
    const lo = Math.min(0, st.min), hi = Math.max(0, st.max);
    const pct = (v) => ((v - lo) / (hi - lo)) * 100;
    const righe = new Map();
    const PASSO = 30;
    const sep = document.createElement('li'); sep.className = 'barre__separatore'; sep.setAttribute('aria-hidden', 'true'); sep.innerHTML = '<span>Selezionati fuori classifica</span>';
    const aggiorna = () => {
      const tutti = PAESI.map((p) => ({ id: p.id, r: valore(d, p.id, S.anno) })).filter((o) => o.r)
        .sort((a, b) => (S.ordineBarre === 'desc' ? b.r.v - a.r.v : a.r.v - b.r.v));
      const N = S.tutteBarre ? tutti.length : 25;
      const vis = tutti.slice(0, N);
      const extra = tutti.slice(N).filter((o) => S.sel.includes(o.id));
      const elenco = vis.map((o, k) => ({ ...o, pos: k, rank: k + 1 })).concat(extra.map((o, k) => ({ ...o, pos: N + 1 + k, rank: tutti.indexOf(o) + 1 })));
      const vivi = new Set(elenco.map((o) => o.id));
      for (const [id, li] of righe) if (!vivi.has(id)) { li.remove(); righe.delete(id); }
      elenco.forEach((o) => {
        let li = righe.get(o.id);
        if (!li) {
          li = document.createElement('li'); li.className = 'barra';
          li.innerHTML = `<button type="button"><span class="barra__nome"></span><span class="barra__traccia"><span class="barra__riempimento"></span><span class="barra__valore"></span></span></button>`;
          li.style.transform = `translateY(${o.pos * PASSO}px)`;
          li.querySelector('button').addEventListener('click', () => attivaSelezione(o.id));
          li.querySelector('button').addEventListener('pointermove', (ev) => mostraSuggerimento(ev, intestazioneSug(o.id) + rigaValore(S.ind, o.id)));
          li.querySelector('button').addEventListener('pointerleave', nascondiSuggerimento);
          ol.appendChild(li); righe.set(o.id, li);
        }
        const sel = S.sel.includes(o.id);
        li.classList.toggle('barra--sel', sel); li.classList.toggle('barra--spenta', !!spento(o.id));
        li.querySelector('button').setAttribute('aria-pressed', sel ? 'true' : 'false');
        li.querySelector('button').setAttribute('aria-label', `${o.rank}° ${T[o.id].n}: ${fmt(o.r.v)} ${IND[S.ind].u}${o.r.y !== S.anno ? `, dato del ${o.r.y}` : ''}. ${sel ? 'Selezionato' : 'Seleziona'}`);
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
      if (!elenco.length) ol.innerHTML = `<li class="nota-piccola" style="padding:24px">Nessun paese ha un valore nel ${S.anno}.</li>`;
    };
    aggiorna();
    return { aggiorna, selezione: aggiorna };
  };

  // TABELLA
  VISTE_IMPL.tabella = function (el) {
    const d = cache[S.ind + '_r'];
    const box = document.createElement('div'); box.className = 'tabella'; box.tabIndex = 0; box.setAttribute('role', 'region'); box.setAttribute('aria-label', 'Tabella dei valori'); el.appendChild(box);
    const conf = () => S.anno - 10 >= d.y0 ? S.anno - 10 : d.y0;
    const cols = [
      { k: 'sel', t: '<span class="visivo-nascosto">Selezione</span>' },
      { k: 'n', t: 'Territorio' }, { k: 'r', t: 'Regione' },
      { k: 'v', t: () => `Valore ${S.anno}`, num: true }, { k: 'y', t: 'Anno del dato', num: true },
      { k: 'c', t: () => `Valore ${conf()}`, num: true }, { k: 'dv', t: 'Variazione', num: true }, { k: 'pos', t: 'Posizione', num: true },
    ];
    const tab = document.createElement('table');
    const cap = document.createElement('caption'); cap.className = 'visivo-nascosto'; tab.appendChild(cap);
    const thead = tab.createTHead(), tbody = tab.createTBody(); box.appendChild(tab);
    const aggiorna = () => {
      cap.textContent = `${IND[S.ind].n}, ${IND[S.ind].u}, anno ${S.anno}`;
      const insieme = S.aggregati ? META.territori : PAESI;
      const righe = insieme.map((t) => {
        const r = valore(d, t.id, S.anno), c = d.d[t.id] ? d.d[t.id][conf() - d.y0] : null;
        return { t, v: r ? r.v : null, y: r ? r.y : null, c: c ?? null, dv: r && c != null ? r.v - c : null };
      });
      const ordinati = righe.filter((o) => o.v != null && o.t.k === 'paese').sort((a, b) => b.v - a.v);
      ordinati.forEach((o, k) => { o.pos = k + 1; });
      const { col, dir } = S.ordTab, s = dir === 'asc' ? 1 : -1;
      const chiave = (o) => (col === 'n' ? o.t.n : col === 'r' ? (o.t.r ? REG[o.t.r].n : 'ü') : col === 'sel' ? (S.sel.includes(o.t.id) ? 0 : 1) : o[col]);
      righe.sort((a, b) => {
        const x = chiave(a), y = chiave(b);
        if (x == null && y == null) return a.t.n.localeCompare(b.t.n, 'it');
        if (x == null) return 1; if (y == null) return -1;
        return typeof x === 'string' ? s * x.localeCompare(y, 'it') : s * (x - y);
      });
      thead.innerHTML = '<tr>' + cols.map((c) => `<th scope="col" class="${c.num ? 'num' : ''}" ${S.ordTab.col === c.k ? `aria-sort="${dir === 'asc' ? 'ascending' : 'descending'}"` : ''}><button type="button" data-k="${c.k}">${typeof c.t === 'function' ? c.t() : c.t}</button></th>`).join('') + '</tr>';
      thead.querySelectorAll('button').forEach((b) => b.addEventListener('click', () => {
        const k = b.dataset.k; S.ordTab = { col: k, dir: S.ordTab.col === k && S.ordTab.dir === 'desc' ? 'asc' : (k === 'n' || k === 'r' || k === 'pos' ? 'asc' : 'desc') };
        if (S.ordTab.col === k && k !== S.ordTab.col) S.ordTab.dir = 'desc';
        aggiorna(); thead.querySelector(`[data-k="${k}"]`).focus();
      }));
      const nd = '<span class="nd">n.d.</span>';
      tbody.innerHTML = righe.map((o) => {
        const sel = S.sel.includes(o.t.id);
        const dv = o.dv == null ? nd : (o.dv > 0 ? '+' : o.dv < 0 ? '−' : '') + fmt(Math.abs(o.dv));
        return `<tr class="${sel ? 'riga--sel' : ''} ${o.t.k === 'agg' ? 'agg' : ''}" style="${spento(o.t.id) ? 'opacity:.4' : ''}">
          <td><input type="checkbox" data-id="${o.t.id}" ${sel ? 'checked' : ''} aria-label="Seleziona ${o.t.n}"></td>
          <td><span class="pallino" style="background:${coloreRegione(o.t.id)}"></span>${o.t.n}</td>
          <td>${o.t.k === 'agg' ? '<span class="nd">aggregato</span>' : o.t.r ? REG[o.t.r].n : '<span class="nd">non classificata</span>'}</td>
          <td class="num"><strong>${o.v == null ? nd : fmt(o.v)}</strong></td>
          <td class="num anno-dato">${o.y ?? nd}</td>
          <td class="num">${o.c == null ? nd : fmt(o.c)}</td>
          <td class="num">${dv}</td>
          <td class="num">${o.pos ?? '<span class="nd">·</span>'}</td></tr>`;
      }).join('');
      tbody.querySelectorAll('input[type="checkbox"]').forEach((c) => c.addEventListener('change', () => attivaSelezione(c.dataset.id)));
    };
    aggiorna();
    return { aggiorna, selezione: aggiorna };
  };

  avvia();
})();
