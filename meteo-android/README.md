# Meteo FVG · app per Android

L'app Android della pagina [costalonga.org/meteo](https://costalonga.org/meteo/):
previsioni per tutti i 215 Comuni del Friuli Venezia Giulia, confronto fra i
modelli europei, riquadro ufficiale delle allerte della Protezione Civile
regionale e **widget animato** per la schermata iniziale del telefono.

Stessi dati, stesse fonti e stessa identità visiva della pagina: carattere
Manrope, blu, giallo, schede bianche con la barra gialla a sinistra.

---

## Che cosa contiene

| Riquadro | Che cosa mostra |
| --- | --- |
| **Adesso** | Temperatura, temperatura percepita, vento e umidità, con l'icona animata della condizione in corso |
| **Attendibilità a 24 ore** | Punteggio da 45 a 96 calcolato sulla dispersione fra ICON-D2 (Deutscher Wetterdienst), ECMWF IFS e Météo-France, con quanto dice ciascun modello fra sei ore |
| **Prossime ore** | Diciotto ore con temperatura, percepita e probabilità di pioggia |
| **Prossimi 7 giorni** | Minime, massime e probabilità di pioggia, con il dettaglio ora per ora del giorno scelto |
| **Da sapere** | Tre indicazioni pratiche su pioggia, vento e temperatura |
| **Allerta della Protezione Civile** | Il riquadro ufficiale pubblicato dalla Protezione Civile regionale per il Comune scelto |
| **Avvisi sul telefono** | L'interruttore che fa avvisare il telefono quando compare un'allerta per il Comune scelto |

I 215 Comuni sono raggruppati per provincia e si cercano scrivendo il nome.
Roveredo in Piano, Pordenone e Trieste restano raggiungibili con un tocco.
L'app ricorda il Comune scelto.

### I tre casi delle allerte

Come sulla pagina, l'app distingue tre situazioni e non confonde mai la terza
con la seconda:

1. **avviso pubblicato**: compare il riquadro ufficiale della Regione;
2. **nessun avviso in corso**: la Regione non sta pubblicando allerte per quel
   Comune, con l'ora della verifica;
3. **riquadro non raggiungibile**: il riquadro non si è caricato. L'app dice
   chiaramente che questo non significa assenza di allerte e rimanda al sito
   della Regione.

### L'avviso sul telefono

Nel riquadro **Avvisi sul telefono** c'è un interruttore, spento finché non lo
si accende. Acceso, il telefono legge il riquadro ufficiale della Regione circa
ogni ora, quando c'è rete, e manda una notifica **solo** quando compare
un'allerta nuova per il Comune scelto, o quando quella in corso cambia testo.

Le regole sono tre e valgono sempre:

- l'avviso non si ripete finché resta lo stesso;
- il riquadro vuoto non genera nessuna notifica: il silenzio della Regione non
  è una notizia;
- il riquadro non raggiungibile non genera nessuna notifica e non cancella
  quello che l'app sapeva: si riprova al giro dopo. Un riquadro che non si
  carica non è un'assenza di allerta.

Il testo della notifica è quello pubblicato dalla Regione, accorciato alla
prima frase ma non riscritto. Cambiando Comune, la memoria dell'avviso si
azzera: quello che si sapeva riguardava un altro paese.

Il controllo in sottofondo è affidato a WorkManager, la pianificazione
standard di Android, che lo rimanda quando il telefono dorme e lo riprende
dopo un riavvio. Da Android 13 le notifiche vanno concesse: se il permesso
viene negato l'interruttore resta spento, perché accenderlo sarebbe una
promessa che il telefono non può mantenere.

---

## Il widget animato

Il widget mostra il Comune scelto nell'app, la temperatura, la condizione, le
minime e le massime del giorno, la probabilità di pioggia e l'ora
dell'aggiornamento. L'icona si muove: il sole gira, le nuvole scorrono, le
gocce cadono, i fiocchi ruotano, il fulmine lampeggia, la nebbia si sposta.

### Come è fatta l'animazione

Un widget di Android non esegue codice proprio: vive nel processo del
lanciatore e può usare solo le viste che il sistema concede. Fra queste c'è il
`ViewFlipper`, che scorre da solo i propri figli. Qui i figli sono i sei
fotogrammi della condizione in corso: l'app decide quali disegni mettere, il
lanciatore li fa scorrere ogni 150 millesimi di secondo.

I sessanta fotogrammi (dieci condizioni per sei passaggi) sono disegni
vettoriali generati da `strumenti/genera_icone.py`, che calcola la geometria:
rotazione dei raggi, scorrimento delle nuvole, caduta delle gocce, giro dei
fiocchi. Nessuna immagine pesante, nessun disegno ripreso da altri.

