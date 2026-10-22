using PCCheck.Core.Infrastructure;
using PCCheck.Core.Models;

namespace PCCheck.Core.Services.Mock;

public sealed class MockPerformanceService : IPerformanceService
{
    private readonly Random _random = new(42);
    private readonly HashSet<int> _terminated = new();
    private readonly object _lock = new();

    private sealed record Seed(int Pid, string Name, string? Description, string? Company, string? Path, double Cpu, long MemoryMb, bool Unknown);

    private static readonly Seed[] Seeds =
    {
        new(4, "System", "NT Kernel & System", "Microsoft Corporation", null, 1.2, 24, false),
        new(1180, "svchost", "Host processo per servizi Windows", "Microsoft Corporation", @"C:\Windows\System32\svchost.exe", 0.8, 96, false),
        new(2456, "MsMpEng", "Antimalware Service Executable", "Microsoft Corporation", @"C:\ProgramData\Microsoft\Windows Defender\Platform\4.18.24090.11-0\MsMpEng.exe", 6.5, 310, false),
        new(5120, "msedge", "Microsoft Edge", "Microsoft Corporation", @"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe", 14.2, 1480, false),
        new(6032, "Teams", "Microsoft Teams", "Microsoft Corporation", @"C:\Users\utente.demo\AppData\Local\Microsoft\Teams\current\Teams.exe", 9.1, 820, false),
        new(7044, "OneDrive", "Microsoft OneDrive", "Microsoft Corporation", @"C:\Users\utente.demo\AppData\Local\Microsoft\OneDrive\OneDrive.exe", 0.4, 140, false),
        new(7788, "explorer", "Esplora risorse", "Microsoft Corporation", @"C:\Windows\explorer.exe", 1.1, 210, false),
        new(8120, "SearchIndexer", "Microsoft Windows Search Indexer", "Microsoft Corporation", @"C:\Windows\System32\SearchIndexer.exe", 22.7, 180, false),
        new(9316, "WINWORD", "Microsoft Word", "Microsoft Corporation", @"C:\Program Files\Microsoft Office\root\Office16\WINWORD.EXE", 2.3, 460, false),
        new(9904, "Spotify", "Spotify", "Spotify AB", @"C:\Users\utente.demo\AppData\Roaming\Spotify\Spotify.exe", 3.6, 390, true),
        new(10240, "SpeedUpMyPC", null, null, @"C:\Users\utente.demo\AppData\Local\Temp\SpeedUpMyPC\SpeedUpMyPC.exe", 31.4, 240, true),
        new(10588, "dwm", "Gestione finestre desktop", "Microsoft Corporation", @"C:\Windows\System32\dwm.exe", 2.0, 120, false),
        new(11012, "RuntimeBroker", "Runtime Broker", "Microsoft Corporation", @"C:\Windows\System32\RuntimeBroker.exe", 0.1, 35, false),
        new(11460, "ctfmon", "CTF Loader", "Microsoft Corporation", @"C:\Windows\System32\ctfmon.exe", 0.0, 18, false),
        new(12020, "Discord", "Discord", "Discord Inc.", @"C:\Users\utente.demo\AppData\Local\Discord\app-1.0.9163\Discord.exe", 4.4, 520, true),
        new(12344, "PCCheck", "PC Check", "PC Check", @"C:\Users\utente.demo\Desktop\PCCheck.exe", 0.6, 95, false)
    };

