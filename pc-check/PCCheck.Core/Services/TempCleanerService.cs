using System.Text.Json;
using Microsoft.Extensions.Logging;
using PCCheck.Core.Infrastructure;
using PCCheck.Core.Models;
using PCCheck.Core.Security;

namespace PCCheck.Core.Services;

/// <summary>
/// Analizza e, solo su richiesta confermata, pulisce %TEMP% e C:\Windows\Temp.
/// Ogni eliminazione e' limitata a queste due radici (controllo PathGuard) e i file in uso vengono saltati.
/// </summary>
public sealed class TempCleanerService : ITempCleanerService
{
    public const string UserTargetKey = "user";
    public const string WindowsTargetKey = "windows";
    private const int MaxFilesToEnumerate = 250_000;
    private const int MaxErrorsReported = 10;
    private static readonly TimeSpan CacheDuration = TimeSpan.FromSeconds(45);

    private readonly ILogger<TempCleanerService> _logger;
    private readonly object _cacheLock = new();
    private TempAnalysis? _cached;

    public TempCleanerService(ILogger<TempCleanerService> logger)
    {
        _logger = logger;
    }

    public static string UserTempPath()
    {
        var path = Path.GetTempPath();
        return Path.TrimEndingDirectorySeparator(path);
    }

    public static string? WindowsTempPath()
    {
        if (!OperatingSystem.IsWindows())
        {
            return null;
        }
        var windows = Environment.GetFolderPath(Environment.SpecialFolder.Windows);
        return string.IsNullOrEmpty(windows) ? null : Path.Combine(windows, "Temp");
    }

    public static IReadOnlyDictionary<string, (string Label, string? Path, bool RequiresAdmin)> Targets() =>
        new Dictionary<string, (string, string?, bool)>
        {
            [UserTargetKey] = ("Cartella temporanea dell'utente (%TEMP%)", UserTempPath(), false),
            [WindowsTargetKey] = ("Cartella temporanea di Windows (C:\\Windows\\Temp)", WindowsTempPath(), !ElevationHelper.IsAdministrator())
        };

    public Task<TempAnalysis> AnalyzeAsync(bool forceRefresh, CancellationToken cancellationToken = default)
    {
        lock (_cacheLock)
        {
            if (!forceRefresh && _cached is not null && DateTimeOffset.Now - _cached.AnalyzedAt < CacheDuration)
            {
                return Task.FromResult(_cached);
            }
        }
        return Task.Run(() =>
        {
            var targets = Targets().Select(kv => Analyze(kv.Key, kv.Value.Label, kv.Value.Path, kv.Value.RequiresAdmin, cancellationToken)).ToList();
            var analysis = new TempAnalysis
            {
                AnalyzedAt = DateTimeOffset.Now,
                Targets = targets,
                TotalReclaimableBytes = targets.Sum(t => t.TotalBytes)
            };
            lock (_cacheLock)
            {
                _cached = analysis;
            }
            return analysis;
        }, cancellationToken);
    }

    private TempTarget Analyze(string key, string label, string? path, bool requiresAdmin, CancellationToken cancellationToken)
    {
        if (string.IsNullOrEmpty(path) || !Directory.Exists(path))
        {
            return new TempTarget { Key = key, Label = label, Path = path ?? "", Exists = false, Accessible = false, RequiresAdmin = requiresAdmin, Error = "Cartella non presente." };
        }

        var cutoff = DateTime.UtcNow.AddDays(-7);
        long total = 0;
        long old = 0;
        int files = 0;
        int oldFiles = 0;
        int dirs = 0;
        var perFolder = new Dictionary<string, (long bytes, int files)>(StringComparer.OrdinalIgnoreCase);
        var options = new EnumerationOptions { IgnoreInaccessible = true, RecurseSubdirectories = true, AttributesToSkip = FileAttributes.ReparsePoint };
        var accessible = true;
        string? error = null;

        try
        {
            foreach (var dir in Directory.EnumerateDirectories(path, "*", options))
            {
                dirs++;
            }
            foreach (var file in Directory.EnumerateFiles(path, "*", options))
            {
                cancellationToken.ThrowIfCancellationRequested();
                if (++files > MaxFilesToEnumerate)
                {
                    error = $"Analisi interrotta dopo {MaxFilesToEnumerate:N0} file: la cartella è molto grande.";
                    break;
                }
                FileInfo info;
                try
                {
                    info = new FileInfo(file);
                    var length = info.Length;
                    total += length;
                    if (info.LastWriteTimeUtc < cutoff)
                    {
                        old += length;
                        oldFiles++;
                    }
                    var top = TopLevelFolder(path, file);
                    if (top is not null)
                    {
                        var current = perFolder.TryGetValue(top, out var v) ? v : (0, 0);
                        perFolder[top] = (current.bytes + length, current.files + 1);
                    }
                }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
                {
                    // File scomparso o non leggibile: si ignora.
                }
            }
        }
        catch (UnauthorizedAccessException ex)
        {
            accessible = false;
            error = "Accesso negato: " + ex.Message;
        }
        catch (IOException ex)
        {
            error = ex.Message;
        }

        var topFolders = perFolder
            .OrderByDescending(kv => kv.Value.bytes)
            .Take(8)
            .Select(kv => new TempFolderSummary { Path = kv.Key, Bytes = kv.Value.bytes, Files = kv.Value.files })
            .ToList();

        return new TempTarget
        {
            Key = key,
            Label = label,
            Path = path,
            Exists = true,
            Accessible = accessible,
            RequiresAdmin = requiresAdmin,
            FileCount = files,
            DirectoryCount = dirs,
            TotalBytes = total,
            OlderThan7DaysBytes = old,
            OlderThan7DaysFiles = oldFiles,
            TopFolders = topFolders,
            Error = error
        };
    }

