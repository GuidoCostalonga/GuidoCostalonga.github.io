using System.Net;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Http.Json;
using Microsoft.Extensions.FileProviders;
using PCCheck.Core.Infrastructure;
using PCCheck.Core.Models;
using PCCheck.Core.Security;
using PCCheck.Core.Services;
using PCCheck.Core.Services.Mock;

// ---------------------------------------------------------------------------
// PC Check: diagnostica locale di sicurezza e prestazioni per Windows 10/11.
// Backend ASP.NET Core Minimal API in ascolto SOLO su 127.0.0.1, interfaccia web
// incorporata nell'eseguibile, nessuna connessione verso l'esterno.
// ---------------------------------------------------------------------------

var cli = CommandLine.Parse(args);
if (cli.ShowHelp)
{
    Console.WriteLine(CommandLine.HelpText);
    return 0;
}

// Compito elevato mirato (avviato da PC Check stesso con verb "runas"): esegue una sola operazione e termina.
if (cli.ElevatedTask is not null)
{
    using var loggerFactory = LoggerFactory.Create(b => b.AddConsole());
    var taskLogger = loggerFactory.CreateLogger("PCCheck.ElevatedTask");
    return cli.ElevatedTask switch
    {
        "clean-temp" => TempCleanerService.RunElevatedTask(args, taskLogger),
        _ => 2
    };
}

var builder = WebApplication.CreateBuilder(new WebApplicationOptions
{
    Args = args,
    ContentRootPath = AppContext.BaseDirectory
});

builder.Logging.ClearProviders();
builder.Logging.AddSimpleConsole(o =>
{
    o.SingleLine = true;
    o.TimestampFormat = "HH:mm:ss ";
});

var options = new AppOptions
{
    Demo = cli.Demo || builder.Configuration.GetValue<bool>("PcCheck:Demo") || IsTruthy(Environment.GetEnvironmentVariable("PCCHECK_DEMO")),
    OpenBrowser = !cli.NoBrowser && builder.Configuration.GetValue("PcCheck:OpenBrowser", true),
    Port = cli.Port ?? builder.Configuration.GetValue<int>("PcCheck:Port"),
    FallbackPort = builder.Configuration.GetValue("PcCheck:FallbackPort", 5000)
};

var sessionToken = SessionToken.FromEnvironmentOrGenerate();
var listenPort = options.Port > 0 && PortFinder.IsPortFree(options.Port) ? options.Port : PortFinder.FindFreePort(options.FallbackPort);

builder.WebHost.ConfigureKestrel(kestrel =>
{
    kestrel.AddServerHeader = false;
    kestrel.Limits.MaxRequestBodySize = 64 * 1024;
    kestrel.Listen(IPAddress.Loopback, listenPort);
});

builder.Services.AddSingleton(sessionToken);
builder.Services.AddSingleton(options);
builder.Services.Configure<JsonOptions>(o =>
{
    o.SerializerOptions.Converters.Add(new JsonStringEnumConverter());
    o.SerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.Never;
});

if (options.Demo || !OperatingSystem.IsWindows())
{
    builder.Services.AddSingleton<IDefenderService, MockDefenderService>();
    builder.Services.AddSingleton<IPerformanceService, MockPerformanceService>();
    builder.Services.AddSingleton<IStartupService, MockStartupService>();
    builder.Services.AddSingleton<IInstalledAppsService, MockInstalledAppsService>();
    builder.Services.AddSingleton<ITempCleanerService, MockTempCleanerService>();
}
else
{
    builder.Services.AddSingleton<IDefenderService, DefenderService>();
    builder.Services.AddSingleton<IPerformanceService, PerformanceService>();
    builder.Services.AddSingleton<IStartupService, StartupService>();
    builder.Services.AddSingleton<IInstalledAppsService, InstalledAppsService>();
    builder.Services.AddSingleton<ITempCleanerService, TempCleanerService>();
}
builder.Services.AddSingleton<ISystemInfoService, SystemInfoService>();
builder.Services.AddSingleton<ReportService>();

