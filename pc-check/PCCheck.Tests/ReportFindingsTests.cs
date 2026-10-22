using PCCheck.Core.Models;
using PCCheck.Core.Services;
using Xunit;

namespace PCCheck.Tests;

public class ReportFindingsTests
{
    private static DefenderStatus HealthyDefender() => new()
    {
        Available = true, AntivirusEnabled = true, RealTimeProtectionEnabled = true, SignatureAgeDays = 0, QuickScanAgeDays = 1
    };

    private static PerformanceSnapshot CalmPerformance() => new()
    {
        CpuPercent = 10, MemoryPercent = 40,
        Disks = new[] { new DiskInfo { Name = @"C:\", TotalBytes = 100, FreeBytes = 50 } }
    };

    [Fact]
    public void HealthySystem_ProducesSingleInformativeFinding()
    {
        var findings = ReportService.BuildFindings(HealthyDefender(), Array.Empty<ThreatInfo>(), CalmPerformance(), Array.Empty<StartupEntry>(), Array.Empty<InstalledApp>(), new TempAnalysis());
        var single = Assert.Single(findings);
        Assert.Equal(FindingSeverity.Info, single.Severity);
        Assert.Equal("all-good", single.Kind);
    }

    [Fact]
    public void ActiveThreat_IsCriticalAndFirst()
    {
        var threats = new[] { new ThreatInfo { Name = "Trojan:Win32/Test", IsActive = true, Status = "Rilevata" } };
        var defender = new DefenderStatus { Available = true, AntivirusEnabled = false };
        var findings = ReportService.BuildFindings(defender, threats, CalmPerformance(), Array.Empty<StartupEntry>(), Array.Empty<InstalledApp>(), new TempAnalysis());
        Assert.Equal(FindingSeverity.Critical, findings[0].Severity);
        Assert.Equal("threat", findings[0].Kind);
        Assert.Contains(findings, f => f.Kind == "defender-off" && f.Severity == FindingSeverity.High);
    }

    [Fact]
    public void HandledThreats_AreOnlyInformative()
    {
        var threats = new[] { new ThreatInfo { Name = "PUA:Win32/Test", IsActive = false, Status = "In quarantena" } };
        var findings = ReportService.BuildFindings(HealthyDefender(), threats, CalmPerformance(), Array.Empty<StartupEntry>(), Array.Empty<InstalledApp>(), new TempAnalysis());
        Assert.DoesNotContain(findings, f => f.Severity >= FindingSeverity.High);
        Assert.Contains(findings, f => f.Kind == "threat-history");
    }

    [Fact]
    public void PerformanceAndDiskThresholds()
    {
        var perf = new PerformanceSnapshot
        {
            CpuPercent = 95, MemoryPercent = 95,
            Disks = new[] { new DiskInfo { Name = @"C:\", TotalBytes = 1000, FreeBytes = 50 } },
            HighImpactProcesses = new[] { new ProcessInfo { Name = "x", CpuPercent = 95 } }
        };
        var findings = ReportService.BuildFindings(HealthyDefender(), Array.Empty<ThreatInfo>(), perf, Array.Empty<StartupEntry>(), Array.Empty<InstalledApp>(), new TempAnalysis());
        Assert.Contains(findings, f => f.Kind == "cpu-high");
        Assert.Contains(findings, f => f.Kind == "memory-high");
        Assert.Contains(findings, f => f.Kind == "disk-full");
        Assert.Contains(findings, f => f.Kind == "high-impact" && f.Severity == FindingSeverity.Info);
    }

    [Fact]
    public void SuspiciousAppsAndStartup_AreLowSeverityNotMalware()
    {
        var apps = new[] { new InstalledApp { Name = "WeatherBar Toolbar", Hints = new[] { "Editore non dichiarato", "Nome tipico di software indesiderato (\"toolbar\")" } } };
        var startup = Enumerable.Range(0, 12).Select(i => new StartupEntry { Name = "v" + i, Enabled = true, Unknown = i == 0 }).ToList();
        var temp = new TempAnalysis { TotalReclaimableBytes = 3L * 1024 * 1024 * 1024 };
        var findings = ReportService.BuildFindings(HealthyDefender(), Array.Empty<ThreatInfo>(), CalmPerformance(), startup, apps, temp);
        Assert.Contains(findings, f => f.Kind == "apps-suspicious" && f.Severity == FindingSeverity.Low);
        Assert.Contains(findings, f => f.Kind == "startup-many");
        Assert.Contains(findings, f => f.Kind == "startup-unknown");
        Assert.Contains(findings, f => f.Kind == "temp-large");
        Assert.DoesNotContain(findings, f => f.Severity == FindingSeverity.Critical);
    }

    [Fact]
    public void Findings_AreOrderedBySeverityDescending()
    {
        var threats = new[] { new ThreatInfo { Name = "T", IsActive = true } };
        var temp = new TempAnalysis { TotalReclaimableBytes = 3L * 1024 * 1024 * 1024 };
        var findings = ReportService.BuildFindings(new DefenderStatus { Available = false }, threats, CalmPerformance(), Array.Empty<StartupEntry>(), Array.Empty<InstalledApp>(), temp);
        for (var i = 1; i < findings.Count; i++)
        {
            Assert.True(findings[i - 1].Severity >= findings[i].Severity);
        }
    }

    [Fact]
    public void RenderHtml_EscapesContentAndContainsDisclaimer()
    {
        var report = new SystemReport
        {
            System = new SystemInfo { MachineName = "PC<script>", OsDescription = "Windows", UserName = "u" },
            Apps = new[] { new InstalledApp { Name = "<b>App</b>" } },
            Findings = new[] { new Finding { Severity = FindingSeverity.Low, Title = "T", Description = "D" } }
        };
        var html = ReportService.RenderHtml(report);
        Assert.Contains("PC&lt;script&gt;", html);
        Assert.Contains("&lt;b&gt;App&lt;/b&gt;", html);
        Assert.DoesNotContain("<script>", html);
        Assert.Contains("Nessun software può garantire", html);
    }
}
