# Polso

App Android per uso personale che raccoglie i dati dello smartwatch HONOR (tramite HONOR Health e Health Connect) sul telefono HONOR Magic7 Pro. I dati diventano statistiche, grafici, confronti, relazioni, riepiloghi e risposte in italiano.

**Caratteristiche**

- Nessuna pubblicità, nessun abbonamento, nessun server dell'app.
- Dati solo sul telefono, salvo quando sei tu a esportarli o a fare il backup.
- Sistema metrico, fuso orario Europe/Rome modificabile.

**Documenti**

| Documento | Contenuto |
|---|---|
| [COMPATIBILITA.md](COMPATIBILITA.md) | Verifica preliminare e matrice dei dati, con fonti ufficiali e data |
| [CATALOGO.md](CATALOGO.md) | Le 59 elaborazioni con dati richiesti, formula, unità, minimo, mancanti, limiti e prova; generato dal codice |
| [IMPORTAZIONE.md](IMPORTAZIONE.md) | Collegamento a HONOR Health e formati dei file importabili |
| [VERIFICHE.md](VERIFICHE.md) | Prove eseguite, prove da fare sul telefono e limiti residui |
| [apk/](apk/) | APK installabile |
| [schermate/](schermate/) | Schermate in modalità dimostrativa |

## Cosa è stato verificato prima di scegliere l'integrazione

- **HONOR Health Kit:** esiste, ma è aperto solo a sviluppatori aziendali e solo per la Cina continentale. Per un'app personale in Italia non è utilizzabile. Nell'app la fonte compare come «Non utilizzabile», con il motivo; non c'è codice che finga di collegarsi.
- **HONOR Health → Health Connect:** è l'unica strada ufficiale praticabile. Lo indicano i permessi di scrittura dichiarati da HONOR Health dalla versione 17.15, ma va confermata sul telefono.
- **Esportazione dell'archivio da HONOR Health:** non esiste un'esportazione documentata. Si può chiedere una copia dei dati per la portabilità GDPR; il formato non è pubblicato.
- **«HONOR Choice Watch A58»:** nessun orologio HONOR risulta con questo codice. Vanno controllati il modello effettivo e i sensori (vedi COMPATIBILITA.md).

## Installazione

1. Copia `apk/Polso.apk` sul telefono e aprilo. Consenti l'installazione da questa fonte quando Android lo chiede.
2. Al primo avvio scegli «Inizia con i miei dati», oppure «Prova con dati dimostrativi»: archivio separato e ben etichettato.
3. Segui IMPORTAZIONE.md per collegare HONOR Health e Health Connect.

## Cosa fa

**Sezioni:** Panoramica, Attività, Cuore, Sonno, Allenamenti, Altri parametri, Relazioni, Indici e qualità, Diario, Assistente. Più le pagine di servizio: Catalogo delle analisi, Fonti dati e importazione, Esporta e backup, Impostazioni.

- **Dashboard.** Comprende:
  - riepilogo del giorno;
  - selettore del periodo (giorno, settimana, mese, anno, personalizzato) con periodi parziali segnalati;
  - schede riordinabili;
  - grafici interattivi (tocco per leggere il valore);
  - confronti con il periodo precedente e con l'anno prima;
  - calendario per intensità;
  - pagina di dettaglio con «Come è calcolato»;
  - qualità dei dati;
  - «Cosa emerge dai tuoi dati»;
  - elenco delle analisi disponibili e dei requisiti mancanti.
- **Provenienza sempre distinta.** Etichette con icona e testo: «Misura della fonte», «Valore calcolato», «Interpretazione dell'IA». I giorni senza dati sono vuoti, mai zero.
- **59 elaborazioni deterministiche.** Coprono:
  - statistica descrittiva, tendenze robuste (Theil–Sen, Mann–Kendall corretto) e confronti con bootstrap;
  - obiettivi e serie, profilo orario e sedentarietà (solo con dati adatti);
  - zone cardiache (due metodi dichiarati), recupero, carico TRIMP;
  - indici di regolarità del sonno (SRI), jet lag sociale, debito rispetto all'obiettivo;
  - relazioni con correzione per autocorrelazione e confronti multipli;
  - indici descrittivi con componenti consultabili.