    private static string? TopLevelFolder(string root, string file)
    {
        var relative = Path.GetRelativePath(root, file);
        var sep = relative.IndexOfAny(new[] { Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar });
        return sep < 0 ? "(file nella radice)" : relative[..sep];
    }

    public async Task<IReadOnlyList<TempCleanResult>> CleanAsync(TempCleanRequest request, CancellationToken cancellationToken = default)
    {
        var results = new List<TempCleanResult>();
        if (!request.Confirmed)
        {
            results.Add(new TempCleanResult { Target = "", Success = false, Message = "Pulizia non confermata: nessun file è stato toccato." });
            return results;
        }
        var targets = Targets();
        foreach (var key in request.Targets.Distinct())
        {
            if (!targets.TryGetValue(key, out var target))
            {
                results.Add(new TempCleanResult { Target = key, Success = false, Message = "Destinazione sconosciuta: ignorata." });
                continue;
            }
            if (key == WindowsTargetKey && target.RequiresAdmin && request.Elevate)
            {
                results.Add(await CleanElevatedAsync(key, request.OlderThanDays, cancellationToken));
                continue;
            }
            results.Add(CleanTarget(key, target.Path, request.OlderThanDays, cancellationToken));
        }
        lock (_cacheLock)
        {
            _cached = null;
        }
        return results;
    }

    /// <summary>Pulizia effettiva di una radice: usata sia dal processo normale sia dalla copia elevata.</summary>
    public TempCleanResult CleanTarget(string key, string? root, int olderThanDays, CancellationToken cancellationToken)
    {
        if (string.IsNullOrEmpty(root) || !Directory.Exists(root))
        {
            return new TempCleanResult { Target = key, Success = false, Message = "Cartella non presente." };
        }
        var normalizedRoot = PathGuard.Normalize(root);
        var cutoff = olderThanDays > 0 ? DateTime.UtcNow.AddDays(-olderThanDays) : (DateTime?)null;
        int deleted = 0, deletedDirs = 0, skipped = 0, denied = 0;
        long freed = 0;
        var errors = new List<string>();
        var options = new EnumerationOptions { IgnoreInaccessible = true, RecurseSubdirectories = true, AttributesToSkip = FileAttributes.ReparsePoint };

        List<string> files;
        try
        {
            files = Directory.EnumerateFiles(normalizedRoot, "*", options).Take(MaxFilesToEnumerate).ToList();
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            return new TempCleanResult { Target = key, Success = false, Message = "Impossibile elencare i file: " + ex.Message, RequiresAdmin = ex is UnauthorizedAccessException };
        }

        foreach (var file in files)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (!PathGuard.IsStrictlyInside(normalizedRoot, file))
            {
                skipped++;
                continue;
            }
            try
            {
                var info = new FileInfo(file);
                if (cutoff.HasValue && info.LastWriteTimeUtc >= cutoff.Value)
                {
                    continue;
                }
                var length = info.Length;
                if ((info.Attributes & FileAttributes.ReadOnly) != 0)
                {
                    info.Attributes &= ~FileAttributes.ReadOnly;
                }
                info.Delete();
                deleted++;
                freed += length;
            }
            catch (UnauthorizedAccessException)
            {
                skipped++;
                denied++;
            }
            catch (IOException ex)
            {
                // File in uso da un altro programma: viene lasciato dov'e'.
                skipped++;
                if (errors.Count < MaxErrorsReported)
                {
                    errors.Add(Path.GetFileName(file) + ": " + ex.Message);
                }
            }
        }

        // Rimozione delle cartelle rimaste vuote, dalla piu' profonda alla meno profonda.
        if (!cutoff.HasValue)
        {
            List<string> dirs;
            try
            {
                dirs = Directory.EnumerateDirectories(normalizedRoot, "*", options).OrderByDescending(d => d.Length).ToList();
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
            {
                dirs = new List<string>();
            }
            foreach (var dir in dirs)
            {
                if (!PathGuard.IsStrictlyInside(normalizedRoot, dir))
                {
                    continue;
                }
                try
                {
                    if (!Directory.EnumerateFileSystemEntries(dir).Any())
                    {
                        Directory.Delete(dir, recursive: false);
                        deletedDirs++;
                    }
                }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
                {
                    // Cartella in uso o protetta.
                }
            }
        }

        var requiresAdmin = denied > 0 && key == WindowsTargetKey && !ElevationHelper.IsAdministrator();
        var message = deleted == 0 && skipped == 0
            ? "Nessun file da eliminare."
            : $"Eliminati {deleted:N0} file ({FormatBytes(freed)}), saltati {skipped:N0} file in uso o protetti.";
        if (requiresAdmin)
        {
            message += " Per i file protetti servono i permessi di amministratore.";
        }
        _logger.LogInformation("Pulizia {Target}: {Deleted} file, {Freed} byte, {Skipped} saltati", key, deleted, freed, skipped);
        return new TempCleanResult
        {
            Target = key,
            Success = true,
            DeletedFiles = deleted,
            DeletedDirectories = deletedDirs,
            FreedBytes = freed,
            SkippedFiles = skipped,
            RequiresAdmin = requiresAdmin,
            Message = message,
            Errors = errors
        };
    }

