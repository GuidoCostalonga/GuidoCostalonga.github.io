namespace PCCheck.Core.Models;

/// <summary>Esito di un intervento (terminazione processo, disattivazione avvio, disinstallazione...).</summary>
public sealed class ActionOutcome
{
    public bool Success { get; init; }
    public string Message { get; init; } = "";
    public bool RequiresAdmin { get; init; }
    public bool UserCancelled { get; init; }
    public string? Details { get; init; }

    public static ActionOutcome Ok(string message, string? details = null) =>
        new() { Success = true, Message = message, Details = details };

    public static ActionOutcome Fail(string message, string? details = null, bool requiresAdmin = false) =>
        new() { Success = false, Message = message, Details = details, RequiresAdmin = requiresAdmin };

    public static ActionOutcome Cancelled(string message) =>
        new() { Success = false, Message = message, UserCancelled = true };
}

public enum FindingSeverity
{
    Info = 0,
    Low = 1,
    Medium = 2,
    High = 3,
    Critical = 4
}

public sealed class Finding
{
    public FindingSeverity Severity { get; init; }
    public string Title { get; init; } = "";
    public string Description { get; init; } = "";
    /// <summary>Sezione dell'interfaccia in cui intervenire: scan, performance, startup, apps, temp.</summary>
    public string Section { get; init; } = "";
    public string Kind { get; init; } = "";
}

public sealed class SystemInfo
{
    public string AppName { get; init; } = "PC Check";
    public string Version { get; init; } = "";
    public bool DemoMode { get; init; }
    public string MachineName { get; init; } = "";
    public string UserName { get; init; } = "";
    public string OsDescription { get; init; } = "";
    public bool IsWindows { get; init; }
    public bool IsAdministrator { get; init; }
    public bool WingetAvailable { get; init; }
    public DateTimeOffset StartedAt { get; init; }
}

public sealed class SystemReport
{
    public DateTimeOffset GeneratedAt { get; init; }
    public SystemInfo System { get; init; } = new();
    public DefenderStatus Defender { get; init; } = new();
    public IReadOnlyList<ThreatInfo> Threats { get; init; } = Array.Empty<ThreatInfo>();
    public ScanStatus Scan { get; init; } = new();
    public PerformanceSnapshot Performance { get; init; } = new();
    public IReadOnlyList<StartupEntry> Startup { get; init; } = Array.Empty<StartupEntry>();
    public IReadOnlyList<InstalledApp> Apps { get; init; } = Array.Empty<InstalledApp>();
    public TempAnalysis Temp { get; init; } = new();
    public IReadOnlyList<Finding> Findings { get; init; } = Array.Empty<Finding>();
    public string Disclaimer { get; init; } = Disclaimers.NoGuarantee;
}

public static class Disclaimers
{
    public const string NoGuarantee =
        "Nessun software può garantire la totale assenza di malware. PC Check mostra ciò che Microsoft Defender e il sistema riportano: distingue sempre tra minacce certe rilevate, programmi sconosciuti da verificare e software che consuma molte risorse.";
}
