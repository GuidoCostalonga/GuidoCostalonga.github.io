# Sport in TV Italia · app per Android

Guida agli eventi sportivi di oggi e dei 14 giorni successivi trasmessi
legalmente in Italia, in TV in chiaro, pay TV e streaming. Nessuna
registrazione, nessuna pubblicità, nessun costo.

L'app è una guida: non trasmette, non incorpora e non ritrasmette contenuti.
Il pulsante «Guarda» apre la pagina ufficiale dell'emittente, nell'app del
servizio se è installata, altrimenti nel browser.

Realizzato da Guido Costalonga. Tutti i diritti riservati.

---

## Che cosa fa

| Schermata | Contenuto |
| --- | --- |
| **Programma** | Eventi con trasmissione confermata, raggruppati per giorno e ordinati per orario. Ricerca per squadra, atleta, competizione, evento o canale. Date rapide (oggi, domani, i 13 giorni seguenti). Filtri combinabili per sport, competizione, canale, piattaforma, gratis o a pagamento, solo dirette, preferiti, eventi già conclusi |
| **Da confermare** | Eventi presenti nei calendari ufficiali ma in nessun palinsesto italiano consultato. Mai mescolati con quelli confermati |
| **Dettagli** | Orario in fuso italiano, stato (confermato, da confermare, rinviato, annullato), ora di fine solo se data dalla fonte, informazioni discordanti fra le fonti, tutte le possibilità di visione con canale, piattaforma, tipo (diretta, differita, replica, non indicato), accesso (in chiaro, gratis con registrazione, abbonamento, acquisto singolo), fonte e momento della verifica |
| **Preferiti** | Squadre, atleti e competizioni seguiti, eventi salvati, prossimi eventi dei preferiti. Tutto resta sul telefono |
| **Impostazioni** | Anticipo dei promemoria (5, 15, 30, 60 minuti), promemoria automatici per i preferiti, aggiornamento automatico, provenienza dei dati, indirizzo del servizio, tema chiaro o scuro |
| **Informazioni** | Fonti, copertura reale, licenze, limiti, la dicitura sull'autore |

**«In corso»** compare solo quando una trasmissione è dichiarata in diretta e
la fonte dà sia l'inizio sia la fine. Se la fonte non dice se è diretta o
replica, l'etichetta è «In onda ora».

Le fonti e i loro limiti sono descritti in [`FONTI.md`](FONTI.md); le verifiche
eseguite in [`VERIFICHE.md`](VERIFICHE.md).

---

## Come è fatto

```
sport-tv/
├── raccolta/      connettori alle fonti, unione dei duplicati, formato dei dati (Kotlin puro)
├── app/           app Android (Kotlin, Jetpack Compose, Material Design 3, Room, DataStore, WorkManager)
├── chiavi/        firma non segreta degli APK, per poter aggiornare l'app senza disinstallarla
└── apk/           l'APK pronto da installare
```

Il modulo `raccolta` è usato due volte:

1. dal **servizio online**, il flusso `.github/workflows/sport-tv-dati.yml`, che
   ogni 3 ore raccoglie i palinsesti e pubblica `eventi.json` sul ramo
   `dati-sport-tv` del deposito;
2. dall'**app**, che legge `eventi.json` dal servizio e, se il servizio non
   risponde o è fermo da oltre 12 ore, interroga direttamente le fonti. Se il
   servizio segnala una fonte non raggiungibile (succede con DAZN, che rifiuta i
   server di GitHub), l'app legge solo quella fonte dal telefono e la unisce.

### Perché GitHub Actions per il servizio

- è già dove vive il progetto: nessun altro account, nessun server da mantenere;
- è gratuito per i depositi pubblici e non può generare addebiti: senza un
  metodo di pagamento registrato, GitHub ferma l'esecuzione invece di fatturare;
- non servono segreti: le fonti sono pubbliche e la pubblicazione usa il
  permesso temporaneo che GitHub dà a ogni esecuzione (`GITHUB_TOKEN`);
- il file viene servito da `raw.githubusercontent.com`, gratuito e con cache.

Nell'APK non c'è alcun segreto: l'indirizzo del servizio è pubblico.

### Gestione degli errori

- Ogni fonte ha un connettore separato, con tempo massimo per richiesta, al
  massimo due richieste contemporanee per sito e due nuovi tentativi
  distanziati solo per errori di rete e risposte 5xx o 429.
- Una fonte che non risponde non blocca le altre. Se esiste un pacchetto
  precedente, i suoi dati vengono conservati e la fonte è segnata «restano i
  dati precedenti, forse superati».
- Sul telefono gli ultimi dati validi non vengono mai cancellati da un
  aggiornamento fallito. L'app mostra sempre l'ultimo aggiornamento riuscito e
  avvisa quando i dati hanno più di 12 ore.
- «Dati non disponibili» (nessun dato scaricato) e «Nessun evento in programma»
  (dati presenti, ma nessun evento) sono messaggi distinti.

### Orari

Gli orari sono conservati in UTC (tempo coordinato universale) e mostrati nel
fuso Europe/Rome con `java.time`, che gestisce da solo l'ora legale (fine il 25
ottobre 2026, inizio il 29 marzo 2026: entrambi i casi sono nelle prove).

### Duplicati

Due messe in onda sono lo stesso evento se sono una sfida fra gli stessi
partecipanti (in qualunque ordine e con nomi diversi: «Inter» e «FC
Internazionale Milano», «Napoli» e «SSC Napoli»), dello stesso sport e genere,
e cominciano a meno di 4 ore l'una dall'altra; oppure se, senza partecipanti,
hanno titoli molto simili e cominciano a meno di 45 minuti. Ogni trasmissione
resta separata con la sua fonte. Le repliche lontane nel tempo restano eventi a
sé. Gli orari discordanti vengono segnalati per esteso.

