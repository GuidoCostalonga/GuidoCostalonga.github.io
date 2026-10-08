# Collegamento e importazione dei dati

## 1. Collegamento automatico (consigliato): HONOR Health › Health Connect › Polso

1. **HONOR Health.** Apri Io › Impostazioni › Health Connect e attiva «Sincronizza con Health Connect». Su alcune versioni la voce si chiama «Condivisione dati». Serve l'accesso con l'account HONOR.
2. **Android.** Apri Impostazioni › Health Connect › Autorizzazioni app › HONOR Health e controlla che possa scrivere i dati.
3. **Polso.** Apri Menu › Fonti dati e importazione › «Concedi i permessi». Concedi anche:
   - **Accesso allo storico**, per leggere oltre 30 giorni;
   - **Accesso in background**, per gli aggiornamenti automatici.
4. Premi «Sincronizza ora». La prima lettura importa tutto lo storico consentito; le successive leggono solo le modifiche, cancellazioni comprese.
5. Facoltativo, sul Magic7 Pro: in Impostazioni › Batteria › Avvio app lascia a Polso la gestione manuale con «Esegui in background». MagicOS altrimenti può ritardare gli aggiornamenti automatici.

La pagina «Fonti dati» mostra:

- i permessi concessi;
- l'ultima sincronizzazione riuscita e l'intervallo letto;
- gli errori;
- le origini presenti, con la loro priorità.

Quando telefono e orologio registrano la stessa cosa, conta l'origine più in alto, mai la somma.

## 2. Formato CSV di Polso

- File di testo UTF-8, separatore `;` o `,` riconosciuto in automatico.
- Prima riga di intestazione.
- Colonne obbligatorie: `metrica`, `inizio`, `valore`.

| Colonna | Contenuto |
|---|---|
| `metrica` | Codice: passi, distanza, calorie_attive, calorie_totali, minuti_attivi, piani, dislivello, fc, fc_riposo, hrv_rmssd, spo2, respirazione, temp_cute_delta, temp_corpo, stress, vo2max, peso, grasso, pa_sistolica, pa_diastolica; oppure `sonno` o `allenamento` |
| `inizio`, `fine` | `2026-10-01T08:00:00+02:00` (con scarto), `2026-10-01 08:00` (ora locale), `01/10/2026 08:00` oppure secondi o millisecondi Unix |
| `valore` | Numero con punto o virgola decimale. Per `sonno` è la fase (sveglio, leggero, profondo, rem, sonno); per `allenamento` è la distanza in metri (facoltativa) |
| `unita` | Facoltativa. Conversioni accettate: km→m, kJ→kcal, s o h→min, g→kg |
| `tipo_valore` | `incremento` (quantità nell'intervallo; per i passi serve `fine`), `cumulativo` (contatore crescente, convertito in incrementi) o `istantaneo` |
| `origine` | Facoltativa: app o dispositivo d'origine |
| `id` | Facoltativo: identificativo stabile, evita doppioni se il file viene reimportato |
| `sport`, `calorie`, `dislivello` | Facoltative, per gli allenamenti |

Esempio:

```
metrica;inizio;fine;valore;unita;tipo_valore;origine;id
passi;2026-10-01 08:00;2026-10-01 09:00;1200;;;orologio;p1
fc;2026-10-01T08:30:00+02:00;;72;bpm;;orologio;f1
passi;2026-10-02 12:00;;1500;;cumulativo;telefono;
sonno;2026-10-01 23:30;2026-10-02 01:00;leggero;;;;
sonno;2026-10-02 01:00;2026-10-02 06:30;profondo;;;;
allenamento;2026-10-01 18:00;2026-10-01 18:40;6500;;;;
```

Regole applicate:

- **Ore senza scarto.** Valgono come ora locale del fuso scelto (Europe/Rome). Nell'ora ripetuta di fine ottobre si prende la prima occorrenza.
- **Righe non valide.** Vengono scartate e segnalate una per una nel registro; le altre si importano.
- **Reimportazione.** Lo stesso file reimportato aggiorna i dati e non crea doppioni.
- **Fasi del sonno.** Fasi consecutive, con pause fino a 30 minuti, formano una sola notte.
- **Contatori cumulativi.** Un valore che scende indica un azzeramento; la prima lettura del giorno vale dalla mezzanotte. Le letture non vengono mai sommate fra loro.

## 3. CSV di altro formato (mappatura)

Serve per file esportati da altre app o per un archivio ottenuto da HONOR con una richiesta privacy, che non ha un formato pubblicato. Si sceglie:

- la grandezza;
- le colonne di data, fine e valore, mostrate in anteprima;
- l'unità del file;
- il formato della data;
- il tipo di valore.

## 4. Tracce GPX e TCX

- **GPX 1.1.** Ogni traccia (`trk`) diventa un allenamento. Distanza e dislivello sono **calcolati** dal percorso e il titolo lo dichiara:
  - distanza: formula dell'emisenoverso;
  - dislivello: soglia di 3 m per non sommare il rumore dell'altimetro.

  La frequenza cardiaca si legge dall'estensione Garmin `gpxtpx:hr`.
- **TCX v2.** Durata, distanza e calorie si leggono dai giri (`Lap`), come scritti dal file; la frequenza cardiaca da `HeartRateBpm`.

## 5. Richiesta dei dati a HONOR (una tantum)

L'informativa privacy di HONOR Health per l'UE prevede il diritto alla portabilità: https://www.hihonor.com/global/privacy/feedback/. In alternativa si può provare Impostazioni › Centro account › Centro privacy › Richiedi i tuoi dati, se presente sul telefono.

Formato e tempi di consegna non sono documentati (***DA VERIFICARE***). Ricevuto il file, lo si importa con «CSV di altro formato».