| Condizione | Codici meteo | Movimento |
| --- | --- | --- |
| Sole | 0 di giorno | I raggi ruotano e pulsano |
| Luna | 0 di notte | Le stelle si accendono a turno |
| Sole o luna con nuvole | 1, 2 | La nuvola scorre davanti |
| Nuvole | 3 | Due nuvole scorrono in controtempo |
| Nebbia | 45, 48 | Le bande orizzontali si spostano |
| Pioviggine | 51, 53, 56, 61 | Due gocce leggere |
| Pioggia | 55, 57, 63, 65, 66, 67, 80, 81 | Quattro gocce sfalsate |
| Neve | 71, 73, 75, 77, 85, 86 | Tre fiocchi che scendono ruotando |
| Temporale | 82, 95, 96, 99 | Il fulmine lampeggia sulle gocce |

Giorno e notte arrivano dal campo `is_day` di Open-Meteo, non da un'ora fissa.

### Due formati

| Formato | Quando | Che cosa mostra |
| --- | --- | --- |
| Largo (4 x 2) | Larghezza da 180dp in su | Icona, Comune, temperatura, condizione, minime e massime, pioggia, ora, freccia per aggiornare |
| Compatto (2 x 2) | Larghezza inferiore | Icona, temperatura, Comune, minime e massime |

Il formato si sceglie da solo quando si ridimensiona il widget.

### Come si aggiorna

- da solo ogni mezz'ora, quando il sistema risveglia il widget;
- subito, toccando la freccia sul widget largo;
- appena si apre l'app: quello che l'app scarica finisce anche nel widget.

Mentre scarica, il widget mostra l'ultimo dato salvato, con l'ora a cui si
riferisce. Se la rete non risponde, il dato vecchio resta e al posto dell'ora
compare «dati non aggiornati»: il widget non mostra mai un numero inventato.

Per aggiungerlo: tocco lungo su una zona vuota della schermata iniziale →
**Widget** → **Meteo FVG**.

---

## Installazione sul telefono

Due strade. La prima è quella comoda.

### Dal telefono, in un passaggio

Apri questo indirizzo con il browser del telefono:

```
https://costalonga.org/meteo-android/apk/MeteoFVG.apk
```

Android chiede il permesso di installare da questa origine: concedilo al
browser che stai usando. Il file è `apk/MeteoFVG.apk` qui nel deposito, ed è
l'app compilata e firmata al momento dell'ultima modifica del codice.
L'indirizzo funziona da quando questa cartella è sul ramo principale.

### Dalla compilazione più recente

1. apri la scheda **Actions** del deposito, il flusso **App Android Meteo FVG**;
2. apri l'ultima esecuzione andata a buon fine;
3. scarica **MeteoFVG-apk** in fondo alla pagina ed estrai `app-pubblica.apk`;
4. aprilo sul telefono.

Serve Android 8.0 o successivo.

Gli aggiornamenti si installano sopra la versione precedente senza
disinstallare nulla, perché la firma resta la stessa.

---

## Fonti dei dati

