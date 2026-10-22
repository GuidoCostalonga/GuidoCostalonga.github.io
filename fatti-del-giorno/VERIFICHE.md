# Verifiche

## Registro dei controlli sull'archivio delle ricorrenze

Ogni voce di `app/src/main/assets/ricorrenze.json` è stata controllata il 22
ottobre 2026 aprendo la fonte indicata e leggendo il passo che conferma data e
fatto.

| Voce | Data | Fonte consultata | Che cosa conferma |
| --- | --- | --- | --- |
| Terremoto del Friuli | 6 maggio 1976 | Dipartimento della Protezione Civile, pagina "Friuli earthquake"; Vigili del Fuoco, "Il terremoto del Friuli del maggio 1976" | "On May 6, 1976, an earthquake of magnitude 6.5 severely hit Friuli", oltre 100 comuni di Udine e Pordenone, 965 vittime nelle due sequenze; "Sono le 21 del 6 maggio 1976…", epicentro a est di Gemona, 990 morti e oltre 45.000 senzatetto |
| Nuove scosse in Friuli | 15 settembre 1976 | Dipartimento della Protezione Civile | "On September 15, a new earthquake of magnitude 5.9 caused further destruction" |
| Catastrofe del Vajont | 9 ottobre 1963 | Parco Naturale Dolomiti Friulane, "Vajont storia e rinascita" (documento del Parco) | "il tragico evento del 9 ottobre 1963… l'immensa frana si staccò dal monte Tóc e precipitò nel lago artificiale… causò 2000 vittime" |
| Fieste de Patrie dal Friûl | 3 aprile (1077) | ARLeF, pagina "Festa della Patria del Friuli" e comunicato sul bando per i Comuni | "Il 3 Aprile 1077 decretò la nascita dello Stato Patriarcale friulano"; "la Fieste ricorre il 3 aprile e ricorda la data di costituzione dello Stato patriarcale friulano nel 1077" |
| Trieste torna all'Italia | 26 ottobre 1954 | Università degli Studi di Trieste e Circolo della Cultura e delle Arti, "Trieste italiana" | "5 ottobre 1954 con la firma del Memorandum di Londra, seguita il 26 ottobre dallo storico…"; "Piazza Unità gremita il 26 ottobre 1954" |
| Statuto speciale del FVG | 31 gennaio 1963 | Normattiva, legge costituzionale 31 gennaio 1963 n. 1; Regione FVG, "statuto e norme di attuazione" | Titolo, "GU n.29 del 01-02-1963"; "adottato con Legge costituzionale 31 gennaio 1963, n.1" |
| Tina Modotti, nascita | 17 agosto 1896 | Portale Antenati, Ministero della Cultura | "nacque il 17 agosto 1896 a Udine"; atto di nascita all'Archivio di Stato di Udine |
| Tina Modotti, scomparsa | 5 gennaio 1942 | Portale Antenati, Ministero della Cultura | "Tina Modotti morì il 5 gennaio 1942 a Città del Messico" |
| Cividale nel patrimonio UNESCO | 25 giugno 2011 | Tempietto Longobardo di Cividale del Friuli, pagina UNESCO | "has been a UNESCO World Heritage Site since 25 June 2011, as a part of the serial site Longobards in Italy. Places of the Power (568-774 A.D.)" |
| Giorno del ricordo | 10 febbraio | Normattiva, legge 30 marzo 2004 n. 92, art. 1 | "La Repubblica riconosce il 10 febbraio quale Giorno del ricordo…" |
| Giornata dell'Unità nazionale | 17 marzo | Normattiva, legge 23 novembre 2012 n. 222, art. 1 comma 3 | "riconosce il giorno 17 marzo, data della proclamazione in Torino, nell'anno 1861, dell'Unità d'Italia" |
| Festa della Repubblica | 2 giugno | Normattiva, legge 27 maggio 1949 n. 260, art. 1 | "Il giorno 2 giugno, data di fondazione della Repubblica, è dichiarato festa nazionale" |

Voci valutate e lasciate fuori perché la fonte istituzionale non era
raggiungibile dall'ambiente di sviluppo: Giorno della Memoria (legge 211/2000,
Normattiva interrompeva la connessione), festa patronale di San Bartolomeo a
Roveredo in Piano (sito del Comune con certificato non valido), fondazione di
Palmanova (pagina UNESCO non raggiungibile), 25 aprile e 4 novembre (articolo 2
della legge 260/1949 non raggiungibile). Le ricorrenze nazionali compaiono
comunque nella sezione ricorrenze del feed di Wikipedia.

