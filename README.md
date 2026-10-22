# guidocostalonga.github.io

Homepage-vetrina che raccoglie guide, mappe e strumenti pubblicati su GitHub Pages.

Ogni progetto vive nel suo repository e viene servito in una sottocartella del
dominio (`/english-quest/`, `/interventi-famiglia-fvg/`, ...). Qui dentro stanno
solo la homepage, `roveredo-in-piano/`, `riunione-pc/` e `trame/`, che non hanno
un repository proprio. Le istruzioni di `trame/` (fornitori di dati e di
percorso, limiti, verifiche) stanno in `trame/README.md`.

In `meteo-android/` c'è invece l'app Android della pagina
[costalonga.org/meteo](https://costalonga.org/meteo/), con il widget animato per
la schermata iniziale del telefono: non è una pagina del sito, è codice che
GitHub compila da solo e da cui esce l'APK da installare. Le istruzioni stanno
in `meteo-android/README.md`.

In `sport-tv/` c'è **Sport in TV Italia**, l'app Android che mostra gli eventi
sportivi trasmessi legalmente in Italia oggi e nei 14 giorni successivi, con il
suo servizio dati gratuito su GitHub Actions. Istruzioni, fonti e verifiche
stanno in `sport-tv/README.md`, `sport-tv/FONTI.md` e `sport-tv/VERIFICHE.md`.

In `pc-check/` c'è **PC Check**, il programma per Windows 10 e 11 che controlla
Microsoft Defender, prestazioni, programmi all'avvio e file temporanei, spiegando
ogni intervento prima di farlo. È codice C# (.NET 8) che GitHub compila da solo e
da cui esce l'eseguibile portatile. Le istruzioni stanno in `pc-check/README.md`.

Per collegare un dominio personalizzato basta aggiungere un file `CNAME` con
dentro il dominio, e impostarlo in Settings → Pages.

@ginopizza
