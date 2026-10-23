using System.Net;
using Microsoft.AspNetCore.Hosting;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using PCCheck.Core.Models;
using PCCheck.Core.Security;
using Xunit;

namespace PCCheck.Tests;

/// <summary>Test di integrazione sull'host reale (modalità dimostrativa, browser non aperto).</summary>
public sealed class PcCheckFactory : WebApplicationFactory<Program>
{
    protected override void ConfigureWebHost(Microsoft.AspNetCore.Hosting.IWebHostBuilder builder)
    {
        builder.UseSetting("PcCheck:Demo", "true");
        builder.UseSetting("PcCheck:OpenBrowser", "false");
        builder.UseEnvironment("Production");
    }

    public string Token => Services.GetRequiredService<SessionToken>().Value;

    public HttpClient CreateAuthenticatedClient()
    {
        var client = CreateClient(new WebApplicationFactoryClientOptions { BaseAddress = new Uri("http://localhost") });
        client.DefaultRequestHeaders.Add(SessionToken.HeaderName, Token);
        return client;
    }
}

public class ApiSecurityIntegrationTests : IClassFixture<PcCheckFactory>
{
    private readonly PcCheckFactory _factory;
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { Converters = { new JsonStringEnumConverter() } };

    public ApiSecurityIntegrationTests(PcCheckFactory factory)
    {
        _factory = factory;
    }

    [Fact]
    public async Task Api_WithoutToken_Returns401()
    {
        var client = _factory.CreateClient();
        var response = await client.GetAsync("/api/system/info");
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Api_WithWrongToken_Returns401()
    {
        var client = _factory.CreateClient();
        client.DefaultRequestHeaders.Add(SessionToken.HeaderName, new string('x', 43));
        var response = await client.GetAsync("/api/system/info");
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Api_WithValidToken_ReturnsSystemInfoInDemoMode()
    {
        var client = _factory.CreateAuthenticatedClient();
        var info = await client.GetFromJsonAsync<SystemInfo>("/api/system/info", Json);
        Assert.NotNull(info);
        Assert.True(info!.DemoMode);
        Assert.Equal("PC Check", info.AppName);
    }

    [Fact]
    public async Task NonLoopbackHostHeader_IsRejected()
    {
        var client = _factory.CreateAuthenticatedClient();
        var request = new HttpRequestMessage(HttpMethod.Get, "/api/system/info");
        request.Headers.Host = "evil.example.com";
        var response = await client.SendAsync(request);
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task ForeignOrigin_IsRejected()
    {
        var client = _factory.CreateAuthenticatedClient();
        var request = new HttpRequestMessage(HttpMethod.Get, "/api/system/info");
        request.Headers.Add("Origin", "http://attacker.example");
        var response = await client.SendAsync(request);
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task StaticPage_IsServedWithStrictCsp()
    {
        var client = _factory.CreateClient();
        var response = await client.GetAsync("/");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var csp = response.Headers.GetValues("Content-Security-Policy").Single();
        Assert.Contains("default-src 'self'", csp);
        Assert.Contains("script-src 'self'", csp);
        Assert.Contains("connect-src 'self'", csp);
        Assert.DoesNotContain("unsafe-eval", csp);
        Assert.Equal("nosniff", response.Headers.GetValues("X-Content-Type-Options").Single());
        var html = await response.Content.ReadAsStringAsync();
        Assert.Contains("<title>PC Check</title>", html);
        Assert.DoesNotContain("<script>", html, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task Scan_RejectsUnknownScanType()
    {
        var client = _factory.CreateAuthenticatedClient();
        var response = await client.PostAsJsonAsync("/api/defender/scan", new { type = "-Remove" });
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task KillProcess_RequiresExplicitConfirmation()
    {
        var client = _factory.CreateAuthenticatedClient();
        var response = await client.PostAsJsonAsync("/api/performance/process/kill", new { pid = 10240, name = "SpeedUpMyPC", confirmed = false });
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task KillProcess_RefusesProtectedProcess()
    {
        var client = _factory.CreateAuthenticatedClient();
        var response = await client.PostAsJsonAsync("/api/performance/process/kill", new { pid = 1180, name = "svchost", confirmed = true });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var outcome = await response.Content.ReadFromJsonAsync<ActionOutcome>(Json);
        Assert.NotNull(outcome);
        Assert.False(outcome!.Success);
    }

    [Fact]
    public async Task TempClean_RequiresConfirmationAndTargets()
    {
        var client = _factory.CreateAuthenticatedClient();
        var noConfirm = await client.PostAsJsonAsync("/api/temp/clean", new { targets = new[] { "user" }, confirmed = false });
        Assert.Equal(HttpStatusCode.BadRequest, noConfirm.StatusCode);
        var noTargets = await client.PostAsJsonAsync("/api/temp/clean", new { targets = Array.Empty<string>(), confirmed = true });
        Assert.Equal(HttpStatusCode.BadRequest, noTargets.StatusCode);
    }

    [Fact]
    public async Task Overview_ReturnsFindingsAndDisclaimer()
    {
        var client = _factory.CreateAuthenticatedClient();
        var report = await client.GetFromJsonAsync<SystemReport>("/api/overview", Json);
        Assert.NotNull(report);
        Assert.NotEmpty(report!.Findings);
        Assert.Contains("Nessun software può garantire", report.Disclaimer);
        Assert.True(report.Defender.Available);
        Assert.NotEmpty(report.Apps);
        Assert.NotEmpty(report.Startup);
    }

    [Fact]
    public async Task UnknownApiRoute_Returns404Json()
    {
        var client = _factory.CreateAuthenticatedClient();
        var response = await client.GetAsync("/api/non-esiste");
        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task ApiResponses_AreNotCached()
    {
        var client = _factory.CreateAuthenticatedClient();
        var response = await client.GetAsync("/api/system/info");
        Assert.Contains("no-store", response.Headers.CacheControl?.ToString() ?? "");
    }
}
