# Fonti integrate

Tutte le fonti sono state controllate sulla documentazione ufficiale il 22
ottobre 2026. Nessuna richiede una chiave. L'app si presenta a ogni fonte con
lo User-Agent `FattiDelGiorno/1.0 (https://costalonga.org/fatti-del-giorno)
okhttp/4.12.0`, come chiede la politica di Wikimedia.

## Archivio dell'app

- File: `app/src/main/assets/ricorrenze.json`.
- Contenuto: ricorrenze e fatti del Friuli Venezia Giulia e dell'Italia,
  ciascuno con testo, giorno, mese, eventuale anno, categoria, tipo di
  collegamento, area e una o più fonti istituzionali con data di consultazione.
- Verifica: ogni voce è stata controllata a mano sulla fonte indicata; il
  registro dei controlli sta in `VERIFICHE.md`. Nell'app compare con il livello
  "Fonte istituzionale verificata".
- Funziona senza connessione.
- Per aggiungere una voce: copiare una voce esistente, controllare il fatto
  sulla fonte, scrivere la data del controllo. La prova
  `RicorrenzeLocaliTest` rifiuta voci con date impossibili o senza fonte.

## Wikipedia, feed "Accadde oggi"

- Indirizzo: `https://it.wikipedia.org/api/rest_v1/feed/onthisday/all/MM/GG`
  (in inglese: `https://en.wikipedia.org/api/rest_v1/feed/onthisday/selected/MM/GG`).
- Che cosa dà: per il giorno e il mese richiesti, le sezioni `holidays`
  (ricorrenze), `selected` (in evidenza), `events`, `births`, `deaths`, ognuna
  con un testo, l'anno e le voci collegate (titolo, descrizione, estratto,
  collegamento alla pagina, identificativo Wikidata).
- Licenza: testi CC BY-SA 4.0. Ogni scheda riporta il collegamento alla voce;
  la bozza riporta la fonte.
- Limiti: l'API REST di Wikimedia chiede uno User-Agent identificativo e
  risponde con il codice 429 quando si fanno troppe richieste (nelle prove in
  questo ambiente, con un indirizzo condiviso, è capitato spesso). L'app fa una
  sola richiesta per lingua e per data, memorizza il risultato per 24 ore e,
  in caso di 429, mostra l'avviso "Limite di richieste raggiunto" con il tempo
  di attesa indicato dalla fonte.
- Nota: l'edizione italiana del feed spesso non riporta nascite e scomparse
  (dipende da come sono scritte le pagine delle date su it.wikipedia.org); per
  questo le persone vengono cercate anche su Wikidata.
- L'edizione inglese si usa solo per la selezione in evidenza, è segnalata
  come "testo in inglese" e si può spegnere nelle impostazioni.
- Livello di verifica assegnato: "Da Wikipedia: controllare le note".
- Area geografica: assegnata dall'app in base ai toponimi del testo (Friuli
  Venezia Giulia, Italia, altrimenti mondo); la scheda lo dichiara nelle note.

## Wikidata, servizio di interrogazione SPARQL

- Indirizzo: `https://query.wikidata.org/sparql` con `Accept:
  application/sparql-results+json`.
- Interrogazioni (il testo completo è in `WikidataPersone.query`):
  persone nate in un luogo situato nel Friuli Venezia Giulia (`wd:Q1250`)
  nel giorno e mese richiesti; persone scomparse in FVG; persone con
  cittadinanza italiana e almeno otto voci collegate. Si chiedono solo le date
  con precisione al giorno (`wikibase:timePrecision 11`): chi ha in archivio
  solo l'anno di nascita non viene mostrato come nato il 1 gennaio.
- Licenza: dati CC0.
- Limiti documentati: ogni interrogazione ha 60 secondi di tempo massimo;
  ogni indirizzo può fare al massimo 5 interrogazioni in parallelo e ha 60
  secondi di elaborazione al minuto; oltre, il servizio risponde 429 con
  `Retry-After`. L'app lancia al massimo tre interrogazioni per data, ciascuna
  limitata a 40 risultati, e memorizza il risultato.
- Livello di verifica assegnato: "Da Wikipedia: controllare le note" se la
  persona ha una voce in italiano collegata, altrimenti "Dato strutturato:
  verifica consigliata". Se la descrizione di Wikidata indica un anno diverso
  dalla data registrata, la scheda diventa "Dati discordanti: da verificare".

## Gemini (facoltativo, con chiave personale)

- Indirizzo: `https://generativelanguage.googleapis.com/v1beta/models/{modello}:generateContent`,
  chiave nell'intestazione `x-goog-api-key`.
- Modello predefinito: `gemini-2.5-flash`, modificabile nelle impostazioni.
- Piano gratuito: nella pagina dei prezzi Google indica "Free of charge" per i
  modelli Flash e dichiara che nel piano gratuito i contenuti inviati possono
  essere usati per migliorare i prodotti. I limiti di richieste al minuto e al
  giorno sono mostrati in AI Studio e possono cambiare: l'app non li promette
  e, quando il servizio risponde 429, lo dice.
- L'app non lo chiama mai senza una chiave salvata e senza che si prema il
  pulsante. La chiave è custodita con il Keystore di Android (vedi README).

## Fonti valutate e non integrate

- Il feed `api.wikimedia.org/feed/v1/...` (portale API di Wikimedia) richiede
  un token per superare il limite anonimo e, nelle prove, rispondeva 429 fin
  dalla prima richiesta: si usa l'API REST di ogni edizione di Wikipedia, che
  non richiede nulla.
- Siti di alcuni enti (per esempio il Comune di Roveredo in Piano e il portale
  del Quirinale) non erano raggiungibili dall'ambiente di sviluppo, per un
  certificato non valido o per un blocco delle richieste automatiche: le
  ricorrenze che dipendevano solo da quelle pagine non sono state inserite
  nell'archivio. Si possono aggiungere dopo averle controllate a mano.
