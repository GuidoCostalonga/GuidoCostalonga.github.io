# Atlante dei dati

Sito di esplorazione di tutti i valori contenuti nel repository. La struttura segue tre passi: **tema** (dieci aree), **indicatore** (scheda con andamento, mappa, classifica e tabella) e **territorio** (profilo di un paese o di una regione). La pagina `scheda-fvg.html` è la scheda "Italia e Friuli Venezia Giulia a confronto con l'Europa".

## Contenuto

| Famiglia | Fonte | Lotti | Copertura nel repository |
|---|---|---|---|
| Indicatori nazionali | Banca Mondiale, WDI (Indicatori di sviluppo mondiale): 150 indicatori | 1 e 2 | completa: 217 paesi e 49 aggregati, dal 1960 |
| Regioni europee | Eurostat: 5 dataset demografici dei lotti 1 e 2 (21 serie più 3 quote per età calcolate) e 105 dataset regionali scaricati dall’API di Eurostat (269 indicatori su popolazione e proiezioni, economia, lavoro, istruzione, ricerca, salute, condizioni di vita, digitale, turismo, trasporti, agricoltura, ambiente, imprese), file originali in `open data/Eurostat regionale/` | 1 e 2; API | completa per i territori pubblicati: livelli NUTS da 0 a 3, dal 1990 (o dall’inizio della serie); le proiezioni arrivano al 2100 |
| Indici regionali e fondi europei | Commissione europea, DG REGIO (piattaforma cohesiondata, trovata su data.europa.eu): 9 dataset, 83 indicatori (indice di competitività RCI 2.0, indice di progresso sociale EU-SPI 2020 e 2024, indice di qualità delle istituzioni EQI, monitor regionale di genere 2021, gas serra regionali e intensità carbonica stimati dal JRC, pagamenti dei fondi europei per regione, energia rinnovabile da sole e vento per provincia), file originali in `open data/Commissione europea cohesiondata/` | nessuno (fuori dai lotti) | regioni NUTS 2 dell’Unione europea (province NUTS 3 per le rinnovabili); indici con pochi anni, gas serra dal 1990, fondi dal 1989 |
| Archivi parziali | FAOSTAT (22 insiemi di dati) e OCSE (3 flussi) | 5, 9, 11, 15, 18, 19, 22, 23, 24, 26, 27, 28 | parziale: di ogni lotto il repository contiene uno o pochi archivi, quindi solo alcuni paesi o aggregati; nel sito gli archivi sono raggruppati per argomento e i numeri di lotto non compaiono |

I lotti 3, 4, 6, 7, 8, 10, 12, 13, 14, 16, 17, 20 e 21 sono presenti solo con indici, registri, dizionari o guide, senza valori; il lotto 25 contiene indagini della fonte senza serie annuali. L'Atlante li elenca nella pagina "Fonti e metodo" ma non può mostrarli.

Mappe: Natural Earth 1:50 milioni (paesi, continenti, regioni del mondo) e GISCO NUTS 2024 1:10 milioni (regioni europee, © EuroGeographics per i confini amministrativi).

## Pagine

| Indirizzo | Pagina |
|---|---|
| `#/` | prima pagina: ricerca, temi, indicatori in evidenza, confronti pronti, schede |
| `#/tema/<tema>` | un tema con i suoi indicatori raggruppati per sottogruppo, valore più recente per l'Italia o il Friuli Venezia Giulia (il tema Regioni europee ha 21 sottogruppi) |
| `#/i/<codice>` | esplorazione di un indicatore: viste Andamento, Mappa, Classifica, Tabella; parametri `v` (vista), `t` (territori), `y` (anno), `area`, `liv`, `pae`, `log`, `ult`, `n`, `o`, `z` (periodo ingrandito) |
| `#/t/<territorio>` | profilo di un paese, di un aggregato o di una regione europea |
| `#/confronta?t=<tema>&g=<territori>` | tabella di confronto: righe = indicatori, colonne = territori |
| `#/archivi` e `#/archivi/<file>` | archivi FAOSTAT e OCSE: elenco per argomento, ricerca fra le serie |
| `#/fonti` | fonti, metodo e copertura: Banca Mondiale, Eurostat, Commissione europea, FAOSTAT, OCSE |

## Funzioni

- **Ricerca unica** nella barra in alto (tasto `/`): temi, indicatori (anche con il nome inglese o il codice), paesi, regioni europee, archivi, pagine.
- **Menù a tendina** Temi e Schede; su telefono un unico menù.
- **Andamento**: rotellina del mouse, pizzico sullo schermo, trascinamento sul grafico, pulsanti + e − sul grafico o navigatore sotto il grafico ingrandiscono un periodo; doppio clic o pulsante ripristinano l'intero periodo. Gli altri territori restano in grigio sullo sfondo.
- **Mappa**: mondo, continenti o regioni e province europee (livello NUTS e paese a scelta), con ingrandimento fino a 28 volte a pulsanti, rotellina, pizzico e doppio clic, trascinamento per spostare; scala logaritmica e "ultimo dato disponibile" quando servono.
- **Classifica**: prime 10, 25, 50 o tutte le voci, ordine crescente o decrescente, selezionati evidenziati anche fuori classifica.
- **Tabella**: valore dell'anno, dato di dieci anni prima, variazione, posizione; ordinabile; selezione con caselle.
- **Linea del tempo** con riproduzione a velocità scelta, anno in grande nella scena.
- **Confronti pronti**: Friuli Venezia Giulia e vicini, province del Friuli Venezia Giulia, regioni italiane, Italia e grandi paesi europei, Italia e vicini, grandi economie del mondo, regioni del mondo.
- **Scarica CSV** e **copia il collegamento** della vista corrente.
- **Profili** con indicatori in evidenza, posizione in classifica e andamento degli ultimi trent'anni (file precalcolati in `dati/profili/`).
- **Scheda Friuli Venezia Giulia**: `scheda-fvg.html`, testi e grafici calcolati dai file di dati al caricamento della pagina; anche qui grafici a linee e mappa si ingrandiscono con rotellina, pizzico e pulsanti.

