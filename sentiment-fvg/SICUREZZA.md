# Sicurezza, chiavi private e protezione dei dati

## 1. Regola d'oro: nessuna chiave nel cruscotto

Il cruscotto (`index.html`, `style.css`, `app.js`) viene scaricato nel browser
di chi lo apre: **tutto ciò che contiene è leggibile da chiunque**. Per questo:

| Dove sta | Cosa contiene | Chi la vede |
|---|---|---|
| Cruscotto (browser) | solo l'indirizzo del servizio (`data-servizio`) | chiunque |
| `servizio/.env` sul server | chiave Anthropic, token di X, Meta, Bluesky, Telegram, codici di accesso, segreti | solo il server |
| Cookie `sessione_monitor` | sessione firmata, valida 12 ore, `HttpOnly` e `Secure` | il browser lo invia, ma il codice JavaScript non può leggerlo |

Il flusso è sempre questo:

```
Browser dello staff ──(cookie di sessione)──► proxy HTTPS ──► servizio Python ──(chiavi private)──► Claude, X, Meta, Bluesky, Telegram
```

Il browser non parla mai direttamente con le piattaforme esterne né con il
motore di analisi: lo fa solo il servizio, che tiene le chiavi in memoria.

### Regole pratiche

1. Copiare `servizio/.env.esempio` in `servizio/.env` e compilarlo **solo sul server**.
   Il file `.env` è già escluso da Git (`servizio/.gitignore`).
2. Permessi del file: `chmod 600 servizio/.env`, proprietario l'utente di sistema che esegue il servizio.
3. Una chiave per ambiente (prova e produzione), con un limite di spesa impostato nella console Anthropic.
4. Generare i segreti con `python3 -c "import secrets; print(secrets.token_hex(32))"`.
5. Codici di accesso lunghi (almeno 20 caratteri casuali), uno per persona, da revocare togliendoli da `CODICI_ACCESSO` e riavviando.
6. Cambiare tutte le chiavi subito se il file `.env` finisce per errore in una chat, in una email o in un repository.
7. Mai incollare chiavi in `app.js`, in `index.html` o negli attributi dell'iframe.

## 2. Configurazione consigliata: stesso dominio

La soluzione più semplice e più sicura è servire cruscotto e servizio dallo
stesso indirizzo, ad esempio `https://atlantefvg.it/monitor/`. In questo modo non
servono regole CORS (Cross Origin Resource Sharing) e il cookie resta di prima parte.

Esempio di configurazione nginx:

```nginx
# Cruscotto statico
location /monitor/ {
    alias /var/www/monitor/;            # index.html, style.css, app.js
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' https://cdnjs.cloudflare.com; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'self' https://atlantefvg.it https://www.atlantefvg.it; base-uri 'none'; form-action 'self'" always;
    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy no-referrer always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
}

# Servizio Python (ascolta solo su 127.0.0.1)
location /monitor/api/ {
    proxy_pass http://127.0.0.1:8080/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;

    # Indispensabile per gli eventi in diretta (SSE)
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 1h;

    # Limite ai tentativi di accesso
    limit_req zone=accesso burst=5 nodelay;
}
```

Nel blocco `http` di nginx aggiungere la zona di limitazione:

```nginx
limit_req_zone $binary_remote_addr zone=accesso:10m rate=10r/m;
```

E nel cruscotto: `<body data-servizio="https://atlantefvg.it/monitor">`.

### Se il servizio sta su un sottodominio

Esempio: cruscotto su `atlantefvg.it`, servizio su `monitor.atlantefvg.it`.
Funziona perché i due indirizzi appartengono allo stesso sito e il cookie
`SameSite=Lax` viene inviato. Impostare nel `.env`:

```
ORIGINI_AMMESSE=https://atlantefvg.it,https://www.atlantefvg.it
```

Sconsigliato invece un servizio su un dominio del tutto diverso: molti browser
bloccano i cookie di terze parti dentro gli iframe e l'accesso non funzionerebbe.

## 3. Difese già presenti nel codice

| Rischio | Difesa |
|---|---|
| Furto delle chiavi | chiavi solo in `.env` sul server, mai inviate al browser |
| Accesso non autorizzato | codice personale, sessione firmata HMAC con scadenza, cookie `HttpOnly`, `Secure`, `SameSite=Lax` |
| Tentativi a raffica sul codice | ritardo di mezzo secondo per tentativo, più `limit_req` di nginx |
| Testi malevoli nei post (script nascosti) | il cruscotto inserisce i testi solo con `textContent`, mai come HTML; i link vengono accettati solo se `http` o `https` |
| Libreria dei grafici alterata | Chart.js caricato con versione fissa e controllo di integrità (`integrity`) |
| Dati finti iniettati nel servizio | `/api/ingresso` accetta solo richieste firmate con `SEGRETO_WEBHOOK` |
| Incorporamento abusivo del cruscotto | `frame-ancestors` nella CSP limita i siti che possono mostrarlo in un iframe |
| Indicizzazione | `noindex, nofollow` nella pagina |
| Documentazione delle API esposta | attiva solo con `MODALITA_SVILUPPO=true` |

## 4. Protezione dei dati personali (GDPR)

Monitorare contenuti pubblici è lecito, ma resta un trattamento di dati
personali (Regolamento UE 2016/679). Prima della messa in produzione:

1. **Titolare e finalità**: individuare chi è titolare del trattamento (gruppo
   consiliare, partito, ente) e scrivere la finalità: analisi aggregata della
   percezione pubblica per la comunicazione istituzionale e politica.
2. **Informativa**: pubblicarla sul sito del titolare, indicando fonti, finalità,
   tempi di conservazione e diritti degli interessati.
3. **Valutazione d'impatto** (articolo 35 del GDPR): consigliata, perché si tratta
   di monitoraggio sistematico su larga scala che può rivelare opinioni politiche
   (categoria particolare di dati, articolo 9). Da far valutare al responsabile
   della protezione dei dati o a un legale.
4. **Minimizzazione già applicata nel codice**: dell'autore si conserva solo
   un'impronta irreversibile (`autore_pseudonimo`), non nome né profilo; le
   menzioni si cancellano dopo `GIORNI_CONSERVAZIONE` (30 giorni di base).
5. **Nessuna schedatura**: il sistema misura temi e tendenze, non profila
   singoli cittadini. Non aggiungere funzioni che raccolgano elenchi di persone
   in base alle opinioni espresse.
6. **Fornitore di analisi**: verificare le condizioni d'uso commerciali di
   Anthropic e la sede del trattamento; i testi inviati per l'analisi sono
   contenuti pubblici, senza nomi degli autori.

## 5. Condizioni d'uso delle piattaforme

| Piattaforma | Cosa consente | Nota |
|---|---|---|
| Testate (RSS) | lettura dei feed pubblicati | conservare titolo, sommario e link, non l'articolo intero |
| Bluesky | ricerca pubblica con password per app | rispettare i limiti di frequenza |
| X | ricerca recente con abbonamento API a pagamento | verificare piano, costi e limiti sul portale sviluppatori |
| Facebook e Instagram | solo contenuti e commenti delle pagine amministrate | la ricerca libera dei post pubblici non è consentita |
| Telegram | canali pubblici tramite account e api_id | solo canali pubblici; niente gruppi privati |

Raccogliere dati con sistemi non autorizzati (lettura automatica delle pagine
web contro le condizioni d'uso) espone a blocchi e contestazioni: non farlo.
