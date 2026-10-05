# Verifiche eseguite e da eseguire

Data: 5 ottobre 2026.

**Ambiente**

- Contenitore Linux con JDK 21, Android SDK 36, Gradle 8.14.3 e accesso a Internet.
- Nessun emulatore: manca la virtualizzazione.
- L'app è stata eseguita con **Robolectric**, che fa girare il codice Android vero sulla JVM, con database, Compose, WorkManager e grafica nativa.

Comando per ripetere tutto:

```
./gradlew :motore:test :app:testDebugUnitTest :app:assembleRelease :app:lintDebug
```

## 1. Controlli eseguiti nell'ambiente

| Controllo | Esito | Dettaglio |
|---|---|---|
| Prove del motore (JVM) | **110 superate su 110** | Statistiche, tempo, aggregazioni, 59 elaborazioni, importatori, esportazioni, cifratura, regole, IA |
| Prove dell'app (Robolectric) | **14 superate su 14** | Archivio Room, Health Connect simulato, backup, PDF, IA senza rete, navigazione, schermate |
| Compilazione | Superata | APK di prova e APK release firmato (39 MB, solo arm64); variante R8 compilata (28 MB) ma non consegnata |
| Lint | 0 errori | 3 avvisi residui: due falsi positivi sugli stream chiusi con `use`; un suggerimento di stile |
| Excel aperto da strumento esterno | Superata | Il file generato si apre con openpyxl (Python): 8 fogli, numeri come numeri, intestazioni corrette |
| Schermate | Prodotte | In `schermate/`, in modalità dimostrativa, catturate dall'app eseguita con Robolectric |
| Licenza e impronte dei modelli di IA | Verificate | Licenza Apache 2.0, file non soggetti ad autorizzazione, dimensioni e SHA-256 letti dall'interfaccia di Hugging Face |

### Prove richieste e dove si trovano

| Requisito | Prova |
|---|---|
| Formule e aggregazioni | `StatisticheTest`, `AggregazioneTest`, `AnalisiAttivitaTest`, `AnalisiCuoreTest`, `AnalisiSonnoTest`, `AnalisiAllenamentiTest`, `AnalisiAltriTest`, `RelazioniTest`, `IndiciTest` |
| Ogni elaborazione ha la sua prova | `CatalogoTest.ogniAnalisiHaUnaProvaCheEsiste`, che fallisce se una scheda cita una prova inesistente |
| Deduplicazione e importazione ripetuta | `ImportazioneTest.importazioneRipetutaStesseChiavi`, `ArchivioEFontiTest.importazioneRipetutaAggiornaSenzaDoppioni` |
| Giorni senza dati (mai zero) | `AggregazioneTest.giornoSenzaDatiNonEZero`, `AnalisiAttivitaTest.passi`, `EsportazioneTest.giorniSenzaDatiAssentiNonZero` |
| Cambio dell'ora | `TempoTest.giorniDelCambioOra` (23 e 25 ore), `TempoTest.ripartizioneNelGiornoDi25Ore`, `ImportazioneTest.oraLegaleNelFile` |
| Sonno oltre mezzanotte | `AnalisiSonnoTest.oltreMezzanotteAppartieneAlRisveglio`, `AnalisiSonnoTest.orari` |
| Fonti sovrapposte | `AggregazioneTest.fontiSovrappostePrevaleLaPriorita`, `AggregazioneTest.totaleGiornalieroEParzialiDellaStessaOrigineNonSiSommano` |
| Contatori cumulativi | `ImportazioneTest.cumulativiInIncrementiConAzzeramento`, `ImportazioneTest.cumulativoSenzaAzzeramento` |
| Permessi revocati | `ArchivioEFontiTest.permessiRevocati`, `ArchivioEFontiTest.permessoRevocatoDuranteLaLettura` |
| Modifiche e cancellazioni dalla fonte | `ArchivioEFontiTest.sincronizzazioneInizialeEModifiche`, `ArchivioEFontiTest.cancellazioneDallaFonteRimuoveAncheICampioni`, `ArchivioEFontiTest.tokenScadutoRileggeTrentaGiorni` |
| Storico di 30 giorni senza permesso | `ArchivioEFontiTest.senzaStoricoSoloTrentaGiorni` |
| Esportazioni | `EsportazioneTest` (Excel, CSV, provenienza), `ArchivioEFontiTest.rapportoPdf` (composizione delle pagine) |
| Cifratura e ripristino | `BackupTest` (passphrase errata, file alterato, sale diverso), `ArchivioEFontiTest.backupERipristinoVerificato` |
| IA non disponibile e rete assente | `ArchivioEFontiTest.iaNonDisponibileEReteAssente`, `IaTest` (numeri inventati, parole vietate, risposte senza modello) |
| Notifiche | `RegoleTest` (persistenza, giorno mancante, storico insufficiente, ore silenziose, intervallo minimo) |
| Navigazione | `NavigazioneTest.primoAvvioDemoEPagine`: visita tutte le 13 pagine dal menu in modalità dimostrativa |

## 2. Da verificare sui tuoi dispositivi

Nessuna integrazione è dichiarata verificata senza prove sul telefono reale.

1. **Modello dell'orologio.** Il codice «A58» non corrisponde a nessun orologio HONOR nelle fonti consultate.
2. **HONOR Health scrive davvero in Health Connect.** Va controllato con la propria versione e il proprio account. Il riscontro è indiretto: permessi dichiarati più una guida non ufficiale.
3. **Quali dati arrivano e con quale dettaglio:**
   - durata degli intervalli dei passi;
   - densità dei campioni cardiaci;
   - fasi del sonno;
   - tipi di sport;
   - quanto storico viene copiato.
4. **Permessi «storico» e «secondo piano»** concessi sul Magic7 Pro a un'app installata da APK.
5. **Aggiornamento automatico** con le restrizioni di batteria di MagicOS.
6. **Scrittura del PDF** con `PdfDocument`: Robolectric non lo simula, quindi la composizione delle pagine è provata su bitmap, non il file finale.
7. **IA locale:**
   - download;
   - avvio di LiteRT-LM su GPU o CPU;
   - tempi di risposta;
   - qualità dell'italiano dei tre profili.

   Nell'ambiente c'è solo il controllo che il codice compili e che, senza modello, l'app risponda con il calcolo.
8. **IA online:** chiave, limiti del piano gratuito e risposte reali del servizio.
9. **Notifiche** reali: permesso di Android, canale e testo.
10. **Blocco biometrico** con impronta o PIN.
11. **Backup su Google Drive** tramite il selettore di sistema e ripristino su un secondo telefono.
12. **Accessibilità:** testo ingrandito, TalkBack, tema scuro su schermo reale.

## 3. Limiti residui noti

- **Dati non disponibili da HONOR via Health Connect.** Stress, HRV, respirazione, temperatura cutanea, minuti attivi e percorsi GPS non risultano condivisi. Le relative analisi restano «non disponibili» con il motivo.
- **Variante R8 non consegnata.** È più leggera (28 MB) ma non è stata eseguita nelle prove; l'APK release consegnato non è offuscato ed è identico al codice provato.
- **Grafici disegnati in proprio** con Canvas di Compose, invece di una libreria esterna, per controllare la resa dei giorni mancanti.
- **Ripristino del backup.** Sostituisce l'intero archivio reale e richiede la stessa passphrase: non c'è recupero se la si dimentica.
- **Chiave dell'IA online.** È cifrata con Android Keystore e non entra nel backup: dopo un ripristino va reinserita.
