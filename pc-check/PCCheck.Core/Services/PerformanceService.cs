using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.Versioning;
using Microsoft.Extensions.Logging;
using PCCheck.Core.Infrastructure;
using PCCheck.Core.Models;
using PCCheck.Core.Security;

namespace PCCheck.Core.Services;

/// <summary>
/// Misura CPU, memoria e dischi con System.Diagnostics.Process e System.IO.DriveInfo.
/// La CPU viene calcolata come differenza tra due campionamenti, mai da un valore istantaneo.
/// </summary>
public sealed class PerformanceService : IPerformanceService
{
    private const int TopProcessCount = 25;
    private readonly ILogger<PerformanceService> _logger;
    private readonly IReadOnlyList<string> _trustedRoots;

    public PerformanceService(ILogger<PerformanceService> logger)
    {
        _logger = logger;
        _trustedRoots = ProcessSampler.DefaultTrustedRoots();
    }

    public async Task<PerformanceSnapshot> GetSnapshotAsync(int sampleMilliseconds, CancellationToken cancellationToken = default)
    {
        sampleMilliseconds = Math.Clamp(sampleMilliseconds, 500, 5000);
        var cores = Math.Max(1, Environment.ProcessorCount);
        var interval = TimeSpan.FromMilliseconds(sampleMilliseconds);

        var first = SampleProcesses();
        var haveSystemTimes = TryGetSystemTimes(out var idleBefore, out var totalBefore);
        var stopwatch = Stopwatch.StartNew();
        await Task.Delay(interval, cancellationToken);
        stopwatch.Stop();
        var elapsed = stopwatch.Elapsed;
        var second = SampleProcesses();
        TryGetSystemTimes(out var idleAfter, out var totalAfter);

        var processes = new List<ProcessInfo>();
        double cpuSum = 0;
        foreach (var (pid, after) in second)
        {
            var cpu = 0.0;
            if (first.TryGetValue(pid, out var before) && before.Name == after.Name)
            {
                cpu = ProcessSampler.ComputeCpuPercent(before.Cpu, after.Cpu, elapsed, cores);
            }
            cpuSum += cpu;
            var isProtected = ProcessSampler.IsProtected(after.Name, pid);
            processes.Add(new ProcessInfo
            {
                Pid = pid,
                Name = after.Name,
                Description = after.Description,
                Company = after.Company,
                Path = after.Path,
                CpuPercent = cpu,
                MemoryBytes = after.Memory,
                Threads = after.Threads,
                StartedAt = after.StartedAt,
                Protected = isProtected,
                HighImpact = ProcessSampler.IsHighImpact(cpu, after.Memory),
                Unknown = !isProtected && ProcessSampler.IsUnknownLocation(after.Path, _trustedRoots)
            });
        }

        var cpuPercent = haveSystemTimes && totalAfter > totalBefore
            ? ProcessSampler.ComputeSystemCpuPercent(idleBefore, totalBefore, idleAfter, totalAfter)
            : Math.Clamp(Math.Round(cpuSum, 1), 0, 100);

        var (memTotal, memUsed) = ReadMemory(processes);

        return new PerformanceSnapshot
        {
            SampledAt = DateTimeOffset.Now,
            SampleMilliseconds = (int)elapsed.TotalMilliseconds,
            CpuPercent = cpuPercent,
            CpuCores = cores,
            MemoryTotalBytes = memTotal,
            MemoryUsedBytes = memUsed,
            MemoryPercent = memTotal > 0 ? Math.Round(memUsed * 100.0 / memTotal, 1) : 0,
            UptimeSeconds = Environment.TickCount64 / 1000.0,
            ProcessCount = processes.Count,
            Disks = ReadDisks(),
            TopProcesses = ProcessSampler.SelectTop(processes, TopProcessCount),
            HighImpactProcesses = processes.Where(p => p.HighImpact).OrderByDescending(p => p.CpuPercent).ThenByDescending(p => p.MemoryBytes).ToList()
        };
    }

