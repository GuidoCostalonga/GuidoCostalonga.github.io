# Monitor della percezione pubblica FVG

Cruscotto in tempo reale che raccoglie menzioni da testate giornalistiche e
social, ne analizza il tono con Claude (sarcasmo, emozioni, varietà
linguistiche del territorio, entità citate) e avvisa lo staff in caso di
**Allerta crisi** o **Opportunità di consenso**.

Il cruscotto è pubblicato sull'Atlante FVG, nella sezione riservata `https://atlantefvg.it/monitor/`
(solo dati reali: finché il servizio non è collegato la pagina resta vuota e lo segnala). Il vecchio indirizzo
`https://costalonga.org/sentiment-fvg/` rimanda lì, conservando l'eventuale `?nome=`.

## Contenuto della cartella

| Percorso | A cosa serve |
|---|---|
| `app.js` | il programma del cruscotto, usato dalla sezione `monitor/` dell'Atlante FVG (grafica e cifratura sono nel repository dell'Atlante) |
| `index.html` | rimando a `https://atlantefvg.it/monitor/` |
| `servizio/app.py` | servizio FastAPI: accesso, istantanea, eventi in diretta (SSE), ingresso firmato |
| `servizio/analisi.py` | analisi con Claude: istruzioni, schema JSON della risposta, lotti |
| `servizio/allerte.py` | regole di Allerta crisi, Ondata coordinata e Opportunità di consenso |
| `servizio/connettori/` | RSS, Bluesky, X, Facebook (Meta Graph API), Telegram |
| `servizio/notifiche.py` | avvisi allo staff su Telegram e su un webhook a scelta |
| `servizio/archivio.py` | archivio SQLite con cancellazione automatica dopo 30 giorni |
| `servizio/fonti.yaml` | feed, ricerche, canali e parole da monitorare |
| `servizio/.env.esempio` | tutte le impostazioni e le chiavi (da copiare in `.env`) |
| `SICUREZZA.md` | chiavi private, proxy, CSP, protezione dei dati, condizioni delle piattaforme |

## Come funziona

```
 Testate RSS ─┐
 Bluesky ─────┤                ┌─────────────────────┐
 X ───────────┼─► coda ─► lotti di 10 ─► Claude (schema JSON) ─► archivio SQLite
 Facebook ────┤                └─────────────────────┘              │
 Telegram ────┤                                                     ▼
 Ingresso ────┘                                  motore delle allerte (15 min contro 6 ore)
 firmato                                                 │                 │
                                                         ▼                 ▼
                                            Telegram / webhook staff   eventi SSE ─► cruscotto
```

1. **Raccolta**: ogni connettore si attiva solo se nel `.env` ci sono le sue
   credenziali. Le menzioni già viste vengono scartate.
2. **Analisi**: Claude riceve fino a 10 menzioni per volta e restituisce, per
   ciascuna, tono, punteggio da −1 a +1, sicurezza del giudizio, sarcasmo,
   intensità di rabbia, paura, entusiasmo, fiducia e tristezza, varietà
   linguistica (friulano, triestino, bisiacco, veneto, sloveno, tedesco), entità,
   temi, ostilità e una motivazione leggibile. La risposta è vincolata a uno
   schema JSON, quindi non può arrivare in un formato sbagliato.
3. **Allerte**: la finestra recente si confronta con le 6 ore precedenti, sul
   totale, per tema e per persona, partito o riforma citati.
4. **Diffusione**: ogni menzione analizzata e ogni allerta arrivano al cruscotto
   in diretta, senza ricaricare la pagina.

## Regole delle allerte

| Allerta | Scatta quando (valori modificabili nel `.env`) |
|---|---|
| Allerta crisi, calo improvviso | almeno 12 menzioni in 15 minuti, indice sceso di 30 punti rispetto alle 6 ore precedenti e almeno metà delle menzioni negative |
| Allerta crisi, indignazione | almeno 12 menzioni in 15 minuti, oltre il 60% negative e rabbia media pari o superiore a 0,5 |
| Allerta crisi, ondata coordinata | lo stesso testo pubblicato da almeno 5 account diversi e pari ad almeno un quarto della finestra, oppure ostilità media pari o superiore a 0,6 su almeno 24 menzioni |
| Opportunità di consenso | tema con volume almeno doppio rispetto alla media, indice di almeno +40 ed entusiasmo medio pari o superiore a 0,4 |

La stessa allerta non si ripete per 45 minuti. Le soglie vanno tarate dopo le
prime settimane di uso reale: con poche menzioni conviene abbassare
`VOLUME_MINIMO`, con molte alzarlo.

## Politico o partito da monitorare

In cima al cruscotto c'è la casella **«Politico o partito da monitorare»**.

- Si scrive un nome e si preme **Monitora**: indicatori, grafici, temi, fonti e flusso
  mostrano solo le menzioni che lo citano (nel testo o tra le entità riconosciute).
- Si possono scrivere fino a 5 varianti separate da virgola, per esempio
  `Mario Rossi, Rossi` oppure `Fratelli d'Italia, FdI`. Maiuscole e accenti non contano.
- I suggerimenti propongono le persone e i partiti citati più spesso nelle ultime 24 ore.
- **Con il servizio collegato** il nome viene inviato a `POST /api/obiettivo`: il servizio lo
  aggiunge subito alle ricerche su Bluesky e X (tra virgolette, per cercare il nome esatto) e
  alle parole dei filtri RSS e Telegram, avvia una raccolta immediata e lo ricorda anche dopo
  un riavvio. Tutti i cruscotti collegati ricevono il cambio in diretta.
