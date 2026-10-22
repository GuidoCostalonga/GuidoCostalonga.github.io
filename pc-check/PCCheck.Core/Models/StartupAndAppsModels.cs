namespace PCCheck.Core.Models;

public enum StartupKind
{
    Registry,
    Folder
}

public sealed class StartupEntry
{
    public string Id { get; init; } = "";
    public string Name { get; init; } = "";
    public string Command { get; init; } = "";
    /// <summary>Chiave di registro o cartella da cui proviene la voce.</summary>
    public string Location { get; init; } = "";
    /// <summary>"Utente" (HKCU / cartella Esecuzione automatica dell'utente) o "Sistema" (HKLM / cartella comune).</summary>
    public string Scope { get; init; } = "Utente";
    public StartupKind Kind { get; init; }
    public bool Enabled { get; init; } = true;
    /// <summary>True se la voce puo' essere attivata/disattivata tramite StartupApproved.</summary>
    public bool CanToggle { get; init; }
    public bool RequiresAdmin { get; init; }
    public string? Publisher { get; init; }
    public bool Unknown { get; init; }
}

public sealed class InstalledApp
{
    public string Id { get; init; } = "";
    public string Name { get; init; } = "";
    public string? Publisher { get; init; }
    public string? Version { get; init; }
    public DateOnly? InstallDate { get; init; }
    public string? InstallLocation { get; init; }
    public long? EstimatedSizeBytes { get; init; }
    public string Scope { get; init; } = "Sistema";
    public bool IsMsi { get; init; }
    public string? ProductCode { get; init; }
    public string? UninstallCommand { get; init; }
    public bool UnknownPublisher { get; init; }
    /// <summary>Indizi (non prove) di software indesiderato o da verificare.</summary>
    public IReadOnlyList<string> Hints { get; init; } = Array.Empty<string>();
    public string RegistryKey { get; init; } = "";
}

public sealed class TempFolderSummary
{
    public string Path { get; init; } = "";
    public long Bytes { get; init; }
    public int Files { get; init; }
}

public sealed class TempTarget
{
    /// <summary>"user" (%TEMP%) o "windows" (C:\Windows\Temp).</summary>
    public string Key { get; init; } = "";
    public string Label { get; init; } = "";
    public string Path { get; init; } = "";
    public bool Exists { get; init; }
    public bool Accessible { get; init; }
    public bool RequiresAdmin { get; init; }
    public int FileCount { get; init; }
    public int DirectoryCount { get; init; }
    public long TotalBytes { get; init; }
    public long OlderThan7DaysBytes { get; init; }
    public int OlderThan7DaysFiles { get; init; }
    public IReadOnlyList<TempFolderSummary> TopFolders { get; init; } = Array.Empty<TempFolderSummary>();
    public string? Error { get; init; }
}

public sealed class TempAnalysis
{
    public DateTimeOffset AnalyzedAt { get; init; }
    public IReadOnlyList<TempTarget> Targets { get; init; } = Array.Empty<TempTarget>();
    public long TotalReclaimableBytes { get; init; }
}

public sealed class TempCleanRequest
{
    public string[] Targets { get; set; } = Array.Empty<string>();
    /// <summary>Elimina solo i file piu' vecchi di N giorni (0 = tutti).</summary>
    public int OlderThanDays { get; set; }
    public bool Confirmed { get; set; }
    /// <summary>Se true, per C:\Windows\Temp viene avviato un processo elevato dedicato.</summary>
    public bool Elevate { get; set; }
}

public sealed class TempCleanResult
{
    public string Target { get; init; } = "";
    public bool Success { get; init; }
    public int DeletedFiles { get; init; }
    public int DeletedDirectories { get; init; }
    public long FreedBytes { get; init; }
    public int SkippedFiles { get; init; }
    public bool ElevationUsed { get; init; }
    public bool RequiresAdmin { get; init; }
    public string Message { get; init; } = "";
    public IReadOnlyList<string> Errors { get; init; } = Array.Empty<string>();
}
