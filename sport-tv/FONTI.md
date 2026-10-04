# Fonti dei dati: ricerca, verifica e limiti

Ricerca svolta il 4 ottobre 2026. Ogni fonte è stata interrogata davvero da
questo ambiente: niente indirizzi presunti, niente dati dimostrativi spacciati
per reali.

Una distinzione guida tutto il progetto:

- un **palinsesto** dice chi trasmette un evento in Italia, a che ora e dove;
- un **calendario** dice solo quando si gioca. Un evento presente soltanto in un
  calendario compare nell'app come **«Trasmissione in Italia da confermare»**.

## Fonti integrate

| Fonte | Indirizzo letto | Tipo | Sport coperti | Dati disponibili | Metodo di accesso | Limiti |
| --- | --- | --- | --- | --- | --- | --- |
| RaiPlay, palinsesto Rai | `https://www.raiplay.it/palinsesto/app/{canale}/{gg-mm-aaaa}.json` per `rai-1`, `rai-2`, `rai-3`, `rai-sport` | Palinsesto | Tutti quelli trasmessi da Rai (calcio, ciclismo, basket, pallavolo, nuoto, judo, ippica, motori, tiro con l'arco e altri) | Titolo, programma, genere, data e ora (ora italiana), durata, pagina RaiPlay dell'evento | File JSON (JavaScript Object Notation) pubblico usato dalla pagina del palinsesto, senza autenticazione | Circa 8 giorni in avanti, poi risponde 404. **Non indica se un programma è in diretta o in replica.** Servizio non documentato: può cambiare senza preavviso |
| Mediaset Infinity, guida TV | `https://api-ott-prod-fe.mediaset.net/PROD/play/feed/allListingFeedEpg/v2.0?byListingTime={da}~{a}&byCallSign={sigla}` per Canale 5, Italia 1, Rete 4, 20 Mediaset, Italia 2 | Palinsesto | Sport trasmessi da Mediaset (nel periodo verificato: NFL su 20 Mediaset) | Titolo, programma, generi, inizio e fine in UTC (tempo coordinato universale), pagina del programma | Servizio JSON pubblico usato dalla guida TV, senza autenticazione | Circa 9 giorni in avanti; un giorno per richiesta (intervalli più lunghi tornano vuoti). Non distingue diretta e replica. Non documentato |
| DAZN, programmazione Italia | `https://epg.discovery.indazn.com/eu/v5/Epg?date={aaaa-mm-gg}&country=it&languageCode=it` | Palinsesto | Tutto il catalogo DAZN Italia: Serie A (tutte le partite), Serie B, Serie A femminile, pallavolo, basket, NFL, motori, ciclismo, golf, eventi Eurosport su DAZN | Titolo, competizione, sport, partecipanti, inizio e fine in UTC, tipo (diretta, in arrivo, su richiesta), gratuità | Servizio JSON pubblico usato dalla pagina «Programmazione» di DAZN, senza autenticazione | Completo per 7 o 8 giorni, poi solo gli eventi già fissati. Collegamento alla pagina della programmazione, non al singolo evento. Non documentato |
| SuperTennis, palinsesto | `https://www.supertennis.tv/On-Demand/Palinsesto` | Palinsesto | Tennis e padel sul canale 64 del digitale terrestre | Titolo, incontro, inizio e fine; «LIVE» e «(replica)» nel titolo | Pagina pubblica letta come la legge un browser | Circa una settimana. Gli orari sono in UTC (verificato nel codice del sito, funzione `convertUtcDateToLocalDate`). Struttura della pagina non garantita |
| openfootball, calendario Serie A | `https://raw.githubusercontent.com/openfootball/football.json/master/{stagione}/it.1.json` | Calendario | Serie A maschile | Giornata, data, ora (ora italiana), squadre | File statico su GitHub, dati di pubblico dominio (CC0 1.0) | Nessun dato televisivo. Orari presenti solo per le giornate già fissate. Aggiornato una volta al giorno |
| Jolpica F1, calendario Formula 1 | `https://api.jolpi.ca/ergast/f1/{anno}/races.json` | Calendario | Formula 1 | Gran premio, prove, qualifiche, sprint e gara con data e ora UTC | Servizio gratuito, dati CC BY-NC-SA 4.0 (uso non commerciale con attribuzione) | Nessun dato televisivo. Limite di 4 richieste al secondo e 500 l'ora |

### Verifiche fatte sulle fonti

- **Orari**: Rai e openfootball sono in ora italiana; Mediaset, DAZN, SuperTennis
  e Jolpica in UTC. Gli orari di openfootball sono stati confrontati con quelli
  UTC di un'altra fonte per tre partite del 18 ottobre 2026 (Milan e Atalanta,
  Udinese e Lecce, Juventus e Lazio): coincidono con l'ora legale italiana.