var app = builder.Build();
var logger = app.Logger;
var demoEffective = options.Demo || !OperatingSystem.IsWindows();

app.UseMiddleware<ApiSecurityMiddleware>();

// Interfaccia web: incorporata nell'eseguibile; in sviluppo si usa la cartella wwwroot su disco se presente.
IFileProvider webFiles;
var physicalWwwroot = Path.Combine(AppContext.BaseDirectory, "wwwroot");
if (app.Environment.IsDevelopment() && Directory.Exists(physicalWwwroot))
{
    webFiles = new PhysicalFileProvider(physicalWwwroot);
}
else
{
    webFiles = new ManifestEmbeddedFileProvider(typeof(Program).Assembly, "wwwroot");
}
app.UseDefaultFiles(new DefaultFilesOptions { FileProvider = webFiles, DefaultFileNames = new List<string> { "index.html" } });
app.UseStaticFiles(new StaticFileOptions
{
    FileProvider = webFiles,
    OnPrepareResponse = ctx => ctx.Context.Response.Headers["Cache-Control"] = "no-cache"
});

var api = app.MapGroup("/api");

// ----- Sistema -------------------------------------------------------------
api.MapGet("/system/info", (ISystemInfoService system) => Results.Ok(system.GetInfo()));

api.MapPost("/system/shutdown", (IHostApplicationLifetime lifetime) =>
{
    logger.LogInformation("Arresto richiesto dall'interfaccia");
    _ = Task.Run(async () =>
    {
        await Task.Delay(300);
        lifetime.StopApplication();
    });
    return Results.Ok(new { message = "PC Check si sta chiudendo. Puoi chiudere questa scheda." });
});

// ----- Panoramica e rapporto ----------------------------------------------
api.MapGet("/overview", async (ReportService report, CancellationToken ct) =>
    Results.Ok(await report.BuildAsync(1000, ct)));

api.MapGet("/report", async (ReportService report, CancellationToken ct) =>
    Results.Ok(await report.BuildAsync(1500, ct)));

api.MapGet("/report/html", async (ReportService report, CancellationToken ct) =>
{
    var data = await report.BuildAsync(1500, ct);
    var html = ReportService.RenderHtml(data);
    return Results.Text(html, "text/html", System.Text.Encoding.UTF8);
});

api.MapPost("/report/save", async (ReportService report, CancellationToken ct) =>
{
    var data = await report.BuildAsync(1500, ct);
    var html = ReportService.RenderHtml(data);
    var documents = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
    if (string.IsNullOrEmpty(documents))
    {
        documents = PathGuard.GetAppDataDirectory();
    }
    var folder = Path.Combine(documents, "PC Check");
    try
    {
        Directory.CreateDirectory(folder);
        var file = Path.Combine(folder, $"Rapporto-PC-Check-{DateTime.Now:yyyyMMdd-HHmm}.html");
        await File.WriteAllTextAsync(file, html, System.Text.Encoding.UTF8, ct);
        return Results.Ok(ActionOutcome.Ok("Rapporto salvato.", file));
    }
    catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
    {
        return Results.Ok(ActionOutcome.Fail("Salvataggio non riuscito: " + ex.Message));
    }
});

// ----- Microsoft Defender --------------------------------------------------
api.MapGet("/defender/status", async (IDefenderService defender, CancellationToken ct) =>
    Results.Ok(await defender.GetStatusAsync(ct)));

api.MapGet("/defender/threats", async (IDefenderService defender, CancellationToken ct) =>
    Results.Ok(await defender.GetThreatsAsync(ct)));

api.MapGet("/defender/scan", (IDefenderService defender) => Results.Ok(defender.GetScanStatus()));

api.MapPost("/defender/scan", async (ScanRequest request, IDefenderService defender, CancellationToken ct) =>
{
    if (!CommandPolicy.TryParseScanType(request.Type, out var type))
    {
        return Results.BadRequest(new { error = "Tipo di scansione non valido: usa \"quick\" o \"full\"." });
    }
    return Results.Ok(await defender.StartScanAsync(type, ct));
});

