# Verifiche eseguite

Data: 4 ottobre 2026. Ambiente: contenitore Linux con JDK 21, Android SDK 35,
Gradle 8.14.3, accesso a Internet. Nessun emulatore Android disponibile (manca
la virtualizzazione KVM): l'app è stata eseguita con **Robolectric**, che fa
girare il codice Android vero sulla JVM, con database, allarmi e interfaccia
reali, e permette di fotografare le schermate.

## Riepilogo

| Verifica | Esito | Come |
| --- | --- | --- |
| Compilazione dell'app | Superata | `assembleDebug` (19 MB) e `assembleRelease` ottimizzato con R8 (1,8 MB) |
| Controllo del codice (lint) | Nessuna segnalazione | `lintDebug`; quattro avvisi iniziali corretti |
| Recupero di dati reali | Superata | Raccolta da riga di comando: 6 fonti su 6 rispondono; 481 eventi, 467 con trasmissione confermata, 14 da confermare |
| Visualizzazione di dati reali | Superata | Pacchetto reale caricato nell'app, schermate in `schermate/` |
| Deduplicazione e più canali | Superata | 5 eventi reali uniti fra fonti diverse (per esempio «Il Lombardia» su DAZN e Rai Sport, «Tre Valli Varesine» su DAZN e Rai 2); 10 partite di Serie A unite fra DAZN e calendario; prove dedicate |
| Identificativi stabili | Superata | Due raccolte reali a 20 minuti di distanza: 481 identificativi su 481 invariati |
| Orari e ora legale | Superata | Prove sul 25 ottobre 2026 (fine dell'ora legale, ora ripetuta) e sul 29 marzo 2026 (inizio, ora inesistente); conversione UTC per ogni fonte |
| Ricerca e filtri | Superata | Prove su ricerca per squadra, atleta, competizione e canale; filtri combinati per giorno, sport, piattaforma, accesso, dirette, preferiti; prova sull'app vera (ricerca «Sassuolo») |
| Preferiti | Superata | Squadre con nomi diversi nelle fonti, atleti, competizioni («Serie A» non include «Serie A Women»), eventi salvati |
| Consultazione offline | Superata | Prove dell'app con servizio irraggiungibile: i dati salvati restano consultabili |
| Dati mancanti o superati | Superata | Fonte guasta con pacchetto precedente: dati conservati e segnalati; senza precedente: errore dichiarato; primo avvio senza rete: «Dati non disponibili» distinto da «Nessun evento» |
| Notifiche e promemoria | Superata | Allarme pianificato 15 minuti prima, nessun duplicato, sostituzione al cambio d'orario, cancellazione per evento rinviato o annullato, evento spostato ad altro giorno ritrovato, promemoria automatici per i preferiti, cambio d'anticipo |
| Schermi piccoli e caratteri ingranditi | Superata dopo correzioni | Schermo da 320 dp con caratteri al 200%: corretti titolo tagliato, segnaposto della ricerca su quattro righe ed etichette della barra inferiore |
| Modalità scura | Superata | Schermata `08-scuro.png` |
| Servizio dati | Superata in simulazione | Passi del flusso eseguiti in locale con pacchetto precedente e pubblicazione su un deposito di prova |

## Prove automatiche

44 prove, tutte superate:

| Gruppo | Prove | Contenuto |
| --- | --- | --- |
| `raccolta` · ConnettoriTest | 7 | Lettura dei campioni reali di ogni fonte (`raccolta/src/test/resources/campioni/`) |
| `raccolta` · LogicaTest | 19 | Orari e ora legale, partecipanti, nomi di squadra, sport, rubriche, tipo di trasmissione, unione, identificativi, fonti guaste |
| `app` · AppLogicaTest | 10 | Filtri, preferiti, «in corso», formati, promemoria |
| `app` · PianificatoreTest | 2 | Allarmi Android: sostituzione, cancellazione, eventi passati |
| `app` · AppVeraTest | 5 | App completa con dati reali: programma, ricerca, dettaglio, promemoria, sezione da confermare, impostazioni, informazioni, schermo piccolo, modalità scura |
| `app` · AppSenzaDatiTest | 1 | Primo avvio senza rete |

Comando: `./gradlew :raccolta:test :app:testDebugUnitTest`.

## Impedimenti residui

1. **Nessuna prova su un telefono fisico o un emulatore**: l'ambiente non ha la
   virtualizzazione necessaria. Robolectric copre interfaccia, database e
   allarmi, ma non i ritardi reali di Android in risparmio energetico né
   l'apertura effettiva delle app RaiPlay, Mediaset Infinity e DAZN dai link.
   Da provare a mano dopo l'installazione.
2. **La versione ottimizzata (R8) non è stata eseguita**, solo compilata. Il
   file da installare consegnato è quindi la versione di prova, verificata.
3. **Il servizio online non è ancora attivo**: GitHub esegue i flussi
   programmati solo dal ramo principale. Fino ad allora l'app legge direttamente
   le fonti (provenienza «Automatica»). Istruzioni nel README.
4. **Risposta delle fonti ai server di GitHub non verificata**: Rai,
   Mediaset, DAZN e SuperTennis hanno risposto da questo ambiente; non è
   verificato che rispondano anche agli indirizzi dei server di GitHub
   Actions. Se una fonte li rifiutasse, il servizio la segna come non
   raggiungibile e l'app, in modalità «Automatica», la legge dal telefono.
5. **Copertura incompleta per limiti delle fonti**: Sky Sport, NOW, TV8, Cielo,
   Prime Video, Eurosport fuori da DAZN, HBO Max e Sportitalia non hanno un
   palinsesto pubblico accessibile senza aggirare protezioni (dettagli in
   `FONTI.md`).
6. **Diretta o replica**: Rai e Mediaset non lo indicano. L'app lo dichiara
   invece di indovinare, quindi per questi canali il filtro «Solo dirette» non
   include i loro eventi.
