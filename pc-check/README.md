# PC Check

Software desktop per Windows 10 e Windows 11 (x64) che aiuta a capire se il computer ha
malware, programmi indesiderati o cause di rallentamento, spiegando ogni cosa in italiano
e guidando gli interventi in modo trasparente.

Funziona **completamente offline**: nessuna telemetria, nessun tracciamento, nessuna
connessione verso domini esterni. Il backend ascolta solo su `127.0.0.1` e l'interfaccia
si apre nel browser predefinito.

> **Nota importante.** Nessun software può garantire la totale assenza di malware. PC Check
> distingue sempre tra *minacce certe* rilevate da Microsoft Defender, *programmi sconosciuti*
> da verificare e *software ad alto impatto* di risorse.

## Contenuto della cartella

| Percorso | Contenuto |
|---|---|
| `PCCheck.sln` | Soluzione .NET con i due progetti |
| `PCCheck.Core/` | Backend ASP.NET Core Minimal API, servizi di sistema, interfaccia web incorporata |
| `PCCheck.Core/Program.cs` | Avvio, server locale, rotte API, token di sessione, modalità dimostrativa |
| `PCCheck.Core/Security/` | Token di sessione, controllo Host/Origin, CSP, regole sui comandi e sui percorsi |
| `PCCheck.Core/Services/` | `DefenderService`, `PerformanceService`, `StartupService`, `InstalledAppsService`, `TempCleanerService`, `ReportService` e le versioni `Mock` per la demo |
| `PCCheck.Core/wwwroot/` | `index.html`, `app.css`, `app.js`: interfaccia in italiano con 6 sezioni |
| `PCCheck.Tests/` | Test unitari e di integrazione xUnit |
| `scripts/build-portable.ps1` | Pubblica l'eseguibile portatile (un solo file) e lo zip |
| `scripts/build-installer.iss` | Script Inno Setup per l'installer |

## Requisiti

- Per usare il pacchetto portatile o l'installer: Windows 10 (1809 o successivo) o Windows 11, 64 bit. Nessun runtime da installare: l'eseguibile è autonomo.
- Per compilare: [.NET SDK 8.0 LTS](https://dotnet.microsoft.com/download/dotnet/8.0) e, solo per l'installer, [Inno Setup 6](https://jrsoftware.org/isinfo.php).

## Compilare e provare

```powershell
cd pc-check
dotnet restore
dotnet build -c Release
dotnet test -c Release
```

Avvio dal sorgente:

```powershell
dotnet run --project PCCheck.Core -c Release            # dati reali del computer
dotnet run --project PCCheck.Core -c Release -- --demo  # modalità dimostrativa
```

Opzioni di riga di comando:

| Opzione | Effetto |
|---|---|
| `--demo` | Carica dati fittizi realistici: nessuna lettura né modifica del sistema reale |
| `--no-browser` | Non apre il browser; il collegamento con il token viene stampato in console |
| `--port N` | Usa la porta N su `127.0.0.1` se libera, altrimenti una porta libera casuale |
| `--help` | Mostra la guida |

La modalità dimostrativa si attiva anche con la variabile d'ambiente `PCCHECK_DEMO=1` o con
`"PcCheck": { "Demo": true }` in `appsettings.json`. Su sistemi diversi da Windows l'app
parte sempre con i dati dimostrativi.

## Creare il pacchetto portatile

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-portable.ps1
```

Lo script esegue i test, pubblica `PCCheck.exe` (self-contained, single-file, win-x64)
in `dist\portable\` insieme a due file `.cmd` di avvio e crea
`dist\PCCheck-portable-<versione>-win-x64.zip`. L'interfaccia web è incorporata
nell'eseguibile: basta copiare `PCCheck.exe` su una chiavetta.

## Creare l'installer

Dopo il pacchetto portatile:

```powershell
& "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" scripts\build-installer.iss
```

L'installer (`dist\PCCheck-setup-<versione>-win-x64.exe`) non richiede permessi di
amministratore: installa in `%LOCALAPPDATA%\Programs\PC Check` e crea i collegamenti nel
menu Start, compreso quello per la modalità dimostrativa.

## Come funziona

1. All'avvio viene generato un **token di sessione casuale** (256 bit, `PC_CHECK_SESSION_TOKEN`).
2. Il server ascolta su una porta libera di `127.0.0.1` (riserva: 5000) e apre il browser su
   `http://127.0.0.1:<porta>/?token=<token>`.
3. L'interfaccia salva il token in `sessionStorage`, lo toglie dall'indirizzo e lo invia in
   ogni chiamata API nell'header `X-PC-Check-Token`.
4. Il pulsante **Chiudi PC Check** chiama `/api/system/shutdown` e arresta il backend.

### Sezioni dell'interfaccia

| Sezione | Cosa mostra |
|---|---|
| Panoramica | Stato della protezione, minacce certe, CPU, memoria, disco, file temporanei e l'elenco delle segnalazioni con gravità |
| Scansione | Stato di Microsoft Defender (protezione in tempo reale, versione e data delle definizioni, ultime scansioni), avvio di scansione rapida o completa, cronologia delle minacce |
| Prestazioni | CPU e memoria campionate su 1-3 secondi, dischi, processi con maggior consumo, terminazione guidata |
| Avvio e App | Programmi all'avvio (registro e cartelle Esecuzione automatica) con attivazione/disattivazione reversibile; programmi installati con editore, cartella, dimensione e disinstallazione guidata |
| Interventi | Anteprima e pulizia dei file temporanei, programmi ad alto impatto, voci di avvio e programmi da verificare |
| Report | Rapporto completo scaricabile in HTML o JSON, oppure salvato in Documenti\PC Check |

### Fonti dei dati

- **Microsoft Defender**: WMI, namespace `root\Microsoft\Windows\Defender`, classi
  `MSFT_MpComputerStatus`, `MSFT_MpThreat`, `MSFT_MpThreatDetection`.
- **Scansioni**: eseguibile ufficiale `MpCmdRun.exe` (cercato solo in
  `%ProgramData%\Microsoft\Windows Defender\Platform\<versione>` e `%ProgramFiles%\Windows Defender`)
  con i soli argomenti `-Scan -ScanType 1` (rapida) o `-Scan -ScanType 2` (completa).
- **Prestazioni**: `System.Diagnostics.Process` (delta del tempo CPU tra due campionamenti),
  `GetSystemTimes` e `GlobalMemoryStatusEx` di kernel32, `System.IO.DriveInfo`.
- **Avvio**: `HKCU` e `HKLM\Software\Microsoft\Windows\CurrentVersion\Run` e `RunOnce`
  (viste a 64 e 32 bit), cartelle Esecuzione automatica, stato da `Explorer\StartupApproved`.
- **Programmi**: chiavi `Uninstall` di `HKLM` (64 e 32 bit) e `HKCU`.
- **File temporanei**: solo `%TEMP%` e `C:\Windows\Temp`.

## Sicurezza e privacy

- **Solo loopback**: Kestrel ascolta esclusivamente su `127.0.0.1`. Le richieste con
  indirizzo remoto non locale, header `Host` diverso da `127.0.0.1`/`localhost` o `Origin`
  non locale vengono rifiutate (protezione da DNS rebinding e CSRF).
- **Token obbligatorio**: ogni rotta `/api/*` richiede l'header `X-PC-Check-Token`, confrontato
  a tempo costante. Senza token: `401`.
- **Content Security Policy severa**: `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'`
  più `object-src 'none'`, `frame-ancestors 'none'`, `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store` sulle API.
- **Nessuna shell**: i comandi esterni (`MpCmdRun.exe`, `taskkill.exe`, `reg.exe`,
  `msiexec.exe`, `winget.exe`) vengono avviati con argomenti passati come lista, mai
  attraverso `cmd` o PowerShell. Le stringhe `UninstallString` del registro non vengono mai eseguite.
- **Interventi confermati**: pulizia dei temporanei, terminazione di processi, disattivazione
  di voci di avvio e disinstallazioni richiedono una conferma esplicita con descrizione di
  impatto e reversibilità. Le modifiche all'avvio usano la chiave `StartupApproved` (la stessa
  di Gestione attività) e sono reversibili. Le API rifiutano le richieste senza `confirmed: true`.
