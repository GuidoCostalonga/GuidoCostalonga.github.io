<#
.SYNOPSIS
  Crea il pacchetto portatile di PC Check (un solo eseguibile per Windows x64).

.DESCRIPTION
  Esegue dotnet publish in modalità self-contained e single-file, copia il risultato
  in dist\portable e produce dist\PCCheck-portable-<versione>.zip.
  Richiede .NET SDK 8.0 (https://dotnet.microsoft.com/download/dotnet/8.0).

.PARAMETER Configuration
  Configurazione di compilazione (predefinita: Release).

.PARAMETER SkipTests
  Salta l'esecuzione dei test unitari prima della pubblicazione.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\build-portable.ps1
#>
[CmdletBinding()]
param(
    [string]$Configuration = "Release",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$project = Join-Path $root "PCCheck.Core\PCCheck.Core.csproj"
$tests = Join-Path $root "PCCheck.Tests\PCCheck.Tests.csproj"
$dist = Join-Path $root "dist"
$portable = Join-Path $dist "portable"

function Write-Step([string]$text) {
    Write-Host ""
    Write-Host "==> $text" -ForegroundColor Cyan
}

Write-Step "Verifica di .NET SDK"
$dotnet = Get-Command dotnet -ErrorAction SilentlyContinue
if (-not $dotnet) {
    throw "dotnet non trovato nel PATH. Installa .NET SDK 8.0 da https://dotnet.microsoft.com/download/dotnet/8.0"
}
$sdkVersion = (& dotnet --version)
Write-Host "dotnet SDK $sdkVersion"
if (-not $sdkVersion.StartsWith("8.")) {
    Write-Warning "Il progetto è pensato per .NET 8 LTS; versione rilevata: $sdkVersion"
}

$version = ([xml](Get-Content $project)).Project.PropertyGroup.Version | Select-Object -First 1
if (-not $version) { $version = "1.0.0" }

Write-Step "Ripristino pacchetti"
& dotnet restore (Join-Path $root "PCCheck.sln")
if ($LASTEXITCODE -ne 0) { throw "dotnet restore non riuscito" }

if (-not $SkipTests) {
    Write-Step "Esecuzione test unitari"
    & dotnet test $tests -c $Configuration --nologo
    if ($LASTEXITCODE -ne 0) { throw "I test non sono passati: pubblicazione interrotta" }
}

Write-Step "Pubblicazione portatile (win-x64, single-file, self-contained)"
if (Test-Path $portable) { Remove-Item $portable -Recurse -Force }
New-Item -ItemType Directory -Path $portable | Out-Null
& dotnet publish $project -c $Configuration -r win-x64 --self-contained true `
    -p:PublishSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:EnableCompressionInSingleFile=true `
    -p:DebugType=none `
    -p:PublishReadyToRun=false `
    -o $portable
if ($LASTEXITCODE -ne 0) { throw "dotnet publish non riuscito" }

# La cartella wwwroot è incorporata nell'eseguibile: la copia su disco non serve nel pacchetto portatile.
$wwwrootCopy = Join-Path $portable "wwwroot"
if (Test-Path $wwwrootCopy) { Remove-Item $wwwrootCopy -Recurse -Force }

Write-Step "File di avvio e documentazione"
@"
@echo off
rem Avvia PC Check con i permessi normali dell'utente.
start "" "%~dp0PCCheck.exe" %*
"@ | Set-Content -Path (Join-Path $portable "Avvia PC Check.cmd") -Encoding ASCII

@"
@echo off
rem Avvia PC Check in modalità dimostrativa (dati fittizi, nessuna modifica al sistema).
start "" "%~dp0PCCheck.exe" --demo
"@ | Set-Content -Path (Join-Path $portable "Avvia PC Check (demo).cmd") -Encoding ASCII

Copy-Item (Join-Path $root "README.md") (Join-Path $portable "LEGGIMI.md") -Force

Write-Step "Creazione archivio zip"
$zip = Join-Path $dist "PCCheck-portable-$version-win-x64.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $portable "*") -DestinationPath $zip -CompressionLevel Optimal

$exe = Get-Item (Join-Path $portable "PCCheck.exe")
Write-Host ""
Write-Host "Pacchetto pronto:" -ForegroundColor Green
Write-Host "  Cartella: $portable"
Write-Host "  Archivio: $zip"
Write-Host ("  PCCheck.exe: {0:N1} MB" -f ($exe.Length / 1MB))
Write-Host "  SHA256: $((Get-FileHash $exe -Algorithm SHA256).Hash)"