- **Mostra tutto** toglie il filtro e ferma la ricerca aggiuntiva.
- Il nome resta anche nell'indirizzo della pagina (`?nome=...`), utile da condividere con lo staff.
- Le allerte restano tutte visibili; quelle che riguardano il nome monitorato hanno il nome nel titolo.

## Indicatori del cruscotto

| Indicatore | Calcolo |
|---|---|
| Indice netto | media dei punteggi pesata per la sicurezza del giudizio, per 100 (da −100 a +100), ultima ora |
| Menzioni all'ora | menzioni degli ultimi 60 minuti, confronto con l'ora prima |
| Volatilità | scarto quadratico medio dell'indice sui 12 intervalli di 10 minuti delle ultime 2 ore |
| Sarcasmo rilevato | quota di menzioni ironiche nell'ultima ora |
| Emozione prevalente | emozione con intensità media più alta nell'ultima ora |

## Installazione del servizio

Requisiti: un server Linux con Python 3.10 o successivo, nginx e un certificato HTTPS.

```bash
cd sentiment-fvg/servizio
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt

cp .env.esempio .env
chmod 600 .env
nano .env               # chiave Anthropic, codici di accesso, segreti, connettori
nano fonti.yaml         # feed RSS verificati, parole e ricerche da monitorare

uvicorn app:app --host 127.0.0.1 --port 8080
```

Per l'avvio automatico, un servizio systemd (`/etc/systemd/system/monitor.service`):

```ini
[Unit]
Description=Monitor percezione pubblica FVG
After=network-online.target

[Service]
User=monitor
WorkingDirectory=/opt/monitor/servizio
ExecStart=/opt/monitor/servizio/.venv/bin/uvicorn app:app --host 127.0.0.1 --port 8080
Restart=always

[Install]
WantedBy=multi-user.target
```

Poi `sudo systemctl enable --now monitor`. La configurazione di nginx è in `SICUREZZA.md`.

**Telegram (facoltativo)**: al primo avvio con `TELEGRAM_API_ID` e
`TELEGRAM_API_HASH` il servizio chiede nel terminale il numero di telefono e il
codice di verifica; farlo una volta a mano con `uvicorn` prima di passare a systemd.

**Ingresso esterno**: strumenti come n8n o Make possono inviare menzioni a
`POST /api/ingresso` con un elenco JSON
(`[{"id_esterno": "...", "testo": "...", "url": "...", "testata": "..."}]`)
e l'intestazione `X-Firma: sha256=<HMAC del corpo con SEGRETO_WEBHOOK>`.

## Pubblicazione del cruscotto su AtlanteFVG.it

Il cruscotto vive nella pagina `monitor/` del repository Atlante-FVG, con la grafica dell'Atlante e lo stesso
accesso cifrato della sezione Elezioni (vedi la sezione «Monitor della percezione pubblica» nel README dell'Atlante).
Dopo una modifica di `app.js` si rigenera il contenuto cifrato da lì:

```
python3 -I scripts/prepara_monitor.py <questa cartella>/app.js <chart.umd.min.js 4.4.1> /tmp/frammento.html
PAROLA='…' node scripts/cifra_pagina.js /tmp/frammento.html monitor/contenuto.json
```

Per collegare il servizio si scrive il suo indirizzo in `data-servizio` di `#monitor`, nel modello
`scripts/monitor_frammento.html` dell'Atlante, e si rigenera il contenuto. La versione autonoma del cruscotto
(`index.html` e `style.css`, adatta anche a un iframe) resta nella storia di questo repository, al commit `9356774`.

## Costi dell'analisi

Il costo dipende da quante menzioni si analizzano. Listino Anthropic di
ottobre 2026 per il modello predefinito `claude-opus-5-5`: 4 dollari per
milione di token (unità di testo, circa tre quarti di parola) in ingresso e 20
dollari per milione in uscita.

Stima per un lotto di 10 menzioni: circa 1.500 token in ingresso e 3.000 in
uscita compreso il ragionamento, cioè circa 7 centesimi di dollaro a lotto e
meno di un centesimo a menzione. Con 2.000 menzioni al giorno si arriva a circa
13 dollari al giorno. *Stima DA VERIFICARE nei primi giorni con il registro di
utilizzo della console Anthropic*, perché la lunghezza dei testi e il
ragionamento variano.

Per contenere la spesa:

- `SFORZO_ANALISI=low` riduce il ragionamento del modello (prima provare se la qualità sul sarcasmo regge);
- parole chiave più precise in `fonti.yaml` riducono le menzioni non pertinenti;
- `MODELLO_ANALISI=claude-haiku-5-5` è molto più economico, a fronte di una minore finezza su ironia e dialetti.

## Limiti da conoscere

- Il giudizio automatico è un supporto alla lettura, non un sondaggio: le
  menzioni sui social non sono un campione rappresentativo della popolazione.
- Facebook e Instagram non consentono la ricerca libera: si leggono solo i
  commenti alle pagine amministrate.
- Gli indirizzi dei feed in `fonti.yaml` sono segnaposto *DA VERIFICARE* sui siti
  delle testate prima dell'uso.
- Prima di rispondere pubblicamente a un'allerta, leggere sempre le fonti originali.
