using PCCheck.Core.Models;

namespace PCCheck.Core.Services.Mock;

public sealed class MockTempCleanerService : ITempCleanerService
{
    private readonly object _lock = new();
    private long _userBytes = 2_340_000_000;
    private int _userFiles = 18_420;
    private long _windowsBytes = 812_000_000;
    private int _windowsFiles = 3_104;

    public Task<TempAnalysis> AnalyzeAsync(bool forceRefresh, CancellationToken cancellationToken = default)
    {
        lock (_lock)
        {
            var targets = new List<TempTarget>
            {
                new()
                {
                    Key = TempCleanerService.UserTargetKey,
                    Label = "Cartella temporanea dell'utente (%TEMP%)",
                    Path = @"C:\Users\utente.demo\AppData\Local\Temp",
                    Exists = true,
                    Accessible = true,
                    RequiresAdmin = false,
                    FileCount = _userFiles,
                    DirectoryCount = _userFiles > 0 ? 642 : 0,
                    TotalBytes = _userBytes,
                    OlderThan7DaysBytes = (long)(_userBytes * 0.7),
                    OlderThan7DaysFiles = (int)(_userFiles * 0.65),
                    TopFolders = _userFiles == 0 ? Array.Empty<TempFolderSummary>() : new[]
                    {
                        new TempFolderSummary { Path = "chrome_installer", Bytes = 610_000_000, Files = 12 },
                        new TempFolderSummary { Path = "Teams", Bytes = 420_000_000, Files = 3_200 },
                        new TempFolderSummary { Path = "7zS8A3F", Bytes = 190_000_000, Files = 48 },
                        new TempFolderSummary { Path = "(file nella radice)", Bytes = 160_000_000, Files = 2_900 },
                        new TempFolderSummary { Path = "WPDNSE", Bytes = 90_000_000, Files = 410 }
                    }
                },
                new()
                {
                    Key = TempCleanerService.WindowsTargetKey,
                    Label = "Cartella temporanea di Windows (C:\\Windows\\Temp)",
                    Path = @"C:\Windows\Temp",
                    Exists = true,
                    Accessible = true,
                    RequiresAdmin = true,
                    FileCount = _windowsFiles,
                    DirectoryCount = _windowsFiles > 0 ? 88 : 0,
                    TotalBytes = _windowsBytes,
                    OlderThan7DaysBytes = (long)(_windowsBytes * 0.9),
                    OlderThan7DaysFiles = (int)(_windowsFiles * 0.9),
                    TopFolders = _windowsFiles == 0 ? Array.Empty<TempFolderSummary>() : new[]
                    {
                        new TempFolderSummary { Path = "MsEdgeCrashpad", Bytes = 380_000_000, Files = 240 },
                        new TempFolderSummary { Path = "(file nella radice)", Bytes = 300_000_000, Files = 2_600 },
                        new TempFolderSummary { Path = "DiagTrack_alternativeTrace", Bytes = 60_000_000, Files = 20 }
                    }
                }
            };
            return Task.FromResult(new TempAnalysis
            {
                AnalyzedAt = DateTimeOffset.Now,
                Targets = targets,
                TotalReclaimableBytes = _userBytes + _windowsBytes
            });
        }
    }

    public Task<IReadOnlyList<TempCleanResult>> CleanAsync(TempCleanRequest request, CancellationToken cancellationToken = default)
    {
        var results = new List<TempCleanResult>();
        if (!request.Confirmed)
        {
            results.Add(new TempCleanResult { Success = false, Message = "Pulizia non confermata: nessun file è stato toccato." });
            return Task.FromResult<IReadOnlyList<TempCleanResult>>(results);
        }
        lock (_lock)
        {
            var factor = request.OlderThanDays > 0 ? 0.7 : 1.0;
            foreach (var key in request.Targets.Distinct())
            {
                if (key == TempCleanerService.UserTargetKey)
                {
                    var freed = (long)(_userBytes * factor);
                    var deleted = (int)(_userFiles * factor) - 37;
                    _userBytes -= freed;
                    _userFiles -= Math.Max(0, deleted);
                    results.Add(new TempCleanResult { Target = key, Success = true, DeletedFiles = Math.Max(0, deleted), DeletedDirectories = 120, FreedBytes = freed, SkippedFiles = 37, Message = $"Eliminati {Math.Max(0, deleted):N0} file ({TempCleanerService.FormatBytes(freed)}), saltati 37 file in uso (simulazione)." });
                }
                else if (key == TempCleanerService.WindowsTargetKey)
                {
                    if (!request.Elevate)
                    {
                        results.Add(new TempCleanResult { Target = key, Success = true, DeletedFiles = 12, FreedBytes = 4_000_000, SkippedFiles = _windowsFiles - 12, RequiresAdmin = true, Message = "Eliminati 12 file (3,8 MB), saltati i file protetti: servono i permessi di amministratore (simulazione)." });
                        _windowsBytes -= 4_000_000;
                        _windowsFiles -= 12;
                    }
                    else
                    {
                        var freed = (long)(_windowsBytes * factor);
                        var deleted = (int)(_windowsFiles * factor);
                        _windowsBytes -= freed;
                        _windowsFiles -= deleted;
                        results.Add(new TempCleanResult { Target = key, Success = true, DeletedFiles = deleted, DeletedDirectories = 30, FreedBytes = freed, SkippedFiles = 5, ElevationUsed = true, Message = $"Eliminati {deleted:N0} file ({TempCleanerService.FormatBytes(freed)}) con permessi di amministratore (simulazione)." });
                    }
                }
                else
                {
                    results.Add(new TempCleanResult { Target = key, Success = false, Message = "Destinazione sconosciuta: ignorata." });
                }
            }
        }
        return Task.FromResult<IReadOnlyList<TempCleanResult>>(results);
    }
}
