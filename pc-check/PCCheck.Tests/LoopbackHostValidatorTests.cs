using System.Net;
using PCCheck.Core.Security;
using Xunit;

namespace PCCheck.Tests;

public class LoopbackHostValidatorTests
{
    [Theory]
    [InlineData("127.0.0.1")]
    [InlineData("127.0.0.1:5000")]
    [InlineData("127.0.0.1:65535")]
    [InlineData("localhost")]
    [InlineData("LOCALHOST:8080")]
    [InlineData("[::1]")]
    [InlineData("[::1]:5000")]
    public void Accepts_LoopbackHosts(string host)
    {
        Assert.True(LoopbackHostValidator.IsLoopbackHost(host));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("example.com")]
    [InlineData("example.com:80")]
    [InlineData("127.0.0.1.example.com")]
    [InlineData("localhost.example.com")]
    [InlineData("127.0.0.1:0")]
    [InlineData("127.0.0.1:70000")]
    [InlineData("127.0.0.1:abc")]
    [InlineData("192.168.1.10:5000")]
    [InlineData("10.0.0.1")]
    [InlineData("::1:5000")]
    [InlineData("[::1")]
    [InlineData("[::2]")]
    [InlineData("user@127.0.0.1")]
    [InlineData("127.0.0.1/path")]
    [InlineData("127.0.0.1:5000:5000")]
    public void Rejects_NonLoopbackOrMalformedHosts(string? host)
    {
        Assert.False(LoopbackHostValidator.IsLoopbackHost(host));
    }

    [Fact]
    public void Rejects_ExcessivelyLongHost()
    {
        Assert.False(LoopbackHostValidator.IsLoopbackHost(new string('a', 300)));
    }

    [Fact]
    public void ExpectedPort_IsEnforcedWhenProvided()
    {
        Assert.True(LoopbackHostValidator.IsLoopbackHost("127.0.0.1:5000", 5000));
        Assert.False(LoopbackHostValidator.IsLoopbackHost("127.0.0.1:5001", 5000));
        Assert.True(LoopbackHostValidator.IsLoopbackHost("localhost", 5000), "Senza porta esplicita l'host resta accettabile");
    }

    [Fact]
    public void LoopbackAddress_Detection()
    {
        Assert.True(LoopbackHostValidator.IsLoopbackAddress(IPAddress.Loopback));
        Assert.True(LoopbackHostValidator.IsLoopbackAddress(IPAddress.IPv6Loopback));
        Assert.True(LoopbackHostValidator.IsLoopbackAddress(IPAddress.Loopback.MapToIPv6()));
        Assert.True(LoopbackHostValidator.IsLoopbackAddress(null));
        Assert.False(LoopbackHostValidator.IsLoopbackAddress(IPAddress.Parse("192.168.0.5")));
        Assert.False(LoopbackHostValidator.IsLoopbackAddress(IPAddress.Parse("8.8.8.8")));
    }

    [Theory]
    [InlineData("http://127.0.0.1:5000", true)]
    [InlineData("http://localhost:5000", true)]
    [InlineData("http://[::1]:5000", true)]
    [InlineData("https://127.0.0.1", true)]
    [InlineData("http://evil.example", false)]
    [InlineData("http://127.0.0.1.evil.example:5000", false)]
    [InlineData("null", false)]
    [InlineData("file:///C:/pagina.html", false)]
    [InlineData("non-un-url", false)]
    public void Origin_Validation(string origin, bool expected)
    {
        Assert.Equal(expected, ApiSecurityMiddleware.IsLoopbackOrigin(origin));
    }
}
