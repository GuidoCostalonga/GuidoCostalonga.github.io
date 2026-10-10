/* Monitor della percezione pubblica FVG: logica del cruscotto.
 *
 * Mostra solo dati reali: legge /api/istantanea dal servizio indicato in
 * data-servizio e resta in ascolto su /api/flusso (eventi SSE). Se il servizio
 * non è indicato, il cruscotto resta vuoto e lo dice chiaramente.
 *
 * Il cruscotto non contiene e non riceve mai chiavi private: l'accesso al
 * servizio avviene con un cookie di sessione HttpOnly impostato dal server.
 */
(() => {
  'use strict';

  // ------------------------------------------------------------------ configurazione
  const parametri = new URLSearchParams(location.search);
  // L'indirizzo del servizio sta su #monitor (pagina incorporata, es. Atlante FVG) oppure su <body>
  const SERVIZIO = (document.getElementById('monitor')?.dataset.servizio || document.body.dataset.servizio || '')
    .trim().replace(/\/+$/, '');
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
    obiettivo: [],             // varianti del nome del politico o partito monitorato
    ora: () => Date.now(),
  };

  const $ = (id) => document.getElementById(id);
  const tempo = (m) => Date.parse(m.raccolto);
  const css = (nome) => getComputedStyle(document.documentElement).getPropertyValue(nome).trim();

  // ------------------------------------------------------------------ obiettivo del monitoraggio
  const normalizza = (t) => String(t || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[’`]/g, "'").replace(/\s+/g, ' ').trim();
  const fuga = (t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  let schemiObiettivo = [];
  function impostaSchemi() {
    schemiObiettivo = stato.obiettivo.map((v) =>
      new RegExp(`(^|[^\\p{L}\\p{N}])${fuga(normalizza(v))}(?=$|[^\\p{L}\\p{N}])`, 'u'));
  }
  function testoNormalizzato(m) {
    if (m._norm === undefined) {
      Object.defineProperty(m, '_norm', {
        value: normalizza([m.testo, ...m.analisi.entita.map((e) => e.testo)].join(' | ')), enumerable: false,
      });
    }
    return m._norm;
  }
  // Una menzione è pertinente se cita almeno una delle varianti (anche come entità riconosciuta)
  const pertinente = (m) => !schemiObiettivo.length || schemiObiettivo.some((r) => r.test(testoNormalizzato(m)));

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
    for (const m of stato.menzioni.values()) { const t = tempo(m); if (t >= da && t < a && pertinente(m)) r.push(m); }
    return r;
  }
  function intervalli(ore, minuti) {
    const fine = Math.ceil(stato.ora() / (minuti * MINUTO)) * minuti * MINUTO;
    const n = (ore * 60) / minuti;
    const secchi = Array.from({ length: n }, (_, i) => ({ inizio: fine - (n - i) * minuti * MINUTO, voci: [] }));
    const inizio = secchi[0].inizio;
    for (const m of stato.menzioni.values()) {
      const t = tempo(m);
      if (t < inizio || t >= fine || !pertinente(m)) continue;
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
        data: [], conteggi: [], borderWidth: 2, tension: 0.2, spanGaps: true,
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
    // Con un solo politico o partito i dati sono meno: intervalli più ampi, linea più affidabile
    const ampiezza = stato.obiettivo.length ? 30 : INTERVALLO_MIN;
    $('nota-andamento').textContent = `Ultime 6 ore, intervalli di ${ampiezza} minuti. Da −100 a +100.`;
    const secchi = intervalli(ORE_GRAFICO, ampiezza);
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
    const elenco = [...stato.menzioni.values()].filter((m) => pertinente(m) && passaFiltri(m)).sort((a, b) => tempo(b) - tempo(a));
    const c = $('flusso');
    c.replaceChildren(...elenco.slice(0, MAX_SCHEDE).map((m) => scheda(m, nuove.has(m.chiave))));
    if (!elenco.length) c.append(elemento('li', 'vuoto', stato.obiettivo.length
      ? `Nessuna menzione di ${stato.obiettivo[0]} nelle ultime 24 ore con questi filtri.` : 'Nessuna menzione con questi filtri.'));
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

  // ------------------------------------------------------------------ pannello obiettivo
  function aggiornaObiettivo() {
    const attivo = stato.obiettivo.length > 0;
    $('obiettivo-attivo').hidden = !attivo;
    const avviso = $('obiettivo-avviso');
    avviso.hidden = true;
    if (attivo) {
      $('obiettivo-nome').textContent = stato.obiettivo.join(', ');
      const ora = stato.ora();
      const n24 = tra(ora - 24 * ORA, ora + 1).length, n1 = tra(ora - ORA, ora + 1).length;
      $('obiettivo-conteggio').textContent = `· ${numero(n24)} menzioni nelle ultime 24 ore, ${numero(n1)} nell'ultima ora`;
      if (!n24) {
        avviso.hidden = false;
        avviso.textContent = SERVIZIO
          ? 'Nessuna menzione ancora: il servizio ha avviato la ricerca su testate e social, i risultati compaiono qui appena arrivano.'
          : 'Il servizio di raccolta non è ancora attivo: il nome resta impostato e verrà cercato appena il servizio sarà collegato.';
      }
    }
    // Suggerimenti: persone e partiti citati più spesso nelle ultime 24 ore
    const conta = new Map();
    for (const m of stato.menzioni.values()) {
      for (const e of m.analisi.entita) if (e.tipo === 'persona' || e.tipo === 'partito') conta.set(e.testo, (conta.get(e.testo) || 0) + 1);
    }
    const lista = $('obiettivo-suggerimenti');
    const voci = [...conta.entries()].sort((a, b) => b[1] - a[1]).slice(0, 30);
    if (lista.dataset.firma !== voci.map((v) => v[0]).join('|')) {
      lista.dataset.firma = voci.map((v) => v[0]).join('|');
      lista.replaceChildren(...voci.map(([nome, n]) => { const o = elemento('option'); o.value = nome; o.label = `${n} menzioni`; return o; }));
    }
  }

  function scegliObiettivo(testo, daServizio = false) {
    const varianti = [...new Set(String(testo).split(',').map((v) => v.replace(/\s+/g, ' ').trim()).filter((v) => v.length >= 2))].slice(0, 5);
    stato.obiettivo = varianti;
    impostaSchemi();
    $('obiettivo-campo').value = '';
    try { localStorage.setItem('monitor-obiettivo', JSON.stringify(varianti)); } catch { /* facoltativo */ }
    const url = new URL(location.href);
    if (varianti.length) url.searchParams.set('nome', varianti.join(', ')); else url.searchParams.delete('nome');
    history.replaceState(null, '', url);
    if (SERVIZIO && !daServizio) inviaObiettivo(varianti);
    aggiornaIndicatori(); aggiornaGrafici(); aggiornaTemi(); aggiornaFonti(); aggiornaObiettivo();
    disegnaFlusso();
    avvisaAltezza();
  }

  async function inviaObiettivo(varianti) {
    try {
      const r = await fetch(`${SERVIZIO}/api/obiettivo`, {
        method: 'POST', credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ varianti }),
      });
      if (!r.ok) {
        const avviso = $('obiettivo-avviso');
        avviso.hidden = false;
        avviso.textContent = r.status === 422
          ? 'Il servizio non accetta questo nome: usa solo lettere, cifre, spazi, apostrofi, punti e trattini, al massimo 5 varianti.'
          : 'Il servizio non ha ricevuto il nome: il filtro funziona sui dati già raccolti, ma la nuova ricerca non è partita.';
      }
    } catch { /* il filtro locale resta comunque attivo */ }
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
      aggiornaObiettivo();
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
    if (!stato.obiettivo.length && dati.obiettivo?.length) scegliObiettivo(dati.obiettivo.join(', '), true);
    else if (stato.obiettivo.length && (dati.obiettivo || []).join('|') !== stato.obiettivo.join('|')) inviaObiettivo(stato.obiettivo);
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
    sorgente.addEventListener('obiettivo', (e) => {
      const v = JSON.parse(e.data).varianti || [];
      if (v.join('|') !== stato.obiettivo.join('|')) scegliObiettivo(v.join(', '), true);
    });
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

  // ------------------------------------------------------------------ servizio non collegato
  function senzaServizio() {
    impostaStato('errore', 'Dati reali non ancora attivi');
    const avviso = $('avviso-servizio');
    if (avviso) avviso.hidden = false;
    disegnaFlusso();
  }

  // ------------------------------------------------------------------ comandi
  function collegaComandi() {
    $('filtri').addEventListener('submit', (e) => e.preventDefault());
    $('obiettivo-modulo').addEventListener('submit', (e) => {
      e.preventDefault();
      if ($('obiettivo-campo').value.trim()) scegliObiettivo($('obiettivo-campo').value);
    });
    $('obiettivo-togli').addEventListener('click', () => scegliObiettivo(''));
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

    $('tasto-tema')?.addEventListener('click', () => {
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
    let iniziale = parametri.get('nome') || '';
    if (!iniziale) { try { iniziale = (JSON.parse(localStorage.getItem('monitor-obiettivo') || '[]') || []).join(', '); } catch { /* facoltativo */ } }
    if (iniziale) { stato.obiettivo = iniziale.split(',').map((v) => v.trim()).filter((v) => v.length >= 2).slice(0, 5); impostaSchemi(); }
    creaGrafici();
    if (SERVIZIO) avviaServizio(); else senzaServizio();
  }
  let giaSbloccato = false;
  try { giaSbloccato = sessionStorage.getItem('monitor-sbloccato') === IMPRONTA_PAROLA; } catch { /* facoltativo */ }
  // Senza il riquadro della parola d'ordine (pagina che ha già un suo accesso) si parte subito
  if (giaSbloccato || !$('lucchetto-modulo')) sblocca();
  $('lucchetto-modulo')?.addEventListener('submit', async (e) => {
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
