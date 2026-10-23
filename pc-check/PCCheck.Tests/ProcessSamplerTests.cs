using PCCheck.Core.Infrastructure;
using PCCheck.Core.Models;
using Xunit;

namespace PCCheck.Tests;

public class ProcessSamplerTests
{
    [Fact]
    public void ComputeCpuPercent_UsesDeltaOverIntervalAndCores()
    {
        // 500 ms di CPU in 1000 ms su 4 core = 12,5%
        var percent = ProcessSampler.ComputeCpuPercent(TimeSpan.FromMilliseconds(1000), TimeSpan.FromMilliseconds(1500), TimeSpan.FromMilliseconds(1000), 4);
        Assert.Equal(12.5, percent);
    }

    [Fact]
    public void ComputeCpuPercent_ClampsToHundred()
    {
        var percent = ProcessSampler.ComputeCpuPercent(TimeSpan.Zero, TimeSpan.FromSeconds(10), TimeSpan.FromSeconds(1), 2);
        Assert.Equal(100, percent);
    }

    [Fact]
    public void ComputeCpuPercent_ReturnsZeroForInvalidInput()
    {
        Assert.Equal(0, ProcessSampler.ComputeCpuPercent(TimeSpan.FromSeconds(2), TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(1), 4));
        Assert.Equal(0, ProcessSampler.ComputeCpuPercent(TimeSpan.Zero, TimeSpan.FromSeconds(1), TimeSpan.Zero, 4));
        Assert.Equal(0, ProcessSampler.ComputeCpuPercent(TimeSpan.Zero, TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(1), 0));
    }

    [Fact]
    public void ComputeSystemCpuPercent_FromKernelTimes()
    {
        // 1000 tick totali, 250 idle = 75% occupato
        Assert.Equal(75, ProcessSampler.ComputeSystemCpuPercent(1000, 5000, 1250, 6000));
        Assert.Equal(0, ProcessSampler.ComputeSystemCpuPercent(10, 10, 10, 10));
        Assert.Equal(0, ProcessSampler.ComputeSystemCpuPercent(0, 100, 200, 200));
    }

    [Theory]
    [InlineData("System", 4, true)]
    [InlineData("Idle", 0, true)]
    [InlineData("qualsiasi", 2, true)]
    [InlineData("csrss", 700, true)]
    [InlineData("svchost.exe", 1200, true)]
    [InlineData("MsMpEng", 2456, true)]
    [InlineData("explorer", 7788, true)]
    [InlineData("PCCheck", 100, true)]
    [InlineData("msedge", 5120, false)]
    [InlineData("SpeedUpMyPC", 10240, false)]
    [InlineData("", 500, false)]
    [InlineData(null, 500, false)]
    public void IsProtected_RecognisesWindowsCoreProcesses(string? name, int pid, bool expected)
    {
        Assert.Equal(expected, ProcessSampler.IsProtected(name, pid));
    }

    [Fact]
    public void IsHighImpact_Thresholds()
    {
        Assert.True(ProcessSampler.IsHighImpact(ProcessSampler.HighCpuPercent, 0));
        Assert.True(ProcessSampler.IsHighImpact(0, ProcessSampler.HighMemoryBytes));
        Assert.False(ProcessSampler.IsHighImpact(ProcessSampler.HighCpuPercent - 0.1, ProcessSampler.HighMemoryBytes - 1));
    }

    [Fact]
    public void IsUnknownLocation_FlagsPathsOutsideTrustedRoots()
    {
        var trusted = new[] { @"C:\Windows", @"C:\Program Files" };
        if (!OperatingSystem.IsWindows())
        {
            trusted = new[] { "/usr/bin", "/opt/app" };
            Assert.False(ProcessSampler.IsUnknownLocation("/usr/bin/python3", trusted));
            Assert.True(ProcessSampler.IsUnknownLocation("/tmp/strano", trusted));
        }
        else
        {
            Assert.False(ProcessSampler.IsUnknownLocation(@"C:\Windows\System32\svchost.exe", trusted));
            Assert.False(ProcessSampler.IsUnknownLocation(@"C:\Program Files\App\app.exe", trusted));
            Assert.True(ProcessSampler.IsUnknownLocation(@"C:\Users\u\AppData\Local\Temp\x.exe", trusted));
            Assert.True(ProcessSampler.IsUnknownLocation(@"C:\Program Files Extra\x.exe", trusted));
        }
        Assert.False(ProcessSampler.IsUnknownLocation(null, trusted), "Percorso non leggibile: non si puo' dire che sia sconosciuto");
    }

    [Fact]
    public void SelectTop_OrdersByCpuThenMemory()
    {
        var list = new[]
        {
            new ProcessInfo { Pid = 1, Name = "a", CpuPercent = 1, MemoryBytes = 500 },
            new ProcessInfo { Pid = 2, Name = "b", CpuPercent = 9, MemoryBytes = 100 },
            new ProcessInfo { Pid = 3, Name = "c", CpuPercent = 1, MemoryBytes = 900 },
            new ProcessInfo { Pid = 4, Name = "d", CpuPercent = 5, MemoryBytes = 100 }
        };
        var top = ProcessSampler.SelectTop(list, 3);
        Assert.Equal(new[] { 2, 4, 3 }, top.Select(p => p.Pid));
    }
}