## Aprire il sito

```
cd atlante
python3 -m http.server 8000
```

poi aprire `http://localhost:8000`.

## Struttura

| Percorso | Contenuto |
|---|---|
| `index.html`, `stile.css` | pagina e stile |
| `nucleo.js` | temi, sottogruppi, stato, formati, caricamento dati, ricerca, componenti, instradamento |
| `grafici.js` | viste Andamento, Mappa, Classifica, Tabella e scarico CSV |
| `pagine.js` | prima pagina, tema, esplorazione, profilo, confronto, archivi, fonti, avvio |
| `scheda-fvg.html`, `scheda.js` | scheda "Italia e Friuli Venezia Giulia a confronto con l'Europa" |
| `dati/meta.json` | indicatori, territori (paesi, aggregati, regioni NUTS, codici storici e aggregati FAOSTAT), regioni del mondo, catalogo degli archivi |
| `dati/panoramica.json` | per ogni indicatore: valore più recente, posizione e andamento per Italia, Friuli Venezia Giulia, Unione europea e mondo (prima pagina e pagine dei temi) |
| `dati/profili/<territorio>.json` | indicatori in evidenza di ciascun territorio (pagine dei profili) |
| `dati/indicatori/<codice>.json` | Banca Mondiale: valori annuali per territorio; `null` = dato mancante |
| `dati/eurostat/<codice>.json` | Eurostat e Commissione europea (DG REGIO): valori annuali per territorio NUTS (chiavi `EU:<codice NUTS>`) |
| `dati/archivi/<lotto>_<insieme>.json` | archivi parziali: tutte le serie di un insieme di dati |
| `dati/note/<codice>.json` | note e stati originali della fonte per osservazione |
| `dati/mondo.topo.json`, `dati/nuts.topo.json` | confini semplificati |
| `strumenti/` | script di estrazione e generazione |

## Regole sui valori

- Nessun valore stimato, interpolato o ricostruito; i dati mancanti restano tali e sono contati in ogni vista.
- Due sole elaborazioni, entrambe indicate come calcolate: le quote di popolazione per età Eurostat (classe d'età divisa per totale, stesso territorio e anno, per 100) e i due totali dei pagamenti dei fondi europei (somma dei sette fondi per regione e anno; i pagamenti dello stesso fondo e anno divisi su due periodi di programmazione sono sommati anche nelle serie per fondo).
- Gli indicatori che la fonte stessa dichiara stimati, modellati o proiettati (gas serra regionali e potenziale rinnovabile del JRC, spesa modellata dei fondi, erosione del suolo, proiezioni di popolazione) portano il campo `stima`, mostrato in evidenza nella scheda.
- Negli archivi FAOSTAT l'identità SHA256 della fonte comprende il territorio: le serie sono raggruppate per descrizione e unità per confrontare i paesi.
- Decimali arrotondati a 6 cifre significative per la visualizzazione; conteggi interi esatti.

## Rigenerare i dati

```
cd atlante/strumenti
# 1. decomprimere gli ZIP dei lotti 1 e 2 in una cartella (PAESI/ e AGGREGATI/)
python3 estrai_eurostat.py <cartella lotti 1-2> /tmp/eurostat.json
# 2. tabella dei codici area FAOSTAT -> ISO3 (dai dizionari dei lotti 20 e 21 e dai confini Natural Earth)
python3 mappa_aree_faostat.py "<Lotto 21  dizionari originali FAOSTAT.xlsx>" <ne50.geojson> aree_faostat.json
# 3. decomprimere gli ZIP degli archivi parziali in <cartella>/L5, L9, L11, ... L28
python3 estrai_archivi_parziali.py <cartella> /tmp/parziali aree_faostat.json   # scrive /tmp/parziali_L<n>.json
# 4. indicatori regionali dall'API di Eurostat: i file TSV compressi stanno in "open data/Eurostat regionale/"
#    (--scarica li scarica di nuovo se mancano, insieme a liste di codici e indice)
python3 estrai_eurostat_api.py "../../open data/Eurostat regionale" /tmp/eurostat_api.json
# 5. indici e serie della Commissione europea (DG REGIO): i CSV e le schede stanno in "open data/Commissione europea cohesiondata/"
python3 estrai_regio.py "../../open data/Commissione europea cohesiondata" /tmp/regio.json
# 6. generare i file del sito
python3 genera_dati.py --cache /tmp/grezzi.json --eurostat /tmp/eurostat.json --eurostat-api /tmp/eurostat_api.json --regio /tmp/regio.json --parziali /tmp/parziali_L*.json
```

Confini NUTS: scaricare da GISCO `NUTS_RG_10M_2024_4326_LEVL_{0,1,2,3}.json`, ridurre le proprietà a `id`, `n`, `l`, `c` (codice ISO3 del paese) e unire con `mapshaper -simplify 35% keep-shapes -o format=topojson quantization=1e5`.
