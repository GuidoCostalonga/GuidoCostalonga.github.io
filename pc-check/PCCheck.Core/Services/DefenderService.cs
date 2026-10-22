using System.Collections.Concurrent;
using System.Diagnostics;
using System.Management;
using System.Runtime.Versioning;
using Microsoft.Extensions.Logging;
using PCCheck.Core.Models;
using PCCheck.Core.Security;

namespace PCCheck.Core.Services;

/// <summary>
/// Legge lo stato di Microsoft Defender via WMI (namespace root\Microsoft\Windows\Defender)
/// e avvia scansioni con l'eseguibile ufficiale MpCmdRun.exe usando solo argomenti convalidati.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class DefenderService : IDefenderService, IDisposable
{
    private const string Namespace = @"root\Microsoft\Windows\Defender";
    private const int MaxOutputLines = 200;

    private readonly ILogger<DefenderService> _logger;
    private readonly object _scanLock = new();
    private Process? _scanProcess;
    private ScanStatus _scanStatus = new();
    private readonly ConcurrentQueue<string> _output = new();
    private DateTimeOffset? _scanStartedAt;
    private ScanType? _scanType;
    private string? _scanCommand;
    private bool _cancelRequested;

    public DefenderService(ILogger<DefenderService> logger)
    {
        _logger = logger;
    }

    public Task<DefenderStatus> GetStatusAsync(CancellationToken cancellationToken = default)
    {
        return Task.Run(() => ReadStatus(), cancellationToken);
    }

    private DefenderStatus ReadStatus()
    {
        var warnings = new List<string>();
        var scannerPath = FindMpCmdRun();
        try
        {
            using var searcher = new ManagementObjectSearcher(Namespace, "SELECT * FROM MSFT_MpComputerStatus");
            using var results = searcher.Get();
            foreach (ManagementBaseObject item in results)
            {
                using (item)
                {
                    var signatureUpdated = ReadDate(item, "AntivirusSignatureLastUpdated");
                    var quickScanEnd = ReadDate(item, "QuickScanEndTime");
                    var fullScanEnd = ReadDate(item, "FullScanEndTime");
                    var signatureAge = ReadUInt(item, "AntivirusSignatureAge");
                    var quickAge = ReadUInt(item, "QuickScanAge");
                    var fullAge = ReadUInt(item, "FullScanAge");

                    var realTime = ReadBool(item, "RealTimeProtectionEnabled");
                    var avEnabled = ReadBool(item, "AntivirusEnabled");
                    if (!avEnabled)
                    {
                        warnings.Add("La protezione antivirus di Microsoft Defender risulta disattivata (potrebbe essere attivo un antivirus di terze parti).");
                    }
                    else if (!realTime)
                    {
                        warnings.Add("La protezione in tempo reale è disattivata: i file vengono controllati solo durante le scansioni manuali.");
                    }
                    if (signatureAge is > 7)
                    {
                        warnings.Add($"Le definizioni antivirus hanno {signatureAge} giorni: verifica Windows Update.");
                    }
                    if (scannerPath is null)
                    {
                        warnings.Add("MpCmdRun.exe non trovato: le scansioni vanno avviate da Sicurezza di Windows.");
                    }

                    return new DefenderStatus
                    {
                        Available = true,
                        AntivirusEnabled = avEnabled,
                        AntispywareEnabled = ReadBool(item, "AntispywareEnabled"),
                        RealTimeProtectionEnabled = realTime,
                        BehaviorMonitorEnabled = ReadBool(item, "BehaviorMonitorEnabled"),
                        OnAccessProtectionEnabled = ReadBool(item, "OnAccessProtectionEnabled"),
                        NetworkInspectionEnabled = ReadBool(item, "NISEnabled"),
                        TamperProtected = ReadBool(item, "IsTamperProtected"),
                        SignatureVersion = ReadString(item, "AntivirusSignatureVersion"),
                        SignatureLastUpdated = signatureUpdated,
                        SignatureAgeDays = signatureAge.HasValue ? (int)signatureAge.Value : AgeInDays(signatureUpdated),
                        EngineVersion = ReadString(item, "AMEngineVersion"),
                        PlatformVersion = ReadString(item, "AMProductVersion"),
                        LastQuickScanEnd = quickScanEnd,
                        LastFullScanEnd = fullScanEnd,
                        QuickScanAgeDays = quickAge.HasValue && quickAge.Value != uint.MaxValue ? (int)quickAge.Value : AgeInDays(quickScanEnd),
                        FullScanAgeDays = fullAge.HasValue && fullAge.Value != uint.MaxValue ? (int)fullAge.Value : AgeInDays(fullScanEnd),
                        Warnings = warnings,
                        ScannerPath = scannerPath
                    };
                }
            }
            return new DefenderStatus
            {
                Available = false,
                Error = "Microsoft Defender non ha restituito alcuno stato (classe MSFT_MpComputerStatus vuota).",
                Warnings = warnings,
                ScannerPath = scannerPath
            };
        }
        catch (ManagementException ex)
        {
            _logger.LogWarning(ex, "Lettura WMI di Defender non riuscita");
            return new DefenderStatus
            {
                Available = false,
                Error = "Impossibile interrogare Microsoft Defender tramite WMI: " + ex.Message +
                        ". Se è installato un antivirus di terze parti, Defender potrebbe essere disattivato.",
                ScannerPath = scannerPath
            };
        }
        catch (Exception ex) when (ex is UnauthorizedAccessException or System.Runtime.InteropServices.COMException or TypeInitializationException or PlatformNotSupportedException)
        {
            _logger.LogWarning(ex, "Lettura dello stato di Defender non riuscita");
            return new DefenderStatus
            {
                Available = false,
                Error = "Stato di Defender non disponibile: " + ex.Message,
                ScannerPath = scannerPath
            };
        }
    }

    public Task<IReadOnlyList<ThreatInfo>> GetThreatsAsync(CancellationToken cancellationToken = default)
    {
        return Task.Run<IReadOnlyList<ThreatInfo>>(() => ReadThreats(), cancellationToken);
    }

    private List<ThreatInfo> ReadThreats()
    {
        var threats = new Dictionary<string, ThreatInfo>();
        try
        {
            using (var searcher = new ManagementObjectSearcher(Namespace, "SELECT * FROM MSFT_MpThreat"))
            using (var results = searcher.Get())
            {
                foreach (ManagementBaseObject item in results)
                {
                    using (item)
                    {
                        var id = ReadString(item, "ThreatID") ?? Guid.NewGuid().ToString("N");
                        var severityId = (int)(ReadUInt(item, "SeverityID") ?? 0);
                        var categoryId = (int)(ReadUInt(item, "CategoryID") ?? 0);
                        threats[id] = new ThreatInfo
                        {
                            Id = id,
                            Name = ReadString(item, "ThreatName") ?? "Minaccia senza nome",
                            Severity = SeverityLabel(severityId),
                            SeverityLevel = severityId,
                            Category = CategoryLabel(categoryId),
                            IsActive = ReadBool(item, "IsActive")
                        };
                    }
                }
            }

            using (var searcher = new ManagementObjectSearcher(Namespace, "SELECT * FROM MSFT_MpThreatDetection"))
            using (var results = searcher.Get())
            {
                foreach (ManagementBaseObject item in results)
                {
                    using (item)
                    {
                        var id = ReadString(item, "ThreatID") ?? "";
                        var statusId = (int)(ReadUInt(item, "ThreatStatusID") ?? 0);
                        var resources = item["Resources"] as string[] ?? Array.Empty<string>();
                        var existing = threats.TryGetValue(id, out var t) ? t : null;
                        threats[id] = new ThreatInfo
                        {
                            Id = id,
                            Name = existing?.Name ?? (ReadString(item, "ThreatName") ?? $"Minaccia {id}"),
                            Severity = existing?.Severity ?? "Sconosciuta",
                            SeverityLevel = existing?.SeverityLevel ?? 0,
                            Category = existing?.Category ?? "Sconosciuta",
                            IsActive = existing?.IsActive ?? statusId == 1,
                            Status = StatusLabel(statusId),
                            DetectedAt = ReadDate(item, "InitialDetectionTime"),
                            LastChangeAt = ReadDate(item, "LastThreatStatusChangeTime"),
                            Resources = resources,
                            ProcessName = ReadString(item, "ProcessName")
                        };
                    }
                }
            }
        }
        catch (Exception ex) when (ex is ManagementException or UnauthorizedAccessException or System.Runtime.InteropServices.COMException or PlatformNotSupportedException)
        {
            _logger.LogWarning(ex, "Lettura delle minacce di Defender non riuscita");
        }
        return threats.Values.OrderByDescending(t => t.IsActive).ThenByDescending(t => t.SeverityLevel).ThenByDescending(t => t.DetectedAt).ToList();
    }

    public Task<ScanStatus> StartScanAsync(ScanType type, CancellationToken cancellationToken = default)
    {
        var arguments = CommandPolicy.BuildDefenderScanArguments(type);
        var exe = FindMpCmdRun();
        if (exe is null)
        {
            return Task.FromResult(new ScanStatus
            {
                State = ScanState.Failed,
                Type = type,
                Message = "MpCmdRun.exe non trovato: avvia la scansione da Sicurezza di Windows > Protezione da virus e minacce."
            });
        }

        lock (_scanLock)
        {
            if (_scanProcess is { HasExited: false })
            {
                return Task.FromResult(BuildStatus(ScanState.Running, "Una scansione è già in corso."));
            }

            var psi = new ProcessStartInfo
            {
                FileName = exe,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true,
                StandardOutputEncoding = System.Text.Encoding.UTF8
            };
            foreach (var a in arguments)
            {
                psi.ArgumentList.Add(a);
            }

            try
            {
                _output.Clear();
                _cancelRequested = false;
                var process = new Process { StartInfo = psi, EnableRaisingEvents = true };
                process.OutputDataReceived += (_, e) => Append(e.Data);
                process.ErrorDataReceived += (_, e) => Append(e.Data);
                process.Exited += (_, _) => OnScanExited();
                if (!process.Start())
                {
                    return Task.FromResult(new ScanStatus { State = ScanState.Failed, Type = type, Message = "Avvio di MpCmdRun.exe non riuscito." });
                }
                process.BeginOutputReadLine();
                process.BeginErrorReadLine();
                _scanProcess = process;
                _scanType = type;
                _scanStartedAt = DateTimeOffset.Now;
                _scanCommand = $"\"{exe}\" {string.Join(' ', arguments)}";
                _scanStatus = BuildStatus(ScanState.Running, type == ScanType.Quick
                    ? "Scansione rapida avviata con Microsoft Defender."
                    : "Scansione completa avviata: può richiedere molto tempo.");
                _logger.LogInformation("Scansione Defender avviata: {Command}", _scanCommand);
                return Task.FromResult(_scanStatus);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Avvio della scansione non riuscito");
                return Task.FromResult(new ScanStatus { State = ScanState.Failed, Type = type, Message = "Avvio della scansione non riuscito: " + ex.Message });
            }
        }
    }

    private void OnScanExited()
    {
        lock (_scanLock)
        {
            var process = _scanProcess;
            if (process is null)
            {
                return;
            }
            int exitCode;
            try
            {
                process.WaitForExit();
                exitCode = process.ExitCode;
            }
            catch (InvalidOperationException)
            {
                exitCode = -1;
            }
            ScanState state;
            string message;
            if (_cancelRequested)
            {
                state = ScanState.Cancelled;
                message = "Scansione interrotta dall'utente. Il motore di Defender potrebbe completare comunque l'operazione in background.";
            }
            else if (exitCode == 0)
            {
                state = ScanState.Completed;
                message = "Scansione terminata: Microsoft Defender non ha trovato minacce.";
            }
            else if (exitCode == 2)
            {
                state = ScanState.ThreatsFound;
                message = "Scansione terminata: Microsoft Defender ha rilevato minacce. Controlla l'elenco e Sicurezza di Windows.";
            }
            else
            {
                state = ScanState.Failed;
                message = $"Scansione terminata con codice {exitCode}. Verifica l'output per i dettagli.";
            }
            _scanStatus = new ScanStatus
            {
                State = state,
                Type = _scanType,
                StartedAt = _scanStartedAt,
                FinishedAt = DateTimeOffset.Now,
                ExitCode = exitCode,
                Message = message,
                Output = _output.ToArray(),
                Command = _scanCommand
            };
            process.Dispose();
            _scanProcess = null;
        }
    }

    public ScanStatus GetScanStatus()
    {
        lock (_scanLock)
        {
            if (_scanProcess is { HasExited: false })
            {
                return BuildStatus(ScanState.Running, _scanStatus.Message);
            }
            return _scanStatus;
        }
    }

    public Task<ScanStatus> CancelScanAsync()
    {
        lock (_scanLock)
        {
            if (_scanProcess is { HasExited: false } process)
            {
                try
                {
                    _cancelRequested = true;
                    process.Kill(entireProcessTree: true);
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex, "Interruzione della scansione non riuscita");
                    return Task.FromResult(BuildStatus(ScanState.Running, "Impossibile interrompere la scansione: " + ex.Message));
                }
                return Task.FromResult(BuildStatus(ScanState.Cancelled, "Interruzione richiesta."));
            }
            return Task.FromResult(_scanStatus);
        }
    }

    private ScanStatus BuildStatus(ScanState state, string message) => new()
    {
        State = state,
        Type = _scanType,
        StartedAt = _scanStartedAt,
        Message = message,
        Output = _output.ToArray(),
        Command = _scanCommand
    };

    private void Append(string? line)
    {
        if (string.IsNullOrWhiteSpace(line))
        {
            return;
        }
        _output.Enqueue(line.Trim());
        while (_output.Count > MaxOutputLines && _output.TryDequeue(out _))
        {
        }
    }

    /// <summary>Cerca MpCmdRun.exe solo nelle cartelle ufficiali di Defender.</summary>
    public static string? FindMpCmdRun()
    {
        if (!OperatingSystem.IsWindows())
        {
            return null;
        }
        var candidates = new List<string>();
        var programData = Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData);
        if (!string.IsNullOrEmpty(programData))
        {
            var platformDir = Path.Combine(programData, "Microsoft", "Windows Defender", "Platform");
            if (Directory.Exists(platformDir))
            {
                try
                {
                    var versions = Directory.GetDirectories(platformDir)
                        .Select(d => new { Dir = d, Version = ParseVersion(Path.GetFileName(d)) })
                        .Where(x => x.Version is not null)
                        .OrderByDescending(x => x.Version)
                        .Select(x => Path.Combine(x.Dir, "MpCmdRun.exe"));
                    candidates.AddRange(versions);
                }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
                {
                    // Ignora: si usa il percorso classico.
                }
            }
        }
        var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        if (!string.IsNullOrEmpty(programFiles))
        {
            candidates.Add(Path.Combine(programFiles, "Windows Defender", "MpCmdRun.exe"));
        }
        foreach (var candidate in candidates)
        {
            if (File.Exists(candidate) && IsOfficialLocation(candidate, programData, programFiles))
            {
                return candidate;
            }
        }
        return null;
    }

    private static bool IsOfficialLocation(string path, string? programData, string? programFiles)
    {
        if (!string.IsNullOrEmpty(programData) && PathGuard.IsInside(Path.Combine(programData, "Microsoft", "Windows Defender"), path))
        {
            return true;
        }
        return !string.IsNullOrEmpty(programFiles) && PathGuard.IsInside(Path.Combine(programFiles, "Windows Defender"), path);
    }

    private static Version? ParseVersion(string name)
    {
        var dash = name.IndexOf('-');
        var text = dash > 0 ? name[..dash] : name;
        return Version.TryParse(text, out var v) ? v : null;
    }

    private static string? ReadString(ManagementBaseObject item, string name)
    {
        try
        {
            var value = item[name];
            return value?.ToString();
        }
        catch (ManagementException)
        {
            return null;
        }
    }

    private static bool ReadBool(ManagementBaseObject item, string name)
    {
        try
        {
            return item[name] is bool b && b;
        }
        catch (ManagementException)
        {
            return false;
        }
    }

    private static uint? ReadUInt(ManagementBaseObject item, string name)
    {
        try
        {
            return item[name] switch
            {
                uint u => u,
                int i when i >= 0 => (uint)i,
                ushort us => us,
                byte b => b,
                _ => null
            };
        }
        catch (ManagementException)
        {
            return null;
        }
    }

    private static DateTimeOffset? ReadDate(ManagementBaseObject item, string name)
    {
        try
        {
            var raw = item[name]?.ToString();
            if (string.IsNullOrEmpty(raw))
            {
                return null;
            }
            var dt = ManagementDateTimeConverter.ToDateTime(raw);
            if (dt.Year < 1980)
            {
                return null;
            }
            return new DateTimeOffset(dt);
        }
        catch (Exception ex) when (ex is ManagementException or ArgumentException or FormatException)
        {
            return null;
        }
    }

    private static int? AgeInDays(DateTimeOffset? date) =>
        date.HasValue ? (int)Math.Max(0, (DateTimeOffset.Now - date.Value).TotalDays) : null;

    public static string SeverityLabel(int id) => id switch
    {
        1 => "Bassa",
        2 => "Moderata",
        4 => "Alta",
        5 => "Grave",
        _ => "Sconosciuta"
    };

    public static string StatusLabel(int id) => id switch
    {
        1 => "Rilevata",
        2 => "Pulita",
        3 => "In quarantena",
        4 => "Rimossa",
        5 => "Consentita dall'utente",
        6 => "Bloccata",
        102 => "Quarantena non riuscita",
        103 => "Rimozione non riuscita",
        104 => "Pulizia non riuscita",
        105 => "Autorizzazione non riuscita",
        106 => "Abbandonata",
        107 => "Blocco non riuscito",
        _ => "Sconosciuto"
    };

    public static string CategoryLabel(int id) => id switch
    {
        1 => "Adware",
        2 => "Spyware",
        3 => "Furto di password",
        4 => "Trojan downloader",
        5 => "Worm",
        6 => "Backdoor",
        7 => "Trojan di accesso remoto",
        8 => "Trojan",
        9 => "Email flooder",
        10 => "Keylogger",
        11 => "Dialer",
        12 => "Software di monitoraggio",
        13 => "Modificatore del browser",
        14 => "Cookie",
        15 => "Plugin del browser",
        16 => "Exploit AOL",
        17 => "Nuker",
        18 => "Disattivatore di sicurezza",
        19 => "Programma scherzo",
        20 => "Controllo ActiveX ostile",
        21 => "Software in bundle",
        22 => "Notificatore nascosto",
        23 => "Modificatore impostazioni",
        24 => "Barra degli strumenti",
        25 => "Software di controllo remoto",
        26 => "Trojan FTP",
        27 => "Software potenzialmente indesiderato",
        28 => "Exploit ICQ",
        29 => "Trojan Telnet",
        30 => "Exploit",
        31 => "Programma di condivisione file",
        32 => "Strumento di creazione malware",
        33 => "Software di controllo remoto",
        34 => "Strumento",
        36 => "Trojan denial of service",
        37 => "Trojan dropper",
        38 => "Trojan mass mailer",
        39 => "Trojan di monitoraggio",
        40 => "Trojan proxy",
        42 => "Virus",
        43 => "Conosciuta",
        44 => "Sconosciuta",
        45 => "SPP",
        46 => "Comportamento sospetto",
        47 => "Vulnerabilità",
        48 => "Criterio",
        49 => "EUS",
        50 => "Ransomware",
        51 => "Riduzione superficie di attacco",
        _ => $"Categoria {id}"
    };

    public void Dispose()
    {
        lock (_scanLock)
        {
            _scanProcess?.Dispose();
            _scanProcess = null;
        }
    }
}
