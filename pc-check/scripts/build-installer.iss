; ---------------------------------------------------------------------------
; PC Check - script Inno Setup 6 (https://jrsoftware.org/isinfo.php)
;
; Prerequisito: eseguire prima scripts\build-portable.ps1, che pubblica
; l'eseguibile in dist\portable. Poi compilare con:
;   "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" scripts\build-installer.iss
; L'installer non richiede permessi di amministratore: installa nel profilo utente.
; ---------------------------------------------------------------------------

#define AppName "PC Check"
#define AppVersion "1.0.0"
#define AppPublisher "PC Check"
#define AppExeName "PCCheck.exe"
#define SourceDir "..\dist\portable"
#define OutputDir "..\dist"

[Setup]
AppId={{7C1E3C9A-5B2D-4F41-9A4E-2B3C0D7E8F10}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={localappdata}\Programs\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir={#OutputDir}
OutputBaseFilename=PCCheck-setup-{#AppVersion}-win-x64
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.17763
UninstallDisplayIcon={app}\{#AppExeName}
UninstallDisplayName={#AppName}
ShowLanguageDialog=no
LicenseFile=
SetupLogging=yes

[Languages]
Name: "italian"; MessagesFile: "compiler:Languages\Italian.isl"

[Tasks]
Name: "desktopicon"; Description: "Crea un'icona sul desktop"; GroupDescription: "Icone aggiuntive:"; Flags: unchecked

[Files]
Source: "{#SourceDir}\{#AppExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourceDir}\LEGGIMI.md"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourceDir}\appsettings.json"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Comment: "Diagnostica locale di sicurezza e prestazioni"
Name: "{group}\{#AppName} (modalità dimostrativa)"; Filename: "{app}\{#AppExeName}"; Parameters: "--demo"
Name: "{group}\Disinstalla {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Avvia {#AppName} adesso"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Dati temporanei dell'app (esiti dei compiti elevati). Nessun altro file dell'utente viene toccato.
Type: filesandordirs; Name: "{localappdata}\{#AppName}"

[Messages]
WelcomeLabel2=Questa procedura installerà {#AppName} {#AppVersion} sul computer.%n%n{#AppName} funziona completamente offline, non invia dati all'esterno e parte con i permessi normali dell'utente.