    private sealed record ProcessSample(string Name, TimeSpan Cpu, long Memory, int Threads, string? Path, string? Description, string? Company, DateTimeOffset? StartedAt);

    private Dictionary<int, ProcessSample> SampleProcesses()
    {
        var map = new Dictionary<int, ProcessSample>();
        Process[] all;
        try
        {
            all = Process.GetProcesses();
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Elenco processi non disponibile");
            return map;
        }

        foreach (var process in all)
        {
            using (process)
            {
                try
                {
                    var cpu = TimeSpan.Zero;
                    try
                    {
                        cpu = process.TotalProcessorTime;
                    }
                    catch (Exception ex) when (ex is Win32Exception or InvalidOperationException or NotSupportedException)
                    {
                        // Processi protetti: tempo CPU non leggibile da un utente normale.
                    }

                    string? path = null;
                    string? description = null;
                    string? company = null;
                    try
                    {
                        path = process.MainModule?.FileName;
                        if (path is not null)
                        {
                            var info = FileVersionInfo.GetVersionInfo(path);
                            description = NullIfEmpty(info.FileDescription);
                            company = NullIfEmpty(info.CompanyName);
                        }
                    }
                    catch (Exception ex) when (ex is Win32Exception or InvalidOperationException or NotSupportedException or FileNotFoundException or UnauthorizedAccessException)
                    {
                        // Percorso non leggibile (processo di sistema o di un altro utente).
                    }

                    DateTimeOffset? started = null;
                    try
                    {
                        started = new DateTimeOffset(process.StartTime);
                    }
                    catch (Exception ex) when (ex is Win32Exception or InvalidOperationException or NotSupportedException)
                    {
                    }

                    int threads = 0;
                    try
                    {
                        threads = process.Threads.Count;
                    }
                    catch (Exception ex) when (ex is Win32Exception or InvalidOperationException)
                    {
                    }

                    map[process.Id] = new ProcessSample(process.ProcessName, cpu, process.WorkingSet64, threads, path, description, company, started);
                }
                catch (Exception ex) when (ex is Win32Exception or InvalidOperationException)
                {
                    // Il processo e' terminato durante la lettura.
                }
            }
        }
        return map;
    }

    private static string? NullIfEmpty(string? value) => string.IsNullOrWhiteSpace(value) ? null : value.Trim();

    private static bool TryGetSystemTimes(out ulong idle, out ulong total)
    {
        idle = 0;
        total = 0;
        if (OperatingSystem.IsWindows())
        {
            return WindowsNative.TryGetSystemTimes(out idle, out total);
        }
        return false;
    }

    private static (long total, long used) ReadMemory(IReadOnlyList<ProcessInfo> processes)
    {
        if (OperatingSystem.IsWindows() && WindowsNative.TryGetMemory(out var total, out var available))
        {
            return (total, Math.Max(0, total - available));
        }
        var info = GC.GetGCMemoryInfo();
        var totalFallback = info.TotalAvailableMemoryBytes;
        var usedFallback = processes.Sum(p => p.MemoryBytes);
        return (totalFallback, Math.Min(usedFallback, totalFallback));
    }

