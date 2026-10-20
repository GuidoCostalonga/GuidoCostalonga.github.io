# Trame: le strade come disegno

Pagina servita su [costalonga.org/trame](https://costalonga.org/trame/). Si sceglie un
luogo, si scarica la sua rete stradale da OpenStreetMap e la si trasforma in una mappa
da personalizzare, analizzare ed esportare. Nella modalità Percorsi si calcola e si
mette in evidenza un itinerario.

## Tecnologia

Pagina statica, come il resto del sito: niente compilazione, niente dipendenze da
installare. I file si servono così come sono da GitHub Pages.

| Dipendenza | Versione | Perché |
|---|---|---|
| MapLibre GL JS (da unpkg) | 4.7.1 | disegno della mappa con WebGL, rotazione, livelli vettoriali veloci anche con decine di migliaia di strade |

Tutto il resto (grafo per i percorsi locali, statistiche, grafici, esportazione PNG e
SVG, GPX) è scritto in JavaScript senza librerie. Se WebGL o MapLibre non sono
disponibili, la pagina passa da sola a una vista semplificata su Canvas 2D con le
stesse funzioni, tranne la mappa di base e la rotazione.

Per provarla in locale serve un qualsiasi server statico, per esempio
`npx http-server .` dalla radice del repository, poi `http://localhost:8080/trame/`.
Aprire il file direttamente dal disco non funziona, perché i moduli JavaScript
richiedono un server.

## Struttura

| File | Contenuto |
|---|---|
| `index.html` | struttura della pagina, schede, comandi |
| `app.css` | stile, tema chiaro e scuro, smartphone, stampa |
| `js/config.js` | indirizzi dei servizi, soglie di superficie, mappe di base |
| `js/providers/geocoding.js` | ricerca dei luoghi: Nominatim (predefinito) o Photon |
| `js/providers/overpass.js` | dati OpenStreetMap via Overpass, con server di riserva |
| `js/providers/routing.js` | adattatori di percorso: Valhalla, OSRM, GraphHopper, motore locale |
| `js/providers/elevation.js` | quote del terreno (Valhalla `/height`) |
| `js/graph.js` | grafo stradale e algoritmo di Dijkstra per il motore locale |
| `js/mapview.js` | vista MapLibre |
| `js/canvasview.js` | vista alternativa senza WebGL |
| `js/scene.js` | scena indipendente dalla libreria, usata per esportazioni e vista alternativa |
| `js/exporter.js` | PNG e SVG ad alta risoluzione, cornice da manifesto |
| `js/analysis.js` | statistiche, rosa degli orientamenti, densità, percorsi generati |
| `js/gpx.js` | lettura e scrittura di GPX e GeoJSON |
| `js/app.js` | stato, interfaccia, cronologia annulla e ripristina, collegamenti |

## Fornitori sostituibili

Tutti gli indirizzi stanno in `js/config.js`. Nessuna chiave segreta è nel codice.

| Funzione | Predefinito | Alternative già pronte | Condizioni d'uso |
|---|---|---|---|
| Ricerca | Nominatim (OpenStreetMap) | Photon (komoot) | massimo una richiesta al secondo, niente completamento automatico: la ricerca parte solo con Invio |
| Dati della mappa | Overpass API (overpass-api.de) | private.coffee, mail.ru, kumi.systems, provati in ordine | una richiesta alla volta; un server che non risponde o è occupato viene lasciato subito per il successivo; tempo massimo dichiarato in base alla superficie (45 s fino a 30 km², 90 s fino a 250 km², poi 180 s); dati conservati nel browser per 24 ore |
| Percorsi | Valhalla, server FOSSGIS | OSRM FOSSGIS, GraphHopper con chiave dell'utente, motore locale, server compatibile indicato dall'utente | uso moderato, attribuzione visibile |
| Quote | Valhalla `/height`, server FOSSGIS | | campione limitato e dichiarato |
| Mappa di base | CARTO chiara e scura | OpenStreetMap standard | attribuzione sempre visibile |
| Caratteri delle etichette | OpenFreeMap | | |

Capacità dei motori di percorso, come le dichiara l'interfaccia:

| Motore | Mezzi | Alternative | Istruzioni | Breve o rapido | Esclusioni |
|---|---|---|---|---|---|
| Valhalla FOSSGIS | auto, bici, a piedi | sì, con due soli punti | sì, in italiano | sì | autostrade, pedaggi, traghetti, sterrati (in auto); traghetti e sterrati (in bici); come forte preferenza, non come divieto assoluto |
| OSRM FOSSGIS | auto, bici, a piedi | sì, con due soli punti | sì, tradotte dalla pagina | no | no: il server pubblico rifiuta il parametro `exclude` |
| GraphHopper | auto, bici, a piedi | sì | sì, in italiano, con dislivello | no | tramite modello personalizzato, se il piano lo consente |
| Motore locale | auto, bici, a piedi | no | no | sì | autostrade, pedaggi, sterrati; durata stimata, nessun divieto di svolta |

Un tratto disegnato a mano resta sempre etichettato come «Tracciato a mano»: non
viene mai presentato come itinerario calcolato e non ha durata.

## Limiti di superficie

| Livello | Avviso oltre | Bloccato oltre |
|---|---|---|
| Rete stradale | 120 km² | 1.500 km² |
| Edifici | 30 km² | 150 km² |
| Punti di interesse | 60 km² | 400 km² |
| Linee di trasporto pubblico | 150 km² | 800 km² |
| Parchi e aree verdi, acque | 150 e 300 km² | 1.500 e 3.000 km² |
| Ferrovie, confini comunali | 800 km² | 5.000 km² |

Oltre la soglia di avviso compare una richiesta di conferma con la superficie; il
caricamento mostra sempre i dati ricevuti, il tempo trascorso, il server in uso e il
pulsante Annulla.

## Privacy

Nessun account, nessuna telemetria. Impostazioni, stili personali, luoghi recenti e
l'eventuale chiave GraphHopper restano nel `localStorage` del browser. La posizione
del dispositivo si chiede solo dopo un clic su «Usa la mia posizione», resta in
memoria nella pagina aperta e non entra mai nei collegamenti condivisi, che
contengono solo area, livelli, filtri e stile.

## Verifiche fatte prima della pubblicazione

Prove automatiche in Chromium (Playwright), su schermo da computer (1400 × 900) e da
smartphone (390 × 780, tocco), con e senza WebGL:

* ricerca «Roveredo in Piano» su Nominatim reale, scelta del risultato, caricamento
  della rete (515 strade, 94,8 km), filtro per tipo di strada e attributi;
* stili pronti, colori per orientamento, livello edifici, annulla e ripristina;
* percorso in auto con Valhalla reale: 7,3 km, alternativa, profilo altimetrico,
  16 istruzioni con tappa intermedia; OSRM reale; motore locale; messaggio corretto
  per GraphHopper senza chiave;
* tracciato a mano, importazione di un GPX, stampa del manifesto, riapertura da
  collegamento condiviso, posizione del dispositivo;
* file esportati validi: PNG 2480 × 3508, SVG e GPX validati con `xmllint`, GeoJSON
  letto correttamente;
* nessun errore nella console, nessuno scorrimento orizzontale su smartphone.

Nell'ambiente di prova i server Overpass non erano raggiungibili: le risposte di
Overpass sono state sostituite con un estratto reale di Roveredo in Piano scaricato
dall'API ufficiale di OpenStreetMap e convertito nello stesso formato. Nominatim,
Valhalla, OSRM, le quote, le tessere e MapLibre sono stati usati dal vivo.
