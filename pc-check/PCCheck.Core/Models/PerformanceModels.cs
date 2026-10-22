namespace PCCheck.Core.Models;

public sealed class DiskInfo
{
    public string Name { get; init; } = "";
    public string Label { get; init; } = "";
    public string Format { get; init; } = "";
    public string DriveType { get; init; } = "";
    public long TotalBytes { get; init; }
    public long FreeBytes { get; init; }
    public double UsedPercent { get; init; }
}

public sealed class ProcessInfo
{
    public int Pid { get; init; }
    public string Name { get; init; } = "";
    public string? Description { get; init; }
    public string? Company { get; init; }
    public string? Path { get; init; }
    public double CpuPercent { get; init; }
    public long MemoryBytes { get; init; }
    public int Threads { get; init; }
    public DateTimeOffset? StartedAt { get; init; }
    /// <summary>Processo di sistema che non deve essere terminato.</summary>
    public bool Protected { get; init; }
    /// <summary>Consumo elevato di CPU o memoria nel campionamento.</summary>
    public bool HighImpact { get; init; }
    /// <summary>Percorso non leggibile o fuori da cartelle di sistema/programmi: merita un controllo.</summary>
    public bool Unknown { get; init; }
}

public sealed class PerformanceSnapshot
{
    public DateTimeOffset SampledAt { get; init; }
    public int SampleMilliseconds { get; init; }
    public double CpuPercent { get; init; }
    public int CpuCores { get; init; }
    public long MemoryTotalBytes { get; init; }
    public long MemoryUsedBytes { get; init; }
    public double MemoryPercent { get; init; }
    public double UptimeSeconds { get; init; }
    public int ProcessCount { get; init; }
    public IReadOnlyList<DiskInfo> Disks { get; init; } = Array.Empty<DiskInfo>();
    public IReadOnlyList<ProcessInfo> TopProcesses { get; init; } = Array.Empty<ProcessInfo>();
    public IReadOnlyList<ProcessInfo> HighImpactProcesses { get; init; } = Array.Empty<ProcessInfo>();
    public string? Error { get; init; }
}
