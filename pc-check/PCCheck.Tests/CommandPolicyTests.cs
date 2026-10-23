using PCCheck.Core.Models;
using PCCheck.Core.Security;
using Xunit;

namespace PCCheck.Tests;

public class CommandPolicyTests
{
    [Fact]
    public void DefenderScanArguments_AreFixedAndValidated()
    {
        Assert.Equal(new[] { "-Scan", "-ScanType", "1" }, CommandPolicy.BuildDefenderScanArguments(ScanType.Quick));
        Assert.Equal(new[] { "-Scan", "-ScanType", "2" }, CommandPolicy.BuildDefenderScanArguments(ScanType.Full));
        Assert.Throws<ArgumentOutOfRangeException>(() => CommandPolicy.BuildDefenderScanArguments((ScanType)3));
    }

    [Theory]
    [InlineData("quick", true, ScanType.Quick)]
    [InlineData("rapida", true, ScanType.Quick)]
    [InlineData("1", true, ScanType.Quick)]
    [InlineData("FULL", true, ScanType.Full)]
    [InlineData("completa", true, ScanType.Full)]
    [InlineData("custom", false, ScanType.Quick)]
    [InlineData("3", false, ScanType.Quick)]
    [InlineData("-Remove", false, ScanType.Quick)]
    [InlineData(null, false, ScanType.Quick)]
    public void TryParseScanType_OnlyAcceptsKnownValues(string? text, bool expectedOk, ScanType expectedType)
    {
        var ok = CommandPolicy.TryParseScanType(text, out var type);
        Assert.Equal(expectedOk, ok);
        Assert.Equal(expectedType, type);
    }

    [Theory]
    [InlineData("{AC76BA86-1033-1033-7760-BC15014EA700}", true)]
    [InlineData("{ac76ba86-1033-1033-7760-bc15014ea700}", true)]
    [InlineData("AC76BA86-1033-1033-7760-BC15014EA700", false)]
    [InlineData("{AC76BA86-1033-1033-7760-BC15014EA700} /qn", false)]
    [InlineData("{ZZZZZZZZ-1033-1033-7760-BC15014EA700}", false)]
    [InlineData("", false)]
    [InlineData(null, false)]
    public void MsiProductCode_Validation(string? code, bool expected)
    {
        Assert.Equal(expected, CommandPolicy.IsValidMsiProductCode(code));
    }

    [Theory]
    [InlineData("7-Zip 23.01 (x64)", true)]
    [InlineData("Microsoft 365 Apps for business - it-it", true)]
    [InlineData("Programma \"strano\"", false)]
    [InlineData("App & calc", false)]
    [InlineData("x | del C:\\", false)]
    [InlineData("nome\ncon-a-capo", false)]
    [InlineData("%TEMP%", false)]
    [InlineData("", false)]
    [InlineData(null, false)]
    public void PackageName_Validation(string? name, bool expected)
    {
        Assert.Equal(expected, CommandPolicy.IsSafePackageName(name));
    }

    [Fact]
    public void PackageName_RejectsVeryLongNames()
    {
        Assert.False(CommandPolicy.IsSafePackageName(new string('a', 201)));
    }

    [Theory]
    [InlineData("OneDrive", true)]
    [InlineData("com.squirrel.Teams.Teams", true)]
    [InlineData("Nome con spazi", true)]
    [InlineData("nome\"virgolette", false)]
    [InlineData("nome\\backslash", false)]
    [InlineData("nome/slash", false)]
    [InlineData("", false)]
    [InlineData(null, false)]
    public void RegistryValueName_Validation(string? name, bool expected)
    {
        Assert.Equal(expected, CommandPolicy.IsSafeRegistryValueName(name));
    }

    [Theory]
    [InlineData(0, false)]
    [InlineData(4, false)]
    [InlineData(-1, false)]
    [InlineData(5, true)]
    [InlineData(1234, true)]
    public void KillablePid_ProtectsSystemProcesses(int pid, bool expected)
    {
        Assert.Equal(expected, CommandPolicy.IsKillablePid(pid));
    }

    [Fact]
    public void RegBinaryHex_FormatsForRegExe()
    {
        Assert.Equal("030000000000000000000000", CommandPolicy.ToRegBinaryHex(new byte[] { 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }));
    }
}
