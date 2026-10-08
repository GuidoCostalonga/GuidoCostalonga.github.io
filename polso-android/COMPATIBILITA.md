# Matrice di compatibilità e verifica preliminare

Data della verifica: **5 ottobre 2026**. Telefono: HONOR Magic7 Pro. App: HONOR Health (`com.hihonor.health`).

Legenda dello stato:

- **Verificato**: letto direttamente in una fonte ufficiale o primaria citata.
- **Indiretto**: ricavato da un documento primario (per esempio i permessi dichiarati nel pacchetto di HONOR Health), ma non dichiarato da HONOR.
- ***DA VERIFICARE***: non confermato da fonti ufficiali; va controllato sul telefono.

## 1. Esito in breve

| Domanda | Risposta | Stato |
|---|---|---|
| Che cos'è l'«HONOR Choice Watch A58»? | Nessun orologio HONOR con codice A58 risulta nelle fonti consultate. HONOR Italia vende tre orologi CHOICE (Haylou Watch, Watch 2 Pro, Watch 2i) e tutti si collegano a HONOR Health. | ***DA VERIFICARE*** sul retro dell'orologio o in HONOR Health › Dispositivo › Informazioni |
| Sistema dell'orologio | Sistema proprietario, non Wear OS: nessuna app di terze parti sull'orologio. | Verificato (scheda GSMArena «Proprietary OS»); la parola «RTOS» non compare in fonti ufficiali |
| HONOR Health Kit per sviluppatori | Esiste (distinto da Huawei Health Kit), ma è **solo per la Cina continentale** e **solo per sviluppatori aziendali**, con revisione di ogni permesso fino a 15 giorni lavorativi. | Verificato |
| HONOR Health scrive in Health Connect? | Sì, secondo i 19 permessi di scrittura dichiarati dalla versione 17.15 in poi. Nessuna pagina ufficiale HONOR lo descrive. | Indiretto |
| Esportazione dell'archivio personale | Nessuna esportazione CSV, GPX o JSON documentata nell'app. Esiste il diritto alla portabilità (art. 20 GDPR) tramite richiesta a HONOR; formato non documentato. | Verificato (informativa privacy UE); formato ***DA VERIFICARE*** |
| Sincronizzazione diretta con Strava o Google Fit | Nessuna fonte ufficiale HONOR. L'unico ponte è Health Connect. | Non trovata |

