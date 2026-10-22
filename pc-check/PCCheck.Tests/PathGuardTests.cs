using PCCheck.Core.Security;
using Xunit;

namespace PCCheck.Tests;

public class PathGuardTests
{
    private static readonly string Root = Path.Combine(Path.GetTempPath(), "pccheck-guard-test");

    [Fact]
    public void IsInside_AcceptsRootAndChildren()
    {
        Assert.True(PathGuard.IsInside(Root, Root));
        Assert.True(PathGuard.IsInside(Root, Path.Combine(Root, "file.tmp")));
        Assert.True(PathGuard.IsInside(Root, Path.Combine(Root, "sub", "deep", "file.tmp")));
        Assert.True(PathGuard.IsInside(Root + Path.DirectorySeparatorChar, Path.Combine(Root, "x")));
    }

    [Fact]
    public void IsInside_RejectsSiblingsWithSamePrefix()
    {
        Assert.False(PathGuard.IsInside(Root, Root + "-altro"));
        Assert.False(PathGuard.IsInside(Root, Path.Combine(Path.GetTempPath(), "pccheck-guard-test2", "file")));
    }

    [Fact]
    public void IsInside_RejectsTraversal()
    {
        Assert.False(PathGuard.IsInside(Root, Path.Combine(Root, "..", "fuori.txt")));
        Assert.False(PathGuard.IsInside(Root, Path.Combine(Root, "sub", "..", "..", "fuori.txt")));
    }

    [Fact]
    public void IsStrictlyInside_ExcludesRootItself()
    {
        Assert.False(PathGuard.IsStrictlyInside(Root, Root));
        Assert.True(PathGuard.IsStrictlyInside(Root, Path.Combine(Root, "a")));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    public void IsInside_RejectsEmptyInput(string? candidate)
    {
        Assert.False(PathGuard.IsInside(Root, candidate!));
        Assert.False(PathGuard.IsInside(candidate!, Root));
    }

    [Fact]
    public void IsInside_RejectsInvalidPathCharacters()
    {
        if (OperatingSystem.IsWindows())
        {
            Assert.False(PathGuard.IsInside(Root, Root + "\\fi|le\0"));
        }
        else
        {
            Assert.False(PathGuard.IsInside(Root, Root + "/fi\0le"));
        }
    }
}