    public async Task<PerformanceSnapshot> GetSnapshotAsync(int sampleMilliseconds, CancellationToken cancellationToken = default)
    {
        sampleMilliseconds = Math.Clamp(sampleMilliseconds, 500, 5000);
        await Task.Delay(Math.Min(sampleMilliseconds, 1200), cancellationToken);

        List<ProcessInfo> processes;
        lock (_lock)
        {
            processes = Seeds.Where(s => !_terminated.Contains(s.Pid)).Select(s =>
            {
                var cpu = Math.Clamp(Math.Round(s.Cpu + (_random.NextDouble() - 0.5) * 3, 1), 0, 100);
                var mem = s.MemoryMb * 1024L * 1024L + _random.Next(-5, 5) * 1024L * 1024L;
                return new ProcessInfo
                {
                    Pid = s.Pid,
                    Name = s.Name,
                    Description = s.Description,
                    Company = s.Company,
                    Path = s.Path,
                    CpuPercent = cpu,
                    MemoryBytes = Math.Max(mem, 1024 * 1024),
                    Threads = 4 + _random.Next(0, 40),
                    StartedAt = DateTimeOffset.Now.AddHours(-_random.Next(1, 30)),
                    Protected = ProcessSampler.IsProtected(s.Name, s.Pid),
                    HighImpact = ProcessSampler.IsHighImpact(cpu, mem),
                    Unknown = s.Unknown
                };
            }).ToList();
        }

        var totalMem = 16L * 1024 * 1024 * 1024;
        var usedMem = processes.Sum(p => p.MemoryBytes) + 3L * 1024 * 1024 * 1024;
        var cpu = Math.Clamp(Math.Round(processes.Sum(p => p.CpuPercent) * 0.6 + 4, 1), 0, 100);

        return new PerformanceSnapshot
        {
            SampledAt = DateTimeOffset.Now,
            SampleMilliseconds = sampleMilliseconds,
            CpuPercent = cpu,
            CpuCores = 8,
            MemoryTotalBytes = totalMem,
            MemoryUsedBytes = usedMem,
            MemoryPercent = Math.Round(usedMem * 100.0 / totalMem, 1),
            UptimeSeconds = 3 * 86400 + 5 * 3600 + 12 * 60,
            ProcessCount = 187,
            Disks = new[]
            {
                new DiskInfo { Name = @"C:\", Label = "Windows", Format = "NTFS", DriveType = "Disco fisso", TotalBytes = 476L * 1024 * 1024 * 1024, FreeBytes = 38L * 1024 * 1024 * 1024, UsedPercent = 92.0 },
                new DiskInfo { Name = @"D:\", Label = "Dati", Format = "NTFS", DriveType = "Disco fisso", TotalBytes = 931L * 1024 * 1024 * 1024, FreeBytes = 540L * 1024 * 1024 * 1024, UsedPercent = 42.0 }
            },
            TopProcesses = ProcessSampler.SelectTop(processes, 25),
            HighImpactProcesses = processes.Where(p => p.HighImpact).OrderByDescending(p => p.CpuPercent).ToList()
        };
    }

    public Task<ActionOutcome> TerminateProcessAsync(int pid, string expectedName, bool allowElevation, CancellationToken cancellationToken = default)
    {
        if (!Security.CommandPolicy.IsKillablePid(pid))
        {
            return Task.FromResult(ActionOutcome.Fail("PID non valido o processo di base del sistema: operazione rifiutata."));
        }
        if (ProcessSampler.IsProtected(expectedName, pid))
        {
            return Task.FromResult(ActionOutcome.Fail($"Il processo {expectedName} è un componente di Windows e non viene terminato."));
        }
        lock (_lock)
        {
            var seed = Seeds.FirstOrDefault(s => s.Pid == pid);
            if (seed is null || _terminated.Contains(pid))
            {
                return Task.FromResult(ActionOutcome.Fail("Il processo non esiste più (è già terminato)."));
            }
            if (!string.Equals(seed.Name, expectedName, StringComparison.OrdinalIgnoreCase))
            {
                return Task.FromResult(ActionOutcome.Fail($"Il PID {pid} ora appartiene a \"{seed.Name}\": operazione annullata per sicurezza."));
            }
            _terminated.Add(pid);
        }
        return Task.FromResult(ActionOutcome.Ok($"Processo {expectedName} (PID {pid}) terminato (simulazione)."));
    }
}