api.MapPost("/defender/scan/cancel", async (IDefenderService defender) => Results.Ok(await defender.CancelScanAsync()));

// ----- Prestazioni ---------------------------------------------------------
api.MapGet("/performance", async (int? sampleMs, IPerformanceService performance, CancellationToken ct) =>
    Results.Ok(await performance.GetSnapshotAsync(sampleMs ?? 1500, ct)));

api.MapPost("/performance/process/kill", async (KillProcessRequest request, IPerformanceService performance, CancellationToken ct) =>
{
    if (!request.Confirmed)
    {
        return Results.BadRequest(ActionOutcome.Fail("Conferma esplicita mancante: nessun processo è stato terminato."));
    }
    if (string.IsNullOrWhiteSpace(request.Name) || request.Name.Length > 260)
    {
        return Results.BadRequest(ActionOutcome.Fail("Nome del processo mancante o non valido."));
    }
    var outcome = await performance.TerminateProcessAsync(request.Pid, request.Name.Trim(), request.AllowElevation, ct);
    return Results.Ok(outcome);
});

// ----- Avvio ---------------------------------------------------------------
api.MapGet("/startup", async (IStartupService startup, CancellationToken ct) => Results.Ok(await startup.GetEntriesAsync(ct)));

api.MapPost("/startup/toggle", async (ToggleStartupRequest request, IStartupService startup, CancellationToken ct) =>
{
    if (!request.Confirmed)
    {
        return Results.BadRequest(ActionOutcome.Fail("Conferma esplicita mancante: nessuna modifica effettuata."));
    }
    if (string.IsNullOrWhiteSpace(request.Id) || request.Id.Length > 64)
    {
        return Results.BadRequest(ActionOutcome.Fail("Identificativo non valido."));
    }
    return Results.Ok(await startup.SetEnabledAsync(request.Id.Trim(), request.Enabled, ct));
});

// ----- Programmi installati ------------------------------------------------
api.MapGet("/apps", async (IInstalledAppsService apps, CancellationToken ct) => Results.Ok(await apps.GetAppsAsync(ct)));

api.MapPost("/apps/uninstall", async (UninstallRequest request, IInstalledAppsService apps, CancellationToken ct) =>
{
    if (!request.Confirmed)
    {
        return Results.BadRequest(ActionOutcome.Fail("Conferma esplicita mancante: nessuna disinstallazione avviata."));
    }
    if (string.IsNullOrWhiteSpace(request.Id) || request.Id.Length > 64)
    {
        return Results.BadRequest(ActionOutcome.Fail("Identificativo non valido."));
    }
    return Results.Ok(await apps.UninstallAsync(request.Id.Trim(), ct));
});

api.MapPost("/apps/open-settings", async (IInstalledAppsService apps) => Results.Ok(await apps.OpenAppsSettingsAsync()));

// ----- File temporanei -----------------------------------------------------
api.MapGet("/temp", async (bool? refresh, ITempCleanerService temp, CancellationToken ct) =>
    Results.Ok(await temp.AnalyzeAsync(refresh ?? false, ct)));

api.MapPost("/temp/clean", async (TempCleanRequest request, ITempCleanerService temp, CancellationToken ct) =>
{
    if (!request.Confirmed)
    {
        return Results.BadRequest(ActionOutcome.Fail("Conferma esplicita mancante: nessun file è stato eliminato."));
    }
    if (request.Targets.Length == 0 || request.Targets.Length > 2)
    {
        return Results.BadRequest(ActionOutcome.Fail("Indica almeno una cartella da pulire."));
    }
    request.OlderThanDays = Math.Clamp(request.OlderThanDays, 0, 3650);
    return Results.Ok(await temp.CleanAsync(request, ct));
});

// Qualsiasi altra rotta sotto /api non esiste.
api.MapFallback(() => Results.NotFound(new { error = "Rotta non trovata." }));

