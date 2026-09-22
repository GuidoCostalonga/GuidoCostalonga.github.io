# La chiave di questa cartella non è segreta

`servizio.jks` è depositato di proposito nel deposito pubblico.

- alias: `meteofvg`
- password dell'archivio e della chiave: `meteofvg`

Serve a una cosa sola: dare a tutti gli APK compilati qui la stessa firma, così
gli aggiornamenti si installano sopra la versione precedente senza disinstallare
nulla. Non protegge niente, non va usata per pubblicare l'app su un negozio di
applicazioni e non va trattata come un segreto.

Per una firma propria: genera una chiave, conservala fuori dal deposito, mettila
fra i segreti del progetto su GitHub e compila la variante `release`, che resta
apposta senza firma.