- **Diario facoltativo.** Peso, circonferenze, pressione da dispositivo esterno, alimentazione, acqua, caffeina, alcol, umore, energia, riposo percepito, sintomi, eventi, farmaci (solo diario), note.
- **IA.**
  - **Senza modello:** risponde il calcolo.
  - **Modello locale gratuito**, scaricato su richiesta, licenza Apache 2.0, impronta SHA-256 verificata. Tre profili:
    - Qwen3 0,6B, 614 MB;
    - Qwen2.5 1,5B, 1,6 GB;
    - Gemma 4 E2B, 2,6 GB, consigliato sul Magic7 Pro.
  - **Servizio online facoltativo** (Gemini o altro compatibile OpenAI): disattivato all'inizio, con consenso a ogni invio e testo mostrato prima.
  - **Controllo delle risposte.** Il modello riceve solo risultati già calcolati. Ogni numero della risposta deve comparire nei dati; diagnosi e indicazioni su farmaci sono scartate.
- **Notifiche solo per variazioni importanti.** Sono cinque regole fisse e configurabili, basate sul tuo storico, con persistenza di più giorni, intervallo minimo e ore silenziose. Ogni notifica dice dato, periodo e motivo.
- **Esportazioni.** PDF, Excel con fogli distinti, CSV; periodo e contenuti a scelta.
- **Backup.**
  - Cifrato AES-256-GCM con passphrase (PBKDF2, 310 000 iterazioni), ripristinabile su qualsiasi telefono e verificato dopo la scrittura e dopo il ripristino.
  - Cloud senza account aggiuntivi: nel selettore di sistema scegli Google Drive.
  - Cancellazione completa e blocco con impronta facoltativo.

## Quali dati escono dal telefono

| Funzione | Cosa esce | Dove |
|---|---|---|
| Download del modello | Nessun dato personale | huggingface.co |
| IA online (solo se attivata e confermata) | Il testo mostrato nella finestra di conferma | Indirizzo configurato (predefinito: Google Gemini) |
| Backup | File cifrato | Dove lo salvi tu |
| Esportazioni | File non cifrati | Dove li salvi o condividi tu |

Il backup automatico di Android è disattivato per l'app: i dati sanitari non finiscono nel cloud di Google senza una tua scelta. Nessun dato sanitario è scritto nei registri di sistema.

## Compilazione e aggiornamenti

Requisiti: JDK 17 o successivo, Android SDK con piattaforma 36.

```
cd polso-android
echo "sdk.dir=/percorso/android-sdk" > local.properties
./gradlew :motore:test :app:testDebugUnitTest     # prove
./gradlew :app:assembleRelease                    # APK in app/build/outputs/apk/release/
```

**Struttura**

- `motore/`: Kotlin puro, senza Android. Modelli, aggregazioni, catalogo delle analisi, regole, importatori, Excel e CSV, cifratura, fatti per l'IA.
- `app/`: interfaccia Compose (Material 3), archivio Room, DataStore, WorkManager, Health Connect, LiteRT-LM, PDF, backup.

Il flusso `.github/workflows/polso-android.yml` esegue prove, lint e APK a ogni modifica di `polso-android/` e lascia l'APK fra i file dell'esecuzione.

**Versioni principali**

| Componente | Versione |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.3.21 |
| Compose BOM | 2025.10.01 |
| Room | 2.8.3 |
| Health Connect | 1.1.0 |
| LiteRT-LM | 0.17.1 |
| Robolectric | 4.17 |

Kotlin 2.3 è necessario per leggere le classi di LiteRT-LM. La firma degli APK usa una chiave non segreta depositata in `chiavi/` (vedi `chiavi/LEGGIMI.md`), così gli aggiornamenti si installano sopra la versione precedente senza perdere i dati.

Per aggiornare: aumenta `versionCode` e `versionName` in `app/build.gradle.kts`, ricompila e installa sopra. Lo schema del database ha versione 1 ed è esportato in `app/schemi/`; le modifiche future richiedono una migrazione Room.

## Avvertenza

I dati dello smartwatch sono stime per il benessere. Polso non fa diagnosi, non modifica terapie e non sostituisce il medico.