- **Permessi minimi**: l'app parte con i permessi normali. Quando serve l'amministratore
  (processi protetti, voci di avvio di sistema, `C:\Windows\Temp`), avvia un processo
  secondario elevato (`runas`) limitato a quel singolo comando: `taskkill /PID n /F`,
  `reg.exe add ...` oppure `PCCheck.exe --task clean-temp ...`.
- **Processi protetti**: i componenti di Windows (`System`, `csrss`, `lsass`, `svchost`,
  `MsMpEng`, ...) non possono essere terminati dall'interfaccia.
- **Percorsi controllati**: ogni eliminazione è limitata alle due cartelle temporanee
  (`PathGuard`); i file in uso vengono saltati, non forzati.
- **Privacy**: nessun dato lascia il computer. Il rapporto viene salvato solo se lo chiedi
  tu, in `Documenti\PC Check`. L'unica cartella creata dall'app è `%LOCALAPPDATA%\PC Check`
  per gli esiti temporanei dei compiti elevati.

## Test

`dotnet test` esegue 154 test xUnit su:

- generazione e verifica del token di sessione (lunghezza, alfabeto, confronto a tempo costante);
- validazione dell'header `Host` e dell'`Origin` (loopback, DNS rebinding, formati malformati);
- regole sui comandi (argomenti di scansione, ProductCode MSI, nomi per `winget` e `reg.exe`, PID);
- controllo dei percorsi (`PathGuard`) contro attraversamenti e prefissi simili;
- calcolo della CPU per processo e di sistema, soglie di alto impatto, processi protetti;
- logica delle voci di avvio (`StartupApproved`), indizi sui programmi, segnalazioni del rapporto;
- integrazione sull'host reale in modalità dimostrativa: `401` senza token, `400` con Host
  esterno, `403` con Origin esterna, CSP sulla pagina, rifiuto delle azioni non confermate.

## Limiti noti

- Lo stato di Defender è leggibile solo se Microsoft Defender è il motore attivo; con un
  antivirus di terze parti la sezione lo segnala.
- L'interruzione di una scansione chiude `MpCmdRun.exe`: il motore di Defender può comunque
  completare l'operazione per conto proprio.
- La disinstallazione con `winget` usa il nome esatto del programma; per i pacchetti MSI è
  disponibile `msiexec /x {ProductCode}`, altrimenti si apre `ms-settings:appsfeatures`.
- Le voci di avvio pianificate con l'Utilità di pianificazione non vengono elencate.