    private async Task<TempCleanResult> CleanElevatedAsync(string key, int olderThanDays, CancellationToken cancellationToken)
    {
        var self = ElevationHelper.GetSelfExecutablePath();
        if (self is null)
        {
            return new TempCleanResult { Target = key, Success = false, Message = "Percorso dell'eseguibile non determinabile: impossibile avviare la copia elevata.", RequiresAdmin = true };
        }
        var resultFile = Path.Combine(PathGuard.GetAppDataDirectory(), $"clean-{Guid.NewGuid():N}.json");
        var args = new[] { "--task", "clean-temp", "--target", key, "--older-than", olderThanDays.ToString(System.Globalization.CultureInfo.InvariantCulture), "--result-file", resultFile };
        var run = await ElevationHelper.RunElevatedAsync(self, args, TimeSpan.FromMinutes(10), cancellationToken);
        try
        {
            if (run.UserCancelled)
            {
                return new TempCleanResult { Target = key, Success = false, Message = "Permessi di amministratore non concessi: nessun file eliminato in C:\\Windows\\Temp.", RequiresAdmin = true };
            }
            if (!run.Started)
            {
                return new TempCleanResult { Target = key, Success = false, Message = run.Error ?? "Avvio del processo elevato non riuscito.", RequiresAdmin = true };
            }
            if (File.Exists(resultFile))
            {
                var json = await File.ReadAllTextAsync(resultFile, cancellationToken);
                var parsed = JsonSerializer.Deserialize<TempCleanResult>(json, JsonOptions);
                if (parsed is not null)
                {
                    return new TempCleanResult
                    {
                        Target = parsed.Target,
                        Success = parsed.Success,
                        DeletedFiles = parsed.DeletedFiles,
                        DeletedDirectories = parsed.DeletedDirectories,
                        FreedBytes = parsed.FreedBytes,
                        SkippedFiles = parsed.SkippedFiles,
                        Message = parsed.Message,
                        Errors = parsed.Errors,
                        ElevationUsed = true
                    };
                }
            }
            return new TempCleanResult { Target = key, Success = false, Message = $"Il processo elevato è terminato (codice {run.ExitCode}) senza scrivere un esito.", RequiresAdmin = true, ElevationUsed = true };
        }
        finally
        {
            try
            {
                if (File.Exists(resultFile))
                {
                    File.Delete(resultFile);
                }
            }
            catch (IOException)
            {
            }
        }
    }

    /// <summary>
    /// Punto d'ingresso del processo elevato: <c>PCCheck.exe --task clean-temp --target windows --older-than N --result-file percorso</c>.
    /// Il file di esito deve trovarsi nella cartella dati di PC Check.
    /// </summary>
    public static int RunElevatedTask(string[] args, ILogger logger)
    {
        string? target = null;
        string? resultFile = null;
        var olderThan = 0;
        for (var i = 0; i < args.Length - 1; i++)
        {
            switch (args[i])
            {
                case "--target":
                    target = args[++i];
                    break;
                case "--older-than":
                    int.TryParse(args[++i], System.Globalization.NumberStyles.Integer, System.Globalization.CultureInfo.InvariantCulture, out olderThan);
                    break;
                case "--result-file":
                    resultFile = args[++i];
                    break;
            }
        }
        if (target is null || resultFile is null || !PathGuard.IsStrictlyInside(PathGuard.GetAppDataDirectory(), resultFile))
        {
            logger.LogError("Argomenti del compito elevato non validi");
            return 2;
        }
        var targets = Targets();
        if (!targets.TryGetValue(target, out var t))
        {
            return 2;
        }
        var service = new TempCleanerService(Microsoft.Extensions.Logging.Abstractions.NullLogger<TempCleanerService>.Instance);
        var result = service.CleanTarget(target, t.Path, Math.Max(0, olderThan), CancellationToken.None);
        File.WriteAllText(resultFile, JsonSerializer.Serialize(result, JsonOptions));
        return result.Success ? 0 : 1;
    }

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public static string FormatBytes(long bytes)
    {
        string[] units = { "B", "KB", "MB", "GB", "TB" };
        double value = bytes;
        var unit = 0;
        while (value >= 1024 && unit < units.Length - 1)
        {
            value /= 1024;
            unit++;
        }
        return $"{value:0.#} {units[unit]}";
    }
}
