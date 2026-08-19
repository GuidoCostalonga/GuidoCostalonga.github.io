/* ============================================================
   01-core.js
   ============================================================ */
/* ==================================================================
   STUDIO DSA OFFLINE
   01-core.js — fondamenta: utilità, salvataggio locale, avvisi, modali

   Tutto quello che c'è qui dentro funziona senza Internet.
   Nessuna richiesta di rete viene mai effettuata dall'applicazione.
   ================================================================== */

'use strict';

const APP = {
  nome: 'Studio DSA',
  versione: '1.0',
  chiaveDati: 'studio_dsa_dati_v1',   // localStorage di riserva
  dbNome: 'studio-dsa',
  dbStore: 'dati'
};

/* ------------------------------------------------------------------
   Utilità di base
   ------------------------------------------------------------------ */

const $  = (sel, ctx) => (ctx || document).querySelector(sel);
const $$ = (sel, ctx) => Array.from((ctx || document).querySelectorAll(sel));

/** Rende sicuro qualsiasi testo dell'utente prima di inserirlo nella pagina. */
function esc(v) {
  return String(v == null ? '' : v)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/** Identificatore semplice e unico (senza librerie). */
function uid(prefisso) {
  return (prefisso || 'id') + '-' + Date.now().toString(36) + '-' +
    Math.random().toString(36).slice(2, 8);
}

function oraISO() { return new Date().toISOString(); }
function oggiISO() {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
function giorniDaOggi(n) {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

const MESI = ['gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno',
  'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre'];
const GIORNI = ['domenica', 'lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato'];

/** "2026-08-16" → "domenica 16 agosto" */
function dataInParole(iso) {
  if (!iso) return '';
  const p = String(iso).slice(0, 10).split('-');
  if (p.length !== 3) return iso;
  const d = new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
  if (isNaN(d.getTime())) return iso;
  return GIORNI[d.getDay()] + ' ' + d.getDate() + ' ' + MESI[d.getMonth()];
}

/** "2026-08-16T17:32:11.000Z" → "oggi 17:32" / "ieri 19:42" / "12 agosto 19:42" */
function quandoInParole(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  const ore = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  const g = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const o = new Date();
  const oggi = new Date(o.getFullYear(), o.getMonth(), o.getDate());
  const diff = Math.round((oggi - g) / 86400000);
  if (diff === 0) return 'oggi ' + ore;
  if (diff === 1) return 'ieri ' + ore;
  return d.getDate() + ' ' + MESI[d.getMonth()] + ' ' + ore;
}

function debounce(fn, ms) {
  let t = null;
  return function () {
    const args = arguments, self = this;
    clearTimeout(t);
    t = setTimeout(() => fn.apply(self, args), ms);
  };
}

function limita(v, min, max) { return Math.min(max, Math.max(min, v)); }

/** Taglia un testo lungo mettendo i puntini. */
function accorcia(t, n) {
  t = String(t || '').replace(/\s+/g, ' ').trim();
  return t.length > n ? t.slice(0, n - 1) + '…' : t;
}

/** Scarica un file creato in locale (nessun server coinvolto). */
function scaricaFile(nomeFile, contenuto, tipo) {
  try {
    const blob = (contenuto instanceof Blob) ? contenuto : new Blob([contenuto], { type: tipo || 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nomeFile;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => { URL.revokeObjectURL(url); a.remove(); }, 1500);
    return true;
  } catch (e) {
    avvisoErrore('Non sono riuscito a preparare il file da salvare. Prova a usare un altro browser.');
    return false;
  }
}

/** Legge un file scelto dall'utente. Restituisce una Promise con il testo. */
function leggiFileTesto(file) {
  return new Promise((risolvi, rifiuta) => {
    const r = new FileReader();
    r.onload = () => risolvi(String(r.result || ''));
    r.onerror = () => rifiuta(new Error('lettura'));
    r.readAsText(file, 'utf-8');
  });
}

/**
 * Legge un'immagine scelta dall'utente e la rimpicciolisce.
 * Restituisce una data URL: l'immagine resta dentro i dati dell'app,
 * sul dispositivo, senza file esterni da tenere insieme.
 */
function immagineRidotta(file, latoMax) {
  return new Promise((risolvi, rifiuta) => {
    if (!file || !/^image\//.test(file.type || '')) { rifiuta(new Error('tipo')); return; }
    const lettore = new FileReader();
    lettore.onerror = () => rifiuta(new Error('lettura'));
    lettore.onload = () => {
      const img = new Image();
      img.onload = () => {
        const lato = latoMax || 420;
        const scala = Math.min(1, lato / Math.max(img.width || 1, img.height || 1));
        const l = Math.max(1, Math.round((img.width || 1) * scala));
        const a = Math.max(1, Math.round((img.height || 1) * scala));
        try {
          const cv = document.createElement('canvas');
          cv.width = l; cv.height = a;
          const g = cv.getContext('2d');
          g.fillStyle = '#ffffff';           // le trasparenze diventerebbero nere in JPEG
          g.fillRect(0, 0, l, a);
          g.drawImage(img, 0, 0, l, a);
          risolvi(cv.toDataURL('image/jpeg', 0.82));
        } catch (e) { rifiuta(new Error('conversione')); }
      };
      img.onerror = () => rifiuta(new Error('immagine'));
      img.src = String(lettore.result);
    };
    lettore.readAsDataURL(file);
  });
}

/** Accetta solo immagini incorporate: mai un indirizzo esterno. */
function immagineSicura(dato) {
  const s = String(dato || '');
  return /^data:image\/(png|jpeg|gif|webp);base64,[A-Za-z0-9+/=]+$/.test(s.replace(/\s/g, '')) ? s : '';
}

/** Apre il selettore di file del dispositivo. */
function scegliFile(accetta) {
  return new Promise((risolvi) => {
    const inp = document.createElement('input');
    inp.type = 'file';
    if (accetta) inp.accept = accetta;
    inp.style.position = 'fixed';
    inp.style.left = '-9999px';
    document.body.appendChild(inp);
    inp.addEventListener('change', () => {
      const f = inp.files && inp.files[0] ? inp.files[0] : null;
      inp.remove();
      risolvi(f);
    });
    inp.click();
  });
}

/* ------------------------------------------------------------------
   Avvisi (toast) e finestre di dialogo
   ------------------------------------------------------------------ */

let _toastTimer = null;
function toast(messaggio, tipo) {
  const box = $('#toast');
  if (!box) return;
  box.textContent = messaggio;
  box.style.borderColor = tipo === 'errore' ? 'var(--errore)' :
                          tipo === 'ok' ? 'var(--ok)' : 'var(--primario)';
  box.classList.add('mostra');
  clearTimeout(_toastTimer);
  _toastTimer = setTimeout(() => box.classList.remove('mostra'), tipo === 'errore' ? 5200 : 2600);
}

function avvisoErrore(messaggio) { toast('⚠️ ' + messaggio, 'errore'); }
function avvisoOk(messaggio) { toast('✓ ' + messaggio, 'ok'); }

/**
 * Finestra modale generica.
 * campi: [{nome, etichetta, tipo:'testo'|'area'|'numero'|'data'|'scelta', valore, opzioni, aiuto}]
 * Restituisce una Promise: oggetto con i valori, oppure null se annullata.
 */
function finestra(opz) {
  return new Promise((risolvi) => {
    const box = $('#modale');
    const campi = opz.campi || [];
    const attivoPrima = document.activeElement;

    const htmlCampi = campi.map((c, i) => {
      const id = 'campo-' + i;
      const val = c.valore == null ? '' : c.valore;
      let controllo;
      if (c.tipo === 'area') {
        controllo = `<textarea class="area" id="${id}" rows="${c.righe || 4}" spellcheck="true">${esc(val)}</textarea>`;
      } else if (c.tipo === 'scelta') {
        controllo = `<select class="campo" id="${id}">` +
          (c.opzioni || []).map((o) => {
            const v = typeof o === 'string' ? o : o.v;
            const t = typeof o === 'string' ? o : o.t;
            return `<option value="${esc(v)}"${String(v) === String(val) ? ' selected' : ''}>${esc(t)}</option>`;
          }).join('') + '</select>';
      } else if (c.tipo === 'checkbox') {
        controllo = `<label class="check"><input type="checkbox" id="${id}"${val ? ' checked' : ''}> <span>${esc(c.testoCheck || '')}</span></label>`;
      } else {
        const t = c.tipo === 'numero' ? 'number' : c.tipo === 'data' ? 'date' : 'text';
        controllo = `<input class="campo" type="${t}" id="${id}" value="${esc(val)}" spellcheck="true">`;
      }
      return `<div class="campo-blocco">
          ${c.tipo === 'checkbox' ? '' : `<label class="etichetta" for="${id}">${esc(c.etichetta)}</label>`}
          ${controllo}
          ${c.aiuto ? `<p class="aiutino">${esc(c.aiuto)}</p>` : ''}
        </div>`;
    }).join('');

    box.innerHTML = `<div class="modale-box" role="dialog" aria-modal="true" aria-labelledby="modale-titolo">
        <h2 id="modale-titolo">${esc(opz.titolo || '')}</h2>
        ${opz.testo ? `<p>${esc(opz.testo)}</p>` : ''}
        ${htmlCampi}
        <div class="modale-azioni">
          <button type="button" class="btn" data-m="annulla">${esc(opz.testoAnnulla || 'Annulla')}</button>
          <button type="button" class="btn ${opz.pericolo ? 'btn-errore' : 'btn-primario'}" data-m="ok">${esc(opz.testoOk || 'Va bene')}</button>
        </div>
      </div>`;
    box.hidden = false;

    function chiudi(risultato) {
      box.hidden = true;
      box.innerHTML = '';
      box.onclick = null;
      document.removeEventListener('keydown', tasti, true);
      if (attivoPrima && attivoPrima.focus) { try { attivoPrima.focus(); } catch (e) { /* niente */ } }
      risolvi(risultato);
    }
    function conferma() {
      const out = {};
      campi.forEach((c, i) => {
        const n = $('#campo-' + i, box);
        out[c.nome] = c.tipo === 'checkbox' ? !!n.checked : n.value;
      });
      chiudi(campi.length ? out : {});
    }
    function tasti(e) {
      if (e.key === 'Escape') { e.preventDefault(); chiudi(null); }
      if (e.key === 'Tab') {
        const f = $$('button, input, textarea, select, a[href]', box).filter((x) => !x.disabled);
        if (!f.length) return;
        const primo = f[0], ultimo = f[f.length - 1];
        if (e.shiftKey && document.activeElement === primo) { e.preventDefault(); ultimo.focus(); }
        else if (!e.shiftKey && document.activeElement === ultimo) { e.preventDefault(); primo.focus(); }
      }
      if (e.key === 'Enter' && e.target.tagName !== 'TEXTAREA' && e.target.closest('.modale-box')) {
        e.preventDefault(); conferma();
      }
    }
    // IMPORTANTE: onclick (uno solo), non addEventListener.
    // Con addEventListener ogni finestra lasciava attaccato il proprio
    // ascoltatore: dalla seconda in poi quello vecchio svuotava la finestra
    // un istante prima che quello nuovo leggesse i campi, e il pulsante
    // "Va bene" non faceva più niente. (Bug trovato dopo la consegna.)
    box.onclick = (e) => {
      const b = e.target.closest('[data-m]');
      if (!b) return;
      if (b.dataset.m === 'ok') conferma(); else chiudi(null);
    };
    document.addEventListener('keydown', tasti, true);
    const primo = $('input, textarea, select, button', box);
    if (primo) primo.focus();
  });
}

/** Domanda sì / no. */
async function conferma(titolo, testo, testoOk, pericolo) {
  const r = await finestra({ titolo, testo, testoOk: testoOk || 'Sì, vai', testoAnnulla: 'No, annulla', pericolo: !!pericolo });
  return r !== null;
}

/** Chiede un solo valore. */
async function chiediTesto(titolo, etichetta, valore, tipo) {
  const r = await finestra({ titolo, campi: [{ nome: 'v', etichetta, valore, tipo: tipo || 'testo' }] });
  return r ? r.v : null;
}

/* ------------------------------------------------------------------
   Salvataggio locale: IndexedDB con riserva su localStorage
   ------------------------------------------------------------------ */

const Archivio = (function () {
  let db = null;
  let usaIDB = true;

  function apri() {
    return new Promise((risolvi) => {
      if (!('indexedDB' in window)) { usaIDB = false; return risolvi(false); }
      let req;
      try { req = indexedDB.open(APP.dbNome, 1); }
      catch (e) { usaIDB = false; return risolvi(false); }
      req.onupgradeneeded = () => {
        const d = req.result;
        if (!d.objectStoreNames.contains(APP.dbStore)) d.createObjectStore(APP.dbStore);
      };
      req.onsuccess = () => { db = req.result; risolvi(true); };
      req.onerror = () => { usaIDB = false; risolvi(false); };
      // Se il browser blocca IndexedDB (per esempio in navigazione privata su iOS)
      setTimeout(() => { if (!db) { usaIDB = false; risolvi(false); } }, 2500);
    });
  }

  function leggi(chiave) {
    return new Promise((risolvi) => {
      if (usaIDB && db) {
        try {
          const tx = db.transaction(APP.dbStore, 'readonly');
          const req = tx.objectStore(APP.dbStore).get(chiave);
          req.onsuccess = () => risolvi(req.result === undefined ? null : req.result);
          req.onerror = () => risolvi(leggiLocale(chiave));
          return;
        } catch (e) { /* passo alla riserva */ }
      }
      risolvi(leggiLocale(chiave));
    });
  }

  function scrivi(chiave, valore) {
    return new Promise((risolvi, rifiuta) => {
      if (usaIDB && db) {
        try {
          const tx = db.transaction(APP.dbStore, 'readwrite');
          tx.objectStore(APP.dbStore).put(valore, chiave);
          tx.oncomplete = () => risolvi(true);
          tx.onerror = () => {
            if (scriviLocale(chiave, valore)) risolvi(true);
            else rifiuta(new Error('spazio'));
          };
          return;
        } catch (e) { /* passo alla riserva */ }
      }
      if (scriviLocale(chiave, valore)) risolvi(true); else rifiuta(new Error('spazio'));
    });
  }

  function cancella(chiave) {
    return new Promise((risolvi) => {
      try { localStorage.removeItem(APP.chiaveDati + ':' + chiave); } catch (e) { /* niente */ }
      if (usaIDB && db) {
        try {
          const tx = db.transaction(APP.dbStore, 'readwrite');
          tx.objectStore(APP.dbStore).delete(chiave);
          tx.oncomplete = () => risolvi(true);
          tx.onerror = () => risolvi(false);
          return;
        } catch (e) { /* niente */ }
      }
      risolvi(true);
    });
  }

  function leggiLocale(chiave) {
    try {
      const s = localStorage.getItem(APP.chiaveDati + ':' + chiave);
      return s ? JSON.parse(s) : null;
    } catch (e) { return null; }
  }
  function scriviLocale(chiave, valore) {
    try {
      localStorage.setItem(APP.chiaveDati + ':' + chiave, JSON.stringify(valore));
      return true;
    } catch (e) { return false; }
  }

  return {
    apri, leggi, scrivi, cancella,
    conIDB: () => usaIDB && !!db
  };
})();

/* ------------------------------------------------------------------
   Stato dell'applicazione (tutti i dati dell'utente)
   ------------------------------------------------------------------ */

/** Struttura vuota di partenza: serve anche come riferimento per i backup. */
function datiVuoti() {
  return {
    versione: 1,
    profilo: { nome: '', classe: '', materie: null },
    impostazioni: {
      tema: 'chiara',
      font: 'sistema',
      testoUI: '2',
      onboardingFatto: false,
      // lettura
      dimensione: 20, interlinea: 1.8, lettere: 0.02, parole: 0.16,
      larghezza: 42, sfondoLettura: 'bianco', spaziaturaSillabe: false,
      // sintesi vocale
      voce: '', velocita: 0.95, tono: 1,
      // righello
      righelloRighe: 3,
      // timer
      timerStudio: 20, timerPausa: 5,
      // varie
      checklistVisibile: true
    },
    testi: [],          // testi salvati nella sezione LEGGI
    documenti: [],      // documenti dell'editor di scrittura
    appunti: [],
    flashcard: [],
    compiti: [],
    formule: [],
    mappe: [],
    vocabolario: [],
    paroleDifficili: [],
    inglese: [],
    quaderno: [],       // quaderno di matematica
    erroriMate: [],     // "i miei errori"
    libri: [],          // PDF conosciuti (senza i byte del file)
    evidenziazioni: [],
    notePdf: [],
    segnalibri: [],
    esercizi: [],       // testi degli esercizi portati in matematica
    lavoroEquazione: null   // il quaderno "Lo risolvo io" ancora aperto
  };
}

const Stato = datiVuoti();

/** Fonde i dati letti dal dispositivo dentro lo stato, senza perdere i campi nuovi. */
function applicaDati(letti) {
  if (!letti || typeof letti !== 'object') return;
  const vuoto = datiVuoti();
  Object.keys(vuoto).forEach((k) => {
    if (k === 'impostazioni' || k === 'profilo') {
      Stato[k] = Object.assign({}, vuoto[k], letti[k] && typeof letti[k] === 'object' ? letti[k] : {});
    } else if (Array.isArray(vuoto[k])) {
      Stato[k] = Array.isArray(letti[k]) ? letti[k] : [];
    } else {
      Stato[k] = letti[k] !== undefined ? letti[k] : vuoto[k];
    }
  });
}

let _salvataggioInCorso = false;
const salvaOra = async function () {
  if (_salvataggioInCorso) return;
  _salvataggioInCorso = true;
  try {
    await Archivio.scrivi('dati', JSON.parse(JSON.stringify(Stato)));
    segnalaSalvato();
  } catch (e) {
    avvisoErrore('Non c\'è abbastanza spazio sul dispositivo per salvare. Prova a eliminare qualche materiale vecchio o a fare un backup.');
  } finally {
    _salvataggioInCorso = false;
  }
};

const salva = debounce(salvaOra, 350);

/** Mostra discretamente "✓ Salvato" dove previsto. */
function segnalaSalvato() {
  $$('.salvato').forEach((n) => {
    n.textContent = '✓ Salvato';
    n.classList.add('mostra');
    clearTimeout(n._t);
    n._t = setTimeout(() => n.classList.remove('mostra'), 1800);
  });
}

/* ------------------------------------------------------------------
   Materie
   ------------------------------------------------------------------ */

const MATERIE_INIZIALI = ['Italiano', 'Matematica', 'Inglese', 'Storia', 'Geografia',
  'Scienze', 'Tecnologia', 'Arte', 'Musica', 'Educazione civica', 'Altra'];

function materie() {
  const m = Stato.profilo.materie;
  return (Array.isArray(m) && m.length) ? m : MATERIE_INIZIALI;
}

function opzioniMaterie(scelta) {
  return materie().map((m) =>
    `<option value="${esc(m)}"${m === scelta ? ' selected' : ''}>${esc(m)}</option>`).join('');
}

/* ------------------------------------------------------------------
   Piccoli aiuti per costruire l'interfaccia
   ------------------------------------------------------------------ */

/** Intestazione standard di una sezione, con pulsante Home sempre disponibile. */
function testaSezione(icona, titolo, sottotitolo, azioniHtml) {
  return `<div class="sezione-testa">
      <h1><span aria-hidden="true">${icona}</span> ${esc(titolo)}</h1>
      <div class="spinta">${azioniHtml || ''}</div>
    </div>
    ${sottotitolo ? `<p class="sezione-sub">${esc(sottotitolo)}</p>` : ''}`;
}

function schedaVuota(icona, testo, suggerimento) {
  return `<div class="vuoto"><span class="ic" aria-hidden="true">${icona}</span>
      <p><b>${esc(testo)}</b></p>${suggerimento ? `<p>${esc(suggerimento)}</p>` : ''}</div>`;
}

/** Pulsante con icona + testo. */
function bottone(azione, icona, testo, classe, extra) {
  return `<button type="button" class="btn ${classe || ''}" data-az="${esc(azione)}" ${extra || ''}>` +
    (icona ? `<span aria-hidden="true">${icona}</span>` : '') + `<span>${esc(testo)}</span></button>`;
}

/* ------------------------------------------------------------------
   Gestione degli errori: mai codici tecnici sullo schermo
   ------------------------------------------------------------------ */

window.addEventListener('error', function (e) {
  if (e && e.message && /ResizeObserver/.test(e.message)) return;
  console.error('[Studio DSA]', e.error || e.message);
  toast('⚠️ Qualcosa non ha funzionato in questa funzione. Prova a tornare alla Home e riaprirla.', 'errore');
});
window.addEventListener('unhandledrejection', function (e) {
  console.error('[Studio DSA]', e.reason);
});


/* ============================================================
   02-ui.js
   ============================================================ */
/* ==================================================================
   02-ui.js — navigazione, home, aspetto, ricerca, backup
   ================================================================== */

/** Ogni sezione registra qui la propria funzione di disegno. */
const VISTE = {};

/** Sezioni principali mostrate nella Home. */
const SEZIONI = [
  { id: 'pdf',      ic: '📄', tit: 'PDF e libri',   des: 'Leggi e ascolta i tuoi libri.' },
  { id: 'leggi',    ic: '📖', tit: 'Leggi',         des: 'Testi più facili da leggere.' },
  { id: 'scrivi',   ic: '✍️', tit: 'Scrivi',        des: 'Scrivi con meno difficoltà.' },
  { id: 'mate',     ic: '🔢', tit: 'Matematica',    des: 'Esercizi e formule passo passo.' },
  { id: 'studia',   ic: '🧠', tit: 'Studia',        des: 'Organizza quello che devi imparare.' },
  { id: 'mappe',    ic: '🗺️', tit: 'Mappe',         des: 'Crea mappe concettuali.' },
  { id: 'flash',    ic: '🃏', tit: 'Flashcard',     des: 'Ripassa velocemente.' },
  { id: 'appunti',  ic: '📓', tit: 'I miei appunti', des: 'Conserva il materiale.' },
  { id: 'compiti',  ic: '📅', tit: 'Compiti',       des: 'Organizza il lavoro.' },
  { id: 'focus',    ic: '🎯', tit: 'Concentrati',   des: 'Studia una cosa alla volta.' },
  { id: 'parole',   ic: '🔤', tit: 'Le mie parole', des: 'Vocabolario e parole difficili.' },
  { id: 'impo',     ic: '⚙️', tit: 'Impostazioni',  des: 'Adatta l\'app a te.' }
];

/** Voci della barra in basso sui telefoni: poche e sempre le stesse. */
const TAB = ['home', 'pdf', 'scrivi', 'mate', 'studia'];
const TAB_ICONE = { home: '🏠', pdf: '📄', scrivi: '✍️', mate: '🔢', studia: '🧠' };
const TAB_NOMI = { home: 'Home', pdf: 'PDF', scrivi: 'Scrivi', mate: 'Matematica', studia: 'Studia' };

let vistaCorrente = 'home';
let parametroCorrente = '';

/* ------------------------------------------------------------------
   Aspetto: temi, font, dimensioni
   ------------------------------------------------------------------ */

const SFONDI_LETTURA = {
  bianco: { s: '#ffffff', t: '#151515' },
  crema:  { s: '#fbf3e2', t: '#2b2318' },
  giallo: { s: '#fdf6c8', t: '#2a2712' },
  grigio: { s: '#e9edf0', t: '#1d2427' },
  scuro:  { s: '#161c21', t: '#e9eff4' }
};

/** Applica al documento tutte le preferenze visive salvate. */
function applicaAspetto() {
  const i = Stato.impostazioni;
  const h = document.documentElement;
  h.dataset.tema = i.tema || 'chiara';
  h.dataset.testo = i.testoUI || '2';
  if (i.font && i.font !== 'sistema') h.dataset.font = i.font; else delete h.dataset.font;

  const sf = SFONDI_LETTURA[i.sfondoLettura] || SFONDI_LETTURA.bianco;
  h.style.setProperty('--f-dim', i.dimensione + 'px');
  h.style.setProperty('--f-interlinea', String(i.interlinea));
  h.style.setProperty('--f-lettere', i.lettere + 'em');
  h.style.setProperty('--f-parole', i.parole + 'em');
  h.style.setProperty('--f-larghezza', i.larghezza + 'rem');
  h.style.setProperty('--f-sfondo', sf.s);
  h.style.setProperty('--f-testo', sf.t);

  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', i.tema === 'scura' || i.tema === 'contrasto' ? '#000000' : '#1d4e6f');
}

/* ------------------------------------------------------------------
   Navigazione
   ------------------------------------------------------------------ */

function vaiA(id, parametro) {
  const nuovo = '#/' + id + (parametro ? '/' + encodeURIComponent(parametro) : '');
  if (location.hash === nuovo) disegna();
  else location.hash = nuovo;
}

function leggiIndirizzo() {
  const pezzi = String(location.hash || '').replace(/^#\/?/, '').split('/');
  const id = pezzi[0] || 'home';
  const par = pezzi[1] ? decodeURIComponent(pezzi[1]) : '';
  return { id: VISTE[id] ? id : 'home', par };
}

function disegna() {
  const { id, par } = leggiIndirizzo();
  vistaCorrente = id;
  parametroCorrente = par;
  fermaLettura();          // la voce non deve continuare in un'altra sezione
  chiudiRighello();
  const cont = $('#vista');
  cont.innerHTML = '';
  try {
    VISTE[id](cont, par);
  } catch (e) {
    console.error(e);
    cont.innerHTML = `<div class="card"><h1>Questa parte non si è aperta</h1>
      <p>Puoi tornare alla Home e riprovare. I tuoi dati sono al sicuro.</p>
      ${bottone('home', '🏠', 'Torna alla Home', 'btn-primario')}</div>`;
  }
  aggiornaTab();
  window.scrollTo(0, 0);
  const t = $('h1', cont);
  document.title = (id === 'home' ? 'Studio DSA' : (t ? t.textContent.trim() + ' · Studio DSA' : 'Studio DSA'));
}

function aggiornaTab() {
  const barra = $('#tabbar');
  barra.innerHTML = TAB.map((t) =>
    `<button type="button" data-vai="${t}"${(t === vistaCorrente || (t === 'home' && vistaCorrente === 'home')) ? ' aria-current="page"' : ''}>
       <span class="ic" aria-hidden="true">${TAB_ICONE[t]}</span><span>${TAB_NOMI[t]}</span>
     </button>`).join('');
}

/* ------------------------------------------------------------------
   HOME
   ------------------------------------------------------------------ */

VISTE.home = function (c) {
  const nome = (Stato.profilo.nome || '').trim();
  const compitiOggi = Stato.compiti.filter((x) => !x.fatto && x.data && x.data <= oggiISO()).length;
  const daRipassare = Stato.flashcard.filter((f) => (f.stato || 'nuova') !== 'so').length;

  c.innerHTML = `
    <div class="sezione-testa">
      <h1>Studio DSA</h1>
    </div>
    <p class="sezione-sub">${nome ? 'Ciao ' + esc(nome) + '! ' : ''}Cosa vuoi fare adesso?</p>

    ${compitiOggi || daRipassare ? `<div class="card card-soft">
      <div class="barra-btn" style="margin:0">
        ${compitiOggi ? bottone('compiti', '📅', compitiOggi === 1 ? '1 compito da fare' : compitiOggi + ' compiti da fare', 'btn-attenzione') : ''}
        ${daRipassare ? bottone('flash', '🃏', daRipassare === 1 ? '1 scheda da ripassare' : daRipassare + ' schede da ripassare') : ''}
      </div></div>` : ''}

    <div class="griglia-home">
      ${SEZIONI.map((s) => `
        <button type="button" class="tessera" data-vai="${s.id}">
          <span class="ic" aria-hidden="true">${s.ic}</span>
          <span class="tx"><b>${esc(s.tit)}</b><span>${esc(s.des)}</span></span>
        </button>`).join('')}
    </div>

    <div class="card card-soft" style="margin-top:18px">
      <p style="margin:0"><span aria-hidden="true">🔒</span>
      I tuoi appunti e i tuoi dati rimangono sul tuo dispositivo.
      Questa applicazione non invia i tuoi contenuti a Internet.</p>
    </div>`;
};

/* ------------------------------------------------------------------
   RICERCA GENERALE
   ------------------------------------------------------------------ */

VISTE.cerca = function (c) {
  c.innerHTML = testaSezione('🔍', 'Cerca nei miei materiali', 'La ricerca guarda solo dentro il tuo dispositivo.') +
    `<div class="card">
      <label class="etichetta" for="qCerca">Che cosa stai cercando?</label>
      <input class="campo" id="qCerca" type="search" placeholder="Per esempio: fotosintesi" autocomplete="off">
    </div>
    <div id="risCerca"></div>`;

  const inp = $('#qCerca', c);
  const box = $('#risCerca', c);

  function cerca() {
    const q = inp.value.trim().toLowerCase();
    if (q.length < 2) { box.innerHTML = `<p class="aiutino">Scrivi almeno due lettere.</p>`; return; }
    const trovati = [];
    const agg = (tipo, icona, vista, id, titolo, testo) => {
      const t = (titolo + ' ' + testo).toLowerCase();
      if (t.indexOf(q) >= 0) trovati.push({ tipo, icona, vista, id, titolo, testo });
    };
    Stato.appunti.forEach((a) => agg('Appunto', '📓', 'appunti', a.id, a.titolo, (a.testo || '') + ' ' + (a.chiavi || '')));
    Stato.documenti.forEach((d) => agg('Documento', '✍️', 'scrivi', d.id, d.titolo, d.testo || ''));
    Stato.flashcard.forEach((f) => agg('Flashcard', '🃏', 'flash', f.id, f.fronte, f.retro || ''));
    Stato.compiti.forEach((t) => agg('Compito', '📅', 'compiti', t.id, t.descrizione, t.materia || ''));
    Stato.formule.forEach((f) => agg('Formula', '📐', 'formule', f.id, f.nome, (f.formula || '') + ' ' + (f.spiegazione || '')));
    Stato.vocabolario.forEach((v) => agg('Parola', '🔤', 'parole', v.id, v.parola, v.significato || ''));
    Stato.paroleDifficili.forEach((v) => agg('Parola difficile', '🔤', 'parole', v.id, v.parola, v.esempio || ''));
    Stato.inglese.forEach((v) => agg('Inglese', '🇬🇧', 'parole', v.id, v.en, v.it || ''));
    Stato.quaderno.forEach((v) => agg('Esercizio', '📓', 'quaderno', v.id, v.titolo, (v.testo || '') + ' ' + (v.procedimento || '')));
    Stato.testi.forEach((t) => agg('Testo da leggere', '📖', 'leggi', t.id, t.titolo, t.testo || ''));
    Stato.notePdf.forEach((n) => agg('Nota su PDF', '📄', 'pdf', n.libroId, n.testoSelezionato || '', n.nota || ''));

    box.innerHTML = trovati.length
      ? `<p class="aiutino">${trovati.length === 1 ? 'Ho trovato 1 risultato.' : 'Ho trovato ' + trovati.length + ' risultati.'}</p>
         <ul class="lista">${trovati.slice(0, 60).map((r) => `
           <li class="voce">
             <span aria-hidden="true" style="font-size:1.5em">${r.icona}</span>
             <div class="corpo">
               <b>${esc(accorcia(r.titolo || '(senza titolo)', 70))}</b>
               <span class="meta">${esc(r.tipo)} · ${esc(accorcia(r.testo, 90))}</span>
             </div>
             <div class="azioni">${bottone('vai:' + r.vista, '➡', 'Apri', 'btn-piccolo')}</div>
           </li>`).join('')}</ul>`
      : `<div class="card">${schedaVuota('🔍', 'Non ho trovato niente con questa parola.', 'Prova con una parola più corta o diversa.')}</div>`;
  }

  inp.addEventListener('input', debounce(cerca, 200));
  cerca();
  inp.focus();
};

/* ------------------------------------------------------------------
   IMPOSTAZIONI
   ------------------------------------------------------------------ */

VISTE.impo = function (c) {
  const i = Stato.impostazioni;
  const p = Stato.profilo;

  c.innerHTML = testaSezione('⚙️', 'Impostazioni', 'Le modifiche vengono salvate subito.') + `

  <div class="card">
    <h2>🎨 Come vedo l'app</h2>
    <label class="etichetta" for="selTema">Colori</label>
    <select class="campo" id="selTema">
      <option value="chiara">Chiara</option>
      <option value="crema">Crema</option>
      <option value="giallo">Giallo tenue</option>
      <option value="grigio">Grigio chiaro</option>
      <option value="scura">Scura</option>
      <option value="contrasto">Alto contrasto</option>
    </select>

    <label class="etichetta" for="selFont">Carattere</label>
    <select class="campo" id="selFont">
      <option value="sistema">Carattere del dispositivo (consigliato)</option>
      <option value="verdana">Verdana (lettere larghe)</option>
      <option value="tahoma">Tahoma</option>
      <option value="georgia">Georgia (con grazie)</option>
      <option value="mono">Larghezza fissa</option>
    </select>
    <p class="aiutino">Non esiste un carattere giusto per tutti: prova e tieni quello che ti stanca meno.</p>

    <div class="slider-riga">
      <label for="rgTesto">Grandezza del testo dei menu</label>
      <output id="outTesto"></output>
      <input type="range" id="rgTesto" min="1" max="5" step="1">
    </div>
  </div>

  <div class="card">
    <h2>📖 Come leggo</h2>
    <div class="slider-riga">
      <label for="rgDim">Grandezza del testo da leggere</label><output id="outDim"></output>
      <input type="range" id="rgDim" min="14" max="46" step="1">
    </div>
    <div class="slider-riga">
      <label for="rgInter">Spazio tra le righe</label><output id="outInter"></output>
      <input type="range" id="rgInter" min="1.2" max="3" step="0.1">
    </div>
    <div class="slider-riga">
      <label for="rgLett">Spazio tra le lettere</label><output id="outLett"></output>
      <input type="range" id="rgLett" min="0" max="0.25" step="0.01">
    </div>
    <div class="slider-riga">
      <label for="rgPar">Spazio tra le parole</label><output id="outPar"></output>
      <input type="range" id="rgPar" min="0" max="1" step="0.04">
    </div>
    <div class="slider-riga">
      <label for="rgLarg">Larghezza della colonna</label><output id="outLarg"></output>
      <input type="range" id="rgLarg" min="20" max="70" step="1">
    </div>
    <label class="etichetta" for="selSfondo">Sfondo del foglio di lettura</label>
    <select class="campo" id="selSfondo">
      <option value="bianco">Bianco</option><option value="crema">Crema</option>
      <option value="giallo">Giallo tenue</option><option value="grigio">Grigio chiaro</option>
      <option value="scuro">Scuro</option>
    </select>
    <label class="etichetta" for="selRighello">Righello di lettura: quante righe mostrare</label>
    <select class="campo" id="selRighello">
      <option value="1">Una riga</option><option value="3">Tre righe</option><option value="5">Cinque righe</option>
    </select>

    <div class="foglio" style="margin-top:14px">
      <div class="foglio-testo">Questa è una prova. Se leggi bene questa riga, le impostazioni vanno bene per te.</div>
    </div>
  </div>

  <div class="card">
    <h2>🔊 Voce che legge</h2>
    <div id="boxVoci"></div>
  </div>

  <div class="card">
    <h2>🙋 Il mio profilo</h2>
    <p class="aiutino">Non serve nessuna registrazione: questi dati restano solo qui.</p>
    <label class="etichetta" for="inNome">Nome (facoltativo)</label>
    <input class="campo" id="inNome" type="text" value="${esc(p.nome || '')}" autocomplete="off">
    <label class="etichetta" for="inClasse">Classe (facoltativo)</label>
    <input class="campo" id="inClasse" type="text" value="${esc(p.classe || '')}" autocomplete="off">
    <label class="etichetta" for="inMaterie">Le mie materie (una per riga)</label>
    <textarea class="area" id="inMaterie" rows="5">${esc(materie().join('\n'))}</textarea>
    <div class="barra-btn" style="margin-top:10px">${bottone('salva-profilo', '💾', 'Salva il profilo', 'btn-primario')}</div>
  </div>

  <div class="card">
    <h2>💾 Backup dei miei dati</h2>
    <p>Il backup è un file che contiene tutto quello che hai scritto. Serve se cambi dispositivo o se il browser cancella i dati.</p>
    <div class="barra-btn">
      ${bottone('backup', '💾', 'Crea backup', 'btn-primario btn-grande')}
      ${bottone('ripristina', '📂', 'Ripristina backup', 'btn-grande')}
    </div>
    <p class="aiutino">Nel backup non entrano i file PDF veri e propri (sono troppo grandi): restano note, evidenziazioni e segnalibri, e ti basterà riaprire il PDF dal dispositivo.</p>
  </div>

  <div class="card">
    <h2>ℹ️ Informazioni</h2>
    <p><b>${esc(APP.nome)}</b> versione ${esc(APP.versione)}.</p>
    <p><span aria-hidden="true">🔒</span> I tuoi appunti e i tuoi dati rimangono sul tuo dispositivo.
       Questa applicazione non invia i tuoi contenuti a Internet.</p>
    <p class="aiutino">Salvataggio dati: ${Archivio.conIDB() ? 'database del browser (IndexedDB)' : 'memoria del browser (localStorage)'}.</p>
    <div class="barra-btn">
      ${bottone('rivedi-onboarding', '👋', 'Rivedi la presentazione iniziale')}
      ${bottone('azzera', '🗑️', 'Cancella tutti i miei dati', 'btn-errore')}
    </div>
  </div>`;

  // --- collegamenti dei controlli ---------------------------------
  const i2 = Stato.impostazioni;
  const lega = (sel, chiave, formato, dopo) => {
    const n = $(sel, c);
    if (!n) return;
    n.value = i2[chiave];
    const out = $('#out' + sel.slice(3), c);
    const mostra = () => { if (out) out.textContent = formato ? formato(n.value) : n.value; };
    mostra();
    n.addEventListener('input', () => {
      i2[chiave] = (n.type === 'range') ? Number(n.value) : n.value;
      mostra(); applicaAspetto(); salva();
      if (dopo) dopo();
    });
  };
  $('#selTema', c).value = i2.tema;
  $('#selFont', c).value = i2.font;
  $('#selSfondo', c).value = i2.sfondoLettura;
  $('#selRighello', c).value = String(i2.righelloRighe);
  ['selTema:tema', 'selFont:font', 'selSfondo:sfondoLettura', 'selRighello:righelloRighe'].forEach((coppia) => {
    const [id, chiave] = coppia.split(':');
    $('#' + id, c).addEventListener('change', (e) => {
      i2[chiave] = chiave === 'righelloRighe' ? Number(e.target.value) : e.target.value;
      applicaAspetto(); salva(); avvisoOk('Impostazione salvata');
    });
  });
  lega('#rgTesto', 'testoUI', (v) => ['', 'piccolo', 'normale', 'grande', 'molto grande', 'enorme'][Number(v)]);
  lega('#rgDim', 'dimensione', (v) => v + ' px');
  lega('#rgInter', 'interlinea', (v) => Number(v).toFixed(1));
  lega('#rgLett', 'lettere', (v) => Number(v).toFixed(2));
  lega('#rgPar', 'parole', (v) => Number(v).toFixed(2));
  lega('#rgLarg', 'larghezza', (v) => v + ' caratteri circa');

  disegnaSceltaVoce($('#boxVoci', c));
};

/* ------------------------------------------------------------------
   Azioni delle impostazioni
   ------------------------------------------------------------------ */

async function salvaProfilo() {
  Stato.profilo.nome = $('#inNome').value.trim();
  Stato.profilo.classe = $('#inClasse').value.trim();
  const righe = $('#inMaterie').value.split('\n').map((s) => s.trim()).filter(Boolean);
  Stato.profilo.materie = righe.length ? righe : null;
  await salvaOra();
  avvisoOk('Profilo salvato');
}

function creaBackup() {
  const dati = JSON.parse(JSON.stringify(Stato));
  dati._app = APP.nome;
  dati._versioneApp = APP.versione;
  dati._data = oraISO();
  const ok = scaricaFile('studio-dsa-backup.json', JSON.stringify(dati, null, 2), 'application/json');
  if (ok) avvisoOk('Backup pronto: controlla nei file scaricati');
}

async function ripristinaBackup() {
  const f = await scegliFile('.json,application/json');
  if (!f) return;
  let dati;
  try {
    dati = JSON.parse(await leggiFileTesto(f));
  } catch (e) {
    avvisoErrore('Questo file non sembra un backup di Studio DSA. Prova con un altro file.');
    return;
  }
  if (!dati || typeof dati !== 'object' || !dati.impostazioni) {
    avvisoErrore('Questo file non sembra un backup di Studio DSA.');
    return;
  }
  const conta = ['appunti', 'flashcard', 'compiti', 'documenti', 'formule', 'mappe']
    .map((k) => (Array.isArray(dati[k]) ? dati[k].length : 0)).reduce((a, b) => a + b, 0);
  const ok = await conferma('Ripristinare il backup?',
    'Dentro ci sono circa ' + conta + ' elementi. I dati che hai adesso su questo dispositivo verranno sostituiti.',
    'Sì, ripristina', true);
  if (!ok) return;
  applicaDati(dati);
  await salvaOra();
  applicaAspetto();
  avvisoOk('Backup ripristinato');
  vaiA('home');
}

async function azzeraTutto() {
  const ok = await conferma('Cancellare tutti i dati?',
    'Spariranno appunti, flashcard, compiti, documenti e impostazioni. Non si può tornare indietro. Se non hai un backup, fallo prima.',
    'Sì, cancella tutto', true);
  if (!ok) return;
  applicaDati(datiVuoti());
  await Archivio.cancella('dati');
  await salvaOra();
  applicaAspetto();
  avvisoOk('Dati cancellati');
  vaiA('home');
}

/* ------------------------------------------------------------------
   Modalità senza distrazioni
   ------------------------------------------------------------------ */

function focusAttivo() { return document.documentElement.dataset.focus === '1'; }
function cambiaFocus(attiva) {
  document.documentElement.dataset.focus = attiva ? '1' : '0';
  if (!attiva) delete document.documentElement.dataset.focus;
}


/* ============================================================
   03-voce.js
   ============================================================ */
/* ==================================================================
   03-voce.js — sintesi vocale

   Usa SOLO la voce già installata nel dispositivo (window.speechSynthesis).
   Nessun servizio online: se il dispositivo non ha voci italiane,
   l'app lo dice chiaramente invece di fingere.
   ================================================================== */

const Voce = (function () {
  const disponibile = ('speechSynthesis' in window) && typeof window.SpeechSynthesisUtterance === 'function';
  let vociCache = [];
  let coda = [];          // frasi ancora da leggere
  let indice = 0;
  let inLettura = false;
  let inPausa = false;
  let cb = {};            // callback della lettura in corso

  function aggiornaVoci() {
    if (!disponibile) return [];
    try { vociCache = window.speechSynthesis.getVoices() || []; } catch (e) { vociCache = []; }
    return vociCache;
  }
  if (disponibile) {
    aggiornaVoci();
    try { window.speechSynthesis.onvoiceschanged = aggiornaVoci; } catch (e) { /* niente */ }
  }

  function voci() { if (!vociCache.length) aggiornaVoci(); return vociCache; }
  function vociItaliane() { return voci().filter((v) => /^it/i.test(v.lang || '')); }

  function voceScelta() {
    const nome = Stato.impostazioni.voce;
    const tutte = voci();
    if (nome) {
      const v = tutte.find((x) => x.name === nome);
      if (v) return v;
    }
    const it = vociItaliane();
    if (it.length) {
      // preferisco una voce locale (funziona anche senza rete)
      return it.find((v) => v.localService) || it[0];
    }
    return null;
  }

  /** true se esiste almeno una voce italiana installata nel dispositivo. */
  function haItaliano() { return vociItaliane().length > 0; }

  function fermaTutto() {
    if (!disponibile) return;
    coda = []; indice = 0; inLettura = false; inPausa = false;
    try { window.speechSynthesis.cancel(); } catch (e) { /* niente */ }
    if (cb.onFine) { const f = cb.onFine; cb = {}; try { f(true); } catch (e) { /* niente */ } }
    cb = {};
  }

  /**
   * Legge un testo dividendolo in frasi (così si può evidenziare quella corrente
   * e la pausa funziona bene su tutti i sistemi).
   * opzioni: { onFrase(indice, frase), onFine(interrotto), velocita, lang }
   */
  function parla(testo, opzioni) {
    opzioni = opzioni || {};
    if (!disponibile) {
      avvisoErrore('Questo browser non sa leggere ad alta voce. Puoi comunque usare tutte le altre funzioni.');
      return false;
    }
    const pezzi = Array.isArray(testo) ? testo.slice() : dividiInFrasi(String(testo || ''));
    if (!pezzi.length) { toast('Non c\'è testo da leggere.'); return false; }

    fermaTutto();
    coda = pezzi; indice = 0; inLettura = true; inPausa = false; cb = opzioni;

    if (!haItaliano()) {
      toast('Nel dispositivo non trovo una voce italiana: proverò con la voce predefinita.');
    }
    prossima();
    return true;
  }

  function prossima() {
    if (!inLettura || indice >= coda.length) {
      const finito = inLettura;
      inLettura = false;
      if (finito && cb.onFine) { const f = cb.onFine; cb = {}; f(false); }
      return;
    }
    const frase = coda[indice];
    const u = new SpeechSynthesisUtterance(frase);
    const v = voceScelta();
    if (v) u.voice = v;
    u.lang = cb.lang || (v ? v.lang : 'it-IT');
    u.rate = limita(Number(cb.velocita || Stato.impostazioni.velocita || 1), 0.5, 2);
    u.pitch = limita(Number(Stato.impostazioni.tono || 1), 0.5, 1.6);
    u.onstart = () => { if (cb.onFrase) cb.onFrase(indice, frase); };
    u.onend = () => { if (!inLettura) return; indice++; prossima(); };
    u.onerror = () => { if (!inLettura) return; indice++; prossima(); };
    try { window.speechSynthesis.speak(u); }
    catch (e) { avvisoErrore('La lettura ad alta voce non è partita. Prova a toccare di nuovo il pulsante.'); inLettura = false; }
  }

  function pausa() {
    if (!disponibile || !inLettura) return;
    try { window.speechSynthesis.pause(); inPausa = true; } catch (e) { /* niente */ }
  }
  function riprendi() {
    if (!disponibile) return;
    try { window.speechSynthesis.resume(); inPausa = false; } catch (e) { /* niente */ }
  }
  function stato() { return { inLettura, inPausa, indice, totale: coda.length }; }

  return { disponibile, voci, vociItaliane, haItaliano, parla, pausa, riprendi, ferma: fermaTutto, stato, aggiornaVoci, voceScelta };
})();

function fermaLettura() { if (Voce.disponibile) Voce.ferma(); }

/* ------------------------------------------------------------------
   Divisione del testo in frasi (serve alla lettura e alla modalità
   "una frase alla volta")
   ------------------------------------------------------------------ */

const ABBREVIAZIONI = ['ecc', 'prof', 'dott', 'sig', 'sig.ra', 'pag', 'pagg', 'art', 'n', 'nr',
  'es', 'cfr', 'av', 'a.C', 'd.C', 'ca', 'vol', 'fig', 'tab', 'ing', 'avv', 'geom'];

function dividiInFrasi(testo) {
  const t = String(testo || '').replace(/\r\n?/g, '\n');
  if (!t.trim()) return [];
  const frasi = [];
  let corrente = '';
  for (let i = 0; i < t.length; i++) {
    const ch = t[i];
    corrente += ch;
    if (ch === '.' || ch === '!' || ch === '?' || ch === '…' || ch === ':' || ch === ';') {
      const dopo = t[i + 1] || ' ';
      const prima = t[i - 1] || '';
      // numeri come 3.14 o 12:30 non chiudono la frase
      if (/[0-9]/.test(prima) && /[0-9]/.test(dopo)) continue;
      // abbreviazioni note
      const ultimaParola = (corrente.match(/([A-Za-zÀ-ÿ.]+)\.$/) || [])[1];
      if (ultimaParola && ABBREVIAZIONI.indexOf(ultimaParola.replace(/\.$/, '').toLowerCase()) >= 0) continue;
      if (/\s|\n|$/.test(dopo) || dopo === '"' || dopo === '»') {
        // salto eventuali virgolette di chiusura
        while (i + 1 < t.length && /["»')\]]/.test(t[i + 1])) { corrente += t[++i]; }
        if (corrente.trim()) frasi.push(corrente.trim());
        corrente = '';
      }
    } else if (ch === '\n' && corrente.trim().length > 0) {
      const dopo = t[i + 1] || '';
      if (dopo === '\n') { frasi.push(corrente.trim()); corrente = ''; }
    }
  }
  if (corrente.trim()) frasi.push(corrente.trim());
  return frasi.filter((f) => f.length > 0);
}

function dividiInParole(testo) {
  return String(testo || '').split(/\s+/).filter(Boolean);
}

/* ------------------------------------------------------------------
   Barra dei comandi di lettura, riutilizzabile in tutte le sezioni
   ------------------------------------------------------------------ */

/**
 * Crea la barra ▶ ⏸ ⏹ + velocità.
 * prendiTesto() deve restituire il testo (stringa o array di frasi) da leggere.
 */
function barraLettura(prendiTesto, opzioni) {
  opzioni = opzioni || {};
  const box = document.createElement('div');
  box.className = 'barra-btn';
  if (!Voce.disponibile) {
    box.innerHTML = `<div class="avviso avviso-att" style="margin:0">
        <span class="ic" aria-hidden="true">🔇</span>
        <p>Questo browser non può leggere ad alta voce. Tutto il resto funziona lo stesso.</p></div>`;
    return box;
  }
  box.innerHTML = `
    <button type="button" class="btn btn-primario btn-grande" data-v="play"><span aria-hidden="true">🔊</span><span>Ascolta</span></button>
    <button type="button" class="btn" data-v="pausa"><span aria-hidden="true">⏸</span><span>Pausa</span></button>
    <button type="button" class="btn" data-v="stop"><span aria-hidden="true">⏹</span><span>Ferma</span></button>
    <label class="btn" style="gap:10px">
      <span aria-hidden="true">🐢</span>
      <input type="range" min="0.5" max="1.6" step="0.05" value="${Stato.impostazioni.velocita}"
             aria-label="Velocità della voce" style="width:110px">
      <span aria-hidden="true">🐇</span>
    </label>`;

  const bPausa = $('[data-v="pausa"]', box);
  bPausa.disabled = true;

  box.addEventListener('click', (e) => {
    const b = e.target.closest('[data-v]');
    if (!b) return;
    if (b.dataset.v === 'play') {
      const testo = prendiTesto();
      const ok = Voce.parla(testo, {
        onFrase: opzioni.onFrase,
        onFine: (interrotto) => { bPausa.disabled = true; bPausa.querySelector('span:last-child').textContent = 'Pausa'; if (opzioni.onFine) opzioni.onFine(interrotto); }
      });
      if (ok) { bPausa.disabled = false; }
    } else if (b.dataset.v === 'pausa') {
      const s = Voce.stato();
      if (s.inPausa) { Voce.riprendi(); b.querySelector('span:last-child').textContent = 'Pausa'; }
      else { Voce.pausa(); b.querySelector('span:last-child').textContent = 'Riprendi'; }
    } else {
      Voce.ferma();
      bPausa.disabled = true;
      if (opzioni.onFine) opzioni.onFine(true);
    }
  });
  const range = $('input[type="range"]', box);
  range.addEventListener('input', () => {
    Stato.impostazioni.velocita = Number(range.value);
    salva();
  });
  return box;
}

/** Selettore della voce, usato nelle impostazioni. */
function disegnaSceltaVoce(box) {
  if (!box) return;
  if (!Voce.disponibile) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic" aria-hidden="true">🔇</span>
      <p>Questo browser non ha la lettura ad alta voce. Su iPhone e iPad funziona con Safari,
      su Windows e Android con Chrome o Edge.</p></div>`;
    return;
  }
  const it = Voce.vociItaliane();
  const tutte = Voce.voci();
  const elenco = (it.length ? it : tutte);

  box.innerHTML = `
    ${it.length ? '' : `<div class="avviso avviso-att"><span class="ic" aria-hidden="true">⚠️</span>
      <p>Non trovo una voce italiana installata su questo dispositivo. La lettura funzionerà male.
      Puoi aggiungere una voce italiana dalle impostazioni del dispositivo (Accessibilità → Contenuto pronunciato).</p></div>`}
    <label class="etichetta" for="selVoce">Voce</label>
    <select class="campo" id="selVoce">
      <option value="">Voce automatica del dispositivo</option>
      ${elenco.map((v) => `<option value="${esc(v.name)}"${v.name === Stato.impostazioni.voce ? ' selected' : ''}>${esc(v.name)} (${esc(v.lang)})${v.localService ? '' : ' — non locale'}</option>`).join('')}
    </select>
    <div class="slider-riga" style="margin-top:12px">
      <label for="rgVel">Velocità</label><output id="outVel">${Number(Stato.impostazioni.velocita).toFixed(2)}</output>
      <input type="range" id="rgVel" min="0.5" max="1.6" step="0.05" value="${Stato.impostazioni.velocita}">
    </div>
    <div class="barra-btn">
      <button type="button" class="btn btn-primario" id="btnProva"><span aria-hidden="true">🔊</span><span>Prova la voce</span></button>
    </div>
    <p class="aiutino">Le voci arrivano dal dispositivo, non da Internet. Se una voce è segnata come "non locale" potrebbe non funzionare in modalità aereo.</p>`;

  $('#selVoce', box).addEventListener('change', (e) => {
    Stato.impostazioni.voce = e.target.value; salva();
  });
  const rg = $('#rgVel', box);
  rg.addEventListener('input', () => {
    Stato.impostazioni.velocita = Number(rg.value);
    $('#outVel', box).textContent = Number(rg.value).toFixed(2);
    salva();
  });
  $('#btnProva', box).addEventListener('click', () => {
    Voce.parla('Ciao! Se mi senti bene, la voce funziona. Puoi cambiare la velocità con il cursore.');
  });
}


/* ============================================================
   04-lettura.js
   ============================================================ */
/* ==================================================================
   04-lettura.js — sezione LEGGI, righello, una frase / una parola
   ================================================================== */

/* ------------------------------------------------------------------
   Righello di lettura (una / tre / cinque righe)
   ------------------------------------------------------------------ */

const Righello = (function () {
  let aperto = false;
  let centro = 0;          // posizione verticale (px nella finestra)
  let altezza = 120;

  function altezzaRiga() {
    const i = Stato.impostazioni;
    return Math.max(20, Number(i.dimensione) * Number(i.interlinea));
  }

  function calcolaAltezza() {
    const righe = Number(Stato.impostazioni.righelloRighe) || 3;
    altezza = altezzaRiga() * righe + 8;
  }

  function disegna() {
    const box = $('#righello');
    if (!aperto) { box.hidden = true; box.innerHTML = ''; return; }
    const h = window.innerHeight;
    const top = limita(centro - altezza / 2, 0, Math.max(0, h - altezza));
    box.hidden = false;
    box.innerHTML =
      `<div class="velo" style="top:0;height:${top}px"></div>
       <div class="finestra" style="top:${top}px;height:${altezza}px"></div>
       <div class="velo" style="top:${top + altezza}px;height:${Math.max(0, h - top - altezza)}px"></div>`;
    let barra = $('.righello-attivo');
    if (!barra) {
      barra = document.createElement('div');
      barra.className = 'righello-attivo';
      barra.innerHTML = `
        <button type="button" class="btn btn-piccolo" data-r="su" aria-label="Sposta il righello in alto">⬆</button>
        <button type="button" class="btn btn-piccolo" data-r="giu" aria-label="Sposta il righello in basso">⬇</button>
        <button type="button" class="btn btn-piccolo" data-r="righe">Righe: ${Stato.impostazioni.righelloRighe}</button>
        <button type="button" class="btn btn-piccolo btn-errore" data-r="chiudi">✕ Chiudi</button>`;
      document.body.appendChild(barra);
      barra.addEventListener('click', (e) => {
        const b = e.target.closest('[data-r]');
        if (!b) return;
        if (b.dataset.r === 'su') muovi(-altezzaRiga());
        else if (b.dataset.r === 'giu') muovi(altezzaRiga());
        else if (b.dataset.r === 'righe') {
          const seq = [1, 3, 5];
          const i = seq.indexOf(Number(Stato.impostazioni.righelloRighe));
          Stato.impostazioni.righelloRighe = seq[(i + 1) % seq.length];
          salva(); calcolaAltezza(); b.textContent = 'Righe: ' + Stato.impostazioni.righelloRighe; disegna();
        } else chiudi();
      });
    }
  }

  function muovi(dy) { centro = limita(centro + dy, 0, window.innerHeight); disegna(); }

  function daEvento(e) {
    const y = (e.touches && e.touches[0]) ? e.touches[0].clientY : e.clientY;
    if (typeof y === 'number') { centro = y; disegna(); }
  }

  function tasti(e) {
    if (!aperto) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); muovi(altezzaRiga()); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); muovi(-altezzaRiga()); }
    else if (e.key === 'Escape') { chiudi(); }
  }

  function apri() {
    if (aperto) return;
    aperto = true;
    centro = window.innerHeight * 0.45;
    calcolaAltezza();
    disegna();
    document.addEventListener('mousemove', daEvento);
    document.addEventListener('touchmove', daEvento, { passive: true });
    document.addEventListener('keydown', tasti);
    window.addEventListener('resize', disegna);
    toast('Righello attivo: muovi il dito o il mouse, oppure usa ⬆ ⬇');
  }

  function chiudi() {
    if (!aperto) return;
    aperto = false;
    document.removeEventListener('mousemove', daEvento);
    document.removeEventListener('touchmove', daEvento);
    document.removeEventListener('keydown', tasti);
    window.removeEventListener('resize', disegna);
    const barra = $('.righello-attivo');
    if (barra) barra.remove();
    disegna();
    $$('[data-az="righello"]').forEach((b) => b.setAttribute('aria-pressed', 'false'));
  }

  function alterna() { aperto ? chiudi() : apri(); return aperto; }
  return { apri, chiudi, alterna, get aperto() { return aperto; } };
})();

function chiudiRighello() { Righello.chiudi(); }

/* ------------------------------------------------------------------
   Costruzione del foglio di lettura con le frasi cliccabili
   ------------------------------------------------------------------ */

/** Restituisce { html, frasi } — le frasi servono per la sintesi vocale. */
function fogliaTesto(testo) {
  const paragrafi = String(testo || '').split(/\n{2,}/);
  const frasi = [];
  let n = 0;
  const html = paragrafi.map((p) => {
    const pezzi = dividiInFrasi(p);
    if (!pezzi.length) return '';
    return '<p>' + pezzi.map((f) => {
      frasi.push(f);
      return `<span class="frase" data-f="${n++}" tabindex="0" role="button">${esc(f)}</span> `;
    }).join('') + '</p>';
  }).join('');
  return { html: html || '<p></p>', frasi };
}

/* ------------------------------------------------------------------
   SEZIONE: LEGGI
   ------------------------------------------------------------------ */

let _lettura = { testo: '', frasi: [], titolo: '', id: null, modo: 'normale', frase: 0, parola: 0, timerParole: null };

VISTE.leggi = function (c, par) {
  if (par) {
    const t = Stato.testi.find((x) => x.id === par);
    if (t) { _lettura.testo = t.testo; _lettura.titolo = t.titolo; _lettura.id = t.id; }
  }
  c.innerHTML = testaSezione('📖', 'Leggi', 'Incolla o apri un testo: lo rendiamo più facile da leggere.',
    bottone('home', '🏠', 'Home', 'btn-piccolo')) + `

    <div class="card no-stampa">
      <div class="barra-btn" style="margin-bottom:0">
        ${bottone('leggi-incolla', '📋', 'Scrivi o incolla un testo', 'btn-primario')}
        ${bottone('leggi-importa', '📂', 'Apri un file (.txt / .md)')}
        ${bottone('leggi-salvati', '📚', 'I miei testi salvati')}
        ${bottone('leggi-da-pdf', '📄', 'Prendi da un PDF')}
      </div>
    </div>

    <div id="areaLettura"></div>`;

  disegnaAreaLettura();
};

function disegnaAreaLettura() {
  const box = $('#areaLettura');
  if (!box) return;
  if (!_lettura.testo.trim()) {
    box.innerHTML = `<div class="card">${schedaVuota('📖', 'Non c\'è ancora niente da leggere.',
      'Tocca "Scrivi o incolla un testo" oppure apri un file dal dispositivo.')}</div>`;
    return;
  }

  const modo = _lettura.modo;
  box.innerHTML = `
    <div class="card no-stampa">
      <div class="barra-btn" style="margin-bottom:8px">
        <button type="button" class="btn" data-az="modo:normale" aria-pressed="${modo === 'normale'}"><span aria-hidden="true">📄</span><span>Tutto il testo</span></button>
        <button type="button" class="btn" data-az="modo:frase" aria-pressed="${modo === 'frase'}"><span aria-hidden="true">1️⃣</span><span>Una frase alla volta</span></button>
        <button type="button" class="btn" data-az="modo:parola" aria-pressed="${modo === 'parola'}"><span aria-hidden="true">👁️</span><span>Una parola alla volta</span></button>
      </div>
      <div class="barra-btn" style="margin-bottom:0">
        <button type="button" class="btn" data-az="righello" aria-pressed="${Righello.aperto}"><span aria-hidden="true">📏</span><span>Righello</span></button>
        <button type="button" class="btn" data-az="sillabe" aria-pressed="${Stato.impostazioni.spaziaturaSillabe}"><span aria-hidden="true">🔠</span><span>Più spazio tra le parole</span></button>
        ${bottone('impo', '⚙️', 'Come si vede')}
        ${bottone('leggi-salva', '💾', 'Salva questo testo')}
        ${bottone('leggi-appunto', '📓', 'Porta negli appunti')}
        ${bottone('leggi-stampa', '🖨️', 'Stampa / PDF')}
      </div>
    </div>
    <div id="barraVoce" class="no-stampa"></div>
    <div id="corpoLettura"></div>`;

  const dati = fogliaTesto(_lettura.testo);
  _lettura.frasi = dati.frasi;

  const corpo = $('#corpoLettura');
  if (modo === 'normale') {
    corpo.innerHTML = `<div class="foglio"><div class="foglio-testo${Stato.impostazioni.spaziaturaSillabe ? ' sillabe' : ''}" id="foglioTesto">${dati.html}</div></div>`;
    corpo.addEventListener('click', (e) => {
      const f = e.target.closest('.frase');
      if (!f) return;
      $$('.frase.attiva', corpo).forEach((x) => x.classList.remove('attiva'));
      f.classList.add('attiva');
    });
    corpo.addEventListener('keydown', (e) => {
      if ((e.key === 'Enter' || e.key === ' ') && e.target.classList.contains('frase')) {
        e.preventDefault(); e.target.click();
      }
    });
  } else if (modo === 'frase') {
    disegnaModoFrase(corpo);
  } else {
    disegnaModoParola(corpo);
  }

  const bv = $('#barraVoce');
  bv.innerHTML = '';
  bv.appendChild(barraLettura(
    () => (_lettura.modo === 'frase' ? [_lettura.frasi[_lettura.frase] || ''] : _lettura.frasi),
    {
      onFrase: (i) => {
        const n = $('#foglioTesto [data-f="' + i + '"]');
        if (n) {
          $$('#foglioTesto .frase.attiva').forEach((x) => x.classList.remove('attiva'));
          n.classList.add('attiva');
          n.scrollIntoView({ block: 'center', behavior: 'smooth' });
        }
      },
      onFine: () => { $$('#foglioTesto .frase.attiva').forEach((x) => x.classList.remove('attiva')); }
    }
  ));
}

/* ---- una frase alla volta ---------------------------------------- */
function disegnaModoFrase(corpo) {
  const i = limita(_lettura.frase, 0, Math.max(0, _lettura.frasi.length - 1));
  _lettura.frase = i;
  corpo.innerHTML = `
    <div class="frase-schermo">${esc(_lettura.frasi[i] || '')}</div>
    <p class="frase-contatore">Frase ${i + 1} di ${_lettura.frasi.length}</p>
    <div class="barra-btn centro no-stampa">
      ${bottone('frase-prec', '⬅', 'Precedente', 'btn-grande')}
      ${bottone('frase-ascolta', '🔊', 'Ascolta', 'btn-primario btn-grande')}
      ${bottone('frase-succ', '➡', 'Successiva', 'btn-grande')}
    </div>`;
}

/* ---- una parola alla volta (lettura focale) ---------------------- */
const VELOCITA_PAROLE = [
  { et: 'Molto lenta', ms: 900 }, { et: 'Lenta', ms: 620 },
  { et: 'Normale', ms: 420 }, { et: 'Veloce', ms: 280 }
];
let _velParole = 1;

function disegnaModoParola(corpo) {
  const parole = dividiInParole(_lettura.testo);
  _lettura.parole = parole;
  const i = limita(_lettura.parola, 0, Math.max(0, parole.length - 1));
  _lettura.parola = i;
  corpo.innerHTML = `
    <div class="parola-schermo" id="schermoParola">${esc(parole[i] || '')}</div>
    <p class="frase-contatore">Parola ${i + 1} di ${parole.length}</p>
    <div class="barra-btn centro no-stampa">
      ${bottone('parola-prec', '⬅', 'Indietro')}
      ${bottone('parola-play', '▶', 'Avvia', 'btn-primario btn-grande')}
      ${bottone('parola-succ', '➡', 'Avanti')}
      ${bottone('parola-stop', '⏹', 'Ferma')}
    </div>
    <div class="barra-btn centro no-stampa">
      ${VELOCITA_PAROLE.map((v, k) => `<button type="button" class="btn btn-piccolo" data-az="parola-vel:${k}" aria-pressed="${k === _velParole}">${esc(v.et)}</button>`).join('')}
    </div>`;
}

function avviaParole() {
  fermaParole();
  _lettura.timerParole = setInterval(() => {
    if (_lettura.parola >= _lettura.parole.length - 1) { fermaParole(); return; }
    _lettura.parola++;
    const s = $('#schermoParola');
    if (s) s.textContent = _lettura.parole[_lettura.parola];
    const cont = $('.frase-contatore');
    if (cont) cont.textContent = 'Parola ' + (_lettura.parola + 1) + ' di ' + _lettura.parole.length;
  }, VELOCITA_PAROLE[_velParole].ms);
}
function fermaParole() { if (_lettura.timerParole) { clearInterval(_lettura.timerParole); _lettura.timerParole = null; } }

/* ------------------------------------------------------------------
   Azioni della sezione LEGGI
   ------------------------------------------------------------------ */

async function letturaIncolla() {
  const r = await finestra({
    titolo: 'Scrivi o incolla il testo',
    campi: [
      { nome: 'titolo', etichetta: 'Titolo (facoltativo)', valore: _lettura.titolo || '' },
      { nome: 'testo', etichetta: 'Testo', tipo: 'area', righe: 10, valore: _lettura.testo || '' }
    ],
    testoOk: 'Leggi'
  });
  if (!r) return;
  _lettura.testo = r.testo; _lettura.titolo = r.titolo; _lettura.id = null;
  _lettura.frase = 0; _lettura.parola = 0;
  disegnaAreaLettura();
}

async function letturaImporta() {
  const f = await scegliFile('.txt,.md,text/plain,text/markdown');
  if (!f) return;
  if (f.size > 3 * 1024 * 1024) {
    avvisoErrore('Questo file è molto grande: potrebbe rallentare il dispositivo.');
  }
  try {
    const t = await leggiFileTesto(f);
    _lettura.testo = t;
    _lettura.titolo = f.name.replace(/\.(txt|md)$/i, '');
    _lettura.id = null; _lettura.frase = 0; _lettura.parola = 0;
    disegnaAreaLettura();
    avvisoOk('Testo aperto');
  } catch (e) {
    avvisoErrore('Non sono riuscito ad aprire questo file. Deve essere un file di testo semplice.');
  }
}

async function letturaSalva() {
  const titolo = await chiediTesto('Salva il testo', 'Che nome gli diamo?', _lettura.titolo || 'Testo del ' + dataInParole(oggiISO()));
  if (titolo === null) return;
  if (_lettura.id) {
    const t = Stato.testi.find((x) => x.id === _lettura.id);
    if (t) { t.titolo = titolo; t.testo = _lettura.testo; t.modificato = oraISO(); }
  } else {
    const t = { id: uid('testo'), titolo, testo: _lettura.testo, creato: oraISO(), modificato: oraISO() };
    Stato.testi.unshift(t);
    _lettura.id = t.id;
  }
  _lettura.titolo = titolo;
  await salvaOra();
  avvisoOk('Testo salvato');
}

function letturaSalvati() {
  const box = $('#areaLettura');
  box.innerHTML = `<div class="card">
      <h2>📚 I miei testi salvati</h2>
      ${Stato.testi.length ? `<ul class="lista">${Stato.testi.map((t) => `
        <li class="voce">
          <div class="corpo"><b>${esc(t.titolo || 'Senza titolo')}</b>
          <span class="meta">${esc(quandoInParole(t.modificato))} · ${dividiInParole(t.testo).length} parole</span></div>
          <div class="azioni">
            ${bottone('testo-apri:' + t.id, '📖', 'Apri', 'btn-piccolo btn-primario')}
            ${bottone('testo-elimina:' + t.id, '🗑️', 'Elimina', 'btn-piccolo btn-errore')}
          </div>
        </li>`).join('')}</ul>` : schedaVuota('📚', 'Non hai ancora salvato nessun testo.')}
      <div class="barra-btn" style="margin-top:14px">${bottone('leggi-indietro', '⬅', 'Torna al testo')}</div>
    </div>`;
}


/* ============================================================
   05-scrivi.js
   ============================================================ */
/* ==================================================================
   05-scrivi.js — editor di scrittura, controllo del testo, modelli
   ================================================================== */

let _editor = { id: null, titolo: '', testo: '', undo: [], redo: [], ultimaVersione: 0 };

const CHECKLIST = [
  'Ho iniziato la frase con la maiuscola?',
  'Ho messo il punto?',
  'Ho controllato le doppie?',
  'Ho controllato gli apostrofi?',
  'Ho controllato gli accenti?',
  'Ho lasciato gli spazi corretti?',
  'Ho riletto lentamente?',
  'Ho ascoltato il testo con la voce?'
];

/* ------------------------------------------------------------------
   Vista principale
   ------------------------------------------------------------------ */

VISTE.scrivi = function (c, par) {
  if (par) apriDocumento(par, true);
  if (!_editor.id && Stato.documenti.length && !_editor.testo) {
    apriDocumento(Stato.documenti[0].id, true);
  }

  c.innerHTML = testaSezione('✍️', 'Scrivi', null,
    `<span class="salvato" aria-live="polite"></span>` + bottone('home', '🏠', 'Home', 'btn-piccolo')) + `

  <div class="card no-stampa">
    <div class="barra-btn" style="margin-bottom:8px">
      ${bottone('doc-nuovo', '📄', 'Nuovo', 'btn-primario')}
      ${bottone('doc-elenco', '📚', 'I miei documenti')}
      ${bottone('doc-salva', '💾', 'Salva')}
      ${bottone('doc-duplica', '⧉', 'Duplica')}
      ${bottone('doc-elimina', '🗑️', 'Elimina', 'btn-errore')}
    </div>
    <div class="barra-btn" style="margin-bottom:8px">
      ${bottone('doc-annulla', '↩️', 'Annulla')}
      ${bottone('doc-ripristina', '↪️', 'Ripristina')}
      ${bottone('doc-trova', '🔍', 'Trova parola')}
      ${bottone('doc-controlla', '🔎', 'Ricontrolla il testo', 'btn-attenzione')}
      ${bottone('doc-modelli', '🧩', 'Scrittura guidata')}
    </div>
    <div class="barra-btn" style="margin-bottom:0">
      ${bottone('doc-esporta', '📤', 'Esporta')}
      ${bottone('doc-importa', '📥', 'Importa')}
      ${bottone('doc-stampa', '🖨️', 'Stampa / PDF')}
      ${bottone('doc-versioni', '🕘', 'Versioni')}
      <button type="button" class="btn" data-az="doc-checklist" aria-pressed="${Stato.impostazioni.checklistVisibile}">
        <span aria-hidden="true">✅</span><span>Checklist</span></button>
    </div>
  </div>

  <div class="card">
    <label class="etichetta" for="docTitolo">Titolo</label>
    <input class="campo" id="docTitolo" type="text" value="${esc(_editor.titolo)}" placeholder="Per esempio: Riassunto di storia" spellcheck="true">
    <label class="etichetta" for="docTesto">Il mio testo</label>
    <textarea class="editor-area" id="docTesto" spellcheck="true" autocorrect="on" autocapitalize="sentences"
      placeholder="Scrivi qui. Non preoccuparti degli errori adesso: prima le idee, poi si controlla.">${esc(_editor.testo)}</textarea>
    <div class="contatori" id="contatori" style="margin-top:10px"></div>
  </div>

  <div id="barraVoceScrivi" class="no-stampa"></div>
  <div id="pannelloScrivi" class="no-stampa"></div>
  <div id="checklistBox" class="no-stampa"></div>`;

  const area = $('#docTesto', c);
  const tit = $('#docTitolo', c);

  aggiornaContatori();
  disegnaChecklist();

  const registraUndo = debounce(() => {
    if (_editor.undo[_editor.undo.length - 1] !== area.value) {
      _editor.undo.push(area.value);
      if (_editor.undo.length > 40) _editor.undo.shift();
      _editor.redo.length = 0;
    }
  }, 900);

  area.addEventListener('input', () => {
    _editor.testo = area.value;
    aggiornaContatori();
    registraUndo();
    salvaDocumentoAuto();
  });
  tit.addEventListener('input', () => { _editor.titolo = tit.value; salvaDocumentoAuto(); });

  const bv = $('#barraVoceScrivi', c);
  bv.appendChild(barraLettura(() => $('#docTesto').value));
};

function aggiornaContatori() {
  const n = $('#contatori');
  if (!n) return;
  const t = ($('#docTesto') ? $('#docTesto').value : _editor.testo) || '';
  const parole = dividiInParole(t).length;
  const caratteri = t.length;
  const frasi = dividiInFrasi(t).length;
  n.innerHTML = `<span>Parole: <b>${parole}</b></span>
     <span>Caratteri: <b>${caratteri}</b></span>
     <span>Frasi: <b>${frasi}</b></span>`;
}

/* ------------------------------------------------------------------
   Gestione documenti
   ------------------------------------------------------------------ */

function documentoCorrente() { return Stato.documenti.find((d) => d.id === _editor.id) || null; }

function apriDocumento(id, silenzioso) {
  const d = Stato.documenti.find((x) => x.id === id);
  if (!d) return false;
  _editor.id = d.id; _editor.titolo = d.titolo || ''; _editor.testo = d.testo || '';
  _editor.undo = [d.testo || '']; _editor.redo = [];
  if (!silenzioso) { vaiA('scrivi'); avvisoOk('Documento aperto'); }
  return true;
}

const salvaDocumentoAuto = debounce(function () {
  let d = documentoCorrente();
  if (!d) {
    if (!_editor.testo.trim() && !_editor.titolo.trim()) return;   // niente da salvare
    d = { id: uid('doc'), titolo: _editor.titolo || 'Senza titolo', testo: _editor.testo, creato: oraISO(), modificato: oraISO(), versioni: [] };
    Stato.documenti.unshift(d);
    _editor.id = d.id;
  }
  d.titolo = _editor.titolo || 'Senza titolo';
  // ogni tanto conservo una versione precedente (cronologia locale)
  const ora = Date.now();
  if (d.testo !== _editor.testo && ora - (_editor.ultimaVersione || 0) > 120000) {
    d.versioni = d.versioni || [];
    d.versioni.unshift({ quando: oraISO(), testo: d.testo });
    if (d.versioni.length > 8) d.versioni.length = 8;
    _editor.ultimaVersione = ora;
  }
  d.testo = _editor.testo;
  d.modificato = oraISO();
  salva();
}, 700);

async function nuovoDocumento() {
  await salvaDocumentoAuto.flush ? null : null;
  _editor = { id: null, titolo: '', testo: '', undo: [''], redo: [], ultimaVersione: 0 };
  disegna();
  const a = $('#docTitolo'); if (a) a.focus();
}

function elencoDocumenti() {
  const box = $('#pannelloScrivi');
  box.innerHTML = `<div class="card">
    <h2>📚 I miei documenti</h2>
    ${Stato.documenti.length ? `<ul class="lista">${Stato.documenti.map((d) => `
      <li class="voce">
        <div class="corpo"><b>${esc(d.titolo || 'Senza titolo')}</b>
        <span class="meta">${esc(quandoInParole(d.modificato))} · ${dividiInParole(d.testo).length} parole</span></div>
        <div class="azioni">
          ${bottone('doc-apri:' + d.id, '📖', 'Apri', 'btn-piccolo btn-primario')}
          ${bottone('doc-canc:' + d.id, '🗑️', 'Elimina', 'btn-piccolo btn-errore')}
        </div>
      </li>`).join('')}</ul>` : schedaVuota('📄', 'Non hai ancora nessun documento.', 'Scrivi qualcosa: si salva da solo.')}
    <div class="barra-btn" style="margin-top:12px">${bottone('pannello-chiudi', '✕', 'Chiudi')}</div>
  </div>`;
}

function mostraVersioni() {
  const d = documentoCorrente();
  const box = $('#pannelloScrivi');
  const v = (d && d.versioni) || [];
  box.innerHTML = `<div class="card">
    <h2>🕘 Versioni precedenti</h2>
    <p class="aiutino">Ogni tanto l'app mette da parte una copia del tuo testo. Restano solo su questo dispositivo.</p>
    ${v.length ? `<div class="versioni">${v.map((x, i) => `
      <button type="button" class="btn" data-az="ver-ripristina:${i}">
        <span aria-hidden="true">↩️</span><span>${esc(quandoInParole(x.quando))} — ${esc(accorcia(x.testo, 60))}</span>
      </button>`).join('')}</div>` : schedaVuota('🕘', 'Non ci sono ancora versioni precedenti.')}
    <div class="barra-btn" style="margin-top:12px">${bottone('pannello-chiudi', '✕', 'Chiudi')}</div>
  </div>`;
}

/* ------------------------------------------------------------------
   Checklist di autocorrezione
   ------------------------------------------------------------------ */

function disegnaChecklist() {
  const box = $('#checklistBox');
  if (!box) return;
  if (!Stato.impostazioni.checklistVisibile) { box.innerHTML = ''; return; }
  const d = documentoCorrente();
  const stato = (d && d.check) || {};
  box.innerHTML = `<div class="card">
    <h2>✅ Prima di consegnare</h2>
    ${CHECKLIST.map((t, i) => `<label class="check">
        <input type="checkbox" data-check="${i}"${stato[i] ? ' checked' : ''}>
        <span>${esc(t)}</span></label>`).join('')}
    <p class="aiutino">Non sei obbligato a spuntare tutto: servono solo a ricordarti cosa guardare.</p>
  </div>`;
  // uso onchange (non addEventListener) perché questa funzione può essere
  // richiamata più volte sullo stesso contenitore
  box.onchange = (e) => {
    const n = e.target.closest('[data-check]');
    if (!n) return;
    const d2 = documentoCorrente();
    if (!d2) return;
    d2.check = d2.check || {};
    d2.check[n.dataset.check] = n.checked;
    salva();
  };
}

/* ------------------------------------------------------------------
   "Ricontrolla il testo": controlli sicuri e deterministici.
   Non correggiamo mai da soli senza chiedere.
   ------------------------------------------------------------------ */

const SOSTITUZIONI_SICURE = [
  { cerca: /\bpò\b/g, con: "po'", perche: 'Si scrive "po\'" con l\'apostrofo (è accorciato da "poco").' },
  { cerca: /\bqual'è\b/gi, con: 'qual è', perche: '"Qual è" si scrive senza apostrofo.' },
  { cerca: /\bun'altro\b/gi, con: "un altro", perche: '"Un altro" (maschile) non vuole l\'apostrofo.' },
  { cerca: /\bpiù +ù\b/g, con: 'più', perche: 'Accento ripetuto.' },
  { cerca: /\bcosidetto\b/gi, con: 'cosiddetto', perche: 'Si scrive "cosiddetto" con due d.' },
  { cerca: /\bproprio +proprio\b/gi, con: 'proprio', perche: 'Parola ripetuta.' }
];

const ACCENTI_DA_GUARDARE = ['perche', 'poiche', 'giache', 'cioe', 'pero', 'piu', 'gia', 'cosi', 'puo', 'e\''];

function controllaTesto() {
  const t = ($('#docTesto') || {}).value || '';
  const box = $('#pannelloScrivi');
  const segnalazioni = [];

  // 1. spazi doppi
  const spazi = (t.match(/ {2,}/g) || []).length;
  if (spazi) segnalazioni.push({ ic: '␣', tit: spazi + (spazi === 1 ? ' spazio doppio' : ' spazi doppi'), det: 'Dove ci sono due spazi di fila ne basta uno.', fix: { cerca: / {2,}/g, con: ' ' } });

  // 2. spazio prima della punteggiatura
  const primaPunt = (t.match(/ +[,.;:!?]/g) || []).length;
  if (primaPunt) segnalazioni.push({ ic: '❕', tit: primaPunt + ' segno di punteggiatura con lo spazio prima', det: 'La virgola e il punto vanno attaccati alla parola.', fix: { cerca: / +([,.;:!?])/g, con: '$1' } });

  // 3. manca lo spazio dopo la punteggiatura
  const dopoPunt = (t.match(/[,;:](?=[A-Za-zÀ-ÿ])/g) || []).length;
  if (dopoPunt) segnalazioni.push({ ic: '␣', tit: dopoPunt + ' volte manca lo spazio dopo la virgola', det: 'Dopo la virgola ci vuole uno spazio.', fix: { cerca: /([,;:])(?=[A-Za-zÀ-ÿ])/g, con: '$1 ' } });

  // 4. maiuscola dopo il punto
  const senzaMaiuscola = [];
  const re = /([.!?])\s+([a-zà-ÿ])/g;
  let m;
  while ((m = re.exec(t))) senzaMaiuscola.push(m[2]);
  if (senzaMaiuscola.length) {
    segnalazioni.push({ ic: '🔠', tit: senzaMaiuscola.length + ' frasi iniziano con la minuscola', det: 'Dopo il punto la frase nuova comincia con la maiuscola.' });
  }

  // 5. parole ripetute
  const ripetute = [];
  const rr = /\b([A-Za-zÀ-ÿ']{3,})\s+\1\b/gi;
  while ((m = rr.exec(t))) ripetute.push(m[1]);
  if (ripetute.length) segnalazioni.push({ ic: '🔁', tit: 'Parole ripetute due volte: ' + ripetute.slice(0, 6).map((x) => '"' + x + '"').join(', '), det: 'Controlla se ne serve solo una.' });

  // 6. frasi molto lunghe
  const lunghe = dividiInFrasi(t).filter((f) => dividiInParole(f).length > 28);
  if (lunghe.length) segnalazioni.push({ ic: '📏', tit: lunghe.length + (lunghe.length === 1 ? ' frase molto lunga' : ' frasi molto lunghe'), det: 'Prova a spezzarla in due: si capisce meglio. Prima frase lunga: "' + accorcia(lunghe[0], 70) + '"' });

  // 7. punto finale
  if (t.trim() && !/[.!?…"»)]\s*$/.test(t.trim())) {
    segnalazioni.push({ ic: '⏹', tit: 'Manca il punto alla fine', det: 'L\'ultima frase non finisce con il punto.' });
  }

  // 8. accenti da guardare
  const trovatiAccenti = ACCENTI_DA_GUARDARE.filter((p) => new RegExp('\\b' + p.replace(/'/g, "'") + '\\b', 'i').test(t));
  if (trovatiAccenti.length) segnalazioni.push({ ic: '´', tit: 'Da controllare l\'accento: ' + trovatiAccenti.join(', '), det: 'Queste parole di solito vogliono l\'accento (perché, però, più, già, così, può).' });

  // 9. errori con correzione sicura
  SOSTITUZIONI_SICURE.forEach((s) => {
    const n = (t.match(s.cerca) || []).length;
    if (n) segnalazioni.push({ ic: '✏️', tit: n + ' volte: ' + s.con, det: s.perche, fix: { cerca: s.cerca, con: s.con } });
  });

  box.innerHTML = `<div class="card">
    <h2>🔎 Ho guardato il testo</h2>
    <p class="aiutino">Sono controlli meccanici: non capisco il significato delle frasi.
      Il correttore vero è quello del browser (le righe rosse sotto le parole). Non cambio niente senza il tuo permesso.</p>
    ${segnalazioni.length ? `<div class="controllo-risultato">${segnalazioni.map((s, i) => `
      <div class="controllo-riga">
        <b><span aria-hidden="true">${s.ic}</span> ${esc(s.tit)}</b>
        <div>${esc(s.det)}</div>
        ${s.fix ? `<div class="barra-btn" style="margin:8px 0 0">
          <button type="button" class="btn btn-piccolo" data-az="fix:${i}">✔️ Correggi tu queste</button></div>` : ''}
      </div>`).join('')}</div>`
      : `<div class="controllo-riga ok"><b>✅ Non ho trovato problemi meccanici.</b>
         <div>Adesso rileggi lentamente, o ascolta il testo con la voce: si sentono gli errori che l'occhio non vede.</div></div>`}
    <div class="barra-btn" style="margin-top:12px">
      ${bottone('doc-doppie', '🔍', 'Evidenzia le doppie')}
      ${bottone('pannello-chiudi', '✕', 'Chiudi')}
    </div>
  </div>`;
  box._fix = segnalazioni.map((s) => s.fix || null);
}

function applicaCorrezione(indice) {
  const box = $('#pannelloScrivi');
  const fix = box && box._fix ? box._fix[indice] : null;
  if (!fix) return;
  const area = $('#docTesto');
  area.value = area.value.replace(fix.cerca, fix.con);
  _editor.testo = area.value;
  aggiornaContatori();
  salvaDocumentoAuto();
  controllaTesto();
  avvisoOk('Corretto');
}

/** Mostra il testo con tutte le doppie evidenziate (aiuto visivo, non correzione). */
function evidenziaDoppie() {
  const t = ($('#docTesto') || {}).value || '';
  const html = esc(t).replace(/([bcdfglmnprstvz])\1/gi, '<mark>$1$1</mark>');
  $('#pannelloScrivi').innerHTML = `<div class="card">
    <h2>🔍 Le doppie del tuo testo</h2>
    <p class="aiutino">Qui vedi evidenziate tutte le doppie che hai scritto. Rileggile una per una: ci sono tutte quelle che servono? Ce n'è qualcuna di troppo?</p>
    <div class="foglio"><div class="foglio-testo" style="white-space:pre-wrap">${html || '<i>Il testo è vuoto.</i>'}</div></div>
    <div class="barra-btn" style="margin-top:12px">${bottone('pannello-chiudi', '✕', 'Chiudi')}</div>
  </div>`;
}

/* ------------------------------------------------------------------
   Scrittura guidata
   ------------------------------------------------------------------ */

const MODELLI_SCRITTURA = {
  testo: {
    tit: '📝 Racconto / testo',
    domande: ['Cosa devo raccontare?', 'Chi c\'è?', 'Dove succede?', 'Quando succede?', 'Cosa succede?', 'Come finisce?']
  },
  riassunto: {
    tit: '📋 Riassunto',
    domande: ['Qual è l\'argomento?', 'Chi sono i protagonisti?', 'Quali sono le informazioni più importanti?', 'Cosa posso eliminare?', 'Scrivi il riassunto.']
  },
  argomentativo: {
    tit: '⚖️ Testo argomentativo',
    domande: ['Argomento', 'La mia opinione', 'Motivo 1', 'Motivo 2', 'Esempio', 'Opinione contraria', 'Conclusione']
  }
};

function mostraModelli(quale) {
  const box = $('#pannelloScrivi');
  if (!quale) {
    box.innerHTML = `<div class="card">
      <h2>🧩 Scrittura guidata</h2>
      <p>Rispondi a una domanda alla volta. Alla fine metto insieme le risposte e tu le sistemi.</p>
      <div class="barra-btn">
        ${Object.keys(MODELLI_SCRITTURA).map((k) => bottone('modello:' + k, '', MODELLI_SCRITTURA[k].tit, 'btn-grande')).join('')}
      </div>
      <div class="barra-btn">${bottone('pannello-chiudi', '✕', 'Chiudi')}</div>
    </div>`;
    return;
  }
  const m = MODELLI_SCRITTURA[quale];
  box.innerHTML = `<div class="card">
    <h2>${esc(m.tit)}</h2>
    ${m.domande.map((d, i) => `<div class="guida-passo">
        <div class="domanda"><span aria-hidden="true">${i + 1}.</span> ${esc(d)}</div>
        <textarea class="area" rows="2" data-guida="${i}" spellcheck="true"></textarea>
      </div>`).join('')}
    <div class="barra-btn">
      ${bottone('modello-componi:' + quale, '⬇️', 'Metti tutto nel testo', 'btn-primario btn-grande')}
      ${bottone('pannello-chiudi', '✕', 'Chiudi')}
    </div>
  </div>`;
}

function componiModello(quale) {
  const m = MODELLI_SCRITTURA[quale];
  const risposte = $$('[data-guida]').map((n) => n.value.trim());
  const pezzi = [];
  m.domande.forEach((d, i) => { if (risposte[i]) pezzi.push(risposte[i]); });
  if (!pezzi.length) { toast('Rispondi almeno a una domanda.'); return; }
  const area = $('#docTesto');
  const nuovo = (area.value ? area.value + '\n\n' : '') + pezzi.join('\n\n');
  area.value = nuovo;
  _editor.testo = nuovo;
  aggiornaContatori();
  salvaDocumentoAuto();
  $('#pannelloScrivi').innerHTML = '';
  area.focus();
  avvisoOk('Ho messo le tue risposte nel testo');
}

/* ------------------------------------------------------------------
   Trova / esporta / stampa
   ------------------------------------------------------------------ */

async function trovaParola() {
  const q = await chiediTesto('Trova una parola', 'Che parola cerco?', '');
  if (!q) return;
  const area = $('#docTesto');
  const t = area.value.toLowerCase();
  const parola = q.toLowerCase();
  const quante = t.split(parola).length - 1;
  if (!quante) { toast('Non ho trovato "' + q + '" nel testo.'); return; }
  const da = t.indexOf(parola);
  area.focus();
  area.setSelectionRange(da, da + parola.length);
  toast('Trovata ' + quante + (quante === 1 ? ' volta' : ' volte'));
}

async function esportaDocumento() {
  const r = await finestra({
    titolo: 'Esporta il documento',
    campi: [{ nome: 'formato', etichetta: 'In che formato?', tipo: 'scelta', valore: 'txt',
      opzioni: [{ v: 'txt', t: 'Testo semplice (.txt)' }, { v: 'html', t: 'Pagina web (.html)' }, { v: 'json', t: 'Dati (.json)' }] }],
    testoOk: 'Esporta'
  });
  if (!r) return;
  const titolo = _editor.titolo || 'documento';
  const nomeBase = titolo.replace(/[^\w\sÀ-ÿ-]/g, '').trim().replace(/\s+/g, '-').toLowerCase() || 'documento';
  if (r.formato === 'txt') {
    scaricaFile(nomeBase + '.txt', titolo + '\n\n' + _editor.testo, 'text/plain;charset=utf-8');
  } else if (r.formato === 'html') {
    const html = `<!DOCTYPE html><html lang="it"><head><meta charset="utf-8">
<title>${esc(titolo)}</title>
<style>body{font-family:system-ui,sans-serif;font-size:18px;line-height:1.8;max-width:40rem;margin:40px auto;padding:0 16px}</style>
</head><body><h1>${esc(titolo)}</h1>${_editor.testo.split(/\n{2,}/).map((p) => '<p>' + esc(p).replace(/\n/g, '<br>') + '</p>').join('\n')}</body></html>`;
    scaricaFile(nomeBase + '.html', html, 'text/html;charset=utf-8');
  } else {
    scaricaFile(nomeBase + '.json', JSON.stringify({ titolo, testo: _editor.testo, data: oraISO() }, null, 2), 'application/json');
  }
  avvisoOk('File pronto');
}

async function importaDocumento() {
  const f = await scegliFile('.txt,.md,.html,.json,text/plain');
  if (!f) return;
  try {
    let t = await leggiFileTesto(f);
    if (/\.json$/i.test(f.name)) {
      try { const j = JSON.parse(t); t = j.testo || t; } catch (e) { /* lo tratto come testo */ }
    } else if (/\.html?$/i.test(f.name)) {
      const d = document.createElement('div');
      d.innerHTML = t;
      t = d.textContent || '';         // solo testo: niente HTML dentro l'editor
    }
    const area = $('#docTesto');
    area.value = t;
    _editor.testo = t;
    if (!_editor.titolo) { _editor.titolo = f.name.replace(/\.[^.]+$/, ''); $('#docTitolo').value = _editor.titolo; }
    aggiornaContatori();
    salvaDocumentoAuto();
    avvisoOk('File importato');
  } catch (e) {
    avvisoErrore('Non sono riuscito ad aprire questo file.');
  }
}


/* ============================================================
   06-mate-motore.js
   ============================================================ */
/* ==================================================================
   06-mate-motore.js — motore matematico locale

   Scritto apposta per questa applicazione: nessuna libreria esterna,
   nessuna connessione. Lavora in modo ESATTO con le frazioni
   (niente errori di arrotondamento tipo 0.30000000000000004).

   Cosa sa fare davvero:
   - leggere un'espressione scritta come sul quaderno;
   - lavorare con polinomi anche a più lettere e con frazioni algebriche;
   - risolvere equazioni di 1° e 2° grado, fratte e alcune di grado
     superiore (quando si scompongono con radici razionali);
   - disequazioni di 1° e 2° grado;
   - sistemi lineari di due equazioni;
   - derivate (regole scolastiche complete);
   - limiti (sostituzione + cancellazione di fattori comuni, oppure
     stima numerica dichiarata come tale);
   - integrali immediati elementari.

   Cosa NON sa fare (e lo dice): trigonometria simbolica avanzata,
   integrali per parti/sostituzione, equazioni trascendenti generiche.
   ================================================================== */

const M = (function () {

  /* ================================================================
     1. FRAZIONI ESATTE
     ================================================================ */

  function mcd(a, b) { a = Math.abs(a); b = Math.abs(b); while (b) { const t = a % b; a = b; b = t; } return a || 1; }

  function fr(n, d) {
    if (d === undefined) d = 1;
    if (!isFinite(n) || !isFinite(d) || d === 0) throw errore('Qui c\'è una divisione per zero.');
    if (!Number.isInteger(n) || !Number.isInteger(d)) {
      // trasformo un decimale in frazione esatta (0,25 → 1/4)
      const p = Math.max(decimali(n), decimali(d));
      const k = Math.pow(10, p);
      n = Math.round(n * k); d = Math.round(d * k);
    }
    if (d < 0) { n = -n; d = -d; }
    const g = mcd(n, d);
    return { n: n / g, d: d / g };
  }
  function decimali(x) {
    const s = String(x);
    const i = s.indexOf('.');
    return i < 0 ? 0 : Math.min(9, s.length - i - 1);
  }
  const F0 = fr(0), F1 = fr(1);
  const fAdd = (a, b) => fr(a.n * b.d + b.n * a.d, a.d * b.d);
  const fSub = (a, b) => fr(a.n * b.d - b.n * a.d, a.d * b.d);
  const fMul = (a, b) => fr(a.n * b.n, a.d * b.d);
  const fDiv = (a, b) => { if (b.n === 0) throw errore('Non si può dividere per zero.'); return fr(a.n * b.d, a.d * b.n); };
  const fNeg = (a) => ({ n: -a.n, d: a.d });
  const fZero = (a) => a.n === 0;
  const fUno = (a) => a.n === 1 && a.d === 1;
  const fInt = (a) => a.d === 1;
  const fNum = (a) => a.n / a.d;
  const fCmp = (a, b) => (a.n * b.d - b.n * a.d);
  function fPow(a, k) {
    if (k === 0) return F1;
    if (k < 0) return fDiv(F1, fPow(a, -k));
    let r = F1;
    for (let i = 0; i < k; i++) r = fMul(r, a);
    return r;
  }
  /** "3/4" oppure "5" oppure "-1,5" → testo leggibile. */
  function fTesto(a) { return a.d === 1 ? String(a.n) : a.n + '/' + a.d; }
  function fDecimale(a, cifre) {
    const v = fNum(a);
    if (fInt(a)) return String(a.n);
    const s = v.toFixed(cifre === undefined ? 4 : cifre).replace(/0+$/, '').replace(/\.$/, '');
    return s.replace('.', ',');
  }

  function errore(msg) { const e = new Error(msg); e.amichevole = true; return e; }

  /* ================================================================
     2. LETTURA DELL'ESPRESSIONE (tokenizer + parser)
     ================================================================ */

  const FUNZIONI = ['sqrt', 'radq', 'sin', 'cos', 'tan', 'asin', 'acos', 'atan', 'sen',
    'log', 'ln', 'log10', 'log2', 'abs', 'exp'];

  function normalizzaTesto(s) {
    return String(s || '')
      .replace(/ /g, ' ')
      .replace(/[−–—]/g, '-')
      .replace(/[×·∙]/g, '*')
      .replace(/[÷:]/g, '/')
      .replace(/√/g, 'sqrt')
      .replace(/π/g, 'pi')
      .replace(/²/g, '^2').replace(/³/g, '^3')
      .replace(/⁰/g, '^0').replace(/¹/g, '^1')
      .replace(/⁴/g, '^4').replace(/⁵/g, '^5').replace(/⁶/g, '^6')
      .replace(/⁷/g, '^7').replace(/⁸/g, '^8').replace(/⁹/g, '^9')
      .replace(/≤/g, '<=').replace(/≥/g, '>=').replace(/≠/g, '!=')
      .replace(/,(?=\d)/g, '.')                 // 0,5 → 0.5
      .replace(/\bsen\b/g, 'sin')
      .replace(/\bradq\b/g, 'sqrt')
      .trim();
  }

  function tokenizza(s) {
    const t = [];
    let i = 0;
    while (i < s.length) {
      const c = s[i];
      if (/\s/.test(c)) { i++; continue; }
      if (/[0-9.]/.test(c)) {
        let j = i;
        while (j < s.length && /[0-9.]/.test(s[j])) j++;
        const num = s.slice(i, j);
        if ((num.match(/\./g) || []).length > 1) throw errore('Ho trovato un numero scritto male: "' + num + '".');
        t.push({ t: 'num', v: Number(num) });
        i = j; continue;
      }
      if (/[A-Za-z_]/.test(c)) {
        let j = i;
        while (j < s.length && /[A-Za-z_0-9]/.test(s[j])) j++;
        let nome = s.slice(i, j);
        // "2xy" → x per y: separo le lettere se non è una funzione conosciuta
        if (FUNZIONI.indexOf(nome.toLowerCase()) >= 0 || nome === 'pi') {
          t.push({ t: nome === 'pi' ? 'cost' : 'fn', v: nome.toLowerCase() });
        } else {
          for (let k = 0; k < nome.length; k++) t.push({ t: 'var', v: nome[k] });
        }
        i = j; continue;
      }
      if ('+-*/^()!'.indexOf(c) >= 0) { t.push({ t: c }); i++; continue; }
      if (c === '=' || c === '<' || c === '>') {
        if (s[i + 1] === '=') { t.push({ t: c + '=' }); i += 2; } else { t.push({ t: c }); i++; }
        continue;
      }
      throw errore('Non capisco il simbolo "' + c + '".');
    }
    return t;
  }

  /** Analizza il testo e costruisce l'albero dell'espressione. */
  function analizza(testo) {
    const tok = tokenizza(normalizzaTesto(testo));
    let p = 0;
    const guarda = () => tok[p];
    const prendi = () => tok[p++];

    function espressione() {
      let a = termine();
      while (guarda() && (guarda().t === '+' || guarda().t === '-')) {
        const op = prendi().t;
        const b = termine();
        a = op === '+' ? nSomma([a, b]) : nSomma([a, nProd([nNum(fr(-1)), b])]);
      }
      return a;
    }
    function termine() {
      let a = unario();
      for (;;) {
        const g = guarda();
        if (!g) break;
        if (g.t === '*') { prendi(); a = nProd([a, unario()]); continue; }
        if (g.t === '/') { prendi(); a = nDiv(a, unario()); continue; }
        // moltiplicazione implicita: 2x, 3(x+1), x y, 2sin(x)
        if (g.t === 'num' || g.t === 'var' || g.t === '(' || g.t === 'fn' || g.t === 'cost') {
          a = nProd([a, unario()]); continue;
        }
        break;
      }
      return a;
    }
    function unario() {
      const g = guarda();
      if (!g) throw errore('L\'espressione finisce troppo presto.');
      if (g.t === '-') { prendi(); return nProd([nNum(fr(-1)), unario()]); }
      if (g.t === '+') { prendi(); return unario(); }
      return potenza();
    }
    function potenza() {
      let base = atomo();
      const g = guarda();
      if (g && g.t === '!') { prendi(); base = { t: 'f', n: 'fatt', a: [base] }; }
      const g2 = guarda();
      if (g2 && g2.t === '^') {
        prendi();
        const esp = unario();          // l'esponente si legge da destra: 2^3^2
        return nPot(base, esp);
      }
      return base;
    }
    function atomo() {
      const g = prendi();
      if (!g) throw errore('Manca qualcosa alla fine dell\'espressione.');
      if (g.t === 'num') return nNum(fr(g.v));
      if (g.t === 'var') return { t: 'v', n: g.v };
      if (g.t === 'cost') return { t: 'c', n: 'pi' };
      if (g.t === 'fn') {
        let arg;
        if (guarda() && guarda().t === '(') { prendi(); arg = espressione(); attendi(')'); }
        else arg = potenza();
        return { t: 'f', n: g.v, a: [arg] };
      }
      if (g.t === '(') { const e = espressione(); attendi(')'); return e; }
      throw errore('Non riesco a leggere questa parte dell\'espressione.');
    }
    function attendi(simbolo) {
      const g = prendi();
      if (!g || g.t !== simbolo) throw errore('Manca una parentesi: controlla che ogni "(" abbia la sua ")".');
    }

    const out = espressione();
    if (p < tok.length) throw errore('C\'è qualcosa di troppo nell\'espressione. Controlla i simboli.');
    return out;
  }

  /* ================================================================
     3. COSTRUTTORI DI NODI
     ================================================================ */

  const nNum = (f) => ({ t: 'n', v: f });
  const nVar = (n) => ({ t: 'v', n });
  /* Le somme e i prodotti vengono "appiattiti": così l'espressione viene
     scritta senza parentesi inutili (3x + 4y − 7 e non ((3x + 4y) − 7)). */
  function nSomma(a) {
    const out = [];
    a.forEach((x) => { if (x && x.t === '+') out.push.apply(out, x.a); else out.push(x); });
    return { t: '+', a: out };
  }
  function nProd(a) {
    const out = [];
    a.forEach((x) => { if (x && x.t === '*') out.push.apply(out, x.a); else out.push(x); });
    return { t: '*', a: out };
  }
  function nPot(b, e) { return { t: '^', b, e }; }
  function nDiv(a, b) { return nProd([a, nPot(b, nNum(fr(-1)))]); }

  const eNum = (x) => x.t === 'n';
  const eZero = (x) => x.t === 'n' && fZero(x.v);
  const eUno = (x) => x.t === 'n' && fUno(x.v);

  /* ================================================================
     4. POLINOMI A PIÙ LETTERE  (cuore del motore)
        Un polinomio è una mappa: monomio → coefficiente frazionario.
        Il monomio è scritto come {x:2, y:1} (cioè x²y).
     ================================================================ */

  function chiaveMon(m) {
    const k = Object.keys(m).filter((v) => m[v] !== 0).sort();
    return k.map((v) => v + '^' + m[v]).join('*');
  }
  function monDaChiave(k) {
    const m = {};
    if (!k) return m;
    k.split('*').forEach((p) => { const [v, e] = p.split('^'); m[v] = Number(e); });
    return m;
  }

  function pol() { return {}; }                      // polinomio vuoto = 0
  function polCost(f) { const p = {}; if (!fZero(f)) p[''] = f; return p; }
  function polVar(v) { const p = {}; p[v + '^1'] = F1; return p; }

  function polSomma(A, B) {
    const R = {};
    Object.keys(A).forEach((k) => { R[k] = A[k]; });
    Object.keys(B).forEach((k) => {
      R[k] = R[k] ? fAdd(R[k], B[k]) : B[k];
      if (fZero(R[k])) delete R[k];
    });
    return R;
  }
  function polScala(A, f) {
    const R = {};
    if (fZero(f)) return R;
    Object.keys(A).forEach((k) => { R[k] = fMul(A[k], f); });
    return R;
  }
  function polSottrai(A, B) { return polSomma(A, polScala(B, fr(-1))); }
  function polProdotto(A, B) {
    const R = {};
    Object.keys(A).forEach((ka) => {
      const ma = monDaChiave(ka);
      Object.keys(B).forEach((kb) => {
        const mb = monDaChiave(kb);
        const m = Object.assign({}, ma);
        Object.keys(mb).forEach((v) => { m[v] = (m[v] || 0) + mb[v]; });
        const k = chiaveMon(m);
        const c = fMul(A[ka], B[kb]);
        R[k] = R[k] ? fAdd(R[k], c) : c;
        if (fZero(R[k])) delete R[k];
      });
    });
    return R;
  }
  function polPotenza(A, k) {
    let r = polCost(F1);
    for (let i = 0; i < k; i++) r = polProdotto(r, A);
    return r;
  }
  function polZero(A) { return Object.keys(A).length === 0; }
  function polCostante(A) { return Object.keys(A).every((k) => k === ''); }
  function polValoreCost(A) { return A[''] || F0; }
  function polVariabili(A) {
    const s = {};
    Object.keys(A).forEach((k) => Object.keys(monDaChiave(k)).forEach((v) => { s[v] = 1; }));
    return Object.keys(s).sort();
  }
  function polGrado(A, v) {
    let g = 0;
    Object.keys(A).forEach((k) => {
      const m = monDaChiave(k);
      if (v) g = Math.max(g, m[v] || 0);
      else g = Math.max(g, Object.keys(m).reduce((s, x) => s + m[x], 0));
    });
    return g;
  }
  /** Coefficienti rispetto a una lettera: [c0, c1, c2, ...] (ognuno è un polinomio). */
  function polCoeff(A, v) {
    const g = polGrado(A, v);
    const out = [];
    for (let i = 0; i <= g; i++) out.push(pol());
    Object.keys(A).forEach((k) => {
      const m = monDaChiave(k);
      const e = m[v] || 0;
      delete m[v];
      const k2 = chiaveMon(m);
      const R = out[e];
      R[k2] = R[k2] ? fAdd(R[k2], A[k]) : A[k];
      if (fZero(R[k2])) delete R[k2];
    });
    return out;
  }
  /** Coefficienti numerici (solo se il polinomio ha una sola lettera). */
  function polCoeffNum(A, v) {
    const c = polCoeff(A, v);
    const out = [];
    for (let i = 0; i < c.length; i++) {
      if (!polCostante(c[i])) return null;
      out.push(polValoreCost(c[i]));
    }
    return out;
  }

  /** Espressione → polinomio, oppure null se non è un polinomio. */
  function polDaAst(a) {
    if (!a) return null;
    if (a.t === 'n') return polCost(a.v);
    if (a.t === 'v') return polVar(a.n);
    if (a.t === 'c') return null;                    // π non è razionale
    if (a.t === '+') {
      let r = pol();
      for (const x of a.a) { const p = polDaAst(x); if (!p) return null; r = polSomma(r, p); }
      return r;
    }
    if (a.t === '*') {
      let r = polCost(F1);
      for (const x of a.a) { const p = polDaAst(x); if (!p) return null; r = polProdotto(r, p); }
      return r;
    }
    if (a.t === '^') {
      const e = a.e;
      if (e.t !== 'n' || !fInt(e.v)) return null;
      const k = e.v.n;
      const b = polDaAst(a.b);
      if (!b) return null;
      if (k >= 0) return polPotenza(b, k);
      // potenza negativa: solo se la base è una costante
      if (polCostante(b)) { const c = polValoreCost(b); if (fZero(c)) return null; return polCost(fPow(c, k)); }
      return null;
    }
    return null;
  }

  /** Polinomio → espressione ordinata (grado decrescente). */
  function polAAst(A, ordine) {
    const chiavi = Object.keys(A);
    if (!chiavi.length) return nNum(F0);
    const v = ordine || polVariabili(A);
    chiavi.sort((a, b) => {
      const ma = monDaChiave(a), mb = monDaChiave(b);
      const ga = Object.keys(ma).reduce((s, x) => s + ma[x], 0);
      const gb = Object.keys(mb).reduce((s, x) => s + mb[x], 0);
      if (ga !== gb) return gb - ga;
      for (const x of v) { if ((mb[x] || 0) !== (ma[x] || 0)) return (mb[x] || 0) - (ma[x] || 0); }
      return a < b ? -1 : 1;
    });
    const termini = chiavi.map((k) => {
      const m = monDaChiave(k);
      const c = A[k];
      const fattori = [];
      if (!fUno(c) || !Object.keys(m).length) fattori.push(nNum(c));
      Object.keys(m).sort().forEach((x) => {
        fattori.push(m[x] === 1 ? nVar(x) : nPot(nVar(x), nNum(fr(m[x]))));
      });
      return fattori.length === 1 ? fattori[0] : nProd(fattori);
    });
    return termini.length === 1 ? termini[0] : nSomma(termini);
  }

  /* ---- divisione fra polinomi di UNA lettera ---------------------- */
  /** [c0,c1,...] / [d0,d1,...] → {q, r} */
  function divisioneNum(a, b) {
    a = a.slice();
    const q = new Array(Math.max(0, a.length - b.length + 1)).fill(F0);
    const gb = b.length - 1;
    if (fZero(b[gb])) return null;
    for (let i = a.length - 1; i >= gb; i--) {
      if (fZero(a[i])) continue;
      const c = fDiv(a[i], b[gb]);
      q[i - gb] = c;
      for (let j = 0; j <= gb; j++) a[i - gb + j] = fSub(a[i - gb + j], fMul(c, b[j]));
    }
    while (a.length > 1 && fZero(a[a.length - 1])) a.pop();
    return { q, r: a };
  }

  /* ================================================================
     5. FUNZIONI RAZIONALI (frazioni algebriche)
     ================================================================ */

  function rat(n, d) { return { n, d: d || polCost(F1) }; }
  function ratDaAst(a) {
    if (!a) return null;
    if (a.t === 'n') return rat(polCost(a.v));
    if (a.t === 'v') return rat(polVar(a.n));
    if (a.t === '+') {
      let r = rat(pol());
      for (const x of a.a) {
        const p = ratDaAst(x); if (!p) return null;
        r = rat(polSomma(polProdotto(r.n, p.d), polProdotto(p.n, r.d)), polProdotto(r.d, p.d));
      }
      return r;
    }
    if (a.t === '*') {
      let r = rat(polCost(F1));
      for (const x of a.a) {
        const p = ratDaAst(x); if (!p) return null;
        r = rat(polProdotto(r.n, p.n), polProdotto(r.d, p.d));
      }
      return r;
    }
    if (a.t === '^') {
      if (a.e.t !== 'n' || !fInt(a.e.v)) return null;
      const k = a.e.v.n;
      const b = ratDaAst(a.b); if (!b) return null;
      if (k >= 0) return rat(polPotenza(b.n, k), polPotenza(b.d, k));
      return rat(polPotenza(b.d, -k), polPotenza(b.n, -k));
    }
    return null;
  }

  /* ================================================================
     6. VALUTAZIONE NUMERICA
     ================================================================ */

  function valuta(a, scope) {
    scope = scope || {};
    switch (a.t) {
      case 'n': return fNum(a.v);
      case 'c': return Math.PI;
      case 'v': {
        if (a.n in scope) return scope[a.n];
        if (a.n === 'e') return Math.E;
        throw errore('Non so quanto vale "' + a.n + '".');
      }
      case '+': return a.a.reduce((s, x) => s + valuta(x, scope), 0);
      case '*': return a.a.reduce((s, x) => s * valuta(x, scope), 1);
      case '^': return Math.pow(valuta(a.b, scope), valuta(a.e, scope));
      case 'f': {
        const x = valuta(a.a[0], scope);
        switch (a.n) {
          case 'sqrt': if (x < 0) throw errore('Non esiste la radice quadrata di un numero negativo.'); return Math.sqrt(x);
          case 'sin': return Math.sin(x);
          case 'cos': return Math.cos(x);
          case 'tan': return Math.tan(x);
          case 'asin': return Math.asin(x);
          case 'acos': return Math.acos(x);
          case 'atan': return Math.atan(x);
          case 'ln': return Math.log(x);
          case 'log': case 'log10': return Math.log10(x);
          case 'log2': return Math.log2(x);
          case 'abs': return Math.abs(x);
          case 'exp': return Math.exp(x);
          case 'fatt': {
            if (x < 0 || !Number.isInteger(x)) throw errore('Il fattoriale si può fare solo con numeri interi maggiori o uguali a zero.');
            if (x > 170) throw errore('Questo fattoriale è un numero troppo grande.');
            let r = 1; for (let i = 2; i <= x; i++) r *= i; return r;
          }
        }
        throw errore('Non conosco la funzione "' + a.n + '".');
      }
    }
    throw errore('Espressione non valida.');
  }

  /* ================================================================
     7. SEMPLIFICAZIONE
     ================================================================ */

  function semplifica(a) {
    const r = ratDaAst(a);
    if (r) {
      if (polZero(r.n)) return nNum(F0);
      if (polCostante(r.d)) {
        const c = polValoreCost(r.d);
        if (!fZero(c)) return polAAst(polScala(r.n, fDiv(F1, c)));
      }
      // provo a semplificare numeratore e denominatore con una lettera sola
      const vs = polVariabili(polProdotto(r.n, r.d));
      if (vs.length === 1) {
        const cn = polCoeffNum(r.n, vs[0]), cd = polCoeffNum(r.d, vs[0]);
        if (cn && cd) {
          const s = semplificaFrazionePoli(cn, cd, vs[0]);
          if (s) return s;
        }
      }
      return nDiv(polAAst(r.n), polAAst(r.d));
    }
    return semplificaGenerico(a);
  }

  function semplificaFrazionePoli(cn, cd, v) {
    // cerco radici comuni razionali e divido
    let num = cn.slice(), den = cd.slice();
    let cambiato = false;
    for (let giro = 0; giro < 6; giro++) {
      const radici = radiciRazionali(num);
      let fatto = false;
      for (const rr of radici) {
        const div = [fNeg(rr), F1];
        const a = divisioneNum(num, div), b = divisioneNum(den, div);
        if (a && b && gradoZero(a.r) && gradoZero(b.r)) {
          num = a.q; den = b.q; cambiato = true; fatto = true; break;
        }
      }
      if (!fatto) break;
    }
    if (!cambiato) return null;
    const N = daCoeff(num, v), D = daCoeff(den, v);
    if (polCostante(D)) {
      const c = polValoreCost(D);
      if (!fZero(c)) return polAAst(polScala(N, fDiv(F1, c)));
    }
    return nDiv(polAAst(N), polAAst(D));
  }
  function gradoZero(r) { return r.every((x) => fZero(x)); }
  function daCoeff(c, v) {
    let P = pol();
    c.forEach((f, i) => {
      if (fZero(f)) return;
      const m = {}; if (i) m[v] = i;
      P[chiaveMon(m)] = f;
    });
    return P;
  }

  /** Semplificazione generale, per espressioni con seno, logaritmi, radici... */
  function semplificaGenerico(a) {
    if (!a || a.t === 'n' || a.t === 'v' || a.t === 'c') return a;
    if (a.t === 'f') {
      const arg = semplifica(a.a[0]);
      if (arg.t === 'n') {
        try {
          const v = valuta({ t: 'f', n: a.n, a: [arg] });
          if (Number.isInteger(v * 1e6) && Math.abs(v) < 1e9) {
            const f = fr(Math.round(v * 1e6), 1e6);
            if (Math.abs(fNum(f) - v) < 1e-12) return nNum(f);
          }
        } catch (e) { /* lo lascio simbolico */ }
      }
      return { t: 'f', n: a.n, a: [arg] };
    }
    if (a.t === '^') {
      const b = semplifica(a.b), e = semplifica(a.e);
      if (eZero(e)) return nNum(F1);
      if (eUno(e)) return b;
      if (eUno(b)) return nNum(F1);
      if (b.t === 'n' && e.t === 'n' && fInt(e.v) && Math.abs(e.v.n) < 40) return nNum(fPow(b.v, e.v.n));
      return nPot(b, e);
    }
    if (a.t === '+') {
      const parti = [];
      let cost = F0;
      const mappa = {};                       // termine simile → coefficiente
      const appiattisci = (x) => { if (x.t === '+') x.a.forEach(appiattisci); else parti.push(semplifica(x)); };
      a.a.forEach(appiattisci);
      parti.forEach((x) => {
        if (x.t === 'n') { cost = fAdd(cost, x.v); return; }
        const { coef, resto } = staccaCoefficiente(x);
        const k = testo(resto);
        if (mappa[k]) mappa[k].c = fAdd(mappa[k].c, coef);
        else mappa[k] = { c: coef, nodo: resto };
      });
      const out = [];
      Object.keys(mappa).forEach((k) => {
        const { c, nodo } = mappa[k];
        if (fZero(c)) return;
        out.push(fUno(c) ? nodo : nProd([nNum(c), nodo]));
      });
      if (!fZero(cost) || !out.length) out.push(nNum(cost));
      return out.length === 1 ? out[0] : nSomma(out);
    }
    if (a.t === '*') {
      const parti = [];
      let cost = F1;
      const appiattisci = (x) => { if (x.t === '*') x.a.forEach(appiattisci); else parti.push(semplifica(x)); };
      a.a.forEach(appiattisci);
      const altri = [];
      for (const x of parti) {
        if (x.t === 'n') { cost = fMul(cost, x.v); continue; }
        altri.push(x);
      }
      if (fZero(cost)) return nNum(F0);
      // raggruppo le potenze uguali:  x * x → x^2
      const basi = [];
      altri.forEach((x) => {
        const b = x.t === '^' ? x.b : x;
        const e = x.t === '^' ? x.e : nNum(F1);
        const k = testo(b);
        const g = basi.find((y) => y.k === k);
        if (g && g.e.t === 'n' && e.t === 'n') g.e = nNum(fAdd(g.e.v, e.v));
        else basi.push({ k, b, e });
      });
      const fattori = basi.map((g) => (eUno(g.e) ? g.b : nPot(g.b, g.e))).filter((x) => !eUno(x));
      if (!fUno(cost) || !fattori.length) fattori.unshift(nNum(cost));
      return fattori.length === 1 ? fattori[0] : nProd(fattori);
    }
    return a;
  }

  function staccaCoefficiente(x) {
    if (x.t === '*') {
      let c = F1; const resto = [];
      x.a.forEach((y) => { if (y.t === 'n') c = fMul(c, y.v); else resto.push(y); });
      if (!resto.length) return { coef: c, resto: nNum(F1) };
      return { coef: c, resto: resto.length === 1 ? resto[0] : nProd(resto) };
    }
    return { coef: F1, resto: x };
  }

  /* ================================================================
     8. SCRITTURA: testo, HTML "come sul quaderno", lettura ad alta voce
     ================================================================ */

  const PREC = { '+': 1, '*': 2, '^': 3 };

  function testo(a) {
    if (!a) return '';
    switch (a.t) {
      case 'n': return fTesto(a.v);
      case 'v': return a.n;
      case 'c': return 'π';
      case '+': return a.a.map((x, i) => {
        const s = testo(x);
        if (i === 0) return s;
        return s.startsWith('-') ? ' - ' + s.slice(1) : ' + ' + s;
      }).join('');
      case '*': return a.a.map((x) => (x.t === '+' ? '(' + testo(x) + ')' : testo(x))).join('·');
      case '^': return (PREC[a.b.t] ? '(' + testo(a.b) + ')' : testo(a.b)) + '^' + (PREC[a.e.t] ? '(' + testo(a.e) + ')' : testo(a.e));
      case 'f': return (a.n === 'fatt' ? testo(a.a[0]) + '!' : a.n + '(' + testo(a.a[0]) + ')');
    }
    return '?';
  }

  /** HTML con frazioni impilate, esponenti veri e radici con il tetto. */
  function html(a, dentro) {
    if (!a) return '';
    switch (a.t) {
      case 'n': {
        if (a.v.d === 1) return String(a.v.n).replace('-', '−');
        const seg = a.v.n < 0 ? '−' : '';
        return seg + frazioneHtml(String(Math.abs(a.v.n)), String(a.v.d));
      }
      case 'v': return '<i>' + esc(a.n) + '</i>';
      case 'c': return 'π';
      case '+': {
        const s = a.a.map((x, i) => {
          const neg = eNegativo(x);
          const corpo = html(neg ? cambiaSegno(x) : x, true);
          if (i === 0) return (neg ? '<span class="operatore">−</span>' : '') + corpo;
          return '<span class="operatore">' + (neg ? '−' : '+') + '</span>' + corpo;
        }).join('');
        return dentro ? '(' + s + ')' : s;
      }
      case '*': {
        // se è una divisione la scrivo come frazione
        const den = [], num = [];
        a.a.forEach((x) => {
          if (x.t === '^' && x.e.t === 'n' && x.e.v.n < 0 && x.e.v.d === 1) {
            den.push(x.e.v.n === -1 ? x.b : nPot(x.b, nNum(fr(-x.e.v.n))));
          } else num.push(x);
        });
        if (den.length) {
          const n = num.length ? (num.length === 1 ? html(num[0]) : num.map((x) => html(x, true)).join('')) : '1';
          const d = den.length === 1 ? html(den[0]) : den.map((x) => html(x, true)).join('');
          return frazioneHtml(n, d);
        }
        return a.a.map((x, i) => {
          const s = html(x, x.t === '+');
          if (i > 0 && (x.t === 'n' || (x.t === '^' && x.b.t === 'n'))) return '<span class="operatore">·</span>' + s;
          return s;
        }).join('');
      }
      case '^': {
        if (a.e.t === 'n' && a.e.v.d === 2 && a.e.v.n === 1) return radiceHtml(html(a.b));
        const b = (a.b.t === '+' || a.b.t === '*' || a.b.t === '^' || eNegativo(a.b)) ? '(' + html(a.b) + ')' : html(a.b);
        return b + '<sup>' + html(a.e) + '</sup>';
      }
      case 'f': {
        if (a.n === 'sqrt') return radiceHtml(html(a.a[0]));
        if (a.n === 'fatt') return html(a.a[0], true) + '!';
        const nome = { ln: 'ln', log: 'log', log10: 'log', log2: 'log<sub>2</sub>' }[a.n] || a.n;
        return nome + '(' + html(a.a[0]) + ')';
      }
    }
    return '?';
  }
  /** Come html(), ma mette le parentesi se il pezzo è negativo o è una somma:
      serve per non scrivere cose ambigue come -5² al posto di (−5)². */
  function htmlP(a) {
    const s = html(a);
    const serve = (a.t === '+' && a.a.length > 1) || (a.t === 'n' && a.v.n < 0) || eNegativo(a);
    return serve ? '(' + s + ')' : s;
  }

  /** Sostituisce una lettera con un numero: serve per le verifiche. */
  function sostituisci(a, v, valore) {
    if (!a) return a;
    switch (a.t) {
      case 'v': return a.n === v ? nNum(valore) : a;
      case '+': return { t: '+', a: a.a.map((x) => sostituisci(x, v, valore)) };
      case '*': return { t: '*', a: a.a.map((x) => sostituisci(x, v, valore)) };
      case '^': return { t: '^', b: sostituisci(a.b, v, valore), e: sostituisci(a.e, v, valore) };
      case 'f': return { t: 'f', n: a.n, a: [sostituisci(a.a[0], v, valore)] };
    }
    return a;
  }

  function frazioneHtml(n, d) {
    return '<span class="frazione"><span class="sopra">' + n + '</span><span class="sotto">' + d + '</span></span>';
  }
  function radiceHtml(dentro) {
    return '<span class="radice"><span class="segno">√</span><span class="dentro">' + dentro + '</span></span>';
  }
  function eNegativo(x) {
    if (x.t === 'n') return x.v.n < 0;
    if (x.t === '*') return x.a.some((y) => y.t === 'n' && y.v.n < 0);
    return false;
  }
  /* Toglie il segno meno SENZA fare i conti: "−5·2" deve restare "5·2",
     non diventare "10". Serve per non far sparire i passaggi sotto gli
     occhi di chi sta cercando di seguirli. */
  function cambiaSegno(x) {
    if (x.t === 'n') return nNum(fNeg(x.v));
    if (x.t === '*') {
      const fattori = x.a.slice();
      for (let i = 0; i < fattori.length; i++) {
        const f = fattori[i];
        if (f.t === 'n' && f.v.n < 0) {
          if (fUno(fNeg(f.v))) fattori.splice(i, 1);      // era −1: sparisce
          else fattori[i] = nNum(fNeg(f.v));
          if (!fattori.length) return nNum(F1);
          return fattori.length === 1 ? fattori[0] : { t: '*', a: fattori };
        }
      }
    }
    return nProd([nNum(fr(-1)), x]);
  }

  /** Blocco HTML pronto da mettere nella pagina. */
  function blocco(a, evidenzia) {
    const dentro = typeof a === 'string' ? a : html(a);
    return '<div class="mate mate-blocco">' + dentro + '</div>';
  }

  /** Trasforma la formula in parole italiane, per la sintesi vocale. */
  function parlato(a) {
    if (!a) return '';
    switch (a.t) {
      case 'n': {
        if (a.v.d === 1) return String(a.v.n);
        const nomi = { 2: 'mezzi', 3: 'terzi', 4: 'quarti', 5: 'quinti', 6: 'sesti', 7: 'settimi', 8: 'ottavi', 9: 'noni', 10: 'decimi' };
        const uno = a.v.n === 1 || a.v.n === -1;
        const sing = { 2: 'mezzo', 3: 'terzo', 4: 'quarto', 5: 'quinto', 6: 'sesto', 7: 'settimo', 8: 'ottavo', 9: 'nono', 10: 'decimo' };
        if (nomi[a.v.d]) return a.v.n + ' ' + (uno ? sing[a.v.d] : nomi[a.v.d]);
        return a.v.n + ' fratto ' + a.v.d;
      }
      case 'v': return a.n;
      case 'c': return 'pi greco';
      case '+': return a.a.map((x, i) => {
        const neg = eNegativo(x);
        const corpo = parlato(neg ? cambiaSegno(x) : x);
        if (i === 0) return (neg ? 'meno ' : '') + corpo;
        return (neg ? ' meno ' : ' più ') + corpo;
      }).join('');
      case '*': {
        // se in mezzo c'è una divisione la leggo come frazione
        const den = [], num = [];
        a.a.forEach((x) => {
          if (x.t === '^' && x.e.t === 'n' && x.e.v.n < 0 && x.e.v.d === 1) {
            den.push(x.e.v.n === -1 ? x.b : nPot(x.b, nNum(fr(-x.e.v.n))));
          } else num.push(x);
        });
        if (den.length) {
          const n = num.length ? num.map(parlato).join(' per ') : 'uno';
          return n + ' fratto ' + den.map(parlato).join(' per ');
        }
        return a.a.map((x) => parlato(x)).join(' per ');
      }
      case '^': {
        const b = parlato(a.b);
        if (a.e.t === 'n' && a.e.v.d === 1) {
          if (a.e.v.n === 2) return b + ' al quadrato';
          if (a.e.v.n === 3) return b + ' al cubo';
          return b + ' alla ' + a.e.v.n;
        }
        if (a.e.t === 'n' && a.e.v.n === 1 && a.e.v.d === 2) return 'radice quadrata di ' + b;
        return b + ' elevato a ' + parlato(a.e);
      }
      case 'f': {
        const dentro = parlato(a.a[0]);
        const nomi = { sqrt: 'radice quadrata di ', sin: 'seno di ', cos: 'coseno di ', tan: 'tangente di ',
          ln: 'logaritmo naturale di ', log: 'logaritmo di ', log2: 'logaritmo in base due di ',
          abs: 'valore assoluto di ', exp: 'e elevato a ', asin: 'arcoseno di ', acos: 'arcocoseno di ', atan: 'arcotangente di ' };
        if (a.n === 'fatt') return dentro + ' fattoriale';
        return (nomi[a.n] || (a.n + ' di ')) + dentro;
      }
    }
    return '';
  }

  /** Legge ad alta voce una formula scritta come testo. */
  function parlaFormula(str) {
    try { return parlato(analizza(str)); }
    catch (e) { return String(str); }
  }

  /* ================================================================
     9. RADICI RAZIONALI E SCOMPOSIZIONE
     ================================================================ */

  function divisoriInteri(n) {
    n = Math.abs(Math.round(n));
    const out = [];
    if (n === 0) return [1];
    for (let i = 1; i <= Math.min(n, 4000); i++) if (n % i === 0) out.push(i);
    return out;
  }

  /** Radici razionali di un polinomio a coefficienti frazionari. */
  function radiciRazionali(c) {
    while (c.length > 1 && fZero(c[c.length - 1])) c = c.slice(0, -1);
    if (c.length < 2) return [];
    // porto a coefficienti interi
    let mcm = 1;
    c.forEach((f) => { mcm = mcm * f.d / mcd(mcm, f.d); });
    const ic = c.map((f) => Math.round(fNum(f) * mcm));
    let i0 = 0;
    while (i0 < ic.length && ic[i0] === 0) i0++;
    const radici = [];
    if (i0 > 0) radici.push(F0);                   // x=0 è radice
    const cc = ic.slice(i0);
    if (cc.length < 2) return radici;
    const p = divisoriInteri(cc[0]);
    const q = divisoriInteri(cc[cc.length - 1]);
    const viste = {};
    for (const a of p) for (const b of q) for (const s of [1, -1]) {
      const r = fr(s * a, b);
      const k = fTesto(r);
      if (viste[k]) continue;
      viste[k] = 1;
      let v = F0;
      for (let i = cc.length - 1; i >= 0; i--) v = fAdd(fMul(v, r), fr(cc[i]));
      if (fZero(v)) radici.push(r);
    }
    return radici;
  }

  /* ================================================================
     10. SUPPORTO ALLE RADICI QUADRATE ESATTE
     ================================================================ */

  /** √72 → {fuori:6, dentro:2}. Se dentro===1 la radice è esatta. */
  function radiceSemplificata(n) {
    n = Math.round(n);
    if (n < 0) return null;
    let fuori = 1, dentro = n;
    for (let i = 2; i * i <= dentro; i++) {
      while (dentro % (i * i) === 0) { fuori *= i; dentro /= i * i; }
    }
    return { fuori, dentro };
  }

  /** Costruisce (a ± √Δ)/b in modo esatto quando si può. */
  function soluzioneRadicale(numCost, segno, delta, den) {
    const rs = radiceSemplificata(delta);
    if (rs && rs.dentro === 1) {
      return { esatta: true, valore: fr(numCost + segno * rs.fuori, den) };
    }
    return { esatta: false, rs, numCost, segno, den, valore: null };
  }

  /* ================================================================
     11. INTERFACCIA PUBBLICA DEL MOTORE
     ================================================================ */

  return {
    // numeri
    fr, fAdd, fSub, fMul, fDiv, fNeg, fZero, fUno, fInt, fNum, fCmp, fPow, fTesto, fDecimale, F0, F1,
    // lettura e scrittura
    analizza, testo, html, htmlP, blocco, parlato, parlaFormula, normalizzaTesto, sostituisci,
    // nodi
    nNum, nVar, nSomma, nProd, nPot, nDiv,
    // polinomi
    pol, polCost, polVar, polSomma, polSottrai, polProdotto, polPotenza, polScala,
    polDaAst, polAAst, polCoeff, polCoeffNum, polGrado, polVariabili, polZero, polCostante, polValoreCost,
    daCoeff, divisioneNum, radiciRazionali, radiceSemplificata, soluzioneRadicale,
    // funzioni razionali
    ratDaAst,
    // calcolo
    valuta, semplifica, errore
  };
})();


/* ============================================================
   07-mate-risolutore.js
   ============================================================ */
/* ==================================================================
   07-mate-risolutore.js — risoluzioni passo passo con spiegazione

   Ogni risultato ha:
     stato:   'verde'  posso risolverlo,
              'giallo' posso aiutarti solo in parte,
              'rosso'  non posso farlo in modo affidabile offline.
     passi:   [{ tit, html, spiega, perche }]
   Non viene mai inventato un passaggio: se il motore non sa
   giustificare una trasformazione, lo dichiara.
   ================================================================== */

const Risolutore = (function () {

  const F = M.fr;
  /** Frazione scritta per gli occhi (con il vero segno meno). */
  function nt(f) { return M.fTesto(f).replace(/-/g, '−'); }

  function passo(tit, html, spiega, perche) {
    return { tit, html, spiega: spiega || '', perche: perche || '' };
  }

  /* ---- come si scrive un passaggio -------------------------------
     Regole tenute per tutta la sezione:
     - l'equazione sta su UNA riga sola, con il segno = al suo posto;
     - il pezzo che cambia in questo passaggio è evidenziato;
     - prima si scrive l'operazione (22 − 7), poi si fa il conto (15).
       Chi fa fatica deve poter vedere DOVE è finito ogni numero.
     ---------------------------------------------------------------- */

  /** Un'equazione su una riga sola. */
  function eqHtml(sinistra, destra, segno) {
    return '<div class="mate mate-blocco">' + sinistra +
      '<span class="uguale">' + (segno || '=') + '</span>' + destra + '</div>';
  }
  /** Mette in risalto il pezzo che cambia (non solo col colore:
      c'è anche il bordo e la sottolineatura). */
  function evid(html) { return '<mark>' + html + '</mark>'; }

  /** Un membro scritto per intero: "3x − 27", "22", "x". */
  function latoHtml(cx, c0, v) {
    let s = '';
    if (!M.fZero(cx)) s += coefVar(cx, v, true);
    if (!M.fZero(c0) || !s) {
      if (!s) s = M.html(M.nNum(c0));
      else {
        const neg = M.fNum(c0) < 0;
        s += '<span class="operatore">' + (neg ? '−' : '+') + '</span>' +
          M.html(M.nNum(neg ? M.fNeg(c0) : c0));
      }
    }
    return s;
  }

  /** L'operazione che aggiungo ai due membri: "− 7", "+ 3x". */
  function operazioneHtml(f, v) {
    const neg = M.fNum(f) < 0;
    const ass = neg ? M.fNeg(f) : f;
    const corpo = v
      ? (M.fUno(ass) ? '<i>' + v + '</i>' : M.html(M.nNum(ass)) + '<i>' + v + '</i>')
      : M.html(M.nNum(ass));
    return '<span class="operatore">' + (neg ? '−' : '+') + '</span>' + corpo;
  }

  /** La stessa operazione detta a parole, con la preposizione giusta:
      "Tolgo 7 da tutte e due le parti" / "Aggiungo 27 a tutte e due le parti". */
  function testoOperazione(f, v) {
    const neg = M.fNum(f) < 0;
    const ass = neg ? M.fNeg(f) : f;
    return (neg ? 'Tolgo ' : 'Aggiungo ') + nt(ass) + (v || '') +
      (neg ? ' da tutte e due le parti' : ' a tutte e due le parti');
  }

  /** Una divisione scritta come frazione: serve per "3x ÷ 3". */
  function frazioneHtml(sopra, sotto) {
    return '<span class="frazione"><span class="sopra">' + sopra +
      '</span><span class="sotto">' + sotto + '</span></span>';
  }
  function esito(stato, messaggio, passi, extra) {
    return Object.assign({ stato, messaggio: messaggio || '', passi: passi || [] }, extra || {});
  }

  /** Divide "3x+7 = 22" in due parti. */
  function dividiUguale(str) {
    const t = M.normalizzaTesto(str);
    const pezzi = t.split('=');
    if (pezzi.length === 1) return { sx: pezzi[0], dx: '0', aveva: false };
    if (pezzi.length !== 2) throw M.errore('In un\'equazione ci va un solo segno "=".');
    return { sx: pezzi[0], dx: pezzi[1], aveva: true };
  }

  /** Trova la lettera dell'incognita. */
  function incognita(P, preferita) {
    const v = M.polVariabili(P);
    if (!v.length) return preferita || 'x';
    if (v.indexOf(preferita || 'x') >= 0) return preferita || 'x';
    return v[0];
  }

  /* ================================================================
     EQUAZIONI
     ================================================================ */

  function risolviEquazione(str, variabile) {
    let parti;
    try { parti = dividiUguale(str); } catch (e) { return esito('rosso', e.message); }
    if (!parti.aveva) return esito('rosso', 'Per risolvere un\'equazione serve il segno "=". Per esempio: 3x + 7 = 22');

    let sx, dx;
    try { sx = M.analizza(parti.sx); dx = M.analizza(parti.dx); }
    catch (e) { return esito('rosso', e.amichevole ? e.message : 'Non riesco a leggere questa equazione.'); }

    const rs = M.ratDaAst(sx), rd = M.ratDaAst(dx);
    if (!rs || !rd) {
      return esito('rosso', 'Questa equazione contiene funzioni (seno, logaritmo, radice…) che non so risolvere passo passo senza rischiare di sbagliare. Posso però disegnarti il grafico e trovare le soluzioni approssimate con il graficatore.');
    }

    const denominatoriConIncognita =
      !M.polCostante(rs.d) || !M.polCostante(rd.d);

    // porto tutto a sinistra:  (Ns*Dd - Nd*Ds) / (Ds*Dd) = 0
    const N = M.polSottrai(M.polProdotto(rs.n, rd.d), M.polProdotto(rd.n, rs.d));
    const D = M.polProdotto(rs.d, rd.d);
    const v = incognita(M.polSomma(N, D), variabile);

    const cN = M.polCoeffNum(N, v);
    if (!cN) {
      return esito('giallo', 'In questa equazione c\'è più di una lettera: dimmi quale è l\'incognita oppure usa una sola lettera.',
        [passo('L\'equazione', eqHtml(M.html(M.polAAst(N)), '0'), 'Questa è l\'equazione con tutto portato a sinistra.')]);
    }

    const passi = [];
    passi.push(passo('Partiamo da', eqHtml(M.html(sx), M.html(dx)),
      'Questa è l\'equazione da risolvere.'));

    let condizioni = [];
    if (denominatoriConIncognita) {
      const cD = M.polCoeffNum(D, v);
      const rad = cD ? M.radiciRazionali(cD) : [];
      condizioni = rad;
      passi.push(passo('Condizioni di esistenza',
        eqHtml(M.html(M.polAAst(D)), '0', '≠') +
        (rad.length ? eqHtml('<i>' + v + '</i>', rad.map((r) => nt(r)).join(' &nbsp;e&nbsp; <i>' + v + '</i> ≠ '), '≠') : ''),
        'Il denominatore non può mai valere zero.',
        'Se il denominatore valesse zero la frazione non avrebbe senso: quei valori vanno esclusi anche se poi vengono fuori dai calcoli.'));
      passi.push(passo('Tolgo i denominatori',
        eqHtml(M.html(M.polAAst(N)), '0'),
        'Moltiplico i due membri per il denominatore comune.',
        'Se due frazioni con lo stesso denominatore sono uguali, allora sono uguali anche i numeratori. Alla fine controllerò le condizioni di esistenza.'));
    }

    // tolgo il grado zero superfluo
    let c = cN.slice();
    while (c.length > 1 && M.fZero(c[c.length - 1])) c.pop();
    const grado = c.length - 1;

    let soluzioni = [];
    let stato = 'verde';

    if (grado <= 0) {
      if (M.fZero(c[0])) {
        passi.push(passo('Risultato', '<div class="mate mate-blocco">0 = 0</div>',
          'L\'uguaglianza è sempre vera: ogni numero va bene.',
          'Quando l\'incognita sparisce e resta una cosa vera, l\'equazione è "indeterminata".'));
        return esito('verde', 'Ogni valore di ' + v + ' è soluzione.', passi, { soluzioni: 'tutte', variabile: v });
      }
      passi.push(passo('Risultato', eqHtml(M.html(M.nNum(c[0])), '0'),
        'Questa uguaglianza è falsa: non esiste nessuna soluzione.',
        'Quando l\'incognita sparisce e resta una cosa falsa, l\'equazione è "impossibile".'));
      return esito('verde', 'Nessuna soluzione.', passi, { soluzioni: [], variabile: v });
    }

    if (grado === 1) {
      passiPrimoGrado(passi, sx, dx, v, denominatoriConIncognita, c);
      soluzioni = [M.fDiv(M.fNeg(c[0]), c[1])];
    } else if (grado === 2) {
      soluzioni = passiSecondoGrado(passi, v, c);
      if (soluzioni === null) { soluzioni = []; }
    } else {
      const r = passiGradoAlto(passi, v, c);
      soluzioni = r.soluzioni;
      stato = r.stato;
    }

    // condizioni di esistenza: scarto le soluzioni non accettabili
    if (condizioni.length && Array.isArray(soluzioni)) {
      const buone = soluzioni.filter((s) => !condizioni.some((k) => M.fCmp(k, s) === 0));
      if (buone.length !== soluzioni.length) {
        passi.push(passo('Controllo le condizioni di esistenza',
          '<div class="mate mate-blocco">' + v + ' ≠ ' + condizioni.map((r) => nt(r)).join(', ') + '</div>',
          'Una soluzione trovata non è accettabile perché annulla il denominatore: la devo scartare.'));
        soluzioni = buone;
      }
    }

    if (Array.isArray(soluzioni) && soluzioni.length) {
      const controllo = verificaNumerica(sx, dx, v, soluzioni[0]);
      if (controllo) passi.push(controllo);
    }

    return esito(stato, '', passi, { soluzioni, variabile: v });
  }

  /* ---- primo grado ------------------------------------------------ */
  function passiPrimoGrado(passi, sx, dx, v, giaSpostato, c) {
    const Psx = M.polDaAst(sx), Pdx = M.polDaAst(dx);
    const cSx = Psx ? M.polCoeffNum(Psx, v) : null;
    const cDx = Pdx ? M.polCoeffNum(Pdx, v) : null;

    let ax = c[1], m0 = M.fNeg(c[0]);      // valori finali, per il caso di ripiego

    if (!giaSpostato && cSx && cDx) {
      ax = cSx[1] || M.F0;
      let n0 = cSx[0] || M.F0;             // sinistra:  ax·x + n0
      let bx = cDx[1] || M.F0;
      m0 = cDx[0] || M.F0;                 // destra:    bx·x + m0

      // 1. via le parentesi (solo se cambia qualcosa)
      if (M.testo(M.polAAst(Psx)) !== M.testo(sx) || M.testo(M.polAAst(Pdx)) !== M.testo(dx)) {
        passi.push(passo('Tolgo le parentesi',
          eqHtml(latoHtml(ax, n0, v), latoHtml(bx, m0, v)),
          'Faccio le moltiplicazioni e sommo i termini uguali.',
          'Conviene sistemare una parte alla volta, prima di spostare i pezzi da una parte all\'altra.'));
      }

      // 2. tutte le x da una parte sola
      if (!M.fZero(bx)) {
        const op = M.fNeg(bx);
        passi.push(passo(testoOperazione(op, v),
          eqHtml(latoHtml(ax, n0, v) + evid(operazioneHtml(op, v)),
                 latoHtml(bx, m0, v) + evid(operazioneHtml(op, v))),
          'Scrivo la stessa operazione a destra e a sinistra.',
          'L\'uguale è una bilancia. Se togli qualcosa da un piatto, devi toglierlo anche dall\'altro: altrimenti non sta più in equilibrio.'));
        ax = M.fAdd(ax, op);
        passi.push(passo('Faccio i conti',
          eqHtml(latoHtml(ax, n0, v), M.html(M.nNum(m0))),
          'A destra la ' + v + ' è sparita: adesso è tutta a sinistra.'));
      }

      // 3. tutti i numeri dall'altra parte
      if (!M.fZero(n0)) {
        const op = M.fNeg(n0);
        const eraGiaSuccesso = !M.fZero(bx);
        passi.push(passo(testoOperazione(op, ''),
          eqHtml(latoHtml(ax, n0, v) + evid(operazioneHtml(op, '')),
                 M.html(M.nNum(m0)) + evid(operazioneHtml(op, ''))),
          eraGiaSuccesso ? 'Di nuovo la stessa cosa da tutte e due le parti.'
                         : 'Scrivo la stessa operazione a destra e a sinistra.',
          'Così a sinistra resta soltanto la parte con la ' + v + ', e i numeri finiscono tutti a destra.'));
        m0 = M.fAdd(m0, op);
        passi.push(passo('Faccio i conti',
          eqHtml(latoHtml(ax, M.F0, v), M.html(M.nNum(m0))),
          'Adesso l\'equazione è pulita: da una parte la ' + v + ', dall\'altra un numero.'));
      }
    } else {
      passi.push(passo('Ordino l\'equazione',
        eqHtml(latoHtml(ax, M.F0, v), M.html(M.nNum(m0))),
        'Metto la parte con la ' + v + ' a sinistra e i numeri a destra.'));
    }

    // 4. divido, se serve
    const sol = M.fDiv(M.fNeg(c[0]), c[1]);
    if (!M.fUno(ax)) {
      passi.push(passo('Divido tutte e due le parti per ' + nt(ax),
        eqHtml(frazioneHtml(latoHtml(ax, M.F0, v), evid(M.html(M.nNum(ax)))),
               frazioneHtml(M.html(M.nNum(m0)), evid(M.html(M.nNum(ax))))),
        'Divido per il numero che sta davanti alla ' + v + '.',
        'Davanti alla ' + v + ' c\'è ' + nt(ax) + '. Dividendo per ' + nt(ax) + ', a sinistra resta la ' + v + ' da sola: è quello che vogliamo.'));
    }

    passi.push(passo('Risultato',
      eqHtml('<i>' + v + '</i>', M.html(M.nNum(sol)) +
        (M.fInt(sol) ? '' : ' <span style="font-size:.65em">(cioè ' + M.fDecimale(sol) + ')</span>')),
      'Ecco quanto vale la ' + v + '.'));
  }

  /* ---- secondo grado --------------------------------------------- */
  function passiSecondoGrado(passi, v, c) {
    const a = c[2], b = c[1] || M.F0, cc = c[0] || M.F0;
    const ordinata = eqHtml(M.html(M.polAAst(M.daCoeff(c, v))), '0');
    // se l'equazione è già ordinata non ripeto lo stesso passaggio due volte
    if (!passi.length || passi[passi.length - 1].html !== ordinata) {
      passi.push(passo('Porto tutto a sinistra, nella forma ordinata', ordinata,
        'Un\'equazione di secondo grado si scrive sempre così: a' + v + '² + b' + v + ' + c = 0.'));
    }
    passi.push(passo('Riconosco a, b e c',
      '<div class="mate mate-blocco">a = ' + M.html(M.nNum(a)) + ' &nbsp;&nbsp; b = ' + M.html(M.nNum(b)) + ' &nbsp;&nbsp; c = ' + M.html(M.nNum(cc)) + '</div>',
      'a è il numero davanti a ' + v + '², b quello davanti a ' + v + ', c il numero da solo.'));

    const delta = M.fSub(M.fMul(b, b), M.fMul(F(4), M.fMul(a, cc)));
    passi.push(passo('Calcolo il delta',
      '<div class="mate mate-blocco">Δ = b² − 4ac = ' + M.htmlP(M.nNum(b)) + '² − 4·' + M.htmlP(M.nNum(a)) + '·' + M.htmlP(M.nNum(cc)) + ' = ' + M.html(M.nNum(delta)) + '</div>',
      'Il delta dice quante soluzioni ci sono.',
      'Se Δ è maggiore di zero ci sono due soluzioni; se è zero ce n\'è una sola; se è minore di zero non ci sono soluzioni fra i numeri reali.'));

    const dNum = M.fNum(delta);
    if (dNum < 0) {
      passi.push(passo('Il delta è negativo',
        '<div class="mate mate-blocco">Δ = ' + M.html(M.nNum(delta)) + ' &lt; 0</div>',
        'Non ci sono soluzioni fra i numeri reali: la parabola non tocca l\'asse delle x.'));
      return [];
    }
    if (dNum === 0) {
      const s = M.fDiv(M.fNeg(b), M.fMul(F(2), a));
      passi.push(passo('Il delta è zero: una sola soluzione (doppia)',
        '<div class="mate mate-blocco"><i>' + v + '</i> = ' + M.html(M.nDiv(M.nNum(M.fNeg(b)), M.nNum(M.fMul(F(2), a)))) + ' = ' + M.html(M.nNum(s)) + '</div>',
        'Quando Δ = 0 la formula dà un unico valore.'));
      return [s];
    }

    // Δ > 0
    passi.push(passo('Applico la formula',
      '<div class="mate mate-blocco"><i>' + v + '</i><sub>1,2</sub> = ' +
      '<span class="frazione"><span class="sopra">−b ± <span class="radice"><span class="segno">√</span><span class="dentro">Δ</span></span></span><span class="sotto">2a</span></span>' +
      ' = <span class="frazione"><span class="sopra">' + M.html(M.nNum(M.fNeg(b))) + ' ± <span class="radice"><span class="segno">√</span><span class="dentro">' + M.html(M.nNum(delta)) + '</span></span></span><span class="sotto">' + M.html(M.nNum(M.fMul(F(2), a))) + '</span></span></div>',
      'Metto i numeri al posto delle lettere.'));

    // radice esatta?
    if (M.fInt(delta) && delta.n >= 0) {
      const rs = M.radiceSemplificata(delta.n);
      if (rs && rs.dentro === 1) {
        const r = F(rs.fuori);
        const s1 = M.fDiv(M.fAdd(M.fNeg(b), r), M.fMul(F(2), a));
        const s2 = M.fDiv(M.fSub(M.fNeg(b), r), M.fMul(F(2), a));
        passi.push(passo('Calcolo la radice',
          '<div class="mate mate-blocco"><span class="radice"><span class="segno">√</span><span class="dentro">' + delta.n + '</span></span> = ' + evid(String(rs.fuori)) + '</div>',
          'La radice viene un numero intero: le soluzioni sono esatte.'));
        // faccio vedere i due conti separati, senza saltare passaggi
        const den = M.fMul(F(2), a);
        const sopraPiu = M.fAdd(M.fNeg(b), r), sopraMeno = M.fSub(M.fNeg(b), r);
        passi.push(passo('Prima soluzione: quella con il +',
          eqHtml('<i>' + v + '</i><sub>1</sub>',
            frazioneHtml(M.html(M.nNum(M.fNeg(b))) + ' <span class="operatore">+</span> ' + rs.fuori, M.html(M.nNum(den))) +
            '<span class="uguale">=</span>' + frazioneHtml(M.html(M.nNum(sopraPiu)), M.html(M.nNum(den))) +
            '<span class="uguale">=</span>' + M.html(M.nNum(s1))),
          'Faccio il conto sopra, poi divido.'));
        passi.push(passo('Seconda soluzione: quella con il −',
          eqHtml('<i>' + v + '</i><sub>2</sub>',
            frazioneHtml(M.html(M.nNum(M.fNeg(b))) + ' <span class="operatore">−</span> ' + rs.fuori, M.html(M.nNum(den))) +
            '<span class="uguale">=</span>' + frazioneHtml(M.html(M.nNum(sopraMeno)), M.html(M.nNum(den))) +
            '<span class="uguale">=</span>' + M.html(M.nNum(s2))),
          'Stesso conto, ma con il meno.'));
        const ordinate = [s1, s2].sort((x, y) => M.fCmp(x, y));
        passi.push(passo('Le due soluzioni',
          eqHtml('<i>' + v + '</i>', M.html(M.nNum(ordinate[0])) + ' &nbsp;&nbsp;oppure&nbsp;&nbsp; ' + M.html(M.nNum(ordinate[1]))),
          'Un\'equazione di secondo grado può avere due soluzioni: vanno bene tutte e due.'));
        return ordinate;
      }
      if (rs && rs.fuori > 1) {
        passi.push(passo('Semplifico la radice',
          '<div class="mate mate-blocco"><span class="radice"><span class="segno">√</span><span class="dentro">' + delta.n + '</span></span> = ' + rs.fuori + '<span class="radice"><span class="segno">√</span><span class="dentro">' + rs.dentro + '</span></span></div>',
          'Porto fuori dalla radice quello che si può.'));
      }
    }
    const dRad = Math.sqrt(Math.abs(M.fNum(delta)));
    const s1 = (M.fNum(M.fNeg(b)) + dRad) / (2 * M.fNum(a));
    const s2 = (M.fNum(M.fNeg(b)) - dRad) / (2 * M.fNum(a));
    passi.push(passo('Le due soluzioni (valori approssimati)',
      '<div class="mate mate-blocco"><i>' + v + '</i><sub>1</sub> ≈ ' + numeroIt(s1) + ' &nbsp;&nbsp;&nbsp; <i>' + v + '</i><sub>2</sub> ≈ ' + numeroIt(s2) + '</div>',
      'La radice del delta non è un numero intero: le soluzioni restano con la radice. Qui sotto trovi il valore approssimato.',
      'Sul quaderno la forma esatta si scrive con la radice: è quella la risposta "giusta". Il numero con la virgola serve solo per capire quanto vale.'));
    return [];    // niente soluzioni esatte da usare per la verifica
  }

  /* ---- grado 3 o superiore ---------------------------------------- */
  function passiGradoAlto(passi, v, c) {
    passi.push(passo('Porto tutto a sinistra',
      eqHtml(M.html(M.polAAst(M.daCoeff(c, v))), '0'),
      'Il grado di questa equazione è ' + (c.length - 1) + '.'));
    let resto = c.slice();
    const trovate = [];
    for (let giro = 0; giro < 8 && resto.length - 1 > 2; giro++) {
      const rad = M.radiciRazionali(resto);
      if (!rad.length) break;
      const r = rad[0];
      const div = M.divisioneNum(resto, [M.fNeg(r), M.F1]);
      if (!div) break;
      trovate.push(r);
      resto = div.q;
      passi.push(passo('Trovo una soluzione e abbasso il grado',
        '<div class="mate mate-blocco"><i>' + v + '</i> = ' + M.html(M.nNum(r)) + '</div>' +
        eqHtml(M.html(M.polAAst(M.daCoeff(resto, v))), '0'),
        'Provando i divisori dei numeri estremi ho trovato che ' + v + ' = ' + nt(r) + ' annulla il polinomio. Divido e resta un\'equazione di grado più basso.',
        'Se ' + v + ' = ' + nt(r) + ' è una soluzione, allora (' + v + ' − ' + nt(r) + ') è un fattore: dividendo si ottiene un polinomio di un grado in meno.'));
    }
    if (resto.length - 1 === 2) {
      const s = passiSecondoGrado(passi, v, resto);
      return { stato: 'verde', soluzioni: trovate.concat(s || []).sort((a, b) => M.fCmp(a, b)) };
    }
    if (resto.length - 1 === 1) {
      const s = M.fDiv(M.fNeg(resto[0]), resto[1]);
      passi.push(passo('Ultima soluzione', '<div class="mate mate-blocco"><i>' + v + '</i> = ' + M.html(M.nNum(s)) + '</div>', ''));
      return { stato: 'verde', soluzioni: trovate.concat([s]).sort((a, b) => M.fCmp(a, b)) };
    }
    return {
      stato: trovate.length ? 'giallo' : 'rosso',
      soluzioni: trovate,
      messaggio: trovate.length
        ? 'Ho trovato le soluzioni "semplici", ma quello che resta non si scompone con numeri razionali: le altre soluzioni non le so calcolare in modo esatto.'
        : 'Questa equazione non si risolve con i metodi della scuola in modo esatto. Prova con il graficatore per vedere dove la curva taglia l\'asse delle x.'
    };
  }

  /* ---- verifica --------------------------------------------------- */
  function verificaNumerica(sx, dx, v, valore) {
    try {
      const scope = {}; scope[v] = M.fNum(valore);
      const a = M.valuta(sx, scope), b = M.valuta(dx, scope);
      if (Math.abs(a - b) < 1e-9) {
        // riscrivo l'equazione mettendo il numero al posto della lettera
        const sxSost = M.html(M.sostituisci(sx, v, valore));
        const dxSost = M.html(M.sostituisci(dx, v, valore));
        return passo('Controllo',
          '<div class="mate mate-blocco">' + sxSost + ' = ' + dxSost + '</div>' +
          '<div class="mate mate-blocco">' + numeroIt(a) + ' = ' + numeroIt(b) + ' ✅</div>',
          'Sostituisco ' + v + ' = ' + nt(valore) + ' nell\'equazione di partenza: viene la stessa cosa da tutte e due le parti. Risultato verificato.');
      }
    } catch (e) { /* niente */ }
    return null;
  }

  /** Controlla il risultato scritto dallo studente. */
  function controllaRisultato(equazione, variabile, valoreTesto) {
    try {
      const parti = dividiUguale(equazione);
      const sx = M.analizza(parti.sx), dx = M.analizza(parti.dx);
      const val = M.valuta(M.analizza(valoreTesto), {});
      const scope = {}; scope[variabile || 'x'] = val;
      const a = M.valuta(sx, scope), b = M.valuta(dx, scope);
      return {
        ok: Math.abs(a - b) < 1e-9,
        sinistra: a, destra: b,
        html: '<div class="mate mate-blocco">' + numeroIt(a) + (Math.abs(a - b) < 1e-9 ? ' = ' : ' ≠ ') + numeroIt(b) + '</div>'
      };
    } catch (e) {
      return { errore: e.amichevole ? e.message : 'Non riesco a controllare questo risultato.' };
    }
  }

  /* ================================================================
     LO RISOLVO IO — controllo dei passaggi scritti dallo studente

     Qui il programma NON risolve: guarda quello che ha scritto il
     ragazzo e gli dice soltanto se sta ancora dicendo la stessa cosa.
     ================================================================ */

  /** Porta l'equazione nella forma "tutto a sinistra" e ne prende i coefficienti. */
  function coefficientiRiga(testo, v) {
    const parti = dividiUguale(testo);
    if (!parti.aveva) throw M.errore('In un\'equazione ci vuole il segno "=".');
    const sx = M.analizza(parti.sx), dx = M.analizza(parti.dx);
    const rs = M.ratDaAst(sx), rd = M.ratDaAst(dx);
    if (!rs || !rd) return null;
    const N = M.polSottrai(M.polProdotto(rs.n, rd.d), M.polProdotto(rd.n, rs.d));
    const c = M.polCoeffNum(N, v || incognita(N));
    if (!c) return null;
    const pulito = c.slice();
    while (pulito.length > 1 && M.fZero(pulito[pulito.length - 1])) pulito.pop();
    return pulito;
  }

  /**
   * Due righe dicono la stessa cosa?
   * Sì se una è l'altra moltiplicata per un numero: è esattamente quello
   * che succede quando si sposta un termine o si divide per un numero.
   */
  function passaggioEquivalente(rigaPrima, rigaDopo, v) {
    let a, b;
    try { a = coefficientiRiga(rigaPrima, v); b = coefficientiRiga(rigaDopo, v); }
    catch (e) { return { esito: 'nonSo', messaggio: e.amichevole ? e.message : 'Non riesco a leggere questa riga.' }; }
    if (!a || !b) {
      return { esito: 'nonSo', messaggio: 'Non riesco a controllare queste righe. Guarda che ci sia un solo segno "=" e che ci sia una sola lettera: se hai copiato dal libro, può esserci finito dentro qualche pezzo di testo.' };
    }

    const tuttoZero = (c) => c.every((x) => M.fZero(x));
    if (tuttoZero(a) && tuttoZero(b)) return { esito: 'ok' };
    if (a.length !== b.length) return { esito: 'diverso' };

    let k = null;
    for (let i = 0; i < a.length; i++) {
      const za = M.fZero(a[i]), zb = M.fZero(b[i]);
      if (za && zb) continue;
      if (za !== zb) return { esito: 'diverso' };
      const r = M.fDiv(b[i], a[i]);
      if (k === null) k = r;
      else if (M.fCmp(k, r) !== 0) return { esito: 'diverso' };
    }
    return { esito: 'ok', fattore: k };
  }

  /** Che cosa conviene fare adesso? (un suggerimento, non la soluzione) */
  function prossimaMossa(riga, v) {
    v = v || 'x';
    let parti, sx, dx;
    try {
      parti = dividiUguale(riga);
      if (!parti.aveva) return { testo: 'Manca il segno "=": un\'equazione ha sempre due parti.' };
      sx = M.analizza(parti.sx); dx = M.analizza(parti.dx);
    } catch (e) {
      return { testo: e.amichevole ? e.message : 'Non riesco a leggere questa riga: controlla come l\'hai scritta.' };
    }
    const Psx = M.polDaAst(sx), Pdx = M.polDaAst(dx);
    if (!Psx || !Pdx) return { testo: 'Ci sono ancora delle frazioni con la ' + v + '. Prova a moltiplicare tutto per il denominatore.' };

    const c = coefficientiRiga(riga, v);
    if (c && c.length - 1 > 1) {
      return { testo: 'Questa è di secondo grado. Portala nella forma a' + v + '² + b' + v + ' + c = 0, poi calcola il delta.' };
    }

    const cSx = M.polCoeffNum(Psx, v) || [], cDx = M.polCoeffNum(Pdx, v) || [];
    const a = cSx[1] || M.F0, n = cSx[0] || M.F0;
    const b = cDx[1] || M.F0, m = cDx[0] || M.F0;

    // ci sono ancora parentesi da togliere?  (guardo quello che vede lui)
    if (/\(/.test(riga)) {
      return { testo: 'Prima togli le parentesi: moltiplica quello che sta fuori per ogni pezzo che sta dentro.' };
    }
    // ci sono termini uguali da mettere insieme?
    const daUnire = (ast, P) => {
      const scritti = ast.t === '+' ? ast.a.length : 1;
      const ridotti = (() => { const c = M.polAAst(P); return c.t === '+' ? c.a.length : 1; })();
      return scritti > ridotti;
    };
    if (daUnire(sx, Psx) || daUnire(dx, Pdx)) {
      return { testo: 'Metti insieme i termini uguali: somma fra loro i numeri, e fra loro le ' + v + '.' };
    }
    if (!M.fZero(b)) {
      return { testo: 'Togli ' + nt(b) + v + ' da tutte e due le parti, così la ' + v + ' resta solo a sinistra.' };
    }
    if (!M.fZero(n)) {
      return M.fNum(n) > 0
        ? { testo: 'Togli ' + nt(n) + ' da tutte e due le parti, così a sinistra resta solo la parte con la ' + v + '.' }
        : { testo: 'Aggiungi ' + nt(M.fNeg(n)) + ' a tutte e due le parti, così a sinistra resta solo la parte con la ' + v + '.' };
    }
    if (M.fZero(a)) return { testo: 'La ' + v + ' è sparita: controlla i passaggi di prima.' };
    if (!M.fUno(a)) {
      return { testo: 'Ci sei quasi: dividi tutte e due le parti per ' + nt(a) + '.' };
    }
    // qui la riga è già "x = m": il valore è m, non il suo opposto
    return { testo: 'Hai finito! Questa riga dice già quanto vale ' + v + '.', finito: true, valore: m };
  }

  /** Alla fine: la riga dice il valore giusto? */
  function esitoFinale(righe, v) {
    v = v || 'x';
    const prima = righe[0], ultima = righe[righe.length - 1];
    const eq = passaggioEquivalente(prima, ultima, v);
    if (eq.esito === 'nonSo') return { stato: 'nonSo', messaggio: eq.messaggio };
    if (eq.esito === 'diverso') {
      return { stato: 'errore', messaggio: 'L\'ultima riga non dice più la stessa cosa della prima: da qualche parte c\'è un errore. Prova il pulsante ✓ su ogni riga per scoprire dove.' };
    }
    const mossa = prossimaMossa(ultima, v);
    if (!mossa.finito) {
      return { stato: 'quasi', messaggio: 'Fin qui è tutto giusto, ma non hai ancora finito. ' + mossa.testo };
    }
    return { stato: 'ok', messaggio: 'Perfetto: ' + v + ' = ' + nt(mossa.valore) + '. E l\'hai trovato tu.', valore: mossa.valore };
  }

  /* ================================================================
     DISEQUAZIONI
     ================================================================ */

  function risolviDisequazione(str) {
    const t = M.normalizzaTesto(str);
    const m = t.match(/(<=|>=|<|>)/);
    if (!m) return esito('rosso', 'In una disequazione ci vuole un segno di disuguaglianza: <, >, ≤ oppure ≥.');
    const segno = m[1];
    const [sxT, dxT] = t.split(segno);
    let sx, dx;
    try { sx = M.analizza(sxT); dx = M.analizza(dxT); }
    catch (e) { return esito('rosso', e.amichevole ? e.message : 'Non riesco a leggere questa disequazione.'); }

    const P = M.polDaAst(sx) && M.polDaAst(dx) ? M.polSottrai(M.polDaAst(sx), M.polDaAst(dx)) : null;
    if (!P) return esito('rosso', 'So risolvere le disequazioni con i polinomi (primo e secondo grado). Questa contiene qualcosa che non riesco a trattare in modo sicuro.');
    const v = incognita(P);
    const c = M.polCoeffNum(P, v);
    if (!c) return esito('giallo', 'Ci sono troppe lettere: usane una sola.');
    let cc = c.slice();
    while (cc.length > 1 && M.fZero(cc[cc.length - 1])) cc.pop();

    const passi = [passo('Partiamo da', eqHtml(M.html(sx), M.html(dx), segnoHtml(segno)), '')];
    passi.push(passo('Porto tutto a sinistra',
      eqHtml(M.html(M.polAAst(M.daCoeff(cc, v))), '0', segnoHtml(segno)),
      'Così devo solo studiare quando questa espressione è positiva o negativa.'));

    const grado = cc.length - 1;
    if (grado === 1) {
      const a = cc[1], b = cc[0];
      const s = M.fDiv(M.fNeg(b), a);
      let segnoFinale = segno;
      if (M.fNum(a) < 0) {
        segnoFinale = giraSegno(segno);
        passi.push(passo('Divido per un numero negativo: il verso cambia!',
          '<div class="mate mate-blocco"><i>' + v + '</i> ' + segnoHtml(segnoFinale) + ' ' + M.html(M.nNum(s)) + '</div>',
          'Ho diviso per ' + nt(a) + ', che è negativo: il segno della disuguaglianza si gira.',
          'Se moltiplichi o dividi per un numero negativo, il maggiore diventa minore. Prova con i numeri: 2 < 3, ma −2 > −3.'));
      } else {
        passi.push(passo('Divido per ' + nt(a),
          '<div class="mate mate-blocco"><i>' + v + '</i> ' + segnoHtml(segnoFinale) + ' ' + M.html(M.nNum(s)) + '</div>',
          'Il numero davanti alla ' + v + ' è positivo: il verso resta uguale.'));
      }
      return esito('verde', '', passi, {
        soluzioneTesto: v + ' ' + segnoTesto(segnoFinale) + ' ' + nt(s),
        retta: { punti: [{ x: M.fNum(s), aperto: segnoFinale === '<' || segnoFinale === '>' }], versoDestra: segnoFinale === '>' || segnoFinale === '>=' },
        variabile: v
      });
    }

    if (grado === 2) {
      const a = cc[2], b = cc[1] || M.F0, k = cc[0] || M.F0;
      const delta = M.fSub(M.fMul(b, b), M.fMul(F(4), M.fMul(a, k)));
      passi.push(passo('Calcolo il delta dell\'equazione associata',
        '<div class="mate mate-blocco">Δ = ' + M.html(M.nNum(delta)) + '</div>',
        'Prima trovo dove l\'espressione vale zero, poi guardo il segno.'));
      const dn = M.fNum(delta), an = M.fNum(a);
      const concavita = an > 0 ? 'verso l\'alto' : 'verso il basso';
      passi.push(passo('Che parabola è?',
        '<div class="mate mate-blocco">a = ' + M.html(M.nNum(a)) + ' → concavità ' + concavita + '</div>',
        an > 0 ? 'La parabola "sorride": è positiva fuori dalle radici.' : 'La parabola "è triste": è positiva dentro le radici.'));
      if (dn < 0) {
        const sempre = (an > 0) === (segno === '>' || segno === '>=');
        passi.push(passo('Δ < 0: la parabola non tocca mai l\'asse x',
          '<div class="mate mate-blocco">' + (sempre ? 'Sempre vera' : 'Mai vera') + '</div>',
          sempre ? 'L\'espressione ha sempre lo stesso segno, ed è quello che ci serve.' : 'L\'espressione ha sempre il segno opposto a quello richiesto.'));
        return esito('verde', '', passi, { soluzioneTesto: sempre ? 'Va bene ogni valore di ' + v : 'Nessuna soluzione', variabile: v });
      }
      const rad = Math.sqrt(dn);
      const x1 = (-M.fNum(b) - rad) / (2 * an);
      const x2 = (-M.fNum(b) + rad) / (2 * an);
      const min = Math.min(x1, x2), max = Math.max(x1, x2);
      passi.push(passo('Trovo dove vale zero',
        '<div class="mate mate-blocco"><i>' + v + '</i><sub>1</sub> = ' + numeroIt(min) + ' &nbsp;&nbsp; <i>' + v + '</i><sub>2</sub> = ' + numeroIt(max) + '</div>', ''));
      const cerco = (segno === '>' || segno === '>=') ? 'positiva' : 'negativa';
      const fuori = (an > 0) === (cerco === 'positiva');
      const incluso = segno === '<=' || segno === '>=';
      const testoSol = dn === 0
        ? (fuori ? (incluso ? 'Va bene ogni valore di ' + v : v + ' ≠ ' + numeroIt(min)) : (incluso ? v + ' = ' + numeroIt(min) : 'Nessuna soluzione'))
        : (fuori
          ? v + ' ' + (incluso ? '≤' : '<') + ' ' + numeroIt(min) + '   oppure   ' + v + ' ' + (incluso ? '≥' : '>') + ' ' + numeroIt(max)
          : numeroIt(min) + ' ' + (incluso ? '≤' : '<') + ' ' + v + ' ' + (incluso ? '≤' : '<') + ' ' + numeroIt(max));
      passi.push(passo('Guardo il segno',
        '<div class="mate mate-blocco">' + esc(testoSol) + '</div>',
        fuori ? 'Prendo i valori FUORI dalle due radici.' : 'Prendo i valori DENTRO le due radici.',
        'Disegna mentalmente la parabola: dove sta sopra l\'asse x l\'espressione è positiva, dove sta sotto è negativa.'));
      return esito('verde', '', passi, {
        soluzioneTesto: testoSol, variabile: v,
        retta: { intervallo: { min, max, fuori, incluso } }
      });
    }

    return esito('giallo', 'So risolvere passo passo le disequazioni di primo e secondo grado. Questa è di grado ' + grado + ': posso mostrarti il grafico e gli zeri, ma non il procedimento completo.', passi);
  }

  function giraSegno(s) { return { '<': '>', '>': '<', '<=': '>=', '>=': '<=' }[s]; }
  function segnoHtml(s) { return { '<': '&lt;', '>': '&gt;', '<=': '≤', '>=': '≥' }[s]; }
  function segnoTesto(s) { return { '<': '<', '>': '>', '<=': '≤', '>=': '≥' }[s]; }

  /* ================================================================
     SISTEMI LINEARI DI DUE EQUAZIONI
     ================================================================ */

  function risolviSistema(eq1, eq2, metodo) {
    let d1, d2;
    try {
      d1 = dividiUguale(eq1); d2 = dividiUguale(eq2);
      if (!d1.aveva || !d2.aveva) throw M.errore('Servono due equazioni con il segno "=".');
    } catch (e) { return esito('rosso', e.message); }

    let P1, P2;
    try {
      P1 = M.polSottrai(M.polDaAst(M.analizza(d1.sx)), M.polDaAst(M.analizza(d1.dx)));
      P2 = M.polSottrai(M.polDaAst(M.analizza(d2.sx)), M.polDaAst(M.analizza(d2.dx)));
    } catch (e) { return esito('rosso', 'Non riesco a leggere le due equazioni. Scrivile per esempio così: x + y = 10'); }
    if (!P1 || !P2) return esito('rosso', 'So risolvere i sistemi lineari (senza potenze e senza frazioni con le incognite).');

    const vars = Array.from(new Set(M.polVariabili(P1).concat(M.polVariabili(P2)))).sort();
    if (vars.length !== 2) return esito('rosso', 'Mi servono esattamente due incognite, per esempio x e y.');
    const [vx, vy] = vars;
    const co = (P) => {
      const cy = M.polCoeff(P, vy);
      const b = cy[1] ? M.polValoreCost(cy[1]) : M.F0;
      const resto = cy[0] || M.pol();
      const cx = M.polCoeff(resto, vx);
      const a = cx[1] ? M.polValoreCost(cx[1]) : M.F0;
      const c = cx[0] ? M.polValoreCost(cx[0]) : M.F0;
      if (M.polGrado(P) > 1) return null;
      return { a, b, c: M.fNeg(c) };     // a x + b y = c
    };
    const s1 = co(P1), s2 = co(P2);
    if (!s1 || !s2) return esito('rosso', 'Questo non è un sistema di primo grado: ci sono potenze o prodotti fra le incognite.');

    const passi = [passo('Il sistema',
      '<div class="mate mate-blocco">' + rigaSistema(s1, vx, vy) + '<br>' + rigaSistema(s2, vx, vy) + '</div>',
      'Due equazioni e due incognite: cerco la coppia di numeri che le rende vere tutte e due.')];

    const det = M.fSub(M.fMul(s1.a, s2.b), M.fMul(s2.a, s1.b));
    if (M.fZero(det)) {
      const d2v = M.fSub(M.fMul(s1.c, s2.b), M.fMul(s2.c, s1.b));
      if (M.fZero(d2v)) {
        passi.push(passo('Le due equazioni dicono la stessa cosa', '<div class="mate mate-blocco">∞ soluzioni</div>',
          'Il sistema ha infinite soluzioni: ogni punto di quella retta va bene.'));
        return esito('verde', '', passi, { soluzioni: 'infinite' });
      }
      passi.push(passo('Le due rette sono parallele', '<div class="mate mate-blocco">Nessuna soluzione</div>',
        'Non esiste nessuna coppia di numeri che vada bene per tutte e due le equazioni.'));
      return esito('verde', '', passi, { soluzioni: 'nessuna' });
    }

    metodo = metodo || 'riduzione';
    if (metodo === 'sostituzione') passiSostituzione(passi, s1, s2, vx, vy);
    else if (metodo === 'confronto') passiConfronto(passi, s1, s2, vx, vy);
    else passiRiduzione(passi, s1, s2, vx, vy);

    const x = M.fDiv(M.fSub(M.fMul(s1.c, s2.b), M.fMul(s2.c, s1.b)), det);
    const y = M.fDiv(M.fSub(M.fMul(s1.a, s2.c), M.fMul(s2.a, s1.c)), det);
    passi.push(passo('Risultato',
      '<div class="mate mate-blocco"><i>' + vx + '</i> = ' + M.html(M.nNum(x)) + ' &nbsp;&nbsp;&nbsp; <i>' + vy + '</i> = ' + M.html(M.nNum(y)) + '</div>', ''));
    passi.push(passo('Controllo',
      '<div class="mate mate-blocco">' + verificaRiga(s1, x, y, vx, vy) + '<br>' + verificaRiga(s2, x, y, vx, vy) + '</div>',
      'Sostituisco i valori trovati in tutte e due le equazioni: tornano. ✅'));
    return esito('verde', '', passi, { soluzioni: { x, y }, variabili: [vx, vy] });
  }

  /** Scrive "x", "−x", "3x" senza mai mettere il brutto "1x". */
  function coefVar(f, v, primo) {
    const neg = M.fNum(f) < 0;
    const ass = neg ? M.fNeg(f) : f;
    const segno = primo ? (neg ? '−' : '') : (neg ? ' − ' : ' + ');
    return segno + (M.fUno(ass) ? '' : M.html(M.nNum(ass))) + '<i>' + v + '</i>';
  }
  function rigaSistema(s, vx, vy) {
    let t = '';
    if (!M.fZero(s.a)) t += coefVar(s.a, vx, true);
    if (!M.fZero(s.b)) t += coefVar(s.b, vy, t === '');
    return (t || '0') + ' = ' + M.html(M.nNum(s.c));
  }
  function verificaRiga(s, x, y, vx, vy) {
    const v = M.fAdd(M.fMul(s.a, x), M.fMul(s.b, y));
    return M.fTesto(v) + ' = ' + M.fTesto(s.c) + (M.fCmp(v, s.c) === 0 ? ' ✅' : ' ❌');
  }

  function passiSostituzione(passi, s1, s2, vx, vy) {
    let base = s1, altra = s2, ricavo = vy, coefRic = s1.b, altroCoef = s1.a, altroVar = vx;
    if (M.fZero(s1.b)) { ricavo = vx; coefRic = s1.a; altroCoef = s1.b; altroVar = vy; }
    if (M.fZero(coefRic)) { base = s2; altra = s1; coefRic = s2.b; altroCoef = s2.a; ricavo = vy; altroVar = vx; }
    const espr = '<span class="frazione"><span class="sopra">' + M.html(M.nNum(base.c)) + ' − ' + M.html(M.nNum(altroCoef)) + '<i>' + altroVar + '</i></span><span class="sotto">' + M.html(M.nNum(coefRic)) + '</span></span>';
    passi.push(passo('Metodo della sostituzione — passo 1',
      '<div class="mate mate-blocco"><i>' + ricavo + '</i> = ' + espr + '</div>',
      'Dalla prima equazione ricavo ' + ricavo + '.',
      'Ricavare una lettera vuol dire lasciarla da sola da una parte dell\'uguale.'));
    passi.push(passo('Passo 2: sostituisco nell\'altra equazione',
      '<div class="mate mate-blocco">' + rigaSistema(altra, vx, vy).replace('<i>' + ricavo + '</i>', '(' + espr + ')') + '</div>',
      'Al posto di ' + ricavo + ' scrivo quello che ho trovato: resta un\'equazione con una sola incognita.'));
  }
  function passiConfronto(passi, s1, s2, vx, vy) {
    passi.push(passo('Metodo del confronto',
      '<div class="mate mate-blocco">' + rigaSistema(s1, vx, vy) + '<br>' + rigaSistema(s2, vx, vy) + '</div>',
      'Ricavo la stessa lettera da tutte e due le equazioni e poi metto le due espressioni una uguale all\'altra.',
      'Se ' + vy + ' è uguale a una cosa e anche a un\'altra, allora quelle due cose sono uguali fra loro.'));
  }
  function passiRiduzione(passi, s1, s2, vx, vy) {
    const m1 = s2.a, m2 = M.fNeg(s1.a);
    passi.push(passo('Metodo della riduzione — passo 1',
      '<div class="mate mate-blocco">(' + M.fTesto(m1) + ') · [' + rigaSistema(s1, vx, vy) + ']<br>(' + M.fTesto(m2) + ') · [' + rigaSistema(s2, vx, vy) + ']</div>',
      'Moltiplico le due equazioni per due numeri scelti apposta, così sommandole la ' + vx + ' sparisce.',
      'Se davanti alla ' + vx + ' compaiono due numeri opposti (per esempio +6 e −6), sommando le due equazioni la ' + vx + ' se ne va.'));
    const b = M.fAdd(M.fMul(m1, s1.b), M.fMul(m2, s2.b));
    const c = M.fAdd(M.fMul(m1, s1.c), M.fMul(m2, s2.c));
    passi.push(passo('Passo 2: sommo le due equazioni',
      '<div class="mate mate-blocco">' + M.html(M.nProd([M.nNum(b), M.nVar(vy)])) + ' = ' + M.html(M.nNum(c)) + '</div>',
      'Resta un\'equazione con una sola incognita: la risolvo come sempre.'));
  }

  /* ================================================================
     POLINOMI: sviluppo e scomposizione
     ================================================================ */

  /** (a + b)(a − b): due binomi uguali tranne il segno di un termine. */
  function sonoConiugati(a) {
    if (a.t !== '*' || a.a.length !== 2) return false;
    const [p, q] = a.a;
    if (p.t !== '+' || q.t !== '+' || p.a.length !== 2 || q.a.length !== 2) return false;
    const t = (x) => M.testo(M.semplifica(x));
    return (t(p.a[0]) === t(q.a[0]) && t(p.a[1]) === t(M.nProd([M.nNum(M.fr(-1)), q.a[1]]))) ||
           (t(p.a[1]) === t(q.a[1]) && t(p.a[0]) === t(M.nProd([M.nNum(M.fr(-1)), q.a[0]])));
  }

  const PRODOTTI_NOTEVOLI = [
    { test: sonoConiugati,
      nome: 'Somma per differenza', regola: '(a + b)(a − b) = a² − b²',
      perche: 'I due prodotti in mezzo si annullano fra loro: resta solo la differenza dei quadrati.' },
    { test: (a) => a.t === '^' && a.e.t === 'n' && a.e.v.n === 2 && a.e.v.d === 1 && a.b.t === '+' && a.b.a.length === 2,
      nome: 'Quadrato di un binomio', regola: '(a + b)² = a² + 2ab + b²',
      perche: 'Il quadrato di una somma NON è la somma dei quadrati: in mezzo c\'è sempre il doppio prodotto.' },
    { test: (a) => a.t === '^' && a.e.t === 'n' && a.e.v.n === 3 && a.e.v.d === 1 && a.b.t === '+' && a.b.a.length === 2,
      nome: 'Cubo di un binomio', regola: '(a + b)³ = a³ + 3a²b + 3ab² + b³', perche: '' },
    { test: (a) => a.t === '*' && a.a.length === 2 && a.a.every((x) => x.t === '+' && x.a.length === 2),
      nome: 'Prodotto di due binomi', regola: '(a + b)(c + d) = ac + ad + bc + bd',
      perche: 'Ogni termine della prima parentesi moltiplica ogni termine della seconda.' }
  ];

  function espandi(str) {
    let a;
    try { a = M.analizza(str); } catch (e) { return esito('rosso', e.amichevole ? e.message : 'Non riesco a leggere questa espressione.'); }
    const P = M.polDaAst(a);
    if (!P) return esito('rosso', 'So sviluppare somme, prodotti e potenze di polinomi. Qui c\'è qualcosa di diverso (una radice, un logaritmo, una divisione per una lettera).');
    const passi = [passo('Partiamo da', M.blocco(a), '')];
    const notevole = PRODOTTI_NOTEVOLI.find((p) => p.test(a));
    if (notevole) {
      passi.push(passo('Riconosco un prodotto notevole',
        '<div class="mate mate-blocco">' + esc(notevole.regola) + '</div>',
        notevole.nome + '.', notevole.perche));
    }
    passi.push(passo('Sviluppo', M.blocco(M.polAAst(P)),
      'Ho fatto tutte le moltiplicazioni e sommato i termini simili.'));
    return esito('verde', '', passi, { risultato: M.polAAst(P) });
  }

  function scomponi(str) {
    let a;
    try { a = M.analizza(str); } catch (e) { return esito('rosso', e.amichevole ? e.message : 'Non riesco a leggere questa espressione.'); }
    const P = M.polDaAst(a);
    if (!P) return esito('rosso', 'So scomporre i polinomi. Questa espressione non è un polinomio.');
    const passi = [passo('Partiamo da', M.blocco(M.polAAst(P)), '')];
    const fattori = [];
    let R = P;

    // 1. raccoglimento a fattor comune
    const rc = raccogliComune(R);
    if (rc) {
      fattori.push(rc.fattore);
      R = rc.resto;
      passi.push(passo('Raccolgo a fattor comune',
        M.blocco(M.nProd([rc.fattore, M.polAAst(R)])),
        'Tutti i termini hanno in comune ' + M.testo(rc.fattore) + ': lo porto fuori.',
        'Raccogliere vuol dire fare il contrario della proprietà distributiva.'));
    }

    const vars = M.polVariabili(R);
    if (vars.length === 1) {
      const v = vars[0];
      const c = M.polCoeffNum(R, v);
      if (c) {
        const g = c.length - 1;
        // differenza di quadrati  a² − b²
        if (g === 2 && M.fZero(c[1]) && M.fNum(c[2]) > 0 && M.fNum(c[0]) < 0) {
          const q1 = M.radiceSemplificata(Math.round(M.fNum(c[2])));
          const q2 = M.radiceSemplificata(Math.round(-M.fNum(c[0])));
          if (q1 && q1.dentro === 1 && q2 && q2.dentro === 1 && M.fInt(c[2]) && M.fInt(c[0])) {
            const h = '<div class="mate mate-blocco">(' + (q1.fuori === 1 ? '' : q1.fuori) + '<i>' + v + '</i> − ' + q2.fuori + ')(' + (q1.fuori === 1 ? '' : q1.fuori) + '<i>' + v + '</i> + ' + q2.fuori + ')</div>';
            passi.push(passo('Differenza di due quadrati', h,
              'La regola è: a² − b² = (a − b)(a + b).',
              'Attenzione: la SOMMA di due quadrati (a² + b²) invece non si scompone.'));
            return esito('verde', '', passi, {});
          }
        }
        // trinomio di secondo grado
        if (g === 2) {
          const rad = M.radiciRazionali(c);
          if (rad.length) {
            const testo2 = rad.slice(0, 2).map((r) => '(<i>' + v + '</i> ' + (M.fNum(r) < 0 ? '+ ' + M.fTesto(M.fNeg(r)) : '− ' + nt(r)) + ')').join('');
            const capo = M.fUno(c[2]) ? '' : M.fTesto(c[2]);
            passi.push(passo('Trovo le radici e scompongo',
              '<div class="mate mate-blocco">' + capo + testo2 + '</div>',
              'Le radici sono ' + rad.slice(0, 2).map((r) => v + ' = ' + nt(r)).join(' e ') + '.',
              'Un trinomio a' + v + '² + b' + v + ' + c si può scrivere come a(' + v + ' − x₁)(' + v + ' − x₂), dove x₁ e x₂ sono le soluzioni dell\'equazione associata.'));
            return esito('verde', '', passi, {});
          }
          passi.push(passo('Non si scompone con numeri interi',
            M.blocco(M.polAAst(R)),
            'Il delta non è un quadrato perfetto: questo trinomio è irriducibile fra i numeri razionali.'));
          return esito('giallo', '', passi, {});
        }
        // grado alto: radici razionali
        if (g >= 3) {
          let resto = c.slice();
          const lineari = [];
          for (let i = 0; i < 6; i++) {
            const rr = M.radiciRazionali(resto);
            if (!rr.length) break;
            const r = rr[0];
            const d = M.divisioneNum(resto, [M.fNeg(r), M.F1]);
            if (!d) break;
            lineari.push(r); resto = d.q;
            if (resto.length - 1 <= 0) break;
          }
          if (lineari.length) {
            const t = lineari.map((r) => '(<i>' + v + '</i> ' + (M.fNum(r) < 0 ? '+ ' + M.fTesto(M.fNeg(r)) : '− ' + nt(r)) + ')').join('');
            const restoAst = M.polAAst(M.daCoeff(resto, v));
            passi.push(passo('Scompongo con il metodo di Ruffini',
              '<div class="mate mate-blocco">' + t + (resto.length - 1 > 0 ? '(' + M.html(restoAst) + ')' : '') + '</div>',
              'Ho cercato le radici fra i divisori del termine noto e ho diviso ogni volta.',
              'Se P(a) = 0, allora (' + v + ' − a) è un fattore di P.'));
            return esito('verde', '', passi, {});
          }
        }
      }
    }

    // raccoglimento parziale (4 termini)
    const parz = raccoglimentoParziale(R);
    if (parz) { passi.push(parz); return esito('verde', '', passi, {}); }

    if (fattori.length) return esito('verde', '', passi, {});
    passi.push(passo('Non riesco a scomporlo', M.blocco(M.polAAst(R)),
      'Con i metodi della scuola (raccoglimento, prodotti notevoli, Ruffini) questo polinomio non si scompone.'));
    return esito('giallo', '', passi, {});
  }

  function raccogliComune(P) {
    const chiavi = Object.keys(P);
    if (chiavi.length < 2) return null;
    // massimo comun divisore dei coefficienti
    let num = Math.abs(P[chiavi[0]].n), den = P[chiavi[0]].d;
    chiavi.forEach((k) => { num = mcdN(num, Math.abs(P[k].n)); den = den * P[k].d / mcdN(den, P[k].d); });
    const coef = M.fr(num, den);
    // lettere comuni con l'esponente più piccolo
    const primi = monVars(chiavi[0]);
    const comuni = {};
    Object.keys(primi).forEach((v) => {
      let e = primi[v];
      chiavi.forEach((k) => { const m = monVars(k); e = Math.min(e, m[v] || 0); });
      if (e > 0) comuni[v] = e;
    });
    const haLettere = Object.keys(comuni).length > 0;
    if (M.fUno(coef) && !haLettere) return null;
    const fattoriAst = [];
    if (!M.fUno(coef)) fattoriAst.push(M.nNum(coef));
    Object.keys(comuni).sort().forEach((v) => fattoriAst.push(comuni[v] === 1 ? M.nVar(v) : M.nPot(M.nVar(v), M.nNum(M.fr(comuni[v])))));
    if (!fattoriAst.length) return null;
    // divido
    let divisore = M.polCost(coef);
    Object.keys(comuni).forEach((v) => { const m = {}; m[v] = comuni[v]; const p = {}; p[monKey(m)] = M.F1; divisore = M.polProdotto(divisore, p); });
    const resto = {};
    Object.keys(P).forEach((k) => {
      const m = monVars(k);
      Object.keys(comuni).forEach((v) => { m[v] = (m[v] || 0) - comuni[v]; if (!m[v]) delete m[v]; });
      resto[monKey(m)] = M.fDiv(P[k], coef);
    });
    return { fattore: fattoriAst.length === 1 ? fattoriAst[0] : M.nProd(fattoriAst), resto };
  }
  function mcdN(a, b) { a = Math.abs(a); b = Math.abs(b); while (b) { const t = a % b; a = b; b = t; } return a || 1; }
  function monVars(k) { const m = {}; if (!k) return m; k.split('*').forEach((p) => { const [v, e] = p.split('^'); m[v] = Number(e); }); return m; }
  function monKey(m) { return Object.keys(m).filter((v) => m[v]).sort().map((v) => v + '^' + m[v]).join('*'); }

  function raccoglimentoParziale(P) {
    const chiavi = Object.keys(P);
    if (chiavi.length !== 4) return null;
    for (const coppia of [[0, 1, 2, 3], [0, 2, 1, 3], [0, 3, 1, 2]]) {
      const A = {}, B = {};
      A[chiavi[coppia[0]]] = P[chiavi[coppia[0]]]; A[chiavi[coppia[1]]] = P[chiavi[coppia[1]]];
      B[chiavi[coppia[2]]] = P[chiavi[coppia[2]]]; B[chiavi[coppia[3]]] = P[chiavi[coppia[3]]];
      const ra = raccogliComune(A), rb = raccogliComune(B);
      if (ra && rb && M.testo(M.polAAst(ra.resto)) === M.testo(M.polAAst(rb.resto))) {
        return passo('Raccoglimento parziale',
          '<div class="mate mate-blocco">(' + M.html(M.polAAst(ra.resto)) + ')(' + M.html(M.nSomma([ra.fattore, rb.fattore])) + ')</div>',
          'Raccolgo a due a due e poi raccolgo la parentesi che si ripete.',
          'Quando ci sono quattro termini conviene provare a raggrupparli a coppie.');
      }
    }
    return null;
  }

  /* ================================================================
     DERIVATE
     ================================================================ */

  const REGOLE_DERIVATA = {
    sin: (u) => ({ t: 'f', n: 'cos', a: [u] }),
    cos: (u) => M.nProd([M.nNum(M.fr(-1)), { t: 'f', n: 'sin', a: [u] }]),
    tan: (u) => M.nDiv(M.nNum(M.F1), M.nPot({ t: 'f', n: 'cos', a: [u] }, M.nNum(M.fr(2)))),
    ln: (u) => M.nDiv(M.nNum(M.F1), u),
    log: (u) => M.nDiv(M.nNum(M.F1), M.nProd([u, { t: 'f', n: 'ln', a: [M.nNum(M.fr(10))] }])),
    exp: (u) => ({ t: 'f', n: 'exp', a: [u] }),
    sqrt: (u) => M.nDiv(M.nNum(M.F1), M.nProd([M.nNum(M.fr(2)), { t: 'f', n: 'sqrt', a: [u] }])),
    atan: (u) => M.nDiv(M.nNum(M.F1), M.nSomma([M.nNum(M.F1), M.nPot(u, M.nNum(M.fr(2)))])),
    asin: (u) => M.nDiv(M.nNum(M.F1), { t: 'f', n: 'sqrt', a: [M.nSomma([M.nNum(M.F1), M.nProd([M.nNum(M.fr(-1)), M.nPot(u, M.nNum(M.fr(2)))])])] }),
    acos: (u) => M.nProd([M.nNum(M.fr(-1)), M.nDiv(M.nNum(M.F1), { t: 'f', n: 'sqrt', a: [M.nSomma([M.nNum(M.F1), M.nProd([M.nNum(M.fr(-1)), M.nPot(u, M.nNum(M.fr(2)))])])] })])
  };

  function derivata(a, v) {
    v = v || 'x';
    switch (a.t) {
      case 'n': case 'c': return M.nNum(M.F0);
      case 'v': return M.nNum(a.n === v ? M.F1 : M.F0);
      case '+': return M.nSomma(a.a.map((x) => derivata(x, v)));
      case '*': {
        const parti = a.a;
        const somma = parti.map((x, i) => {
          const altri = parti.filter((_, j) => j !== i);
          return M.nProd([derivata(x, v)].concat(altri));
        });
        return M.nSomma(somma);
      }
      case '^': {
        const b = a.b, e = a.e;
        const eCost = !contiene(e, v);
        const bCost = !contiene(b, v);
        if (eCost && !bCost) {
          const nuovo = e.t === 'n' ? M.nNum(M.fSub(e.v, M.F1)) : M.nSomma([e, M.nNum(M.fr(-1))]);
          return M.nProd([e, M.nPot(b, nuovo), derivata(b, v)]);
        }
        if (bCost && !eCost) {
          return M.nProd([M.nPot(b, e), { t: 'f', n: 'ln', a: [b] }, derivata(e, v)]);
        }
        if (bCost && eCost) return M.nNum(M.F0);
        // caso generale f(x)^g(x)
        return M.nProd([M.nPot(b, e), M.nSomma([
          M.nProd([derivata(e, v), { t: 'f', n: 'ln', a: [b] }]),
          M.nProd([e, derivata(b, v), M.nPot(b, M.nNum(M.fr(-1)))])
        ])]);
      }
      case 'f': {
        const u = a.a[0];
        const r = REGOLE_DERIVATA[a.n];
        if (!r) return null;
        const est = r(u);
        if (!est) return null;
        return M.nProd([est, derivata(u, v)]);
      }
    }
    return null;
  }

  function contiene(a, v) {
    if (!a) return false;
    if (a.t === 'v') return a.n === v;
    if (a.t === '+' || a.t === '*') return a.a.some((x) => contiene(x, v));
    if (a.t === '^') return contiene(a.b, v) || contiene(a.e, v);
    if (a.t === 'f') return contiene(a.a[0], v);
    return false;
  }

  function derivataPassi(str, v) {
    v = v || 'x';
    let a;
    try { a = M.analizza(str); } catch (e) { return esito('rosso', e.amichevole ? e.message : 'Non riesco a leggere questa funzione.'); }
    const d = derivata(a, v);
    if (!d) return esito('rosso', 'Questa funzione contiene qualcosa di cui non conosco la regola di derivazione.');
    const semplice = M.semplifica(d);
    const passi = [passo('La funzione', '<div class="mate mate-blocco">f(' + v + ') = ' + M.html(a) + '</div>', '')];

    const regole = regoleUsate(a, v);
    if (regole.length) {
      passi.push(passo('Regole che servono',
        '<ul style="margin:0;padding-left:1.2em">' + regole.map((r) => '<li>' + r + '</li>').join('') + '</ul>',
        'Queste sono le regole che uso in questo esercizio.'));
    }
    // se è una somma, mostro un pezzo alla volta: è molto più chiaro
    if (a.t === '+' && a.a.length > 1) {
      const righe = a.a.map((termine) => {
        const dt = derivata(termine, v);
        return '<div class="riga"><div class="mate">' + M.html(termine) + '</div><div class="nota">→ derivata: <span class="mate">' +
          (dt ? M.html(M.semplifica(dt)) : '?') + '</span></div></div>';
      }).join('');
      passi.push(passo('Derivo un pezzo alla volta', '<div class="cosa-faccio">' + righe + '</div>',
        'La derivata di una somma si fa termine per termine.'));
    } else {
      passi.push(passo('Applico la regola', '<div class="mate mate-blocco">' + M.html(M.semplifica(d)) + '</div>',
        'Applico la regola alla funzione.'));
    }
    passi.push(passo('Risultato', '<div class="mate mate-blocco">f\'(' + v + ') = ' + M.html(semplice) + '</div>',
      'Ho fatto i conti e messo insieme i termini simili.'));
    return esito('verde', '', passi, { risultato: semplice });
  }

  function regoleUsate(a, v) {
    const r = [];
    const vista = {};
    (function scorri(x) {
      if (!x) return;
      if (x.t === '+' && !vista.somma) { vista.somma = 1; r.push('<b>Somma:</b> la derivata di una somma è la somma delle derivate.'); }
      if (x.t === '*' && x.a.filter((y) => contiene(y, v)).length > 1 && !vista.prod) {
        vista.prod = 1; r.push('<b>Prodotto:</b> (f·g)\' = f\'·g + f·g\'.');
      }
      if (x.t === '^' && x.e.t === 'n' && contiene(x.b, v)) {
        if (!vista.pot) { vista.pot = 1; r.push('<b>Potenza:</b> la derivata di ' + v + '<sup>n</sup> è n·' + v + '<sup>n−1</sup>.'); }
        if (x.b.t !== 'v' && !vista.cat) { vista.cat = 1; r.push('<b>Funzione composta:</b> derivo la parte esterna e moltiplico per la derivata di quella interna.'); }
      }
      if (x.t === '^' && x.e.t === 'n' && x.e.v.n < 0 && !vista.quoz) { vista.quoz = 1; r.push('<b>Quoziente:</b> una divisione si può vedere come potenza con esponente negativo.'); }
      if (x.t === 'f' && !vista['f' + x.n]) {
        vista['f' + x.n] = 1;
        const nomi = { sin: 'sen(x)\' = cos(x)', cos: 'cos(x)\' = −sen(x)', tan: 'tan(x)\' = 1/cos²(x)',
          ln: 'ln(x)\' = 1/x', exp: 'e^x resta e^x', sqrt: '√x\' = 1/(2√x)' };
        if (nomi[x.n]) r.push('<b>Funzione:</b> ' + nomi[x.n] + '.');
        if (contiene(x.a[0], v) && x.a[0].t !== 'v' && !vista.cat) { vista.cat = 1; r.push('<b>Funzione composta:</b> derivo la parte esterna e moltiplico per la derivata di quella interna.'); }
      }
      if (x.a) x.a.forEach(scorri);
      if (x.b) scorri(x.b);
      if (x.e) scorri(x.e);
    })(a);
    return r;
  }

  /* ================================================================
     LIMITI
     ================================================================ */

  function limitePassi(str, verso, v) {
    v = v || 'x';
    let a;
    try { a = M.analizza(str); } catch (e) { return esito('rosso', e.amichevole ? e.message : 'Non riesco a leggere questa funzione.'); }
    const passi = [];
    const infinito = /inf|∞/i.test(String(verso));
    const x0 = infinito ? (String(verso).indexOf('-') === 0 ? -Infinity : Infinity) : Number(String(verso).replace(',', '.'));

    passi.push(passo('Il limite da calcolare',
      '<div class="mate mate-blocco">lim<sub>' + v + ' → ' + (infinito ? (x0 < 0 ? '−∞' : '+∞') : numeroIt(x0)) + '</sub> ' + M.html(a) + '</div>', ''));

    const r = M.ratDaAst(a);

    if (infinito && r) {
      const gn = M.polGrado(r.n, v), gd = M.polGrado(r.d, v);
      const cn = M.polCoeffNum(r.n, v), cd = M.polCoeffNum(r.d, v);
      passi.push(passo('Guardo i gradi',
        '<div class="mate mate-blocco">grado sopra = ' + gn + ' &nbsp;&nbsp; grado sotto = ' + gd + '</div>',
        'Quando x tende all\'infinito comandano i termini di grado più alto.'));
      if (gn < gd) {
        passi.push(passo('Risultato', '<div class="mate mate-blocco">0</div>', 'Sotto cresce più in fretta di sopra: la frazione si schiaccia verso zero.'));
        return esito('verde', '', passi, { risultato: '0' });
      }
      if (gn === gd && cn && cd) {
        const q = M.fDiv(cn[gn], cd[gd]);
        passi.push(passo('Risultato', '<div class="mate mate-blocco">' + M.html(M.nNum(q)) + '</div>',
          'Stesso grado: il limite è il rapporto fra i due coefficienti principali.'));
        return esito('verde', '', passi, { risultato: M.fTesto(q) });
      }
      const segno = (cn && cd) ? Math.sign(M.fNum(cn[gn]) / M.fNum(cd[gd])) * (x0 < 0 && (gn - gd) % 2 ? -1 : 1) : 1;
      passi.push(passo('Risultato', '<div class="mate mate-blocco">' + (segno < 0 ? '−∞' : '+∞') + '</div>',
        'Sopra cresce più in fretta: il valore diventa sempre più grande.'));
      return esito('verde', '', passi, { risultato: segno < 0 ? '-inf' : '+inf' });
    }

    if (r && isFinite(x0)) {
      const num = valutaPol(r.n, v, x0), den = valutaPol(r.d, v, x0);
      passi.push(passo('Provo a sostituire',
        '<div class="mate mate-blocco">' + (den === null || num === null ? '?' :
          '<span class="frazione"><span class="sopra">' + numeroIt(num) + '</span><span class="sotto">' + numeroIt(den) + '</span></span>') + '</div>',
        'Il primo tentativo è sempre sostituire il valore.'));
      if (den !== null && Math.abs(den) > 1e-12) {
        const val = num / den;
        passi.push(passo('Risultato', '<div class="mate mate-blocco">' + numeroIt(val) + '</div>', 'La sostituzione funziona: il limite è questo valore.'));
        return esito('verde', '', passi, { risultato: val });
      }
      if (den !== null && Math.abs(num) < 1e-12) {
        passi.push(passo('Forma indeterminata 0/0', '<div class="mate mate-blocco"><span class="frazione"><span class="sopra">0</span><span class="sotto">0</span></span></div>',
          'Devo semplificare: sopra e sotto hanno un fattore in comune.',
          'Se sostituendo viene 0/0 non vuol dire che il limite non esiste: vuol dire che devo scomporre e semplificare.'));
        const semplificata = M.semplifica(a);
        const r2 = M.ratDaAst(semplificata);
        if (r2) {
          const n2 = valutaPol(r2.n, v, x0), d2 = valutaPol(r2.d, v, x0);
          if (d2 !== null && Math.abs(d2) > 1e-12) {
            passi.push(passo('Semplifico e risostituisco',
              '<div class="mate mate-blocco">' + M.html(semplificata) + ' → ' + numeroIt(n2 / d2) + '</div>',
              'Dopo la semplificazione la sostituzione funziona.'));
            return esito('verde', '', passi, { risultato: n2 / d2 });
          }
        }
      }
      if (den !== null && Math.abs(den) < 1e-12 && Math.abs(num) > 1e-12) {
        passi.push(passo('Il denominatore va a zero', '<div class="mate mate-blocco">→ ∞</div>',
          'Il numeratore resta diverso da zero: il valore cresce senza limite (attenzione al segno, guarda da che parte ti avvicini).'));
        return esito('giallo', 'Il limite è infinito: controlla il segno avvicinandoti da destra e da sinistra.', passi, {});
      }
    }

    // stima numerica dichiarata come tale
    const stima = stimaLimite(a, v, x0);
    if (stima === null) {
      return esito('rosso', 'Non riesco a calcolare questo limite in modo affidabile senza rischiare di inventare un passaggio.', passi);
    }
    passi.push(passo('Stima numerica',
      '<div class="mate mate-blocco">≈ ' + numeroIt(stima) + '</div>',
      'Non sono riuscito a fare i passaggi simbolici, quindi mi sono avvicinato con i numeri.',
      'Questo NON è un procedimento da scrivere sul compito: è solo un controllo per capire quanto viene.'));
    return esito('giallo', 'Questo è un valore approssimato, non un procedimento: usalo solo per controllare.', passi, { risultato: stima });
  }

  function valutaPol(P, v, x) {
    const c = M.polCoeffNum(P, v);
    if (!c) return null;
    let s = 0;
    for (let i = c.length - 1; i >= 0; i--) s = s * x + M.fNum(c[i]);
    return s;
  }
  function stimaLimite(a, v, x0) {
    try {
      const punti = isFinite(x0)
        ? [x0 + 1e-4, x0 - 1e-4, x0 + 1e-6, x0 - 1e-6]
        : [x0 > 0 ? 1e5 : -1e5, x0 > 0 ? 1e6 : -1e6];
      const val = punti.map((p) => { const s = {}; s[v] = p; return M.valuta(a, s); }).filter((x) => isFinite(x));
      if (val.length < 2) return null;
      const media = val.reduce((s, x) => s + x, 0) / val.length;
      const scarto = Math.max.apply(null, val.map((x) => Math.abs(x - media)));
      if (scarto > Math.max(1e-2, Math.abs(media) * 0.01)) return null;
      return Math.abs(media) < 1e-9 ? 0 : media;
    } catch (e) { return null; }
  }

  /* ================================================================
     INTEGRALI IMMEDIATI
     ================================================================ */

  function integralePassi(str, v) {
    v = v || 'x';
    let a;
    try { a = M.analizza(str); } catch (e) { return esito('rosso', e.amichevole ? e.message : 'Non riesco a leggere questa funzione.'); }
    const passi = [passo('L\'integrale da calcolare', '<div class="mate mate-blocco">∫ ' + M.html(a) + ' d' + v + '</div>', '')];
    const termini = [];
    const P = M.polDaAst(a);
    if (P) {
      const c = M.polCoeffNum(P, v);
      if (c) {
        const nuovi = [];
        c.forEach((f, i) => {
          if (M.fZero(f)) return;
          nuovi.push(M.nProd([M.nNum(M.fDiv(f, M.fr(i + 1))), M.nPot(M.nVar(v), M.nNum(M.fr(i + 1)))]));
        });
        passi.push(passo('Regola della potenza',
          '<div class="mate mate-blocco">∫ ' + v + '<sup>n</sup> d' + v + ' = <span class="frazione"><span class="sopra">' + v + '<sup>n+1</sup></span><span class="sotto">n+1</span></span> + c</div>',
          'Aumento di uno l\'esponente e divido per il nuovo esponente.'));
        const ris = M.semplifica(M.nSomma(nuovi.length ? nuovi : [M.nNum(M.F0)]));
        passi.push(passo('Risultato', '<div class="mate mate-blocco">' + M.html(ris) + ' + c</div>',
          'Non dimenticare la costante c: le primitive sono infinite.'));
        return esito('verde', '', passi, { risultato: ris });
      }
    }
    // funzioni elementari singole
    const tabella = [
      { test: (x) => x.t === 'f' && x.n === 'sin' && x.a[0].t === 'v', ris: (x) => '−cos(' + v + ')', reg: '∫ sen(x) dx = −cos(x) + c' },
      { test: (x) => x.t === 'f' && x.n === 'cos' && x.a[0].t === 'v', ris: () => 'sen(' + v + ')', reg: '∫ cos(x) dx = sen(x) + c' },
      { test: (x) => x.t === 'f' && x.n === 'exp' && x.a[0].t === 'v', ris: () => 'e<sup>' + v + '</sup>', reg: '∫ e^x dx = e^x + c' },
      { test: (x) => x.t === '^' && x.b.t === 'v' && x.e.t === 'n' && x.e.v.n === -1 && x.e.v.d === 1, ris: () => 'ln|' + v + '|', reg: '∫ 1/x dx = ln|x| + c' }
    ];
    const t = tabella.find((r) => r.test(a));
    if (t) {
      passi.push(passo('Integrale immediato', '<div class="mate mate-blocco">' + esc(t.reg) + '</div>', 'È una delle formule da sapere a memoria.'));
      passi.push(passo('Risultato', '<div class="mate mate-blocco">' + t.ris(a) + ' + c</div>', ''));
      return esito('verde', '', passi, {});
    }
    return esito('rosso', 'So calcolare solo gli integrali immediati: polinomi, seno, coseno, e^x e 1/x. Per sostituzione o per parti serve il ragionamento, e non voglio inventarlo.', passi);
  }

  /* ================================================================
     RETTA E PARABOLA
     ================================================================ */

  function studioRetta(str) {
    const t = M.normalizzaTesto(str).replace(/^y\s*=\s*/i, '');
    let a;
    try { a = M.analizza(t); } catch (e) { return esito('rosso', 'Scrivi la retta così: y = 2x + 3'); }
    const P = M.polDaAst(a);
    if (!P) return esito('rosso', 'Scrivi la retta così: y = 2x + 3');
    const c = M.polCoeffNum(P, 'x');
    if (!c || c.length > 2) return esito('rosso', 'Questa non è una retta: c\'è una potenza della x.');
    const m = c[1] || M.F0, q = c[0] || M.F0;
    const passi = [
      passo('La retta', '<div class="mate mate-blocco">y = ' + M.html(a) + '</div>', ''),
      passo('Coefficiente angolare', '<div class="mate mate-blocco">m = ' + M.html(M.nNum(m)) + '</div>',
        'm dice quanto è ripida la retta: ' + (M.fNum(m) > 0 ? 'sale da sinistra a destra.' : M.fNum(m) < 0 ? 'scende da sinistra a destra.' : 'è orizzontale.'),
        'Il coefficiente angolare è "di quanto salgo se vado avanti di 1".'),
      passo('Intersezione con l\'asse y', '<div class="mate mate-blocco">q = ' + M.html(M.nNum(q)) + ' → punto (0; ' + M.fTesto(q) + ')</div>',
        'q è il punto in cui la retta taglia l\'asse verticale.')
    ];
    if (!M.fZero(m)) {
      const zero = M.fDiv(M.fNeg(q), m);
      passi.push(passo('Intersezione con l\'asse x', '<div class="mate mate-blocco">(' + nt(zero) + '; 0)</div>',
        'Metto y = 0 e risolvo.'));
    }
    return esito('verde', '', passi, { m, q, funzione: t });
  }

  function studioParabola(str) {
    const t = M.normalizzaTesto(str).replace(/^y\s*=\s*/i, '');
    let a;
    try { a = M.analizza(t); } catch (e) { return esito('rosso', 'Scrivi la parabola così: y = x^2 - 4x + 3'); }
    const P = M.polDaAst(a);
    const c = P ? M.polCoeffNum(P, 'x') : null;
    if (!c || c.length !== 3) return esito('rosso', 'Scrivi una parabola di questo tipo: y = x² − 4x + 3');
    const A = c[2], B = c[1] || M.F0, C = c[0] || M.F0;
    const delta = M.fSub(M.fMul(B, B), M.fMul(M.fr(4), M.fMul(A, C)));
    const xv = M.fDiv(M.fNeg(B), M.fMul(M.fr(2), A));
    const yv = M.fDiv(M.fNeg(delta), M.fMul(M.fr(4), A));
    const passi = [
      passo('La parabola', '<div class="mate mate-blocco">y = ' + M.html(a) + '</div>', ''),
      passo('a, b, c', '<div class="mate mate-blocco">a = ' + M.html(M.nNum(A)) + ' &nbsp; b = ' + M.html(M.nNum(B)) + ' &nbsp; c = ' + M.html(M.nNum(C)) + '</div>', ''),
      passo('Concavità', '<div class="mate mate-blocco">' + (M.fNum(A) > 0 ? 'verso l\'alto ∪' : 'verso il basso ∩') + '</div>',
        M.fNum(A) > 0 ? 'a è positivo: la parabola "sorride".' : 'a è negativo: la parabola è girata in giù.'),
      passo('Asse di simmetria', '<div class="mate mate-blocco">x = ' + M.html(M.nNum(xv)) + '</div>', 'È la retta verticale che divide la parabola in due parti uguali.'),
      passo('Vertice', '<div class="mate mate-blocco">V(' + nt(xv) + '; ' + nt(yv) + ')</div>',
        'Il vertice è il punto più basso (o più alto) della parabola.'),
      passo('Delta', '<div class="mate mate-blocco">Δ = ' + M.html(M.nNum(delta)) + '</div>',
        M.fNum(delta) > 0 ? 'Δ > 0: taglia l\'asse x in due punti.' : M.fNum(delta) === 0 ? 'Δ = 0: tocca l\'asse x in un punto solo.' : 'Δ < 0: non tocca mai l\'asse x.')
    ];
    const dn = M.fNum(delta);
    if (dn >= 0) {
      const r = Math.sqrt(dn), an = M.fNum(A), bn = M.fNum(B);
      passi.push(passo('Intersezioni con l\'asse x',
        '<div class="mate mate-blocco">(' + numeroIt((-bn - r) / (2 * an)) + '; 0) &nbsp;&nbsp; (' + numeroIt((-bn + r) / (2 * an)) + '; 0)</div>',
        'Metto y = 0 e risolvo l\'equazione di secondo grado.'));
    }
    passi.push(passo('Intersezione con l\'asse y', '<div class="mate mate-blocco">(0; ' + nt(C) + ')</div>', 'Metto x = 0: resta il termine noto.'));
    return esito('verde', '', passi, { a: A, b: B, c: C, vertice: { x: xv, y: yv }, delta, funzione: t });
  }

  /* ================================================================
     Utilità condivise
     ================================================================ */

  function numeroIt(x) {
    if (!isFinite(x)) return x > 0 ? '+∞' : '−∞';
    if (Math.abs(x) < 1e-12) return '0';
    const arrotondato = Math.round(x * 1e6) / 1e6;
    let s = String(arrotondato);
    if (Math.abs(arrotondato) >= 1e9 || (Math.abs(arrotondato) < 1e-4 && arrotondato !== 0)) s = arrotondato.toExponential(4);
    return s.replace('.', ',').replace('-', '−');
  }

  return {
    risolviEquazione, risolviDisequazione, risolviSistema,
    espandi, scomponi, derivata, derivataPassi, limitePassi, integralePassi,
    studioRetta, studioParabola, controllaRisultato, numeroIt, contiene,
    passaggioEquivalente, prossimaMossa, esitoFinale, coefficientiRiga
  };
})();



/* ============================================================
   08-matematica.js
   ============================================================ */
/* ==================================================================
   08-matematica.js — laboratorio di matematica (parte 1)
   Calcolatrice, operazioni in colonna, tabelline, frazioni,
   retta dei numeri, percentuali, problemi guidati.
   ================================================================== */

const STRUMENTI_MATE = [
  { gruppo: 'Tutti i giorni', voci: [
    { id: 'calc', ic: '🧮', tit: 'Calcolatrice', des: 'Anche scientifica, con i tasti grandi.' },
    { id: 'colonna', ic: '📐', tit: 'Operazioni in colonna', des: 'Numeri sempre allineati.' },
    { id: 'tabelline', ic: '✖️', tit: 'Tabelline', des: 'Tavola e allenamento.' },
    { id: 'frazioni', ic: '🍕', tit: 'Frazioni', des: 'Vedere le frazioni disegnate.' },
    { id: 'rettanum', ic: '📏', tit: 'Retta dei numeri', des: 'Positivi, negativi, addizioni.' },
    { id: 'perc', ic: '％', tit: 'Percentuali', des: 'Capire il 25% di 200.' }
  ] },
  { gruppo: 'Esercizi e problemi', voci: [
    { id: 'risolvoio', ic: '✍️', tit: 'Lo risolvo io', des: 'Scrivi tu i passaggi, io controllo.' },
    { id: 'problema', ic: '🧩', tit: 'Risolviamo il problema', des: 'Un passaggio alla volta.' },
    { id: 'risolvi', ic: '🧠', tit: 'Risolvi con me', des: 'Equazioni, disequazioni, sistemi.' },
    { id: 'alleno', ic: '🎓', tit: 'Mi alleno', des: 'Provi tu, io ti do gli aiuti.' },
    { id: 'quaderno', ic: '📓', tit: 'Il mio quaderno', des: 'Gli esercizi che ho fatto.' },
    { id: 'errori', ic: '🔁', tit: 'I miei errori', des: 'Gli sbagli da non rifare.' }
  ] },
  { gruppo: 'Superiori', voci: [
    { id: 'grafico', ic: '📈', tit: 'Grafici di funzioni', des: 'Piano cartesiano, y = ...' },
    { id: 'rettapar', ic: '📉', tit: 'Retta e parabola', des: 'm, q, vertice, intersezioni.' },
    { id: 'trigono', ic: '⭕', tit: 'Trigonometria', des: 'Circonferenza goniometrica.' },
    { id: 'triangoli', ic: '🔺', tit: 'Triangoli', des: 'Pitagora, seni, coseno.' },
    { id: 'loghi', ic: '🔟', tit: 'Logaritmi e potenze', des: 'log₂8 = 3 perché 2³ = 8.' },
    { id: 'analisi', ic: '∫', tit: 'Derivate e limiti', des: 'Analisi degli ultimi anni.' },
    { id: 'statistica', ic: '📊', tit: 'Statistica', des: 'Media, mediana, moda, grafici.' },
    { id: 'studiofun', ic: '📊', tit: 'Studiamo una funzione', des: 'La scaletta completa.' },
    { id: 'formule', ic: '📐', tit: 'Formulario', des: 'Le formule che mi servono.' }
  ] }
];

/* ------------------------------------------------------------------
   Hub della sezione
   ------------------------------------------------------------------ */

VISTE.mate = function (c, par) {
  // alcuni strumenti (per esempio il foglio da stampare) non stanno
  // nell'elenco della Home ma si raggiungono lo stesso
  if (par && STRUMENTI['mate-' + par]) {
    c.innerHTML = '';
    STRUMENTI['mate-' + par](c);
    return;
  }
  c.innerHTML = testaSezione('🔢', 'Matematica', 'Scegli lo strumento che ti serve adesso.',
    bottone('home', '🏠', 'Home', 'btn-piccolo')) +
    STRUMENTI_MATE.map((g, i) => `
      <h2 style="margin-top:${i ? '22px' : '4px'}">${esc(g.gruppo)}</h2>
      <div class="griglia-home">
        ${g.voci.map((v) => `<button type="button" class="tessera" data-vai="mate/${v.id}">
            <span class="ic" aria-hidden="true">${v.ic}</span>
            <span class="tx"><b>${esc(v.tit)}</b><span>${esc(v.des)}</span></span>
          </button>`).join('')}
      </div>`).join('');
};

/** Ogni strumento si registra qui. */
const STRUMENTI = {};

/** Intestazione comune degli strumenti di matematica. */
function testaStrumento(ic, tit, sub) {
  return testaSezione(ic, tit, sub,
    bottone('mate', '⬅', 'Matematica', 'btn-piccolo') + bottone('home', '🏠', 'Home', 'btn-piccolo'));
}

/* ------------------------------------------------------------------
   CALCOLATRICE (normale e scientifica)
   ------------------------------------------------------------------ */

const Calc = {
  espressione: '',
  memoria: 0,
  gradi: true,
  scientifica: false,
  storia: []
};

const TASTI_BASE = [
  ['C', 'canc'], ['⌫', 'back'], ['(', 'ins'], [')', 'ins'],
  ['7', 'ins'], ['8', 'ins'], ['9', 'ins'], ['÷', 'op'],
  ['4', 'ins'], ['5', 'ins'], ['6', 'ins'], ['×', 'op'],
  ['1', 'ins'], ['2', 'ins'], ['3', 'ins'], ['−', 'op'],
  ['0', 'ins'], [',', 'ins'], ['%', 'op'], ['+', 'op']
];
const TASTI_SCIENZA = [
  ['x²', 'fn'], ['x³', 'fn'], ['xʸ', 'fn'], ['√', 'fn'], ['1/x', 'fn'], ['π', 'fn'],
  ['sin', 'fn'], ['cos', 'fn'], ['tan', 'fn'], ['log', 'fn'], ['ln', 'fn'], ['e', 'fn'],
  ['asin', 'fn'], ['acos', 'fn'], ['atan', 'fn'], ['10ˣ', 'fn'], ['eˣ', 'fn'], ['n!', 'fn']
];

STRUMENTI['mate-calc'] = function (c) {
  c.innerHTML = testaStrumento('🧮', 'Calcolatrice', null) + `
    <div class="card">
      <div class="barra-btn">
        <button type="button" class="btn" data-az="calc-modo" aria-pressed="${Calc.scientifica}">
          <span aria-hidden="true">🔬</span><span>Scientifica</span></button>
        <button type="button" class="btn" data-az="calc-gradi" aria-pressed="${Calc.gradi}">
          <span aria-hidden="true">📐</span><span>${Calc.gradi ? 'GRADI' : 'RADIANTI'}</span></button>
        ${bottone('calc-leggi', '🔊', 'Leggi il calcolo')}
        ${bottone('calc-storia', '🕘', 'Cronologia')}
      </div>
      <div class="calc-display">
        <div class="espressione" id="calcEsp" aria-live="off">${esc(Calc.espressione || '0')}</div>
        <div class="risultato" id="calcRis" aria-live="polite"></div>
      </div>
      <div class="calc-tasti scientifica" id="calcTasti"></div>
      <p class="aiutino">Il numero decimale si scrive con la virgola: 2,5. La modalità attiva è
        <b>${Calc.gradi ? 'GRADI' : 'RADIANTI'}</b>.</p>
    </div>
    <div id="calcStoria"></div>`;

  disegnaTastiCalc();
};

function disegnaTastiCalc() {
  const box = $('#calcTasti');
  if (!box) return;
  const tasti = (Calc.scientifica ? TASTI_SCIENZA : []).concat(TASTI_BASE)
    .concat([['MC', 'mem'], ['MR', 'mem'], ['M+', 'mem'], ['=', 'uguale']]);
  box.innerHTML = tasti.map(([t, k]) => {
    const cl = k === 'uguale' ? 'uguale' : k === 'canc' ? 'canc' : k === 'op' ? 'op' : k === 'fn' ? 'fn' : '';
    return `<button type="button" class="${cl}" data-tasto="${esc(t)}" aria-label="${esc(nomeTasto(t))}">${esc(t)}</button>`;
  }).join('');
  if (Calc.scientifica) box.classList.add('scientifica');
}

function nomeTasto(t) {
  const n = { '÷': 'diviso', '×': 'per', '−': 'meno', '+': 'più', '=': 'uguale', 'C': 'cancella tutto',
    '⌫': 'cancella l\'ultimo', '√': 'radice quadrata', 'π': 'pi greco', 'x²': 'al quadrato',
    'x³': 'al cubo', 'xʸ': 'elevato a', '1/x': 'uno diviso', 'n!': 'fattoriale',
    '10ˣ': 'dieci elevato a', 'eˣ': 'e elevato a', ',': 'virgola', '%': 'per cento' };
  return n[t] || t;
}

function premiTastoCalc(t) {
  const agg = (s) => { Calc.espressione += s; };
  switch (t) {
    case 'C': Calc.espressione = ''; $('#calcRis').textContent = ''; break;
    case '⌫': Calc.espressione = Calc.espressione.slice(0, -1); break;
    case '=': calcolaEspressione(); return;
    case '÷': agg('÷'); break;
    case '×': agg('×'); break;
    case '−': agg('−'); break;
    case 'x²': agg('^2'); break;
    case 'x³': agg('^3'); break;
    case 'xʸ': agg('^'); break;
    case '√': agg('√('); break;
    case '1/x': Calc.espressione = '1÷(' + Calc.espressione + ')'; break;
    case 'π': agg('π'); break;
    case 'e': agg('e'); break;
    case '10ˣ': agg('10^'); break;
    case 'eˣ': agg('exp('); break;
    case 'n!': agg('!'); break;
    case 'sin': case 'cos': case 'tan': case 'asin': case 'acos': case 'atan':
    case 'log': case 'ln': agg(t + '('); break;
    case 'MC': Calc.memoria = 0; toast('Memoria azzerata'); break;
    case 'MR': agg(String(Calc.memoria).replace('.', ',')); break;
    case 'M+': {
      const v = calcolaEspressione(true);
      if (v !== null) { Calc.memoria += v; toast('In memoria: ' + Risolutore.numeroIt(Calc.memoria)); }
      break;
    }
    default: agg(t);
  }
  $('#calcEsp').textContent = Calc.espressione || '0';
}

/** Converte gradi/radianti prima di calcolare. */
function adattaGradi(a) {
  if (!Calc.gradi) return a;
  const conv = (x) => {
    if (!x || typeof x !== 'object') return x;
    if (x.t === 'f' && ['sin', 'cos', 'tan'].indexOf(x.n) >= 0) {
      return { t: 'f', n: x.n, a: [M.nProd([conv(x.a[0]), { t: 'c', n: 'pi' }, M.nNum(M.fr(1, 180))])] };
    }
    if (x.t === 'f' && ['asin', 'acos', 'atan'].indexOf(x.n) >= 0) {
      return M.nProd([{ t: 'f', n: x.n, a: [conv(x.a[0])] }, M.nNum(M.fr(180)), M.nPot({ t: 'c', n: 'pi' }, M.nNum(M.fr(-1)))]);
    }
    if (x.t === 'f') return { t: 'f', n: x.n, a: [conv(x.a[0])] };
    if (x.t === '+' || x.t === '*') return { t: x.t, a: x.a.map(conv) };
    if (x.t === '^') return { t: '^', b: conv(x.b), e: conv(x.e) };
    return x;
  };
  return conv(a);
}

function calcolaEspressione(soloValore) {
  const box = $('#calcRis');
  if (!Calc.espressione.trim()) return null;
  try {
    let testo = Calc.espressione.replace(/%/g, '/100');
    const a = M.analizza(testo);
    const v = M.valuta(adattaGradi(a), { e: Math.E });
    if (!isFinite(v)) throw M.errore('Il risultato è troppo grande, oppure hai diviso per zero.');
    const arrotondato = Math.abs(v) < 1e-12 ? 0 : Number(v.toPrecision(12));
    if (soloValore) return arrotondato;
    box.textContent = Risolutore.numeroIt(arrotondato);
    Calc.storia.unshift({ esp: Calc.espressione, ris: Risolutore.numeroIt(arrotondato) });
    if (Calc.storia.length > 30) Calc.storia.pop();
    return arrotondato;
  } catch (e) {
    if (soloValore) return null;
    box.textContent = '';
    avvisoErrore(e.amichevole ? e.message : 'Non riesco a fare questo calcolo. Controlla le parentesi.');
    return null;
  }
}

function mostraStoriaCalc() {
  $('#calcStoria').innerHTML = `<div class="card">
    <h2>🕘 Cronologia dei calcoli</h2>
    ${Calc.storia.length ? `<ul class="lista">${Calc.storia.map((s, i) => `
      <li class="voce"><div class="corpo"><b>${esc(s.esp)}</b><span class="meta">= ${esc(s.ris)}</span></div>
      <div class="azioni">${bottone('calc-riusa:' + i, '↩️', 'Riusa', 'btn-piccolo')}</div></li>`).join('')}</ul>`
      : schedaVuota('🕘', 'Non hai ancora fatto calcoli.')}
    <div class="barra-btn" style="margin-top:12px">${bottone('calc-chiudi-storia', '✕', 'Chiudi')}</div>
  </div>`;
}

/* ------------------------------------------------------------------
   OPERAZIONI IN COLONNA
   ------------------------------------------------------------------ */

const Colonna = { a: '', b: '', op: '+', mostraRiporti: true };

STRUMENTI['mate-colonna'] = function (c) {
  c.innerHTML = testaStrumento('📐', 'Operazioni in colonna',
    'Scrivi i due numeri: li incolonno io. Poi provi tu a fare il conto.') + `
    <div class="card">
      <div class="riga-campi">
        <div><label class="etichetta" for="colA">Primo numero</label>
          <input class="campo" id="colA" type="text" inputmode="numeric" value="${esc(Colonna.a)}"></div>
        <div><label class="etichetta" for="colOp">Operazione</label>
          <select class="campo" id="colOp">
            <option value="+"${Colonna.op === '+' ? ' selected' : ''}>+ addizione</option>
            <option value="−"${Colonna.op === '−' ? ' selected' : ''}>− sottrazione</option>
            <option value="×"${Colonna.op === '×' ? ' selected' : ''}>× moltiplicazione</option>
          </select></div>
        <div><label class="etichetta" for="colB">Secondo numero</label>
          <input class="campo" id="colB" type="text" inputmode="numeric" value="${esc(Colonna.b)}"></div>
      </div>
      <div class="barra-btn" style="margin-top:12px">
        ${bottone('col-incolonna', '📐', 'Incolonna', 'btn-primario btn-grande')}
      </div>
    </div>
    <div id="colGriglia"></div>`;
};

function incolonna() {
  Colonna.a = $('#colA').value.trim().replace(/[^\d]/g, '');
  Colonna.b = $('#colB').value.trim().replace(/[^\d]/g, '');
  Colonna.op = $('#colOp').value;
  const box = $('#colGriglia');
  if (!Colonna.a || !Colonna.b) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">✏️</span><p>Scrivi tutti e due i numeri (solo cifre, senza virgola).</p></div>`;
    return;
  }
  if (Colonna.op === '−' && Number(Colonna.a) < Number(Colonna.b)) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">🔄</span>
      <p>Il primo numero è più piccolo del secondo: in colonna la sottrazione si fa mettendo sopra il numero più grande.
      Prova a scambiarli (il risultato sarà negativo).</p></div>`;
    return;
  }
  const larghezza = Math.max(Colonna.a.length, Colonna.b.length) +
    (Colonna.op === '×' ? Colonna.b.length : 1);
  const NOMI = ['Unità', 'Decine', 'Centinaia', 'Migliaia', 'Dec. di migliaia', 'Centinaia di migliaia', 'Milioni'];

  const cella = (ch, cl) => `<div class="colonna-cella ${cl || ''}">${ch === null ? '' : esc(ch)}</div>`;
  const rigaNum = (n, segno) => {
    const cifre = String(n).padStart(larghezza, ' ').split('');
    return '<div class="colonna-riga">' +
      (segno ? cella(segno, 'segno') : cella('', 'segno')) +
      cifre.map((ch) => cella(ch === ' ' ? null : ch)).join('') + '</div>';
  };

  const intest = '<div class="colonna-riga colonna-intestazioni">' + cella('', 'segno') +
    Array.from({ length: larghezza }, (_, i) => cella(NOMI[larghezza - 1 - i] || '', '')).join('') + '</div>';

  const riporti = Colonna.mostraRiporti
    ? '<div class="colonna-riga">' + cella('', 'segno') +
      Array.from({ length: larghezza }, (_, i) =>
        `<div class="colonna-cella riporto"><input type="text" inputmode="numeric" maxlength="1" aria-label="riporto colonna ${larghezza - i}"></div>`).join('') + '</div>'
    : '';

  const risultato = '<div class="colonna-riga linea">' + cella('', 'segno') +
    Array.from({ length: larghezza }, (_, i) =>
      `<div class="colonna-cella"><input type="text" inputmode="numeric" maxlength="1" data-ris="${larghezza - 1 - i}" aria-label="risultato colonna ${larghezza - i}"></div>`).join('') + '</div>';

  box.innerHTML = `<div class="card">
      <div style="overflow-x:auto"><div class="colonna-griglia">
        ${intest}${riporti}${rigaNum(Colonna.a)}${rigaNum(Colonna.b, Colonna.op)}${risultato}
      </div></div>
      <div class="barra-btn" style="margin-top:16px">
        ${bottone('col-controlla', '✅', 'Controlla il mio risultato', 'btn-primario btn-grande')}
        ${bottone('col-aiuto', '💡', 'Aiutami con la prima colonna')}
        ${bottone('col-mostra', '👀', 'Mostrami il risultato')}
        ${bottone('col-pulisci', '🧽', 'Cancella quello che ho scritto')}
      </div>
      <p class="aiutino">Scrivi una cifra per casella, partendo da destra. Le caselle rosse in alto sono per i riporti.</p>
      <div id="colEsito"></div>
    </div>`;
}

function risultatoColonna() {
  const a = Number(Colonna.a), b = Number(Colonna.b);
  return Colonna.op === '+' ? a + b : Colonna.op === '−' ? a - b : a * b;
}

function controllaColonna() {
  const giusto = String(risultatoColonna());
  const celle = $$('[data-ris]').sort((x, y) => Number(y.dataset.ris) - Number(x.dataset.ris));
  const scritto = celle.map((n) => (n.value || '').trim()).join('').replace(/^0+(?=\d)/, '');
  const box = $('#colEsito');
  if (!scritto) { box.innerHTML = `<div class="avviso avviso-att"><span class="ic">✏️</span><p>Scrivi prima il tuo risultato nelle caselle.</p></div>`; return; }
  if (scritto === giusto) {
    box.innerHTML = `<div class="avviso avviso-ok"><span class="ic">✅</span><p><b>Giusto!</b> ${esc(Colonna.a)} ${esc(Colonna.op)} ${esc(Colonna.b)} = ${esc(giusto)}</p></div>`;
  } else {
    const quante = Math.abs(scritto.length - giusto.length);
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">🤔</span>
      <p><b>Non ci siamo ancora.</b> ${quante ? 'Il risultato ha ' + giusto.length + ' cifre, tu ne hai scritte ' + scritto.length + '.' : 'Riguarda le colonne da destra verso sinistra: hai messo tutti i riporti?'}</p></div>`;
  }
}

function aiutoColonna() {
  const a = Colonna.a, b = Colonna.b;
  const ua = Number(a[a.length - 1]), ub = Number(b[b.length - 1]);
  let testo;
  if (Colonna.op === '+') {
    const s = ua + ub;
    testo = 'Comincia dalle unità: ' + ua + ' + ' + ub + ' = ' + s + '. ' +
      (s > 9 ? 'Scrivi ' + (s % 10) + ' e porti 1 sopra la colonna delle decine.' : 'Scrivi ' + s + ', non c\'è riporto.');
  } else if (Colonna.op === '−') {
    testo = ua >= ub
      ? 'Comincia dalle unità: ' + ua + ' − ' + ub + ' = ' + (ua - ub) + '.'
      : 'Comincia dalle unità: ' + ua + ' è più piccolo di ' + ub + ', quindi devi prendere in prestito una decina: ' + (ua + 10) + ' − ' + ub + ' = ' + (ua + 10 - ub) + '.';
  } else {
    const s = ua * ub;
    testo = 'Comincia da: ' + ub + ' × ' + ua + ' = ' + s + '. ' + (s > 9 ? 'Scrivi ' + (s % 10) + ' e porti ' + Math.floor(s / 10) + '.' : 'Scrivi ' + s + '.');
  }
  $('#colEsito').innerHTML = `<div class="avviso"><span class="ic">💡</span><p>${esc(testo)}</p></div>`;
}

/* ------------------------------------------------------------------
   TABELLINE
   ------------------------------------------------------------------ */

const Tab = { riga: 0, col: 0, nascondi: false, sfida: null, punti: 0 };

STRUMENTI['mate-tabelline'] = function (c) {
  c.innerHTML = testaStrumento('✖️', 'Tabelline', 'Tocca un numero per vedere riga e colonna.') + `
    <div class="card">
      <div class="barra-btn">
        <button type="button" class="btn" data-az="tab-nascondi" aria-pressed="${Tab.nascondi}">
          <span aria-hidden="true">🙈</span><span>Nascondi i risultati</span></button>
        ${bottone('tab-allena', '🎯', 'Allenamento', 'btn-primario')}
      </div>
      <div class="tabella-scroll" id="tabBox"></div>
      <p class="aiutino" id="tabInfo" aria-live="polite"></p>
    </div>
    <div id="tabSfida"></div>`;
  disegnaPitagorica();
};

function disegnaPitagorica() {
  const box = $('#tabBox');
  if (!box) return;
  let h = '<table class="pitagorica' + (Tab.nascondi ? ' nascondi' : '') + '"><caption class="sr-only">Tavola pitagorica da 1 a 10</caption><tr><th>×</th>';
  for (let j = 1; j <= 10; j++) h += '<th>' + j + '</th>';
  h += '</tr>';
  for (let i = 1; i <= 10; i++) {
    h += '<tr><th>' + i + '</th>';
    for (let j = 1; j <= 10; j++) {
      const evid = (Tab.riga === i || Tab.col === j);
      const scelto = (Tab.riga === i && Tab.col === j);
      h += `<td class="${scelto ? 'scelto' : evid ? 'evid' : ''}"><button type="button" data-tab="${i},${j}"
        aria-label="${i} per ${j} uguale ${i * j}">${i * j}</button></td>`;
    }
    h += '</tr>';
  }
  box.innerHTML = h + '</table>';
}

function toccaTabellina(i, j) {
  Tab.riga = i; Tab.col = j;
  disegnaPitagorica();
  const info = $('#tabInfo');
  if (info) info.textContent = i + ' × ' + j + ' = ' + (i * j) + '   (e anche ' + j + ' × ' + i + ' = ' + (i * j) + ')';
  const cella = $('[data-tab="' + i + ',' + j + '"]');
  if (cella) cella.closest('td').classList.add('svelato');
}

function nuovaSfidaTabelline() {
  const a = 2 + Math.floor(Math.random() * 9);
  const b = 2 + Math.floor(Math.random() * 9);
  Tab.sfida = { a, b };
  $('#tabSfida').innerHTML = `<div class="card">
    <h2>🎯 Allenamento</h2>
    <div class="mate mate-blocco" style="font-size:2.2em">${a} × ${b} = ?</div>
    <label class="etichetta" for="sfRisp">La mia risposta</label>
    <input class="campo" id="sfRisp" type="number" inputmode="numeric" autocomplete="off">
    <div class="barra-btn" style="margin-top:12px">
      ${bottone('tab-rispondi', '✔️', 'Controlla', 'btn-primario btn-grande')}
      ${bottone('tab-salta', '➡', 'Un\'altra')}
      ${bottone('tab-aiuto', '💡', 'Aiutami')}
    </div>
    <div id="sfEsito"></div>
  </div>`;
  const inp = $('#sfRisp');
  inp.focus();
  inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') rispondiTabellina(); });
}

function rispondiTabellina() {
  if (!Tab.sfida) return;
  const v = Number($('#sfRisp').value);
  const giusto = Tab.sfida.a * Tab.sfida.b;
  const box = $('#sfEsito');
  if (v === giusto) {
    Tab.punti++;
    box.innerHTML = `<div class="avviso avviso-ok"><span class="ic">✅</span><p><b>Corretto!</b> ${Tab.sfida.a} × ${Tab.sfida.b} = ${giusto}</p></div>`;
    setTimeout(nuovaSfidaTabelline, 1100);
  } else {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">🤔</span>
      <p><b>Riprova.</b> Nessun problema: prova a contare ${Tab.sfida.a} volte ${Tab.sfida.b}, oppure guarda la tavola qui sopra.</p></div>`;
  }
}

function aiutoTabellina() {
  if (!Tab.sfida) return;
  const { a, b } = Tab.sfida;
  const passi = [];
  for (let i = 1; i <= b; i++) passi.push(a * i);
  $('#sfEsito').innerHTML = `<div class="avviso"><span class="ic">💡</span>
    <p>Conta di ${a} in ${a}: ${esc(passi.join(' → '))}.<br>
    Oppure: ${a} × ${b} è come ${b} × ${a}: a volte è più facile al contrario.</p></div>`;
}

/* ------------------------------------------------------------------
   FRAZIONI
   ------------------------------------------------------------------ */

STRUMENTI['mate-frazioni'] = function (c) {
  c.innerHTML = testaStrumento('🍕', 'Frazioni', 'Scrivi una frazione e la vedi disegnata.') + `
    <div class="card">
      <div class="riga-campi">
        <div><label class="etichetta" for="frA">Prima frazione</label>
          <div style="display:flex;gap:6px;align-items:center">
            <input class="campo" id="frA1" type="number" value="3" aria-label="numeratore prima frazione">
            <span style="font-size:1.6em">/</span>
            <input class="campo" id="frA2" type="number" value="4" aria-label="denominatore prima frazione">
          </div></div>
        <div><label class="etichetta" for="frB">Seconda frazione (per confrontare)</label>
          <div style="display:flex;gap:6px;align-items:center">
            <input class="campo" id="frB1" type="number" value="1" aria-label="numeratore seconda frazione">
            <span style="font-size:1.6em">/</span>
            <input class="campo" id="frB2" type="number" value="2" aria-label="denominatore seconda frazione">
          </div></div>
      </div>
      <div class="barra-btn" style="margin-top:12px">${bottone('fr-disegna', '🎨', 'Disegna e confronta', 'btn-primario btn-grande')}</div>
    </div>
    <div id="frRis"></div>`;
  disegnaFrazioni();
};

function disegnaFrazioni() {
  const n1 = Number($('#frA1').value), d1 = Number($('#frA2').value);
  const n2 = Number($('#frB1').value), d2 = Number($('#frB2').value);
  const box = $('#frRis');
  if (!d1 || !d2) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">⚠️</span>
      <p>Il denominatore (il numero sotto) non può essere zero: non si può dividere una cosa in zero parti.</p></div>`;
    return;
  }
  box.innerHTML = `<div class="card">
      <h2>La prima frazione</h2>
      ${bloccoFrazione(n1, d1)}
    </div>
    <div class="card">
      <h2>La seconda frazione</h2>
      ${bloccoFrazione(n2, d2)}
    </div>
    <div class="card">
      <h2>Confronto</h2>
      <div class="mate mate-blocco">${M.html(M.nNum(M.fr(n1, d1)))} ${confrontoSimbolo(n1 / d1, n2 / d2)} ${M.html(M.nNum(M.fr(n2, d2)))}</div>
      <p>${esc(spiegaConfronto(n1, d1, n2, d2))}</p>
      <div class="barra-btn">${bottone('fr-leggi', '🔊', 'Leggi le frazioni')}</div>
    </div>`;
}

function bloccoFrazione(n, d) {
  const intere = Math.floor(Math.abs(n) / d);
  const resto = Math.abs(n) % d;
  const torte = [];
  for (let k = 0; k < Math.max(1, intere + (resto ? 1 : 0)); k++) {
    const piene = k < intere ? d : resto;
    torte.push(tortaSvg(piene, d));
  }
  return `<div class="mate mate-blocco">${M.html(M.nNum(M.fr(n, d)))}</div>
    <div class="torta">${torte.join('')}</div>
    <div class="barra-frazione" style="margin:14px auto">
      ${Array.from({ length: d }, (_, i) => `<div class="fetta${i < (Math.abs(n) % d || (Math.abs(n) >= d ? d : 0)) ? ' piena' : ''}"></div>`).join('')}
    </div>
    <p class="aiutino">Sotto c'è in quante parti ho diviso l'intero (${d}), sopra quante parti prendo (${n}).</p>`;
}

function tortaSvg(piene, tot) {
  const r = 52, cx = 60, cy = 60;
  let s = `<svg width="120" height="120" viewBox="0 0 120 120" role="img" aria-label="${piene} parti colorate su ${tot}">`;
  for (let i = 0; i < tot; i++) {
    const a1 = (i / tot) * 2 * Math.PI - Math.PI / 2;
    const a2 = ((i + 1) / tot) * 2 * Math.PI - Math.PI / 2;
    const x1 = cx + r * Math.cos(a1), y1 = cy + r * Math.sin(a1);
    const x2 = cx + r * Math.cos(a2), y2 = cy + r * Math.sin(a2);
    const grande = (a2 - a1) > Math.PI ? 1 : 0;
    const d = tot === 1
      ? `M ${cx} ${cy - r} A ${r} ${r} 0 1 1 ${cx - 0.01} ${cy - r} Z`
      : `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 ${grande} 1 ${x2} ${y2} Z`;
    s += `<path d="${d}" fill="${i < piene ? 'var(--primario)' : 'transparent'}" stroke="currentColor" stroke-width="2"/>`;
  }
  return s + '</svg>';
}

function confrontoSimbolo(a, b) { return a > b ? '&gt;' : a < b ? '&lt;' : '='; }
function spiegaConfronto(n1, d1, n2, d2) {
  const c = n1 * d2, e = n2 * d1;
  const mcm = d1 * d2;
  if (c === e) return 'Sono la stessa quantità, anche se scritte in modo diverso.';
  return 'Per confrontarle le riduco allo stesso denominatore (' + mcm + '): diventano ' +
    (n1 * d2) + '/' + mcm + ' e ' + (n2 * d1) + '/' + mcm +
    '. Adesso basta guardare i numeri sopra: ' + (c > e ? c + ' è più di ' + e : c + ' è meno di ' + e) + '.';
}

/* ------------------------------------------------------------------
   RETTA DEI NUMERI
   ------------------------------------------------------------------ */

const Retta = { da: -10, a: 10, punto: 0, secondo: 5, op: '+' };

STRUMENTI['mate-rettanum'] = function (c) {
  c.innerHTML = testaStrumento('📏', 'Retta dei numeri',
    'I numeri negativi sono a sinistra dello zero, i positivi a destra.') + `
    <div class="card">
      <div class="riga-campi">
        <div><label class="etichetta" for="rtA">Parto da</label>
          <input class="campo" id="rtA" type="number" value="${Retta.punto}"></div>
        <div><label class="etichetta" for="rtOp">Operazione</label>
          <select class="campo" id="rtOp"><option value="+">+ vado a destra</option><option value="-">− vado a sinistra</option></select></div>
        <div><label class="etichetta" for="rtB">Di quanto</label>
          <input class="campo" id="rtB" type="number" value="${Retta.secondo}"></div>
      </div>
      <div class="barra-btn" style="margin-top:12px">${bottone('rt-mostra', '▶', 'Fammi vedere', 'btn-primario btn-grande')}</div>
    </div>
    <div class="card"><div class="tela-wrap"><canvas class="tela" id="telaRetta" height="220"></canvas></div>
      <p id="rtSpiega" class="aiutino" aria-live="polite"></p></div>`;
  setTimeout(disegnaRettaNumeri, 30);
};

function disegnaRettaNumeri(mostraSalto) {
  const cv = $('#telaRetta');
  if (!cv) return;
  const larg = cv.parentElement.clientWidth - 20;
  cv.width = Math.max(320, larg) * (window.devicePixelRatio || 1);
  cv.height = 220 * (window.devicePixelRatio || 1);
  cv.style.height = '220px';
  const g = cv.getContext('2d');
  const S = window.devicePixelRatio || 1;
  g.setTransform(S, 0, 0, S, 0, 0);
  const W = cv.width / S, H = cv.height / S;
  const stile = getComputedStyle(document.documentElement);
  const colTesto = stile.getPropertyValue('--testo').trim() || '#000';
  const colPrim = stile.getPropertyValue('--primario').trim() || '#1d4e6f';

  const a = Number($('#rtA').value) || 0;
  const b = Number($('#rtB').value) || 0;
  const op = $('#rtOp').value;
  const ris = op === '+' ? a + b : a - b;
  const min = Math.min(a, ris, 0) - 3, max = Math.max(a, ris, 0) + 3;
  const px = (v) => 40 + (v - min) / (max - min) * (W - 80);
  const y = H / 2 + 20;

  g.clearRect(0, 0, W, H);
  g.strokeStyle = colTesto; g.fillStyle = colTesto; g.lineWidth = 2;
  g.beginPath(); g.moveTo(20, y); g.lineTo(W - 20, y); g.stroke();
  g.beginPath(); g.moveTo(W - 20, y); g.lineTo(W - 32, y - 6); g.lineTo(W - 32, y + 6); g.fill();

  g.font = '14px system-ui, sans-serif'; g.textAlign = 'center';
  const passo = Math.max(1, Math.round((max - min) / 20));
  for (let v = Math.ceil(min); v <= max; v += passo) {
    const x = px(v);
    g.beginPath(); g.moveTo(x, y - (v === 0 ? 12 : 7)); g.lineTo(x, y + (v === 0 ? 12 : 7)); g.stroke();
    g.fillText(String(v).replace('-', '−'), x, y + 30);
  }

  if (mostraSalto) {
    g.strokeStyle = colPrim; g.lineWidth = 3;
    g.beginPath();
    const x1 = px(a), x2 = px(ris);
    g.moveTo(x1, y - 12);
    g.quadraticCurveTo((x1 + x2) / 2, y - 78, x2, y - 12);
    g.stroke();
    g.fillStyle = colPrim;
    [[x1, a], [x2, ris]].forEach(([x, v], i) => {
      g.beginPath(); g.arc(x, y, 8, 0, 7); g.fill();
      g.font = 'bold 16px system-ui, sans-serif';
      g.fillText(i === 0 ? 'parto' : 'arrivo', x, y - 18 + (i ? -6 : 0));
    });
    $('#rtSpiega').textContent = 'Parto da ' + a + ', ' + (op === '+' ? 'vado a destra' : 'vado a sinistra') +
      ' di ' + Math.abs(b) + ' passi e arrivo a ' + ris + '.  ' + a + ' ' + op + ' ' + b + ' = ' + ris;
  }
}

/* ------------------------------------------------------------------
   PERCENTUALI
   ------------------------------------------------------------------ */

STRUMENTI['mate-perc'] = function (c) {
  c.innerHTML = testaStrumento('％', 'Percentuali', 'Per cento vuol dire "ogni cento".') + `
    <div class="card">
      <div class="riga-campi">
        <div><label class="etichetta" for="pcP">Percentuale</label>
          <input class="campo" id="pcP" type="number" value="25"></div>
        <div><label class="etichetta" for="pcD">di questo numero</label>
          <input class="campo" id="pcD" type="number" value="200"></div>
      </div>
      <div class="barra-btn" style="margin-top:12px">
        ${bottone('pc-calcola', '▶', 'Fammi vedere', 'btn-primario btn-grande')}
        ${bottone('pc-passaggi', '👣', 'Mostra i passaggi')}
      </div>
    </div>
    <div id="pcRis"></div>`;
  calcolaPercentuale(false);
};

function calcolaPercentuale(conPassaggi) {
  const p = Number($('#pcP').value) || 0;
  const d = Number($('#pcD').value) || 0;
  const r = d * p / 100;
  $('#pcRis').innerHTML = `<div class="card">
      <div class="perc-blocchi">
        <div class="blocco"><div class="et">Dato</div><div class="val">${Risolutore.numeroIt(d)}</div></div>
        <div class="blocco"><div class="et">Percentuale</div><div class="val">${Risolutore.numeroIt(p)}%</div></div>
        <div class="blocco"><div class="et">Operazione</div><div class="val" style="font-size:1.1em">${Risolutore.numeroIt(d)} × ${Risolutore.numeroIt(p)} ÷ 100</div></div>
        <div class="blocco" style="border-color:var(--ok);background:var(--ok-chiaro)"><div class="et">Risultato</div><div class="val">${Risolutore.numeroIt(r)}</div></div>
      </div>
      <div class="perc-barra" style="margin-top:16px" role="img" aria-label="${p} per cento riempito">
        <div class="riempi" style="width:${limita(p, 0, 100)}%"></div>
      </div>
      ${conPassaggi ? `
      <div style="margin-top:16px">
        <div class="passo"><div class="numero">Passo 1</div>
          <div class="mate mate-blocco">${Risolutore.numeroIt(p)}% = ${M.html(M.nNum(M.fr(p, 100)))}</div>
          <div class="spiega">"Per cento" vuol dire "diviso cento": ${p}% è ${p} parti su 100.</div></div>
        <div class="passo"><div class="numero">Passo 2</div>
          <div class="mate mate-blocco">${M.html(M.nNum(M.fr(p, 100)))} × ${Risolutore.numeroIt(d)}</div>
          <div class="spiega">"Di" vuol dire moltiplicare.</div></div>
        <div class="passo controllo"><div class="numero">Risultato</div>
          <div class="mate mate-blocco">${Risolutore.numeroIt(r)}</div>
          <div class="spiega">${esc('Controllo: se ' + p + '% è ' + Risolutore.numeroIt(r) + ', allora il 100% deve essere ' + Risolutore.numeroIt(d) + '.')}</div></div>
      </div>` : ''}
    </div>`;
}

/* ------------------------------------------------------------------
   PROBLEMI GUIDATI
   ------------------------------------------------------------------ */

const Problema = { testo: '', so: '', trovare: '', op: '', calcolo: '', risposta: '', passo: 1 };

STRUMENTI['mate-problema'] = function (c) {
  c.innerHTML = testaStrumento('🧩', 'Risolviamo il problema',
    'Non ti do subito il risultato: lo troviamo insieme, un pezzo alla volta.');
  disegnaProblema(c);
};

function disegnaProblema(c) {
  c = c || $('#vista');
  let box = $('#boxProblema');
  if (!box) {
    box = document.createElement('div');
    box.id = 'boxProblema';
    c.appendChild(box);
  }
  const p = Problema;
  const passi = [
    { n: 1, tit: '1. LEGGI', html: `
      <textarea class="area" id="pbTesto" rows="4" spellcheck="true"
        placeholder="Scrivi o incolla qui il testo del problema.">${esc(p.testo)}</textarea>
      <div class="barra-btn" style="margin-top:10px">
        ${bottone('pb-leggi', '🔊', 'Leggimelo')}
        ${bottone('pb-avanti:2', '➡', 'Ho letto, vado avanti', 'btn-primario')}
      </div>` },
    { n: 2, tit: '2. COSA SO?', html: `
      <p class="aiutino">Scrivi i numeri e le informazioni che il problema ti dà.</p>
      <textarea class="area" id="pbSo" rows="3" spellcheck="true" placeholder="Per esempio: Marco ha 24 figurine. Ne regala 7.">${esc(p.so)}</textarea>
      <div class="barra-btn" style="margin-top:10px">${bottone('pb-avanti:3', '➡', 'Avanti', 'btn-primario')}${bottone('pb-indietro:1', '⬅', 'Indietro')}</div>` },
    { n: 3, tit: '3. COSA DEVO TROVARE?', html: `
      <p class="aiutino">Di solito è l'ultima frase, quella con il punto di domanda.</p>
      <textarea class="area" id="pbTrovare" rows="2" spellcheck="true" placeholder="Per esempio: quante figurine gli rimangono.">${esc(p.trovare)}</textarea>
      <div class="barra-btn" style="margin-top:10px">${bottone('pb-avanti:4', '➡', 'Avanti', 'btn-primario')}${bottone('pb-indietro:2', '⬅', 'Indietro')}</div>` },
    { n: 4, tit: '4. QUALE OPERAZIONE SERVE?', html: `
      <div class="barra-btn">
        <button type="button" class="btn btn-grande" data-az="pb-op:+" aria-pressed="${p.op === '+'}">➕ Addizione</button>
        <button type="button" class="btn btn-grande" data-az="pb-op:-" aria-pressed="${p.op === '-'}">➖ Sottrazione</button>
        <button type="button" class="btn btn-grande" data-az="pb-op:*" aria-pressed="${p.op === '*'}">✖️ Moltiplicazione</button>
        <button type="button" class="btn btn-grande" data-az="pb-op:/" aria-pressed="${p.op === '/'}">➗ Divisione</button>
        <button type="button" class="btn btn-grande btn-attenzione" data-az="pb-op:?" aria-pressed="${p.op === '?'}">❓ Non lo so</button>
      </div>
      <div id="pbAiutoOp"></div>
      <div class="barra-btn" style="margin-top:10px">${bottone('pb-avanti:5', '➡', 'Avanti', 'btn-primario')}${bottone('pb-indietro:3', '⬅', 'Indietro')}</div>` },
    { n: 5, tit: '5. ESEGUI IL CALCOLO', html: `
      <p class="aiutino">Scrivi il calcolo, per esempio: 24 - 7</p>
      <input class="campo" id="pbCalcolo" type="text" value="${esc(p.calcolo)}" inputmode="text">
      <div class="barra-btn" style="margin-top:10px">
        ${bottone('pb-calcola', '🧮', 'Fai il conto', 'btn-primario')}
        ${bottone('pb-avanti:6', '➡', 'Avanti')}
        ${bottone('pb-indietro:4', '⬅', 'Indietro')}
      </div>
      <div id="pbRisCalcolo"></div>` },
    { n: 6, tit: '6. SCRIVI LA RISPOSTA', html: `
      <p class="aiutino">La risposta è una frase, non solo un numero.</p>
      <textarea class="area" id="pbRisposta" rows="2" spellcheck="true"
        placeholder="Per esempio: A Marco rimangono 17 figurine.">${esc(p.risposta)}</textarea>
      <div class="barra-btn" style="margin-top:10px">
        ${bottone('pb-salva', '💾', 'Salva nel quaderno', 'btn-primario')}
        ${bottone('pb-nuovo', '🆕', 'Nuovo problema')}
        ${bottone('pb-indietro:5', '⬅', 'Indietro')}
      </div>` }
  ];
  const corrente = passi.find((x) => x.n === p.passo) || passi[0];
  box.innerHTML = `
    <p class="frase-contatore">Passo ${p.passo} di 6</p>
    <div class="card">
      <h2>${esc(corrente.tit)}</h2>
      ${corrente.html}
    </div>`;
}

function problemaLeggiCampi() {
  const g = (id) => { const n = $(id); return n ? n.value : null; };
  const t = g('#pbTesto'); if (t !== null) Problema.testo = t;
  const s = g('#pbSo'); if (s !== null) Problema.so = s;
  const tr = g('#pbTrovare'); if (tr !== null) Problema.trovare = tr;
  const cl = g('#pbCalcolo'); if (cl !== null) Problema.calcolo = cl;
  const r = g('#pbRisposta'); if (r !== null) Problema.risposta = r;
}

const AIUTO_OPERAZIONE = {
  '+': 'L\'addizione serve quando si mette insieme, si aggiunge, si guadagna, si arriva in più.',
  '-': 'La sottrazione serve quando si toglie, si regala, si perde, si spende, oppure quando si chiede "quanti in più / quanti in meno".',
  '*': 'La moltiplicazione serve quando la stessa quantità si ripete più volte (per esempio 6 scatole da 12 penne).',
  '/': 'La divisione serve quando si distribuisce in parti uguali, oppure quando si chiede "quante volte ci sta".',
  '?': 'Nessun problema. Rileggi la domanda e chiediti: la quantità alla fine è PIÙ GRANDE o PIÙ PICCOLA di quella di partenza? Se è più piccola di solito serve una sottrazione o una divisione; se è più grande, un\'addizione o una moltiplicazione.'
};

function problemaOperazione(op) {
  Problema.op = op;
  const box = $('#pbAiutoOp');
  if (box) box.innerHTML = `<div class="avviso"><span class="ic">💡</span><p>${esc(AIUTO_OPERAZIONE[op] || '')}</p></div>`;
  $$('[data-az^="pb-op:"]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.az === 'pb-op:' + op)));
}

function problemaCalcola() {
  problemaLeggiCampi();
  const box = $('#pbRisCalcolo');
  try {
    const v = M.valuta(M.analizza(Problema.calcolo), {});
    box.innerHTML = `<div class="avviso avviso-ok"><span class="ic">🧮</span>
      <p>Il calcolo <b>${esc(Problema.calcolo)}</b> fa <b>${esc(Risolutore.numeroIt(Number(v.toPrecision(12))))}</b>.
      Adesso però la risposta scrivila con parole tue.</p></div>`;
  } catch (e) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">✏️</span>
      <p>Non riesco a leggere questo calcolo. Scrivilo con i numeri e i segni, per esempio: 24 - 7</p></div>`;
  }
}

async function problemaSalva() {
  problemaLeggiCampi();
  if (!Problema.testo.trim()) { toast('Scrivi prima il testo del problema.'); return; }
  Stato.quaderno.unshift({
    id: uid('es'), data: oggiISO(), materia: 'Matematica', argomento: 'Problema',
    titolo: accorcia(Problema.testo, 50),
    testo: Problema.testo,
    procedimento: 'So: ' + Problema.so + '\nDevo trovare: ' + Problema.trovare +
      '\nOperazione: ' + (Problema.op || '—') + '\nCalcolo: ' + Problema.calcolo,
    risultato: Problema.risposta, nota: ''
  });
  await salvaOra();
  avvisoOk('Salvato nel quaderno di matematica');
}


/* ============================================================
   09-mate-lab.js
   ============================================================ */
/* ==================================================================
   09-mate-lab.js — "Risolvi con me", aiuti progressivi, allenamento,
   quaderno di matematica, i miei errori.
   ================================================================== */

/* ------------------------------------------------------------------
   Pannello dei passaggi (uno alla volta oppure tutti)
   ------------------------------------------------------------------ */

let _passi = { esito: null, indice: 0, unoAllaVolta: true, contenitore: null };

const SEMAFORO = {
  verde: { cl: 'verde', ic: '✅', txt: 'Posso risolverlo' },
  giallo: { cl: 'giallo', ic: '🟡', txt: 'Posso aiutarti in parte' },
  rosso: { cl: 'rosso', ic: '🔴', txt: 'Non posso farlo in modo sicuro' }
};

function mostraPassi(esito, idContenitore) {
  _passi.esito = esito;
  _passi.indice = 0;
  _passi.contenitore = idContenitore || '#risultatoMate';
  disegnaPassi();
}

function disegnaPassi() {
  const box = $(_passi.contenitore);
  if (!box) return;
  const e = _passi.esito;
  if (!e) { box.innerHTML = ''; return; }
  const sem = SEMAFORO[e.stato] || SEMAFORO.giallo;

  if (!e.passi.length) {
    box.innerHTML = `<div class="card">
      <span class="semaforo ${sem.cl}">${sem.ic} ${esc(sem.txt)}</span>
      <p style="margin-top:12px">${esc(e.messaggio || 'Non ho niente da mostrare.')}</p></div>`;
    return;
  }

  const n = e.passi.length;
  const i = limita(_passi.indice, 0, n - 1);
  const daMostrare = _passi.unoAllaVolta ? [e.passi[i]] : e.passi;

  box.innerHTML = `<div class="card">
      <div class="barra-btn" style="margin-bottom:12px">
        <span class="semaforo ${sem.cl}">${sem.ic} ${esc(sem.txt)}</span>
        <button type="button" class="btn btn-piccolo" data-az="passi-modo" aria-pressed="${_passi.unoAllaVolta}">
          <span aria-hidden="true">👣</span><span>Un passo alla volta</span></button>
        ${bottone('passi-leggi', '🔊', 'Ascolta', 'btn-piccolo')}
        ${bottone('passi-quaderno', '📓', 'Salva nel quaderno', 'btn-piccolo')}
      </div>
      ${e.messaggio ? `<div class="avviso avviso-att"><span class="ic">ℹ️</span><p>${esc(e.messaggio)}</p></div>` : ''}
      ${_passi.unoAllaVolta ? `<p class="frase-contatore">Passo ${i + 1} di ${n}</p>` : ''}
      ${daMostrare.map((p, k) => {
        const num = _passi.unoAllaVolta ? i : k;
        return `<div class="passo ${_passi.unoAllaVolta ? 'passo-solo' : ''} ${/controllo|risultato/i.test(p.tit) ? 'controllo' : ''}">
          <div class="numero">Passo ${num + 1} di ${n} · ${esc(p.tit)}</div>
          ${p.html}
          ${p.spiega ? `<div class="spiega">${esc(p.spiega)}</div>` : ''}
          ${p.perche ? `<div class="barra-btn" style="margin:8px 0 0">
              <button type="button" class="btn btn-piccolo" data-az="perche:${num}">❓ Perché?</button></div>
              <div class="aiuto-testo" id="perche-${num}" hidden>${esc(p.perche)}</div>` : ''}
        </div>`;
      }).join('')}
      ${_passi.unoAllaVolta ? `<div class="passo-nav">
        <button type="button" class="btn btn-grande" data-az="passo-prec"${i === 0 ? ' disabled' : ''}>⬅ Indietro</button>
        <button type="button" class="btn btn-grande btn-primario" data-az="passo-succ"${i >= n - 1 ? ' disabled' : ''}>Prossimo passo ➡</button>
      </div>` : ''}
      ${e.soluzioneTesto ? `<div class="avviso avviso-ok" style="margin-top:14px">
        <span class="ic">🎯</span><p><b>Soluzione:</b> ${esc(e.soluzioneTesto)}</p></div>` : ''}
      ${e.retta ? disegnaRettaSoluzione(e.retta) : ''}
    </div>`;
}

/** Piccolo disegno della soluzione di una disequazione sulla retta. */
function disegnaRettaSoluzione(r) {
  const W = 100;
  let barre = '';
  if (r.intervallo) {
    const { fuori, incluso } = r.intervallo;
    barre = fuori
      ? `<div style="position:absolute;left:0;width:28%;height:10px;background:var(--primario);top:15px"></div>
         <div style="position:absolute;right:0;width:28%;height:10px;background:var(--primario);top:15px"></div>`
      : `<div style="position:absolute;left:28%;width:44%;height:10px;background:var(--primario);top:15px"></div>`;
    return `<div style="position:relative;height:60px;margin-top:14px;border-bottom:3px solid var(--testo)">
        ${barre}
        <div style="position:absolute;left:28%;top:8px;width:24px;height:24px;border-radius:50%;border:3px solid var(--testo);background:${incluso ? 'var(--testo)' : 'var(--superficie)'};transform:translateX(-12px)"></div>
        <div style="position:absolute;left:72%;top:8px;width:24px;height:24px;border-radius:50%;border:3px solid var(--testo);background:${incluso ? 'var(--testo)' : 'var(--superficie)'};transform:translateX(-12px)"></div>
        <div style="position:absolute;left:28%;top:40px;transform:translateX(-50%);font-weight:700">${esc(Risolutore.numeroIt(r.intervallo.min))}</div>
        <div style="position:absolute;left:72%;top:40px;transform:translateX(-50%);font-weight:700">${esc(Risolutore.numeroIt(r.intervallo.max))}</div>
      </div>
      <p class="aiutino">Il pallino ${r.intervallo.incluso ? 'pieno vuol dire che il numero è compreso' : 'vuoto vuol dire che il numero NON è compreso'}.</p>`;
  }
  if (r.punti && r.punti.length) {
    const p = r.punti[0];
    return `<div style="position:relative;height:60px;margin-top:14px;border-bottom:3px solid var(--testo)">
        <div style="position:absolute;${r.versoDestra ? 'left:50%;right:0' : 'left:0;width:50%'};height:10px;background:var(--primario);top:15px"></div>
        <div style="position:absolute;left:50%;top:8px;width:24px;height:24px;border-radius:50%;border:3px solid var(--testo);background:${p.aperto ? 'var(--superficie)' : 'var(--testo)'};transform:translateX(-12px)"></div>
        <div style="position:absolute;left:50%;top:40px;transform:translateX(-50%);font-weight:700">${esc(Risolutore.numeroIt(p.x))}</div>
      </div>
      <p class="aiutino">Il pallino ${p.aperto ? 'vuoto: quel numero NON fa parte della soluzione' : 'pieno: quel numero fa parte della soluzione'}. La linea colorata è l\'insieme delle soluzioni.</p>`;
  }
  return '';
}

/* ------------------------------------------------------------------
   Tastiera dei simboli matematici
   ------------------------------------------------------------------ */

const SIMBOLI_MATE = ['x', 'y', 'a', 'b', 'c', '+', '−', '×', '÷', '=', '<', '>', '≤', '≥',
  '(', ')', '^2', '^', '√', 'π', '/', 'sin', 'cos', 'tan', 'log', 'ln'];

function tastieraMate(idCampo) {
  return `<div class="tastiera-mate" role="group" aria-label="Simboli matematici">
    ${SIMBOLI_MATE.map((s) => `<button type="button" data-simbolo="${esc(s)}" data-campo="${esc(idCampo)}"
      aria-label="inserisci ${esc(s === '^2' ? 'al quadrato' : s === '^' ? 'elevato a' : s)}">${esc(s === '^2' ? 'x²' : s === '^' ? 'xⁿ' : s)}</button>`).join('')}
    <button type="button" data-simbolo="⌫" data-campo="${esc(idCampo)}" aria-label="cancella l'ultimo carattere">⌫</button>
  </div>`;
}

function inserisciSimbolo(idCampo, simbolo) {
  // "__eq" vuol dire: la riga su cui sto scrivendo adesso nel quaderno
  if (idCampo === '__eq') idCampo = RisolvoIo.campoAttivo || 'riga-0';
  const n = $('#' + idCampo);
  if (!n) return;
  if (simbolo === '⌫') {
    const p = n.selectionStart;
    if (p > 0) { n.value = n.value.slice(0, p - 1) + n.value.slice(n.selectionEnd); n.setSelectionRange(p - 1, p - 1); }
  } else {
    const p = n.selectionStart == null ? n.value.length : n.selectionStart;
    const f = n.selectionEnd == null ? p : n.selectionEnd;
    n.value = n.value.slice(0, p) + simbolo + n.value.slice(f);
    n.setSelectionRange(p + simbolo.length, p + simbolo.length);
  }
  n.focus();
}

/* ------------------------------------------------------------------
   RISOLVI CON ME
   ------------------------------------------------------------------ */

const Risolvi = { testo: '', tipo: 'auto', aiuto: 0, seconda: '', metodo: 'riduzione' };

const TIPI_ESERCIZIO = [
  { id: 'auto', ic: '🪄', tit: 'Capiscilo tu' },
  { id: 'equazione', ic: '=', tit: 'Equazione' },
  { id: 'disequazione', ic: '<', tit: 'Disequazione' },
  { id: 'sistema', ic: '{', tit: 'Sistema' },
  { id: 'sviluppa', ic: '×', tit: 'Sviluppa' },
  { id: 'scomponi', ic: '÷', tit: 'Scomponi' },
  { id: 'derivata', ic: 'f′', tit: 'Derivata' },
  { id: 'limite', ic: 'lim', tit: 'Limite' },
  { id: 'integrale', ic: '∫', tit: 'Integrale' }
];

STRUMENTI['mate-risolvi'] = function (c) {
  if (_esercizioDaPdf) { Risolvi.testo = _esercizioDaPdf; _esercizioDaPdf = ''; }
  c.innerHTML = testaStrumento('🧠', 'Risolvi con me',
    'Prima proviamo insieme. La soluzione completa te la faccio vedere solo se vuoi.') + `
    <div class="card">
      <label class="etichetta" for="rsTesto">Scrivi l'esercizio</label>
      <input class="campo" id="rsTesto" type="text" value="${esc(Risolvi.testo)}"
        placeholder="Per esempio: 3x + 7 = 22" autocomplete="off" autocapitalize="off" spellcheck="false">
      ${tastieraMate('rsTesto')}
      <div id="rsSeconda" ${Risolvi.tipo === 'sistema' ? '' : 'hidden'}>
        <label class="etichetta" for="rsTesto2">Seconda equazione del sistema</label>
        <input class="campo" id="rsTesto2" type="text" value="${esc(Risolvi.seconda)}" placeholder="Per esempio: x - y = 2" autocomplete="off">
        <label class="etichetta" for="rsMetodo">Metodo</label>
        <select class="campo" id="rsMetodo">
          <option value="riduzione">Riduzione</option>
          <option value="sostituzione">Sostituzione</option>
          <option value="confronto">Confronto</option>
        </select>
      </div>
      <div id="rsExtra"></div>

      <label class="etichetta">Che tipo di esercizio è?</label>
      <div class="barra-btn">
        ${TIPI_ESERCIZIO.map((t) => `<button type="button" class="btn btn-piccolo" data-az="rs-tipo:${t.id}"
            aria-pressed="${Risolvi.tipo === t.id}"><span aria-hidden="true">${t.ic}</span><span>${esc(t.tit)}</span></button>`).join('')}
      </div>
    </div>

    <div class="card">
      <div class="barra-btn" style="margin-bottom:0">
        ${bottone('rs-conme', '🧠', 'Fallo con me', 'btn-primario btn-grande')}
        ${bottone('rs-aiuto', '💡', 'Dammi un aiuto', 'btn-grande')}
        ${bottone('rs-passaggi', '👣', 'Mostra i passaggi', 'btn-grande')}
        ${bottone('rs-controlla', '✅', 'Controlla il mio risultato', 'btn-grande')}
        ${bottone('rs-soluzione', '👀', 'Mostra la soluzione completa', 'btn-grande btn-attenzione')}
      </div>
      <p class="aiutino">Il pulsante giallo è l'ultima spiaggia: se lo usi sempre, impari meno.</p>
    </div>

    <div id="aiutiMate"></div>
    <div id="risultatoMate"></div>`;
};

function tipoAutomatico(t) {
  const s = M.normalizzaTesto(t);
  if (/lim|→/.test(s)) return 'limite';
  if (/<=|>=|<|>/.test(s)) return 'disequazione';
  if (s.indexOf('=') >= 0) return 'equazione';
  if (/[+\-][^)]*\)\s*\(/.test(s) || /\^\s*[23]/.test(s)) return 'sviluppa';
  return 'sviluppa';
}

function risolviEsercizio(quanto) {
  Risolvi.testo = $('#rsTesto').value.trim();
  const seconda = $('#rsTesto2');
  if (seconda) Risolvi.seconda = seconda.value.trim();
  const met = $('#rsMetodo');
  if (met) Risolvi.metodo = met.value;
  if (!Risolvi.testo) { toast('Scrivi prima l\'esercizio.'); return; }

  const tipo = Risolvi.tipo === 'auto' ? tipoAutomatico(Risolvi.testo) : Risolvi.tipo;
  let esito;
  try {
    switch (tipo) {
      case 'equazione': esito = Risolutore.risolviEquazione(Risolvi.testo); break;
      case 'disequazione': esito = Risolutore.risolviDisequazione(Risolvi.testo); break;
      case 'sistema': esito = Risolutore.risolviSistema(Risolvi.testo, Risolvi.seconda, Risolvi.metodo); break;
      case 'sviluppa': esito = Risolutore.espandi(Risolvi.testo); break;
      case 'scomponi': esito = Risolutore.scomponi(Risolvi.testo); break;
      case 'derivata': esito = Risolutore.derivataPassi(Risolvi.testo); break;
      case 'limite': esito = Risolutore.limitePassi(Risolvi.testo, ($('#rsVerso') || {}).value || '0'); break;
      case 'integrale': esito = Risolutore.integralePassi(Risolvi.testo); break;
      default: esito = Risolutore.risolviEquazione(Risolvi.testo);
    }
  } catch (e) {
    esito = { stato: 'rosso', messaggio: e.amichevole ? e.message : 'Non riesco a leggere questo esercizio. Controlla come l\'hai scritto.', passi: [] };
  }

  _passi.unoAllaVolta = (quanto !== 'tutto');
  mostraPassi(esito, '#risultatoMate');
  _ultimoEsito = esito;
  if (quanto === 'tutto') $('#aiutiMate').innerHTML = '';
}

let _ultimoEsito = null;
let _esercizioDaPdf = '';

/** Aiuti progressivi: 1 piccolo, 2 più esplicito, 3 il passaggio, 4 la soluzione. */
function aiutoProgressivo() {
  Risolvi.testo = $('#rsTesto').value.trim();
  if (!Risolvi.testo) { toast('Scrivi prima l\'esercizio.'); return; }
  if (!_ultimoEsito) risolviEsercizio('conme');
  const e = _ultimoEsito;
  if (!e || !e.passi.length) return;
  Risolvi.aiuto = Math.min(Risolvi.aiuto + 1, 4);
  const box = $('#aiutiMate');
  const p0 = e.passi[Math.min(1, e.passi.length - 1)];
  const testi = {
    1: { tit: '💡 Aiuto 1', txt: 'Guarda bene com\'è fatto l\'esercizio: ' + (e.passi[0].spiega || 'parti da quello che c\'è scritto e non saltare passaggi.') },
    2: { tit: '💡 Aiuto 2', txt: p0.perche || p0.spiega || 'Prova a fare la prima trasformazione da solo.' },
    3: { tit: '👀 Il prossimo passaggio', txt: (p0.spiega || '') },
    4: { tit: '✅ La soluzione', txt: 'Guarda tutti i passaggi qui sotto.' }
  };
  const a = testi[Risolvi.aiuto];
  box.innerHTML = `<div class="card">
    <div class="aiuti">
      <div class="aiuto-testo"><b>${esc(a.tit)}</b><br>${esc(a.txt)}</div>
      ${Risolvi.aiuto >= 3 ? `<div class="passo">${e.passi[Math.min(1, e.passi.length - 1)].html}</div>` : ''}
    </div>
    <div class="barra-btn" style="margin-top:10px">
      ${Risolvi.aiuto < 4 ? bottone('rs-aiuto', '💡', 'Ho ancora bisogno di aiuto') : bottone('rs-soluzione', '👀', 'Mostra tutto', 'btn-attenzione')}
    </div>
  </div>`;
  if (Risolvi.aiuto >= 4) { _passi.unoAllaVolta = false; disegnaPassi(); }
}

async function controllaMioRisultato() {
  const eq = $('#rsTesto').value.trim();
  if (!eq) { toast('Scrivi prima l\'esercizio.'); return; }
  const r = await finestra({
    titolo: 'Controlla il tuo risultato',
    testo: 'Scrivi il valore che hai trovato. Lo sostituisco nell\'esercizio e vediamo se torna.',
    campi: [
      { nome: 'variabile', etichetta: 'Incognita', valore: 'x' },
      { nome: 'valore', etichetta: 'Il mio risultato', valore: '', aiuto: 'Per esempio: 5 oppure 3/2' }
    ],
    testoOk: 'Controlla'
  });
  if (!r || !r.valore) return;
  const c = Risolutore.controllaRisultato(eq, r.variabile || 'x', r.valore);
  const box = $('#risultatoMate');
  if (c.errore) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">🤔</span><p>${esc(c.errore)}</p></div>`;
    return;
  }
  box.innerHTML = c.ok
    ? `<div class="card"><div class="avviso avviso-ok"><span class="ic">✅</span>
        <p><b>Risultato verificato!</b> Sostituendo ${esc(r.variabile)} = ${esc(r.valore)} l'uguaglianza è vera.</p></div>
        ${c.html}</div>`
    : `<div class="card"><div class="avviso avviso-att"><span class="ic">🤔</span>
        <p><b>Non torna.</b> Con ${esc(r.variabile)} = ${esc(r.valore)} le due parti dell'uguale danno numeri diversi.
        Rifai i passaggi con calma: succede a tutti.</p></div>
        ${c.html}</div>`;
}

/* ------------------------------------------------------------------
   LO RISOLVO IO — il quaderno dei passaggi

   Lo studente copia l'esercizio (anche dal PDF del libro), poi ricopia
   la riga di sopra e la modifica, un passaggio alla volta, come farebbe
   sul quaderno. Il programma NON risolve: controlla soltanto che ogni
   riga dica ancora la stessa cosa della precedente.
   La soluzione automatica arriva solo dopo, e solo se la chiede lui.
   ------------------------------------------------------------------ */

const RisolvoIo = { righe: [], variabile: 'x', campoAttivo: null, esitoRighe: {} };

function caricaLavoroEquazione() {
  const l = Stato.lavoroEquazione;
  if (l && Array.isArray(l.righe) && l.righe.length) {
    RisolvoIo.righe = l.righe.slice();
    RisolvoIo.variabile = l.variabile || 'x';
  }
}

/** Aggiorna SUBITO lo stato (la scrittura su disco resta ritardata da salva()):
    così, se l'app viene chiusa di colpo, l'ultima riga non si perde. */
function salvaLavoroEquazione() {
  Stato.lavoroEquazione = { righe: RisolvoIo.righe.slice(), variabile: RisolvoIo.variabile, quando: oraISO() };
  salva();
}

STRUMENTI['mate-risolvoio'] = function (c) {
  if (_esercizioDaPdf) {
    RisolvoIo.righe = [pulisciEsercizio(_esercizioDaPdf)];
    RisolvoIo.esitoRighe = {};
    _esercizioDaPdf = '';
    salvaLavoroEquazione();
  } else if (!RisolvoIo.righe.length) {
    caricaLavoroEquazione();
  }
  if (!RisolvoIo.righe.length) RisolvoIo.righe = [''];

  c.innerHTML = testaStrumento('✍️', 'Lo risolvo io',
    'Scrivi tu i passaggi, una riga alla volta. Io controllo che siano giusti.') + `
    <div class="card no-stampa">
      <div class="barra-btn" style="margin-bottom:0">
        ${bottone('ri-daPdf', '📄', 'Copia dal libro PDF')}
        ${bottone('ri-nuovo', '🆕', 'Ricomincia da capo')}
        ${bottone('ri-quaderno', '📓', 'Salva nel quaderno')}
      </div>
    </div>
    <div id="riRighe"></div>
    <div id="riEsito"></div>`;
  disegnaRisolvoIo();
};

/** Toglie dal testo copiato dal PDF le cose che non fanno parte dell'equazione. */
/**
 * Toglie dal testo copiato dal libro tutto quello che non è l'equazione:
 * "84. Risolvi la seguente equazione: 4x − 6 = 2x + 8" → "4x − 6 = 2x + 8".
 * Va tolto pezzo per pezzo, in giro, perché le formule dei libri sono
 * tutte diverse fra loro.
 */
const PAROLE_DA_TOGLIERE = new RegExp('^(esercizio|esercizi|es|num|numero|problema|quesito|' +
  'risolvi|risolvere|risolvete|calcola|calcolare|trova|trovare|determina|determinare|' +
  'semplifica|semplificare|verifica|verificare|svolgi|svolgere|' +
  'la|le|il|un|una|seguente|seguenti|equazione|equazioni|espressione|espressioni|' +
  'disequazione|disequazioni|sistema|sistemi)\\b[\'’]?\\s*[:.)]?\\s*', 'i');

function pulisciEsercizio(t) {
  const originale = String(t || '').replace(/\s+/g, ' ').trim();
  if (originale.indexOf('=') < 0) return originale;    // non è un'equazione: non tocco niente

  let s = originale, prima;
  do {
    prima = s;
    s = s.replace(/^n[.°]\s*\d*\s*/i, '');      // "n. 84"
    s = s.replace(/^\d+\s*[.)]\s*/, '');        // "84."  "84)"
    // "84: risolvi" è il numero dell'esercizio, ma "6 : 2" e "24 : x" sono
    // divisioni: taglio solo se dopo i due punti c'è una parola vera
    // (almeno tre lettere), non un'incognita.
    s = s.replace(/^\d+\s*:\s*(?=[a-zà-ÿ]{3,})/i, '');
    s = s.replace(/^l['’]\s*/i, '');            // "l'espressione"
    s = s.replace(PAROLE_DA_TOGLIERE, '');
  } while (s !== prima && s.length);

  // resta una frase davanti che finisce con i due punti? ("il valore di x: 5x = 20")
  // Attenzione: i due punti sono anche il segno di divisione (6 : 2 = 3),
  // quindi taglio solo se davanti c'è davvero una parola.
  const uguale = s.indexOf('=');
  const duePunti = s.lastIndexOf(':', uguale < 0 ? s.length : uguale);
  if (duePunti > 0 && /[a-zà-ÿ]{3,}/i.test(s.slice(0, duePunti))) {
    const dopo = s.slice(duePunti + 1).trim();
    if (dopo.indexOf('=') >= 0) s = dopo;
  }

  s = s.replace(/[.;,]\s*$/, '').trim();
  // se ho tolto troppo tengo il testo originale
  return (s.length >= 3 && s.indexOf('=') >= 0) ? s : originale;
}

function disegnaRisolvoIo() {
  const box = $('#riRighe');
  if (!box) return;
  const n = RisolvoIo.righe.length;
  box.innerHTML = `<div class="card">
      ${RisolvoIo.righe.map((r, i) => {
        const esito = RisolvoIo.esitoRighe[i];
        return `<div class="riga-eq ${esito ? 'esito-' + esito.stato : ''}">
          <span class="numero-riga">${i + 1}</span>
          <input class="campo campo-eq" id="riga-${i}" type="text" value="${esc(r)}"
            autocomplete="off" autocapitalize="off" spellcheck="false" inputmode="text"
            aria-label="Riga ${i + 1} del procedimento"
            placeholder="${i === 0 ? 'Scrivi qui l\'equazione, per esempio 3x + 7 = 22' : 'Scrivi il passaggio'}">
          <div class="azioni-riga">
            ${i > 0 ? `<button type="button" class="btn btn-piccolo" data-az="ri-controlla:${i}"
              aria-label="Controlla la riga ${i + 1}">✓</button>` : ''}
            <button type="button" class="btn btn-piccolo" data-az="ri-leggi:${i}" aria-label="Ascolta la riga ${i + 1}">🔊</button>
            ${n > 1 ? `<button type="button" class="btn btn-piccolo btn-errore" data-az="ri-canc:${i}"
              aria-label="Cancella la riga ${i + 1}">🗑</button>` : ''}
          </div>
          ${esito ? `<p class="messaggio-riga">${esc(esito.messaggio)}</p>` : ''}
        </div>`;
      }).join('')}

      <div class="barra-btn" style="margin-top:14px">
        ${bottone('ri-ricopia', '⬇️', 'Ricopia la riga qui sotto', 'btn-primario btn-grande')}
      </div>
      <p class="aiutino">Ricopia la riga, poi cambia solo quello che devi cambiare.
        Il pulsante ✓ ti dice se la riga nuova dice ancora la stessa cosa.</p>

      ${tastieraMate('__eq')}
    </div>

    <div class="card no-stampa">
      <div class="barra-btn" style="margin-bottom:0">
        ${bottone('ri-finito', '✅', 'Ho finito, controlla', 'btn-primario btn-grande')}
        ${bottone('ri-aiuto', '💡', 'Che cosa faccio adesso?', 'btn-grande')}
        ${bottone('ri-programma', '👀', 'Fammela vedere risolta', 'btn-grande btn-attenzione')}
      </div>
    </div>`;

  // memorizzo su quale riga sta scrivendo, per la tastiera dei simboli
  $$('.campo-eq', box).forEach((inp, i) => {
    inp.addEventListener('focus', () => { RisolvoIo.campoAttivo = inp.id; });
    inp.addEventListener('input', () => {
      RisolvoIo.righe[i] = inp.value;
      delete RisolvoIo.esitoRighe[i];
      salvaLavoroEquazione();
    });
    inp.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); ricopiaRiga(); }
    });
  });
}

function ricopiaRiga() {
  leggiRigheDalloSchermo();
  const ultima = RisolvoIo.righe[RisolvoIo.righe.length - 1] || '';
  if (!ultima.trim()) { toast('Scrivi prima l\'equazione nella riga di sopra.'); return; }
  RisolvoIo.righe.push(ultima);
  salvaLavoroEquazione();
  disegnaRisolvoIo();
  const nuovo = $('#riga-' + (RisolvoIo.righe.length - 1));
  if (nuovo) { nuovo.focus(); nuovo.setSelectionRange(nuovo.value.length, nuovo.value.length); }
}

function leggiRigheDalloSchermo() {
  RisolvoIo.righe.forEach((r, i) => {
    const n = $('#riga-' + i);
    if (n) RisolvoIo.righe[i] = n.value;
  });
  salvaLavoroEquazione();
}

function controllaRigaEq(i) {
  leggiRigheDalloSchermo();
  const v = RisolvoIo.variabile;
  const r = Risolutore.passaggioEquivalente(RisolvoIo.righe[i - 1], RisolvoIo.righe[i], v);
  if (r.esito === 'ok') {
    RisolvoIo.esitoRighe[i] = { stato: 'ok', messaggio: '✅ Giusto: questa riga dice ancora la stessa cosa.' };
  } else if (r.esito === 'diverso') {
    RisolvoIo.esitoRighe[i] = { stato: 'errore', messaggio: '🤔 Attenzione: questa riga non dice più la stessa cosa di quella sopra. Guarda bene che cosa è cambiato.' };
  } else {
    RisolvoIo.esitoRighe[i] = { stato: 'nonSo', messaggio: '🟡 ' + (r.messaggio || 'Non riesco a controllare questa riga.') };
  }
  disegnaRisolvoIo();
}

function aiutoRisolvoIo() {
  leggiRigheDalloSchermo();
  const ultima = RisolvoIo.righe[RisolvoIo.righe.length - 1] || '';
  if (!ultima.trim()) { toast('Scrivi prima l\'equazione.'); return; }
  const m = Risolutore.prossimaMossa(ultima, RisolvoIo.variabile);
  $('#riEsito').innerHTML = `<div class="card">
    <div class="aiuto-testo"><b>💡 Che cosa faccio adesso</b><br>${esc(m.testo)}</div>
    <p class="aiutino">Provaci tu: ricopia la riga qui sopra e cambia solo quello che serve.</p>
  </div>`;
}

function finitoRisolvoIo() {
  leggiRigheDalloSchermo();
  if (RisolvoIo.righe.length < 2) { toast('Scrivi almeno un passaggio, poi controlliamo.'); return; }
  const e = Risolutore.esitoFinale(RisolvoIo.righe, RisolvoIo.variabile);
  const classe = e.stato === 'ok' ? 'avviso-ok' : e.stato === 'errore' ? 'avviso-att' : 'avviso';
  const icona = e.stato === 'ok' ? '🎉' : e.stato === 'quasi' ? '💪' : e.stato === 'errore' ? '🤔' : 'ℹ️';
  $('#riEsito').innerHTML = `<div class="card">
    <div class="avviso ${classe}"><span class="ic">${icona}</span><p>${esc(e.messaggio)}</p></div>
    ${e.stato === 'ok' ? `<div class="barra-btn">
      ${bottone('ri-quaderno', '📓', 'Salvalo nel quaderno', 'btn-primario')}
      ${bottone('ri-nuovo', '🆕', 'Un altro esercizio')}
    </div>` : `<div class="barra-btn">
      ${bottone('ri-aiuto', '💡', 'Dammi un suggerimento')}
    </div>`}
  </div>`;
}

/** La soluzione automatica arriva solo dopo che ci ha provato. */
async function mostraSoluzioneProgramma() {
  leggiRigheDalloSchermo();
  const partenza = RisolvoIo.righe[0] || '';
  if (!partenza.trim()) { toast('Scrivi prima l\'equazione.'); return; }
  if (RisolvoIo.righe.length < 2) {
    const ok = await conferma('Vuoi già la soluzione?',
      'Non hai ancora provato nessun passaggio. Anche un tentativo sbagliato serve a imparare: prova almeno una riga, poi te la faccio vedere.',
      'Fammela vedere lo stesso');
    if (!ok) return;
  }
  Risolvi.testo = partenza;
  Risolvi.tipo = 'equazione';
  Risolvi.aiuto = 0;
  vaiA('mate', 'risolvi');
  setTimeout(() => {
    const inp = $('#rsTesto');
    if (inp) inp.value = partenza;
    risolviEsercizio('conme');
  }, 80);
}

async function salvaLavoroNelQuaderno() {
  leggiRigheDalloSchermo();
  if (!RisolvoIo.righe[0]) return;
  await nuovoEsercizioQuaderno({
    titolo: accorcia(RisolvoIo.righe[0], 50),
    argomento: 'Algebra',
    testo: RisolvoIo.righe[0],
    procedimento: RisolvoIo.righe.map((r, i) => (i + 1) + ')  ' + r).join('\n'),
    risultato: RisolvoIo.righe[RisolvoIo.righe.length - 1]
  });
}

/* ------------------------------------------------------------------
   MI ALLENO
   ------------------------------------------------------------------ */

const Alleno = { tipo: 'eq1', esercizio: null, aiuto: 0 };

const TIPI_ALLENAMENTO = [
  { id: 'eq1', tit: 'Equazioni di primo grado' },
  { id: 'eq2', tit: 'Equazioni di secondo grado' },
  { id: 'tab', tit: 'Tabelline' },
  { id: 'perc', tit: 'Percentuali' },
  { id: 'frazioni', tit: 'Frazioni' }
];

STRUMENTI['mate-alleno'] = function (c) {
  c.innerHTML = testaStrumento('🎓', 'Mi alleno',
    'Qui la soluzione non arriva subito: prima provi tu, e se serve chiedi un aiuto.') + `
    <div class="card">
      <label class="etichetta" for="alTipo">Su cosa mi alleno?</label>
      <select class="campo" id="alTipo">
        ${TIPI_ALLENAMENTO.map((t) => `<option value="${t.id}"${Alleno.tipo === t.id ? ' selected' : ''}>${esc(t.tit)}</option>`).join('')}
      </select>
      <div class="barra-btn" style="margin-top:12px">${bottone('al-nuovo', '🎲', 'Dammi un esercizio', 'btn-primario btn-grande')}</div>
    </div>
    <div id="alBox"></div>`;
};

function nuovoAllenamento() {
  Alleno.tipo = $('#alTipo').value;
  Alleno.aiuto = 0;
  const r = (n) => Math.floor(Math.random() * n);
  let e;
  switch (Alleno.tipo) {
    case 'eq1': {
      const a = 2 + r(8), x = 1 + r(9), b = 1 + r(20);
      e = { testo: a + 'x + ' + b + ' = ' + (a * x + b), risposta: String(x), variabile: 'x',
        aiuto1: 'Prima togli ' + b + ' da tutti e due i membri.',
        aiuto2: 'Ti resta ' + a + 'x = ' + (a * x) + '. Adesso dividi per ' + a + '.',
        formula: 'Quello che faccio a sinistra lo devo fare anche a destra.' };
      break;
    }
    case 'eq2': {
      const x1 = 1 + r(6), x2 = 1 + r(6);
      const b = -(x1 + x2), c = x1 * x2;
      e = { testo: 'x^2 ' + (b < 0 ? '- ' + Math.abs(b) : '+ ' + b) + 'x + ' + c + ' = 0',
        risposta: String(Math.min(x1, x2)) + ' e ' + String(Math.max(x1, x2)), variabile: 'x',
        aiuto1: 'Riconosci a, b e c, poi calcola Δ = b² − 4ac.',
        aiuto2: 'Δ = ' + (b * b - 4 * c) + '. Adesso usa la formula (−b ± √Δ) / 2a.',
        formula: 'x = (−b ± √(b² − 4ac)) / (2a)' };
      break;
    }
    case 'tab': {
      const a = 2 + r(9), b = 2 + r(9);
      e = { testo: a + ' × ' + b, risposta: String(a * b),
        aiuto1: 'Conta di ' + a + ' in ' + a + '.', aiuto2: a + ' × ' + b + ' è come ' + b + ' × ' + a + '.', formula: '' };
      break;
    }
    case 'perc': {
      const p = [10, 20, 25, 50, 5][r(5)], d = (2 + r(9)) * 20;
      e = { testo: p + '% di ' + d, risposta: String(d * p / 100),
        aiuto1: 'Il ' + p + '% vuol dire ' + p + ' ogni 100.',
        aiuto2: 'Fai ' + d + ' × ' + p + ' e poi dividi per 100.', formula: 'parte = totale × percentuale ÷ 100' };
      break;
    }
    default: {
      const d = 2 + r(6), n1 = 1 + r(d - 1), n2 = 1 + r(d - 1);
      e = { testo: n1 + '/' + d + ' + ' + n2 + '/' + d, risposta: M.fTesto(M.fr(n1 + n2, d)),
        aiuto1: 'I denominatori sono uguali: si sommano solo i numeratori.',
        aiuto2: 'Fai ' + n1 + ' + ' + n2 + ' = ' + (n1 + n2) + ', il denominatore resta ' + d + '. Poi semplifica se puoi.',
        formula: 'a/c + b/c = (a+b)/c' };
    }
  }
  Alleno.esercizio = e;
  disegnaAllenamento();
}

function disegnaAllenamento() {
  const e = Alleno.esercizio;
  if (!e) return;
  $('#alBox').innerHTML = `<div class="card">
      <h2>Prova tu</h2>
      <div class="mate mate-blocco" style="font-size:1.9em">${esc(e.testo)}</div>
      <label class="etichetta" for="alRisp">La mia risposta</label>
      <input class="campo" id="alRisp" type="text" autocomplete="off" placeholder="Scrivi qui">
      <div class="barra-btn" style="margin-top:12px">
        ${bottone('al-controlla', '✅', 'Controlla', 'btn-primario btn-grande')}
        ${bottone('al-aiuto1', '💡', 'Suggerimento 1')}
        ${bottone('al-aiuto2', '💡', 'Suggerimento 2')}
        ${e.formula ? bottone('al-formula', '📐', 'Formula utile') : ''}
        ${bottone('al-nuovo', '🎲', 'Un altro esercizio')}
      </div>
      <div id="alEsito"></div>
    </div>`;
  const inp = $('#alRisp');
  inp.focus();
  inp.addEventListener('keydown', (ev) => { if (ev.key === 'Enter') controllaAllenamento(); });
}

function controllaAllenamento() {
  const e = Alleno.esercizio;
  if (!e) return;
  const v = ($('#alRisp').value || '').trim().replace(/\s+/g, ' ').replace(',', '.');
  const atteso = String(e.risposta).replace(',', '.');
  let ok = v.toLowerCase() === atteso.toLowerCase();
  if (!ok) {
    try {
      const a = M.valuta(M.analizza(v), {}), b = M.valuta(M.analizza(atteso), {});
      ok = Math.abs(a - b) < 1e-9;
    } catch (err) { /* confronto testuale */ }
  }
  $('#alEsito').innerHTML = ok
    ? `<div class="avviso avviso-ok"><span class="ic">✅</span><p><b>Corretto!</b> Bravo.</p></div>`
    : `<div class="avviso avviso-att"><span class="ic">🤔</span><p><b>Non ancora.</b> Prova a usare un suggerimento: non c'è nessuna fretta e nessun voto.</p></div>`;
  if (ok) setTimeout(nuovoAllenamento, 1400);
}

function aiutoAllenamento(livello) {
  const e = Alleno.esercizio;
  if (!e) return;
  const t = livello === 1 ? e.aiuto1 : livello === 2 ? e.aiuto2 : e.formula;
  $('#alEsito').innerHTML = `<div class="aiuto-testo">${esc(t)}</div>`;
}

/* ------------------------------------------------------------------
   IL MIO QUADERNO DI MATEMATICA
   ------------------------------------------------------------------ */

const ARGOMENTI_QUADERNO = ['Algebra', 'Geometria', 'Funzioni', 'Trigonometria', 'Analisi', 'Statistica', 'Problema', 'Altro'];

STRUMENTI['mate-quaderno'] = function (c) {
  c.innerHTML = testaStrumento('📓', 'Il mio quaderno di matematica',
    'Gli esercizi che hai già fatto, per ritrovarli quando servono.') + `
    <div class="card">
      <div class="barra-btn" style="margin-bottom:0">
        ${bottone('qd-nuovo', '➕', 'Aggiungi un esercizio', 'btn-primario btn-grande')}
        <button type="button" class="btn btn-grande" data-vai="mate/quadernofoglio">
          <span aria-hidden="true">🖨️</span><span>Vedi e stampa il quaderno</span></button>
      </div>
    </div>
    <div id="qdLista"></div>`;
  disegnaQuaderno();
};

/* ---- il quaderno su un foglio A4 -------------------------------- */

STRUMENTI['mate-quadernofoglio'] = function (c) {
  const argomenti = Array.from(new Set(Stato.quaderno.map((q) => q.argomento || 'Altro'))).sort();
  c.innerHTML = testaStrumento('🖨️', 'Il quaderno da stampare',
    'Ecco il tuo lavoro su un foglio A4: puoi stamparlo o salvarlo in PDF.') + `
    <div class="card no-stampa">
      <div class="riga-campi">
        <div>
          <label class="etichetta" for="qfArg">Che cosa metto sul foglio</label>
          <select class="campo" id="qfArg">
            <option value="">Tutto il quaderno</option>
            ${argomenti.map((a) => `<option value="${esc(a)}">${esc(a)}</option>`).join('')}
          </select>
        </div>
        <div>
          <label class="etichetta" for="qfDa">Solo da questa data in poi</label>
          <input class="campo" id="qfDa" type="date">
        </div>
      </div>
      <label class="check"><input type="checkbox" id="qfProc" checked>
        <span>Metti anche i procedimenti e i risultati</span></label>
      <label class="check"><input type="checkbox" id="qfVuoto">
        <span>Foglio di ripasso: solo i testi, con le righe vuote per rifarli</span></label>
      <div class="barra-btn" style="margin-top:12px">
        ${bottone('qf-aggiorna', '🔄', 'Aggiorna il foglio')}
        ${bottone('qf-stampa', '🖨️', 'Stampa in A4', 'btn-primario btn-grande')}
        <button type="button" class="btn" data-vai="mate/quaderno">
          <span aria-hidden="true">⬅</span><span>Torna al quaderno</span></button>
      </div>
    </div>
    <div id="qfFoglio"></div>`;
  ['#qfArg', '#qfDa', '#qfProc', '#qfVuoto'].forEach((s) => {
    const n = $(s, c);
    if (n) n.addEventListener('change', disegnaFoglioQuaderno);
  });
  disegnaFoglioQuaderno();
};

function disegnaFoglioQuaderno() {
  const box = $('#qfFoglio');
  if (!box) return;
  const arg = ($('#qfArg') || {}).value || '';
  const da = ($('#qfDa') || {}).value || '';
  const conProcedimento = ($('#qfProc') || {}).checked !== false;
  const perRipasso = !!(($('#qfVuoto') || {}).checked);

  const lista = Stato.quaderno.filter((q) =>
    (!arg || (q.argomento || 'Altro') === arg) && (!da || String(q.data || '') >= da));

  const nome = (Stato.profilo.nome || '').trim();
  const classe = (Stato.profilo.classe || '').trim();

  box.innerHTML = `<div class="foglio-a4">
      <div class="riga-titolo">
        <h1 style="margin:0">Il mio quaderno di matematica</h1>
        <div style="text-align:right;font-size:.9em">
          ${nome ? esc(nome) + (classe ? ' — ' + esc(classe) : '') + '<br>' : ''}
          ${esc(dataInParole(oggiISO()))}
        </div>
      </div>
      ${arg ? `<p class="etichette">Argomento: <b>${esc(arg)}</b></p>` : ''}
      ${lista.length ? lista.map((q, i) => `
        <div class="esercizio">
          <h3 style="margin:0 0 4px">${i + 1}. ${esc(q.titolo || accorcia(q.testo, 60))}</h3>
          <p class="etichette">${esc(dataInParole(q.data))}${q.argomento ? ' · ' + esc(q.argomento) : ''}</p>
          ${q.testo ? `<p style="margin:4px 0">${esc(q.testo)}</p>` : ''}
          ${perRipasso
            ? '<div class="spazio-scrittura"></div>'.repeat(4)
            : (conProcedimento ? `
              ${q.procedimento ? `<div class="procedimento">${esc(q.procedimento)}</div>` : ''}
              ${q.risultato ? `<p class="risultato">Risultato: ${esc(q.risultato)}</p>` : ''}
              ${q.nota ? `<p class="etichette">Nota: ${esc(q.nota)}</p>` : ''}` : '')}
        </div>`).join('')
      : '<p>Non c\'è ancora niente da mettere sul foglio.</p>'}
      <p class="etichette" style="margin-top:18px">
        ${lista.length === 1 ? '1 esercizio' : lista.length + ' esercizi'} · Studio DSA
      </p>
    </div>`;
}

function disegnaQuaderno() {
  const box = $('#qdLista');
  if (!box) return;
  box.innerHTML = Stato.quaderno.length
    ? `<ul class="lista">${Stato.quaderno.map((q) => `
        <li class="voce">
          <div class="corpo">
            <b>${esc(q.titolo || accorcia(q.testo, 50))}</b>
            <span class="meta">${esc(dataInParole(q.data))} · ${esc(q.argomento || 'Altro')}${q.risultato ? ' · risultato: ' + esc(accorcia(q.risultato, 30)) : ''}</span>
          </div>
          <div class="azioni">
            ${bottone('qd-apri:' + q.id, '👁️', 'Vedi', 'btn-piccolo')}
            ${bottone('qd-canc:' + q.id, '🗑️', 'Elimina', 'btn-piccolo btn-errore')}
          </div>
        </li>`).join('')}</ul>`
    : `<div class="card">${schedaVuota('📓', 'Il quaderno è vuoto.', 'Ogni esercizio che risolvi lo puoi salvare qui.')}</div>`;
}

async function nuovoEsercizioQuaderno(precompilato) {
  const r = await finestra({
    titolo: 'Esercizio nel quaderno',
    campi: [
      { nome: 'titolo', etichetta: 'Titolo', valore: (precompilato && precompilato.titolo) || '' },
      { nome: 'argomento', etichetta: 'Argomento', tipo: 'scelta', opzioni: ARGOMENTI_QUADERNO, valore: (precompilato && precompilato.argomento) || 'Algebra' },
      { nome: 'testo', etichetta: 'Testo dell\'esercizio', tipo: 'area', righe: 3, valore: (precompilato && precompilato.testo) || '' },
      { nome: 'procedimento', etichetta: 'Procedimento', tipo: 'area', righe: 4, valore: (precompilato && precompilato.procedimento) || '' },
      { nome: 'risultato', etichetta: 'Risultato', valore: (precompilato && precompilato.risultato) || '' },
      { nome: 'nota', etichetta: 'Nota per me', tipo: 'area', righe: 2, valore: '' }
    ],
    testoOk: 'Salva'
  });
  if (!r) return;
  Stato.quaderno.unshift(Object.assign({ id: uid('es'), data: oggiISO(), materia: 'Matematica' }, r));
  await salvaOra();
  disegnaQuaderno();
  avvisoOk('Salvato nel quaderno');
}

function apriEsercizioQuaderno(id) {
  const q = Stato.quaderno.find((x) => x.id === id);
  if (!q) return;
  finestra({
    titolo: q.titolo || 'Esercizio',
    testo: [q.testo, q.procedimento, q.risultato ? 'Risultato: ' + q.risultato : '', q.nota].filter(Boolean).join('\n\n'),
    testoOk: 'Chiudi', testoAnnulla: 'Chiudi'
  });
}

/* ------------------------------------------------------------------
   I MIEI ERRORI
   ------------------------------------------------------------------ */

STRUMENTI['mate-errori'] = function (c) {
  c.innerHTML = testaStrumento('🔁', 'I miei errori',
    'Gli errori più utili sono quelli che si ripetono: scrivili qui e non li rifarai.') + `
    <div class="card">
      <div class="barra-btn" style="margin-bottom:0">${bottone('er-nuovo', '➕', 'Aggiungi un errore', 'btn-primario btn-grande')}</div>
    </div>
    <div id="erLista"></div>`;
  disegnaErrori();
};

function disegnaErrori() {
  const box = $('#erLista');
  if (!box) return;
  box.innerHTML = Stato.erroriMate.length
    ? Stato.erroriMate.map((e) => `<div class="card">
        <h3>${esc(e.titolo || 'Errore')}</h3>
        <div class="controllo-riga"><b>❌ Come lo sbaglio</b><div class="mate">${esc(e.sbagliato)}</div></div>
        <div class="controllo-riga ok" style="margin-top:8px"><b>✅ Come si fa</b><div class="mate">${esc(e.corretto)}</div></div>
        <p style="margin-top:10px"><b>Regola da ricordare:</b> ${esc(e.regola)}</p>
        <div class="barra-btn">
          ${bottone('er-leggi:' + e.id, '🔊', 'Leggimelo', 'btn-piccolo')}
          ${bottone('er-canc:' + e.id, '🗑️', 'Elimina', 'btn-piccolo btn-errore')}
        </div>
      </div>`).join('')
    : `<div class="card">${schedaVuota('🔁', 'Non hai ancora segnato nessun errore.',
        'Esempio: −(x − 3) non fa −x − 3 ma −x + 3.')}</div>`;
}

async function nuovoErrore() {
  const r = await finestra({
    titolo: 'Un errore da non rifare',
    campi: [
      { nome: 'titolo', etichetta: 'Che argomento è?', valore: '', aiuto: 'Per esempio: parentesi con il meno davanti' },
      { nome: 'sbagliato', etichetta: '❌ Come lo sbaglio', valore: '', aiuto: 'Per esempio: -(x - 3) = -x - 3' },
      { nome: 'corretto', etichetta: '✅ Come si fa', valore: '', aiuto: 'Per esempio: -(x - 3) = -x + 3' },
      { nome: 'regola', etichetta: 'Regola da ricordare', tipo: 'area', righe: 3, valore: '' }
    ],
    testoOk: 'Salva'
  });
  if (!r) return;
  Stato.erroriMate.unshift(Object.assign({ id: uid('err'), data: oggiISO() }, r));
  await salvaOra();
  disegnaErrori();
  avvisoOk('Salvato');
}


/* ============================================================
   10-mate-grafici.js
   ============================================================ */
/* ==================================================================
   10-mate-grafici.js — piano cartesiano, retta e parabola,
   trigonometria, triangoli, logaritmi, analisi, formulario.
   ================================================================== */

/* ------------------------------------------------------------------
   Piano cartesiano riutilizzabile
   ------------------------------------------------------------------ */

const Piano = {
  xmin: -10, xmax: 10, ymin: -7, ymax: 7,
  funzioni: [],           // [{testo, ast, colore}]
  punti: [],              // [{x, y, et}]
  tela: null,

  colori: ['#1d6fa5', '#b03a2e', '#1f7a4d', '#7d3c98', '#b9770e'],

  imposta(idTela) {
    this.tela = $('#' + idTela);
    if (!this.tela) return;
    this.adatta();
    let trascina = null;
    const pos = (e) => {
      const r = this.tela.getBoundingClientRect();
      const t = (e.touches && e.touches[0]) || e;
      return { x: t.clientX - r.left, y: t.clientY - r.top, w: r.width, h: r.height };
    };
    const giu = (e) => { trascina = pos(e); };
    const muovi = (e) => {
      if (!trascina) return;
      const p = pos(e);
      const dx = (p.x - trascina.x) / p.w * (this.xmax - this.xmin);
      const dy = (p.y - trascina.y) / p.h * (this.ymax - this.ymin);
      this.xmin -= dx; this.xmax -= dx; this.ymin += dy; this.ymax += dy;
      trascina = p;
      this.disegna();
      if (e.cancelable) e.preventDefault();
    };
    const su = () => { trascina = null; };
    this.tela.addEventListener('mousedown', giu);
    this.tela.addEventListener('mousemove', muovi);
    window.addEventListener('mouseup', su);
    this.tela.addEventListener('touchstart', giu, { passive: true });
    this.tela.addEventListener('touchmove', muovi, { passive: false });
    this.tela.addEventListener('touchend', su);
    window.addEventListener('resize', () => { this.adatta(); this.disegna(); });
    this.disegna();
  },

  adatta() {
    const cv = this.tela;
    if (!cv) return;
    const S = window.devicePixelRatio || 1;
    const larg = Math.max(300, cv.parentElement.clientWidth - 20);
    const alt = Math.round(Math.min(520, Math.max(260, larg * 0.66)));
    cv.width = larg * S; cv.height = alt * S;
    cv.style.height = alt + 'px';
    // mantengo le proporzioni: un quadretto resta quadrato
    const centroY = (this.ymin + this.ymax) / 2;
    const mezzo = (this.xmax - this.xmin) * alt / larg / 2;
    this.ymin = centroY - mezzo; this.ymax = centroY + mezzo;
  },

  zoom(fattore) {
    const cx = (this.xmin + this.xmax) / 2, cy = (this.ymin + this.ymax) / 2;
    const dx = (this.xmax - this.xmin) * fattore / 2, dy = (this.ymax - this.ymin) * fattore / 2;
    this.xmin = cx - dx; this.xmax = cx + dx; this.ymin = cy - dy; this.ymax = cy + dy;
    this.disegna();
  },

  disegna() {
    const cv = this.tela;
    if (!cv) return;
    const g = cv.getContext('2d');
    const S = window.devicePixelRatio || 1;
    g.setTransform(S, 0, 0, S, 0, 0);
    const W = cv.width / S, H = cv.height / S;
    const st = getComputedStyle(document.documentElement);
    const colTesto = (st.getPropertyValue('--testo') || '#000').trim();
    const colBordo = (st.getPropertyValue('--bordo') || '#ccc').trim();
    const colSfondo = (st.getPropertyValue('--superficie') || '#fff').trim();

    const PX = (x) => (x - this.xmin) / (this.xmax - this.xmin) * W;
    const PY = (y) => H - (y - this.ymin) / (this.ymax - this.ymin) * H;

    g.fillStyle = colSfondo; g.fillRect(0, 0, W, H);

    // griglia
    const passo = passoGriglia(this.xmax - this.xmin);
    g.lineWidth = 1; g.strokeStyle = colBordo;
    g.beginPath();
    for (let x = Math.ceil(this.xmin / passo) * passo; x <= this.xmax; x += passo) {
      g.moveTo(PX(x), 0); g.lineTo(PX(x), H);
    }
    for (let y = Math.ceil(this.ymin / passo) * passo; y <= this.ymax; y += passo) {
      g.moveTo(0, PY(y)); g.lineTo(W, PY(y));
    }
    g.stroke();

    // assi
    g.strokeStyle = colTesto; g.lineWidth = 2;
    g.beginPath();
    g.moveTo(0, PY(0)); g.lineTo(W, PY(0));
    g.moveTo(PX(0), 0); g.lineTo(PX(0), H);
    g.stroke();

    // numeri sugli assi
    g.fillStyle = colTesto; g.font = '12px system-ui, sans-serif';
    g.textAlign = 'center'; g.textBaseline = 'top';
    for (let x = Math.ceil(this.xmin / passo) * passo; x <= this.xmax; x += passo) {
      if (Math.abs(x) < 1e-9) continue;
      g.fillText(etichettaNumero(x), PX(x), limita(PY(0) + 4, 0, H - 16));
    }
    g.textAlign = 'right'; g.textBaseline = 'middle';
    for (let y = Math.ceil(this.ymin / passo) * passo; y <= this.ymax; y += passo) {
      if (Math.abs(y) < 1e-9) continue;
      g.fillText(etichettaNumero(y), limita(PX(0) - 6, 24, W - 4), PY(y));
    }

    // funzioni
    this.funzioni.forEach((f, i) => {
      g.strokeStyle = f.colore || this.colori[i % this.colori.length];
      g.lineWidth = 3;
      g.beginPath();
      let primo = true, yPrec = null;
      const passiX = Math.min(1400, Math.round(W));
      for (let k = 0; k <= passiX; k++) {
        const x = this.xmin + (this.xmax - this.xmin) * k / passiX;
        let y;
        try { y = M.valuta(f.ast, { x, e: Math.E }); } catch (e) { y = NaN; }
        if (!isFinite(y)) { primo = true; yPrec = null; continue; }
        // salto agli asintoti verticali
        if (yPrec !== null && Math.abs(y - yPrec) > (this.ymax - this.ymin) * 2) primo = true;
        const px = PX(x), py = PY(y);
        if (primo) { g.moveTo(px, py); primo = false; } else g.lineTo(px, py);
        yPrec = y;
      }
      g.stroke();
    });

    // punti notevoli
    this.punti.forEach((p) => {
      g.fillStyle = colTesto;
      g.beginPath(); g.arc(PX(p.x), PY(p.y), 6, 0, 7); g.fill();
      if (p.et) {
        g.font = 'bold 13px system-ui, sans-serif'; g.textAlign = 'left'; g.textBaseline = 'bottom';
        g.fillText(p.et, PX(p.x) + 9, PY(p.y) - 6);
      }
    });
  }
};

function passoGriglia(larghezza) {
  const grezzo = larghezza / 12;
  const potenza = Math.pow(10, Math.floor(Math.log10(grezzo)));
  const n = grezzo / potenza;
  return (n < 1.5 ? 1 : n < 3.5 ? 2 : n < 7.5 ? 5 : 10) * potenza;
}
function etichettaNumero(x) {
  const v = Math.abs(x) < 1e-10 ? 0 : x;
  const s = Math.abs(v) >= 10000 || (Math.abs(v) < 0.001 && v !== 0) ? v.toExponential(1) : String(Math.round(v * 1000) / 1000);
  return s.replace('.', ',').replace('-', '−');
}

/* ------------------------------------------------------------------
   GRAFICATORE DI FUNZIONI
   ------------------------------------------------------------------ */

STRUMENTI['mate-grafico'] = function (c) {
  c.innerHTML = testaStrumento('📈', 'Grafici di funzioni',
    'Scrivi una funzione e la vedi disegnata. Puoi trascinare il piano con il dito.') + `
    <div class="card">
      <label class="etichetta" for="gfTesto">Funzione</label>
      <div class="riga-campi">
        <input class="campo" id="gfTesto" type="text" value="x^2 - 4" placeholder="Per esempio: 2x + 3" autocomplete="off" spellcheck="false">
        <button type="button" class="btn btn-primario" data-az="gf-aggiungi" style="flex:0 0 auto">
          <span aria-hidden="true">➕</span><span>Disegna</span></button>
      </div>
      ${tastieraMate('gfTesto')}
      <div id="gfElenco"></div>
    </div>
    <div class="card">
      <div class="barra-btn">
        ${bottone('gf-zoom+', '🔍', 'Ingrandisci')}
        ${bottone('gf-zoom-', '🔎', 'Rimpicciolisci')}
        ${bottone('gf-centra', '🎯', 'Rimetti al centro')}
        ${bottone('gf-zeri', '🔢', 'Trova gli zeri')}
        ${bottone('gf-inter', '✖️', 'Intersezioni')}
      </div>
      <div class="tela-wrap"><canvas class="tela" id="telaPiano"></canvas></div>
      <p class="aiutino">Trascina per spostarti. Gli assi si incontrano nell'origine (0; 0).</p>
      <div id="gfNote"></div>
    </div>`;
  setTimeout(() => { Piano.imposta('telaPiano'); disegnaElencoFunzioni(); }, 30);
};

function aggiungiFunzione(testo) {
  const t = (testo || $('#gfTesto').value || '').trim().replace(/^y\s*=\s*/i, '').replace(/^f\(x\)\s*=\s*/i, '');
  if (!t) return;
  try {
    const ast = M.analizza(t);
    M.valuta(ast, { x: 1, e: Math.E });      // prova: deve funzionare
    Piano.funzioni.push({ testo: t, ast, colore: Piano.colori[Piano.funzioni.length % Piano.colori.length] });
    disegnaElencoFunzioni();
    Piano.disegna();
  } catch (e) {
    avvisoErrore(e.amichevole ? e.message : 'Non riesco a leggere questa funzione. Scrivila per esempio così: x^2 - 4');
  }
}

function disegnaElencoFunzioni() {
  const box = $('#gfElenco');
  if (!box) return;
  box.innerHTML = Piano.funzioni.length
    ? `<ul class="lista" style="margin-top:12px">${Piano.funzioni.map((f, i) => `
      <li class="voce" style="align-items:center">
        <span class="pallino" style="background:${f.colore}"></span>
        <div class="corpo"><b>${i === 0 ? 'f' : i === 1 ? 'g' : 'h' + i}(x) = ${esc(f.testo)}</b></div>
        <div class="azioni">${bottone('gf-togli:' + i, '🗑️', 'Togli', 'btn-piccolo')}</div>
      </li>`).join('')}</ul>`
    : '<p class="aiutino">Nessuna funzione disegnata.</p>';
}

/** Zeri numerici: cerco i cambi di segno e affino per bisezione. */
function zeriFunzione(f) {
  const zeri = [];
  const N = 2000;
  let xPrec = Piano.xmin, yPrec = valoreSicuro(f, xPrec);
  for (let i = 1; i <= N; i++) {
    const x = Piano.xmin + (Piano.xmax - Piano.xmin) * i / N;
    const y = valoreSicuro(f, x);
    if (yPrec !== null && y !== null && yPrec * y < 0) {
      let a = xPrec, b = x;
      for (let k = 0; k < 60; k++) {
        const m = (a + b) / 2, ym = valoreSicuro(f, m);
        if (ym === null) break;
        if (valoreSicuro(f, a) * ym <= 0) b = m; else a = m;
      }
      const r = (a + b) / 2;
      if (Math.abs(valoreSicuro(f, r) || 1) < 1e-6) zeri.push(r);
    }
    xPrec = x; yPrec = y;
  }
  return zeri;
}
function valoreSicuro(f, x) {
  try { const v = M.valuta(f.ast, { x, e: Math.E }); return isFinite(v) ? v : null; }
  catch (e) { return null; }
}

function trovaZeri() {
  if (!Piano.funzioni.length) { toast('Prima disegna una funzione.'); return; }
  Piano.punti = [];
  let testo = '';
  Piano.funzioni.forEach((f, i) => {
    const z = zeriFunzione(f);
    z.forEach((x) => Piano.punti.push({ x, y: 0, et: Risolutore.numeroIt(x) }));
    testo += `<p><b>${i === 0 ? 'f' : 'g'}(x) = ${esc(f.testo)}</b>: ` +
      (z.length ? 'taglia l\'asse x in ' + z.map((x) => 'x = ' + Risolutore.numeroIt(x)).join(', ')
        : 'non taglia l\'asse x in questa parte di piano') + '</p>';
  });
  Piano.disegna();
  $('#gfNote').innerHTML = `<div class="avviso"><span class="ic">🔢</span><div>${testo}
    <p class="aiutino">Gli zeri sono i punti in cui la funzione vale 0: sono le soluzioni dell'equazione f(x) = 0.</p></div></div>`;
}

function trovaIntersezioni() {
  if (Piano.funzioni.length < 2) { toast('Servono almeno due funzioni.'); return; }
  const f = Piano.funzioni[0], g = Piano.funzioni[1];
  const diff = { ast: M.nSomma([f.ast, M.nProd([M.nNum(M.fr(-1)), g.ast])]) };
  const z = zeriFunzione(diff);
  Piano.punti = z.map((x) => ({ x, y: valoreSicuro(f, x) || 0, et: '(' + Risolutore.numeroIt(x) + '; ' + Risolutore.numeroIt(valoreSicuro(f, x) || 0) + ')' }));
  Piano.disegna();
  $('#gfNote').innerHTML = `<div class="avviso"><span class="ic">✖️</span><div>
    ${z.length ? '<p>Le due funzioni si incontrano in ' + z.length + (z.length === 1 ? ' punto.' : ' punti.') + '</p>'
      : '<p>In questa parte di piano non si incontrano.</p>'}
    <p class="aiutino">Le intersezioni sono i punti dove le due curve hanno la stessa y: cioè le soluzioni di f(x) = g(x).</p></div></div>`;
}

/* ------------------------------------------------------------------
   RETTA E PARABOLA
   ------------------------------------------------------------------ */

STRUMENTI['mate-rettapar'] = function (c) {
  c.innerHTML = testaStrumento('📉', 'Retta e parabola',
    'Scrivi l\'equazione: ti mostro i dati importanti e il disegno.') + `
    <div class="card">
      <label class="etichetta" for="rpTesto">Equazione</label>
      <input class="campo" id="rpTesto" type="text" value="y = x^2 - 4x + 3" autocomplete="off" spellcheck="false">
      ${tastieraMate('rpTesto')}
      <div class="barra-btn">
        ${bottone('rp-studia', '🔍', 'Studiala', 'btn-primario btn-grande')}
        ${bottone('rp-esempio-retta', '📏', 'Esempio retta')}
        ${bottone('rp-esempio-par', '🥣', 'Esempio parabola')}
      </div>
    </div>
    <div id="risultatoMate"></div>
    <div class="card"><div class="tela-wrap"><canvas class="tela" id="telaPiano"></canvas></div></div>`;
  setTimeout(() => Piano.imposta('telaPiano'), 30);
};

function studiaRettaParabola() {
  const t = $('#rpTesto').value.trim();
  if (!t) return;
  const senzaY = M.normalizzaTesto(t).replace(/^y\s*=\s*/i, '');
  let esito;
  let grado = 1;
  try {
    const P = M.polDaAst(M.analizza(senzaY));
    grado = P ? M.polGrado(P, 'x') : 1;
  } catch (e) { /* deciderà il risolutore */ }
  esito = grado >= 2 ? Risolutore.studioParabola(t) : Risolutore.studioRetta(t);
  _passi.unoAllaVolta = false;
  mostraPassi(esito, '#risultatoMate');

  Piano.funzioni = [];
  Piano.punti = [];
  try {
    const ast = M.analizza(senzaY);
    Piano.funzioni = [{ testo: senzaY, ast, colore: Piano.colori[0] }];
    if (esito.vertice) {
      Piano.punti.push({ x: M.fNum(esito.vertice.x), y: M.fNum(esito.vertice.y), et: 'V' });
      const dx = Math.max(6, Math.abs(M.fNum(esito.vertice.x)) + 5);
      Piano.xmin = M.fNum(esito.vertice.x) - dx; Piano.xmax = M.fNum(esito.vertice.x) + dx;
      Piano.ymin = M.fNum(esito.vertice.y) - dx; Piano.ymax = M.fNum(esito.vertice.y) + dx;
      Piano.adatta();
    }
    Piano.disegna();
  } catch (e) { /* il grafico resta vuoto */ }
}

/* ------------------------------------------------------------------
   TRIGONOMETRIA: circonferenza goniometrica
   ------------------------------------------------------------------ */

const Trigo = { angolo: 30 };

STRUMENTI['mate-trigono'] = function (c) {
  c.innerHTML = testaStrumento('⭕', 'Circonferenza goniometrica',
    'Muovi l\'angolo e guarda come cambiano seno, coseno e tangente.') + `
    <div class="card">
      <div class="slider-riga">
        <label for="trAng">Angolo</label><output id="trOut">${Trigo.angolo}°</output>
        <input type="range" id="trAng" min="0" max="360" step="1" value="${Trigo.angolo}">
      </div>
      <div class="barra-btn">
        ${[0, 30, 45, 60, 90, 180, 270].map((a) => `<button type="button" class="btn btn-piccolo" data-az="tr-vai:${a}">${a}°</button>`).join('')}
      </div>
      <div class="tela-wrap"><canvas class="tela" id="telaTrigo" style="max-width:520px;margin:0 auto"></canvas></div>
      <div class="perc-blocchi" id="trValori" style="margin-top:14px"></div>
      <div class="barra-btn" style="margin-top:12px">${bottone('tr-leggi', '🔊', 'Leggi i valori')}</div>
    </div>
    <div class="card card-soft">
      <h3>Da ricordare</h3>
      <p>Il <b>coseno</b> si legge sull'asse orizzontale (quanto vado a destra o a sinistra).<br>
         Il <b>seno</b> si legge sull'asse verticale (quanto vado su o giù).<br>
         La <b>tangente</b> è seno diviso coseno: quando il coseno è zero non esiste.</p>
      <div class="mate mate-blocco">sen²α + cos²α = 1</div>
      <p class="aiutino">Questa è l'identità fondamentale: vale per qualsiasi angolo.</p>
    </div>`;
  const rg = $('#trAng', c);
  rg.addEventListener('input', () => { Trigo.angolo = Number(rg.value); disegnaTrigo(); });
  setTimeout(disegnaTrigo, 30);
};

function disegnaTrigo() {
  const cv = $('#telaTrigo');
  if (!cv) return;
  const S = window.devicePixelRatio || 1;
  const lato = Math.min(520, Math.max(280, cv.parentElement.clientWidth - 20));
  cv.width = lato * S; cv.height = lato * S; cv.style.height = lato + 'px';
  const g = cv.getContext('2d');
  g.setTransform(S, 0, 0, S, 0, 0);
  const W = lato, H = lato, cx = W / 2, cy = H / 2, R = W * 0.36;
  const st = getComputedStyle(document.documentElement);
  const colTesto = (st.getPropertyValue('--testo') || '#000').trim();
  const colBordo = (st.getPropertyValue('--bordo') || '#ccc').trim();
  const colPrim = (st.getPropertyValue('--primario') || '#1d4e6f').trim();

  g.clearRect(0, 0, W, H);
  g.strokeStyle = colBordo; g.lineWidth = 1;
  g.beginPath(); g.moveTo(0, cy); g.lineTo(W, cy); g.moveTo(cx, 0); g.lineTo(cx, H); g.stroke();
  g.strokeStyle = colTesto; g.lineWidth = 2;
  g.beginPath(); g.arc(cx, cy, R, 0, Math.PI * 2); g.stroke();

  const a = Trigo.angolo * Math.PI / 180;
  const px = cx + R * Math.cos(a), py = cy - R * Math.sin(a);

  // archi e segmenti
  g.strokeStyle = colPrim; g.lineWidth = 3;
  g.beginPath(); g.moveTo(cx, cy); g.lineTo(px, py); g.stroke();
  g.setLineDash([6, 5]); g.lineWidth = 2;
  g.beginPath(); g.moveTo(px, py); g.lineTo(px, cy); g.stroke();     // seno
  g.beginPath(); g.moveTo(px, cy); g.lineTo(cx, cy); g.stroke();     // coseno
  g.setLineDash([]);
  g.fillStyle = colPrim;
  g.beginPath(); g.arc(px, py, 7, 0, 7); g.fill();

  g.strokeStyle = colPrim; g.lineWidth = 2;
  g.beginPath(); g.arc(cx, cy, 26, 0, -a, true); g.stroke();
  g.fillStyle = colTesto; g.font = 'bold 14px system-ui, sans-serif';
  g.fillText(Trigo.angolo + '°', cx + 32, cy - 12);
  g.font = '13px system-ui, sans-serif';
  g.fillText('sen', px + 8, (py + cy) / 2);
  g.fillText('cos', (px + cx) / 2 - 12, cy + 18);

  const sen = Math.sin(a), cos = Math.cos(a);
  const tan = Math.abs(cos) < 1e-12 ? null : sen / cos;
  const arr = (v) => Risolutore.numeroIt(Math.round(v * 10000) / 10000);
  $('#trOut').textContent = Trigo.angolo + '°';
  $('#trValori').innerHTML = `
    <div class="blocco"><div class="et">Gradi</div><div class="val">${Trigo.angolo}°</div></div>
    <div class="blocco"><div class="et">Radianti</div><div class="val">${esc(radianti(Trigo.angolo))}</div></div>
    <div class="blocco"><div class="et">Seno</div><div class="val">${esc(arr(sen))}</div></div>
    <div class="blocco"><div class="et">Coseno</div><div class="val">${esc(arr(cos))}</div></div>
    <div class="blocco"><div class="et">Tangente</div><div class="val">${tan === null ? 'non esiste' : esc(arr(tan))}</div></div>`;
}

function radianti(gradi) {
  const g = ((gradi % 360) + 360) % 360;
  const noti = { 0: '0', 30: 'π/6', 45: 'π/4', 60: 'π/3', 90: 'π/2', 120: '2π/3', 135: '3π/4',
    150: '5π/6', 180: 'π', 210: '7π/6', 225: '5π/4', 240: '4π/3', 270: '3π/2', 300: '5π/3', 315: '7π/4', 330: '11π/6' };
  if (noti[g] !== undefined) return noti[g];
  return Risolutore.numeroIt(Math.round(g * Math.PI / 180 * 1000) / 1000);
}

/* ------------------------------------------------------------------
   TRIANGOLI
   ------------------------------------------------------------------ */

STRUMENTI['mate-triangoli'] = function (c) {
  c.innerHTML = testaStrumento('🔺', 'Triangoli',
    'Scrivi i dati che conosci e lascia vuoto il resto: cerco io il teorema giusto.') + `
    <div class="card">
      <p class="aiutino">I lati si chiamano a, b, c. Ogni angolo sta di fronte al lato con la stessa lettera:
        l'angolo A è opposto al lato a.</p>
      <div class="riga-campi">
        <div><label class="etichetta" for="triA">Lato a</label><input class="campo" id="triA" type="number" step="any"></div>
        <div><label class="etichetta" for="triB">Lato b</label><input class="campo" id="triB" type="number" step="any"></div>
        <div><label class="etichetta" for="triC">Lato c</label><input class="campo" id="triC" type="number" step="any"></div>
      </div>
      <div class="riga-campi">
        <div><label class="etichetta" for="triAA">Angolo A (gradi)</label><input class="campo" id="triAA" type="number" step="any"></div>
        <div><label class="etichetta" for="triBB">Angolo B (gradi)</label><input class="campo" id="triBB" type="number" step="any"></div>
        <div><label class="etichetta" for="triCC">Angolo C (gradi)</label><input class="campo" id="triCC" type="number" step="any"></div>
      </div>
      <div class="barra-btn" style="margin-top:12px">
        ${bottone('tri-risolvi', '🔍', 'Risolvi il triangolo', 'btn-primario btn-grande')}
        ${bottone('tri-esempio', '💡', 'Esempio')}
      </div>
    </div>
    <div id="triRis"></div>`;
};

function risolviTriangolo() {
  const n = (id) => { const v = $(id).value.trim(); return v === '' ? null : Number(v); };
  let a = n('#triA'), b = n('#triB'), c = n('#triC');
  let A = n('#triAA'), B = n('#triBB'), C = n('#triCC');
  const box = $('#triRis');
  const passi = [];
  const rad = (x) => x * Math.PI / 180, gra = (x) => x * 180 / Math.PI;
  const num = Risolutore.numeroIt;

  const datiLati = [a, b, c].filter((x) => x !== null && x > 0).length;
  const datiAngoli = [A, B, C].filter((x) => x !== null && x > 0).length;
  if (datiLati + datiAngoli < 3) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">✏️</span>
      <p>Mi servono almeno tre dati (e almeno un lato) per risolvere il triangolo.</p></div>`;
    return;
  }
  if (datiAngoli && [A, B, C].reduce((s, x) => s + (x || 0), 0) > 180.01) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">⚠️</span>
      <p>La somma degli angoli di un triangolo è 180°: i dati che hai scritto non possono stare insieme.</p></div>`;
    return;
  }

  // terzo angolo
  if (datiAngoli === 2) {
    const somma = (A || 0) + (B || 0) + (C || 0);
    const manca = 180 - somma;
    if (A === null) { A = manca; } else if (B === null) { B = manca; } else { C = manca; }
    passi.push({ tit: 'Terzo angolo', html: '<div class="mate mate-blocco">180° − (somma degli altri due) = ' + num(manca) + '°</div>',
      spiega: 'La somma degli angoli interni di un triangolo è sempre 180°.' });
  }

  if (datiLati === 3) {
    // teorema del coseno per tutti gli angoli
    A = gra(Math.acos((b * b + c * c - a * a) / (2 * b * c)));
    B = gra(Math.acos((a * a + c * c - b * b) / (2 * a * c)));
    C = 180 - A - B;
    const rettangolo = [A, B, C].some((x) => Math.abs(x - 90) < 0.01);
    passi.push({ tit: 'Conosco i tre lati', html: '<div class="mate mate-blocco">cos A = (b² + c² − a²) / (2bc)</div>',
      spiega: 'Con i tre lati uso il teorema del coseno per trovare gli angoli.',
      perche: 'Il teorema del coseno è la versione "generale" di Pitagora: se l\'angolo è di 90° il termine con il coseno sparisce e resta a² = b² + c².' });
    if (rettangolo) passi.push({ tit: 'È un triangolo rettangolo!', html: '<div class="mate mate-blocco">a² = b² + c²</div>', spiega: 'Uno degli angoli è di 90°: qui vale il teorema di Pitagora.' });
  } else if (datiLati === 2 && datiAngoli >= 1) {
    // lato mancante
    if (a === null && A !== null) { a = Math.sqrt(b * b + c * c - 2 * b * c * Math.cos(rad(A))); }
    else if (b === null && B !== null) { b = Math.sqrt(a * a + c * c - 2 * a * c * Math.cos(rad(B))); }
    else if (c === null && C !== null) { c = Math.sqrt(a * a + b * b - 2 * a * b * Math.cos(rad(C))); }
    else {
      // teorema dei seni
      if (a !== null && A !== null && b !== null) { B = gra(Math.asin(b * Math.sin(rad(A)) / a)); C = 180 - A - B; c = a * Math.sin(rad(C)) / Math.sin(rad(A)); }
    }
    passi.push({ tit: 'Due lati e un angolo', html: '<div class="mate mate-blocco">a² = b² + c² − 2bc·cos A</div>',
      spiega: 'Uso il teorema del coseno per trovare il lato che manca.' });
    if (a && b && c) {
      A = gra(Math.acos((b * b + c * c - a * a) / (2 * b * c)));
      B = gra(Math.acos((a * a + c * c - b * b) / (2 * a * c)));
      C = 180 - A - B;
    }
  } else if (datiLati === 1 && datiAngoli >= 2) {
    const noto = a !== null ? { l: a, ang: A } : b !== null ? { l: b, ang: B } : { l: c, ang: C };
    if (noto.ang) {
      const k = noto.l / Math.sin(rad(noto.ang));
      if (a === null && A) a = k * Math.sin(rad(A));
      if (b === null && B) b = k * Math.sin(rad(B));
      if (c === null && C) c = k * Math.sin(rad(C));
      passi.push({ tit: 'Un lato e due angoli', html: '<div class="mate mate-blocco">a / sen A = b / sen B = c / sen C</div>',
        spiega: 'Uso il teorema dei seni: il rapporto fra un lato e il seno del suo angolo opposto è sempre lo stesso.' });
    }
  }

  if (![a, b, c].every((x) => x && isFinite(x))) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">🤔</span>
      <p>Con questi dati non riesco a risolvere il triangolo in modo sicuro. Controlla di aver messo l'angolo giusto,
      oppure aggiungi un altro dato.</p></div>`;
    return;
  }

  const p = (a + b + c) / 2;
  const area = Math.sqrt(Math.max(0, p * (p - a) * (p - b) * (p - c)));

  box.innerHTML = `<div class="card">
      ${disegnaTriangoloSvg(a, b, c)}
      <div class="perc-blocchi" style="margin-top:14px">
        <div class="blocco"><div class="et">Lato a</div><div class="val">${num(a)}</div></div>
        <div class="blocco"><div class="et">Lato b</div><div class="val">${num(b)}</div></div>
        <div class="blocco"><div class="et">Lato c</div><div class="val">${num(c)}</div></div>
        <div class="blocco"><div class="et">Angolo A</div><div class="val">${num(A)}°</div></div>
        <div class="blocco"><div class="et">Angolo B</div><div class="val">${num(B)}°</div></div>
        <div class="blocco"><div class="et">Angolo C</div><div class="val">${num(C)}°</div></div>
        <div class="blocco"><div class="et">Perimetro</div><div class="val">${num(a + b + c)}</div></div>
        <div class="blocco" style="border-color:var(--ok);background:var(--ok-chiaro)"><div class="et">Area</div><div class="val">${num(Math.round(area * 10000) / 10000)}</div></div>
      </div>
    </div>
    ${passi.map((s) => `<div class="passo"><div class="numero">${esc(s.tit)}</div>${s.html}
      <div class="spiega">${esc(s.spiega)}</div>${s.perche ? `<div class="aiuto-testo" style="margin-top:8px">${esc(s.perche)}</div>` : ''}</div>`).join('')}
    <div class="card card-soft"><p class="aiutino">L'area l'ho calcolata con la formula di Erone:
      p = (a+b+c)/2, area = √(p(p−a)(p−b)(p−c)).</p></div>`;
}

function disegnaTriangoloSvg(a, b, c) {
  // metto il lato c in orizzontale e calcolo il terzo vertice
  const W = 320, H = 220;
  const scala = Math.min(W - 60, H - 60) / Math.max(a, b, c);
  const cx = c * scala;
  const x = (b * b + cx * cx - a * a * scala * scala / (scala * scala)) / (2 * cx);
  const px = (b * b * scala * scala + cx * cx - a * a * scala * scala) / (2 * cx);
  const py = Math.sqrt(Math.max(0, b * b * scala * scala - px * px));
  const offX = (W - cx) / 2, offY = H - 30;
  const P = [[offX, offY], [offX + cx, offY], [offX + px, offY - py]];
  return `<svg viewBox="0 0 ${W} ${H}" style="width:100%;max-width:340px;display:block;margin:0 auto" role="img" aria-label="disegno del triangolo">
    <polygon points="${P.map((q) => q[0].toFixed(1) + ',' + q[1].toFixed(1)).join(' ')}"
      fill="var(--primario-chiaro)" stroke="var(--primario)" stroke-width="3"/>
    <text x="${(P[0][0] + P[1][0]) / 2}" y="${P[0][1] + 20}" text-anchor="middle" font-size="15" fill="currentColor">c</text>
    <text x="${(P[1][0] + P[2][0]) / 2 + 12}" y="${(P[1][1] + P[2][1]) / 2}" font-size="15" fill="currentColor">a</text>
    <text x="${(P[0][0] + P[2][0]) / 2 - 18}" y="${(P[0][1] + P[2][1]) / 2}" font-size="15" fill="currentColor">b</text>
    <text x="${P[0][0] - 4}" y="${P[0][1] + 4}" text-anchor="end" font-size="14" font-weight="bold" fill="currentColor">A</text>
    <text x="${P[1][0] + 6}" y="${P[1][1] + 4}" font-size="14" font-weight="bold" fill="currentColor">B</text>
    <text x="${P[2][0]}" y="${P[2][1] - 8}" text-anchor="middle" font-size="14" font-weight="bold" fill="currentColor">C</text>
  </svg>`;
}

/* ------------------------------------------------------------------
   LOGARITMI E POTENZE
   ------------------------------------------------------------------ */

STRUMENTI['mate-loghi'] = function (c) {
  c.innerHTML = testaStrumento('🔟', 'Logaritmi e potenze',
    'Il logaritmo risponde alla domanda: "a quale potenza devo elevare la base?"') + `
    <div class="card">
      <div class="riga-campi">
        <div><label class="etichetta" for="lgBase">Base</label><input class="campo" id="lgBase" type="number" step="any" value="2"></div>
        <div><label class="etichetta" for="lgArg">Numero</label><input class="campo" id="lgArg" type="number" step="any" value="8"></div>
      </div>
      <div class="barra-btn" style="margin-top:12px">${bottone('lg-calcola', '▶', 'Fammi vedere', 'btn-primario btn-grande')}</div>
      <div id="lgRis"></div>
    </div>
    <div class="card card-soft">
      <h3>Le proprietà da ricordare</h3>
      <div class="cosa-faccio">
        <div class="riga"><div class="mate">log<sub>a</sub>(x·y) = log<sub>a</sub>x + log<sub>a</sub>y</div><div class="nota">Il prodotto dentro diventa una somma fuori.</div></div>
        <div class="riga"><div class="mate">log<sub>a</sub>(x/y) = log<sub>a</sub>x − log<sub>a</sub>y</div><div class="nota">La divisione diventa una sottrazione.</div></div>
        <div class="riga"><div class="mate">log<sub>a</sub>(x<sup>n</sup>) = n · log<sub>a</sub>x</div><div class="nota">L'esponente viene davanti.</div></div>
        <div class="riga"><div class="mate">log<sub>a</sub>1 = 0</div><div class="nota">Qualsiasi numero elevato a 0 fa 1.</div></div>
        <div class="riga"><div class="mate">log<sub>a</sub>a = 1</div><div class="nota">La base elevata a 1 dà se stessa.</div></div>
      </div>
      <div class="avviso avviso-att" style="margin-top:12px"><span class="ic">⚠️</span>
        <p>Il numero dentro al logaritmo deve essere <b>maggiore di zero</b>, e la base deve essere positiva e diversa da 1.</p></div>
    </div>`;
};

function calcolaLogaritmo() {
  const b = Number($('#lgBase').value), x = Number($('#lgArg').value);
  const box = $('#lgRis');
  if (!(b > 0) || b === 1 || !(x > 0)) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">⚠️</span>
      <p>La base deve essere positiva e diversa da 1, e il numero deve essere maggiore di zero.</p></div>`;
    return;
  }
  const y = Math.log(x) / Math.log(b);
  const esatto = Math.abs(y - Math.round(y)) < 1e-9;
  box.innerHTML = `
    <div class="passo"><div class="numero">La domanda</div>
      <div class="mate mate-blocco">log<sub>${esc(b)}</sub> ${esc(x)} = ?</div>
      <div class="spiega">A quale potenza devo elevare ${esc(String(b))} per ottenere ${esc(String(x))}?</div></div>
    <div class="passo controllo"><div class="numero">La risposta</div>
      <div class="mate mate-blocco">${esc(Risolutore.numeroIt(esatto ? Math.round(y) : Math.round(y * 10000) / 10000))}</div>
      <div class="spiega">${esatto ? 'Infatti ' + b + '^' + Math.round(y) + ' = ' + x + '.' : 'Non viene un numero intero: questo è il valore approssimato.'}</div></div>
    <div class="passo"><div class="numero">Le due facce della stessa medaglia</div>
      <div class="mate mate-blocco">log<sub>${esc(b)}</sub> ${esc(x)} = ${esc(Risolutore.numeroIt(esatto ? Math.round(y) : Math.round(y * 100) / 100))}
        &nbsp;&nbsp;⇄&nbsp;&nbsp; ${esc(b)}<sup>${esc(Risolutore.numeroIt(esatto ? Math.round(y) : Math.round(y * 100) / 100))}</sup> = ${esc(x)}</div>
      <div class="spiega">Logaritmo e potenza dicono la stessa cosa, girata al contrario.</div></div>`;
}

/* ------------------------------------------------------------------
   ANALISI: derivate, limiti, integrali
   ------------------------------------------------------------------ */

STRUMENTI['mate-analisi'] = function (c) {
  c.innerHTML = testaStrumento('∫', 'Derivate, limiti e integrali', null) + `
    <div class="card">
      <label class="etichetta" for="anTesto">La funzione</label>
      <input class="campo" id="anTesto" type="text" value="3x^2 + 2x - 5" autocomplete="off" spellcheck="false">
      ${tastieraMate('anTesto')}
      <div class="riga-campi">
        <div>
          <label class="etichetta" for="anVerso">Per il limite: x tende a</label>
          <input class="campo" id="anVerso" type="text" value="0" placeholder="un numero, oppure inf">
        </div>
      </div>
      <div class="barra-btn" style="margin-top:12px">
        ${bottone('an-derivata', 'f′', 'Derivata', 'btn-primario btn-grande')}
        ${bottone('an-limite', 'lim', 'Limite', 'btn-grande')}
        ${bottone('an-integrale', '∫', 'Integrale', 'btn-grande')}
        ${bottone('an-grafico', '📈', 'Vedi il grafico', 'btn-grande')}
      </div>
    </div>
    <div id="risultatoMate"></div>
    <div class="card"><div class="tela-wrap"><canvas class="tela" id="telaPiano"></canvas></div></div>`;
  setTimeout(() => Piano.imposta('telaPiano'), 30);
};

/* ------------------------------------------------------------------
   STUDIAMO UNA FUNZIONE
   ------------------------------------------------------------------ */

const PASSI_STUDIO = [
  { id: 'dominio', tit: 'Dominio' },
  { id: 'simmetrie', tit: 'Simmetrie' },
  { id: 'interx', tit: 'Intersezione con l\'asse x' },
  { id: 'intery', tit: 'Intersezione con l\'asse y' },
  { id: 'segno', tit: 'Segno' },
  { id: 'limiti', tit: 'Limiti agli estremi' },
  { id: 'asintoti', tit: 'Asintoti' },
  { id: 'derivata1', tit: 'Derivata prima' },
  { id: 'crescenza', tit: 'Crescenza, massimi e minimi' },
  { id: 'derivata2', tit: 'Derivata seconda' },
  { id: 'concavita', tit: 'Concavità e flessi' },
  { id: 'grafico', tit: 'Grafico finale' }
];

const Studio = { funzione: 'x^2 - 4', fatti: {}, risultati: {} };

STRUMENTI['mate-studiofun'] = function (c) {
  c.innerHTML = testaStrumento('📊', 'Studiamo una funzione',
    'Una cosa alla volta: spunta i passaggi man mano che li capisci.') + `
    <div class="card">
      <label class="etichetta" for="sfTesto">La funzione</label>
      <input class="campo" id="sfTesto" type="text" value="${esc(Studio.funzione)}" autocomplete="off" spellcheck="false">
      ${tastieraMate('sfTesto')}
      <div class="barra-btn">${bottone('sf-avvia', '▶', 'Cominciamo', 'btn-primario btn-grande')}</div>
    </div>
    <div id="sfPassi"></div>`;
  disegnaChecklistStudio();
};

function disegnaChecklistStudio() {
  const box = $('#sfPassi');
  if (!box) return;
  box.innerHTML = `<div class="card">
    <h2>La scaletta</h2>
    ${PASSI_STUDIO.map((p) => `
      <div class="voce" style="align-items:center;margin-bottom:8px">
        <label class="check" style="flex:1 1 auto;min-height:0">
          <input type="checkbox" data-studio="${p.id}"${Studio.fatti[p.id] ? ' checked' : ''}>
          <span>${esc(p.tit)}</span>
        </label>
        <div class="azioni">${bottone('sf-passo:' + p.id, '🔍', 'Fai questo', 'btn-piccolo')}</div>
      </div>`).join('')}
    <p class="aiutino">Non serve fare tutto oggi. Spunta quello che hai capito.</p>
  </div>
  <div id="sfRisultato"></div>`;
  box.addEventListener('change', (e) => {
    const n = e.target.closest('[data-studio]');
    if (!n) return;
    Studio.fatti[n.dataset.studio] = n.checked;
  });
}

function eseguiPassoStudio(id) {
  const t = ($('#sfTesto') || {}).value || Studio.funzione;
  Studio.funzione = t.replace(/^y\s*=\s*/i, '').replace(/^f\(x\)\s*=\s*/i, '');
  const box = $('#sfRisultato');
  let ast, rat;
  try { ast = M.analizza(Studio.funzione); rat = M.ratDaAst(ast); }
  catch (e) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">✏️</span><p>Non riesco a leggere questa funzione.</p></div>`;
    return;
  }
  const titolo = (PASSI_STUDIO.find((p) => p.id === id) || {}).tit || '';
  let html = '', spiega = '', stato = 'verde';

  const zeriDi = (P) => {
    const c = P ? M.polCoeffNum(P, 'x') : null;
    return c ? M.radiciRazionali(c) : null;
  };

  switch (id) {
    case 'dominio': {
      if (rat && M.polCostante(rat.d)) { html = '<div class="mate mate-blocco">D = ℝ</div>'; spiega = 'Non ci sono denominatori con la x, né radici, né logaritmi: va bene ogni numero.'; }
      else if (rat) {
        const z = zeriDi(rat.d);
        html = '<div class="mate mate-blocco">' + (z && z.length ? 'x ≠ ' + z.map((r) => M.fTesto(r).replace('-', '−')).join(', &nbsp; x ≠ ') : 'D = ℝ') + '</div>';
        spiega = 'Il denominatore non può valere zero: quei valori vanno tolti dal dominio.';
      } else {
        stato = 'giallo';
        html = '<div class="mate mate-blocco">Da controllare a mano</div>';
        spiega = 'Questa funzione contiene radici, logaritmi o altre cose: guarda tu le condizioni (radice pari ≥ 0, argomento del logaritmo > 0, denominatore ≠ 0).';
      }
      break;
    }
    case 'simmetrie': {
      let pari = true, dispari = true;
      for (const x of [0.7, 1.3, 2.1, 3.4]) {
        try {
          const f1 = M.valuta(ast, { x }), f2 = M.valuta(ast, { x: -x });
          if (Math.abs(f1 - f2) > 1e-9) pari = false;
          if (Math.abs(f1 + f2) > 1e-9) dispari = false;
        } catch (e) { pari = dispari = false; }
      }
      html = '<div class="mate mate-blocco">' + (pari ? 'Funzione PARI: f(−x) = f(x)' : dispari ? 'Funzione DISPARI: f(−x) = −f(x)' : 'Né pari né dispari') + '</div>';
      spiega = pari ? 'Il grafico è simmetrico rispetto all\'asse y.' : dispari ? 'Il grafico è simmetrico rispetto all\'origine.' : 'Non c\'è una simmetria semplice.';
      break;
    }
    case 'interx': {
      const z = rat ? zeriDi(rat.n) : null;
      if (z && z.length) { html = '<div class="mate mate-blocco">' + z.map((r) => '(' + M.fTesto(r).replace('-', '−') + '; 0)').join(' &nbsp; ') + '</div>'; spiega = 'Ho messo y = 0 e risolto: sono i punti dove il grafico taglia l\'asse orizzontale.'; }
      else {
        const f = { ast };
        Piano.xmin = -10; Piano.xmax = 10;
        const zn = zeriFunzione(f);
        stato = zn.length ? 'giallo' : 'giallo';
        html = '<div class="mate mate-blocco">' + (zn.length ? zn.map((x) => '≈ (' + Risolutore.numeroIt(x) + '; 0)').join(' &nbsp; ') : 'Nessuno fra −10 e 10') + '</div>';
        spiega = 'Questi valori li ho trovati numericamente (approssimati), non con i passaggi algebrici.';
      }
      break;
    }
    case 'intery': {
      try {
        const y0 = M.valuta(ast, { x: 0 });
        html = '<div class="mate mate-blocco">(0; ' + Risolutore.numeroIt(y0) + ')</div>';
        spiega = 'Ho messo x = 0 nella funzione.';
      } catch (e) { html = '<div class="mate mate-blocco">Non esiste</div>'; spiega = 'In x = 0 la funzione non è definita.'; }
      break;
    }
    case 'segno': {
      const z = rat ? (zeriDi(rat.n) || []).concat(zeriDi(rat.d) || []) : [];
      const punti = z.map((r) => M.fNum(r)).sort((a, b) => a - b);
      const intervalli = [];
      const bordi = [-Infinity].concat(punti, [Infinity]);
      for (let i = 0; i < bordi.length - 1; i++) {
        const a = bordi[i], b = bordi[i + 1];
        const campione = !isFinite(a) ? b - 1 : !isFinite(b) ? a + 1 : (a + b) / 2;
        let v;
        try { v = M.valuta(ast, { x: campione }); } catch (e) { v = NaN; }
        intervalli.push({ da: a, a: b, segno: v > 0 ? '+' : v < 0 ? '−' : '0' });
      }
      html = '<div class="cosa-faccio">' + intervalli.map((iv) =>
        `<div class="riga"><div class="mate">${isFinite(iv.da) ? Risolutore.numeroIt(iv.da) : '−∞'} &lt; x &lt; ${isFinite(iv.a) ? Risolutore.numeroIt(iv.a) : '+∞'}</div>
         <div class="nota">la funzione è ${iv.segno === '+' ? 'POSITIVA (sopra l\'asse x)' : iv.segno === '−' ? 'NEGATIVA (sotto l\'asse x)' : 'nulla'}</div></div>`).join('') + '</div>';
      spiega = 'Ho diviso la retta nei punti dove la funzione vale zero o non esiste, e ho provato un valore dentro ogni pezzo.';
      break;
    }
    case 'limiti': {
      const e1 = Risolutore.limitePassi(Studio.funzione, 'inf');
      const e2 = Risolutore.limitePassi(Studio.funzione, '-inf');
      html = '<div class="mate mate-blocco">lim<sub>x → +∞</sub> f(x) = ' + esc(String(e1.risultato === undefined ? '?' : e1.risultato)) +
        '<br>lim<sub>x → −∞</sub> f(x) = ' + esc(String(e2.risultato === undefined ? '?' : e2.risultato)) + '</div>';
      spiega = 'Guardo come si comporta la funzione quando la x diventa molto grande, in positivo e in negativo.';
      stato = (e1.stato === 'verde' && e2.stato === 'verde') ? 'verde' : 'giallo';
      break;
    }
    case 'asintoti': {
      const righe = [];
      if (rat && !M.polCostante(rat.d)) {
        const z = zeriDi(rat.d) || [];
        z.forEach((r) => righe.push('Asintoto verticale: x = ' + M.fTesto(r).replace('-', '−')));
        const gn = M.polGrado(rat.n, 'x'), gd = M.polGrado(rat.d, 'x');
        const cn = M.polCoeffNum(rat.n, 'x'), cd = M.polCoeffNum(rat.d, 'x');
        if (gn < gd) righe.push('Asintoto orizzontale: y = 0');
        else if (gn === gd && cn && cd) righe.push('Asintoto orizzontale: y = ' + M.fTesto(M.fDiv(cn[gn], cd[gd])).replace('-', '−'));
        else if (gn === gd + 1) righe.push('C\'è un asintoto obliquo: si trova con la divisione fra i polinomi.');
      }
      if (!righe.length) righe.push('Non trovo asintoti (oppure questa funzione non è una frazione algebrica).');
      html = '<div class="mate mate-blocco">' + righe.map(esc).join('<br>') + '</div>';
      spiega = 'Gli asintoti sono le rette a cui il grafico si avvicina sempre di più senza toccarle.';
      break;
    }
    case 'derivata1': case 'derivata2': {
      const uno = Risolutore.derivataPassi(Studio.funzione);
      if (uno.stato !== 'verde') { html = '<div class="mate mate-blocco">Non so derivare questa funzione</div>'; stato = 'rosso'; break; }
      if (id === 'derivata1') {
        Studio.risultati.d1 = uno.risultato;
        html = '<div class="mate mate-blocco">f\'(x) = ' + M.html(uno.risultato) + '</div>';
        spiega = 'La derivata prima serve per capire dove la funzione sale e dove scende.';
      } else {
        const due = Risolutore.derivataPassi(M.testo(uno.risultato));
        Studio.risultati.d2 = due.risultato;
        html = '<div class="mate mate-blocco">f\'\'(x) = ' + (due.risultato ? M.html(due.risultato) : '?') + '</div>';
        spiega = 'La derivata seconda serve per la concavità.';
      }
      break;
    }
    case 'crescenza': case 'concavita': {
      const quale = id === 'crescenza' ? 'd1' : 'd2';
      if (!Studio.risultati[quale]) {
        const base = quale === 'd1' ? Studio.funzione : M.testo(Risolutore.derivataPassi(Studio.funzione).risultato || '0');
        const d = Risolutore.derivataPassi(base);
        Studio.risultati[quale] = d.risultato;
      }
      const d = Studio.risultati[quale];
      if (!d) { html = '<div class="mate mate-blocco">Non disponibile</div>'; stato = 'rosso'; break; }
      const P = M.polDaAst(d);
      const c = P ? M.polCoeffNum(P, 'x') : null;
      const z = c ? M.radiciRazionali(c) : [];
      if (!z.length) {
        html = '<div class="mate mate-blocco">La derivata non si annulla in punti "semplici"</div>';
        spiega = 'Prova a risolvere tu l\'equazione ' + (quale === 'd1' ? 'f\'(x) = 0' : 'f\'\'(x) = 0') + ' con "Risolvi con me".';
        stato = 'giallo';
        break;
      }
      const righe = z.map((r) => {
        const x = M.fNum(r);
        let prima, dopo;
        try { prima = M.valuta(d, { x: x - 0.5 }); dopo = M.valuta(d, { x: x + 0.5 }); } catch (e) { prima = dopo = 0; }
        if (id === 'crescenza') {
          const tipo = prima < 0 && dopo > 0 ? 'MINIMO' : prima > 0 && dopo < 0 ? 'MASSIMO' : 'punto stazionario';
          let y = '';
          try { y = ' → punto (' + Risolutore.numeroIt(x) + '; ' + Risolutore.numeroIt(M.valuta(ast, { x })) + ')'; } catch (e) { /* niente */ }
          return 'x = ' + Risolutore.numeroIt(x) + ' è un ' + tipo + y;
        }
        return 'x = ' + Risolutore.numeroIt(x) + ' è un possibile FLESSO (la concavità cambia)';
      });
      html = '<div class="mate mate-blocco">' + righe.map(esc).join('<br>') + '</div>';
      spiega = id === 'crescenza'
        ? 'Dove la derivata prima è positiva la funzione sale, dove è negativa scende. Nei punti in cui cambia segno ci sono massimi e minimi.'
        : 'Dove la derivata seconda è positiva la curva tiene l\'acqua (∪), dove è negativa la perde (∩).';
      break;
    }
    case 'grafico': {
      html = '<div class="tela-wrap"><canvas class="tela" id="telaPiano"></canvas></div>';
      spiega = 'Ecco come viene il grafico: confrontalo con quello che hai trovato tu.';
      break;
    }
  }

  const sem = SEMAFORO[stato];
  box.innerHTML = `<div class="card">
    <div class="barra-btn"><span class="semaforo ${sem.cl}">${sem.ic} ${esc(sem.txt)}</span></div>
    <div class="passo"><div class="numero">${esc(titolo)}</div>${html}<div class="spiega">${esc(spiega)}</div></div>
  </div>`;
  if (id === 'grafico') {
    Piano.funzioni = [{ testo: Studio.funzione, ast, colore: Piano.colori[0] }];
    Piano.punti = [];
    setTimeout(() => Piano.imposta('telaPiano'), 30);
  }
}

/* ------------------------------------------------------------------
   STATISTICA
   ------------------------------------------------------------------ */

STRUMENTI['mate-statistica'] = function (c) {
  c.innerHTML = testaStrumento('📊', 'Statistica',
    'Scrivi i numeri separati da uno spazio o da una virgola: penso io ai conti.') + `
    <div class="card">
      <label class="etichetta" for="stNumeri">I miei dati</label>
      <textarea class="area" id="stNumeri" rows="3" inputmode="decimal"
        placeholder="Per esempio: 4 7 7 9 12 15">6 7 7 8 10 12 12 12 15</textarea>
      <div class="barra-btn" style="margin-top:12px">
        ${bottone('sta-calcola', '▶', 'Calcola', 'btn-primario btn-grande')}
        ${bottone('sta-passaggi', '👣', 'Mostrami i passaggi')}
      </div>
    </div>
    <div id="staRis"></div>`;
  calcolaStatistica(false);
};

function calcolaStatistica(conPassaggi) {
  const grezzo = ($('#stNumeri') || {}).value || '';
  const dati = grezzo.split(/[\s,;]+/).map((s) => Number(String(s).replace(',', '.')))
    .filter((x) => isFinite(x));
  const box = $('#staRis');
  if (dati.length < 2) {
    box.innerHTML = `<div class="avviso avviso-att"><span class="ic">✏️</span>
      <p>Servono almeno due numeri. Scrivili separati da uno spazio.</p></div>`;
    return;
  }
  const ordinati = dati.slice().sort((a, b) => a - b);
  const somma = dati.reduce((s, x) => s + x, 0);
  const media = somma / dati.length;
  const meta = Math.floor(ordinati.length / 2);
  const mediana = ordinati.length % 2 ? ordinati[meta] : (ordinati[meta - 1] + ordinati[meta]) / 2;
  const freq = {};
  dati.forEach((x) => { freq[x] = (freq[x] || 0) + 1; });
  const maxFreq = Math.max.apply(null, Object.keys(freq).map((k) => freq[k]));
  const mode = Object.keys(freq).filter((k) => freq[k] === maxFreq).map(Number);
  const num = Risolutore.numeroIt;
  const arrotonda = (x) => num(Math.round(x * 10000) / 10000);

  box.innerHTML = `<div class="card">
      <div class="perc-blocchi">
        <div class="blocco"><div class="et">Quanti dati</div><div class="val">${dati.length}</div></div>
        <div class="blocco"><div class="et">Somma</div><div class="val">${arrotonda(somma)}</div></div>
        <div class="blocco" style="border-color:var(--ok);background:var(--ok-chiaro)">
          <div class="et">Media</div><div class="val">${arrotonda(media)}</div></div>
        <div class="blocco"><div class="et">Mediana</div><div class="val">${arrotonda(mediana)}</div></div>
        <div class="blocco"><div class="et">Moda</div><div class="val">${maxFreq === 1 ? 'nessuna' : mode.map(num).join(' e ')}</div></div>
        <div class="blocco"><div class="et">Minimo e massimo</div><div class="val">${num(ordinati[0])} … ${num(ordinati[ordinati.length - 1])}</div></div>
      </div>
    </div>
    <div class="card">
      <h2>Quante volte compare ogni numero</h2>
      <div style="display:grid;gap:8px">
        ${Object.keys(freq).sort((a, b) => Number(a) - Number(b)).map((k) => `
          <div style="display:grid;grid-template-columns:4em 1fr 3em;gap:8px;align-items:center">
            <b>${esc(num(Number(k)))}</b>
            <div class="perc-barra" style="height:26px"><div class="riempi" style="width:${freq[k] / maxFreq * 100}%"></div></div>
            <span>${freq[k]}×</span>
          </div>`).join('')}
      </div>
    </div>
    ${conPassaggi ? `<div class="card">
      <div class="passo"><div class="numero">Media</div>
        <div class="mate mate-blocco">${esc(dati.map(num).join(' + '))} = ${esc(arrotonda(somma))}</div>
        <div class="mate mate-blocco">${esc(arrotonda(somma))} ÷ ${dati.length} = ${esc(arrotonda(media))}</div>
        <div class="spiega">Sommo tutti i numeri e divido per quanti sono.</div></div>
      <div class="passo"><div class="numero">Mediana</div>
        <div class="mate mate-blocco">${esc(ordinati.map(num).join('  '))}</div>
        <div class="spiega">Prima metto i numeri in ordine, poi prendo quello in mezzo${ordinati.length % 2 ? '' : ' (qui sono due, quindi faccio la loro media)'}.</div></div>
      <div class="passo"><div class="numero">Moda</div>
        <div class="spiega">${maxFreq === 1 ? 'Nessun numero si ripete: la moda non c\'è.' : 'Il numero che compare più spesso (' + maxFreq + ' volte).'}</div></div>
    </div>` : ''}`;
}

/* ------------------------------------------------------------------
   FORMULARIO
   ------------------------------------------------------------------ */

const FORMULE_INIZIALI = [
  { cat: 'Aritmetica', nome: 'Percentuale', formula: 'parte = totale × p ÷ 100', quando: 'Quando devi calcolare il p% di un numero.', simboli: 'p = percentuale', esempio: '25% di 200 = 200 × 25 ÷ 100 = 50' },
  { cat: 'Aritmetica', nome: 'Proporzione', formula: 'a : b = c : d', quando: 'Quando due rapporti sono uguali.', simboli: 'Il prodotto dei medi è uguale al prodotto degli estremi: b·c = a·d', esempio: '3 : 6 = 5 : 10' },
  { cat: 'Aritmetica', nome: 'Potenze', formula: 'aⁿ · aᵐ = aⁿ⁺ᵐ ;  aⁿ ÷ aᵐ = aⁿ⁻ᵐ ;  (aⁿ)ᵐ = aⁿ·ᵐ', quando: 'Per moltiplicare o dividere potenze con la stessa base.', simboli: 'a = base, n e m = esponenti', esempio: '2³ · 2² = 2⁵ = 32' },
  { cat: 'Algebra', nome: 'Quadrato di binomio', formula: '(a + b)² = a² + 2ab + b²', quando: 'Per sviluppare il quadrato di una somma.', simboli: 'a e b sono i due termini', esempio: '(x + 3)² = x² + 6x + 9' },
  { cat: 'Algebra', nome: 'Somma per differenza', formula: '(a + b)(a − b) = a² − b²', quando: 'Quando i due binomi differiscono solo per un segno.', simboli: '', esempio: '(x + 3)(x − 3) = x² − 9' },
  { cat: 'Algebra', nome: 'Equazione di 2° grado', formula: 'x = (−b ± √(b² − 4ac)) ÷ (2a)', quando: 'Per risolvere ax² + bx + c = 0.', simboli: 'a, b, c sono i coefficienti; Δ = b² − 4ac', esempio: 'x² − 5x + 6 = 0 → x = 2 e x = 3' },
  { cat: 'Geometria', nome: 'Teorema di Pitagora', formula: 'i² = c₁² + c₂²', quando: 'Nei triangoli rettangoli.', simboli: 'i = ipotenusa, c = cateti', esempio: '3² + 4² = 5²' },
  { cat: 'Geometria', nome: 'Area del triangolo', formula: 'A = (b × h) ÷ 2', quando: 'Sempre, conoscendo base e altezza.', simboli: 'b = base, h = altezza', esempio: 'b = 6, h = 4 → A = 12' },
  { cat: 'Geometria', nome: 'Cerchio', formula: 'C = 2πr ;  A = πr²', quando: 'Circonferenza e area del cerchio.', simboli: 'r = raggio, π ≈ 3,14', esempio: 'r = 5 → A = 78,5' },
  { cat: 'Geometria', nome: 'Volume del prisma', formula: 'V = area di base × altezza', quando: 'Per prismi e cilindri.', simboli: '', esempio: 'base 12 cm², h 5 cm → V = 60 cm³' },
  { cat: 'Geometria analitica', nome: 'Distanza fra due punti', formula: 'd = √((x₂ − x₁)² + (y₂ − y₁)²)', quando: 'Per misurare quanto distano due punti.', simboli: '', esempio: 'A(1;2) B(4;6) → d = 5' },
  { cat: 'Geometria analitica', nome: 'Punto medio', formula: 'M = ((x₁ + x₂)/2 ; (y₁ + y₂)/2)', quando: 'Per trovare il punto a metà di un segmento.', simboli: '', esempio: 'A(0;0) B(4;6) → M(2;3)' },
  { cat: 'Geometria analitica', nome: 'Retta', formula: 'y = mx + q', quando: 'Equazione della retta.', simboli: 'm = coefficiente angolare (pendenza), q = intersezione con l\'asse y', esempio: 'y = 2x + 3' },
  { cat: 'Geometria analitica', nome: 'Rette parallele e perpendicolari', formula: 'parallele: m₁ = m₂ ;  perpendicolari: m₁ · m₂ = −1', quando: 'Per capire come stanno due rette.', simboli: '', esempio: 'y = 2x e y = −x/2 sono perpendicolari' },
  { cat: 'Geometria analitica', nome: 'Vertice della parabola', formula: 'V(−b/2a ; −Δ/4a)', quando: 'Per y = ax² + bx + c.', simboli: 'Δ = b² − 4ac', esempio: 'y = x² − 4x + 3 → V(2; −1)' },
  { cat: 'Trigonometria', nome: 'Identità fondamentale', formula: 'sen²α + cos²α = 1', quando: 'Sempre, per qualsiasi angolo.', simboli: 'α = angolo', esempio: 'sen30° = 0,5 e cos30° ≈ 0,866' },
  { cat: 'Trigonometria', nome: 'Tangente', formula: 'tanα = senα ÷ cosα', quando: 'Quando il coseno non è zero.', simboli: '', esempio: 'tan45° = 1' },
  { cat: 'Trigonometria', nome: 'Teorema dei seni', formula: 'a/senA = b/senB = c/senC', quando: 'In qualsiasi triangolo, con un lato e il suo angolo opposto.', simboli: '', esempio: '' },
  { cat: 'Trigonometria', nome: 'Teorema del coseno', formula: 'a² = b² + c² − 2bc·cosA', quando: 'Quando conosci due lati e l\'angolo fra loro.', simboli: '', esempio: '' },
  { cat: 'Logaritmi', nome: 'Definizione', formula: 'log_a x = y  ⇄  aʸ = x', quando: 'Per passare da logaritmo a potenza.', simboli: 'a = base > 0 e ≠ 1; x > 0', esempio: 'log₂8 = 3 perché 2³ = 8' },
  { cat: 'Logaritmi', nome: 'Proprietà', formula: 'log(xy) = log x + log y ;  log(x/y) = log x − log y ;  log(xⁿ) = n log x', quando: 'Per spezzare o unire logaritmi.', simboli: '', esempio: 'log(100) = log(10·10) = 1 + 1 = 2' },
  { cat: 'Derivate', nome: 'Regole principali', formula: '(xⁿ)\' = n·xⁿ⁻¹ ;  (f+g)\' = f\'+g\' ;  (f·g)\' = f\'g + fg\'', quando: 'Per derivare polinomi e prodotti.', simboli: '', esempio: '(3x²)\' = 6x' },
  { cat: 'Derivate', nome: 'Funzioni note', formula: '(senx)\' = cosx ;  (cosx)\' = −senx ;  (eˣ)\' = eˣ ;  (lnx)\' = 1/x', quando: 'Derivate da sapere a memoria.', simboli: '', esempio: '' },
  { cat: 'Integrali', nome: 'Integrale immediato', formula: '∫xⁿ dx = xⁿ⁺¹/(n+1) + c', quando: 'Per n diverso da −1.', simboli: 'c = costante di integrazione', esempio: '∫3x² dx = x³ + c' },
  { cat: 'Statistica', nome: 'Media aritmetica', formula: 'media = somma dei valori ÷ quanti sono', quando: 'Per trovare il valore "tipico".', simboli: '', esempio: '(4+6+8) ÷ 3 = 6' },
  { cat: 'Statistica', nome: 'Mediana e moda', formula: 'mediana = valore centrale ;  moda = valore più frequente', quando: 'Per descrivere un insieme di dati.', simboli: 'Per la mediana i dati vanno prima messi in ordine.', esempio: '2, 3, 3, 7, 9 → mediana 3, moda 3' },
  { cat: 'Probabilità', nome: 'Probabilità elementare', formula: 'P = casi favorevoli ÷ casi possibili', quando: 'Quando tutti i casi sono ugualmente probabili.', simboli: 'P è sempre fra 0 e 1', esempio: 'Dado: P(esce 6) = 1/6' },
  { cat: 'Fisica', nome: 'Velocità', formula: 'v = s ÷ t', quando: 'Moto rettilineo uniforme.', simboli: 's = spazio, t = tempo', esempio: '100 m in 10 s → 10 m/s' },
  { cat: 'Scienze', nome: 'Densità', formula: 'd = m ÷ V', quando: 'Per confrontare materiali.', simboli: 'm = massa, V = volume', esempio: '' },
  { cat: 'Grammatica', nome: 'Analisi logica: le domande', formula: 'soggetto = chi? ;  complemento oggetto = che cosa?', quando: 'Per riconoscere le parti della frase.', simboli: '', esempio: 'Marco (chi?) mangia la mela (che cosa?)' }
];

STRUMENTI['mate-formule'] = function (c) {
  if (!Stato.formule.length) {
    Stato.formule = FORMULE_INIZIALI.map((f) => Object.assign({ id: uid('f'), iniziale: true }, f));
    salva();
  }
  const categorie = Array.from(new Set(Stato.formule.map((f) => f.cat))).sort();
  c.innerHTML = testaStrumento('📐', 'Formulario', 'Le formule che ti servono, con un esempio.') + `
    <div class="card">
      <div class="barra-btn" style="margin-bottom:8px">
        ${bottone('fm-nuova', '➕', 'Aggiungi una formula', 'btn-primario')}
        ${bottone('fm-stampa', '🖨️', 'Stampa il formulario')}
      </div>
      <label class="etichetta" for="fmCerca">Cerca</label>
      <input class="campo" id="fmCerca" type="search" placeholder="Per esempio: pitagora" autocomplete="off">
    </div>
    <div id="fmLista"></div>`;
  $('#fmCerca', c).addEventListener('input', debounce(disegnaFormule, 180));
  disegnaFormule();
};

function disegnaFormule() {
  const box = $('#fmLista');
  if (!box) return;
  const q = (($('#fmCerca') || {}).value || '').trim().toLowerCase();
  const lista = Stato.formule.filter((f) => !q ||
    (f.nome + ' ' + f.formula + ' ' + f.cat + ' ' + (f.quando || '')).toLowerCase().indexOf(q) >= 0);
  const categorie = Array.from(new Set(lista.map((f) => f.cat)));
  box.innerHTML = categorie.length ? categorie.map((cat) => `
    <details class="pannello"${q ? ' open' : ''}>
      <summary>${esc(cat)} <span class="tag">${lista.filter((f) => f.cat === cat).length}</span></summary>
      <div class="contenuto">
        ${lista.filter((f) => f.cat === cat).map((f) => `
          <div class="card card-soft">
            <h3>${esc(f.nome)}</h3>
            <div class="mate mate-blocco">${esc(f.formula)}</div>
            ${f.quando ? `<p><b>Quando si usa:</b> ${esc(f.quando)}</p>` : ''}
            ${f.simboli ? `<p><b>Significato dei simboli:</b> ${esc(f.simboli)}</p>` : ''}
            ${f.esempio ? `<p><b>Esempio:</b> ${esc(f.esempio)}</p>` : ''}
            <div class="barra-btn" style="margin:0">
              ${bottone('fm-leggi:' + f.id, '🔊', 'Leggimela', 'btn-piccolo')}
              ${bottone('fm-modifica:' + f.id, '✏️', 'Modifica', 'btn-piccolo')}
              ${bottone('fm-canc:' + f.id, '🗑️', 'Elimina', 'btn-piccolo btn-errore')}
            </div>
          </div>`).join('')}
      </div>
    </details>`).join('')
    : `<div class="card">${schedaVuota('🔍', 'Nessuna formula trovata.')}</div>`;
}

async function modificaFormula(id) {
  const f = id ? Stato.formule.find((x) => x.id === id) : null;
  const categorie = Array.from(new Set(Stato.formule.map((x) => x.cat).concat(
    ['Aritmetica', 'Algebra', 'Geometria', 'Geometria analitica', 'Trigonometria', 'Logaritmi', 'Derivate', 'Integrali', 'Statistica', 'Probabilità', 'Scienze', 'Fisica', 'Grammatica'])));
  const r = await finestra({
    titolo: f ? 'Modifica la formula' : 'Nuova formula',
    campi: [
      { nome: 'cat', etichetta: 'Categoria', tipo: 'scelta', opzioni: categorie, valore: f ? f.cat : 'Algebra' },
      { nome: 'nome', etichetta: 'Nome', valore: f ? f.nome : '' },
      { nome: 'formula', etichetta: 'Formula', valore: f ? f.formula : '' },
      { nome: 'quando', etichetta: 'Quando si usa', tipo: 'area', righe: 2, valore: f ? f.quando : '' },
      { nome: 'simboli', etichetta: 'Significato dei simboli', tipo: 'area', righe: 2, valore: f ? f.simboli : '' },
      { nome: 'esempio', etichetta: 'Esempio', valore: f ? f.esempio : '' }
    ],
    testoOk: 'Salva'
  });
  if (!r) return;
  if (f) Object.assign(f, r);
  else Stato.formule.unshift(Object.assign({ id: uid('f') }, r));
  await salvaOra();
  disegnaFormule();
  avvisoOk('Formula salvata');
}


/* ============================================================
   11-studia.js
   ============================================================ */
/* ==================================================================
   11-studia.js — Studia, Flashcard, Mappe, Appunti, Compiti,
   Concentrazione, Le mie parole.
   ================================================================== */

/* ------------------------------------------------------------------
   STUDIA: trasformare gli appunti (tutto in locale, senza AI)
   ------------------------------------------------------------------ */

const Studia = { testo: '', frasi: [], etichette: {} };

const ETICHETTE = [
  { id: 'titolo', ic: '🏷️', tit: 'Titolo' },
  { id: 'chiave', ic: '🔑', tit: 'Parola chiave' },
  { id: 'domanda', ic: '❓', tit: 'Domanda' },
  { id: 'risposta', ic: '💬', tit: 'Risposta' },
  { id: 'definizione', ic: '📖', tit: 'Definizione' },
  { id: 'data', ic: '📅', tit: 'Data' },
  { id: 'nome', ic: '👤', tit: 'Nome' }
];

VISTE.studia = function (c) {
  c.innerHTML = testaSezione('🧠', 'Studia',
    'Incolla gli appunti: li spezzo in pezzi piccoli e tu decidi cosa è importante.',
    bottone('home', '🏠', 'Home', 'btn-piccolo')) + `
    <div class="card no-stampa">
      <label class="etichetta" for="stTesto">I miei appunti</label>
      <textarea class="area" id="stTesto" rows="6" spellcheck="true"
        placeholder="Incolla qui il testo da studiare.">${esc(Studia.testo)}</textarea>
      <div class="barra-btn" style="margin-top:12px">
        ${bottone('st-spezza', '✂️', 'Spezza in pezzi piccoli', 'btn-primario btn-grande')}
        ${bottone('st-elenco', '•', 'Trasforma in elenco puntato')}
        ${bottone('st-daPdf', '📄', 'Prendi da un PDF')}
      </div>
      <p class="aiutino">Attenzione: io non capisco il significato del testo. Faccio solo lavori meccanici
        (dividere, contare, riordinare). Le cose importanti le scegli tu.</p>
    </div>
    <div id="stRisultato"></div>`;
};

function spezzaAppunti() {
  Studia.testo = $('#stTesto').value;
  Studia.frasi = dividiInFrasi(Studia.testo);
  Studia.etichette = {};
  if (!Studia.frasi.length) { toast('Prima incolla un testo.'); return; }
  disegnaPezziStudio();
}

function disegnaPezziStudio() {
  const box = $('#stRisultato');
  box.innerHTML = `<div class="card">
      <h2>Un pezzo alla volta</h2>
      <p class="aiutino">Tocca un'etichetta per dire che tipo di informazione è. Poi puoi creare le flashcard.</p>
      ${Studia.frasi.map((f, i) => `
        <div class="card card-soft">
          <p style="font-size:1.05em">${esc(f)}</p>
          <div class="barra-btn" style="margin:0">
            ${ETICHETTE.map((e) => `<button type="button" class="btn btn-piccolo" data-az="st-eti:${i}:${e.id}"
              aria-pressed="${Studia.etichette[i] === e.id}"><span aria-hidden="true">${e.ic}</span><span>${esc(e.tit)}</span></button>`).join('')}
            <button type="button" class="btn btn-piccolo" data-az="st-leggi:${i}">🔊</button>
          </div>
        </div>`).join('')}
      <div class="barra-btn" style="margin-top:14px">
        ${bottone('st-flashcard', '🃏', 'Crea le flashcard dalle mie etichette', 'btn-primario btn-grande')}
        ${bottone('st-appunto', '📓', 'Salva negli appunti')}
        ${bottone('st-mappa', '🗺️', 'Manda le parole chiave alla mappa')}
      </div>
    </div>`;
}

function etichettaFrase(i, tipo) {
  Studia.etichette[i] = Studia.etichette[i] === tipo ? null : tipo;
  disegnaPezziStudio();
}

function trasformaInElenco() {
  Studia.testo = $('#stTesto').value;
  const frasi = dividiInFrasi(Studia.testo);
  if (!frasi.length) { toast('Prima incolla un testo.'); return; }
  $('#stRisultato').innerHTML = `<div class="card">
    <h2>• Elenco puntato</h2>
    <ul style="font-size:1.05em;line-height:1.9">${frasi.map((f) => '<li>' + esc(f) + '</li>').join('')}</ul>
    <div class="barra-btn">
      ${bottone('st-copiaElenco', '📋', 'Copia negli appunti', 'btn-primario')}
      ${bottone('st-stampa', '🖨️', 'Stampa')}
    </div>
  </div>`;
}

async function flashcardDaStudio() {
  const domande = [], risposte = [], definizioni = [];
  Object.keys(Studia.etichette).forEach((k) => {
    const t = Studia.etichette[k];
    if (t === 'domanda') domande.push({ i: Number(k), f: Studia.frasi[k] });
    if (t === 'risposta') risposte.push({ i: Number(k), f: Studia.frasi[k] });
    if (t === 'definizione') definizioni.push({ i: Number(k), f: Studia.frasi[k] });
  });
  let create = 0;
  domande.forEach((d) => {
    const r = risposte.filter((x) => x.i > d.i).sort((a, b) => a.i - b.i)[0];
    if (r) { Stato.flashcard.unshift(nuovaCard(d.f, r.f, 'Studio')); create++; }
  });
  definizioni.forEach((d) => {
    const pezzi = d.f.split(/[:–—]/);
    if (pezzi.length >= 2) {
      Stato.flashcard.unshift(nuovaCard('Che cos\'è ' + pezzi[0].trim() + '?', pezzi.slice(1).join(':').trim(), 'Studio'));
      create++;
    }
  });
  if (!create) {
    await finestra({ titolo: 'Non ho abbastanza etichette',
      testo: 'Per creare le flashcard segna almeno una frase come "Domanda" e quella dopo come "Risposta", oppure segna una "Definizione" scritta come "parola: significato".',
      testoOk: 'Ho capito', testoAnnulla: 'Ho capito' });
    return;
  }
  await salvaOra();
  avvisoOk(create === 1 ? 'Ho creato 1 flashcard' : 'Ho creato ' + create + ' flashcard');
}

function nuovaCard(fronte, retro, materia) {
  return { id: uid('fc'), fronte, retro, materia: materia || 'Altra', stato: 'nuova', creata: oraISO(), viste: 0 };
}

/* ------------------------------------------------------------------
   FLASHCARD
   ------------------------------------------------------------------ */

const Flash = { indice: 0, giro: false, mazzo: 'tutte', ordine: [] };
const ORDINE_STATI = { ripassa: 0, nuova: 1, quasi: 2, so: 3 };

VISTE.flash = function (c) {
  const materie2 = Array.from(new Set(Stato.flashcard.map((f) => f.materia || 'Altra')));
  c.innerHTML = testaSezione('🃏', 'Flashcard', null,
    bottone('home', '🏠', 'Home', 'btn-piccolo')) + `
    <div class="card no-stampa">
      <div class="barra-btn" style="margin-bottom:8px">
        ${bottone('fc-nuova', '➕', 'Nuova scheda', 'btn-primario')}
        ${bottone('fc-elenco', '📋', 'Vedi tutte')}
        ${bottone('fc-mischia', '🔀', 'Mischia')}
      </div>
      ${materie2.length > 1 ? `<label class="etichetta" for="fcMazzo">Quale mazzo?</label>
        <select class="campo" id="fcMazzo">
          <option value="tutte">Tutte le materie</option>
          ${materie2.map((m) => `<option value="${esc(m)}"${Flash.mazzo === m ? ' selected' : ''}>${esc(m)}</option>`).join('')}
        </select>` : ''}
    </div>
    <div id="fcBox"></div>`;
  const sel = $('#fcMazzo', c);
  if (sel) sel.addEventListener('change', () => { Flash.mazzo = sel.value; preparaMazzo(); disegnaFlashcard(); });
  preparaMazzo();
  disegnaFlashcard();
};

function preparaMazzo() {
  const tutte = Stato.flashcard.filter((f) => Flash.mazzo === 'tutte' || f.materia === Flash.mazzo);
  // prima quelle da ripassare, poi le nuove, poi le "quasi", infine quelle che so
  Flash.ordine = tutte.slice().sort((a, b) =>
    (ORDINE_STATI[a.stato || 'nuova'] - ORDINE_STATI[b.stato || 'nuova']) || ((a.viste || 0) - (b.viste || 0)));
  Flash.indice = 0;
  Flash.giro = false;
}

function disegnaFlashcard() {
  const box = $('#fcBox');
  if (!box) return;
  if (!Flash.ordine.length) {
    box.innerHTML = `<div class="card">${schedaVuota('🃏', 'Non hai ancora nessuna scheda.',
      'Creane una tu, oppure creale dalla sezione Studia o da un PDF.')}</div>`;
    return;
  }
  const i = limita(Flash.indice, 0, Flash.ordine.length - 1);
  const card = Flash.ordine[i];
  const daRipassare = Flash.ordine.filter((f) => (f.stato || 'nuova') !== 'so').length;

  box.innerHTML = `
    <p class="frase-contatore">Scheda ${i + 1} di ${Flash.ordine.length} · da ripassare: ${daRipassare}</p>
    <div class="flash-wrap">
      <div class="flash${Flash.giro ? ' retro' : ''}" id="fcCard" tabindex="0" role="button"
        aria-label="Tocca per girare la scheda">
        <span class="flash-lato">${Flash.giro ? 'RISPOSTA' : 'DOMANDA'}</span>
        <div>${esc(Flash.giro ? card.retro : card.fronte)}</div>
      </div>
    </div>
    <div class="barra-btn centro" style="margin-top:14px">
      ${bottone('fc-gira', '🔄', Flash.giro ? 'Rivedi la domanda' : 'Gira la scheda', 'btn-primario btn-grande')}
      ${bottone('fc-leggi', '🔊', 'Ascolta')}
    </div>
    ${Flash.giro ? `<div class="barra-btn centro">
      ${bottone('fc-so', '✅', 'Lo so', 'btn-ok btn-grande')}
      ${bottone('fc-quasi', '🤔', 'Quasi', 'btn-attenzione btn-grande')}
      ${bottone('fc-ripassa', '❌', 'Da ripassare', 'btn-errore btn-grande')}
    </div>` : ''}
    <div class="barra-btn centro">
      ${bottone('fc-prec', '⬅', 'Precedente', 'btn-piccolo')}
      ${bottone('fc-succ', '➡', 'Successiva', 'btn-piccolo')}
      ${bottone('fc-modifica:' + card.id, '✏️', 'Modifica', 'btn-piccolo')}
      ${bottone('fc-canc:' + card.id, '🗑️', 'Elimina', 'btn-piccolo btn-errore')}
    </div>`;

  const cn = $('#fcCard');
  cn.addEventListener('click', () => { Flash.giro = !Flash.giro; disegnaFlashcard(); });
  cn.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); cn.click(); } });
}

function rispondiFlashcard(stato) {
  const card = Flash.ordine[Flash.indice];
  if (!card) return;
  const vera = Stato.flashcard.find((f) => f.id === card.id);
  if (vera) { vera.stato = stato; vera.viste = (vera.viste || 0) + 1; vera.ultima = oraISO(); }
  salva();
  Flash.giro = false;
  if (Flash.indice < Flash.ordine.length - 1) Flash.indice++;
  else { preparaMazzo(); toast('Hai finito il giro! Ricominciamo dalle schede da ripassare.'); }
  disegnaFlashcard();
}

async function modificaFlashcard(id) {
  const f = id ? Stato.flashcard.find((x) => x.id === id) : null;
  const r = await finestra({
    titolo: f ? 'Modifica la scheda' : 'Nuova flashcard',
    campi: [
      { nome: 'fronte', etichetta: 'FRONTE — la domanda', tipo: 'area', righe: 3, valore: f ? f.fronte : '' },
      { nome: 'retro', etichetta: 'RETRO — la risposta', tipo: 'area', righe: 3, valore: f ? f.retro : '' },
      { nome: 'materia', etichetta: 'Materia', tipo: 'scelta', opzioni: materie(), valore: f ? f.materia : materie()[0] }
    ],
    testoOk: 'Salva'
  });
  if (!r) return;
  if (!r.fronte.trim()) { toast('La domanda non può essere vuota.'); return; }
  if (f) Object.assign(f, r);
  else Stato.flashcard.unshift(Object.assign(nuovaCard(r.fronte, r.retro, r.materia)));
  await salvaOra();
  preparaMazzo();
  disegnaFlashcard();
  avvisoOk('Scheda salvata');
}

function elencoFlashcard() {
  $('#fcBox').innerHTML = `<div class="card">
    <h2>📋 Tutte le schede</h2>
    <ul class="lista">${Stato.flashcard.map((f) => `
      <li class="voce">
        <div class="corpo"><b>${esc(accorcia(f.fronte, 60))}</b>
          <span class="meta">${esc(f.materia || '')} · ${esc({ so: 'la so', quasi: 'quasi', ripassa: 'da ripassare', nuova: 'nuova' }[f.stato || 'nuova'])}</span></div>
        <div class="azioni">
          ${bottone('fc-modifica:' + f.id, '✏️', 'Modifica', 'btn-piccolo')}
          ${bottone('fc-canc:' + f.id, '🗑️', 'Elimina', 'btn-piccolo btn-errore')}
        </div>
      </li>`).join('')}</ul>
    <div class="barra-btn" style="margin-top:12px">${bottone('fc-torna', '⬅', 'Torna al ripasso')}</div>
  </div>`;
}

/* ------------------------------------------------------------------
   MAPPE CONCETTUALI
   ------------------------------------------------------------------ */

const Mappa = { corrente: null, scelto: null, collegando: null, zoom: 1 };

VISTE.mappe = function (c, par) {
  if (par) Mappa.corrente = par;
  if (!Mappa.corrente && Stato.mappe.length) Mappa.corrente = Stato.mappe[0].id;
  const m = Stato.mappe.find((x) => x.id === Mappa.corrente);

  c.innerHTML = testaSezione('🗺️', 'Mappe concettuali', null,
    bottone('home', '🏠', 'Home', 'btn-piccolo')) + `
    <div class="card no-stampa">
      <div class="barra-btn" style="margin-bottom:${Stato.mappe.length > 1 ? '8px' : '0'}">
        ${bottone('mp-nuova', '➕', 'Nuova mappa', 'btn-primario')}
        ${m ? bottone('mp-nodo', '⬤', 'Aggiungi nodo') : ''}
        ${m ? bottone('mp-collega', '🔗', 'Collega due nodi') : ''}
        ${m ? bottone('mp-zoom+', '🔍', 'Più grande') : ''}
        ${m ? bottone('mp-zoom-', '🔎', 'Più piccolo') : ''}
        ${m ? bottone('mp-esporta', '📤', 'Esporta') : ''}
        ${m ? bottone('mp-canc', '🗑️', 'Elimina mappa', 'btn-errore') : ''}
      </div>
      ${Stato.mappe.length > 1 ? `<label class="etichetta" for="mpSel">Quale mappa?</label>
        <select class="campo" id="mpSel">${Stato.mappe.map((x) => `<option value="${esc(x.id)}"${x.id === Mappa.corrente ? ' selected' : ''}>${esc(x.titolo)}</option>`).join('')}</select>` : ''}
    </div>
    ${m ? `<div class="mappa-tela" id="mpTela"></div>
      <p class="mappa-aiuto">Trascina i nodi per spostarli. Tocca un nodo per sceglierlo, poi usa i pulsanti.
        Con la tastiera: Tab per spostarti fra i nodi, Invio per modificarli.</p>
      <div id="mpAzioniNodo"></div>`
    : `<div class="card">${schedaVuota('🗺️', 'Non hai ancora nessuna mappa.', 'Crea la prima: si parte sempre dall\'argomento centrale.')}</div>`}`;

  const sel = $('#mpSel', c);
  if (sel) sel.addEventListener('change', () => vaiA('mappe', sel.value));
  if (m) disegnaMappa();
};

async function nuovaMappa() {
  const titolo = await chiediTesto('Nuova mappa', 'Di che argomento parla?', '');
  if (titolo === null) return;
  const m = {
    id: uid('mp'), titolo: titolo || 'Mappa senza titolo', creata: oraISO(),
    nodi: [{ id: uid('nd'), testo: titolo || 'Argomento', x: 50, y: 30, centrale: true }],
    archi: []
  };
  Stato.mappe.unshift(m);
  Mappa.corrente = m.id;
  await salvaOra();
  vaiA('mappe', m.id);
}

function mappaCorrente() { return Stato.mappe.find((x) => x.id === Mappa.corrente); }

function disegnaMappa() {
  const m = mappaCorrente();
  const tela = $('#mpTela');
  if (!m || !tela) return;
  const archi = m.archi.map((a) => {
    const n1 = m.nodi.find((n) => n.id === a.da), n2 = m.nodi.find((n) => n.id === a.a);
    if (!n1 || !n2) return '';
    return `<line x1="${n1.x}%" y1="${n1.y}%" x2="${n2.x}%" y2="${n2.y}%"
      stroke="var(--primario)" stroke-width="3" opacity=".65"/>`;
  }).join('');
  tela.innerHTML = `<svg aria-hidden="true">${archi}</svg>` +
    m.nodi.map((n) => {
      const img = immagineSicura(n.img);
      return `<div class="nodo${n.centrale ? ' centrale' : ''}${Mappa.scelto === n.id ? ' scelto' : ''}"
        data-nodo="${esc(n.id)}" tabindex="0" role="button"
        style="left:${n.x}%;top:${n.y}%;font-size:${(n.dim || 1) * Mappa.zoom}em">${
        img ? `<img src="${esc(img)}" alt="${esc(n.testo)}" draggable="false">` : ''}<span>${esc(n.testo)}</span></div>`;
    }).join('');

  collegaTrascinamento(tela);
}

/** Ridisegna solo le linee fra i nodi: durante il trascinamento non
    serve rifare tutta la mappa (era lento e accumulava ascoltatori). */
function aggiornaArchi() {
  const m = mappaCorrente();
  const svg = $('#mpTela svg');
  if (!m || !svg) return;
  svg.innerHTML = m.archi.map((a) => {
    const n1 = m.nodi.find((n) => n.id === a.da), n2 = m.nodi.find((n) => n.id === a.a);
    if (!n1 || !n2) return '';
    return `<line x1="${n1.x}%" y1="${n1.y}%" x2="${n2.x}%" y2="${n2.y}%"
      stroke="var(--primario)" stroke-width="3" opacity=".65"/>`;
  }).join('');
}

let _trascinaNodo = null;

/**
 * Trascinamento dei nodi: si collega UNA VOLTA SOLA alla tela.
 * Usa i "pointer event", che valgono insieme per dito, mouse e penna, e
 * la cattura del puntatore: così il rilascio arriva sempre, anche se il
 * dito esce dalla tela.
 * (Prima il rilascio era registrato con { once: true }: dopo il primo
 * rilascio non esisteva più e il nodo restava appiccicato al cursore.)
 */
function collegaTrascinamento(tela) {
  if (tela._trascinamentoCollegato) return;
  tela._trascinamentoCollegato = true;

  const SOGLIA = 6;   // sotto questi pixel è un clic, non un trascinamento

  tela.addEventListener('pointerdown', (e) => {
    const nd = e.target.closest('.nodo');
    if (!nd) return;
    const stavaCollegando = !!Mappa.collegando;
    scegliNodo(nd.dataset.nodo);
    // se stavo collegando due nodi la mappa è stata ridisegnata: niente trascinamento
    if (stavaCollegando || !nd.isConnected) return;
    _trascinaNodo = { id: nd.dataset.nodo, elemento: nd, x0: e.clientX, y0: e.clientY, mosso: false };
    try { nd.setPointerCapture(e.pointerId); } catch (err) { /* non indispensabile */ }
  });

  tela.addEventListener('pointermove', (e) => {
    if (!_trascinaNodo) return;
    if (!_trascinaNodo.mosso &&
        Math.abs(e.clientX - _trascinaNodo.x0) < SOGLIA &&
        Math.abs(e.clientY - _trascinaNodo.y0) < SOGLIA) return;
    const m = mappaCorrente();
    const n = m && m.nodi.find((x) => x.id === _trascinaNodo.id);
    if (!n) return;
    _trascinaNodo.mosso = true;
    const r = tela.getBoundingClientRect();
    n.x = limita((e.clientX - r.left) / r.width * 100, 3, 97);
    n.y = limita((e.clientY - r.top) / r.height * 100, 5, 95);
    _trascinaNodo.elemento.style.left = n.x + '%';
    _trascinaNodo.elemento.style.top = n.y + '%';
    aggiornaArchi();
    if (e.cancelable) e.preventDefault();
  });

  const rilascia = (e) => {
    if (!_trascinaNodo) return;
    const mosso = _trascinaNodo.mosso;
    try { _trascinaNodo.elemento.releasePointerCapture(e.pointerId); } catch (err) { /* già rilasciato */ }
    _trascinaNodo = null;
    if (mosso) salva();
  };
  tela.addEventListener('pointerup', rilascia);
  tela.addEventListener('pointercancel', rilascia);
  // rete di sicurezza: una volta sola per tutta l'applicazione, altrimenti
  // si accumulerebbe un ascoltatore a ogni ingresso nella sezione Mappe
  if (!collegaTrascinamento.reteMessa) {
    collegaTrascinamento.reteMessa = true;
    window.addEventListener('pointerup', (e) => {
      if (!_trascinaNodo) return;
      const mosso = _trascinaNodo.mosso;
      _trascinaNodo = null;
      if (mosso) salva();
    });
    window.addEventListener('blur', () => { _trascinaNodo = null; });
  }

  // tastiera: frecce per spostare, Invio per modificare
  tela.addEventListener('keydown', (e) => {
    const nd = e.target.closest('.nodo');
    if (!nd) return;
    const m = mappaCorrente();
    const n = m && m.nodi.find((x) => x.id === nd.dataset.nodo);
    if (!n) return;
    if (e.key === 'Enter') { e.preventDefault(); modificaNodo(nd.dataset.nodo); return; }
    const passo = 3;
    if (e.key === 'ArrowUp') n.y = limita(n.y - passo, 5, 95);
    else if (e.key === 'ArrowDown') n.y = limita(n.y + passo, 5, 95);
    else if (e.key === 'ArrowLeft') n.x = limita(n.x - passo, 3, 97);
    else if (e.key === 'ArrowRight') n.x = limita(n.x + passo, 3, 97);
    else return;
    e.preventDefault();
    nd.style.left = n.x + '%';
    nd.style.top = n.y + '%';
    aggiornaArchi();
    salva();
  });
}

function scegliNodo(id) {
  Mappa.scelto = id;
  const m = mappaCorrente();
  const n = m && m.nodi.find((x) => x.id === id);
  $$('.nodo').forEach((x) => x.classList.toggle('scelto', x.dataset.nodo === id));
  const box = $('#mpAzioniNodo');
  if (!box || !n) return;
  if (Mappa.collegando && Mappa.collegando !== id) {
    m.archi.push({ da: Mappa.collegando, a: id });
    Mappa.collegando = null;
    salva();
    disegnaMappa();
    avvisoOk('Nodi collegati');
    return;
  }
  box.innerHTML = `<div class="card">
    <h3>Nodo scelto: ${esc(accorcia(n.testo, 40))}</h3>
    <div class="barra-btn" style="margin:0">
      ${bottone('mp-modnodo:' + id, '✏️', 'Cambia il testo')}
      ${bottone('mp-figlio:' + id, '➕', 'Aggiungi un sotto-argomento', 'btn-primario')}
      ${bottone('mp-immagine:' + id, '🖼️', n.img ? 'Cambia immagine' : 'Metti un\'immagine')}
      ${n.img ? bottone('mp-toglimg:' + id, '🚫', 'Togli l\'immagine') : ''}
      ${bottone('mp-daqui:' + id, '🔗', 'Collega a un altro nodo')}
      ${bottone('mp-grande:' + id, '🔠', 'Più grande')}
      ${bottone('mp-piccolo:' + id, '🔡', 'Più piccolo')}
      ${n.centrale ? '' : bottone('mp-elimina:' + id, '🗑️', 'Elimina nodo', 'btn-errore')}
    </div>
  </div>`;
}

async function aggiungiNodo(padre) {
  const m = mappaCorrente();
  if (!m) return;
  const t = await chiediTesto('Nuovo nodo', 'Che cosa ci scrivo?', '');
  if (!t) return;
  const p = padre && m.nodi.find((x) => x.id === padre);
  const n = {
    id: uid('nd'), testo: t,
    x: p ? limita(p.x + (Math.random() * 40 - 20), 8, 92) : 50,
    y: p ? limita(p.y + 22, 8, 92) : 60
  };
  m.nodi.push(n);
  if (p) m.archi.push({ da: p.id, a: n.id });
  await salvaOra();
  disegnaMappa();
}

async function modificaNodo(id) {
  const m = mappaCorrente();
  const n = m && m.nodi.find((x) => x.id === id);
  if (!n) return;
  const t = await chiediTesto('Modifica il nodo', 'Testo', n.testo);
  if (t === null) return;
  n.testo = t;
  await salvaOra();
  disegnaMappa();
}

/** Mette (o cambia) l'immagine di un nodo. Sul telefono il selettore
    propone anche la fotocamera, così si può fotografare il libro. */
async function immagineNodo(id) {
  const m = mappaCorrente();
  const n = m && m.nodi.find((x) => x.id === id);
  if (!n) return;
  const file = await scegliFile('image/*');
  if (!file) return;
  toast('Sto preparando l\'immagine…');
  try {
    n.img = await immagineRidotta(file, 420);
    await salvaOra();
    disegnaMappa();
    scegliNodo(id);
    avvisoOk('Immagine aggiunta');
  } catch (e) {
    avvisoErrore('Non sono riuscito a usare questa immagine. Prova con una foto o un file .jpg o .png.');
  }
}

async function togliImmagineNodo(id) {
  const m = mappaCorrente();
  const n = m && m.nodi.find((x) => x.id === id);
  if (!n) return;
  delete n.img;
  await salvaOra();
  disegnaMappa();
  scegliNodo(id);
}

async function esportaMappa() {
  const m = mappaCorrente();
  if (!m) return;
  const r = await finestra({
    titolo: 'Esporta la mappa',
    campi: [{ nome: 'f', etichetta: 'Formato', tipo: 'scelta', valore: 'png',
      opzioni: [{ v: 'png', t: 'Immagine PNG' }, { v: 'svg', t: 'Disegno SVG' }, { v: 'json', t: 'Dati JSON' }] }],
    testoOk: 'Esporta'
  });
  if (!r) return;
  const nome = (m.titolo || 'mappa').replace(/[^\wÀ-ÿ -]/g, '').trim().replace(/\s+/g, '-').toLowerCase();
  if (r.f === 'json') { scaricaFile(nome + '.json', JSON.stringify(m, null, 2), 'application/json'); return; }
  if (r.f === 'svg') { scaricaFile(nome + '.svg', mappaSvg(m), 'image/svg+xml'); return; }
  // PNG disegnato direttamente sul canvas: così le immagini dei nodi
  // finiscono davvero dentro il file (passando dall'SVG i browser le bloccano).
  try {
    const blob = await mappaPng(m);
    scaricaFile(nome + '.png', blob, 'image/png');
  } catch (e) {
    avvisoErrore('Non sono riuscito a creare l\'immagine. Prova con il formato SVG.');
  }
}

/** Disegna la mappa su un canvas e restituisce il PNG. */
function mappaPng(m) {
  const W = 1400, H = 1000;
  const cv = document.createElement('canvas');
  cv.width = W; cv.height = H;
  const g = cv.getContext('2d');
  g.fillStyle = '#ffffff'; g.fillRect(0, 0, W, H);
  const px = (n) => n.x / 100 * W, py = (n) => n.y / 100 * H;

  // prima carico tutte le immagini dei nodi
  const caricamenti = m.nodi.map((n) => new Promise((risolvi) => {
    const dato = immagineSicura(n.img);
    if (!dato) { risolvi(null); return; }
    const im = new Image();
    im.onload = () => risolvi(im);
    im.onerror = () => risolvi(null);
    im.src = dato;
  }));

  return Promise.all(caricamenti).then((immagini) => {
    g.strokeStyle = '#1d4e6f'; g.lineWidth = 3;
    m.archi.forEach((a) => {
      const n1 = m.nodi.find((n) => n.id === a.da), n2 = m.nodi.find((n) => n.id === a.a);
      if (!n1 || !n2) return;
      g.beginPath(); g.moveTo(px(n1), py(n1)); g.lineTo(px(n2), py(n2)); g.stroke();
    });

    g.textAlign = 'center'; g.textBaseline = 'middle';
    m.nodi.forEach((n, i) => {
      const im = immagini[i];
      const dim = 20 * (n.dim || 1);
      g.font = 'bold ' + dim + 'px system-ui, sans-serif';
      const righe = spezzaTesto(g, n.testo, 260);
      const largImg = im ? 150 : 0;
      const altImg = im ? Math.round(150 * (im.height / im.width)) : 0;
      const larg = Math.max(110, largImg + 20, Math.max.apply(null, righe.map((t) => g.measureText(t).width)) + 34);
      const alt = altImg + righe.length * (dim + 6) + 24;
      const x = px(n) - larg / 2, y = py(n) - alt / 2;

      g.fillStyle = n.centrale ? '#1d4e6f' : '#e3eef6';
      g.strokeStyle = '#1d4e6f'; g.lineWidth = 3;
      rettangoloTondo(g, x, y, larg, alt, 14);
      g.fill(); g.stroke();

      if (im) {
        try { g.drawImage(im, px(n) - largImg / 2, y + 10, largImg, altImg); } catch (e) { /* la salto */ }
      }
      g.fillStyle = n.centrale ? '#ffffff' : '#16242e';
      righe.forEach((t, k) => {
        g.fillText(t, px(n), y + altImg + 16 + k * (dim + 6) + dim / 2);
      });
    });

    g.fillStyle = '#16242e'; g.textAlign = 'left'; g.textBaseline = 'alphabetic';
    g.font = 'bold 26px system-ui, sans-serif';
    g.fillText(m.titolo || 'Mappa', 26, 44);

    return new Promise((risolvi, rifiuta) => {
      cv.toBlob((b) => (b ? risolvi(b) : rifiuta(new Error('png'))), 'image/png');
    });
  });
}

function rettangoloTondo(g, x, y, l, a, r) {
  g.beginPath();
  g.moveTo(x + r, y);
  g.arcTo(x + l, y, x + l, y + a, r);
  g.arcTo(x + l, y + a, x, y + a, r);
  g.arcTo(x, y + a, x, y, r);
  g.arcTo(x, y, x + l, y, r);
  g.closePath();
}

function spezzaTesto(g, testo, largMax) {
  const parole = String(testo || '').split(/\s+/).filter(Boolean);
  const righe = [];
  let riga = '';
  parole.forEach((p) => {
    const prova = riga ? riga + ' ' + p : p;
    if (g.measureText(prova).width > largMax && riga) { righe.push(riga); riga = p; }
    else riga = prova;
  });
  if (riga) righe.push(riga);
  return righe.length ? righe.slice(0, 4) : [''];
}

function mappaSvg(m) {
  const W = 1200, H = 800;
  const px = (n) => (n.x / 100 * W).toFixed(0), py = (n) => (n.y / 100 * H).toFixed(0);
  const archi = m.archi.map((a) => {
    const n1 = m.nodi.find((n) => n.id === a.da), n2 = m.nodi.find((n) => n.id === a.a);
    return (n1 && n2) ? `<line x1="${px(n1)}" y1="${py(n1)}" x2="${px(n2)}" y2="${py(n2)}" stroke="#1d4e6f" stroke-width="3"/>` : '';
  }).join('');
  const nodi = m.nodi.map((n) => {
    const img = immagineSicura(n.img);
    const larg = Math.max(img ? 160 : 90, Math.min(300, n.testo.length * 11));
    const altImg = img ? 110 : 0;
    const alt = altImg + 48;
    const y0 = py(n) - alt / 2;
    return `<g><rect x="${px(n) - larg / 2}" y="${y0}" width="${larg}" height="${alt}" rx="12"
      fill="${n.centrale ? '#1d4e6f' : '#e3eef6'}" stroke="#1d4e6f" stroke-width="3"/>
      ${img ? `<image href="${esc(img)}" x="${px(n) - 70}" y="${y0 + 8}" width="140" height="${altImg - 16}" preserveAspectRatio="xMidYMid meet"/>` : ''}
      <text x="${px(n)}" y="${y0 + altImg + 30}" text-anchor="middle" font-family="sans-serif" font-size="18"
      font-weight="bold" fill="${n.centrale ? '#ffffff' : '#16242e'}">${esc(accorcia(n.testo, 26))}</text></g>`;
  }).join('');
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
    <rect width="${W}" height="${H}" fill="#ffffff"/>
    <text x="24" y="40" font-family="sans-serif" font-size="24" font-weight="bold" fill="#16242e">${esc(m.titolo)}</text>
    ${archi}${nodi}</svg>`;
}

/* ------------------------------------------------------------------
   APPUNTI
   ------------------------------------------------------------------ */

const COLORI_APPUNTO = ['#e3eef6', '#fde9d0', '#e2f3e9', '#fbe6e2', '#efe2f6', '#f6f2d0'];

VISTE.appunti = function (c) {
  c.innerHTML = testaSezione('📓', 'I miei appunti', null,
    bottone('home', '🏠', 'Home', 'btn-piccolo')) + `
    <div class="card no-stampa">
      <div class="barra-btn" style="margin-bottom:8px">
        ${bottone('ap-nuovo', '➕', 'Nuovo appunto', 'btn-primario btn-grande')}
      </div>
      <label class="etichetta" for="apCerca">🔍 Cerca nei miei appunti</label>
      <input class="campo" id="apCerca" type="search" placeholder="Scrivi una parola" autocomplete="off">
    </div>
    <div id="apLista"></div>`;
  $('#apCerca', c).addEventListener('input', debounce(disegnaAppunti, 180));
  disegnaAppunti();
};

function disegnaAppunti() {
  const box = $('#apLista');
  if (!box) return;
  const q = (($('#apCerca') || {}).value || '').trim().toLowerCase();
  const lista = Stato.appunti.filter((a) => !q ||
    ((a.titolo || '') + ' ' + (a.testo || '') + ' ' + (a.chiavi || '') + ' ' + (a.materia || '')).toLowerCase().indexOf(q) >= 0);
  box.innerHTML = lista.length ? lista.map((a) => `
    <div class="card" style="background:${esc(a.colore || 'var(--superficie)')}">
      <div class="card-titolo">
        <h3 style="margin:0">${esc(a.titolo || 'Senza titolo')}</h3>
        <span class="tag">${esc(a.materia || 'Altra')}</span>
      </div>
      <p class="aiutino" style="margin-top:0">${esc(dataInParole(a.data))}${a.fonte ? ' · ' + esc(a.fonte) : ''}</p>
      <p style="white-space:pre-wrap">${esc(accorcia(a.testo, 400))}</p>
      ${a.chiavi ? `<p><b>Parole chiave:</b> ${esc(a.chiavi)}</p>` : ''}
      <div class="barra-btn" style="margin:0">
        ${bottone('ap-apri:' + a.id, '👁️', 'Apri tutto', 'btn-piccolo')}
        ${bottone('ap-leggi:' + a.id, '🔊', 'Ascolta', 'btn-piccolo')}
        ${bottone('ap-mod:' + a.id, '✏️', 'Modifica', 'btn-piccolo')}
        ${bottone('ap-flash:' + a.id, '🃏', 'Fanne una flashcard', 'btn-piccolo')}
        ${bottone('ap-canc:' + a.id, '🗑️', 'Elimina', 'btn-piccolo btn-errore')}
      </div>
    </div>`).join('')
    : `<div class="card">${schedaVuota('📓', q ? 'Nessun appunto con questa parola.' : 'Non hai ancora appunti.',
        q ? 'Prova con una parola diversa.' : 'Tocca "Nuovo appunto" per cominciare.')}</div>`;
}

async function modificaAppunto(id, precompilato) {
  const a = id ? Stato.appunti.find((x) => x.id === id) : null;
  const r = await finestra({
    titolo: a ? 'Modifica l\'appunto' : 'Nuovo appunto',
    campi: [
      { nome: 'titolo', etichetta: 'Titolo', valore: a ? a.titolo : (precompilato && precompilato.titolo) || '' },
      { nome: 'materia', etichetta: 'Materia', tipo: 'scelta', opzioni: materie(), valore: a ? a.materia : (precompilato && precompilato.materia) || materie()[0] },
      { nome: 'data', etichetta: 'Data', tipo: 'data', valore: a ? a.data : oggiISO() },
      { nome: 'testo', etichetta: 'Testo', tipo: 'area', righe: 8, valore: a ? a.testo : (precompilato && precompilato.testo) || '' },
      { nome: 'chiavi', etichetta: 'Parole chiave (facoltative)', valore: a ? a.chiavi : '' },
      { nome: 'colore', etichetta: 'Colore', tipo: 'scelta', valore: a ? a.colore : COLORI_APPUNTO[0],
        opzioni: COLORI_APPUNTO.map((c, i) => ({ v: c, t: ['Azzurro', 'Arancio', 'Verde', 'Rosa', 'Viola', 'Giallo'][i] })) }
    ],
    testoOk: 'Salva'
  });
  if (!r) return;
  if (a) Object.assign(a, r);
  else Stato.appunti.unshift(Object.assign({ id: uid('ap'), creato: oraISO(), fonte: (precompilato && precompilato.fonte) || '' }, r));
  await salvaOra();
  if ($('#apLista')) disegnaAppunti();
  avvisoOk('Appunto salvato');
}

/* ------------------------------------------------------------------
   COMPITI
   ------------------------------------------------------------------ */

const Compiti = { vista: 'oggi' };
const PRIORITA = [{ v: 'alta', t: '🔴 Importante' }, { v: 'media', t: '🟡 Normale' }, { v: 'bassa', t: '🟢 Con calma' }];

VISTE.compiti = function (c) {
  c.innerHTML = testaSezione('📅', 'Compiti', null,
    bottone('home', '🏠', 'Home', 'btn-piccolo')) + `
    <div class="card no-stampa">
      <div class="barra-btn" style="margin-bottom:8px">
        ${bottone('cp-nuovo', '➕', 'Nuovo compito', 'btn-primario btn-grande')}
      </div>
      <div class="barra-btn" style="margin-bottom:0">
        ${['oggi', 'domani', 'settimana', 'tutti'].map((v) => `
          <button type="button" class="btn" data-az="cp-vista:${v}" aria-pressed="${Compiti.vista === v}">
            ${esc({ oggi: 'Oggi', domani: 'Domani', settimana: 'Questa settimana', tutti: 'Tutti' }[v])}</button>`).join('')}
      </div>
    </div>
    <div id="cpLista"></div>`;
  disegnaCompiti();
};

function disegnaCompiti() {
  const box = $('#cpLista');
  if (!box) return;
  const oggi = oggiISO(), domani = giorniDaOggi(1), fine = giorniDaOggi(7);
  const filtro = (t) => {
    if (Compiti.vista === 'tutti') return true;
    if (!t.data) return Compiti.vista === 'tutti';
    if (Compiti.vista === 'oggi') return t.data <= oggi;
    if (Compiti.vista === 'domani') return t.data === domani;
    return t.data >= oggi && t.data <= fine;
  };
  const lista = Stato.compiti.filter(filtro)
    .sort((a, b) => (a.fatto - b.fatto) || String(a.data).localeCompare(String(b.data)));

  box.innerHTML = lista.length ? `<ul class="lista">${lista.map((t) => `
    <li class="voce${t.fatto ? ' fatto' : ''}">
      <label class="check" style="min-height:0">
        <input type="checkbox" data-fatto="${esc(t.id)}"${t.fatto ? ' checked' : ''}
          aria-label="segna come fatto: ${esc(accorcia(t.descrizione, 40))}">
      </label>
      <div class="corpo">
        <b>${esc(t.descrizione)}</b>
        <span class="meta">${esc(t.materia || '')}${t.data ? ' · ' + esc(dataInParole(t.data)) : ''}
          ${t.data && t.data < oggi && !t.fatto ? ' · ⚠️ era per ieri o prima' : ''}</span>
        ${t.passi && t.passi.length ? `<ol style="margin:8px 0 0;padding-left:1.3em">
          ${t.passi.map((p, i) => `<li${p.fatto ? ' style="opacity:.55;text-decoration:line-through"' : ''}>
            <label class="check" style="min-height:0;display:inline-flex">
              <input type="checkbox" data-passo="${esc(t.id)}:${i}"${p.fatto ? ' checked' : ''}>
              <span>${esc(p.testo)}</span></label></li>`).join('')}</ol>` : ''}
      </div>
      <div class="azioni">
        <span class="tag ${t.priorita === 'alta' ? 'tag-err' : t.priorita === 'bassa' ? 'tag-ok' : 'tag-att'}">
          ${esc((PRIORITA.find((p) => p.v === t.priorita) || PRIORITA[1]).t)}</span>
        ${bottone('cp-spezza:' + t.id, '🪓', 'Spezza il compito', 'btn-piccolo')}
        ${bottone('cp-mod:' + t.id, '✏️', 'Modifica', 'btn-piccolo')}
        ${bottone('cp-focus:' + t.id, '🎯', 'Fallo adesso', 'btn-piccolo btn-primario')}
        ${bottone('cp-canc:' + t.id, '🗑️', 'Elimina', 'btn-piccolo btn-errore')}
      </div>
    </li>`).join('')}</ul>`
    : `<div class="card">${schedaVuota('📅', 'Niente da fare qui.',
        Compiti.vista === 'oggi' ? 'Per oggi sei a posto!' : 'Prova a guardare in "Tutti".')}</div>`;
}

async function modificaCompito(id) {
  const t = id ? Stato.compiti.find((x) => x.id === id) : null;
  const r = await finestra({
    titolo: t ? 'Modifica il compito' : 'Nuovo compito',
    campi: [
      { nome: 'materia', etichetta: 'Materia', tipo: 'scelta', opzioni: materie(), valore: t ? t.materia : materie()[0] },
      { nome: 'descrizione', etichetta: 'Che cosa devo fare', tipo: 'area', righe: 2, valore: t ? t.descrizione : '' },
      { nome: 'data', etichetta: 'Per quando', tipo: 'data', valore: t ? t.data : giorniDaOggi(1) },
      { nome: 'priorita', etichetta: 'Quanto è importante', tipo: 'scelta', opzioni: PRIORITA.map((p) => ({ v: p.v, t: p.t })), valore: t ? t.priorita : 'media' }
    ],
    testoOk: 'Salva'
  });
  if (!r) return;
  if (!r.descrizione.trim()) { toast('Scrivi che cosa devi fare.'); return; }
  if (t) Object.assign(t, r);
  else Stato.compiti.unshift(Object.assign({ id: uid('cp'), fatto: false, passi: [] }, r));
  await salvaOra();
  disegnaCompiti();
  avvisoOk('Compito salvato');
}

const PASSI_SUGGERITI = [
  'Leggo la prima parte',
  'Pausa di 3 minuti',
  'Sottolineo le parole importanti',
  'Leggo la seconda parte',
  'Mi faccio 3 domande',
  'Pausa di 3 minuti',
  'Ripasso a voce alta',
  'Controllo cosa mi ricordo'
];

async function spezzaCompito(id) {
  const t = Stato.compiti.find((x) => x.id === id);
  if (!t) return;
  const attuali = (t.passi || []).map((p) => p.testo).join('\n') || PASSI_SUGGERITI.join('\n');
  const r = await finestra({
    titolo: '🪓 Spezza il compito',
    testo: 'Un compito grande fa paura. Tanti pezzi piccoli no. Cambia i passaggi come vuoi: uno per riga.',
    campi: [{ nome: 'passi', etichetta: 'I miei passaggi', tipo: 'area', righe: 9, valore: attuali }],
    testoOk: 'Salva i passaggi'
  });
  if (!r) return;
  t.passi = r.passi.split('\n').map((s) => s.trim()).filter(Boolean).map((s) => ({ testo: s, fatto: false }));
  await salvaOra();
  disegnaCompiti();
  avvisoOk('Adesso è più facile: un pezzo alla volta');
}

/* ------------------------------------------------------------------
   CONCENTRAZIONE (timer)
   ------------------------------------------------------------------ */

const Timer = {
  attivita: '', secondi: 0, totale: 0, inPausa: true, fase: 'studio',
  studio: 20, pausa: 5, riferimento: null
};

const PRESET_TIMER = [[10, 3], [15, 5], [20, 5], [25, 5]];

VISTE.focus = function (c) {
  c.innerHTML = testaSezione('🎯', 'Concentrati', null,
    bottone('home', '🏠', 'Home', 'btn-piccolo')) + `
    <div class="card no-stampa" id="cardImpostaTimer">
      <label class="etichetta" for="tmAttivita">Adesso fai solo questo</label>
      <input class="campo" id="tmAttivita" type="text" value="${esc(Timer.attivita)}"
        placeholder="Per esempio: leggo storia pagine 20-22">
      <label class="etichetta">Quanto tempo?</label>
      <div class="barra-btn">
        ${PRESET_TIMER.map(([s, p]) => `<button type="button" class="btn btn-grande" data-az="tm-preset:${s}:${p}"
          aria-pressed="${Timer.studio === s && Timer.pausa === p}">${s} min studio<br>+ ${p} pausa</button>`).join('')}
        ${bottone('tm-personalizzato', '⏱️', 'Scelgo io')}
      </div>
      <div class="barra-btn" style="margin-top:12px">
        ${bottone('tm-avvia', '▶', 'Comincia', 'btn-primario btn-grande')}
      </div>
    </div>
    <div id="tmSchermo"></div>`;
  if (Timer.totale) disegnaTimer();
};

function avviaTimer(studio, pausa) {
  const a = $('#tmAttivita');
  if (a) Timer.attivita = a.value.trim();
  if (studio) { Timer.studio = studio; Timer.pausa = pausa; }
  Stato.impostazioni.timerStudio = Timer.studio;
  Stato.impostazioni.timerPausa = Timer.pausa;
  salva();
  Timer.fase = 'studio';
  Timer.totale = Timer.studio * 60;
  Timer.secondi = Timer.totale;
  Timer.inPausa = false;
  cambiaFocus(true);
  battitoTimer();
  disegnaTimer();
}

function battitoTimer() {
  clearInterval(Timer.riferimento);
  Timer.riferimento = setInterval(() => {
    if (Timer.inPausa) return;
    Timer.secondi--;
    if (Timer.secondi <= 0) {
      finePeriodo();
      return;
    }
    aggiornaOrologio();
  }, 1000);
}

function finePeriodo() {
  clearInterval(Timer.riferimento);
  const eraStudio = Timer.fase === 'studio';
  Timer.fase = eraStudio ? 'pausa' : 'studio';
  Timer.totale = (eraStudio ? Timer.pausa : Timer.studio) * 60;
  Timer.secondi = Timer.totale;
  Timer.inPausa = true;
  avvisaFinePeriodo(eraStudio);
  disegnaTimer();
}

function avvisaFinePeriodo(eraStudio) {
  const testo = eraStudio ? 'Tempo! Adesso fai una pausa.' : 'Pausa finita: si riprende quando vuoi tu.';
  toast(eraStudio ? '⏰ ' + testo : '💪 ' + testo);
  // avviso sonoro semplice, generato dal dispositivo (nessun file audio)
  try {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (AC) {
      const ctx = new AC();
      const o = ctx.createOscillator(), g = ctx.createGain();
      o.connect(g); g.connect(ctx.destination);
      o.frequency.value = eraStudio ? 660 : 440;
      g.gain.setValueAtTime(0.0001, ctx.currentTime);
      g.gain.exponentialRampToValueAtTime(0.2, ctx.currentTime + 0.05);
      g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 1.1);
      o.start(); o.stop(ctx.currentTime + 1.2);
      setTimeout(() => { try { ctx.close(); } catch (e) { /* niente */ } }, 1600);
    }
  } catch (e) { /* senza suono va bene lo stesso */ }
  try {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('Studio DSA', { body: testo });
    }
  } catch (e) { /* niente */ }
}

function aggiornaOrologio() {
  const n = $('#tmOrologio');
  if (!n) return;
  n.textContent = formattaTempo(Timer.secondi);
  const b = $('#tmBarra');
  if (b) b.style.width = (100 - Timer.secondi / Timer.totale * 100) + '%';
}

function formattaTempo(s) {
  const m = Math.floor(Math.max(0, s) / 60), r = Math.max(0, s) % 60;
  return m + ':' + String(r).padStart(2, '0');
}

function disegnaTimer() {
  const box = $('#tmSchermo');
  if (!box) return;
  const impo = $('#cardImpostaTimer');
  if (impo) impo.hidden = true;
  box.innerHTML = `<div class="card">
      <p class="timer-stato">${Timer.fase === 'studio' ? 'Adesso fai solo questo' : 'Pausa'}</p>
      ${Timer.fase === 'studio' && Timer.attivita ? `<p class="timer-cosa">${esc(Timer.attivita)}</p>` : ''}
      ${Timer.fase === 'pausa' ? '<p class="timer-cosa">Alzati, bevi, guarda lontano 😊</p>' : ''}
      <div class="timer-grande" id="tmOrologio" aria-live="off">${formattaTempo(Timer.secondi)}</div>
      <div class="perc-barra"><div class="riempi" id="tmBarra" style="width:${100 - Timer.secondi / Timer.totale * 100}%"></div></div>
      <div class="barra-btn centro" style="margin-top:16px">
        <button type="button" class="btn btn-primario btn-grande" data-az="tm-pausa">
          ${Timer.inPausa ? '▶ Riprendi' : '⏸ Metti in pausa'}</button>
        ${bottone('tm-stop', '⏹', 'Ho finito', 'btn-grande')}
      </div>
    </div>`;
}

function fermaTimer() {
  clearInterval(Timer.riferimento);
  Timer.riferimento = null;
  Timer.totale = 0;
  cambiaFocus(false);
  disegna();
}

/* ------------------------------------------------------------------
   LE MIE PAROLE (vocabolario, parole difficili, inglese)
   ------------------------------------------------------------------ */

const Parole = { tab: 'vocabolario' };

VISTE.parole = function (c) {
  c.innerHTML = testaSezione('🔤', 'Le mie parole', null,
    bottone('home', '🏠', 'Home', 'btn-piccolo')) + `
    <div class="card no-stampa">
      <div class="barra-btn" style="margin-bottom:0">
        <button type="button" class="btn btn-grande" data-az="pr-tab:vocabolario" aria-pressed="${Parole.tab === 'vocabolario'}">📖 Vocabolario</button>
        <button type="button" class="btn btn-grande" data-az="pr-tab:difficili" aria-pressed="${Parole.tab === 'difficili'}">✏️ Parole difficili</button>
        <button type="button" class="btn btn-grande" data-az="pr-tab:inglese" aria-pressed="${Parole.tab === 'inglese'}">🇬🇧 Inglese</button>
      </div>
    </div>
    <div id="prBox"></div>`;
  disegnaParole();
};

function disegnaParole() {
  const box = $('#prBox');
  if (!box) return;
  if (Parole.tab === 'vocabolario') {
    box.innerHTML = `<div class="card">
        <p>Questo è il TUO vocabolario: non c'è nessun dizionario dentro l'app (servirebbe Internet).
          Le parole le scrivi tu, con le tue parole.</p>
        <div class="barra-btn">${bottone('pr-nuovaVoc', '➕', 'Aggiungi una parola', 'btn-primario btn-grande')}</div>
      </div>
      ${Stato.vocabolario.length ? `<ul class="lista">${Stato.vocabolario.map((v) => `
        <li class="voce"><div class="corpo">
          <b>${esc(v.parola)}</b>
          <span class="meta">${esc(v.materia || '')}</span>
          <p style="margin:6px 0 0">${esc(v.significato)}</p>
          ${v.esempio ? `<p style="margin:4px 0 0"><i>${esc(v.esempio)}</i></p>` : ''}
        </div><div class="azioni">
          ${bottone('pr-leggiVoc:' + v.id, '🔊', 'Ascolta', 'btn-piccolo')}
          ${bottone('pr-modVoc:' + v.id, '✏️', 'Modifica', 'btn-piccolo')}
          ${bottone('pr-cancVoc:' + v.id, '🗑️', 'Elimina', 'btn-piccolo btn-errore')}
        </div></li>`).join('')}</ul>`
        : `<div class="card">${schedaVuota('📖', 'Il vocabolario è vuoto.')}</div>`}`;
  } else if (Parole.tab === 'difficili') {
    box.innerHTML = `<div class="card">
        <p>Le parole che sbagli spesso. Scriverle qui, con la sillabazione fatta da te, aiuta a ricordarle.</p>
        <div class="barra-btn">${bottone('pr-nuovaDiff', '➕', 'Aggiungi una parola difficile', 'btn-primario btn-grande')}</div>
      </div>
      ${Stato.paroleDifficili.length ? Stato.paroleDifficili.map((v) => `
        <div class="card">
          <h3 style="margin-bottom:4px">${esc(v.parola)}</h3>
          ${v.sillabe ? `<p class="mate" style="font-size:1.3em;letter-spacing:.08em">${esc(v.sillabe)}</p>` : ''}
          ${v.errore ? `<p><b>❌ Come la sbaglio:</b> ${esc(v.errore)}</p>` : ''}
          ${v.esempio ? `<p><b>Esempio:</b> ${esc(v.esempio)}</p>` : ''}
          <div class="barra-btn" style="margin:0">
            ${bottone('pr-leggiDiff:' + v.id, '🔊', 'Ascolta la parola', 'btn-piccolo')}
            ${bottone('pr-modDiff:' + v.id, '✏️', 'Modifica', 'btn-piccolo')}
            ${bottone('pr-cancDiff:' + v.id, '🗑️', 'Elimina', 'btn-piccolo btn-errore')}
          </div>
        </div>`).join('')
        : `<div class="card">${schedaVuota('✏️', 'Non hai ancora segnato nessuna parola.')}</div>`}`;
  } else {
    box.innerHTML = `<div class="card">
        <div class="barra-btn">
          ${bottone('pr-nuovaEn', '➕', 'Aggiungi un vocabolo', 'btn-primario btn-grande')}
          ${bottone('pr-flashEn', '🃏', 'Crea flashcard inglese ⇄ italiano')}
        </div>
      </div>
      ${Stato.inglese.length ? `<div class="tabella-scroll"><table class="tab">
        <tr><th>English</th><th>Italiano</th><th>Esempio</th><th></th></tr>
        ${Stato.inglese.map((v) => `<tr>
          <td><b>${esc(v.en)}</b></td><td>${esc(v.it)}</td><td><i>${esc(v.esempio || '')}</i></td>
          <td style="white-space:nowrap">
            ${bottone('pr-leggiEn:' + v.id, '🔊', '', 'btn-piccolo')}
            ${bottone('pr-cancEn:' + v.id, '🗑️', '', 'btn-piccolo btn-errore')}</td>
        </tr>`).join('')}</table></div>`
        : `<div class="card">${schedaVuota('🇬🇧', 'Nessun vocabolo inglese.')}</div>`}`;
  }
}

async function nuovaParola(tipo, id) {
  if (tipo === 'vocabolario') {
    const v = id ? Stato.vocabolario.find((x) => x.id === id) : null;
    const r = await finestra({
      titolo: v ? 'Modifica la parola' : 'Nuova parola',
      campi: [
        { nome: 'parola', etichetta: 'Parola', valore: v ? v.parola : '' },
        { nome: 'significato', etichetta: 'Significato (con parole tue)', tipo: 'area', righe: 3, valore: v ? v.significato : '' },
        { nome: 'esempio', etichetta: 'Esempio', valore: v ? v.esempio : '' },
        { nome: 'materia', etichetta: 'Materia', tipo: 'scelta', opzioni: materie(), valore: v ? v.materia : materie()[0] }
      ], testoOk: 'Salva'
    });
    if (!r || !r.parola.trim()) return;
    if (v) Object.assign(v, r); else Stato.vocabolario.unshift(Object.assign({ id: uid('vc') }, r));
  } else if (tipo === 'difficili') {
    const v = id ? Stato.paroleDifficili.find((x) => x.id === id) : null;
    const r = await finestra({
      titolo: v ? 'Modifica' : 'Una parola difficile',
      campi: [
        { nome: 'parola', etichetta: 'Parola scritta giusta', valore: v ? v.parola : '' },
        { nome: 'errore', etichetta: 'Come la sbaglio di solito', valore: v ? v.errore : '' },
        { nome: 'sillabe', etichetta: 'Divisa in sillabe (scrivila tu)', valore: v ? v.sillabe : '', aiuto: 'Per esempio: ac-qua' },
        { nome: 'esempio', etichetta: 'Frase di esempio', valore: v ? v.esempio : '' }
      ], testoOk: 'Salva'
    });
    if (!r || !r.parola.trim()) return;
    if (v) Object.assign(v, r); else Stato.paroleDifficili.unshift(Object.assign({ id: uid('pd') }, r));
  } else {
    const r = await finestra({
      titolo: 'Nuovo vocabolo inglese',
      campi: [
        { nome: 'en', etichetta: 'English', valore: '' },
        { nome: 'it', etichetta: 'Italiano', valore: '' },
        { nome: 'esempio', etichetta: 'Esempio', valore: '' }
      ], testoOk: 'Salva'
    });
    if (!r || !r.en.trim()) return;
    Stato.inglese.unshift(Object.assign({ id: uid('en') }, r));
  }
  await salvaOra();
  disegnaParole();
  avvisoOk('Salvato');
}

async function flashcardInglese() {
  if (!Stato.inglese.length) { toast('Prima aggiungi qualche vocabolo.'); return; }
  const r = await finestra({
    titolo: 'Flashcard di inglese',
    campi: [{ nome: 'verso', etichetta: 'In che verso?', tipo: 'scelta', valore: 'en-it',
      opzioni: [{ v: 'en-it', t: 'Inglese → Italiano' }, { v: 'it-en', t: 'Italiano → Inglese' }, { v: 'due', t: 'Tutte e due' }] }],
    testoOk: 'Crea'
  });
  if (!r) return;
  let n = 0;
  Stato.inglese.forEach((v) => {
    if (r.verso === 'en-it' || r.verso === 'due') { Stato.flashcard.unshift(nuovaCard(v.en, v.it, 'Inglese')); n++; }
    if (r.verso === 'it-en' || r.verso === 'due') { Stato.flashcard.unshift(nuovaCard(v.it, v.en, 'Inglese')); n++; }
  });
  await salvaOra();
  avvisoOk('Ho creato ' + n + ' flashcard');
}


/* ============================================================
   12-pdf.js
   ============================================================ */
/* ==================================================================
   12-pdf.js — lettore PDF completamente offline

   Usa PDF.js 3.11.174 (licenza Apache 2.0), che è dentro
   la cartella dell'applicazione: nessun file viene scaricato da
   Internet e nessun PDF esce mai dal dispositivo.
   ================================================================== */

const Pdf = {
  lib: null,               // pdfjsLib, caricata solo quando serve
  doc: null,               // documento aperto
  libroId: null,
  pagina: 1,
  totale: 0,
  zoom: 1.2,
  adatta: 'larghezza',     // 'larghezza' | 'pagina' | 'no'
  testoPagina: '',
  haTesto: true,
  modoDsa: false,
  categoria: 'importante',
  inCaricamento: false
};

const CATEGORIE_EVID = [
  { id: 'importante', tit: 'Importante', colore: '#ffe14d' },
  { id: 'definizione', tit: 'Definizione', colore: '#9fe6a0' },
  { id: 'data', tit: 'Data', colore: '#9fd4f5' },
  { id: 'formula', tit: 'Formula', colore: '#f5b6e0' },
  { id: 'nonCapito', tit: 'Non ho capito', colore: '#ffb3a7' }
];

/* ------------------------------------------------------------------
   Caricamento di PDF.js dalla cartella locale
   ------------------------------------------------------------------ */

function caricaPdfLib() {
  if (Pdf.lib) return Promise.resolve(Pdf.lib);
  return new Promise((risolvi, rifiuta) => {
    const sorgenteInterna = document.getElementById('pdfjsLibSrc');   // versione in un unico file
    function dopo() {
      const lib = window.pdfjsLib;
      if (!lib) { rifiuta(new Error('pdfjs')); return; }
      try {
        const w = document.getElementById('pdfjsWorkerSrc');
        if (w) {
          const blob = new Blob([w.textContent], { type: 'text/javascript' });
          lib.GlobalWorkerOptions.workerSrc = URL.createObjectURL(blob);
        } else {
          lib.GlobalWorkerOptions.workerSrc = 'assets/pdfjs/pdf.worker.min.js';
        }
      } catch (e) { /* PDF.js userà il modo "senza worker": più lento ma funziona */ }
      Pdf.lib = lib;
      risolvi(lib);
    }
    if (sorgenteInterna) {
      try {
        const s = document.createElement('script');
        s.textContent = sorgenteInterna.textContent;
        document.head.appendChild(s);
        dopo();
      } catch (e) { rifiuta(e); }
    } else {
      const s = document.createElement('script');
      s.src = 'assets/pdfjs/pdf.min.js';
      s.onload = dopo;
      s.onerror = () => rifiuta(new Error('file'));
      document.head.appendChild(s);
    }
  });
}

/* ------------------------------------------------------------------
   Vista principale
   ------------------------------------------------------------------ */

VISTE.pdf = function (c, par) {
  c.innerHTML = testaSezione('📄', 'PDF e libri', null,
    bottone('home', '🏠', 'Home', 'btn-piccolo')) + `
    <div class="pdf-barra no-stampa" id="pdfBarra"></div>
    <div id="pdfCorpo"></div>`;
  disegnaBarraPdf();

  if (par && par !== Pdf.libroId) apriLibroSalvato(par);
  else if (Pdf.doc) { disegnaVisualizzatore(); mostraPagina(); }
  else disegnaLibreria();
};

function disegnaBarraPdf() {
  const b = $('#pdfBarra');
  if (!b) return;
  if (!Pdf.doc) {
    b.innerHTML = `${bottone('pdf-apri', '📂', 'Apri un PDF', 'btn-primario')}
      ${bottone('pdf-libreria', '📚', 'I miei libri')}`;
    return;
  }
  b.innerHTML = `
    ${bottone('pdf-libreria', '📚', 'Libri', 'btn-piccolo')}
    <div class="pdf-sep"></div>
    <button type="button" class="btn" data-az="pdf-prec" aria-label="pagina precedente">◀</button>
    <input class="campo pdf-pagina-campo" id="pdfPag" type="number" min="1" max="${Pdf.totale}" value="${Pdf.pagina}" aria-label="numero di pagina">
    <span class="pdf-conta">/ ${Pdf.totale}</span>
    <button type="button" class="btn" data-az="pdf-succ" aria-label="pagina successiva">▶</button>
    <div class="pdf-sep"></div>
    <button type="button" class="btn" data-az="pdf-zoom+" aria-label="ingrandisci">🔍+</button>
    <button type="button" class="btn" data-az="pdf-zoom-" aria-label="rimpicciolisci">🔍−</button>
    <button type="button" class="btn" data-az="pdf-adatta" aria-label="adatta alla larghezza">↔️</button>
    <div class="pdf-sep"></div>
    <button type="button" class="btn ${Pdf.modoDsa ? 'btn-primario' : ''}" data-az="pdf-dsa"
      aria-pressed="${Pdf.modoDsa}"><span aria-hidden="true">🧠</span><span>Modalità DSA</span></button>
    ${bottone('pdf-ascolta', '🔊', 'Leggi questa pagina')}
    ${bottone('pdf-frase', '1️⃣', 'Una frase alla volta')}
    <button type="button" class="btn" data-az="righello" aria-pressed="${Righello.aperto}"><span aria-hidden="true">📏</span><span>Righello</span></button>
    ${bottone('pdf-cerca', '🔎', 'Cerca')}
    ${bottone('pdf-segnalibro', '🔖', 'Segnalibro')}
    ${bottone('pdf-note', '📝', 'Note')}`;

  const inp = $('#pdfPag', b);
  if (inp) inp.addEventListener('change', () => vaiAPagina(Number(inp.value)));
}

/* ------------------------------------------------------------------
   Libreria personale
   ------------------------------------------------------------------ */

function disegnaLibreria() {
  const box = $('#pdfCorpo');
  box.innerHTML = `<div class="card">
      <h2>📚 I miei libri</h2>
      <p>Apri un PDF dal tuo dispositivo: resta qui, non viene mandato da nessuna parte.</p>
      <div class="barra-btn">${bottone('pdf-apri', '📂', 'Apri un PDF', 'btn-primario btn-grande')}</div>
    </div>
    ${Stato.libri.length ? Stato.libri.map((l) => {
      const perc = l.totale ? Math.round(l.ultimaPagina / l.totale * 100) : 0;
      const note = Stato.notePdf.filter((n) => n.libroId === l.id).length;
      const evid = Stato.evidenziazioni.filter((n) => n.libroId === l.id).length;
      const segn = Stato.segnalibri.filter((n) => n.libroId === l.id).length;
      return `<div class="card">
        <div class="libro">
          <div class="copertina" aria-hidden="true">📕</div>
          <div style="flex:1 1 auto;min-width:0">
            <b>${esc(l.nome)}</b>
            <div class="aiutino">${esc(l.materia || 'Nessuna materia')} · ultima apertura ${esc(quandoInParole(l.ultimoAccesso))}</div>
            <div class="aiutino">Pagina ${l.ultimaPagina || 1} di ${l.totale || '?'}${note || evid || segn ? ' · ' + [
              note ? note + ' note' : '', evid ? evid + ' evidenziazioni' : '', segn ? segn + ' segnalibri' : ''
            ].filter(Boolean).join(', ') : ''}</div>
            <div class="avanzamento" role="img" aria-label="letto il ${perc} per cento"><div class="riempi" style="width:${perc}%"></div></div>
          </div>
        </div>
        <div class="barra-btn" style="margin:12px 0 0">
          ${bottone('pdf-riapri:' + l.id, '📖', l.haFile ? 'Continua da pagina ' + (l.ultimaPagina || 1) : 'Riscegli il file e continua', 'btn-primario')}
          ${bottone('pdf-materia:' + l.id, '🏷️', 'Materia', 'btn-piccolo')}
          ${bottone('pdf-canc:' + l.id, '🗑️', 'Togli dalla libreria', 'btn-piccolo btn-errore')}
        </div>
      </div>`;
    }).join('')
    : `<div class="card">${schedaVuota('📚', 'Non hai ancora nessun libro.', 'Apri un PDF: mi ricorderò la pagina, le note e le evidenziazioni.')}</div>`}`;
}

/* ------------------------------------------------------------------
   Apertura di un PDF
   ------------------------------------------------------------------ */

async function apriPdfDaFile(libroEsistente) {
  const f = await scegliFile('.pdf,application/pdf');
  if (!f) return;
  if (!/\.pdf$/i.test(f.name) && f.type !== 'application/pdf') {
    avvisoErrore('Questo non sembra un file PDF.');
    return;
  }
  const box = $('#pdfCorpo');
  box.innerHTML = `<div class="card"><p>⏳ Sto aprendo <b>${esc(f.name)}</b>… un momento.</p></div>`;
  try {
    const byte = new Uint8Array(await f.arrayBuffer());
    await apriPdfDaByte(byte, f.name, libroEsistente, true);
  } catch (e) {
    console.error(e);
    box.innerHTML = `<div class="card"><div class="avviso avviso-err"><span class="ic">⚠️</span>
      <p>Non sono riuscito ad aprire questo PDF. Può essere protetto da password oppure rovinato.
      Prova con un altro file.</p></div>
      <div class="barra-btn">${bottone('pdf-libreria', '⬅', 'Torna ai libri')}</div></div>`;
  }
}

async function apriPdfDaByte(byte, nome, libroEsistente, salvaByte) {
  const lib = await caricaPdfLib().catch(() => null);
  if (!lib) {
    $('#pdfCorpo').innerHTML = `<div class="card"><div class="avviso avviso-err"><span class="ic">⚠️</span>
      <p>Il lettore PDF non si è caricato. Se stai usando la versione con più file, controlla che
      la cartella <b>assets</b> sia accanto al file index.html.</p></div></div>`;
    return;
  }
  // pdf.js consuma l'array: gliene do sempre una copia
  const doc = await lib.getDocument({ data: byte.slice(0) }).promise;
  Pdf.doc = doc;
  Pdf.totale = doc.numPages;

  let libro = libroEsistente ? Stato.libri.find((l) => l.id === libroEsistente) : null;
  if (!libro) libro = Stato.libri.find((l) => l.nome === nome);
  if (!libro) {
    libro = { id: uid('libro'), nome, materia: '', ultimaPagina: 1, totale: doc.numPages, zoom: Pdf.zoom, haFile: false, creato: oraISO() };
    Stato.libri.unshift(libro);
  }
  libro.totale = doc.numPages;
  libro.ultimoAccesso = oraISO();
  Pdf.libroId = libro.id;
  Pdf.pagina = limita(libro.ultimaPagina || 1, 1, doc.numPages);
  Pdf.zoom = libro.zoom || Pdf.zoom;
  await salvaOra();

  if (salvaByte) {
    try {
      await Archivio.scrivi('pdf:' + libro.id, byte);
      libro.haFile = true;
      await salvaOra();
    } catch (e) {
      libro.haFile = false;
      toast('Il PDF è troppo grande per essere tenuto in memoria: la prossima volta te lo richiederò.');
    }
  }
  disegnaBarraPdf();
  disegnaVisualizzatore();
  await mostraPagina();
}

async function apriLibroSalvato(id) {
  const libro = Stato.libri.find((l) => l.id === id);
  if (!libro) { disegnaLibreria(); return; }
  const box = $('#pdfCorpo');
  box.innerHTML = `<div class="card"><p>⏳ Sto cercando il file…</p></div>`;
  const byte = await Archivio.leggi('pdf:' + id);
  if (byte && byte.byteLength) {
    try { await apriPdfDaByte(new Uint8Array(byte), libro.nome, id, false); return; }
    catch (e) { /* passo alla richiesta del file */ }
  }
  box.innerHTML = `<div class="card">
    <h2>${esc(libro.nome)}</h2>
    <div class="avviso"><span class="ic">📂</span>
      <p>Per motivi di sicurezza il browser non può riaprire da solo un file del tuo dispositivo.
      Scegli di nuovo <b>${esc(libro.nome)}</b>: ritroverai la pagina ${libro.ultimaPagina || 1},
      le note, le evidenziazioni e i segnalibri.</p></div>
    <div class="barra-btn">
      ${bottone('pdf-riscegli:' + id, '📂', 'Scegli il file', 'btn-primario btn-grande')}
      ${bottone('pdf-libreria', '⬅', 'Torna ai libri')}
    </div></div>`;
}

/* ------------------------------------------------------------------
   Visualizzatore
   ------------------------------------------------------------------ */

function disegnaVisualizzatore() {
  $('#pdfCorpo').innerHTML = `
    <div class="pdf-scena" id="pdfScena">
      <div class="pdf-foglio" id="pdfFoglio">
        <canvas id="pdfCanvas"></canvas>
        <div class="pdf-testo" id="pdfLivelloTesto"></div>
      </div>
    </div>
    <div id="pdfPannello"></div>`;
}

async function mostraPagina() {
  if (!Pdf.doc || Pdf.inCaricamento) return;
  Pdf.inCaricamento = true;
  try {
    const pagina = await Pdf.doc.getPage(Pdf.pagina);
    const scena = $('#pdfScena');
    let scala = Pdf.zoom;
    const base = pagina.getViewport({ scale: 1 });
    if (Pdf.adatta === 'larghezza' && scena) scala = (scena.clientWidth - 30) / base.width;
    else if (Pdf.adatta === 'pagina' && scena) scala = Math.min((scena.clientWidth - 30) / base.width, (window.innerHeight * 0.72) / base.height);
    scala = limita(scala, 0.25, 5);
    const viewport = pagina.getViewport({ scale: scala });

    const cv = $('#pdfCanvas');
    const S = Math.min(2, window.devicePixelRatio || 1);
    cv.width = Math.floor(viewport.width * S);
    cv.height = Math.floor(viewport.height * S);
    cv.style.width = Math.floor(viewport.width) + 'px';
    cv.style.height = Math.floor(viewport.height) + 'px';
    const g = cv.getContext('2d');
    g.setTransform(S, 0, 0, S, 0, 0);
    await pagina.render({ canvasContext: g, viewport }).promise;

    const foglio = $('#pdfFoglio');
    foglio.style.width = Math.floor(viewport.width) + 'px';

    // livello del testo (per selezionare e copiare)
    const contenuto = await pagina.getTextContent();
    Pdf.testoPagina = componiTesto(contenuto);
    Pdf.haTesto = Pdf.testoPagina.replace(/\s/g, '').length > 12;
    costruisciLivelloTesto(contenuto, viewport);
    disegnaEvidenziazioni(viewport);

    const libro = Stato.libri.find((l) => l.id === Pdf.libroId);
    if (libro) { libro.ultimaPagina = Pdf.pagina; libro.zoom = Pdf.zoom; libro.ultimoAccesso = oraISO(); salva(); }

    const inp = $('#pdfPag');
    if (inp) inp.value = Pdf.pagina;

    if (!Pdf.haTesto) mostraAvvisoScansione();
    else { const p = $('#pdfPannello'); if (p && p._scansione) { p.innerHTML = ''; p._scansione = false; } }

    if (Pdf.modoDsa) mostraModoDsa();
  } catch (e) {
    console.error(e);
    avvisoErrore('Non riesco a mostrare questa pagina.');
  } finally {
    Pdf.inCaricamento = false;
  }
}

function componiTesto(contenuto) {
  let testo = '';
  let ultimaY = null;
  contenuto.items.forEach((it) => {
    if (!it.str) return;
    const y = it.transform ? Math.round(it.transform[5]) : null;
    if (ultimaY !== null && y !== null && Math.abs(y - ultimaY) > 3) testo += (it.hasEOL ? '\n' : '\n');
    else if (testo && !/\s$/.test(testo) && !/^\s/.test(it.str)) testo += ' ';
    testo += it.str;
    ultimaY = y;
  });
  return testo.replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
}

function costruisciLivelloTesto(contenuto, viewport) {
  const box = $('#pdfLivelloTesto');
  if (!box) return;
  box.innerHTML = '';
  box.style.width = viewport.width + 'px';
  box.style.height = viewport.height + 'px';
  const lib = Pdf.lib;
  contenuto.items.forEach((it) => {
    if (!it.str || !it.str.trim()) return;
    let t;
    try { t = lib.Util.transform(viewport.transform, it.transform); }
    catch (e) { return; }
    const altezza = Math.hypot(t[2], t[3]) || 12;
    const sp = document.createElement('span');
    sp.textContent = it.str;
    sp.style.left = t[4] + 'px';
    sp.style.top = (t[5] - altezza) + 'px';
    sp.style.fontSize = altezza + 'px';
    sp.style.fontFamily = 'sans-serif';
    if (it.width) {
      const largheza = it.width * viewport.scale;
      sp.dataset.larghezza = largheza;
    }
    box.appendChild(sp);
    // adatto la larghezza del testo invisibile a quella reale
    if (it.width) {
      const reale = sp.getBoundingClientRect().width;
      const voluta = it.width * viewport.scale;
      if (reale > 0 && voluta > 0) sp.style.transform = 'scaleX(' + (voluta / reale) + ')';
    }
  });
}

function vaiAPagina(n) {
  if (!Pdf.doc) return;
  Pdf.pagina = limita(Math.round(n) || 1, 1, Pdf.totale);
  mostraPagina();
}

/* ------------------------------------------------------------------
   Pagine scansionate: lo diciamo chiaramente
   ------------------------------------------------------------------ */

function mostraAvvisoScansione() {
  const p = $('#pdfPannello');
  if (!p) return;
  p._scansione = true;
  p.innerHTML = `<div class="card">
    <div class="avviso avviso-att"><span class="ic">🖼️</span>
      <p><b>Questa pagina sembra essere un'immagine.</b> Dentro non c'è testo che io possa leggere,
      quindi non posso né ingrandirlo in modalità DSA né leggerlo ad alta voce.
      Per trasformare l'immagine in testo servirebbe il riconoscimento OCR, che questa applicazione
      non include: renderebbe l'app cinque volte più pesante e ci metterebbe quasi un minuto per pagina.</p></div>
    <div class="avviso"><span class="ic">💡</span>
      <p><b>Il tuo dispositivo però sa già farlo.</b> Fai uno screenshot della pagina, poi:
      su iPhone, iPad e Mac tieni premuto sul testo (“Testo attivo”) e copia;
      su Windows 11 usa lo Strumento di cattura e poi “Azioni di testo”;
      su Android usa Google Lens. Poi torna qui e incolla con il pulsante qui sotto.</p></div>
    <p><b>Cosa puoi fare adesso:</b></p>
    <ul>
      <li>ingrandire la pagina con 🔍+ e usare il righello 📏;</li>
      <li>incollare (o scrivere) tu la parte che ti serve: dopo potrai ascoltarla come tutto il resto;</li>
      <li>chiedere all'insegnante il PDF "con il testo" invece della scansione.</li>
    </ul>
    <div class="barra-btn">
      ${bottone('pdf-trascrivi', '📋', 'Incolla o scrivi il testo di questa pagina', 'btn-primario btn-grande')}
    </div>
  </div>`;
}

async function trascriviPagina() {
  const r = await finestra({
    titolo: 'Il testo della pagina ' + Pdf.pagina,
    testo: 'Incolla qui il testo che hai copiato con il tuo dispositivo, oppure scrivilo (o dettalo con la tastiera del telefono). Poi potrai ascoltarlo e usarlo come tutto il resto.',
    campi: [{ nome: 'testo', etichetta: 'Testo', tipo: 'area', righe: 8, valore: '' }],
    testoOk: 'Usa questo testo'
  });
  if (!r || !r.testo.trim()) return;
  Pdf.testoPagina = r.testo;
  Pdf.haTesto = true;
  Pdf.modoDsa = true;
  disegnaBarraPdf();
  mostraModoDsa();
}

/* ------------------------------------------------------------------
   Modalità DSA: il testo della pagina, scritto in modo leggibile
   ------------------------------------------------------------------ */

function mostraModoDsa() {
  const p = $('#pdfPannello');
  if (!p) return;
  if (!Pdf.haTesto) { mostraAvvisoScansione(); return; }
  const dati = fogliaTesto(Pdf.testoPagina);
  p.innerHTML = `<div class="card">
      <div class="barra-btn">
        ${bottone('pdf-dsaChiudi', '✕', 'Chiudi la modalità DSA')}
        ${bottone('impo', '⚙️', 'Come si vede')}
        <button type="button" class="btn" data-az="sillabe" aria-pressed="${Stato.impostazioni.spaziaturaSillabe}">
          <span aria-hidden="true">🔠</span><span>Più spazio</span></button>
        ${bottone('pdf-appunto', '📓', 'Porta negli appunti')}
        ${bottone('pdf-inLettura', '📖', 'Aprilo in Leggi')}
      </div>
      <div id="pdfBarraVoce"></div>
      <div class="foglio"><div class="foglio-testo${Stato.impostazioni.spaziaturaSillabe ? ' sillabe' : ''}" id="foglioTesto">${dati.html}</div></div>
    </div>`;
  const bv = $('#pdfBarraVoce');
  bv.appendChild(barraLettura(() => dati.frasi, {
    onFrase: (i) => {
      const n = $('#foglioTesto [data-f="' + i + '"]');
      if (n) {
        $$('#foglioTesto .frase.attiva').forEach((x) => x.classList.remove('attiva'));
        n.classList.add('attiva');
        n.scrollIntoView({ block: 'center', behavior: 'smooth' });
      }
    },
    onFine: () => $$('#foglioTesto .frase.attiva').forEach((x) => x.classList.remove('attiva'))
  }));
}

/* ------------------------------------------------------------------
   Selezione del testo → menu delle azioni
   ------------------------------------------------------------------ */

function testoSelezionato() {
  const s = window.getSelection();
  return s && s.toString().trim() ? s.toString().trim() : '';
}

function mostraMenuSelezione(testo, x, y) {
  chiudiMenuSelezione();
  const menu = document.createElement('div');
  menu.className = 'menu-selezione';
  menu.id = 'menuSelezione';
  menu.innerHTML = `
    <div class="estratto">${esc(accorcia(testo, 160))}</div>
    <div class="voci">
      ${bottone('sel-leggi', '🔊', 'Leggi')}
      ${bottone('sel-evidenzia', '🖍️', 'Evidenzia')}
      ${bottone('sel-nota', '📝', 'Nota')}
      ${bottone('sel-appunto', '📓', 'Appunti')}
      ${bottone('sel-flash', '🃏', 'Flashcard')}
      ${bottone('sel-mappa', '🗺️', 'Mappa')}
      ${bottone('sel-mate', '🔢', 'Matematica')}
      ${bottone('sel-dsa', '🧠', 'Leggilo grande')}
      ${bottone('sel-chiudi', '✕', 'Chiudi')}
    </div>`;
  document.body.appendChild(menu);
  const larg = menu.offsetWidth, alt = menu.offsetHeight;
  menu.style.left = limita(x - larg / 2, 8, window.innerWidth - larg - 8) + 'px';
  menu.style.top = limita(y + 12, 8, window.innerHeight - alt - 8) + 'px';
  menu._testo = testo;
}

function chiudiMenuSelezione() {
  const m = $('#menuSelezione');
  if (m) m.remove();
}

function selezioneCorrente() {
  const m = $('#menuSelezione');
  return m ? m._testo : '';
}

/* ------------------------------------------------------------------
   Evidenziazioni
   ------------------------------------------------------------------ */

async function evidenziaSelezione() {
  const testo = selezioneCorrente();
  if (!testo) return;
  const r = await finestra({
    titolo: 'Evidenzia',
    campi: [{ nome: 'cat', etichetta: 'Che tipo di informazione è?', tipo: 'scelta', valore: Pdf.categoria,
      opzioni: CATEGORIE_EVID.map((c) => ({ v: c.id, t: c.tit })) }],
    testoOk: 'Evidenzia'
  });
  if (!r) return;
  Pdf.categoria = r.cat;
  const rettangoli = rettangoliSelezione();
  Stato.evidenziazioni.push({
    id: uid('ev'), libroId: Pdf.libroId, pagina: Pdf.pagina,
    testo, categoria: r.cat, rettangoli, quando: oraISO()
  });
  await salvaOra();
  chiudiMenuSelezione();
  window.getSelection().removeAllRanges();
  const foglio = $('#pdfFoglio');
  if (foglio) {
    const pagina = await Pdf.doc.getPage(Pdf.pagina);
    disegnaEvidenziazioni(pagina.getViewport({ scale: 1 }));
  }
  avvisoOk('Evidenziato');
}

/** Salvo i rettangoli in percentuale, così valgono a qualsiasi zoom. */
function rettangoliSelezione() {
  const foglio = $('#pdfFoglio');
  const s = window.getSelection();
  if (!foglio || !s || !s.rangeCount) return [];
  const base = foglio.getBoundingClientRect();
  const out = [];
  for (let i = 0; i < s.rangeCount; i++) {
    const rects = s.getRangeAt(i).getClientRects();
    for (let k = 0; k < rects.length; k++) {
      const r = rects[k];
      if (r.width < 2 || r.height < 2) continue;
      out.push({
        x: (r.left - base.left) / base.width,
        y: (r.top - base.top) / base.height,
        w: r.width / base.width,
        h: r.height / base.height
      });
    }
  }
  return out;
}

function disegnaEvidenziazioni() {
  const foglio = $('#pdfFoglio');
  if (!foglio) return;
  $$('.pdf-evid', foglio).forEach((n) => n.remove());
  Stato.evidenziazioni
    .filter((e) => e.libroId === Pdf.libroId && e.pagina === Pdf.pagina)
    .forEach((e) => {
      const cat = CATEGORIE_EVID.find((c) => c.id === e.categoria) || CATEGORIE_EVID[0];
      (e.rettangoli || []).forEach((r) => {
        const d = document.createElement('div');
        d.className = 'pdf-evid';
        d.style.left = (r.x * 100) + '%';
        d.style.top = (r.y * 100) + '%';
        d.style.width = (r.w * 100) + '%';
        d.style.height = (r.h * 100) + '%';
        d.style.background = cat.colore;
        d.title = cat.tit;
        foglio.appendChild(d);
      });
    });
}

/* ------------------------------------------------------------------
   Note, segnalibri, ricerca
   ------------------------------------------------------------------ */

async function notaSuPdf() {
  const testo = selezioneCorrente();
  const r = await finestra({
    titolo: '📝 Nota a pagina ' + Pdf.pagina,
    testo: testo ? 'Testo scelto: "' + accorcia(testo, 120) + '"' : '',
    campi: [{ nome: 'nota', etichetta: 'La mia nota', tipo: 'area', righe: 4, valore: '' }],
    testoOk: 'Salva la nota'
  });
  if (!r || !r.nota.trim()) return;
  Stato.notePdf.unshift({
    id: uid('nt'), libroId: Pdf.libroId, pagina: Pdf.pagina,
    testoSelezionato: testo, nota: r.nota, quando: oraISO()
  });
  await salvaOra();
  chiudiMenuSelezione();
  avvisoOk('Nota salvata');
}

function mostraNotePdf() {
  const p = $('#pdfPannello');
  const note = Stato.notePdf.filter((n) => n.libroId === Pdf.libroId);
  const evid = Stato.evidenziazioni.filter((n) => n.libroId === Pdf.libroId);
  const segn = Stato.segnalibri.filter((n) => n.libroId === Pdf.libroId);
  p.innerHTML = `<div class="card">
    <h2>📝 Note, evidenziazioni e segnalibri di questo libro</h2>
    ${segn.length ? `<h3>🔖 Segnalibri</h3><ul class="lista">${segn.map((s) => `
      <li class="voce"><div class="corpo"><b>${esc(s.titolo || 'Pagina ' + s.pagina)}</b>
        <span class="meta">pagina ${s.pagina} · ${esc(quandoInParole(s.quando))}</span></div>
        <div class="azioni">${bottone('pdf-vai:' + s.pagina, '➡', 'Vai', 'btn-piccolo btn-primario')}
        ${bottone('pdf-cancSeg:' + s.id, '🗑️', '', 'btn-piccolo btn-errore')}</div></li>`).join('')}</ul>` : ''}
    ${note.length ? `<h3>📝 Note</h3><ul class="lista">${note.map((n) => `
      <li class="voce"><div class="corpo"><b>Pagina ${n.pagina}</b>
        ${n.testoSelezionato ? `<p style="margin:4px 0;border-left:4px solid var(--bordo);padding-left:8px"><i>${esc(accorcia(n.testoSelezionato, 140))}</i></p>` : ''}
        <p style="margin:4px 0">${esc(n.nota)}</p></div>
        <div class="azioni">${bottone('pdf-vai:' + n.pagina, '➡', 'Vai', 'btn-piccolo')}
        ${bottone('pdf-cancNota:' + n.id, '🗑️', '', 'btn-piccolo btn-errore')}</div></li>`).join('')}</ul>` : ''}
    ${evid.length ? `<h3>🖍️ Evidenziazioni</h3><ul class="lista">${evid.map((e) => {
      const cat = CATEGORIE_EVID.find((c) => c.id === e.categoria) || CATEGORIE_EVID[0];
      return `<li class="voce"><div class="corpo">
        <b><span class="pallino" style="background:${cat.colore}"></span>${esc(cat.tit)} · pagina ${e.pagina}</b>
        <p style="margin:4px 0">${esc(accorcia(e.testo, 160))}</p></div>
        <div class="azioni">${bottone('pdf-vai:' + e.pagina, '➡', 'Vai', 'btn-piccolo')}
        ${bottone('pdf-cancEvid:' + e.id, '🗑️', '', 'btn-piccolo btn-errore')}</div></li>`;
    }).join('')}</ul>` : ''}
    ${!note.length && !evid.length && !segn.length ? schedaVuota('📝', 'Non hai ancora messo niente su questo libro.') : ''}
    <div class="barra-btn" style="margin-top:12px">
      ${bottone('pdf-esportaNote', '📤', 'Esporta tutto in un file')}
      ${bottone('pdf-chiudiPannello', '✕', 'Chiudi')}
    </div>
  </div>`;
}

async function aggiungiSegnalibro() {
  const t = await chiediTesto('🔖 Segnalibro a pagina ' + Pdf.pagina, 'Come lo chiamo? (facoltativo)', '');
  if (t === null) return;
  const libro = Stato.libri.find((l) => l.id === Pdf.libroId);
  Stato.segnalibri.unshift({
    id: uid('sg'), libroId: Pdf.libroId, pagina: Pdf.pagina,
    titolo: t || ((libro ? libro.nome : '') + ' — pagina ' + Pdf.pagina), quando: oraISO()
  });
  await salvaOra();
  avvisoOk('Segnalibro messo');
}

async function cercaNelPdf() {
  if (!Pdf.doc) return;
  const q = await chiediTesto('🔎 Cerca nel documento', 'Che parola cerco?', '');
  if (!q) return;
  const p = $('#pdfPannello');
  p.innerHTML = `<div class="card"><p>⏳ Sto cercando "${esc(q)}" in ${Pdf.totale} pagine…</p></div>`;
  const trovate = [];
  const cerca = q.toLowerCase();
  for (let n = 1; n <= Pdf.totale; n++) {
    try {
      const pg = await Pdf.doc.getPage(n);
      const c = await pg.getTextContent();
      const t = c.items.map((i) => i.str).join(' ');
      const k = t.toLowerCase().indexOf(cerca);
      if (k >= 0) trovate.push({ pagina: n, estratto: t.slice(Math.max(0, k - 60), k + 90) });
      if (trovate.length >= 60) break;
    } catch (e) { /* pagina saltata */ }
  }
  p.innerHTML = `<div class="card">
    <h2>🔎 "${esc(q)}"</h2>
    ${trovate.length ? `<p class="aiutino">Trovata in ${trovate.length} pagine.</p>
      <ul class="lista">${trovate.map((t) => `<li class="voce">
        <div class="corpo"><b>Pagina ${t.pagina}</b><p style="margin:4px 0">…${esc(t.estratto)}…</p></div>
        <div class="azioni">${bottone('pdf-vai:' + t.pagina, '➡', 'Vai', 'btn-piccolo btn-primario')}</div></li>`).join('')}</ul>`
      : `<p>Non ho trovato questa parola. Se il libro è una scansione (immagini), il testo non è cercabile.</p>`}
    <div class="barra-btn">${bottone('pdf-chiudiPannello', '✕', 'Chiudi')}</div>
  </div>`;
}

function esportaNotePdf() {
  const libro = Stato.libri.find((l) => l.id === Pdf.libroId);
  const nome = libro ? libro.nome : 'libro';
  const righe = [];
  righe.push('APPUNTI SU: ' + nome, '');
  Stato.segnalibri.filter((s) => s.libroId === Pdf.libroId).forEach((s) => righe.push('[SEGNALIBRO] p.' + s.pagina + ' — ' + s.titolo));
  Stato.evidenziazioni.filter((e) => e.libroId === Pdf.libroId).forEach((e) => {
    const cat = CATEGORIE_EVID.find((c) => c.id === e.categoria) || CATEGORIE_EVID[0];
    righe.push('', '[' + cat.tit.toUpperCase() + '] p.' + e.pagina, e.testo);
  });
  Stato.notePdf.filter((n) => n.libroId === Pdf.libroId).forEach((n) => {
    righe.push('', '[NOTA] p.' + n.pagina, n.testoSelezionato ? '« ' + n.testoSelezionato + ' »' : '', '→ ' + n.nota);
  });
  scaricaFile(nome.replace(/\.pdf$/i, '') + '-appunti.txt', righe.join('\n'), 'text/plain;charset=utf-8');
  avvisoOk('File pronto');
}


/* ============================================================
   13-avvio.js
   ============================================================ */
/* ==================================================================
   13-avvio.js — collegamento dei pulsanti, scorciatoie, onboarding,
   dati di esempio e avvio dell'applicazione.
   ================================================================== */

/* ------------------------------------------------------------------
   Piccole funzioni di appoggio usate dai pulsanti
   ------------------------------------------------------------------ */

/**
 * Stampa la pagina in A4 (o la salva come PDF, dalla finestra del browser).
 * Prima di stampare: scrive l'intestazione del foglio e apre i pannelli
 * chiusi, altrimenti sulla carta resterebbero dei buchi.
 */
function stampa(titoloPagina) {
  const testa = $('#intestazioneStampa');
  if (testa) {
    const nome = (Stato.profilo.nome || '').trim();
    const classe = (Stato.profilo.classe || '').trim();
    const titolo = titoloPagina || (($('#vista h1') || {}).textContent || APP.nome).trim();
    testa.innerHTML =
      '<span><b>' + esc(titolo) + '</b>' +
      (nome ? ' — ' + esc(nome) : '') + (classe ? ' (' + esc(classe) + ')' : '') + '</span>' +
      '<span>' + esc(dataInParole(oggiISO())) + '</span>';
  }
  const chiusi = $$('details:not([open])');
  chiusi.forEach((d) => { d.open = true; });
  const ripristina = () => chiusi.forEach((d) => { d.open = false; });
  try { window.addEventListener('afterprint', ripristina, { once: true }); } catch (e) { /* niente */ }
  setTimeout(() => {
    try { window.print(); }
    catch (e) { avvisoErrore('Questo browser non riesce ad aprire la stampa. Prova con Ctrl+P (su Mac ⌘+P).'); }
  }, 60);
  setTimeout(ripristina, 5000);          // rete di sicurezza
}

async function eliminaSeConfermi(elenco, id, cosa, dopo) {
  const i = elenco.findIndex((x) => x.id === id);
  if (i < 0) return;
  const ok = await conferma('Elimino ' + cosa + '?', 'Non si può tornare indietro.', 'Sì, elimina', true);
  if (!ok) return;
  elenco.splice(i, 1);
  await salvaOra();
  if (dopo) dopo();
  avvisoOk('Eliminato');
}

function leggiTesto(t) {
  if (!t || !String(t).trim()) { toast('Non c\'è niente da leggere.'); return; }
  Voce.parla(String(t));
}

async function duplicaDocumento() {
  const d = documentoCorrente();
  if (!d) { toast('Prima scrivi qualcosa.'); return; }
  const copia = Object.assign({}, d, { id: uid('doc'), titolo: (d.titolo || 'Documento') + ' (copia)', creato: oraISO(), modificato: oraISO(), versioni: [] });
  Stato.documenti.unshift(copia);
  await salvaOra();
  apriDocumento(copia.id);
  disegna();
  avvisoOk('Documento duplicato');
}

async function eliminaDocumentoCorrente() {
  const d = documentoCorrente();
  if (!d) { _editor = { id: null, titolo: '', testo: '', undo: [''], redo: [] }; disegna(); return; }
  const ok = await conferma('Elimino questo documento?', '"' + (d.titolo || 'Senza titolo') + '" sparirà per sempre.', 'Sì, elimina', true);
  if (!ok) return;
  Stato.documenti = Stato.documenti.filter((x) => x.id !== d.id);
  _editor = { id: null, titolo: '', testo: '', undo: [''], redo: [] };
  await salvaOra();
  disegna();
  avvisoOk('Documento eliminato');
}

function annullaEditor() {
  const area = $('#docTesto');
  if (!area || _editor.undo.length < 2) { toast('Non c\'è niente da annullare.'); return; }
  _editor.redo.push(_editor.undo.pop());
  const t = _editor.undo[_editor.undo.length - 1] || '';
  area.value = t; _editor.testo = t;
  aggiornaContatori(); salvaDocumentoAuto();
}
function ripristinaEditor() {
  const area = $('#docTesto');
  if (!area || !_editor.redo.length) { toast('Non c\'è niente da ripristinare.'); return; }
  const t = _editor.redo.pop();
  _editor.undo.push(t);
  area.value = t; _editor.testo = t;
  aggiornaContatori(); salvaDocumentoAuto();
}

async function portaNegliAppunti(testo, fonte) {
  if (!testo || !testo.trim()) { toast('Non c\'è testo da salvare.'); return; }
  await modificaAppunto(null, { titolo: accorcia(testo, 40), testo, fonte: fonte || '' });
}

async function creaFlashcardDaTesto(testo) {
  const r = await finestra({
    titolo: '🃏 Nuova flashcard',
    testo: 'Scegli tu cosa chiedere e cosa rispondere: io non invento le domande.',
    campi: [
      { nome: 'fronte', etichetta: 'FRONTE — la domanda', tipo: 'area', righe: 3, valore: '' },
      { nome: 'retro', etichetta: 'RETRO — la risposta', tipo: 'area', righe: 3, valore: testo || '' },
      { nome: 'materia', etichetta: 'Materia', tipo: 'scelta', opzioni: materie(), valore: materie()[0] }
    ],
    testoOk: 'Crea la scheda'
  });
  if (!r || !r.fronte.trim()) return;
  Stato.flashcard.unshift(nuovaCard(r.fronte, r.retro, r.materia));
  await salvaOra();
  avvisoOk('Flashcard creata');
}

async function mandaAllaMappa(testo) {
  if (!Stato.mappe.length) { await nuovaMappa(); }
  const m = Stato.mappe.find((x) => x.id === Mappa.corrente) || Stato.mappe[0];
  if (!m) return;
  Mappa.corrente = m.id;
  m.nodi.push({
    id: uid('nd'), testo: accorcia(testo, 60),
    x: limita(30 + Math.random() * 40, 10, 90), y: limita(40 + Math.random() * 40, 15, 90)
  });
  await salvaOra();
  avvisoOk('Aggiunto alla mappa "' + m.titolo + '"');
}

/**
 * Dal libro al quaderno: il testo copiato dal PDF finisce in "Lo risolvo io",
 * dove il ragazzo prova per conto suo. La soluzione automatica è un passo
 * successivo, e la chiede lui.
 */
function portaInMatematica(testo) {
  _esercizioDaPdf = String(testo || '').replace(/\s+/g, ' ').trim();
  Risolvi.aiuto = 0;
  vaiA('mate', 'risolvoio');
  setTimeout(() => {
    const box = $('#riEsito');
    if (box) box.innerHTML = `<div class="avviso avviso-att"><span class="ic">👀</span>
      <p><b>Controlla che l'esercizio sia stato copiato bene prima di cominciare.</b>
      Quando si copia da un PDF capita che qualche simbolo si perda o cambi:
      puoi correggerlo direttamente nella prima riga.</p></div>`;
  }, 80);
}

/* ------------------------------------------------------------------
   Elenco delle azioni: nome → cosa fare
   ------------------------------------------------------------------ */

const AZIONI = {
  /* ---- navigazione ---- */
  home: () => vaiA('home'),
  impo: () => vaiA('impo'),
  cerca: () => vaiA('cerca'),
  mate: () => vaiA('mate'),
  vai: (v) => vaiA(v),
  righello: (x, b) => { const a = Righello.alterna(); if (b) b.setAttribute('aria-pressed', String(a)); },
  sillabe: (x, b) => {
    Stato.impostazioni.spaziaturaSillabe = !Stato.impostazioni.spaziaturaSillabe;
    salva();
    $$('.foglio-testo').forEach((n) => n.classList.toggle('sillabe', Stato.impostazioni.spaziaturaSillabe));
    if (b) b.setAttribute('aria-pressed', String(Stato.impostazioni.spaziaturaSillabe));
  },

  /* ---- impostazioni ---- */
  'salva-profilo': salvaProfilo,
  backup: creaBackup,
  ripristina: ripristinaBackup,
  azzera: azzeraTutto,
  'rivedi-onboarding': () => { Stato.impostazioni.onboardingFatto = false; salva(); mostraOnboarding(); },

  /* ---- lettura ---- */
  'leggi-incolla': letturaIncolla,
  'leggi-importa': letturaImporta,
  'leggi-salvati': letturaSalvati,
  'leggi-da-pdf': () => vaiA('pdf'),
  'leggi-indietro': disegnaAreaLettura,
  'leggi-salva': letturaSalva,
  'leggi-appunto': () => portaNegliAppunti(_lettura.testo, _lettura.titolo),
  'leggi-stampa': stampa,
  modo: (m) => { _lettura.modo = m; fermaParole(); fermaLettura(); disegnaAreaLettura(); },
  'frase-prec': () => { _lettura.frase = Math.max(0, _lettura.frase - 1); disegnaAreaLettura(); },
  'frase-succ': () => { _lettura.frase = Math.min(_lettura.frasi.length - 1, _lettura.frase + 1); disegnaAreaLettura(); },
  'frase-ascolta': () => leggiTesto(_lettura.frasi[_lettura.frase]),
  'parola-prec': () => { _lettura.parola = Math.max(0, _lettura.parola - 1); disegnaAreaLettura(); },
  'parola-succ': () => { _lettura.parola = Math.min(_lettura.parole.length - 1, _lettura.parola + 1); disegnaAreaLettura(); },
  'parola-play': avviaParole,
  'parola-stop': fermaParole,
  'parola-vel': (k) => { _velParole = Number(k); if (_lettura.timerParole) avviaParole(); disegnaAreaLettura(); },
  'testo-apri': (id) => { const t = Stato.testi.find((x) => x.id === id); if (t) { _lettura.testo = t.testo; _lettura.titolo = t.titolo; _lettura.id = t.id; _lettura.frase = 0; _lettura.parola = 0; disegnaAreaLettura(); } },
  'testo-elimina': (id) => eliminaSeConfermi(Stato.testi, id, 'questo testo', letturaSalvati),

  /* ---- scrittura ---- */
  'doc-nuovo': nuovoDocumento,
  'doc-elenco': elencoDocumenti,
  'doc-salva': async () => { await salvaDocumentoAuto(); await salvaOra(); avvisoOk('Salvato'); },
  'doc-duplica': duplicaDocumento,
  'doc-elimina': eliminaDocumentoCorrente,
  'doc-annulla': annullaEditor,
  'doc-ripristina': ripristinaEditor,
  'doc-trova': trovaParola,
  'doc-controlla': controllaTesto,
  'doc-doppie': evidenziaDoppie,
  'doc-modelli': () => mostraModelli(null),
  'doc-esporta': esportaDocumento,
  'doc-importa': importaDocumento,
  'doc-stampa': stampa,
  'doc-versioni': mostraVersioni,
  'doc-checklist': (x, b) => {
    Stato.impostazioni.checklistVisibile = !Stato.impostazioni.checklistVisibile;
    salva(); disegnaChecklist();
    if (b) b.setAttribute('aria-pressed', String(Stato.impostazioni.checklistVisibile));
  },
  'doc-apri': (id) => { apriDocumento(id); disegna(); },
  'doc-canc': (id) => eliminaSeConfermi(Stato.documenti, id, 'questo documento', elencoDocumenti),
  'pannello-chiudi': () => { const p = $('#pannelloScrivi'); if (p) p.innerHTML = ''; },
  'ver-ripristina': async (i) => {
    const d = documentoCorrente();
    if (!d || !d.versioni || !d.versioni[i]) return;
    const ok = await conferma('Torno a questa versione?', 'Il testo di adesso viene messo da parte come versione precedente.', 'Sì');
    if (!ok) return;
    d.versioni.unshift({ quando: oraISO(), testo: d.testo });
    d.testo = d.versioni[i + 1].testo;
    _editor.testo = d.testo;
    await salvaOra();
    disegna();
    avvisoOk('Versione ripristinata');
  },
  fix: (i) => applicaCorrezione(Number(i)),
  modello: (k) => mostraModelli(k),
  'modello-componi': (k) => componiModello(k),

  /* ---- calcolatrice ---- */
  'calc-modo': (x, b) => { Calc.scientifica = !Calc.scientifica; disegnaTastiCalc(); if (b) b.setAttribute('aria-pressed', String(Calc.scientifica)); },
  'calc-gradi': (x, b) => {
    Calc.gradi = !Calc.gradi;
    if (b) { b.setAttribute('aria-pressed', String(Calc.gradi)); b.querySelector('span:last-child').textContent = Calc.gradi ? 'GRADI' : 'RADIANTI'; }
    toast(Calc.gradi ? 'Modalità GRADI' : 'Modalità RADIANTI');
  },
  'calc-leggi': () => leggiTesto(M.parlaFormula(Calc.espressione) + (($('#calcRis') || {}).textContent ? ' fa ' + $('#calcRis').textContent : '')),
  'calc-storia': mostraStoriaCalc,
  'calc-chiudi-storia': () => { $('#calcStoria').innerHTML = ''; },
  'calc-riusa': (i) => { Calc.espressione = Calc.storia[i].esp; $('#calcEsp').textContent = Calc.espressione; $('#calcStoria').innerHTML = ''; },

  /* ---- colonna ---- */
  'col-incolonna': incolonna,
  'col-controlla': controllaColonna,
  'col-aiuto': aiutoColonna,
  'col-mostra': () => {
    const r = String(risultatoColonna());
    $('#colEsito').innerHTML = `<div class="avviso"><span class="ic">👀</span>
      <p>Il risultato è <b>${esc(r)}</b>. Adesso prova a rifare il conto da solo per capire perché.</p></div>`;
  },
  'col-pulisci': () => { $$('#colGriglia input').forEach((n) => { n.value = ''; }); $('#colEsito').innerHTML = ''; },

  /* ---- tabelline ---- */
  'tab-nascondi': (x, b) => { Tab.nascondi = !Tab.nascondi; disegnaPitagorica(); if (b) b.setAttribute('aria-pressed', String(Tab.nascondi)); },
  'tab-allena': nuovaSfidaTabelline,
  'tab-rispondi': rispondiTabellina,
  'tab-salta': nuovaSfidaTabelline,
  'tab-aiuto': aiutoTabellina,

  /* ---- frazioni, retta, percentuali ---- */
  'fr-disegna': disegnaFrazioni,
  'fr-leggi': () => {
    const t = 'Prima frazione: ' + M.parlato(M.nNum(M.fr(Number($('#frA1').value), Number($('#frA2').value)))) +
      '. Seconda frazione: ' + M.parlato(M.nNum(M.fr(Number($('#frB1').value), Number($('#frB2').value)))) + '.';
    leggiTesto(t);
  },
  'rt-mostra': () => disegnaRettaNumeri(true),
  'pc-calcola': () => calcolaPercentuale(false),
  'pc-passaggi': () => calcolaPercentuale(true),

  /* ---- problemi guidati ---- */
  'pb-leggi': () => { problemaLeggiCampi(); leggiTesto(Problema.testo); },
  'pb-avanti': (n) => { problemaLeggiCampi(); Problema.passo = Number(n); disegnaProblema(); },
  'pb-indietro': (n) => { problemaLeggiCampi(); Problema.passo = Number(n); disegnaProblema(); },
  'pb-op': (op) => problemaOperazione(op),
  'pb-calcola': problemaCalcola,
  'pb-salva': problemaSalva,
  'pb-nuovo': () => {
    Object.assign(Problema, { testo: '', so: '', trovare: '', op: '', calcolo: '', risposta: '', passo: 1 });
    disegnaProblema();
  },

  /* ---- risolutore ---- */
  'passi-modo': (x, b) => { _passi.unoAllaVolta = !_passi.unoAllaVolta; disegnaPassi(); },
  'passi-leggi': () => {
    if (!_passi.esito) return;
    const t = _passi.esito.passi.map((p) => p.tit + '. ' + (p.spiega || '')).join(' ');
    leggiTesto(t);
  },
  'passi-quaderno': () => {
    if (!_passi.esito) return;
    nuovoEsercizioQuaderno({
      titolo: accorcia(Risolvi.testo || 'Esercizio', 50),
      testo: Risolvi.testo,
      procedimento: _passi.esito.passi.map((p, i) => (i + 1) + '. ' + p.tit + ' — ' + (p.spiega || '')).join('\n'),
      risultato: _passi.esito.soluzioneTesto || ''
    });
  },
  perche: (i) => { const n = $('#perche-' + i); if (n) n.hidden = !n.hidden; },
  'passo-prec': () => { _passi.indice = Math.max(0, _passi.indice - 1); disegnaPassi(); },
  'passo-succ': () => { _passi.indice = Math.min(_passi.esito.passi.length - 1, _passi.indice + 1); disegnaPassi(); },
  'rs-tipo': (t) => {
    Risolvi.tipo = t;
    $$('[data-az^="rs-tipo:"]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.az === 'rs-tipo:' + t)));
    const s = $('#rsSeconda');
    if (s) s.hidden = (t !== 'sistema');
    const ex = $('#rsExtra');
    if (ex) ex.innerHTML = (t === 'limite')
      ? `<label class="etichetta" for="rsVerso">x tende a</label>
         <input class="campo" id="rsVerso" type="text" value="0" placeholder="un numero oppure inf">` : '';
  },
  'rs-conme': () => { Risolvi.aiuto = 0; risolviEsercizio('conme'); },
  'rs-aiuto': aiutoProgressivo,
  'rs-passaggi': () => risolviEsercizio('conme'),
  'rs-controlla': controllaMioRisultato,
  'rs-soluzione': () => risolviEsercizio('tutto'),

  /* ---- allenamento, quaderno, errori ---- */
  /* ---- lo risolvo io ---- */
  'ri-ricopia': ricopiaRiga,
  'ri-controlla': (i) => controllaRigaEq(Number(i)),
  'ri-leggi': (i) => { leggiRigheDalloSchermo(); leggiTesto(M.parlaFormula(RisolvoIo.righe[i])); },
  'ri-canc': (i) => {
    leggiRigheDalloSchermo();
    RisolvoIo.righe.splice(Number(i), 1);
    RisolvoIo.esitoRighe = {};
    salvaLavoroEquazione();
    disegnaRisolvoIo();
  },
  'ri-aiuto': aiutoRisolvoIo,
  'ri-finito': finitoRisolvoIo,
  'ri-programma': mostraSoluzioneProgramma,
  'ri-quaderno': salvaLavoroNelQuaderno,
  'ri-daPdf': () => vaiA('pdf'),
  'ri-nuovo': async () => {
    const ok = await conferma('Ricomincio da capo?', 'I passaggi che hai scritto verranno cancellati.', 'Sì, ricomincia');
    if (!ok) return;
    RisolvoIo.righe = [''];
    RisolvoIo.esitoRighe = {};
    Stato.lavoroEquazione = null;
    await salvaOra();
    disegnaRisolvoIo();
    const p = $('#riEsito'); if (p) p.innerHTML = '';
  },

  'al-nuovo': nuovoAllenamento,
  'al-controlla': controllaAllenamento,
  'al-aiuto1': () => aiutoAllenamento(1),
  'al-aiuto2': () => aiutoAllenamento(2),
  'al-formula': () => aiutoAllenamento(3),
  'qd-nuovo': () => nuovoEsercizioQuaderno(),
  'qf-aggiorna': disegnaFoglioQuaderno,
  'qf-stampa': () => stampa('Il mio quaderno di matematica'),
  'qd-apri': apriEsercizioQuaderno,
  'qd-canc': (id) => eliminaSeConfermi(Stato.quaderno, id, 'questo esercizio', disegnaQuaderno),
  'er-nuovo': nuovoErrore,
  'er-leggi': (id) => { const e = Stato.erroriMate.find((x) => x.id === id); if (e) leggiTesto(e.titolo + '. Sbagliato: ' + M.parlaFormula(e.sbagliato) + '. Corretto: ' + M.parlaFormula(e.corretto) + '. ' + e.regola); },
  'er-canc': (id) => eliminaSeConfermi(Stato.erroriMate, id, 'questo errore', disegnaErrori),

  /* ---- grafici ---- */
  'gf-aggiungi': () => aggiungiFunzione(),
  'gf-zoom+': () => Piano.zoom(0.7),
  'gf-zoom-': () => Piano.zoom(1.4),
  'gf-centra': () => { Piano.xmin = -10; Piano.xmax = 10; Piano.adatta(); Piano.disegna(); },
  'gf-zeri': trovaZeri,
  'gf-inter': trovaIntersezioni,
  'gf-togli': (i) => { Piano.funzioni.splice(Number(i), 1); disegnaElencoFunzioni(); Piano.disegna(); },
  'rp-studia': studiaRettaParabola,
  'rp-esempio-retta': () => { $('#rpTesto').value = 'y = 2x + 3'; studiaRettaParabola(); },
  'rp-esempio-par': () => { $('#rpTesto').value = 'y = x^2 - 4x + 3'; studiaRettaParabola(); },

  /* ---- trigonometria e triangoli ---- */
  'tr-vai': (a) => { Trigo.angolo = Number(a); const r = $('#trAng'); if (r) r.value = a; disegnaTrigo(); },
  'tr-leggi': () => {
    const a = Trigo.angolo * Math.PI / 180;
    leggiTesto('Angolo di ' + Trigo.angolo + ' gradi. Seno ' + Risolutore.numeroIt(Math.round(Math.sin(a) * 1000) / 1000) +
      '. Coseno ' + Risolutore.numeroIt(Math.round(Math.cos(a) * 1000) / 1000) + '.');
  },
  'tri-risolvi': risolviTriangolo,
  'tri-esempio': () => { $('#triA').value = 3; $('#triB').value = 4; $('#triC').value = 5; risolviTriangolo(); },
  'lg-calcola': calcolaLogaritmo,

  /* ---- analisi ---- */
  'an-derivata': () => { _passi.unoAllaVolta = false; mostraPassi(Risolutore.derivataPassi($('#anTesto').value), '#risultatoMate'); },
  'an-limite': () => { _passi.unoAllaVolta = false; mostraPassi(Risolutore.limitePassi($('#anTesto').value, $('#anVerso').value), '#risultatoMate'); },
  'an-integrale': () => { _passi.unoAllaVolta = false; mostraPassi(Risolutore.integralePassi($('#anTesto').value), '#risultatoMate'); },
  'an-grafico': () => {
    Piano.funzioni = []; Piano.punti = [];
    aggiungiFunzione($('#anTesto').value);
  },
  'sf-avvia': () => { Studio.risultati = {}; eseguiPassoStudio('dominio'); },
  'sf-passo': eseguiPassoStudio,
  'sta-calcola': () => calcolaStatistica(false),
  'sta-passaggi': () => calcolaStatistica(true),

  /* ---- formulario ---- */
  'fm-nuova': () => modificaFormula(null),
  'fm-modifica': modificaFormula,
  'fm-canc': (id) => eliminaSeConfermi(Stato.formule, id, 'questa formula', disegnaFormule),
  'fm-leggi': (id) => { const f = Stato.formule.find((x) => x.id === id); if (f) leggiTesto(f.nome + '. ' + M.parlaFormula(f.formula) + '. ' + (f.quando || '')); },
  'fm-stampa': stampa,

  /* ---- studia ---- */
  'st-spezza': spezzaAppunti,
  'st-elenco': trasformaInElenco,
  'st-daPdf': () => vaiA('pdf'),
  'st-eti': (i, tipo) => etichettaFrase(Number(i), tipo),
  'st-leggi': (i) => leggiTesto(Studia.frasi[i]),
  'st-flashcard': flashcardDaStudio,
  'st-appunto': () => portaNegliAppunti(($('#stTesto') || {}).value || Studia.testo, 'Da Studia'),
  'st-mappa': () => {
    const chiavi = Object.keys(Studia.etichette).filter((k) => Studia.etichette[k] === 'chiave' || Studia.etichette[k] === 'titolo');
    if (!chiavi.length) { toast('Segna prima qualche frase come "Titolo" o "Parola chiave".'); return; }
    chiavi.forEach((k) => mandaAllaMappa(Studia.frasi[k]));
  },
  'st-copiaElenco': () => portaNegliAppunti(dividiInFrasi(Studia.testo).map((f) => '• ' + f).join('\n'), 'Elenco'),
  'st-stampa': stampa,

  /* ---- flashcard ---- */
  'fc-nuova': () => modificaFlashcard(null),
  'fc-elenco': elencoFlashcard,
  'fc-torna': () => { preparaMazzo(); disegnaFlashcard(); },
  'fc-mischia': () => { Flash.ordine.sort(() => Math.random() - 0.5); Flash.indice = 0; Flash.giro = false; disegnaFlashcard(); },
  'fc-gira': () => { Flash.giro = !Flash.giro; disegnaFlashcard(); },
  'fc-leggi': () => { const c = Flash.ordine[Flash.indice]; if (c) leggiTesto(Flash.giro ? c.retro : c.fronte); },
  'fc-so': () => rispondiFlashcard('so'),
  'fc-quasi': () => rispondiFlashcard('quasi'),
  'fc-ripassa': () => rispondiFlashcard('ripassa'),
  'fc-prec': () => { Flash.indice = Math.max(0, Flash.indice - 1); Flash.giro = false; disegnaFlashcard(); },
  'fc-succ': () => { Flash.indice = Math.min(Flash.ordine.length - 1, Flash.indice + 1); Flash.giro = false; disegnaFlashcard(); },
  'fc-modifica': modificaFlashcard,
  'fc-canc': (id) => eliminaSeConfermi(Stato.flashcard, id, 'questa scheda', () => { preparaMazzo(); disegnaFlashcard(); }),

  /* ---- mappe ---- */
  'mp-nuova': nuovaMappa,
  'mp-nodo': () => aggiungiNodo(null),
  'mp-figlio': (id) => aggiungiNodo(id),
  'mp-modnodo': modificaNodo,
  'mp-immagine': immagineNodo,
  'mp-toglimg': togliImmagineNodo,
  'mp-collega': () => { toast('Tocca il primo nodo, poi il secondo.'); Mappa.collegando = Mappa.scelto; },
  'mp-daqui': (id) => { Mappa.collegando = id; toast('Adesso tocca l\'altro nodo da collegare.'); },
  'mp-elimina': async (id) => {
    const m = mappaCorrente();
    if (!m) return;
    m.nodi = m.nodi.filter((n) => n.id !== id);
    m.archi = m.archi.filter((a) => a.da !== id && a.a !== id);
    await salvaOra();
    $('#mpAzioniNodo').innerHTML = '';
    disegnaMappa();
  },
  'mp-grande': async (id) => { const n = mappaCorrente().nodi.find((x) => x.id === id); if (n) { n.dim = limita((n.dim || 1) + 0.15, 0.7, 2.2); await salvaOra(); disegnaMappa(); } },
  'mp-piccolo': async (id) => { const n = mappaCorrente().nodi.find((x) => x.id === id); if (n) { n.dim = limita((n.dim || 1) - 0.15, 0.7, 2.2); await salvaOra(); disegnaMappa(); } },
  'mp-zoom+': () => { Mappa.zoom = limita(Mappa.zoom + 0.12, 0.6, 2); disegnaMappa(); },
  'mp-zoom-': () => { Mappa.zoom = limita(Mappa.zoom - 0.12, 0.6, 2); disegnaMappa(); },
  'mp-esporta': esportaMappa,
  'mp-canc': () => eliminaSeConfermi(Stato.mappe, Mappa.corrente, 'questa mappa', () => { Mappa.corrente = null; vaiA('mappe'); }),

  /* ---- appunti ---- */
  'ap-nuovo': () => modificaAppunto(null),
  'ap-mod': modificaAppunto,
  'ap-canc': (id) => eliminaSeConfermi(Stato.appunti, id, 'questo appunto', disegnaAppunti),
  'ap-leggi': (id) => { const a = Stato.appunti.find((x) => x.id === id); if (a) leggiTesto((a.titolo || '') + '. ' + a.testo); },
  'ap-apri': (id) => {
    const a = Stato.appunti.find((x) => x.id === id);
    if (a) finestra({ titolo: a.titolo || 'Appunto', testo: a.testo, testoOk: 'Chiudi', testoAnnulla: 'Chiudi' });
  },
  'ap-flash': (id) => { const a = Stato.appunti.find((x) => x.id === id); if (a) creaFlashcardDaTesto(a.testo); },

  /* ---- compiti ---- */
  'cp-nuovo': () => modificaCompito(null),
  'cp-mod': modificaCompito,
  'cp-vista': (v) => { Compiti.vista = v; disegna(); },
  'cp-spezza': spezzaCompito,
  'cp-canc': (id) => eliminaSeConfermi(Stato.compiti, id, 'questo compito', disegnaCompiti),
  'cp-focus': (id) => {
    const t = Stato.compiti.find((x) => x.id === id);
    if (!t) return;
    Timer.attivita = t.descrizione;
    vaiA('focus');
    setTimeout(() => { const a = $('#tmAttivita'); if (a) a.value = t.descrizione; }, 60);
  },

  /* ---- timer ---- */
  'tm-preset': (s, p) => avviaTimer(Number(s), Number(p)),
  'tm-avvia': () => avviaTimer(Stato.impostazioni.timerStudio || 20, Stato.impostazioni.timerPausa || 5),
  'tm-personalizzato': async () => {
    const r = await finestra({
      titolo: 'Timer su misura',
      campi: [
        { nome: 'studio', etichetta: 'Minuti di studio', tipo: 'numero', valore: Timer.studio },
        { nome: 'pausa', etichetta: 'Minuti di pausa', tipo: 'numero', valore: Timer.pausa }
      ], testoOk: 'Comincia'
    });
    if (!r) return;
    avviaTimer(limita(Number(r.studio) || 20, 1, 120), limita(Number(r.pausa) || 5, 1, 60));
  },
  'tm-pausa': () => { Timer.inPausa = !Timer.inPausa; if (!Timer.inPausa && !Timer.riferimento) battitoTimer(); disegnaTimer(); },
  'tm-stop': fermaTimer,

  /* ---- parole ---- */
  'pr-tab': (t) => { Parole.tab = t; disegna(); },
  'pr-nuovaVoc': () => nuovaParola('vocabolario'),
  'pr-modVoc': (id) => nuovaParola('vocabolario', id),
  'pr-cancVoc': (id) => eliminaSeConfermi(Stato.vocabolario, id, 'questa parola', disegnaParole),
  'pr-leggiVoc': (id) => { const v = Stato.vocabolario.find((x) => x.id === id); if (v) leggiTesto(v.parola + '. ' + v.significato); },
  'pr-nuovaDiff': () => nuovaParola('difficili'),
  'pr-modDiff': (id) => nuovaParola('difficili', id),
  'pr-cancDiff': (id) => eliminaSeConfermi(Stato.paroleDifficili, id, 'questa parola', disegnaParole),
  'pr-leggiDiff': (id) => { const v = Stato.paroleDifficili.find((x) => x.id === id); if (v) leggiTesto(v.parola + '. ' + (v.sillabe || '') + '. ' + (v.esempio || '')); },
  'pr-nuovaEn': () => nuovaParola('inglese'),
  'pr-cancEn': (id) => eliminaSeConfermi(Stato.inglese, id, 'questo vocabolo', disegnaParole),
  'pr-leggiEn': (id) => {
    const v = Stato.inglese.find((x) => x.id === id);
    if (!v) return;
    if (!Voce.disponibile) { toast('Questo browser non legge ad alta voce.'); return; }
    Voce.parla([v.en], { lang: 'en-GB' });
  },
  'pr-flashEn': flashcardInglese,

  /* ---- PDF ---- */
  'pdf-apri': () => apriPdfDaFile(null),
  'pdf-libreria': () => { Pdf.doc = null; Pdf.libroId = null; disegnaBarraPdf(); disegnaLibreria(); },
  'pdf-riapri': (id) => vaiA('pdf', id),
  'pdf-riscegli': (id) => apriPdfDaFile(id),
  'pdf-prec': () => vaiAPagina(Pdf.pagina - 1),
  'pdf-succ': () => vaiAPagina(Pdf.pagina + 1),
  'pdf-vai': (n) => { const p = $('#pdfPannello'); if (p) p.innerHTML = ''; vaiAPagina(Number(n)); },
  'pdf-zoom+': () => { Pdf.adatta = 'no'; Pdf.zoom = limita(Pdf.zoom * 1.25, 0.25, 5); mostraPagina(); },
  'pdf-zoom-': () => { Pdf.adatta = 'no'; Pdf.zoom = limita(Pdf.zoom / 1.25, 0.25, 5); mostraPagina(); },
  'pdf-adatta': () => {
    Pdf.adatta = Pdf.adatta === 'larghezza' ? 'pagina' : 'larghezza';
    toast(Pdf.adatta === 'larghezza' ? 'Adattata alla larghezza' : 'Pagina intera');
    mostraPagina();
  },
  'pdf-dsa': (x, b) => {
    Pdf.modoDsa = !Pdf.modoDsa;
    if (b) b.setAttribute('aria-pressed', String(Pdf.modoDsa));
    if (Pdf.modoDsa) mostraModoDsa();
    else { const p = $('#pdfPannello'); if (p) p.innerHTML = ''; }
  },
  'pdf-dsaChiudi': () => { Pdf.modoDsa = false; disegnaBarraPdf(); const p = $('#pdfPannello'); if (p) p.innerHTML = ''; },
  'pdf-ascolta': () => {
    if (!Pdf.haTesto) { mostraAvvisoScansione(); toast('Questa pagina è un\'immagine: non c\'è testo da leggere.'); return; }
    leggiTesto(Pdf.testoPagina);
  },
  'pdf-frase': () => {
    if (!Pdf.haTesto) { mostraAvvisoScansione(); return; }
    _lettura.testo = Pdf.testoPagina;
    _lettura.titolo = 'Pagina ' + Pdf.pagina;
    _lettura.id = null; _lettura.modo = 'frase'; _lettura.frase = 0;
    vaiA('leggi');
  },
  'pdf-inLettura': () => {
    _lettura.testo = Pdf.testoPagina;
    _lettura.titolo = 'Pagina ' + Pdf.pagina;
    _lettura.id = null; _lettura.modo = 'normale';
    vaiA('leggi');
  },
  'pdf-cerca': cercaNelPdf,
  'pdf-segnalibro': aggiungiSegnalibro,
  'pdf-note': mostraNotePdf,
  'pdf-chiudiPannello': () => { const p = $('#pdfPannello'); if (p) { p.innerHTML = ''; p._scansione = false; } },
  'pdf-esportaNote': esportaNotePdf,
  'pdf-trascrivi': trascriviPagina,
  'pdf-appunto': () => {
    const libro = Stato.libri.find((l) => l.id === Pdf.libroId);
    portaNegliAppunti(Pdf.testoPagina, (libro ? libro.nome : 'PDF') + ' — pagina ' + Pdf.pagina);
  },
  'pdf-materia': async (id) => {
    const l = Stato.libri.find((x) => x.id === id);
    if (!l) return;
    const r = await finestra({ titolo: 'Materia del libro', campi: [{ nome: 'm', etichetta: 'Materia', tipo: 'scelta', opzioni: materie(), valore: l.materia || materie()[0] }], testoOk: 'Salva' });
    if (!r) return;
    l.materia = r.m;
    await salvaOra();
    disegnaLibreria();
  },
  'pdf-canc': async (id) => {
    const ok = await conferma('Tolgo questo libro?', 'Spariranno anche le note, le evidenziazioni e i segnalibri di questo libro.', 'Sì, togli', true);
    if (!ok) return;
    Stato.libri = Stato.libri.filter((l) => l.id !== id);
    Stato.notePdf = Stato.notePdf.filter((n) => n.libroId !== id);
    Stato.evidenziazioni = Stato.evidenziazioni.filter((n) => n.libroId !== id);
    Stato.segnalibri = Stato.segnalibri.filter((n) => n.libroId !== id);
    await Archivio.cancella('pdf:' + id);
    await salvaOra();
    disegnaLibreria();
    avvisoOk('Libro tolto dalla libreria');
  },
  'pdf-cancNota': (id) => eliminaSeConfermi(Stato.notePdf, id, 'questa nota', mostraNotePdf),
  'pdf-cancEvid': (id) => eliminaSeConfermi(Stato.evidenziazioni, id, 'questa evidenziazione', () => { mostraNotePdf(); disegnaEvidenziazioni(); }),
  'pdf-cancSeg': (id) => eliminaSeConfermi(Stato.segnalibri, id, 'questo segnalibro', mostraNotePdf),

  /* ---- menu della selezione ---- */
  'sel-leggi': () => { leggiTesto(selezioneCorrente()); chiudiMenuSelezione(); },
  'sel-evidenzia': evidenziaSelezione,
  'sel-nota': notaSuPdf,
  'sel-appunto': () => {
    const libro = Stato.libri.find((l) => l.id === Pdf.libroId);
    const t = selezioneCorrente();
    chiudiMenuSelezione();
    portaNegliAppunti(t, libro ? libro.nome + ' — pagina ' + Pdf.pagina : '');
  },
  'sel-flash': () => { const t = selezioneCorrente(); chiudiMenuSelezione(); creaFlashcardDaTesto(t); },
  'sel-mappa': () => { const t = selezioneCorrente(); chiudiMenuSelezione(); mandaAllaMappa(t); },
  'sel-mate': () => { const t = selezioneCorrente(); chiudiMenuSelezione(); portaInMatematica(t); },
  'sel-dsa': () => {
    const t = selezioneCorrente();
    chiudiMenuSelezione();
    _lettura.testo = t; _lettura.titolo = 'Dal PDF'; _lettura.id = null; _lettura.modo = 'frase'; _lettura.frase = 0;
    vaiA('leggi');
  },
  'sel-chiudi': chiudiMenuSelezione
};

/* ------------------------------------------------------------------
   Un solo ascoltatore per tutti i pulsanti
   ------------------------------------------------------------------ */

function collegaEventi() {
  document.addEventListener('click', (e) => {
    // navigazione rapida
    const vai = e.target.closest('[data-vai]');
    if (vai) {
      const p = vai.dataset.vai.split('/');
      vaiA(p[0], p[1]);
      return;
    }
    // azioni
    const b = e.target.closest('[data-az]');
    if (b) {
      const pezzi = b.dataset.az.split(':');
      const fn = AZIONI[pezzi[0]];
      if (fn) {
        e.preventDefault();
        try { fn.apply(null, pezzi.slice(1).concat([b])); }
        catch (err) { console.error(err); avvisoErrore('Questa funzione non è riuscita a partire. Riprova.'); }
        return;
      }
    }
    // tasti della calcolatrice
    const t = e.target.closest('[data-tasto]');
    if (t) { premiTastoCalc(t.dataset.tasto); return; }
    // simboli matematici
    const s = e.target.closest('[data-simbolo]');
    if (s) { inserisciSimbolo(s.dataset.campo, s.dataset.simbolo); return; }
    // tavola pitagorica
    const tb = e.target.closest('[data-tab]');
    if (tb) { const [i, j] = tb.dataset.tab.split(','); toccaTabellina(Number(i), Number(j)); return; }
  });

  document.addEventListener('change', (e) => {
    const f = e.target.closest('[data-fatto]');
    if (f) {
      const t = Stato.compiti.find((x) => x.id === f.dataset.fatto);
      if (t) { t.fatto = f.checked; salva(); disegnaCompiti(); if (t.fatto) toast('👏 Fatto!'); }
      return;
    }
    const p = e.target.closest('[data-passo]');
    if (p) {
      const [id, i] = p.dataset.passo.split(':');
      const t = Stato.compiti.find((x) => x.id === id);
      if (t && t.passi && t.passi[i]) { t.passi[i].fatto = p.checked; salva(); }
    }
  });

  // barra superiore
  $('#btnHome').addEventListener('click', () => vaiA('home'));
  $('#btnCerca').addEventListener('click', () => vaiA('cerca'));
  $('#btnImpostazioni').addEventListener('click', () => vaiA('impo'));
  $('#btnStampa').addEventListener('click', () => stampa());

  window.addEventListener('hashchange', disegna);

  // se l'app viene chiusa o messa via, provo comunque a salvare l'ultimo lavoro
  window.addEventListener('pagehide', () => { salvaOra(); });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') salvaOra();
  });

  // selezione del testo → menu (nel PDF e nel foglio di lettura)
  document.addEventListener('mouseup', gestisciSelezione);
  document.addEventListener('touchend', gestisciSelezione);

  // scorciatoie da tastiera (tutte le funzioni restano disponibili anche senza)
  document.addEventListener('keydown', (e) => {
    const ctrl = e.ctrlKey || e.metaKey;
    if (ctrl && e.key.toLowerCase() === 's') { e.preventDefault(); salvaOra(); avvisoOk('Salvato'); }
    else if (ctrl && e.key.toLowerCase() === 'f') {
      if (vistaCorrente === 'scrivi') { e.preventDefault(); trovaParola(); }
      else if (vistaCorrente === 'pdf' && Pdf.doc) { e.preventDefault(); cercaNelPdf(); }
      else { e.preventDefault(); vaiA('cerca'); }
    } else if (ctrl && e.key.toLowerCase() === 'z' && vistaCorrente === 'scrivi' && e.target.id !== 'docTesto') {
      e.preventDefault(); annullaEditor();
    } else if (e.key === 'Escape') {
      if ($('#menuSelezione')) chiudiMenuSelezione();
      else if (Righello.aperto) Righello.chiudi();
      else if (focusAttivo()) { cambiaFocus(false); disegna(); }
    } else if (vistaCorrente === 'pdf' && Pdf.doc && !e.target.closest('input, textarea')) {
      if (e.key === 'ArrowRight' || e.key === 'PageDown') { e.preventDefault(); vaiAPagina(Pdf.pagina + 1); }
      if (e.key === 'ArrowLeft' || e.key === 'PageUp') { e.preventDefault(); vaiAPagina(Pdf.pagina - 1); }
    } else if (vistaCorrente === 'flash' && !e.target.closest('input, textarea')) {
      if (e.key === ' ') { e.preventDefault(); Flash.giro = !Flash.giro; disegnaFlashcard(); }
      if (e.key === 'ArrowRight') { AZIONI['fc-succ'](); }
      if (e.key === 'ArrowLeft') { AZIONI['fc-prec'](); }
    }
  });
}

function gestisciSelezione(e) {
  if (e.target && e.target.closest('.menu-selezione')) return;
  setTimeout(() => {
    const t = testoSelezionato();
    const dentroPdf = e.target && (e.target.closest('#pdfLivelloTesto') || e.target.closest('.foglio-testo'));
    if (t && t.length > 2 && dentroPdf) {
      const p = (e.changedTouches && e.changedTouches[0]) || e;
      mostraMenuSelezione(t, p.clientX || window.innerWidth / 2, p.clientY || window.innerHeight / 2);
    } else if (!t) {
      chiudiMenuSelezione();
    }
  }, 30);
}

/* ------------------------------------------------------------------
   Onboarding (solo la prima volta)
   ------------------------------------------------------------------ */

let _passoOnboarding = 1;

function mostraOnboarding() {
  const box = $('#modale');
  const i = Stato.impostazioni;
  const schermate = {
    1: `<h2>👋 Benvenuto in Studio DSA</h2>
        <p style="font-size:1.15em">Questo è il tuo spazio per studiare.</p>
        <p>Funziona senza Internet e quello che scrivi resta sul tuo dispositivo.</p>`,
    2: `<h2>👓 Come preferisci leggere?</h2>
        <p>Prova a cambiare finché questa frase non ti sembra facile da leggere.</p>
        <div class="foglio" style="margin:12px 0"><div class="foglio-testo">
          Il gatto dorme sul divano vicino alla finestra.</div></div>
        <div class="slider-riga">
          <label for="obDim">Grandezza del testo</label><output id="obOutDim">${i.dimensione} px</output>
          <input type="range" id="obDim" min="14" max="42" step="1" value="${i.dimensione}">
        </div>
        <div class="slider-riga">
          <label for="obSpazio">Spazio fra le parole</label><output id="obOutSp">${i.parole}</output>
          <input type="range" id="obSpazio" min="0" max="0.8" step="0.04" value="${i.parole}">
        </div>
        <label class="etichetta" for="obSfondo">Sfondo</label>
        <select class="campo" id="obSfondo">
          <option value="bianco">Bianco</option><option value="crema">Crema</option>
          <option value="giallo">Giallo tenue</option><option value="grigio">Grigio chiaro</option>
          <option value="scuro">Scuro</option>
        </select>`,
    3: `<h2>🎉 Sei pronto</h2>
        <p>Puoi cambiare tutto quando vuoi da <b>⚙️ Impostazioni</b>.</p>
        <p>Se una cosa non ti serve, lasciala perdere: l'app si usa anche solo per una funzione alla volta.</p>`
  };
  box.innerHTML = `<div class="modale-box" role="dialog" aria-modal="true">
      ${schermate[_passoOnboarding]}
      <p class="frase-contatore">${_passoOnboarding} di 3</p>
      <div class="modale-azioni">
        ${_passoOnboarding > 1 ? '<button type="button" class="btn" data-ob="indietro">⬅ Indietro</button>' : ''}
        <button type="button" class="btn btn-primario btn-grande" data-ob="avanti">
          ${_passoOnboarding === 3 ? 'INIZIA' : 'Avanti ➡'}</button>
      </div>
    </div>`;
  box.hidden = false;

  if (_passoOnboarding === 2) {
    const d = $('#obDim', box), s = $('#obSpazio', box), sf = $('#obSfondo', box);
    sf.value = i.sfondoLettura;
    d.addEventListener('input', () => { i.dimensione = Number(d.value); $('#obOutDim', box).textContent = d.value + ' px'; applicaAspetto(); });
    s.addEventListener('input', () => { i.parole = Number(s.value); $('#obOutSp', box).textContent = s.value; applicaAspetto(); });
    sf.addEventListener('change', () => { i.sfondoLettura = sf.value; applicaAspetto(); });
  }

  box.onclick = async (e) => {
    const b = e.target.closest('[data-ob]');
    if (!b) return;
    if (b.dataset.ob === 'indietro') { _passoOnboarding--; mostraOnboarding(); return; }
    if (_passoOnboarding < 3) { _passoOnboarding++; mostraOnboarding(); return; }
    Stato.impostazioni.onboardingFatto = true;
    await salvaOra();
    box.hidden = true;
    box.innerHTML = '';
    box.onclick = null;
    _passoOnboarding = 1;
    vaiA('home');
  };
}

/* ------------------------------------------------------------------
   Pochi dati di esempio, chiaramente cancellabili
   ------------------------------------------------------------------ */

function metteDatiDiEsempio() {
  Stato.flashcard.push({
    id: uid('fc'), fronte: 'In quale anno è iniziata la Prima guerra mondiale?', retro: '1914.',
    materia: 'Storia', stato: 'nuova', creata: oraISO(), viste: 0, esempio: true
  });
  Stato.quaderno.push({
    id: uid('es'), data: oggiISO(), materia: 'Matematica', argomento: 'Problema',
    titolo: 'Esempio: le figurine di Marco',
    testo: 'Marco ha 24 figurine e ne regala 7. Quante figurine gli rimangono?',
    procedimento: 'So: ha 24 figurine, ne regala 7.\nDevo trovare: quante gliene restano.\nOperazione: sottrazione\nCalcolo: 24 - 7',
    risultato: 'A Marco rimangono 17 figurine.', nota: 'Questo è un esempio: puoi cancellarlo.', esempio: true
  });
  Stato.appunti.push({
    id: uid('ap'), titolo: 'Come funziona questa app', materia: 'Altra', data: oggiISO(),
    testo: 'Questo è un appunto di esempio: puoi cancellarlo con il cestino.\n\n' +
      'Ogni cosa che scrivi resta su questo dispositivo. Ogni tanto fai un backup dalle Impostazioni: ' +
      'è un file che puoi rimettere dentro se cambi computer.',
    chiavi: 'esempio, backup', colore: COLORI_APPUNTO[0], esempio: true
  });
}

/* ------------------------------------------------------------------
   Service worker (solo quando serve e solo se possibile)
   ------------------------------------------------------------------ */

function registraServiceWorker() {
  if (!('serviceWorker' in navigator)) return;
  const p = location.protocol;
  if (p !== 'https:' && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') return;
  navigator.serviceWorker.register('service-worker.js').catch(() => { /* l'app funziona lo stesso */ });
}

/* ------------------------------------------------------------------
   Avvio
   ------------------------------------------------------------------ */

async function avvia() {
  await Archivio.apri();
  const letti = await Archivio.leggi('dati');
  if (letti) applicaDati(letti);
  else metteDatiDiEsempio();

  if (Stato.impostazioni.timerStudio) Timer.studio = Stato.impostazioni.timerStudio;
  if (Stato.impostazioni.timerPausa) Timer.pausa = Stato.impostazioni.timerPausa;

  applicaAspetto();
  collegaEventi();
  aggiornaTab();

  if (!Stato.impostazioni.onboardingFatto) mostraOnboarding();
  if (!location.hash) location.hash = '#/home';
  disegna();

  registraServiceWorker();

  // le voci del dispositivo arrivano con un attimo di ritardo
  if (Voce.disponibile) setTimeout(() => Voce.aggiornaVoci(), 800);
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', avvia);
else avvia();