- **Previsioni:** [Open-Meteo](https://open-meteo.com/), che distribuisce le
  corse dei modelli ECMWF IFS, ICON-D2 del Deutscher Wetterdienst e
  Météo-France. Fuso Europe/Rome, nessuna chiave di accesso.
- **Allerte:** riquadro ufficiale della Protezione Civile della Regione Friuli
  Venezia Giulia ([protezionecivile.fvg.it](https://www.protezionecivile.fvg.it/)),
  richiamato con il codice ISTAT del Comune.
- **Elenco dei Comuni:** codici ISTAT delle unità amministrative territoriali.
- **Coordinate:** centroide del municipio di ciascun Comune, standard EPSG:4326.

L'elenco dei 215 Comuni non è stato riscritto a mano: è estratto dalla pagina
costalonga.org/meteo e verificato voce per voce (nomi tutti distinti, codici
ISTAT tutti distinti, indirizzi tutti distinti, Gorizia 25, Pordenone 50,
Trieste 6, Udine 134).

---

## Com'è fatta

Applicazione Android nativa in Kotlin con Jetpack Compose. Nessun servizio
terzo, nessuna raccolta di dati, nessuna pubblicità.

I permessi sono questi e nessun altro: accesso a internet e lettura dello stato
della rete; notifiche, chieste solo se si accendono gli avvisi di allerta.
WorkManager, che pianifica il controllo in sottofondo, ne aggiunge altri tre di
servizio che Android non mostra a chi installa e che l'app non usa mai
direttamente: mantenimento della veglia, avvio dopo il riavvio del telefono e
servizio in primo piano.

```
meteo-android/
├── app/src/main/
│   ├── java/org/costalonga/meteofvg/
│   │   ├── MainActivity.kt          apertura, collegamenti della pagina
│   │   ├── MeteoViewModel.kt        stato, ricarica ogni quindici minuti
│   │   ├── data/
│   │   │   ├── Comuni.kt            i 215 Comuni (generato)
│   │   │   ├── Condizioni.kt        codici meteo, descrizioni, fotogrammi
│   │   │   ├── MeteoApi.kt          indirizzi di Open-Meteo
│   │   │   ├── MeteoRete.kt         le chiamate
│   │   │   ├── Previsione.kt        lettura, attendibilità, consigli
│   │   │   └── Preferenze.kt        Comune scelto, ultimo dato, memoria avvisi
│   │   ├── allerte/
│   │   │   ├── Allerte.kt           la pagina del riquadro della Regione
│   │   │   ├── LetturaAllerte.kt    la stessa lettura, senza mostrarla
│   │   │   ├── Decisione.kt         quando avvisare e che cosa scrivere
│   │   │   ├── Notifiche.kt         canale e notifica
│   │   │   └── Guardia.kt           il controllo ogni ora
│   │   ├── ui/                      tema, schede, elenco, allerte, avvisi
│   │   └── widget/                  il widget e il suo disegno
│   └── res/
│       ├── drawable/                60 fotogrammi + icone (generati)
│       ├── font/                    Manrope, cinque pesi
│       ├── layout/                  i due formati del widget
│       └── values/                  colori, testi, tema
├── app/src/test/                    le prove automatiche sulla logica
├── strumenti/genera_icone.py        genera i fotogrammi dell'animazione
├── apk/MeteoFVG.apk                 l'app pronta da installare
├── chiavi/                          la firma di servizio dell'APK
└── licenze/                         licenza del carattere Manrope
```

### Rifare i fotogrammi

```bash
python3 strumenti/genera_icone.py
```

Riscrive i sessanta disegni e l'icona dell'applicazione. I file in
`app/src/main/res/drawable/ic_meteo_*.xml` non si modificano a mano.

### Compilare e controllare a mano

```bash
export ANDROID_HOME=/percorso/dell/android-sdk
./gradlew testPubblicaUnitTest   # prove automatiche sulla logica
./gradlew assemblePubblica       # APK da installare
./gradlew lintPubblica           # controllo del codice
./gradlew assembleDebug          # APK per lo sviluppo
```

Il controllo del codice è un passo a sé, non parte della compilazione
dell'APK: alcune sue verifiche interrogano in rete l'indice delle librerie di
Google e farebbero cadere la compilazione quando la rete non risponde. Quelle
verifiche sono disattivate in `app/build.gradle.kts`, perché dicono solo se
esiste una versione più nuova di una libreria.

### Che cosa provano le prove automatiche

| File | Verifica |
| --- | --- |
| `ComuniTest` | 215 Comuni, conteggi per provincia, nomi e codici ISTAT e indirizzi tutti distinti, coordinate dentro la regione, ogni Comune raggiungibile dal proprio indirizzo |
| `CondizioniTest` | Ogni codice meteo ha una descrizione propria, giorno e notte cambiano disegno, sei fotogrammi distinti per condizione, nessun disegno usato due volte |
| `PrevisioneTest` | Calcolo dell'attendibilità, buchi nei dati, soglie dei tre consigli, soglie del giudizio |
| `DecisioneTest` | Quando l'avviso parte e quando tace, l'impronta che distingue un'allerta nuova da una già vista, il testo della notifica |

---

## La firma dell'APK

In `chiavi/servizio.jks` c'è una chiave **non segreta**, depositata di
proposito: alias `meteofvg`, password `meteofvg`. Serve a una cosa sola, che
tutti gli APK compilati qui abbiano la stessa firma, così gli aggiornamenti si
installano sopra i precedenti. Non protegge nulla e non va usata per
pubblicare l'app su un negozio di applicazioni.

Per una firma propria: genera una chiave, mettila fra i segreti del deposito e
compila la variante `release`, che resta apposta senza firma.

---

## Avvertenza

Le previsioni indicano probabilità, non certezze. Per le allerte ufficiali di
protezione civile fare sempre riferimento alla Protezione Civile della Regione
Friuli Venezia Giulia.

---

## Licenze

Il carattere Manrope è distribuito con licenza SIL Open Font License 1.1, il
cui testo è in `licenze/manrope-OFL.txt`. I cinque pesi inclusi sono ricavati
dal carattere variabile ufficiale.
