# Fatti del Giorno · app Android

Ogni giorno, o per qualunque data scelta dal calendario, l'app propone fatti,
ricorrenze e anniversari realmente legati a quel giorno e a quel mese, con le
fonti da aprire con un tocco, e li trasforma in una bozza per i social e in un
prompt pronto da incollare in un servizio di intelligenza artificiale.

Funziona senza registrazione, senza chiavi e senza servizi a pagamento. Chiede
solo i permessi di rete (per interrogare le fonti e sapere se c'è connessione).

## L'APK pronto da installare

Il file compilato sta in `apk/FattiDelGiorno.apk`. Una volta che questa cartella
è sul ramo principale, dal telefono si apre:

```
https://costalonga.org/fatti-del-giorno/apk/FattiDelGiorno.apk
```

Android chiede di concedere al browser o al gestore dei file il permesso di
installare app da quella origine. Serve Android 8.0 o successivo. L'ultima
versione in assoluto sta sempre fra i file dell'esecuzione più recente del
flusso **Fatti del Giorno · app Android** nella scheda Actions del deposito.

Gli aggiornamenti si installano sopra la versione precedente, perché tutti gli
APK compilati qui hanno la stessa firma (vedi `chiavi/LEGGIMI.md`).

## Che cosa fa

1. All'apertura mostra la data di oggi calcolata con il fuso `Europe/Rome`,
   qualunque sia il fuso del telefono. Il pulsante **Cambia data** apre il
   calendario; **Torna a oggi** riporta alla data corrente.
2. Interroga le fonti per quel giorno e quel mese e mostra le schede trovate:
   ricorrenze e festività, eventi storici, nascite, scomparse, fatti di
   cultura, scienza, sport e società, curiosità. I filtri **Friuli Venezia
   Giulia**, **Italia** e **Mondo** e le categorie sono tutti attivi
   all'inizio e si possono togliere con un tocco.
3. Ogni scheda dice perché il fatto è legato alla data ("avvenuto il…",
   "nato/a il…", "ricorrenza celebrata il…"), l'area geografica, da dove viene
   il dato, il livello di verifica e le fonti con il collegamento, il nome e la
   data di consultazione.
4. Con **Usa per un contenuto social** si sceglie la scheda; in fondo alla
   pagina si scelgono piattaforma (Instagram, Facebook, TikTok, LinkedIn, X),
   formato (post, storia, carosello, script per Reel o video breve, script per
   TikTok, carosello di foto, testo professionale, thread), tono (informale,
   istituzionale, divulgativo, ironico, entusiasta) e, se servono, pubblico,
   lunghezza, invito all'azione, emoji e hashtag.
5. Compaiono due testi distinti, entrambi modificabili: la **bozza del
   contenuto** e il **prompt** da incollare in un servizio di intelligenza
   artificiale. Ogni testo ha i pulsanti **Copia** e **Condividi**, che usano
   gli appunti e il menu di condivisione di Android. L'app non pubblica nulla e
   non chiede l'accesso a nessun profilo social.

La bozza viene costruita sul telefono usando soltanto titolo, testo, data e
fonti della scheda: non aggiunge dettagli storici. Il prompt chiede al servizio
AI di creare o rifinire il contenuto senza alterare i fatti verificati,
mantenendo il riferimento alle fonti e segnalando tra parentesi quadre ciò che
non è chiaro invece di inventare.

## Livelli di verifica

| Etichetta nella scheda | Che cosa significa |
| --- | --- |
| Fonte istituzionale verificata | Voce dell'archivio dell'app, controllata a mano su una fonte ufficiale (vedi `VERIFICHE.md`). |
| Da Wikipedia: controllare le note | Viene dal feed "Accadde oggi" di Wikipedia. Prima di pubblicare, aprire la voce e controllare le note. |
| Dato strutturato: verifica consigliata | Viene da Wikidata senza una voce in italiano collegata. Cercare una seconda conferma. |
| Dati discordanti: da verificare | Le fonti non concordano su una data o su un anno. Non pubblicare senza aver chiarito. |

Quando lo stesso fatto compare sia in Wikipedia sia in Wikidata, le due schede
vengono fuse in una sola con tutte le fonti; se gli anni non coincidono, la
scheda diventa "discordante".

## Stati dell'app

- **Caricamento**: "Interrogo le fonti per questa data…".
- **Senza connessione**: vengono mostrati solo l'archivio dell'app e i
  contenuti già memorizzati per quella data, con la scritta "Contenuti
  memorizzati sul telefono il …": non vengono presentati come aggiornati.
- **Limite raggiunto**: se una fonte risponde "troppe richieste" (codice 429),
  la scheda di avviso lo dice, con il tempo di attesa suggerito dalla fonte, e
  le altre fonti restano in uso.
- **Fonte che non risponde o errore**: ogni fonte ha il proprio avviso; se
  nessuna risponde e c'è una memoria precedente, si usa quella dicendolo.
- **Nessun risultato verificabile**: lo dice un avviso, senza inventare nulla.
- I risultati di rete restano in memoria per 24 ore (pulsante **Aggiorna** in
  alto per forzare una nuova consultazione; **Svuota** nelle impostazioni).

## Come si compila

Servono Java 17 e l'SDK Android con la piattaforma 35 (Android Studio li
installa; da riga di comando basta `sdkmanager "platforms;android-35"
"build-tools;35.0.0"` e un file `local.properties` con `sdk.dir=`).