### Promemoria

- Un allarme per evento: ripianificare sostituisce, non duplica.
- Dopo ogni aggiornamento: se l'evento cambia orario il promemoria si sposta e
  l'utente viene avvisato; se è rinviato o annullato il promemoria si cancella
  con un avviso; se l'evento è stato spostato ad altro giorno viene ritrovato.
- Riavvio del telefono, aggiornamento dell'app, cambio di ora o di fuso: tutti i
  promemoria vengono ripianificati.
- Permessi chiesti: Internet, stato della rete, notifiche (solo quando si attiva
  il primo promemoria, da Android 13), avvio del telefono. **Nessun permesso per
  le sveglie esatte**: gli avvisi usano `setAndAllowWhileIdle`, che arriva anche
  col telefono in riposo ma può essere ritardato di alcuni minuti (fino a una
  quindicina in risparmio energetico profondo).

---

## Installare l'app

1. Scaricare sul telefono `apk/SportInTV.apk`
   (dal sito: `https://costalonga.org/sport-tv/apk/SportInTV.apk`, una volta unite
   le modifiche al ramo principale).
2. Aprire il file e consentire l'installazione da questa fonte quando Android lo
   chiede.
3. Al primo avvio l'app scarica i dati. Il permesso per le notifiche viene
   chiesto solo quando si attiva un promemoria.

Gli APK compilati dal deposito hanno tutti la stessa firma (`chiavi/`): le
versioni successive si installano sopra le precedenti.

## Compilare

Serve JDK (Java Development Kit) 17 o successivo e l'Android SDK con la
piattaforma 35.

```bash
cd sport-tv
export ANDROID_HOME=/percorso/dell/android-sdk     # oppure local.properties con sdk.dir=...
./gradlew :raccolta:test :app:testDebugUnitTest    # prove automatiche
./gradlew :app:assembleDebug                       # APK di prova: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease                     # APK ottimizzato: app/build/outputs/apk/release/app-release.apk
./gradlew :app:lintDebug                           # controllo del codice
```

Il Gradle Wrapper scarica da solo Gradle 8.14.3. Versioni: Android Gradle
Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01 (Material 3 1.3.1), Room
2.6.1, DataStore 1.1.1, WorkManager 2.9.1, OkHttp 4.12.0, kotlinx.serialization
1.7.3. Sono le stesse combinazioni già usate con successo nell'app Meteo FVG di
questo deposito, verificate compatibili fra loro.

Ogni modifica a `sport-tv/` fa partire `.github/workflows/sport-tv-android.yml`,
che esegue le prove, compila i due APK e li lascia fra i file dell'esecuzione
(scheda Actions), insieme alle schermate generate dalle prove.

## Raccogliere i dati a mano

```bash
cd sport-tv
./gradlew :raccolta:run --args="--uscita eventi.json"
# opzioni: --precedente FILE_O_INDIRIZZO   --giorni 15
```

Stampa lo stato di ogni fonte e scrive il pacchetto. Se non raccoglie nulla non
scrive il file ed esce con codice 2, così resta valido quello precedente.

## Attivare il servizio online

1. Unire le modifiche al ramo principale: GitHub esegue i flussi programmati
   solo da lì.
2. In **Settings → Actions → General → Workflow permissions** scegliere «Read
   and write permissions» (serve a pubblicare il ramo `dati-sport-tv`).
3. Nella scheda **Actions** aprire «Sport in TV · dati» e premere **Run
   workflow** per la prima raccolta; poi parte da solo ogni 3 ore.
4. Verificare che risponda
   `https://raw.githubusercontent.com/GuidoCostalonga/GuidoCostalonga.github.io/dati-sport-tv/eventi.json`.

Finché il servizio non è attivo l'app funziona lo stesso: con la provenienza
«Automatica» (predefinita) legge direttamente le fonti.

### Limiti del piano gratuito

| Voce | Limite | Uso previsto |
| --- | --- | --- |
| Minuti di GitHub Actions, deposito pubblico | Illimitati | Circa 3 minuti a raccolta, 8 raccolte al giorno |
| Minuti di GitHub Actions, deposito privato | 2.000 al mese | Circa 720 al mese: rientra |
| Costi automatici | Nessuno senza metodo di pagamento; con un metodo registrato il limite di spesa predefinito è zero | Nessuna azione necessaria |
| Flussi programmati | Nei depositi pubblici GitHub li sospende dopo 60 giorni senza attività | Riattivarli dalla scheda Actions |
| Orario | Le esecuzioni programmate possono partire con qualche minuto di ritardo | Irrilevante per palinsesti aggiornati ogni 3 ore |
| Jolpica F1 | 500 richieste l'ora | 1 richiesta a raccolta |

---

## Licenze e attribuzioni

- Dati: Jolpica F1 (CC BY-NC-SA 4.0), openfootball (CC0 1.0). Palinsesti di Rai,
  Mediaset, DAZN e SuperTennis: marchi e contenuti dei rispettivi titolari,
  riportati solo come orari e titoli con rimando alle pagine ufficiali.
- Librerie: AndroidX, Jetpack Compose, Material Design 3 e icone Material
  (Apache 2.0, Google); Kotlin e kotlinx (Apache 2.0, JetBrains); OkHttp (Apache
  2.0, Square); in prova Robolectric (MIT) e JUnit (EPL 1.0).
- Icona dell'app disegnata per questo progetto.
