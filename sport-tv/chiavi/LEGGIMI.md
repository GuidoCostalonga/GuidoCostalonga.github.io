# La chiave di questa cartella non è segreta

`servizio.jks` è depositato di proposito nel deposito pubblico.

- alias: `sportintv`
- password dell'archivio e della chiave: `sportintv`

Serve a una cosa sola: dare a tutti gli APK compilati qui, sul computer o da
GitHub Actions, la stessa firma, così gli aggiornamenti si installano sopra la
versione precedente senza disinstallare nulla e senza perdere preferiti e
promemoria. Non protegge niente e non va usata per pubblicare l'app su un
negozio di applicazioni.

Per una firma propria: generare una chiave, conservarla fuori dal deposito,
metterla fra i segreti del progetto su GitHub e cambiare `signingConfigs` in
`app/build.gradle.kts`.
