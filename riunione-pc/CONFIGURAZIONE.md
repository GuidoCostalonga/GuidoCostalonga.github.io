# Configurazione della pagina «Riunione mensile PC Roveredo»

La pagina è un unico file, `index.html`, e funziona su GitHub Pages senza alcun
programma da installare. Per salvare i dati condivisi si appoggia a **Firebase
Firestore**, il servizio di Google: il piano gratuito «Spark» non richiede carta
di credito. Servono quindici minuti, una volta sola.

## 1. Crea il progetto Firebase

1. Vai su https://console.firebase.google.com ed entra con un account Google.
2. Premi **Aggiungi progetto**. Dai un nome, per esempio `pc-roveredo`.
3. Alla domanda su Google Analytics rispondi **no**: non serve.
4. Attendi la creazione, poi premi **Continua**.

## 2. Crea la base dati

1. Nel menu a sinistra apri **Build**, poi **Firestore Database**.
2. Premi **Crea database**.
3. Scegli la posizione **eur3 (europe-west)**: i dati restano in Europa.
4. Scegli **Avvia in modalità produzione**, poi **Attiva**.

## 3. Imposta i permessi

Apri la scheda **Regole**, cancella quello che c'è, incolla il testo seguente e
premi **Pubblica**.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    match /riunioni/{riunione} {
      allow read, write: if true;

      match /punti/{punto} {
        allow read, write: if true;
      }
    }

    match /impostazioni/{documento} {
      allow read, write: if true;
    }
  }
}
```

Queste regole aprono in lettura e scrittura soltanto le tre parti usate dalla
pagina: nessun altro dato può essere creato nel progetto.

## 4. Registra la pagina e copia la configurazione

> **Già fatto.** La pagina è collegata al progetto `pc-roveredo-2026`: i sei
> valori sono dentro `index.html`. Questo passaggio serve solo se un domani si
> cambia progetto Firebase.

1. Torna alla **panoramica del progetto** e premi l'icona **`</>`**, cioè «Web».
2. Dai un nome all'app, per esempio `riunione`, e premi **Registra app**.
   Non attivare Firebase Hosting: la pagina sta su GitHub Pages.
3. Compare un riquadro di codice con `const firebaseConfig = { ... }`.
   Copia i valori dentro le virgolette e riportali in `index.html`, nel blocco
   `var CONFIG_FIREBASE = { ... }` che si trova poco dopo il commento
   `configurazione della base dati`.

| Valore in Firebase | Riga in `index.html` |
|---|---|
| `apiKey` | `apiKey: ""` |
| `authDomain` | `authDomain: ""` |
| `projectId` | `projectId: ""` |
| `storageBucket` | `storageBucket: ""` |
| `messagingSenderId` | `messagingSenderId: ""` |
| `appId` | `appId: ""` |

Sono valori pubblici, previsti da Google per stare dentro una pagina web: non
sono password e non danno accesso all'account Google.

## 5. Pubblica

Il file viene servito da GitHub Pages all'indirizzo

    https://costalonga.org/riunione-pc/

raggiungibile anche come `https://guidocostalonga.github.io/riunione-pc/`, che
rimanda al dominio personalizzato del sito.

Da lì la pagina è raggiungibile da qualunque telefono o computer, senza account
e senza applicazioni: bastano il collegamento e la password.

## Password

La password di accesso è **Roveredo2026** e si cambia dalla pagina stessa, in
fondo, con «Cambia la password»: la nuova vale per tutti.

## Che cosa protegge la password, e che cosa no

La password tiene fuori chi capita sulla pagina per caso ed è più che sufficiente
per un ordine del giorno di squadra. Non è però una cassaforte: i valori di
configurazione sono visibili nel codice della pagina, come previsto dal servizio,
quindi una persona esperta che possieda il collegamento potrebbe leggere i dati
anche senza password. Per questo la pagina non contiene dati personali delicati,
non viene indicizzata dai motori di ricerca e il collegamento va diffuso solo
dentro il gruppo.

## Limiti del piano gratuito

Firestore gratuito consente ogni giorno 50.000 letture, 20.000 scritture e 1 GB
di archivio. La pagina usa gli aggiornamenti in tempo reale, che consumano una
lettura solo quando un dato cambia davvero: una squadra comunale resta molto
sotto la soglia, anche con tutti i volontari collegati durante la riunione.

## Copia di sicurezza

Dalla pagina, il pulsante **Esporta in Excel** salva l'ordine del giorno completo
della riunione aperta, spunte, volontari e note compresi.
