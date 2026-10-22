namespace PCCheck.Core.Models;

/// <summary>Stato di Microsoft Defender letto da WMI (root\Microsoft\Windows\Defender, classe MSFT_MpComputerStatus).</summary>
public sealed class DefenderStatus
{
    /// <summary>True se Defender e' installato e interrogabile.</summary>
    public bool Available { get; init; }
    public string ProductName { get; init; } = "Microsoft Defender Antivirus";
    public bool AntivirusEnabled { get; init; }
    public bool AntispywareEnabled { get; init; }
    public bool RealTimeProtectionEnabled { get; init; }
    public bool BehaviorMonitorEnabled { get; init; }
    public bool OnAccessProtectionEnabled { get; init; }
    public bool NetworkInspectionEnabled { get; init; }
    public bool TamperProtected { get; init; }
    public string? SignatureVersion { get; init; }
    public DateTimeOffset? SignatureLastUpdated { get; init; }
    public int? SignatureAgeDays { get; init; }
    public string? EngineVersion { get; init; }
    public string? PlatformVersion { get; init; }
    public DateTimeOffset? LastQuickScanEnd { get; init; }
    public DateTimeOffset? LastFullScanEnd { get; init; }
    public int? QuickScanAgeDays { get; init; }
    public int? FullScanAgeDays { get; init; }
    /// <summary>Messaggio di errore se la lettura non e' riuscita (Defender assente, antivirus di terze parti, WMI non disponibile).</summary>
    public string? Error { get; init; }
    public IReadOnlyList<string> Warnings { get; init; } = Array.Empty<string>();
    /// <summary>Percorso di MpCmdRun.exe individuato, se presente.</summary>
    public string? ScannerPath { get; init; }
}

/// <summary>Minaccia rilevata da Defender (classi MSFT_MpThreat e MSFT_MpThreatDetection).</summary>
public sealed class ThreatInfo
{
    public string Id { get; init; } = "";
    public string Name { get; init; } = "";
    public string Severity { get; init; } = "Sconosciuta";
    public int SeverityLevel { get; init; }
    public string Category { get; init; } = "Sconosciuta";
    public string Status { get; init; } = "Sconosciuto";
    public bool IsActive { get; init; }
    public DateTimeOffset? DetectedAt { get; init; }
    public DateTimeOffset? LastChangeAt { get; init; }
    public IReadOnlyList<string> Resources { get; init; } = Array.Empty<string>();
    public string? ProcessName { get; init; }
}

public enum ScanType
{
    Quick = 1,
    Full = 2
}

public enum ScanState
{
    Idle,
    Running,
    Completed,
    ThreatsFound,
    Failed,
    Cancelled
}

/// <summary>Stato della scansione avviata tramite MpCmdRun.exe.</summary>
public sealed class ScanStatus
{
    public ScanState State { get; init; } = ScanState.Idle;
    public ScanType? Type { get; init; }
    public DateTimeOffset? StartedAt { get; init; }
    public DateTimeOffset? FinishedAt { get; init; }
    public int? ExitCode { get; init; }
    public string Message { get; init; } = "";
    public IReadOnlyList<string> Output { get; init; } = Array.Empty<string>();
    public string? Command { get; init; }
}