- **Corrispondenza fra fonti**: le dieci partite di Serie A della 6ª giornata
  (10, 11 e 12 ottobre 2026) presenti in DAZN sono state unite alle stesse
  partite del calendario openfootball, con gli stessi orari.
- **Tipo di trasmissione**: dove la fonte non lo dice, l'app scrive «Diretta o
  replica non indicato dalla fonte». Unica deduzione ammessa: un titolo che cita
  solo annate passate (per esempio «ATP 1000 Shanghai 2025» trasmesso nel 2026)
  viene indicato come replica, con una nota che lo spiega.

## Fonti valutate e non integrate

| Fonte | Esito della verifica | Motivo |
| --- | --- | --- |
| Sky Guida TV (`apid.sky.it/gtv/v1`, `guidatv.sky.it`) | Risposta «This page can't be displayed» e pagina con controllo anti-automazione | Il servizio è protetto da un sistema anti-bot: aggirarlo violerebbe la richiesta di non superare protezioni tecniche. Restano quindi esclusi **Sky Sport, NOW, TV8 e Cielo** |
| TV8 (`tv8.it/guida-tv`) | Stessa pagina d'errore della piattaforma Sky | Come sopra |
| Eurosport (`eurosport.it/programmi-tv`) | Errore 403 (accesso negato) | Fonte non accessibile. Gli eventi Eurosport compaiono solo se pubblicati nel palinsesto DAZN |
| TheSportsDB (chiave gratuita) | Risposte troncate (un solo risultato per la guida TV per Paese, 3 o 5 partite per giornata) | La guida TV per Paese e i calendari completi richiedono l'abbonamento a pagamento |
| Sportitalia (`sportitalia.it/palinsesto`) | Errore 404 | Nessun palinsesto pubblico trovato |
| NOVE e Discovery (`nove.tv/guida-tv`) | Pagina caricata solo via JavaScript, nessun servizio pubblico individuato | Non verificabile senza ricostruire chiamate non pubblicate |
| Prime Video, HBO Max, NOW | Nessun palinsesto pubblico | Contenuti accessibili solo con autenticazione |
| Liste IPTV, guide di terze parti non ufficiali | Non considerate | Escluse per scelta: non sono fonti ufficiali o non sono autorizzate |

## Copertura reale

L'obiettivo è coprire tutti gli sport, ma le fonti gratuite **non** coprono
tutta l'offerta italiana. Con le fonti integrate si vedono:

- **TV in chiaro**: Rai 1, Rai 2, Rai 3, Rai Sport, Canale 5, Italia 1, Rete 4,
  20 Mediaset, Italia 2, SuperTennis;
- **streaming a pagamento**: DAZN (incluse le partite visibili gratis con un
  account, segnalate come tali);
- **calendari senza dato televisivo**: Serie A maschile, Formula 1.

Mancano Sky Sport, NOW, TV8, Cielo, Prime Video, Eurosport (fuori da DAZN),
HBO Max, Sportitalia e le emittenti locali. L'app lo dichiara nella schermata
Informazioni.

## Indicazione generale sui diritti della Serie A

Per le partite di Serie A presenti solo nel calendario, l'app aggiunge una nota
esplicitamente **non verificata per la singola partita**: secondo l'assegnazione
dei diritti 2024-2029 deliberata dall'assemblea della Lega Serie A il 23 ottobre
2023, DAZN trasmette tutte le partite e Sky tre per giornata in co-esclusiva.
L'evento resta comunque nella sezione «da confermare» finché non compare in un
palinsesto.

## Condizioni d'uso

- I servizi di Rai, Mediaset, DAZN e SuperTennis sono quelli usati dalle loro
  pagine pubbliche, senza autenticazione e senza aggirare protezioni. Non hanno
  condizioni d'uso specifiche per il riuso automatico: per questo il servizio li
  interroga poche volte al giorno (ogni 3 ore), si presenta con un nome
  riconoscibile (`SportInTVItalia/1.0`), riporta solo orari e titoli e rimanda
  sempre alla pagina ufficiale. Se un titolare chiedesse di non essere letto,
  basta togliere il connettore da `connettori/Calendari.kt` (funzione
  `tuttiIConnettori`).
- openfootball: pubblico dominio (CC0 1.0).
- Jolpica F1: CC BY-NC-SA 4.0, uso non commerciale con attribuzione, riportata
  nella schermata Informazioni.