app.Lifetime.ApplicationStarted.Register(() =>
{
    var url = $"http://127.0.0.1:{listenPort}/";
    var urlWithToken = $"{url}?{SessionToken.QueryParameterName}={sessionToken.Value}";
    Console.WriteLine();
    Console.WriteLine("==========================================================");
    Console.WriteLine("  PC Check è in esecuzione" + (demoEffective ? " (MODALITÀ DIMOSTRATIVA)" : ""));
    Console.WriteLine($"  Indirizzo: {url}");
    Console.WriteLine("  Il collegamento con il token di sessione è stato aperto nel browser.");
    Console.WriteLine("  Se il browser non si apre, incolla questo indirizzo:");
    Console.WriteLine($"  {urlWithToken}");
    Console.WriteLine("  Per chiudere: pulsante \"Chiudi PC Check\" nell'interfaccia oppure Ctrl+C.");
    Console.WriteLine("==========================================================");
    Console.WriteLine();
    if (options.OpenBrowser)
    {
        if (!BrowserLauncher.TryOpen(urlWithToken, out var error))
        {
            logger.LogWarning("Apertura del browser non riuscita: {Error}", error);
        }
    }
});

try
{
    await app.RunAsync();
    return 0;
}
catch (IOException ex)
{
    logger.LogCritical(ex, "Impossibile avviare il server locale sulla porta {Port}", listenPort);
    return 1;
}

static bool IsTruthy(string? value) =>
    value is not null && (value.Equals("1", StringComparison.OrdinalIgnoreCase) || value.Equals("true", StringComparison.OrdinalIgnoreCase) || value.Equals("yes", StringComparison.OrdinalIgnoreCase));

/// <summary>Richieste JSON accettate dalle API (deserializzate con System.Text.Json, nomi insensibili alle maiuscole).</summary>
public sealed record ScanRequest(string? Type);
public sealed record KillProcessRequest(int Pid, string? Name, bool Confirmed, bool AllowElevation);
public sealed record ToggleStartupRequest(string? Id, bool Enabled, bool Confirmed);
public sealed record UninstallRequest(string? Id, bool Confirmed);

/// <summary>Argomenti di riga di comando riconosciuti.</summary>
public sealed class CommandLine
{
    public bool Demo { get; private set; }
    public bool NoBrowser { get; private set; }
    public bool ShowHelp { get; private set; }
    public int? Port { get; private set; }
    public string? ElevatedTask { get; private set; }

    public const string HelpText = """
        PC Check - diagnostica locale di sicurezza e prestazioni

        Uso: PCCheck.exe [opzioni]

          --demo          Modalità dimostrativa: dati fittizi, nessuna lettura o modifica del sistema reale
          --no-browser    Non aprire il browser automaticamente (l'indirizzo viene stampato in console)
          --port N        Usa la porta N su 127.0.0.1 (se libera), altrimenti una porta libera casuale
          --help          Mostra questo testo

        Variabili d'ambiente: PCCHECK_DEMO=1 attiva la modalità dimostrativa.
        """;

    public static CommandLine Parse(string[] args)
    {
        var result = new CommandLine();
        for (var i = 0; i < args.Length; i++)
        {
            switch (args[i].ToLowerInvariant())
            {
                case "--demo":
                    result.Demo = true;
                    break;
                case "--no-browser":
                    result.NoBrowser = true;
                    break;
                case "--help":
                case "-h":
                case "/?":
                    result.ShowHelp = true;
                    break;
                case "--port":
                    if (i + 1 < args.Length && int.TryParse(args[i + 1], out var port) && port is > 0 and < 65536)
                    {
                        result.Port = port;
                    }
                    i++;
                    break;
                case "--task":
                    if (i + 1 < args.Length)
                    {
                        result.ElevatedTask = args[i + 1].ToLowerInvariant();
                    }
                    i++;
                    break;
            }
        }
        return result;
    }
}

/// <summary>Reso pubblico per i test di integrazione (WebApplicationFactory).</summary>
public partial class Program
{
}
