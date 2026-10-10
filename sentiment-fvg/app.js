/* Monitor della percezione pubblica FVG: logica del cruscotto.
 *
 * Due modalità:
 *  - servizio: se <body data-servizio="https://..."> è compilato, legge
 *    /api/istantanea e resta in ascolto su /api/flusso (eventi SSE);
 *  - simulazione: se data-servizio è vuoto (o con ?demo=1), genera dati finti
 *    chiaramente etichettati, utili per provare il cruscotto.
 *
 * Il cruscotto non contiene e non riceve mai chiavi private: l'accesso al
 * servizio avviene con un cookie di sessione HttpOnly impostato dal server.
 */
(() => {
  'use strict';

  // ------------------------------------------------------------------ configurazione
  const parametri = new URLSearchParams(location.search);
  const SERVIZIO = (document.body.dataset.servizio || '').trim().replace(/\/+$/, '');
  const DEMO = !SERVIZIO || parametri.get('demo') === '1';

  const SOGLIE = {           // le stesse del servizio (vedi servizio/.env.esempio)
    finestraMin: 15, baseOre: 6, volumeMinimo: 12, caloCrisi: 30, quotaNegativaCrisi: 0.6,
    crescitaOpportunita: 2, indiceOpportunita: 40, pausaMin: 45,
  };
  const INTERVALLO_MIN = 10;                 // ampiezza degli intervalli nei grafici
  const ORE_GRAFICO = 6;
  const MAX_SCHEDE = 80;
  const MINUTO = 60_000, ORA = 60 * MINUTO;

  const NOMI_FONTE = {
    rss: 'Testata', bluesky: 'Bluesky', x: 'X', facebook: 'Facebook',
    instagram: 'Instagram', telegram: 'Telegram', webhook: 'Altra fonte',
  };
  const EMOZIONI = ['rabbia', 'paura', 'tristezza', 'fiducia', 'entusiasmo'];
  const ICONA_TONO = { positivo: '▲', neutro: '●', negativo: '▼' };

  // ------------------------------------------------------------------ stato
  const stato = {
    menzioni: new Map(),       // chiave → menzione
    allerte: [],
    visteAllerte: new Set(),
    pausa: false,
    suoni: false,
    filtri: { fonte: 'tutte', polarita: 'tutte', sarcasmo: false, testo: '' },
    ora: () => Date.now(),     // in simulazione è l'orologio accelerato
  };

  const $ = (id) => document.getElementById(id);
  const tempo = (m) => Date.parse(m.raccolto);
  const css = (nome) => getComputedStyle(document.documentElement).getPropertyValue(nome).trim();

  // ------------------------------------------------------------------ calcoli
  function indiceNetto(lista) {
    let somma = 0, pesi = 0;
    for (const m of lista) {
      somma += m.analisi.punteggio * m.analisi.confidenza;
      pesi += m.analisi.confidenza;
    }
    return pesi ? (100 * somma) / pesi : 0;
  }
  const quota = (lista, pol) => lista.length ? lista.filter((m) => m.analisi.polarita === pol).length / lista.length : 0;
  const media = (lista, f) => lista.length ? lista.reduce((s, m) => s + f(m), 0) / lista.length : 0;
  function scarto(valori) {
    if (valori.length < 2) return 0;
    const mu = valori.reduce((a, b) => a + b, 0) / valori.length;
    return Math.sqrt(valori.reduce((s, v) => s + (v - mu) ** 2, 0) / valori.length);
  }
  function tra(da, a) {
    const r = [];
    for (const m of stato.menzioni.values()) { const t = tempo(m); if (t >= da && t < a) r.push(m); }
    return r;
  }
  function intervalli(ore, minuti) {
    const fine = Math.ceil(stato.ora() / (minuti * MINUTO)) * minuti * MINUTO;
    const n = (ore * 60) / minuti;
    const secchi = Array.from({ length: n }, (_, i) => ({ inizio: fine - (n - i) * minuti * MINUTO, voci: [] }));
    const inizio = secchi[0].inizio;
    for (const m of stato.menzioni.values()) {
      const t = tempo(m);
      if (t < inizio || t >= fine) continue;
      secchi[Math.floor((t - inizio) / (minuti * MINUTO))].voci.push(m);
    }
    return secchi;
  }

  // ------------------------------------------------------------------ formattazione
  const numero = (v, cifre = 0) => v.toLocaleString('it-IT', { maximumFractionDigits: cifre, minimumFractionDigits: cifre });
  const conSegno = (v) => (v > 0 ? '+' : v < 0 ? '−' : '') + numero(Math.abs(v));
  const oraBreve = (t) => new Date(t).toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit' });
  function fa(t) {
    const s = Math.max(0, Math.round((stato.ora() - t) / 1000));
    if (s < 60) return 'adesso';
    if (s < 3600) return `${Math.round(s / 60)} min fa`;
    return oraBreve(t);
  }
  function elemento(tag, classe, testo) {
    const e = document.createElement(tag);
    if (classe) e.className = classe;
    if (testo != null) e.textContent = testo;   // mai innerHTML con testi esterni
    return e;
  }
  function urlSicuro(u) {
    try { const x = new URL(u); return x.protocol === 'https:' || x.protocol === 'http:' ? x.href : null; }
    catch { return null; }
  }

  // ------------------------------------------------------------------ indicatori
  function aggiornaIndicatori() {
    const ora = stato.ora();
    const ultima = tra(ora - ORA, ora + 1);
    const precedente = tra(ora - 2 * ORA, ora - ORA);

    const ind = indiceNetto(ultima), indPrec = indiceNetto(precedente);
    $('k-indice').textContent = ultima.length ? conSegno(Math.round(ind)) : '–';
    $('k-indice-var').textContent = precedente.length ? `${conSegno(Math.round(ind - indPrec))} punti sull'ora prima` : '';
    $('k-indice-cursore').style.left = `${50 + ind / 2}%`;

    $('k-volume').textContent = numero(ultima.length);
    const varVol = precedente.length ? ((ultima.length - precedente.length) / precedente.length) * 100 : null;
    $('k-volume-var').textContent = varVol == null ? '' : `${conSegno(Math.round(varVol))}% sull'ora prima`;

    const indici = intervalli(2, INTERVALLO_MIN).filter((s) => s.voci.length >= 3).map((s) => indiceNetto(s.voci));
    const vol = scarto(indici);
    $('k-volatilita').textContent = indici.length >= 3 ? numero(vol, 1) : '–';
    $('k-volatilita-var').textContent = indici.length < 3 ? 'dati insufficienti' : vol < 10 ? 'bassa: opinione stabile' : vol < 20 ? 'media' : 'alta: opinione in movimento';

    const sarc = ultima.filter((m) => m.analisi.sarcasmo).length;
    $('k-sarcasmo').textContent = ultima.length ? `${numero((100 * sarc) / ultima.length)}%` : '–';
    $('k-sarcasmo-var').textContent = `${numero(sarc)} menzioni ironiche`;

    const medie = EMOZIONI.map((e) => [e, media(ultima, (m) => m.analisi.emozioni[e] || 0)]).sort((a, b) => b[1] - a[1]);
    $('k-emozione').textContent = ultima.length ? medie[0][0] : '–';
    $('k-emozione-var').textContent = ultima.length ? `intensità media ${numero(medie[0][1] * 100)} su 100` : '';
  }

  // ------------------------------------------------------------------ grafici
  let grafici = {};
  function opzioniBase() {
    return {
      responsive: true, maintainAspectRatio: false, animation: { duration: 300 },
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: { backgroundColor: css('--superficie'), titleColor: css('--testo'), bodyColor: css('--testo-2'),
                   borderColor: css('--bordo'), borderWidth: 1, padding: 10, boxPadding: 4 },
      },
      scales: {
        x: { grid: { display: false }, border: { color: css('--bordo') }, ticks: { color: css('--testo-3'), maxRotation: 0, autoSkipPadding: 16 } },
        y: { grid: { color: css('--griglia') }, border: { display: false }, ticks: { color: css('--testo-3') } },
      },
    };
  }

  function creaGrafici() {
    if (!window.Chart) return;
    Object.values(grafici).forEach((g) => g.destroy());
    Chart.defaults.font.family = css('--carattere');
    const pos = css('--positivo'), neg = css('--negativo'), neu = css('--neutro'), sup = css('--superficie');

    const oA = opzioniBase();
    oA.scales.y.min = -100; oA.scales.y.max = 100;
    oA.scales.y.ticks.stepSize = 50;
    oA.scales.y.grid.color = (c) => (c.tick.value === 0 ? css('--testo-3') : css('--griglia'));
    oA.plugins.tooltip.callbacks = {
      label: (c) => c.raw == null ? 'meno di 3 menzioni' : `Indice ${conSegno(Math.round(c.raw))} · ${c.dataset.conteggi[c.dataIndex]} menzioni`,
    };
    grafici.andamento = new Chart($('g-andamento'), {
      type: 'line',
      data: { labels: [], datasets: [{
        data: [], conteggi: [], borderWidth: 2, tension: 0.3, spanGaps: true,
        pointRadius: 0, pointHoverRadius: 5, pointHoverBorderWidth: 2, pointHoverBorderColor: sup,
        segment: { borderColor: (c) => ((c.p0.parsed.y + c.p1.parsed.y) / 2 >= 0 ? pos : neg) },
        pointHoverBackgroundColor: (c) => ((c.raw ?? 0) >= 0 ? pos : neg),
      }] },
      options: oA,
    });

    const oV = opzioniBase();
    oV.scales.x.stacked = true; oV.scales.y.stacked = true;
    oV.plugins.legend = { display: true, position: 'top', align: 'end',
      labels: { color: css('--testo-2'), boxWidth: 10, boxHeight: 10, useBorderRadius: true, borderRadius: 2 } };
    const serie = (etichetta, colore) => ({ label: etichetta, data: [], backgroundColor: colore, borderColor: sup,
      borderWidth: { top: 2 }, borderSkipped: 'bottom', borderRadius: 2, barPercentage: 0.9, categoryPercentage: 0.9 });
    grafici.volume = new Chart($('g-volume'), {
      type: 'bar',
      data: { labels: [], datasets: [serie('Negativo', neg), serie('Neutro', neu), serie('Positivo', pos)] },
      options: oV,
    });

    const oE = opzioniBase();
    oE.indexAxis = 'y';
    oE.interaction = { mode: 'nearest', axis: 'y', intersect: false };
    oE.scales.x = { min: 0, max: 100, grid: { color: css('--griglia') }, border: { display: false }, ticks: { color: css('--testo-3'), stepSize: 25 } };
    oE.scales.y = { grid: { display: false }, border: { color: css('--bordo') }, ticks: { color: css('--testo-2') } };
    oE.plugins.tooltip.callbacks = { label: (c) => `Intensità media ${numero(c.raw)} su 100` };
    grafici.emozioni = new Chart($('g-emozioni'), {
      type: 'bar',
      data: { labels: EMOZIONI.map((e) => e[0].toUpperCase() + e.slice(1)),
              datasets: [{ data: [], backgroundColor: css('--accento'), borderRadius: 4, barPercentage: 0.7 }] },
      options: oE,
    });
    aggiornaGrafici();
  }

  function aggiornaGrafici() {
    if (!grafici.andamento) return;
    const secchi = intervalli(ORE_GRAFICO, INTERVALLO_MIN);
    const etichette = secchi.map((s) => oraBreve(s.inizio));

    const a = grafici.andamento;
    a.data.labels = etichette;
    a.data.datasets[0].data = secchi.map((s) => (s.voci.length >= 3 ? Math.round(indiceNetto(s.voci)) : null));
    a.data.datasets[0].conteggi = secchi.map((s) => s.voci.length);
    a.update('none');

    const v = grafici.volume;
    v.data.labels = etichette;
    ['negativo', 'neutro', 'positivo'].forEach((p, i) => {
      v.data.datasets[i].data = secchi.map((s) => s.voci.filter((m) => m.analisi.polarita === p).length);
    });
    v.update('none');

    const ora = stato.ora();
    const ultima = tra(ora - ORA, ora + 1);
    grafici.emozioni.data.datasets[0].data = EMOZIONI.map((e) => Math.round(100 * media(ultima, (m) => m.analisi.emozioni[e] || 0)));
    grafici.emozioni.update('none');
  }

  // ------------------------------------------------------------------ temi caldi
  function coloreDivergente(indice) {
    // Interpola fra negativo, neutro e positivo secondo l'indice (da −100 a +100)
    const hex = (c) => c.match(/\w\w/g).map((x) => parseInt(x, 16));
    const [a, b] = indice >= 0 ? [css('--neutro'), css('--positivo')] : [css('--neutro'), css('--negativo')];
    const t = Math.min(1, Math.abs(indice) / 60);
    const ca = hex(a), cb = hex(b);
    return `rgb(${ca.map((v, i) => Math.round(v + (cb[i] - v) * t)).join(' ')})`;
  }

  function aggiornaTemi() {
    const ora = stato.ora();
    const gruppi = new Map();
    for (const m of tra(ora - 2 * ORA, ora + 1)) {
      for (const t of m.analisi.temi) {
        if (!gruppi.has(t)) gruppi.set(t, []);
        gruppi.get(t).push(m);
      }
    }
    const temi = [...gruppi.entries()].sort((a, b) => b[1].length - a[1].length).slice(0, 18);
    const contenitore = $('nuvola');
    contenitore.replaceChildren();
    if (!temi.length) { contenitore.append(elemento('p', 'nota', 'Nessun tema nelle ultime 2 ore.')); return; }
    const max = temi[0][1].length;
    temi.sort((a, b) => a[0].localeCompare(b[0], 'it'));
    for (const [nome, voci] of temi) {
      const ind = indiceNetto(voci);
      const b = elemento('button', 'tema');
      b.type = 'button';
      b.style.fontSize = `${13 + 13 * Math.sqrt(voci.length / max)}px`;
      b.title = `${nome}: ${voci.length} menzioni, indice ${conSegno(Math.round(ind))}. Clic per filtrare il flusso.`;
      const segno = elemento('span', 'segno');
      segno.style.background = coloreDivergente(ind);
      b.append(segno, elemento('span', null, nome), elemento('span', 'cifra', `${voci.length} · ${conSegno(Math.round(ind))}`));
      b.addEventListener('click', () => { $('f-testo').value = nome; stato.filtri.testo = nome; disegnaFlusso(); });
      contenitore.append(b);
    }
  }

  // ------------------------------------------------------------------ fonti
  function aggiornaFonti() {
    const ora = stato.ora();
    const ultima = tra(ora - ORA, ora + 1);
    const perFonte = new Map();
    for (const m of ultima) {
      if (!perFonte.has(m.fonte)) perFonte.set(m.fonte, []);
      perFonte.get(m.fonte).push(m);
    }
    const righe = [...perFonte.entries()].sort((a, b) => b[1].length - a[1].length);
    const max = righe[0]?.[1].length || 1;
    const c = $('fonti');
    c.replaceChildren();
    if (!righe.length) { c.append(elemento('p', 'nota', 'Nessuna menzione nell\'ultima ora.')); return; }
    for (const [fonte, voci] of righe) {
      const riga = elemento('div', 'fonte-riga');
      const barra = elemento('div', 'fonte-barra');
      barra.style.width = `${(100 * voci.length) / max}%`;
      barra.title = ['negativo', 'neutro', 'positivo'].map((p) => `${p}: ${voci.filter((m) => m.analisi.polarita === p).length}`).join(' · ');
      for (const [p, colore] of [['negativo', '--negativo'], ['neutro', '--neutro'], ['positivo', '--positivo']]) {
        const n = voci.filter((m) => m.analisi.polarita === p).length;
        if (!n) continue;
        const s = elemento('span');
        s.style.flex = String(n);
        s.style.background = css(colore);
        barra.append(s);
      }
      riga.append(elemento('span', null, NOMI_FONTE[fonte] || fonte), barra, elemento('span', 'cifra', numero(voci.length)));
      c.append(riga);
    }
  }

  // ------------------------------------------------------------------ flusso
  function passaFiltri(m) {
    const f = stato.filtri;
    if (f.fonte !== 'tutte' && m.fonte !== f.fonte) return false;
    if (f.polarita !== 'tutte' && m.analisi.polarita !== f.polarita) return false;
    if (f.sarcasmo && !m.analisi.sarcasmo) return false;
    if (f.testo) {
      const q = f.testo.toLowerCase();
      const testo = [m.testo, m.testata, ...m.analisi.temi, ...m.analisi.entita.map((e) => e.testo)].join(' ').toLowerCase();
      if (!testo.includes(q)) return false;
    }
    return true;
  }

  function scheda(m, nuova) {
    const a = m.analisi;
    const li = elemento('li', 'scheda' + (nuova ? ' nuova' : ''));
    li.dataset.polarita = a.polarita;

    const testa = elemento('div', 'scheda-testa');
    testa.append(elemento('span', 'etichetta', `${ICONA_TONO[a.polarita]} ${a.polarita[0].toUpperCase() + a.polarita.slice(1)} ${conSegno(Math.round(a.punteggio * 100))}`));
    if (a.sarcasmo) testa.append(elemento('span', 'etichetta sarcasmo', '😏 Sarcasmo'));
    if (a.ostilita >= 0.6) testa.append(elemento('span', 'etichetta ostile', '⚠ Toni ostili'));
    if (a.emozione_dominante && a.emozione_dominante !== 'indifferenza') testa.append(elemento('span', 'etichetta', a.emozione_dominante));
    if (a.dialetto) testa.append(elemento('span', 'etichetta', `🗣 ${a.dialetto}`));
    const ora = elemento('time', 'ora', fa(tempo(m)));
    ora.dateTime = m.raccolto;
    ora.title = new Date(tempo(m)).toLocaleString('it-IT');
    testa.append(ora);

    const corpo = elemento('p', 'scheda-testo', m.testo.length > 420 ? m.testo.slice(0, 420) + '…' : m.testo);
    const motivo = a.motivazione ? elemento('p', 'scheda-motivo', a.motivazione) : null;

    const piede = elemento('div', 'scheda-piede');
    piede.append(elemento('strong', null, m.testata || NOMI_FONTE[m.fonte] || m.fonte));
    for (const e of a.entita.slice(0, 3)) piede.append(elemento('span', 'entita', e.testo));
    const url = m.url && urlSicuro(m.url);
    if (url) {
      const link = elemento('a', null, 'Apri originale ↗');
      link.href = url; link.target = '_blank'; link.rel = 'noopener noreferrer';
      piede.append(link);
    }
    li.append(testa, corpo);
    if (motivo) li.append(motivo);
    li.append(piede);
    return li;
  }

  function disegnaFlusso(nuove = new Set()) {
    const elenco = [...stato.menzioni.values()].filter(passaFiltri).sort((a, b) => tempo(b) - tempo(a));
    const c = $('flusso');
    c.replaceChildren(...elenco.slice(0, MAX_SCHEDE).map((m) => scheda(m, nuove.has(m.chiave))));
    if (!elenco.length) c.append(elemento('li', 'vuoto', 'Nessuna menzione con questi filtri.'));
    $('flusso-conteggio').textContent = `${numero(elenco.length)} menzioni nelle ultime 24 ore${elenco.length > MAX_SCHEDE ? `, mostrate le ultime ${MAX_SCHEDE}` : ''}`;
  }

  // ------------------------------------------------------------------ allerte
  function disegnaRegistro() {
    const c = $('registro');
    const elenco = [...stato.allerte].sort((a, b) => Date.parse(b.creata) - Date.parse(a.creata));
    if (!elenco.length) { c.replaceChildren(elemento('li', 'vuoto', 'Nessuna allerta.')); return; }
    c.replaceChildren(...elenco.map((al) => {
      const li = elemento('li');
      li.dataset.tipo = al.tipo;
      const testa = elemento('div', 'riga-testa');
      testa.append(elemento('strong', null, `${al.tipo === 'crisi' ? '🔴' : '🟢'} ${al.titolo}`));
      const t = elemento('time', null, oraBreve(Date.parse(al.creata)));
      t.dateTime = al.creata;
      testa.append(t);
      li.append(testa, elemento('span', 'dettaglio', `${al.sottotipo} · gravità ${al.gravita}/3`), elemento('span', null, al.descrizione));
      return li;
    }));
  }

  function mostraStriscione() {
    const attiva = [...stato.allerte].sort((a, b) => Date.parse(b.creata) - Date.parse(a.creata))
      .find((a) => !stato.visteAllerte.has(a.id));
    const s = $('striscione');
    if (!attiva) { s.hidden = true; return; }
    s.hidden = false;
    s.dataset.tipo = attiva.tipo;
    s.dataset.id = attiva.id;
    $('striscione-icona').textContent = attiva.tipo === 'crisi' ? '⚠' : '★';
    $('striscione-titolo').textContent = attiva.titolo;
    $('striscione-testo').textContent = `${attiva.sottotipo}. ${attiva.descrizione}`;
  }

  function nuovaAllerta(al, silenziosa = false) {
    if (stato.allerte.some((a) => a.id === al.id)) return;
    stato.allerte.push(al);
    const limite = stato.ora() - 24 * ORA;
    stato.allerte = stato.allerte.filter((a) => Date.parse(a.creata) >= limite);
    disegnaRegistro();
    mostraStriscione();
    if (silenziosa) return;
    if (stato.suoni) suono(al.tipo);
    if ('Notification' in window && Notification.permission === 'granted' && document.hidden) {
      new Notification(al.titolo, { body: al.descrizione, tag: al.id });
    }
  }

  let audio;
  function suono(tipo) {
    try {
      audio ||= new (window.AudioContext || window.webkitAudioContext)();
      const note = tipo === 'crisi' ? [440, 330, 440, 330] : [523, 659, 784];
      note.forEach((f, i) => {
        const o = audio.createOscillator(), g = audio.createGain();
        o.type = tipo === 'crisi' ? 'square' : 'sine';
        o.frequency.value = f;
        const t = audio.currentTime + i * 0.18;
        g.gain.setValueAtTime(0.0001, t);
        g.gain.exponentialRampToValueAtTime(0.12, t + 0.02);
        g.gain.exponentialRampToValueAtTime(0.0001, t + 0.16);
        o.connect(g).connect(audio.destination);
        o.start(t); o.stop(t + 0.17);
      });
    } catch { /* audio non disponibile */ }
  }

  // ------------------------------------------------------------------ aggiornamento
  let nuoveInAttesa = new Set();
  let programmato = false;
  function aggiungiMenzioni(lista, nuove = true) {
    for (const m of lista) {
      if (!m?.chiave || !m.analisi) continue;
      if (nuove && !stato.menzioni.has(m.chiave)) nuoveInAttesa.add(m.chiave);
      stato.menzioni.set(m.chiave, m);
    }
    const limite = stato.ora() - 24 * ORA;
    for (const [k, m] of stato.menzioni) if (tempo(m) < limite) stato.menzioni.delete(k);
    programma();
  }
  function programma() {
    if (programmato) return;
    programmato = true;
    setTimeout(() => {
      programmato = false;
      aggiornaIndicatori();
      aggiornaGrafici();
      aggiornaTemi();
      aggiornaFonti();
      if (!stato.pausa) { disegnaFlusso(nuoveInAttesa); nuoveInAttesa = new Set(); }
      avvisaAltezza();
    }, 800);
  }

  // Ridimensionamento automatico quando il cruscotto è dentro un iframe
  let ultimaAltezza = 0;
  function avvisaAltezza() {
    if (window.parent === window) return;
    const h = document.documentElement.scrollHeight;
    if (Math.abs(h - ultimaAltezza) < 4) return;
    ultimaAltezza = h;
    window.parent.postMessage({ tipo: 'monitor-percezione-altezza', altezza: h }, '*');
  }

  function impostaStato(tipo, testo) {
    $('stato').dataset.tipo = tipo;
    $('stato-testo').textContent = testo;
  }

  // ------------------------------------------------------------------ servizio reale
  let sorgente;
  async function avviaServizio() {
    impostaStato('attesa', 'Collegamento');
    let r;
    try {
      r = await fetch(`${SERVIZIO}/api/istantanea`, { credentials: 'include' });
    } catch {
      impostaStato('errore', 'Servizio non raggiungibile');
      setTimeout(avviaServizio, 15000);
      return;
    }
    if (r.status === 401) { chiediAccesso(); return; }
    if (!r.ok) { impostaStato('errore', `Errore ${r.status}`); setTimeout(avviaServizio, 15000); return; }
    const dati = await r.json();
    aggiungiMenzioni(dati.menzioni || [], false);
    (dati.allerte || []).forEach((a) => nuovaAllerta(a, true));

    sorgente?.close();
    sorgente = new EventSource(`${SERVIZIO}/api/flusso`, { withCredentials: true });
    let eraCaduto = false;
    sorgente.addEventListener('open', () => {
      impostaStato('collegato', 'In diretta');
      if (eraCaduto) { eraCaduto = false; recupera(); }
    });
    sorgente.addEventListener('menzione', (e) => aggiungiMenzioni([JSON.parse(e.data)]));
    sorgente.addEventListener('allerta', (e) => nuovaAllerta(JSON.parse(e.data)));
    sorgente.addEventListener('error', () => {
      eraCaduto = true;
      if (sorgente.readyState === EventSource.CLOSED) { setTimeout(avviaServizio, 5000); impostaStato('errore', 'Disconnesso'); }
      else impostaStato('attesa', 'Riconnessione');
    });
  }
  // Dopo una caduta della linea recupera quanto arrivato nel frattempo
  async function recupera() {
    try {
      const r = await fetch(`${SERVIZIO}/api/istantanea`, { credentials: 'include' });
      if (!r.ok) return;
      const dati = await r.json();
      aggiungiMenzioni(dati.menzioni || []);
      (dati.allerte || []).forEach((a) => nuovaAllerta(a));
    } catch { /* si riproverà alla prossima riconnessione */ }
  }

  function chiediAccesso() {
    impostaStato('attesa', 'Accesso richiesto');
    const d = $('accesso');
    if (!d.open) d.showModal();
    $('accesso-modulo').onsubmit = async (ev) => {
      ev.preventDefault();
      $('accesso-errore').textContent = '';
      try {
        const r = await fetch(`${SERVIZIO}/api/accesso`, {
          method: 'POST', credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ codice: $('accesso-codice').value }),
        });
        if (!r.ok) { $('accesso-errore').textContent = 'Codice non valido.'; return; }
        $('accesso-codice').value = '';
        d.close();
        avviaServizio();
      } catch {
        $('accesso-errore').textContent = 'Servizio non raggiungibile.';
      }
    };
  }

  // ------------------------------------------------------------------ simulazione
  // Testi inventati per la dimostrazione: nessun riferimento a persone reali.
  const E = (testo, tipo = 'ente') => ({ testo, tipo });
  const MODELLI = {
    normale: [
      ['Complimenti davvero, terzo mese di cantiere e la rotonda è ancora chiusa 👏', 'negativo', -0.7, true, 'rabbia', ['viabilità'], [E('Comune')], null, 'Lode ironica su un cantiere fermo.'],
      ['Bravissimi, un\'altra ora di coda in tangenziale. Organizzazione perfetta.', 'negativo', -0.6, true, 'rabbia', ['viabilità'], [], null, 'Sarcasmo sulle code.'],
      ['Grazie per il treno soppresso anche oggi, così mi godo l\'alba in stazione.', 'negativo', -0.6, true, 'rabbia', ['trasporti'], [], null, 'Ringraziamento ironico per un disservizio.'],
      ['Che efficienza, sei mesi per una visita specialistica. Avanti così!', 'negativo', -0.7, true, 'rabbia', ['sanità'], [E('Azienda sanitaria')], null, 'Ironia sulle liste di attesa.'],
      ['Pronto soccorso pieno da ieri sera, servono più medici e subito.', 'negativo', -0.6, false, 'rabbia', ['sanità'], [E('Pronto soccorso')], null, 'Protesta esplicita sulla carenza di personale.'],
      ['Al è un disastri: la strade e je plene di busis.', 'negativo', -0.6, false, 'rabbia', ['viabilità'], [], 'friulano', 'Lamentela sulle buche, in friulano.'],
      ['Xe ora che i se movi con le corriere, la sera no passa più niente.', 'negativo', -0.5, false, 'rabbia', ['trasporti'], [], 'triestino', 'Critica al trasporto serale, in triestino.'],
      ['Furti nelle case in zona industriale, i residenti chiedono più controlli.', 'negativo', -0.4, false, 'paura', ['sicurezza'], [], null, 'Preoccupazione per i furti.'],
      ['Affitti alle stelle, i giovani se ne vanno dal paese.', 'negativo', -0.5, false, 'tristezza', ['casa', 'lavoro'], [], null, 'Amarezza per lo spopolamento.'],
      ['Approvato il piano per la manutenzione delle scuole: i lavori partiranno in primavera.', 'neutro', 0.1, false, 'indifferenza', ['scuola'], [E('Regione Friuli Venezia Giulia')], null, 'Notizia senza giudizio.'],
      ['Convocato il Consiglio regionale per l\'esame della legge di assestamento.', 'neutro', 0, false, 'indifferenza', ['bilancio'], [E('Consiglio regionale')], null, 'Notizia di servizio.'],
      ['Pubblicato il bando per i contributi alle associazioni sportive: domande entro fine mese.', 'neutro', 0.1, false, 'indifferenza', ['sport'], [E('Regione Friuli Venezia Giulia')], null, 'Informazione su un bando.'],
      ['Domani chiusura temporanea del tratto stradale per lavori notturni.', 'neutro', -0.05, false, 'indifferenza', ['viabilità'], [], null, 'Avviso di servizio.'],
      ['Finalmente la nuova pista ciclabile lungo il fiume: bellissima, già piena di famiglie!', 'positivo', 0.8, false, 'entusiasmo', ['ambiente', 'mobilità'], [], null, 'Apprezzamento sincero.'],
      ['Ce biel il centri cul marcjât, mandi a ducj!', 'positivo', 0.8, false, 'entusiasmo', ['eventi'], [], 'friulano', 'Lode sincera in friulano.'],
      ['Ottimo lavoro della protezione civile durante il temporale, presenti in pochi minuti.', 'positivo', 0.7, false, 'fiducia', ['sicurezza', 'protezione civile'], [E('Protezione civile')], null, 'Riconoscimento per un intervento.'],
      ['Nuovo ambulatorio di comunità aperto: meno attese e più servizi vicino a casa.', 'positivo', 0.6, false, 'fiducia', ['sanità'], [E('Azienda sanitaria')], null, 'Giudizio favorevole su un servizio.'],
      ['Il contributo per i libri di scuola è arrivato in tempo, una mano concreta alle famiglie.', 'positivo', 0.6, false, 'fiducia', ['scuola', 'famiglie'], [], null, 'Gratitudine per un sostegno.'],
    ],
    crisi: [
      ['Ancora ponte chiuso e nessuna alternativa: siamo isolati e nessuno risponde!', 'negativo', -0.85, false, 'rabbia', ['viabilità'], [E('Ponte sul torrente', 'localita')], null, 'Protesta forte per un isolamento.'],
      ['Vergognoso, due ore fermi in colonna senza una sola comunicazione.', 'negativo', -0.8, false, 'rabbia', ['viabilità'], [], null, 'Indignazione per la mancata informazione.'],
      ['Complimenti per la gestione del cantiere, davvero un capolavoro 👏👏', 'negativo', -0.8, true, 'rabbia', ['viabilità'], [], null, 'Elogio ironico: critica netta.'],
      ['Ma xe mai possibile che nessun avvisi prima dei lavori?', 'negativo', -0.7, false, 'rabbia', ['viabilità'], [], 'veneto', 'Protesta in veneto pordenonese.'],
      ['Ambulanze costrette al giro lungo per il ponte chiuso, qui si rischia grosso.', 'negativo', -0.75, false, 'paura', ['viabilità', 'sanità'], [], null, 'Timore per i soccorsi.'],
    ],
    ondata: [
      ['Dimissioni subito! Vergogna! #bastacosì', 'negativo', -0.9, false, 'rabbia', ['viabilità'], [], null, 'Messaggio ripetuto da molti account.'],
    ],
    opportunita: [
      ['Piazze piene per la festa del vino, il Friuli quando vuole è imbattibile!', 'positivo', 0.85, false, 'entusiasmo', ['eventi', 'turismo'], [], null, 'Entusiasmo per un evento.'],
      ['Fine settimana da record nei rifugi in montagna, che orgoglio vedere tanta gente.', 'positivo', 0.8, false, 'entusiasmo', ['turismo'], [], null, 'Orgoglio per l\'affluenza.'],
      ['Bellissima la giornata delle borgate, da rifare ogni anno!', 'positivo', 0.8, false, 'entusiasmo', ['eventi', 'turismo'], [], null, 'Richiesta di ripetere l\'iniziativa.'],
      ['Turisti ovunque sulla costa, ottimo segnale per chi lavora d\'estate.', 'positivo', 0.7, false, 'fiducia', ['turismo', 'lavoro'], [], null, 'Fiducia nella stagione turistica.'],
    ],
  };
  const FONTI_DEMO = [['rss', 'Testata locale (simulata)'], ['bluesky', null], ['x', null], ['facebook', null], ['telegram', 'Canale pubblico (simulato)']];
  const caso = (lista) => lista[Math.floor(Math.random() * lista.length)];
  const vicino = (v, d) => Math.max(-1, Math.min(1, v + (Math.random() - 0.5) * d));
  let progressivo = 0;

  function menzioneDemo(t, modello, autore) {
    const [testo, polarita, punteggio, sarcasmo, emo, temi, entita, dialetto, motivazione] = modello;
    const [fonte, testata] = caso(FONTI_DEMO);
    const emozioni = { rabbia: 0.05, paura: 0.05, entusiasmo: 0.05, fiducia: 0.1, tristezza: 0.05 };
    if (emo !== 'indifferenza') emozioni[emo] = 0.55 + Math.random() * 0.35;
    if (polarita === 'negativo' && emo !== 'rabbia') emozioni.rabbia = 0.3;
    return {
      chiave: `demo-${++progressivo}`,
      fonte,
      testo,
      url: null,
      testata: testata || `${NOMI_FONTE[fonte]} (simulato)`,
      autore_pseudonimo: autore || `a${Math.floor(Math.random() * 1e6)}`,
      pubblicato: new Date(t).toISOString(),
      raccolto: new Date(t).toISOString(),
      interazioni: Math.floor(Math.random() * 60),
      analisi: {
        polarita, punteggio: vicino(punteggio, 0.2), confidenza: 0.6 + Math.random() * 0.35,
        sarcasmo, emozione_dominante: emo, emozioni, dialetto,
        entita, temi, bersaglio: null, ostilita: modello === MODELLI.ondata[0] ? 0.8 : polarita === 'negativo' ? 0.2 : 0.02,
        motivazione,
      },
    };
  }

  // Motore delle allerte in miniatura, con le stesse regole del servizio (solo per la simulazione)
  const ultimeAllerte = new Map();
  function rilevaDemo() {
    const ora = stato.ora();
    const inizioF = ora - SOGLIE.finestraMin * MINUTO;
    const inizioB = inizioF - SOGLIE.baseOre * ORA;
    const recenti = new Map(), base = new Map();
    for (const m of stato.menzioni.values()) {
      const t = tempo(m);
      if (t < inizioB) continue;
      const dest = t >= inizioF ? recenti : base;
      for (const ambito of ['generale', ...m.analisi.temi.map((x) => `tema:${x}`)]) {
        if (!dest.has(ambito)) dest.set(ambito, []);
        dest.get(ambito).push(m);
      }
    }
    for (const [ambito, f] of recenti) {
      const b = base.get(ambito) || [];
      const nome = ambito.split(':').pop();
      const crescita = (f.length / SOGLIE.finestraMin) / Math.max(b.length / (SOGLIE.baseOre * 60), 1e-9);
      const ind = indiceNetto(f), calo = b.length ? indiceNetto(b) - ind : 0;
      const neg = quota(f, 'negativo'), rabbia = media(f, (m) => m.analisi.emozioni.rabbia);

      if (f.length >= SOGLIE.volumeMinimo) {
        const autori = new Map();
        for (const m of f) if (m.fonte !== 'rss') {
          const k = m.testo.toLowerCase().slice(0, 90);
          if (!autori.has(k)) autori.set(k, new Set());
          autori.get(k).add(m.autore_pseudonimo);
        }
        const massimo = Math.max(0, ...[...autori.values()].map((s) => s.size));
        if (massimo >= 5 && massimo >= 0.25 * f.length) {
          emetti('crisi', 'ondata coordinata', ambito, 3, `Possibile azione organizzata: ${massimo} account diversi pubblicano lo stesso testo. Valutare prima di rispondere.`, nome);
        }
        const crollo = calo >= SOGLIE.caloCrisi && neg >= 0.5;
        const indignazione = neg >= SOGLIE.quotaNegativaCrisi && rabbia >= 0.5;
        if (crollo || indignazione) {
          emetti('crisi', indignazione ? 'indignazione' : 'calo improvviso', ambito, neg >= 0.8 ? 3 : 2,
            `${f.length} menzioni in ${SOGLIE.finestraMin} minuti, ` +
            (crollo ? `indice sceso di ${Math.round(calo)} punti rispetto alle ultime ${SOGLIE.baseOre} ore.` : `${Math.round(neg * 100)}% negative con rabbia diffusa.`), nome);
        }
      }
      if (ambito !== 'generale' && f.length >= Math.max(4, SOGLIE.volumeMinimo / 2) && crescita >= SOGLIE.crescitaOpportunita
          && ind >= SOGLIE.indiceOpportunita && media(f, (m) => m.analisi.emozioni.entusiasmo) >= 0.4) {
        emetti('opportunita', 'tema in crescita favorevole', ambito, ind >= 60 ? 3 : 2,
          `Volume ${numero(crescita, 1)} volte superiore alla media, indice ${conSegno(Math.round(ind))}.`, nome);
      }
    }
  }
  function emetti(tipo, sottotipo, ambito, gravita, descrizione, nome) {
    const chiave = `${tipo}${sottotipo}|${ambito}`;
    const ora = stato.ora();
    if (ora - (ultimeAllerte.get(chiave) || -Infinity) < SOGLIE.pausaMin * MINUTO) return;
    ultimeAllerte.set(chiave, ora);
    nuovaAllerta({
      id: `demo-al-${ora}-${Math.random().toString(36).slice(2, 7)}`,
      tipo, sottotipo, ambito, gravita, descrizione,
      titolo: `${tipo === 'crisi' ? 'Allerta crisi' : 'Opportunità di consenso'}: ${nome}`,
      metriche: {}, temi_collegati: [], esempi: [], creata: new Date(ora).toISOString(),
    });
  }

  function avviaSimulazione() {
    $('avviso-demo').hidden = false;
    impostaStato('demo', 'Simulazione');
    let orologio = Date.now();
    stato.ora = () => orologio;

    // Sei ore di storico a ritmo normale
    const storico = [];
    for (let t = orologio - ORE_GRAFICO * ORA; t < orologio; t += -Math.log(Math.random()) * 30_000) {
      storico.push(menzioneDemo(t, caso(MODELLI.normale)));
    }
    aggiungiMenzioni(storico, false);

    // Scenari a rotazione: normale, crisi, ondata coordinata, opportunità
    const scenari = ['normale', 'crisi', 'normale', 'opportunita', 'normale', 'ondata'];
    let indice = 0, fineScenario = orologio + 25 * MINUTO;
    setInterval(() => {
      if (orologio >= fineScenario) {
        indice = (indice + 1) % scenari.length;
        fineScenario = orologio + (scenari[indice] === 'normale' ? 40 : 25) * MINUTO;
      }
      const scenario = scenari[indice];
      const speciale = scenario !== 'normale';
      orologio += -Math.log(Math.random()) * (speciale ? 9_000 : 30_000);
      const usaSpeciale = speciale && Math.random() < 0.65;
      const modello = usaSpeciale ? caso(MODELLI[scenario]) : caso(MODELLI.normale);
      aggiungiMenzioni([menzioneDemo(orologio, modello)]);
      rilevaDemo();
    }, 1200);
  }

  // ------------------------------------------------------------------ comandi
  function collegaComandi() {
    $('filtri').addEventListener('submit', (e) => e.preventDefault());
    const filtro = (id, campo, valore = (e) => e.target.value) =>
      $(id).addEventListener('input', (e) => { stato.filtri[campo] = valore(e); disegnaFlusso(); });
    filtro('f-fonte', 'fonte');
    filtro('f-polarita', 'polarita');
    filtro('f-sarcasmo', 'sarcasmo', (e) => e.target.checked);
    filtro('f-testo', 'testo', (e) => e.target.value.trim());

    $('tasto-pausa').addEventListener('click', (e) => {
      stato.pausa = !stato.pausa;
      e.currentTarget.setAttribute('aria-pressed', String(stato.pausa));
      e.currentTarget.textContent = stato.pausa ? '▶ Riprendi' : '⏸ Pausa';
      if (!stato.pausa) { disegnaFlusso(nuoveInAttesa); nuoveInAttesa = new Set(); }
    });

    $('tasto-suoni').addEventListener('click', (e) => {
      stato.suoni = !stato.suoni;
      e.currentTarget.setAttribute('aria-pressed', String(stato.suoni));
      e.currentTarget.textContent = stato.suoni ? '🔊 Suoni' : '🔕 Suoni';
      if (stato.suoni) suono('opportunita');          // prova e sblocco dell'audio
    });

    const tn = $('tasto-notifiche');
    if (!('Notification' in window)) tn.hidden = true;
    tn.addEventListener('click', async () => {
      const esito = await Notification.requestPermission();
      tn.textContent = esito === 'granted' ? '🔔 Notifiche attive' : '🔔 Notifiche bloccate';
    });

    $('tasto-tema').addEventListener('click', () => {
      const scuro = document.documentElement.dataset.theme
        ? document.documentElement.dataset.theme === 'dark'
        : matchMedia('(prefers-color-scheme: dark)').matches;
      document.documentElement.dataset.theme = scuro ? 'light' : 'dark';
      try { localStorage.setItem('monitor-tema', document.documentElement.dataset.theme); } catch { /* facoltativo */ }
      creaGrafici(); aggiornaTemi(); aggiornaFonti();
    });
    matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => { creaGrafici(); aggiornaTemi(); aggiornaFonti(); });

    $('striscione-visto').addEventListener('click', () => {
      stato.visteAllerte.add($('striscione').dataset.id);
      mostraStriscione();
      avvisaAltezza();
    });

    // Aggiorna gli orari relativi ("3 min fa") anche quando non arrivano dati
    setInterval(() => { aggiornaIndicatori(); if (!stato.pausa) disegnaFlusso(); }, 30_000);
  }

  // ------------------------------------------------------------------ avvio
  try {
    const tema = localStorage.getItem('monitor-tema');
    if (tema) document.documentElement.dataset.theme = tema;
  } catch { /* facoltativo */ }

  // Area riservata: la pagina si apre solo con la parola d'ordine.
  // È una protezione di cortesia (il codice di un sito statico è leggibile da
  // chiunque): i dati reali restano protetti dai codici di accesso del servizio.
  const IMPRONTA_PAROLA = '261b3791571ca49823a094309607326e71769c57dd89c02ca55635459e9b38a0';
  async function impronta(testo) {
    const b = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(testo));
    return [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, '0')).join('');
  }
  let avviato = false;
  function sblocca() {
    document.body.classList.remove('bloccato');
    if (avviato) return;
    avviato = true;
    collegaComandi();
    creaGrafici();
    if (DEMO) avviaSimulazione(); else avviaServizio();
  }
  let giaSbloccato = false;
  try { giaSbloccato = sessionStorage.getItem('monitor-sbloccato') === IMPRONTA_PAROLA; } catch { /* facoltativo */ }
  if (giaSbloccato) sblocca();
  $('lucchetto-modulo').addEventListener('submit', async (e) => {
    e.preventDefault();
    const campo = $('lucchetto-parola');
    if (await impronta(campo.value.trim().toUpperCase()) !== IMPRONTA_PAROLA) {
      $('lucchetto-errore').textContent = 'Parola d\'ordine non corretta.';
      campo.select();
      return;
    }
    try { sessionStorage.setItem('monitor-sbloccato', IMPRONTA_PAROLA); } catch { /* facoltativo */ }
    campo.value = '';
    sblocca();
  });
})();