## Prove automatiche

`./gradlew testPubblicaUnitTest`. Ogni prova è nel file indicato, in
`app/src/test/kotlin/org/costalonga/fattidelgiorno/`.

| Controllo richiesto | Prova | Che cosa verifica |
| --- | --- | --- |
| Date e fuso orario | `DateTest` | La data di oggi segue `Europe/Rome` (alle 23:30 UTC del 28 marzo a Roma è il 29); il calendario (mezzanotte UTC) non sbaglia giorno; il 29 febbraio si può cercare, il 31 aprile no; formati italiani; momento di consultazione leggibile |
| Legame fra data selezionata e risultati | `WikipediaOggiTest`, `WikidataPersoneTest`, `RicorrenzeLocaliTest`, `FattiRepositoryTest` | Ogni scheda porta il giorno e il mese richiesti; la chiamata al feed usa `MM/GG`; l'interrogazione Wikidata filtra giorno, mese e precisione al giorno; l'archivio propone le voci solo nel loro giorno; la spiegazione "avvenuto il…", "nato/a il…", "ricorrenza celebrata il…" è coerente; una data inesistente viene rifiutata |
| Collegamenti alle fonti | `WikipediaOggiTest`, `WikidataPersoneTest`, `RicorrenzeLocaliTest`, `CondivisioneTest` | Ogni scheda ha almeno una fonte con indirizzo `https`, nome e data di consultazione; l'apertura di una fonte produce un intent `ACTION_VIEW` con quell'indirizzo |
| Generazione di bozza e prompt | `GeneratoreTest` | Per ogni piattaforma e formato la bozza contiene data, fatto e fonte; il post per X sta in 280 caratteri (collegamenti contati 23); il thread è numerato ed entro il limite; lo script TikTok ha apertura, battute brevi e chiusura; carosello e storia hanno la sequenza di schede; tono, emoji, hashtag, lunghezza e invito seguono le opzioni; il prompt riporta fatto, fonti, livello di verifica, regole e bozza, e chiede la traduzione fedele per i testi in inglese |
| Funzionamento senza chiave | `FattiRepositoryTest`, `WikipediaOggiTest`, `WikidataPersoneTest` | Le fonti e la ricerca non ricevono nessuna chiave; la generazione è locale; `GeminiTest` controlla che la chiave, quando c'è, viaggi solo nell'intestazione e che quota esaurita ed errori diventino messaggi |
| Errori e assenza di risultati | `WikipediaOggiTest`, `WikidataPersoneTest`, `FattiRepositoryTest` | Codice 429 con `Retry-After`, errore del server, risposta illeggibile, timeout, fonte che lancia un'eccezione; senza connessione si mostrano solo i contenuti memorizzati segnalati come tali; con tutte le fonti ferme si usa la memoria o si dichiara "nessun risultato verificabile"; i doppioni vengono tolti e le schede uguali in Wikipedia e Wikidata vengono fuse, con anni discordanti segnalati |
| Copia e condivisione Android | `CondivisioneTest` (Robolectric) | La condivisione apre il menu di Android con `ACTION_SEND` e il testo; la copia mette il testo negli appunti con l'etichetta |
| Archivio delle ricorrenze | `RicorrenzeLocaliTest` | Date valide, fonti con collegamento e data di consultazione, identificativi unici, livello "Fonte istituzionale verificata" |
| Memoria su file | `CacheFattiTest` | Salvataggio e rilettura per data, file rovinato senza conseguenze |

## Controlli manuali consigliati sul telefono

1. Aprire l'app senza rete: deve comparire l'avviso "Nessuna connessione" e
   l'archivio dell'app (per esempio il 6 maggio o il 3 aprile).
2. Scegliere il 22 ottobre e confrontare una scheda con la voce di Wikipedia
   aperta dal collegamento.
3. Scegliere una scheda, passare da Instagram a X e verificare che il formato
   cambi e che il contatore dei caratteri resti entro 280 per il post.
4. Premere **Copia** e incollare in un'altra app; premere **Condividi** e
   scegliere un'app dal menu di Android.
5. Nelle impostazioni, salvare una chiave Gemini, chiudere e riaprire l'app:
   deve risultare "chiave salvata"; eliminarla e verificare che il pulsante
   "Rifinisci con Gemini" scompaia.
