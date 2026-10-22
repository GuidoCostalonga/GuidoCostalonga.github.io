using PCCheck.Core.Models;

namespace PCCheck.Core.Infrastructure;

/// <summary>Logica pura (testabile) per il campionamento dei processi: percentuali CPU, soglie e processi protetti.</summary>
public static class ProcessSampler
{
    public const double HighCpuPercent = 20.0;
    public const long HighMemoryBytes = 700L * 1024 * 1024;

    private static readonly HashSet<string> ProtectedNames = new(StringComparer.OrdinalIgnoreCase)
    {
        "System", "Idle", "Registry", "Memory Compression", "smss", "csrss", "wininit", "winlogon",
        "services", "lsass", "svchost", "dwm", "fontdrvhost", "explorer", "MsMpEng", "NisSrv",
        "SecurityHealthService", "sihost", "ctfmon", "RuntimeBroker", "audiodg", "spoolsv",
        "WmiPrvSE", "conhost", "LogonUI", "SearchIndexer", "ShellExperienceHost", "StartMenuExperienceHost",
        "TextInputHost", "SgrmBroker", "MpDefenderCoreService", "PCCheck"
    };

    /// <summary>
    /// Percentuale di CPU usata da un processo tra due campionamenti.
    /// Il valore e' normalizzato sul numero di core (100 = tutti i core occupati).
    /// </summary>
    public static double ComputeCpuPercent(TimeSpan before, TimeSpan after, TimeSpan interval, int cores)
    {
        if (interval <= TimeSpan.Zero || cores <= 0)
        {
            return 0;
        }
        var delta = after - before;
        if (delta <= TimeSpan.Zero)
        {
            return 0;
        }
        var percent = delta.TotalMilliseconds / (interval.TotalMilliseconds * cores) * 100.0;
        return Math.Clamp(Math.Round(percent, 1), 0, 100);
    }

    /// <summary>Percentuale CPU del sistema dai contatori di GetSystemTimes (tick da 100 ns).</summary>
    public static double ComputeSystemCpuPercent(ulong idleBefore, ulong totalBefore, ulong idleAfter, ulong totalAfter)
    {
        if (totalAfter <= totalBefore)
        {
            return 0;
        }
        var total = (double)(totalAfter - totalBefore);
        var idle = (double)(idleAfter >= idleBefore ? idleAfter - idleBefore : 0);
        var busy = Math.Max(0, total - idle);
        return Math.Clamp(Math.Round(busy / total * 100.0, 1), 0, 100);
    }

    public static bool IsProtected(string? processName, int pid)
    {
        if (pid >= 0 && pid <= 4)
        {
            return true;
        }
        if (string.IsNullOrWhiteSpace(processName))
        {
            return false;
        }
        var name = processName.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)
            ? processName[..^4]
            : processName;
        return ProtectedNames.Contains(name);
    }

    public static bool IsHighImpact(double cpuPercent, long memoryBytes) =>
        cpuPercent >= HighCpuPercent || memoryBytes >= HighMemoryBytes;

    /// <summary>
    /// Un processo e' "sconosciuto" quando il suo eseguibile non si trova nelle cartelle di sistema o dei programmi
    /// (ad esempio in %TEMP%, %APPDATA% o nei Download) oppure non e' leggibile.
    /// </summary>
    public static bool IsUnknownLocation(string? path, IReadOnlyList<string> trustedRoots)
    {
        if (string.IsNullOrWhiteSpace(path))
        {
            return false;
        }
        foreach (var root in trustedRoots)
        {
            if (!string.IsNullOrEmpty(root) && Security.PathGuard.IsInside(root, path))
            {
                return false;
            }
        }
        return true;
    }

    public static IReadOnlyList<string> DefaultTrustedRoots()
    {
        var roots = new List<string>();
        void Add(Environment.SpecialFolder folder)
        {
            var p = Environment.GetFolderPath(folder);
            if (!string.IsNullOrEmpty(p))
            {
                roots.Add(p);
            }
        }
        Add(Environment.SpecialFolder.Windows);
        Add(Environment.SpecialFolder.ProgramFiles);
        Add(Environment.SpecialFolder.ProgramFilesX86);
        var programW6432 = Environment.GetEnvironmentVariable("ProgramW6432");
        if (!string.IsNullOrEmpty(programW6432))
        {
            roots.Add(programW6432);
        }
        var local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        if (!string.IsNullOrEmpty(local))
        {
            // Store Microsoft e app installate per utente da fornitori noti (Teams, Discord, VS Code...).
            roots.Add(Path.Combine(local, "Microsoft"));
            roots.Add(Path.Combine(local, "Programs"));
        }
        return roots;
    }

    /// <summary>Ordina e seleziona i processi da mostrare: prima per CPU, poi per memoria.</summary>
    public static IReadOnlyList<ProcessInfo> SelectTop(IEnumerable<ProcessInfo> processes, int count) =>
        processes.OrderByDescending(p => p.CpuPercent).ThenByDescending(p => p.MemoryBytes).Take(count).ToList();
}
