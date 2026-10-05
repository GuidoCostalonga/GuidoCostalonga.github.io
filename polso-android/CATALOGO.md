# Catalogo delle elaborazioni

Generato automaticamente dalle schede tecniche del codice (`motore/.../analisi`). Ogni elaborazione ha una prova automatica con il nome indicato. Totale: 59 elaborazioni, più le regole delle notifiche in fondo.

## Attività

### Passi nel periodo (`att.passi`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi (intervalli della fonte) |
| Formula o metodo | Somma per giorno locale degli intervalli della fonte scelta; intervalli a cavallo della mezzanotte ripartiti in proporzione alla durata. Totale, media, mediana, minimo, massimo, percentili (tipo 7), deviazione standard, coefficiente di variazione, media mobile a 7 giorni (almeno 4 giorni presenti). |
| Unità | passi |
| Minimo di osservazioni | 1 giorno con dati (percentili e dispersione da 5) |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | I passi dipendono dall'indossare l'orologio: un giorno con l'orologio sul comodino sembra un giorno poco attivo. |
| Prova automatica | `AnalisiAttivitaTest.passi` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Distanza (`att.distanza`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Distanza (intervalli della fonte) |
| Formula o metodo | Come per i passi, in metri; mostrata in km. |
| Unità | m |
| Minimo di osservazioni | 1 giorno con dati |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Distanza stimata dalla fonte (passo medio o GPS): non è una misura topografica. |
| Prova automatica | `AnalisiAttivitaTest.distanza` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Calorie attive (`att.calorie`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Calorie attive della fonte |
| Formula o metodo | Somma giornaliera delle calorie attive fornite dalla fonte. |
| Unità | kcal |
| Minimo di osservazioni | 1 giorno con dati |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Le calorie sono una stima della fonte, con errori individuali anche ampi. |
| Prova automatica | `AnalisiAttivitaTest.calorie` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Minuti attivi (`att.minuti`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Minuti attivi della fonte |
| Formula o metodo | Somma giornaliera dei minuti attivi forniti dalla fonte (solo importazione da file: Health Connect non ha questo tipo). |
| Unità | min |
| Minimo di osservazioni | 1 giorno con dati |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | La definizione di «minuto attivo» è della fonte. |
| Prova automatica | `AnalisiAttivitaTest.minuti` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Piani e dislivello (`att.piani`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Piani saliti della fonte |
| Formula o metodo | Somma giornaliera. |
| Unità | piani |
| Minimo di osservazioni | 1 giorno con dati |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | L'A58 non dichiara un barometro: il dato può mancare del tutto. |
| Prova automatica | `AnalisiAttivitaTest.piani` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Confronto con il periodo precedente (`att.confronto`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi |
| Formula o metodo | Media giornaliera dei passi nel periodo e nel periodo equivalente precedente (stessa durata) e dello stesso periodo dell'anno prima. Differenza assoluta e percentuale; intervallo di confidenza al 95% della differenza con bootstrap (2000 ricampionamenti, seme fisso). |
| Unità | passi/giorno |
| Minimo di osservazioni | 3 giorni con dati in entrambi i periodi |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Con pochi giorni l'intervallo è ampio: se comprende lo zero la differenza non è distinguibile dalla normale variabilità. |
| Prova automatica | `AnalisiAttivitaTest.confronto` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Andamento per giorno della settimana (`att.settimana`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi |
| Formula o metodo | Media dei passi per ciascun giorno della settimana nel periodo, con il numero di giorni usati. |
| Unità | passi |
| Minimo di osservazioni | 14 giorni con dati |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Con meno di 3 osservazioni per giorno la media è poco stabile; il numero è indicato accanto. |
| Prova automatica | `AnalisiAttivitaTest.settimana` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Obiettivo personale e serie di giorni attivi (`att.obiettivo`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi; obiettivo dalle impostazioni |
| Formula o metodo | Giorni con passi ≥ obiettivo sul totale dei giorni con dati; serie attuale e serie più lunga di giorni consecutivi con obiettivo raggiunto. |
| Unità | giorni |
| Minimo di osservazioni | 1 giorno con dati |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Un giorno senza dati interrompe la serie (non si può sapere se l'obiettivo era stato raggiunto). Oggi non interrompe la serie finché non è finito. |
| Prova automatica | `AnalisiAttivitaTest.obiettivo` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Record personali (`att.record`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi e distanza di tutto lo storico |
| Formula o metodo | Giorno con più passi, settimana di calendario (lunedì-domenica) con più passi fra quelle con 7 giorni di dati, mese con la media giornaliera più alta fra quelli con almeno 20 giorni di dati. |
| Unità | passi |
| Minimo di osservazioni | 1 giorno con dati |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Settimane e mesi incompleti non concorrono, per non confrontare totali su durate diverse. |
| Prova automatica | `AnalisiAttivitaTest.record` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Tendenza dei passi (`att.tendenza`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi |
| Formula o metodo | Pendenza di Theil–Sen (mediana delle pendenze fra tutte le coppie di giorni) e test di Mann–Kendall con correzione della varianza per l'autocorrelazione dei residui. |
| Unità | passi/giorno per giorno |
| Minimo di osservazioni | 14 giorni con dati |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Una tendenza descrive il periodo osservato e non prevede il futuro. p < 0,05 indica che una pendenza così netta sarebbe improbabile se non ci fosse alcuna tendenza. |
| Prova automatica | `AnalisiAttivitaTest.tendenza` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Distribuzione dei passi giornalieri (`att.distribuzione`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi |
| Formula o metodo | Istogramma in 8 classi di uguale ampiezza fra minimo e massimo del periodo. |
| Unità | giorni |
| Minimo di osservazioni | 7 giorni con dati |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | La forma dipende dal numero di giorni: con poche osservazioni è indicativa. |
| Prova automatica | `AnalisiAttivitaTest.distribuzione` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Passi per fascia oraria (`att.orario`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi con intervalli di al massimo 60 minuti |
| Formula o metodo | Media, per ciascuna ora del giorno, dei passi registrati in quell'ora nei giorni con dati. |
| Unità | passi/ora |
| Minimo di osservazioni | 7 giorni; durata mediana degli intervalli ≤ 60 minuti |
| Dati mancanti | Si considerano solo i giorni con almeno un dato di passi; un'ora senza intervalli in un giorno con dati conta come zero passi in quell'ora, perché le fonti non registrano intervalli vuoti. |
| Limiti interpretativi | Se la fonte fornisce solo totali giornalieri l'analisi non è possibile. |
| Prova automatica | `AnalisiAttivitaTest.orario` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Ore sedentarie (`att.sedentarieta`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi con intervalli ≤ 60 minuti e frequenza cardiaca (prova che l'orologio era indossato) |
| Formula o metodo | Fra le 8 e le 21, un'ora è sedentaria se l'orologio risulta indossato (almeno un battito registrato in quell'ora) e i passi sono meno di 250 (soglia usata dai promemoria «muoviti» dei contapassi). Media delle ore sedentarie per giorno. |
| Unità | ore/giorno |
| Minimo di osservazioni | 7 giorni con passi orari e frequenza cardiaca |
| Dati mancanti | Le ore senza battiti registrati sono escluse (orologio probabilmente non indossato). |
| Limiti interpretativi | Senza frequenza cardiaca non si distingue un'ora ferma da un'ora senza orologio: in quel caso l'analisi non viene fatta. Bici, nuoto e attività senza passi risultano sedentarie. |
| Prova automatica | `AnalisiAttivitaTest.sedentarieta` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Regolarità dell'attività (`att.regolarita`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi |
| Formula o metodo | Coefficiente di variazione dei passi giornalieri (più basso = più regolare) e quota di giorni entro ±25% della mediana del periodo. |
| Unità | % |
| Minimo di osservazioni | 14 giorni con dati |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Misura la costanza, non la quantità: una settimana sempre sedentaria è «regolare». |
| Prova automatica | `AnalisiAttivitaTest.regolarita` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

## Cuore

### Frequenza cardiaca giornaliera (`fc.giornaliera`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Campioni di frequenza cardiaca |
| Formula o metodo | Per ogni giorno: media dei campioni, minimo e massimo, numero di campioni e ore coperte. Riepilogo del periodo sulle medie giornaliere. |
| Unità | bpm |
| Minimo di osservazioni | 1 giorno con campioni |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | La media dei campioni pesa di più i momenti in cui l'orologio misura più spesso (per esempio durante gli allenamenti). Valori fuori 25–230 bpm sono esclusi come sospetti. |
| Prova automatica | `AnalisiCuoreTest.giornaliera` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Frequenza cardiaca durante il sonno (`fc.notturna`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Campioni di frequenza cardiaca e sessioni di sonno |
| Formula o metodo | Per ogni notte: media e minimo dei campioni caduti dentro la sessione di sonno principale. Il minimo notturno è un valore calcolato, non la «frequenza a riposo» della fonte. |
| Unità | bpm |
| Minimo di osservazioni | 3 notti con almeno 20 campioni ciascuna |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Dipende dalla frequenza di misura notturna impostata sull'orologio. |
| Prova automatica | `AnalisiCuoreTest.notturna` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Frequenza a riposo (fonte) (`fc.riposo`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Frequenza a riposo fornita dalla fonte |
| Formula o metodo | Riepilogo dei valori giornalieri e confronto fra la mediana degli ultimi 7 giorni e quella dei 28 giorni precedenti. |
| Unità | bpm |
| Minimo di osservazioni | 1 giorno (confronto: 4 valori recenti e 14 di riferimento) |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | È il valore calcolato dalla fonte con il suo metodo. |
| Prova automatica | `AnalisiCuoreTest.riposo` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Distribuzione dei battiti (`fc.distribuzione`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Campioni di frequenza cardiaca |
| Formula o metodo | Istogramma dei campioni del periodo in classi di 10 bpm e quota di campioni in ciascuna zona (se configurate). |
| Unità | % dei campioni |
| Minimo di osservazioni | 100 campioni |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Conta campioni, non minuti: se l'orologio misura più spesso sotto sforzo, le zone alte risultano sovrarappresentate. |
| Prova automatica | `AnalisiCuoreTest.distribuzione` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Tempo nelle zone cardiache durante gli allenamenti (`fc.zone`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Allenamenti, campioni cardiaci, FC massima (e a riposo per Karvonen) |
| Formula o metodo | Zone 1-5 al 50/60/70/80/90% della FC massima o della riserva cardiaca (metodo scelto nelle impostazioni). Il tempo fra due campioni distanti al massimo 2 minuti va alla zona del primo; buchi più lunghi non sono attribuiti. |
| Unità | min |
| Minimo di osservazioni | 1 allenamento con campioni; FC massima nota |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | La formula di Tanaka ha un errore tipico di circa 10 bpm: una FC massima misurata è preferibile. |
| Prova automatica | `AnalisiCuoreTest.zone` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Risposta cardiaca agli allenamenti (`fc.allenamento`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Allenamenti e campioni cardiaci (o media/massimo forniti dalla fonte) |
| Formula o metodo | Per ogni allenamento: FC media e massima dai campioni della sessione; se mancano, i valori forniti dalla fonte. Percentuale della FC massima di riferimento. |
| Unità | bpm |
| Minimo di osservazioni | 1 allenamento |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Con campioni radi la massima può essere sottostimata. |
| Prova automatica | `AnalisiCuoreTest.risposta` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Recupero dopo l'allenamento (`fc.recupero`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Campioni cardiaci entro ±15 s dalla fine dell'allenamento e da 60 s (e 120 s) dopo |
| Formula o metodo | Recupero a 1 minuto = FC alla fine − FC 60 secondi dopo (e a 2 minuti). Calcolato solo quando entrambi i campioni esistono. |
| Unità | bpm |
| Minimo di osservazioni | 1 allenamento con campioni adeguati |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Se ci si ferma prima di chiudere la sessione, o se l'orologio smette di misurare alla chiusura, il valore non è calcolabile o non è attendibile. |
| Prova automatica | `AnalisiCuoreTest.recupero` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Confronto con il tuo storico (`fc.storico`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Media giornaliera della frequenza cardiaca |
| Formula o metodo | Posizione della media del periodo rispetto alle medie giornaliere dei 90 giorni precedenti (percentile empirico). |
| Unità | percentile |
| Minimo di osservazioni | 3 giorni nel periodo e 30 nei 90 precedenti |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Le medie giornaliere risentono di quanto si è indossato l'orologio e di quanti allenamenti si sono fatti. |
| Prova automatica | `AnalisiCuoreTest.storico` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Variabilità cardiaca (HRV) della fonte (`fc.hrv`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | HRV RMSSD fornita dalla fonte |
| Formula o metodo | Riepilogo dei valori forniti. L'app non calcola la HRV dai campioni di frequenza: servirebbero gli intervalli fra battiti (RR), che la fonte non esporta. |
| Unità | ms |
| Minimo di osservazioni | 1 giorno |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Valori molto sensibili a orario e condizioni di misura: confrontare solo misure notturne o della stessa fascia. |
| Prova automatica | `AnalisiCuoreTest.hrv` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

## Sonno

### Durata del sonno (`sonno.durata`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni di sonno (e fasi, se presenti) |
| Formula o metodo | Minuti di sonno per notte: somma delle fasi di sonno quando la fonte le fornisce, altrimenti durata della sessione principale. Media, mediana, minimo, massimo, deviazione standard; tempo a letto; pisolini a parte. |
| Unità | min |
| Minimo di osservazioni | 1 notte |
| Dati mancanti | Le notti senza sessione registrata sono escluse; il sonno appartiene al giorno del risveglio. Non si stimano notti mancanti. |
| Limiti interpretativi | Senza fasi, il tempo sveglio a letto viene contato come sonno. |
| Prova automatica | `AnalisiSonnoTest.durata` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Orari e regolarità (`sonno.orari`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni di sonno |
| Formula o metodo | Orario medio di addormentamento, risveglio e punto medio con media circolare (gestisce la mezzanotte); regolarità come deviazione standard circolare in minuti. |
| Unità | hh:mm, min |
| Minimo di osservazioni | 3 notti |
| Dati mancanti | Le notti senza sessione registrata sono escluse; il sonno appartiene al giorno del risveglio. Non si stimano notti mancanti. |
| Limiti interpretativi | La sessione inizia quando la fonte rileva il sonno, non quando si va a letto. |
| Prova automatica | `AnalisiSonnoTest.orari` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Fasi del sonno (`sonno.fasi`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni con fasi (leggero, profondo, REM, veglia) |
| Formula o metodo | Minuti medi e percentuale sul sonno totale di ciascuna fase, sulle sole notti con fasi. |
| Unità | min, % |
| Minimo di osservazioni | 3 notti con fasi |
| Dati mancanti | Le notti senza sessione registrata sono escluse; il sonno appartiene al giorno del risveglio. Non si stimano notti mancanti. |
| Limiti interpretativi | La classificazione delle fasi da polso è una stima della fonte, meno precisa della polisonnografia. |
| Prova automatica | `AnalisiSonnoTest.fasi` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Risvegli ed efficienza (`sonno.risvegli`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni con fasi, compresa la veglia |
| Formula o metodo | Risvegli = passaggi da sonno a veglia seguiti da altro sonno; minuti svegli a letto; efficienza = sonno / tempo a letto. |
| Unità | numero, min, % |
| Minimo di osservazioni | 3 notti con fasi |
| Dati mancanti | Le notti senza sessione registrata sono escluse; il sonno appartiene al giorno del risveglio. Non si stimano notti mancanti. |
| Limiti interpretativi | I brevi risvegli sotto la risoluzione della fonte non vengono visti. |
| Prova automatica | `AnalisiSonnoTest.risvegli` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Giorni lavorativi e fine settimana (`sonno.weekend`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni di sonno |
| Formula o metodo | Durata e punto medio nelle notti che terminano da lunedì a venerdì e in quelle che terminano sabato e domenica. Lo scarto fra i punti medi è il cosiddetto «jet lag sociale». |
| Unità | min |
| Minimo di osservazioni | 3 notti feriali e 2 festive |
| Dati mancanti | Le notti senza sessione registrata sono escluse; il sonno appartiene al giorno del risveglio. Non si stimano notti mancanti. |
| Limiti interpretativi | Le festività infrasettimanali sono considerate giorni lavorativi. |
| Prova automatica | `AnalisiSonnoTest.weekend` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Scostamento dall'obiettivo personale (`sonno.obiettivo`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni di sonno; obiettivo dalle impostazioni |
| Formula o metodo | Differenza media fra sonno e obiettivo; notti sotto obiettivo; saldo cumulato degli ultimi 7 e 14 giorni (somma delle differenze sulle sole notti registrate). |
| Unità | min |
| Minimo di osservazioni | 1 notte |
| Dati mancanti | Le notti senza sessione registrata sono escluse; il sonno appartiene al giorno del risveglio. Non si stimano notti mancanti. |
| Limiti interpretativi | Il «debito» è solo un calcolo rispetto all'obiettivo che hai scelto: non è una misura clinica né un bisogno fisiologico accertato. |
| Prova automatica | `AnalisiSonnoTest.obiettivo` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Indice di regolarità del sonno (SRI) (`sonno.sri`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni di sonno di notti consecutive |
| Formula o metodo | Sleep Regularity Index (Phillips et al., 2017): percentuale di minuti in cui lo stato sonno/veglia coincide a 24 ore di distanza, riscalata in −100…100 (100 = orari identici). |
| Unità | punti |
| Minimo di osservazioni | 7 coppie di notti consecutive |
| Dati mancanti | Le notti senza sessione registrata sono escluse; il sonno appartiene al giorno del risveglio. Non si stimano notti mancanti. |
| Limiti interpretativi | Usa solo inizio e fine della sessione principale (non le fasi): i risvegli notturni non incidono. Le coppie con una notte mancante sono escluse. |
| Prova automatica | `AnalisiSonnoTest.sri` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Tendenza della durata del sonno (`sonno.tendenza`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni di sonno |
| Formula o metodo | Pendenza di Theil–Sen e test di Mann–Kendall corretto, sulla durata per notte. |
| Unità | min/settimana |
| Minimo di osservazioni | 14 notti |
| Dati mancanti | Le notti senza sessione registrata sono escluse; il sonno appartiene al giorno del risveglio. Non si stimano notti mancanti. |
| Limiti interpretativi | Descrive il periodo, non prevede il futuro. |
| Prova automatica | `AnalisiSonnoTest.tendenza` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

## Allenamenti

### Riepilogo degli allenamenti (`all.riepilogo`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni di allenamento |
| Formula o metodo | Numero di sessioni, durata totale e media, distanza totale (sulle sessioni che la riportano), calorie e dislivello forniti dalla fonte. |
| Unità | sessioni, min, km |
| Minimo di osservazioni | 1 sessione |
| Dati mancanti | Si usano solo le sessioni registrate. Distanza, calorie o dislivello assenti restano assenti: non contano come zero nelle medie. |
| Limiti interpretativi | Durata = fine − inizio della sessione, comprese le pause se la fonte non le separa. |
| Prova automatica | `AnalisiAllenamentiTest.riepilogo` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Frequenza e volume settimanale (`all.frequenza`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni di allenamento |
| Formula o metodo | Sessioni e minuti per settimana di calendario; confronto dei minuti settimanali con l'indicazione dell'Organizzazione mondiale della sanità (150-300 minuti di attività moderata, 2020). |
| Unità | min/settimana |
| Minimo di osservazioni | 1 sessione; periodo di almeno 7 giorni |
| Dati mancanti | Si usano solo le sessioni registrate. Distanza, calorie o dislivello assenti restano assenti: non contano come zero nelle medie. |
| Limiti interpretativi | Si contano solo gli allenamenti registrati: camminate e attività non avviate sull'orologio non entrano. L'intensità non viene verificata. |
| Prova automatica | `AnalisiAllenamentiTest.frequenza` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Distribuzione per sport (`all.sport`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni di allenamento |
| Formula o metodo | Numero di sessioni e minuti per sport. |
| Unità | sessioni, min |
| Minimo di osservazioni | 1 sessione |
| Dati mancanti | Si usano solo le sessioni registrate. Distanza, calorie o dislivello assenti restano assenti: non contano come zero nelle medie. |
| Limiti interpretativi | Il tipo di sport è quello scelto o riconosciuto dall'orologio. |
| Prova automatica | `AnalisiAllenamentiTest.sport` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Passo e velocità (`all.passo`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni con distanza (camminata, corsa, ciclismo, nuoto…) |
| Formula o metodo | Passo = durata / distanza (min/km); velocità = distanza / durata (km/h), per sessione e in media per sport. |
| Unità | min/km, km/h |
| Minimo di osservazioni | 1 sessione con distanza ≥ 100 m |
| Dati mancanti | Si usano solo le sessioni registrate. Distanza, calorie o dislivello assenti restano assenti: non contano come zero nelle medie. |
| Limiti interpretativi | Le pause incluse nella durata rallentano il passo. |
| Prova automatica | `AnalisiAllenamentiTest.passo` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Progressi in sessioni comparabili (`all.progressi`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni dello stesso sport con distanza simile |
| Formula o metodo | Si prende come riferimento la distanza mediana dello sport più frequente e si tengono le sessioni entro ±15% di quella distanza; tendenza del passo con Theil–Sen e Mann–Kendall (pendenza negativa = più veloce). |
| Unità | s/km per settimana |
| Minimo di osservazioni | 4 sessioni comparabili |
| Dati mancanti | Si usano solo le sessioni registrate. Distanza, calorie o dislivello assenti restano assenti: non contano come zero nelle medie. |
| Limiti interpretativi | Percorso, meteo e dislivello non sono controllati. |
| Prova automatica | `AnalisiAllenamentiTest.progressi` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Carico di allenamento (TRIMP di Edwards) (`all.carico`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sessioni con campioni cardiaci che coprono almeno l'80% della durata; FC massima |
| Formula o metodo | TRIMP di Edwards = Σ minuti in zona × numero della zona (1-5), zone come nella sezione Cuore. Somma per settimana. |
| Unità | unità arbitrarie |
| Minimo di osservazioni | 1 sessione con copertura cardiaca ≥ 80% |
| Dati mancanti | Si usano solo le sessioni registrate. Distanza, calorie o dislivello assenti restano assenti: non contano come zero nelle medie. |
| Limiti interpretativi | Indice descrittivo del volume e dell'intensità: non misura fatica, recupero o rischio di infortunio. |
| Prova automatica | `AnalisiAllenamentiTest.carico` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

## Altri parametri

### Saturazione (SpO₂) (`altri.spo2`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Misure di SpO₂ della fonte |
| Formula o metodo | Media giornaliera, minimo e massimo; numero di letture sotto il 90% (solo conteggio descrittivo). |
| Unità | % |
| Minimo di osservazioni | 1 giorno |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | La saturazione da polso è sensibile a movimento, freddo e posizione: una lettura bassa isolata è spesso un artefatto. Non è uno strumento diagnostico. |
| Prova automatica | `AnalisiAltriTest.spo2` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Frequenza respiratoria (`altri.respirazione`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Frequenza respiratoria della fonte |
| Formula o metodo | Riepilogo dei valori giornalieri e tendenza. |
| Unità | atti/min |
| Minimo di osservazioni | 1 giorno |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | HONOR Health non dichiara di condividere questo dato con Health Connect: in genere sarà assente. |
| Prova automatica | `AnalisiAltriTest.respirazione` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Temperatura cutanea (variazione) (`altri.temperatura`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Variazioni di temperatura cutanea della fonte |
| Formula o metodo | Riepilogo delle variazioni notturne rispetto al riferimento della fonte. |
| Unità | °C |
| Minimo di osservazioni | 1 giorno |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | È una variazione relativa, non la temperatura corporea. L'A58 non dichiara questo sensore. |
| Prova automatica | `AnalisiAltriTest.temperatura` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Stress (punteggio della fonte) (`altri.stress`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Punteggio di stress importato da file |
| Formula o metodo | Media giornaliera e distribuzione del punteggio fornito dalla fonte. |
| Unità | punti |
| Minimo di osservazioni | 1 giorno |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Punteggio proprietario stimato dalla variabilità cardiaca con un metodo non pubblico: non è una misura di stress psicologico né clinica. Health Connect non ha questo tipo di dato. |
| Prova automatica | `AnalisiAltriTest.stress` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### VO₂max (stima della fonte) (`altri.vo2max`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | VO₂max fornito dalla fonte |
| Formula o metodo | Ultimo valore e andamento. L'app non stima il VO₂max da sola. |
| Unità | ml/kg/min |
| Minimo di osservazioni | 1 valore |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Stima della fonte da corsa e frequenza cardiaca, con errore tipico di alcuni ml/kg/min. |
| Prova automatica | `AnalisiAltriTest.vo2max` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Peso (`altri.peso`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Peso da bilancia collegata (fonte) e diario manuale |
| Formula o metodo | Ultimo valore, variazione dall'inizio del periodo, tendenza in kg a settimana (Theil–Sen) con almeno 4 pesate. |
| Unità | kg |
| Minimo di osservazioni | 2 pesate |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Il peso oscilla di 1-2 kg in un giorno per acqua e pasti: conta la tendenza, non la singola pesata. |
| Prova automatica | `AnalisiAltriTest.peso` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Pressione arteriosa (dispositivo esterno) (`altri.pressione`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Misure di pressione dal diario o dalla fonte |
| Formula o metodo | Media, minimo e massimo di sistolica e diastolica nel periodo; numero di misure. |
| Unità | mmHg |
| Minimo di osservazioni | 1 misura |
| Dati mancanti | I giorni senza dati sono esclusi dai calcoli e mostrati come vuoti nei grafici; non vengono mai considerati zero. |
| Limiti interpretativi | Dato inserito da te o letto da un misuratore collegato: l'A58 non misura la pressione. Nessuna classificazione clinica. |
| Prova automatica | `AnalisiAltriTest.pressione` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

## Relazioni

### Attività e sonno della notte successiva (`rel.passi_sonno`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi e sonno |
| Formula o metodo | Correlazione di Spearman fra passi del giorno d con il sonno della notte che termina il giorno d+1. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher. |
| Unità | ρ (−1…1) |
| Minimo di osservazioni | 14 giorni appaiati |
| Dati mancanti | Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato. |
| Limiti interpretativi | Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze. |
| Prova automatica | `RelazioniTest` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Sonno e attività del giorno dopo (`rel.sonno_passi`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sonno e passi |
| Formula o metodo | Correlazione di Spearman fra sonno della notte che termina il giorno d con i passi dello stesso giorno d. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher. |
| Unità | ρ (−1…1) |
| Minimo di osservazioni | 14 giorni appaiati |
| Dati mancanti | Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato. |
| Limiti interpretativi | Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze. |
| Prova automatica | `RelazioniTest` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Allenamenti e frequenza a riposo del giorno dopo (`rel.allenamento_riposo`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Allenamenti, passi (per i giorni senza allenamento) e FC a riposo della fonte o FC notturna |
| Formula o metodo | Correlazione di Spearman fra minuti di allenamento del giorno d con la FC del giorno d+1. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher. |
| Unità | ρ (−1…1) |
| Minimo di osservazioni | 14 giorni appaiati |
| Dati mancanti | Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato. |
| Limiti interpretativi | Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze. |
| Prova automatica | `RelazioniTest` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Stress e sonno della notte successiva (`rel.stress_sonno`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Stress importato e sonno |
| Formula o metodo | Correlazione di Spearman fra stress del giorno d con il sonno della notte che termina il giorno d+1. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher. |
| Unità | ρ (−1…1) |
| Minimo di osservazioni | 14 giorni appaiati |
| Dati mancanti | Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato. |
| Limiti interpretativi | Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze. |
| Prova automatica | `RelazioniTest` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Regolarità del sonno e attività (`rel.regolarita_passi`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sonno e passi |
| Formula o metodo | Correlazione di Spearman fra scarto del punto medio della notte che termina il giorno d dal tuo punto medio abituale, con i passi del giorno d. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher. |
| Unità | ρ (−1…1) |
| Minimo di osservazioni | 14 giorni appaiati |
| Dati mancanti | Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato. |
| Limiti interpretativi | Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze. |
| Prova automatica | `RelazioniTest` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Peso e movimento (`rel.peso_movimento`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Peso (diario o bilancia) e passi |
| Formula o metodo | Correlazione di Spearman fra media dei passi di una settimana (almeno 4 giorni) con la variazione del peso medio rispetto alla settimana precedente. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher. |
| Unità | ρ (−1…1) |
| Minimo di osservazioni | 6 settimane appaiate |
| Dati mancanti | Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato. |
| Limiti interpretativi | Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze. |
| Prova automatica | `RelazioniTest` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Caffeina e sonno (`rel.caffeina_sonno`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Diario caffeina e sonno |
| Formula o metodo | Correlazione di Spearman fra caffeina del giorno d con il sonno della notte che termina il giorno d+1. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher. |
| Unità | ρ (−1…1) |
| Minimo di osservazioni | 14 giorni appaiati |
| Dati mancanti | Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato. |
| Limiti interpretativi | Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze. |
| Prova automatica | `RelazioniTest` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Alcol e frequenza notturna (`rel.alcol_fc`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Diario alcol, sonno e frequenza cardiaca |
| Formula o metodo | Correlazione di Spearman fra alcol del giorno d con la FC media durante il sonno della notte che termina il giorno d+1. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher. |
| Unità | ρ (−1…1) |
| Minimo di osservazioni | 14 giorni appaiati |
| Dati mancanti | Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato. |
| Limiti interpretativi | Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze. |
| Prova automatica | `RelazioniTest` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Sonno e umore (`rel.sonno_umore`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sonno e diario umore |
| Formula o metodo | Correlazione di Spearman fra sonno della notte che termina il giorno d con l'umore annotato il giorno d. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher. |
| Unità | ρ (−1…1) |
| Minimo di osservazioni | 14 giorni appaiati |
| Dati mancanti | Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato. |
| Limiti interpretativi | Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze. |
| Prova automatica | `RelazioniTest` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Sonno ed energia (`rel.sonno_energia`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sonno e diario energia |
| Formula o metodo | Correlazione di Spearman fra sonno della notte che termina il giorno d con l'energia annotata il giorno d. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher. |
| Unità | ρ (−1…1) |
| Minimo di osservazioni | 14 giorni appaiati |
| Dati mancanti | Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato. |
| Limiti interpretativi | Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze. |
| Prova automatica | `RelazioniTest` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Sonno misurato e riposo percepito (`rel.sonno_riposo`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sonno e diario riposo |
| Formula o metodo | Correlazione di Spearman fra sonno della notte che termina il giorno d con il riposo percepito annotato il giorno d. p-value con numerosità efficace corretta per l'autocorrelazione (Bartlett); q-value di Benjamini–Hochberg su tutte le relazioni calcolate nel periodo; intervallo di confidenza 95% con trasformata di Fisher. |
| Unità | ρ (−1…1) |
| Minimo di osservazioni | 14 giorni appaiati |
| Dati mancanti | Si usano solo i giorni in cui entrambe le grandezze sono presenti (coppie complete). Nessun valore viene stimato. |
| Limiti interpretativi | Associazione esplorativa: non dimostra un rapporto di causa ed effetto. Altri fattori (lavoro, malattia, meteo, stagione) possono influire su entrambe le grandezze. |
| Prova automatica | `RelazioniTest` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

## Indici descrittivi

### Indice di regolarità (`ind.regolarita`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Orari del sonno (almeno 7 notti) e passi giornalieri (almeno 14 giorni) |
| Formula o metodo | Media delle componenti disponibili. Sonno: 100 × max(0; 1 − DS circolare del punto medio / 120 min). Attività: 100 × max(0; 1 − CV dei passi / 100%). |
| Unità | punti 0-100 |
| Minimo di osservazioni | 1 componente |
| Dati mancanti | Una componente senza dati sufficienti viene esclusa e dichiarata; non viene mai posta a zero. |
| Limiti interpretativi | Indice descrittivo costruito da questa app per riassumere i tuoi dati: non è validato clinicamente e non va confrontato con punteggi di altre app. |
| Prova automatica | `IndiciTest.regolarita` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Indice di attività (`ind.attivita`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Passi (almeno 7 giorni) e, se ne hai mai registrati, allenamenti |
| Formula o metodo | Media delle componenti. Obiettivo: % di giorni con passi ≥ obiettivo. Allenamento: 100 × min(1; minuti settimanali medi / 150), riferimento OMS 2020. |
| Unità | punti 0-100 |
| Minimo di osservazioni | 1 componente |
| Dati mancanti | Una componente senza dati sufficienti viene esclusa e dichiarata; non viene mai posta a zero. |
| Limiti interpretativi | Indice descrittivo costruito da questa app per riassumere i tuoi dati: non è validato clinicamente e non va confrontato con punteggi di altre app. |
| Prova automatica | `IndiciTest.attivita` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

### Indice di recupero (`ind.recupero`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Sonno degli ultimi 7 giorni; FC a riposo (o minima notturna) e HRV degli ultimi 7 giorni rispetto ai 28 precedenti |
| Formula o metodo | Media delle componenti. Sonno: 100 × min(1; sonno medio / obiettivo). FC: 100 − min(100; max(0; scarto dalla mediana di riferimento in bpm) × 10). HRV: 100 × min(1; mediana recente / mediana di riferimento). Riferimenti calcolati sui 28 giorni precedenti la settimana finale del periodo. |
| Unità | punti 0-100 |
| Minimo di osservazioni | 2 componenti |
| Dati mancanti | Una componente senza dati sufficienti viene esclusa e dichiarata; non viene mai posta a zero. |
| Limiti interpretativi | Indice descrittivo costruito da questa app per riassumere i tuoi dati: non è validato clinicamente e non va confrontato con punteggi di altre app. |
| Prova automatica | `IndiciTest.recupero` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

## Qualità dei dati

### Copertura e qualità dei dati (`qual.copertura`)

| Voce | Contenuto |
|---|---|
| Dati richiesti | Tutto l'archivio |
| Formula o metodo | Per ogni grandezza: giorni con dati nel periodo, ore coperte in media, valori sospetti (fuori dall'intervallo plausibile), misure sovrapposte scartate, origini presenti. Per il sonno: notti registrate e notti con fasi. |
| Unità | giorni, % |
| Minimo di osservazioni | nessuno |
| Dati mancanti | I giorni senza dati sono dichiarati, non stimati. |
| Limiti interpretativi | Un dato presente non è necessariamente corretto: i controlli riconoscono solo valori impossibili o sovrapposti. |
| Prova automatica | `QualitaTest.copertura` |
| Se non disponibile | l'app mostra il motivo e i dati che mancano (es. «Servono almeno N giorni…», «Nessun dato di …») |

## Regole delle notifiche

- **Frequenza a riposo più alta del solito** (`fc_riposo`): Scatta se negli ultimi giorni completi (3 per impostazione) la FC a riposo della fonte, o in sua assenza la FC media durante il sonno, supera ogni giorno la tua mediana dei 28 giorni precedenti di almeno max(5 bpm; 2 deviazioni robuste). Richiede 14 giorni di riferimento.
- **Sonno molto più breve del solito** (`sonno_ridotto`): Scatta se le ultime notti (3 per impostazione), tutte registrate, durano ciascuna meno della tua mediana dei 28 giorni precedenti di almeno max(60 minuti; 2 deviazioni robuste). Richiede 14 notti di riferimento.
- **Attività dimezzata** (`passi_calati`): Scatta se la media dei passi degli ultimi 7 giorni completi (almeno 5 con dati) è inferiore alla metà della media dei 28 giorni precedenti (almeno 20 con dati).
- **Saturazione notturna più bassa del solito** (`spo2_bassa`): Scatta se la media giornaliera della SpO₂ negli ultimi giorni completi (3 per impostazione) è ogni giorno inferiore di almeno 3 punti alla tua mediana dei 28 giorni precedenti (14 giorni di riferimento). Non è una soglia clinica.
- **Nessun dato nuovo** (`dati_assenti`): Scatta se da 3 giorni non arriva nessuna misura da Health Connect o da altre fonti automatiche: di solito la sincronizzazione si è interrotta.

Criteri di «Cosa emerge dai tuoi dati»: Si mostrano solo: tendenze con p < 0,05 su almeno 14 giorni; confronti in cui l'intervallo di confidenza al 95% esclude lo zero; relazioni con q < 0,1 (Benjamini–Hochberg); serie di almeno 3 giorni con obiettivo raggiunto; avvisi quando la copertura è sotto il 50%.