**Integrazione scelta:** HONOR Health → Health Connect → Polso, in sola lettura, con la libreria ufficiale `androidx.health.connect:connect-client:1.1.0` (stabile dall'8 ottobre 2025). Health Connect è parte di Android dal 14, quindi è già presente sul Magic7 Pro. In alternativa: importazione da file in formati verificati (CSV di Polso documentato, CSV qualsiasi con mappatura delle colonne, GPX 1.1, TCX v2).

**Adattatore HONOR Health Kit:** non implementato e disattivato nell'app, con la spiegazione nella pagina «Fonti dati». Un'app personale in Italia non può ottenerne l'accesso.

## 2. Matrice per tipo di dato

Note comuni a tutte le righe via Health Connect:

- **Permessi di Polso:** quello di lettura del tipo, più «storico» (`READ_HEALTH_DATA_HISTORY`) per i dati anteriori a 30 giorni prima del primo consenso, più «secondo piano» (`READ_HEALTH_DATA_IN_BACKGROUND`) per gli aggiornamenti automatici.
- **Storico:** al massimo ciò che HONOR Health ha scritto in Health Connect. Se HONOR Health ricopia anche i dati precedenti all'attivazione della sincronizzazione è ***DA VERIFICARE***.
- **Frequenza di aggiornamento:** HONOR Health scrive quando sincronizza con l'orologio; Polso legge all'apertura, con il pulsante «Sincronizza» o con il lavoro periodico (3–24 ore, a discrezione di Android e MagicOS). Non è un monitoraggio continuo.

| Dato | Fonte | Metodo di acquisizione in Polso | Disponibilità | Dettaglio | Limiti |
|---|---|---|---|---|---|
| Passi | Accelerometro dell'orologio | Health Connect `StepsRecord` | Indiretto (WRITE_STEPS) | Intervalli; durata degli intervalli scritti da HONOR ***DA VERIFICARE*** | Profilo orario e sedentarietà solo se gli intervalli durano al massimo 60 minuti |
| Distanza | Accelerometro o GNSS | `DistanceRecord` | Indiretto | Intervalli | Stima, non misura topografica |
| Calorie attive e totali | Stima di HONOR | `ActiveCaloriesBurnedRecord`, `TotalCaloriesBurnedRecord` | Indiretto | Intervalli | Stime con errore individuale ampio |
| Minuti attivi | Intensità media-alta di HONOR | Health Connect: non disponibile; solo da file | Non disponibile via Health Connect | — | HONOR non dichiara WRITE_ACTIVITY_INTENSITY |
| Piani | — | `FloorsClimbedRecord` | Non dichiarato da HONOR | — | Letto se un'altra app lo scrive |
| Dislivello | Barometro (solo Watch 2 Pro) | `ElevationGainedRecord` | Indiretto (WRITE_ELEVATION_GAINED) | Intervalli | Dipende dal modello |
| Frequenza cardiaca | Sensore ottico (PPG) | `HeartRateRecord` (campioni) | Indiretto | Campioni; densità ***DA VERIFICARE*** | HONOR: misura «periodica, non in tempo reale» |
| Frequenza a riposo | Calcolo di HONOR | `RestingHeartRateRecord` | Indiretto | Giornaliera | Metodo di HONOR |
| Sonno e fasi | Accelerometro e PPG | `SleepSessionRecord` con fasi | Indiretto (WRITE_SLEEP) | Sessione; fasi ***DA VERIFICARE*** | Se le fasi mancano, Polso usa la durata della sessione e lo dichiara |
| SpO₂ | PPG | `OxygenSaturationRecord` | Indiretto | Letture puntuali | Periodiche o manuali; non diagnostiche |
| Stress | PPG (punteggio proprietario) | Solo da file | Non disponibile via Health Connect | — | Health Connect non ha questo tipo |
| Respirazione | Non dichiarata dagli orologi | `RespiratoryRateRecord` | Non disponibile | — | Letta se un'altra fonte la scrive |
| Temperatura cutanea | Non dichiarata | `SkinTemperatureRecord` | Non disponibile | — | Letta se un'altra fonte la scrive |
| Temperatura corporea | Inserimento manuale in HONOR Health | `BodyTemperatureRecord` | Indiretto | Puntuale | — |
| Allenamenti | Orologio | `ExerciseSessionRecord` + aggregati di Health Connect per distanza, calorie e dislivello della stessa app | Indiretto (WRITE_EXERCISE) | Sessione (tipo, inizio, fine) | Corrispondenza dei tipi di sport ***DA VERIFICARE*** |
| Percorsi GPS | GNSS dell'orologio o del telefono | `ExerciseRoute`, oppure file GPX o TCX | Non disponibile via Health Connect | — | HONOR non dichiara WRITE_EXERCISE_ROUTE |
| HRV | Non dichiarata | `HeartRateVariabilityRmssdRecord` | Non disponibile | — | Polso non la calcola: servirebbero gli intervalli RR |
| VO₂max | Stima di HONOR | `Vo2MaxRecord` | Indiretto | Puntuale | Stima proprietaria |
| Peso e massa grassa | Bilancia HONOR o inserimento manuale | `WeightRecord`, `BodyFatRecord`; diario di Polso | Indiretto | Puntuale | — |
| Pressione | Misuratore esterno o manuale | `BloodPressureRecord`; diario di Polso | Indiretto | Puntuale | L'orologio non misura la pressione |

## 3. Health Connect: fatti tecnici verificati

- Libreria stabile: `androidx.health.connect:connect-client:1.1.0` dell'8 ottobre 2025. Ultima anteprima: 1.2.0-alpha06 del 26 agosto 2026, non usata.
- Dal 14 Health Connect fa parte di Android.
- Senza il permesso dello storico, Polso legge solo fino a 30 giorni prima del primo consenso. Reinstallando l'app, la finestra riparte.
- Le modifiche si leggono con i «token delle modifiche», che comprendono anche le cancellazioni. Un token inutilizzato per 30 giorni scade, e Polso rilegge allora gli ultimi 30 giorni.
- Un'app installata dall'APK, fuori dal Play Store, può usare Health Connect se dichiara i permessi e la schermata informativa: è il caso di Polso. Il comportamento sul Magic7 Pro è ***DA VERIFICARE***.

## 4. Fonti consultate (5 ottobre 2026)

**HONOR Health Kit**

- Introduzione: https://developer.honor.com/cn/docs/11005/guides/introduction
- Paesi supportati, solo «中国大陆 / Chinese mainland»: https://developer.honor.com/cn/docs/11005/guides/support-regions
- «运动健康服务仅支持企业开发者», approvazione entro 15 giorni lavorativi: https://developer.honor.com/cn/docs/11005/guides/permission-apply
- Accordi da firmare: https://developer.honor.com/cn/docs/11005/guides/agreement-sign
- Libreria `com.hihonor.mcs:fitness-health:1.1.0.300`: https://developer.honor.com/cn/docs/11005/guides/version-history
- Ambiti dei permessi: https://developer.honor.com/cn/docs/11005/guides/scope

**HONOR Health e Health Connect**

- Permessi dichiarati da HONOR Health 17.20.0.303 (analisi Exodus Privacy): https://reports.exodus-privacy.eu.org/en/reports/com.hihonor.health/latest/
- Storico delle analisi Exodus, con i permessi Health Connect comparsi dalla 17.15: https://reports.exodus-privacy.eu.org/en/reports/search/com.hihonor.health/
- Scheda Google Play di HONOR Health: https://play.google.com/store/apps/details?id=com.hihonor.health
- Guida di terze parti (Strove, 25 novembre 2025, non ufficiale): https://support.strove.ai/en/articles/12928719-how-to-link-your-honor-health-app-to-strove

**Esportazione e privacy**

- Informativa privacy di HONOR Health per l'UE (Honor Technologies Germany GmbH, 25 ottobre 2023): https://agreement.itsec.hihonor.com/asm/agrFile/getHtmlFile?agrNo=1070&country=de&branchId=9&langCode=en-us
- Modulo per le richieste privacy: https://www.hihonor.com/global/privacy/feedback/

**Orologi**

- Orologi HONOR CHOICE in vendita in Italia: https://www.honor.com/it/honor-choice/
- Scheda GSMArena dell'HONOR Choice Watch: https://www.gsmarena.com/honor_choice_watch-12832.php

**Health Connect**

- Versioni della libreria: https://developer.android.com/jetpack/androidx/releases/health-connect
- Primi passi: https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started
- Lettura dei dati e limite dei 30 giorni: https://developer.android.com/health-and-fitness/guides/health-connect/develop/read-data
- Tipi di dato: https://developer.android.com/health-and-fitness/guides/health-connect/plan/data-types

**IA**

- Modelli LiteRT-LM (licenza Apache 2.0, impronte SHA-256 lette dall'interfaccia di Hugging Face): https://huggingface.co/litert-community
- Libreria `com.google.ai.edge.litertlm:litertlm-android:0.17.1` (Google Maven, 15 settembre 2026). L'interfaccia MediaPipe LLM Inference per Android è deprecata a favore di LiteRT-LM: https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
- Gemini, interfaccia compatibile OpenAI: https://ai.google.dev/gemini-api/docs/openai
- Limiti del piano gratuito di Gemini: https://ai.google.dev/gemini-api/docs/rate-limits

## 5. Cosa controllare sul telefono

1. Il codice del modello dell'orologio, sul retro o in HONOR Health › Dispositivo › Informazioni.
2. In HONOR Health, sotto Io › Impostazioni, la voce Health Connect e la sincronizzazione attiva.
3. In Impostazioni di Android › Health Connect › Autorizzazioni app: HONOR Health compare e scrive i dati.
4. Dopo la prima sincronizzazione di Polso, nella pagina «Fonti dati»:
   - quanti giorni di storico sono arrivati;
   - se il sonno ha le fasi;
   - quanto durano gli intervalli dei passi.
