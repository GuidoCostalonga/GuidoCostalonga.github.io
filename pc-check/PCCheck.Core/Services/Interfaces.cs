using PCCheck.Core.Models;

namespace PCCheck.Core.Services;

public interface IDefenderService
{
    Task<DefenderStatus> GetStatusAsync(CancellationToken cancellationToken = default);
    Task<IReadOnlyList<ThreatInfo>> GetThreatsAsync(CancellationToken cancellationToken = default);
    Task<ScanStatus> StartScanAsync(ScanType type, CancellationToken cancellationToken = default);
    ScanStatus GetScanStatus();
    Task<ScanStatus> CancelScanAsync();
}

public interface IPerformanceService
{
    /// <summary>Campiona il sistema per <paramref name="sampleMilliseconds"/> millisecondi (delta CPU reale, non istantaneo).</summary>
    Task<PerformanceSnapshot> GetSnapshotAsync(int sampleMilliseconds, CancellationToken cancellationToken = default);
    Task<ActionOutcome> TerminateProcessAsync(int pid, string expectedName, bool allowElevation, CancellationToken cancellationToken = default);
}

public interface IStartupService
{
    Task<IReadOnlyList<StartupEntry>> GetEntriesAsync(CancellationToken cancellationToken = default);
    Task<ActionOutcome> SetEnabledAsync(string id, bool enabled, CancellationToken cancellationToken = default);
}

public interface IInstalledAppsService
{
    Task<IReadOnlyList<InstalledApp>> GetAppsAsync(CancellationToken cancellationToken = default);
    Task<ActionOutcome> UninstallAsync(string id, CancellationToken cancellationToken = default);
    Task<ActionOutcome> OpenAppsSettingsAsync();
    bool IsWingetAvailable();
}

public interface ITempCleanerService
{
    Task<TempAnalysis> AnalyzeAsync(bool forceRefresh, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<TempCleanResult>> CleanAsync(TempCleanRequest request, CancellationToken cancellationToken = default);
}

public interface ISystemInfoService
{
    SystemInfo GetInfo();
}