```
cd fatti-del-giorno
./gradlew testPubblicaUnitTest        # prove automatiche
./gradlew assemblePubblica            # APK firmato: app/build/outputs/apk/pubblica/
./gradlew lintPubblica                # controllo del codice
./gradlew installDebug                # installa la variante di prova su un telefono collegato
```

La variante `pubblica` è quella da installare; `release` resta senza firma per
chi vuole usare una chiave propria.

## Chiave Gemini facoltativa

L'app è completa senza nessuna chiave. Chi vuole far rifinire la bozza da
Gemini (API di Google, piano gratuito) può:

1. creare una chiave gratuita su Google AI Studio (`aistudio.google.com`);
2. aprire **Impostazioni** nell'app, incollare la chiave, eventualmente
   cambiare il modello (predefinito `gemini-2.5-flash`) e premere **Salva**;
3. nella sezione "Contenuto social" comparirà il pulsante **Rifinisci con
   Gemini**.

Come viene usata e conservata la chiave:

- è cifrata con una chiave AES generata nel Keystore di Android, che non lascia
  il dispositivo; nelle preferenze dell'app resta solo il testo cifrato;
- è esclusa dal backup (vedi `app/src/main/res/xml/regole_copia.xml`);
- non compare nei registri, non è nel codice né nell'APK;
- viene inviata solo a Google, nell'intestazione `x-goog-api-key` della
  richiesta, e solo quando si preme il pulsante; il prompt inviato contiene il
  fatto, le fonti e la bozza;
- si elimina con **Elimina la chiave**.

Limiti e condizioni: il piano gratuito ha quote di richieste al minuto e al
giorno decise da Google (la pagina ufficiale rimanda ai limiti mostrati in AI
Studio). Se la quota finisce, l'app lo dice e la bozza locale resta
disponibile. Nel piano gratuito Google dichiara di poter usare i contenuti
inviati per migliorare i propri prodotti: non incollare dati personali o
riservati nel prompt. Chi non vuole usare Gemini copia il prompt e lo incolla
nel servizio che preferisce.

## Fonti integrate, limiti e connessione

Il dettaglio di ogni fonte (indirizzi, licenze, limiti, attribuzione) sta in
`FONTI.md`. In sintesi:

| Fonte | Chiave | Che cosa dà | Serve Internet |
| --- | --- | --- | --- |
| Archivio dell'app (`app/src/main/assets/ricorrenze.json`) | no | ricorrenze e fatti del FVG e dell'Italia controllati su fonti istituzionali | no |
| Wikipedia in italiano, feed "Accadde oggi" | no | ricorrenze, eventi, nascite e scomparse del giorno | sì |
| Wikipedia in inglese, selezione del giorno (si può spegnere) | no | fatti in evidenza nel mondo, testo in inglese | sì |
| Wikidata, interrogazioni SPARQL | no | persone nate o scomparse in FVG nel giorno; persone nate in Italia (si può spegnere) | sì |
| Gemini (facoltativo) | sì, personale | rifinitura della bozza | sì |

Senza connessione funzionano: la scelta della data, l'archivio dell'app, i
contenuti memorizzati, la generazione della bozza e del prompt, copia e
condivisione.

## Struttura del codice

```
app/src/main/kotlin/org/costalonga/fattidelgiorno/
  dominio/        modelli, date (fuso Europe/Rome), classificatore per area e categoria
  dati/rete/      client HTTP, feed di Wikipedia, interrogazioni Wikidata
  dati/locale/    archivio delle ricorrenze verificate, memoria su file
  dati/           FattiRepository: unisce le fonti, toglie i doppioni, gestisce memoria ed errori
  generazione/    piattaforme, formati, toni; GeneratoreBozza e GeneratorePrompt
  ai/             client Gemini facoltativo
  sicurezza/      custodia della chiave con il Keystore di Android
  ui/             schermate Compose, ViewModel, copia e condivisione
app/src/test/     prove automatiche (JUnit, MockWebServer, Robolectric)
```

Interfaccia, accesso ai dati e generazione dei testi sono separati: la
generazione non sa nulla della rete, l'interfaccia non sa nulla delle fonti.

## Prove automatiche

`./gradlew testPubblicaUnitTest` esegue le prove descritte in `VERIFICHE.md`:
date e fuso orario, legame fra data selezionata e risultati, collegamenti alle
fonti, generazione di bozza e prompt per ogni piattaforma e formato,
funzionamento senza chiave, gestione di limite, errori, timeout, assenza di
rete e assenza di risultati, copia e condivisione tramite Android, integrità
dell'archivio delle ricorrenze.

## Licenze e attribuzione

I testi di Wikipedia sono rilasciati con licenza CC BY-SA 4.0, i dati di
Wikidata con CC0: ogni scheda porta il collegamento alla voce o all'elemento di
origine, e la bozza riporta la fonte. Il codice dell'app è nel deposito del
sito; la chiave di firma depositata non è segreta (vedi `chiavi/LEGGIMI.md`).
