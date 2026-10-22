using PCCheck.Core.Security;
using Xunit;

namespace PCCheck.Tests;

public class SessionTokenTests
{
    [Fact]
    public void Generate_ProducesLongUrlSafeToken()
    {
        var token = SessionToken.Generate();
        Assert.True(token.Value.Length >= 43, "Il token deve codificare almeno 256 bit");
        Assert.True(SessionToken.IsSafeCharset(token.Value));
        Assert.DoesNotContain("=", token.Value);
        Assert.DoesNotContain("+", token.Value);
        Assert.DoesNotContain("/", token.Value);
    }

    [Fact]
    public void Generate_ProducesDifferentTokensEachTime()
    {
        var a = SessionToken.Generate();
        var b = SessionToken.Generate();
        Assert.NotEqual(a.Value, b.Value);
    }

    [Fact]
    public void Matches_AcceptsExactValueOnly()
    {
        var token = SessionToken.Generate();
        Assert.True(token.Matches(token.Value));
        Assert.False(token.Matches(token.Value + "x"));
        Assert.False(token.Matches(token.Value[..^1]));
        Assert.False(token.Matches(token.Value.ToUpperInvariant() == token.Value ? token.Value.ToLowerInvariant() : token.Value.ToUpperInvariant()));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData(" ")]
    [InlineData("Bearer qualcosa")]
    public void Matches_RejectsMissingOrForeignValues(string? candidate)
    {
        var token = SessionToken.Generate();
        Assert.False(token.Matches(candidate));
    }

    [Fact]
    public void Matches_RejectsValueOfSameLengthButDifferentContent()
    {
        var token = SessionToken.Generate();
        var other = new string('A', token.Value.Length);
        Assert.False(token.Matches(other));
    }

    [Theory]
    [InlineData("corto")]
    [InlineData("")]
    [InlineData("token con spazi e lunghezza sufficiente per il controllo")]
    [InlineData("token<script>alert(1)</script>lunghezza-ok-123")]
    public void Constructor_RejectsWeakOrUnsafeTokens(string value)
    {
        Assert.Throws<ArgumentException>(() => new SessionToken(value));
    }

    [Fact]
    public void FromEnvironmentOrGenerate_IgnoresWeakEnvironmentValue()
    {
        Environment.SetEnvironmentVariable(SessionToken.EnvironmentVariableName, "debole");
        try
        {
            var token = SessionToken.FromEnvironmentOrGenerate();
            Assert.NotEqual("debole", token.Value);
            Assert.Equal(token.Value, Environment.GetEnvironmentVariable(SessionToken.EnvironmentVariableName));
        }
        finally
        {
            Environment.SetEnvironmentVariable(SessionToken.EnvironmentVariableName, null);
        }
    }

    [Fact]
    public void FromEnvironmentOrGenerate_UsesStrongEnvironmentValue()
    {
        var strong = SessionToken.Generate().Value;
        Environment.SetEnvironmentVariable(SessionToken.EnvironmentVariableName, strong);
        try
        {
            var token = SessionToken.FromEnvironmentOrGenerate();
            Assert.Equal(strong, token.Value);
        }
        finally
        {
            Environment.SetEnvironmentVariable(SessionToken.EnvironmentVariableName, null);
        }
    }

    [Fact]
    public void ToString_DoesNotLeakValue()
    {
        var token = SessionToken.Generate();
        Assert.DoesNotContain(token.Value, token.ToString());
    }
}
