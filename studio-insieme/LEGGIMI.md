# StudioInsieme

Copia pubblicata di **StudioInsieme**, l'app web installabile per lo studio di studenti con DSA
(disturbi specifici dell'apprendimento) e per i tutor. Indirizzo: https://costalonga.org/studio-insieme/

Qui dentro ci sono solo i file compilati. Sorgenti, prove e documentazione stanno nella cartella
`studio-insieme/` del repository `GuidoCostalonga/Servizio`: le modifiche si fanno lì.

## Come si aggiorna

1. Nel repository Servizio, dalla cartella `studio-insieme`: `npm ci` e poi `npm run build`.
2. Sostituire il contenuto di questa cartella con quello di `dist/`, conservando questo file.
3. Rimettere in `index.html` i due meta tag `robots` e `googlebot` con `noindex, nofollow`.

## Scelte di pubblicazione

- La pagina è volutamente **non indicizzata**: ha i meta tag `noindex, nofollow`, non sta in
  `sitemap.xml` e non è collegata dalla homepage. L'app non è ancora stata provata su dispositivi
  reali né con studenti e tutor.
- La cartella `ocr/` contiene il motore di riconoscimento del testo: l'app lo scarica solo su richiesta.
- Il servizio di collegamento fra dispositivi non è pubblicato: lo scambio con il tutor funziona
  tramite pacchetti su file.
- Nessuna chiave e nessun segreto: il servizio di intelligenza artificiale è facoltativo e usa la
  chiave personale di chi lo attiva.
