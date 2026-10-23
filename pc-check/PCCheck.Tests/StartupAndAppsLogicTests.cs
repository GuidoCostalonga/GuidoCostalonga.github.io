using PCCheck.Core.Services;
using Xunit;

namespace PCCheck.Tests;

public class StartupAndAppsLogicTests
{
    [Theory]
    [InlineData(0x02, true)]
    [InlineData(0x06, true)]
    [InlineData(0x03, false)]
    [InlineData(0x07, false)]
    public void StartupApprovedFlag_EvenIsEnabled(byte flag, bool expected)
    {
        Assert.Equal(expected, StartupService.IsEnabledFlag(flag));
    }

    [Fact]
    public void BuildApprovedValue_WritesTaskManagerCompatibleBytes()
    {
        var enabled = StartupService.BuildApprovedValue(true, DateTimeOffset.UnixEpoch);
        Assert.Equal(12, enabled.Length);
        Assert.Equal(0x02, enabled[0]);
        Assert.All(enabled.Skip(1), b => Assert.Equal(0, b));

        var when = new DateTimeOffset(2024, 5, 1, 12, 0, 0, TimeSpan.Zero);
        var disabled = StartupService.BuildApprovedValue(false, when);
        Assert.Equal(0x03, disabled[0]);
        Assert.Equal(when.ToFileTime(), BitConverter.ToInt64(disabled, 4));
    }

    [Theory]
    [InlineData("\"C:\\Program Files\\App\\app.exe\" /background", "C:\\Program Files\\App\\app.exe")]
    [InlineData("C:\\Tools\\tool.exe --silent", "C:\\Tools\\tool.exe")]
    [InlineData("C:\\Tools\\tool.exe", "C:\\Tools\\tool.exe")]
    [InlineData("tool --flag", "tool")]
    public void ExtractExecutable_HandlesQuotesAndArguments(string command, string expected)
    {
        Assert.Equal(expected, StartupService.ExtractExecutable(command));
    }

    [Fact]
    public void ExtractExecutable_ReturnsNullForEmptyCommand()
    {
        Assert.Null(StartupService.ExtractExecutable(""));
        Assert.Null(StartupService.ExtractExecutable("   "));
    }

    [Fact]
    public void MakeId_IsStableAndCaseInsensitive()
    {
        var a = StartupService.MakeId(@"HKCU\...\Run", "OneDrive");
        var b = StartupService.MakeId(@"hkcu\...\run", "onedrive");
        var c = StartupService.MakeId(@"HKLM\...\Run", "OneDrive");
        Assert.Equal(a, b);
        Assert.NotEqual(a, c);
        Assert.Equal(20, a.Length);
        Assert.Matches("^[0-9a-f]+$", a);
    }

    [Fact]
    public void BuildHints_FlagsUnknownPublisherKeywordsAndTempLocation()
    {
        var hints = InstalledAppsService.BuildHints("SpeedUpMyPC 2024", null, @"C:\Users\u\AppData\Local\Temp\SpeedUpMyPC\");
        Assert.Contains("Editore non dichiarato", hints);
        Assert.Contains(hints, h => h.StartsWith("Nome tipico", StringComparison.Ordinal));
        Assert.Contains(hints, h => h.StartsWith("Installato in una cartella temporanea", StringComparison.Ordinal));
    }

    [Fact]
    public void BuildHints_IsEmptyForOrdinarySoftware()
    {
        var hints = InstalledAppsService.BuildHints("7-Zip 23.01 (x64)", "Igor Pavlov", @"C:\Program Files\7-Zip\");
        Assert.Empty(hints);
    }

    [Fact]
    public void FormatBytes_UsesHumanUnits()
    {
        Assert.Equal("0 B", TempCleanerService.FormatBytes(0));
        Assert.Equal("1 KB", TempCleanerService.FormatBytes(1024));
        Assert.Equal("1.5 MB", TempCleanerService.FormatBytes(1024 * 1024 + 512 * 1024).Replace(',', '.'));
        Assert.Equal("2 GB", TempCleanerService.FormatBytes(2L * 1024 * 1024 * 1024));
    }
}