    private IReadOnlyList<DiskInfo> ReadDisks()
    {
        var list = new List<DiskInfo>();
        DriveInfo[] drives;
        try
        {
            drives = DriveInfo.GetDrives();
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Elenco dischi non disponibile");
            return list;
        }
        foreach (var drive in drives)
        {
            try
            {
                if (!drive.IsReady || drive.DriveType == DriveType.CDRom || drive.DriveType == DriveType.Ram)
                {
                    continue;
                }
                if (!OperatingSystem.IsWindows() && drive.TotalSize == 0)
                {
                    continue;
                }
                var total = drive.TotalSize;
                var free = drive.AvailableFreeSpace;
                list.Add(new DiskInfo
                {
                    Name = drive.Name,
                    Label = SafeLabel(drive),
                    Format = drive.DriveFormat,
                    DriveType = DriveTypeLabel(drive.DriveType),
                    TotalBytes = total,
                    FreeBytes = free,
                    UsedPercent = total > 0 ? Math.Round((total - free) * 100.0 / total, 1) : 0
                });
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
            {
                // Unita' non accessibile (rete scollegata, supporto rimosso).
            }
        }
        return list;
    }

    private static string SafeLabel(DriveInfo drive)
    {
        try
        {
            return drive.VolumeLabel;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            return "";
        }
    }

    private static string DriveTypeLabel(DriveType type) => type switch
    {
        DriveType.Fixed => "Disco fisso",
        DriveType.Removable => "Rimovibile",
        DriveType.Network => "Rete",
        DriveType.CDRom => "Ottico",
        _ => "Altro"
    };

    public async Task<ActionOutcome> TerminateProcessAsync(int pid, string expectedName, bool allowElevation, CancellationToken cancellationToken = default)
    {
        if (!CommandPolicy.IsKillablePid(pid))
        {
            return ActionOutcome.Fail("PID non valido o processo di base del sistema: operazione rifiutata.");
        }
        if (ProcessSampler.IsProtected(expectedName, pid))
        {
            return ActionOutcome.Fail($"Il processo {expectedName} è un componente di Windows o di PC Check e non viene terminato.");
        }

        Process process;
        try
        {
            process = Process.GetProcessById(pid);
        }
        catch (ArgumentException)
        {
            return ActionOutcome.Fail("Il processo non esiste più (è già terminato).");
        }

        using (process)
        {
            if (!string.Equals(process.ProcessName, expectedName, StringComparison.OrdinalIgnoreCase))
            {
                return ActionOutcome.Fail($"Il PID {pid} ora appartiene a \"{process.ProcessName}\", non a \"{expectedName}\": operazione annullata per sicurezza.");
            }
            if (ProcessSampler.IsProtected(process.ProcessName, pid))
            {
                return ActionOutcome.Fail("Processo protetto: operazione rifiutata.");
            }
            try
            {
                process.Kill(entireProcessTree: false);
                await process.WaitForExitAsync(cancellationToken);
                return ActionOutcome.Ok($"Processo {expectedName} (PID {pid}) terminato.");
            }
            catch (Win32Exception ex) when (ex.NativeErrorCode == 5)
            {
                if (!allowElevation)
                {
                    return ActionOutcome.Fail("Permessi insufficienti per terminare questo processo. Sono necessari i permessi di amministratore.", requiresAdmin: true);
                }
                return await TerminateElevatedAsync(pid, expectedName, cancellationToken);
            }
            catch (InvalidOperationException)
            {
                return ActionOutcome.Ok("Il processo era già terminato.");
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Terminazione processo {Pid} non riuscita", pid);
                return ActionOutcome.Fail("Terminazione non riuscita: " + ex.Message);
            }
        }
    }

    [SupportedOSPlatform("windows")]
    private static async Task<ActionOutcome> TerminateElevatedAsync(int pid, string expectedName, CancellationToken cancellationToken)
    {
        var system = Environment.GetFolderPath(Environment.SpecialFolder.System);
        var taskkill = Path.Combine(system, "taskkill.exe");
        var args = new[] { "/PID", pid.ToString(System.Globalization.CultureInfo.InvariantCulture), "/F" };
        var result = await ElevationHelper.RunElevatedAsync(taskkill, args, TimeSpan.FromMinutes(2), cancellationToken);
        if (result.UserCancelled)
        {
            return ActionOutcome.Cancelled(result.Error ?? "Operazione annullata.");
        }
        if (!result.Started)
        {
            return ActionOutcome.Fail(result.Error ?? "Avvio di taskkill elevato non riuscito.", requiresAdmin: true);
        }
        return result.ExitCode == 0
            ? ActionOutcome.Ok($"Processo {expectedName} (PID {pid}) terminato con permessi di amministratore.")
            : ActionOutcome.Fail($"taskkill ha restituito il codice {result.ExitCode}.", requiresAdmin: true);
    }
}
