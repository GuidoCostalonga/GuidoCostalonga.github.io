# guidocostalonga.github.io

Homepage-vetrina che raccoglie guide, mappe e strumenti pubblicati su GitHub Pages.

Ogni progetto vive nel suo repository e viene servito in una sottocartella del
dominio (`/english-quest/`, `/interventi-famiglia-fvg/`, ...). Qui dentro stanno
solo la homepage, `roveredo-in-piano/` e `riunione-pc/`, che non hanno un
repository proprio.

In `meteo-android/` c'è invece l'app Android della pagina
[costalonga.org/meteo](https://costalonga.org/meteo/), con il widget animato per
la schermata iniziale del telefono: non è una pagina del sito, è codice che
GitHub compila da solo e da cui esce l'APK da installare. Le istruzioni stanno
in `meteo-android/README.md`.

In `sport-tv/` c'è **Sport in TV Italia**, l'app Android che mostra gli eventi
sportivi trasmessi legalmente in Italia oggi e nei 14 giorni successivi, con il
suo servizio dati gratuito su GitHub Actions. Istruzioni, fonti e verifiche
stanno in `sport-tv/README.md`, `sport-tv/FONTI.md` e `sport-tv/VERIFICHE.md`.

Per collegare un dominio personalizzato basta aggiungere un file `CNAME` con
dentro il dominio, e impostarlo in Settings → Pages.

@ginopizza

In `database-fvg/` c'è il **cruscotto del Database Friuli Venezia Giulia**
([costalonga.org/database-fvg](https://costalonga.org/database-fvg/)): 102 fogli
di dati pubblici con filtri per ogni colonna, scheda per comune, turismo,
amministratori, rifiuti, redditi e mappa. I dati stanno in `database-fvg/dati/`,
un file JSON per foglio, caricato solo quando serve.
