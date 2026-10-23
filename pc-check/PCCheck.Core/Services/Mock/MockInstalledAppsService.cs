using PCCheck.Core.Models;

namespace PCCheck.Core.Services.Mock;

public sealed class MockInstalledAppsService : IInstalledAppsService
{
    private readonly object _lock = new();
    private readonly List<InstalledApp> _apps;

    public MockInstalledAppsService()
    {
        var today = DateOnly.FromDateTime(DateTime.Today);
        _apps = new List<InstalledApp>
        {
            Make("7-Zip 23.01 (x64)", "Igor Pavlov", "23.01", today.AddDays(-400), @"C:\Program Files\7-Zip\", 5_600_000, "Sistema", false, null),
            Make("Adobe Acrobat Reader", "Adobe Inc.", "24.002.20857", today.AddDays(-120), @"C:\Program Files\Adobe\Acrobat DC\", 620_000_000, "Sistema", true, "{AC76BA86-1033-1033-7760-BC15014EA700}"),
            Make("Google Chrome", "Google LLC", "129.0.6668.90", today.AddDays(-30), @"C:\Program Files\Google\Chrome\Application", 110_000_000, "Sistema", false, null),
            Make("LibreOffice 24.2.5.2", "The Document Foundation", "24.2.5.2", today.AddDays(-80), @"C:\Program Files\LibreOffice\", 780_000_000, "Sistema", true, "{1F4C8E2B-6D1A-4C7B-9E3B-5A1D2F3E4C5B}"),
            Make("Microsoft 365 Apps for business - it-it", "Microsoft Corporation", "16.0.17928.20156", today.AddDays(-200), @"C:\Program Files\Microsoft Office", 2_900_000_000, "Sistema", false, null),
            Make("Microsoft Edge", "Microsoft Corporation", "129.0.2792.65", today.AddDays(-7), @"C:\Program Files (x86)\Microsoft\Edge\Application", 650_000_000, "Sistema", false, null),
            Make("Microsoft Teams", "Microsoft Corporation", "1.7.00.21357", today.AddDays(-15), @"C:\Users\utente.demo\AppData\Local\Microsoft\Teams", 320_000_000, "Utente", false, null),
            Make("Spotify", "Spotify AB", "1.2.45.454", today.AddDays(-60), @"C:\Users\utente.demo\AppData\Roaming\Spotify", 300_000_000, "Utente", false, null),
            Make("Discord", "Discord Inc.", "1.0.9163", today.AddDays(-45), @"C:\Users\utente.demo\AppData\Local\Discord", 250_000_000, "Utente", false, null),
            Make("SpeedUpMyPC 2024", null, "6.2.1", today.AddDays(-4), @"C:\Users\utente.demo\AppData\Local\Temp\SpeedUpMyPC\", 48_000_000, "Utente", false, null),
            Make("WeatherBar Toolbar", null, "3.1", today.AddDays(-4), @"C:\Users\utente.demo\AppData\Roaming\WeatherBar", 22_000_000, "Utente", false, null),
            Make("Driver Updater Pro", "Smart PC Tools Ltd", "9.0.3", today.AddDays(-4), @"C:\Program Files (x86)\Driver Updater Pro\", 35_000_000, "Sistema", true, "{8B6F2E1D-3C4A-4B5E-9F0A-1B2C3D4E5F60}"),
            Make("VLC media player", "VideoLAN", "3.0.21", today.AddDays(-300), @"C:\Program Files\VideoLAN\VLC", 190_000_000, "Sistema", false, null),
            Make("Zoom Workplace", "Zoom Video Communications, Inc.", "6.2.0", today.AddDays(-20), @"C:\Users\utente.demo\AppData\Roaming\Zoom\bin", 230_000_000, "Utente", false, null),
            Make("Strumento di cattura personalizzato", null, "1.0", today.AddDays(-500), @"C:\Tools\Cattura", 3_000_000, "Sistema", false, null)
        };
    }

    private static InstalledApp Make(string name, string? publisher, string version, DateOnly installed, string location, long size, string scope, bool msi, string? productCode)
    {
        var key = @"HKLM\SOFTWARE\...\Uninstall\" + (productCode ?? name);
        return new InstalledApp
        {
            Id = StartupService.MakeId(key, name),
            Name = name,
            Publisher = publisher,
            Version = version,
            InstallDate = installed,
            InstallLocation = location,
            EstimatedSizeBytes = size,
            Scope = scope,
            IsMsi = msi,
            ProductCode = productCode,
            UninstallCommand = msi ? "MsiExec.exe /X" + productCode : $"\"{location.TrimEnd('\\')}\\uninstall.exe\"",
            UnknownPublisher = string.IsNullOrEmpty(publisher),
            Hints = InstalledAppsService.BuildHints(name, publisher, location),
            RegistryKey = key
        };
    }

    public Task<IReadOnlyList<InstalledApp>> GetAppsAsync(CancellationToken cancellationToken = default)
    {
        lock (_lock)
        {
            return Task.FromResult<IReadOnlyList<InstalledApp>>(_apps.ToList());
        }
    }

    public Task<ActionOutcome> UninstallAsync(string id, CancellationToken cancellationToken = default)
    {
        lock (_lock)
        {
            var app = _apps.FirstOrDefault(a => a.Id == id);
            if (app is null)
            {
                return Task.FromResult(ActionOutcome.Fail("Programma non trovato."));
            }
            _apps.Remove(app);
            var tool = app.IsMsi ? "Windows Installer (msiexec /x " + app.ProductCode + ")" : "winget uninstall --exact --name \"" + app.Name + "\"";
            return Task.FromResult(ActionOutcome.Ok($"Disinstallazione di \"{app.Name}\" simulata.", "In modalità reale verrebbe usato: " + tool));
        }
    }

    public Task<ActionOutcome> OpenAppsSettingsAsync() =>
        Task.FromResult(ActionOutcome.Ok("In modalità dimostrativa le Impostazioni di Windows non vengono aperte."));

    public bool IsWingetAvailable() => true;
}
