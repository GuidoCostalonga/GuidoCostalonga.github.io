using System.Diagnostics;
using System.Globalization;
using System.Runtime.Versioning;
using Microsoft.Extensions.Logging;
using Microsoft.Win32;
using PCCheck.Core.Models;
using PCCheck.Core.Security;

namespace PCCheck.Core.Services;

/// <summary>
/// Elenca i programmi installati dalle chiavi Uninstall del registro e guida la disinstallazione
/// con strumenti standard: winget (se presente), msiexec per i pacchetti MSI, oppure Impostazioni di Windows.
/// Nessuna stringa UninstallString del registro viene mai eseguita.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class InstalledAppsService : IInstalledAppsService
{
    private const string UninstallKey = @"Software\Microsoft\Windows\CurrentVersion\Uninstall";

    private static readonly string[] UnwantedKeywords =
    {
        "toolbar", "optimizer", "pc cleaner", "pccleaner", "driver updater", "driver booster", "web companion",
        "search protect", "speedupmypc", "speed up my pc", "registry cleaner", "weatherbar", "weather bar",
        "coupon", "savings", "browser assistant", "mystart", "conduit", "ask toolbar", "babylon", "softonic",
        "opencandy", "funmoods", "delta search", "sweetim", "iminent", "wajam", "reimage", "advanced system repair",
        "system care", "smart defrag", "pc accelerate", "one system care"
    };

    private readonly ILogger<InstalledAppsService> _logger;

    public InstalledAppsService(ILogger<InstalledAppsService> logger)
    {
        _logger = logger;
    }

    private sealed record Source(RegistryHive Hive, RegistryView View, string Scope, string Label);

    private static readonly Source[] Sources =
    {
        new(RegistryHive.LocalMachine, RegistryView.Registry64, "Sistema", @"HKLM\SOFTWARE\...\Uninstall"),
        new(RegistryHive.LocalMachine, RegistryView.Registry32, "Sistema", @"HKLM\SOFTWARE\WOW6432Node\...\Uninstall"),
        new(RegistryHive.CurrentUser, RegistryView.Default, "Utente", @"HKCU\Software\...\Uninstall")
    };

    public Task<IReadOnlyList<InstalledApp>> GetAppsAsync(CancellationToken cancellationToken = default)
    {
        return Task.Run<IReadOnlyList<InstalledApp>>(() => ReadAll(), cancellationToken);
    }

    private List<InstalledApp> ReadAll()
    {
        var apps = new Dictionary<string, InstalledApp>(StringComparer.OrdinalIgnoreCase);
        foreach (var source in Sources)
        {
            try
            {
                using var baseKey = RegistryKey.OpenBaseKey(source.Hive, source.View);
                using var key = baseKey.OpenSubKey(UninstallKey, writable: false);
                if (key is null)
                {
                    continue;
                }
                foreach (var subName in key.GetSubKeyNames())
                {
                    try
                    {
                        using var sub = key.OpenSubKey(subName, writable: false);
                        if (sub is null)
                        {
                            continue;
                        }
                        var app = ReadApp(sub, subName, source);
                        if (app is not null && !apps.ContainsKey(app.Name + "|" + app.Version))
                        {
                            apps[app.Name + "|" + app.Version] = app;
                        }
                    }
                    catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException or IOException)
                    {
                        _logger.LogDebug(ex, "Chiave {Key} non leggibile", subName);
                    }
                }
            }
            catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException or IOException)
            {
                _logger.LogWarning(ex, "Lettura di {Label} non riuscita", source.Label);
            }
        }
        return apps.Values.OrderBy(a => a.Name, StringComparer.OrdinalIgnoreCase).ToList();
    }

    private static InstalledApp? ReadApp(RegistryKey sub, string subName, Source source)
    {
        var name = sub.GetValue("DisplayName")?.ToString()?.Trim();
        if (string.IsNullOrEmpty(name))
        {
            return null;
        }
        if (sub.GetValue("SystemComponent") is int sc && sc == 1)
        {
            return null;
        }
        var releaseType = sub.GetValue("ReleaseType")?.ToString();
        var parentKey = sub.GetValue("ParentKeyName")?.ToString();
        if (!string.IsNullOrEmpty(parentKey) || string.Equals(releaseType, "Update", StringComparison.OrdinalIgnoreCase) ||
            string.Equals(releaseType, "Hotfix", StringComparison.OrdinalIgnoreCase))
        {
            return null; // aggiornamenti, non programmi
        }

        var publisher = sub.GetValue("Publisher")?.ToString()?.Trim();
        var version = sub.GetValue("DisplayVersion")?.ToString()?.Trim();
        var location = sub.GetValue("InstallLocation")?.ToString()?.Trim().Trim('"');
        var uninstall = sub.GetValue("UninstallString")?.ToString()?.Trim();
        var isMsi = sub.GetValue("WindowsInstaller") is int wi && wi == 1;
        var productCode = CommandPolicy.IsValidMsiProductCode(subName) ? subName : null;
        if (productCode is not null)
        {
            isMsi = true;
        }

        long? size = null;
        if (sub.GetValue("EstimatedSize") is int kb && kb > 0)
        {
            size = (long)kb * 1024;
        }

        DateOnly? installDate = null;
        var dateText = sub.GetValue("InstallDate")?.ToString();
        if (!string.IsNullOrEmpty(dateText) && DateOnly.TryParseExact(dateText, "yyyyMMdd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var d))
        {
            installDate = d;
        }

        var hints = BuildHints(name, publisher, location);
        return new InstalledApp
        {
            Id = StartupService.MakeId(source.Label + "\\" + subName, name),
            Name = name,
            Publisher = string.IsNullOrEmpty(publisher) ? null : publisher,
            Version = string.IsNullOrEmpty(version) ? null : version,
            InstallDate = installDate,
            InstallLocation = string.IsNullOrEmpty(location) ? null : location,
            EstimatedSizeBytes = size,
            Scope = source.Scope,
            IsMsi = isMsi,
            ProductCode = productCode,
            UninstallCommand = uninstall,
            UnknownPublisher = string.IsNullOrEmpty(publisher),
            Hints = hints,
            RegistryKey = source.Label + "\\" + subName
        };
    }

    /// <summary>Indizi euristici, mostrati come "da verificare": non sono mai una diagnosi di malware.</summary>
    public static IReadOnlyList<string> BuildHints(string name, string? publisher, string? location)
    {
        var hints = new List<string>();
        var lowerName = name.ToLowerInvariant();
        if (string.IsNullOrWhiteSpace(publisher))
        {
            hints.Add("Editore non dichiarato");
        }
        foreach (var keyword in UnwantedKeywords)
        {
            if (lowerName.Contains(keyword, StringComparison.Ordinal))
            {
                hints.Add($"Nome tipico di software indesiderato (\"{keyword}\")");
                break;
            }
        }
        if (!string.IsNullOrEmpty(location))
        {
            var lowerLoc = location.ToLowerInvariant();
            if (lowerLoc.Contains(@"\temp\") || lowerLoc.Contains(@"\appdata\local\temp") || lowerLoc.Contains(@"\downloads\"))
            {
                hints.Add("Installato in una cartella temporanea o dei download");
            }
        }
        return hints;
    }

    public bool IsWingetAvailable() => FindWinget() is not null;

    private static string? FindWinget()
    {
        if (!OperatingSystem.IsWindows())
        {
            return null;
        }
        var local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        if (string.IsNullOrEmpty(local))
        {
            return null;
        }
        var path = Path.Combine(local, "Microsoft", "WindowsApps", "winget.exe");
        return File.Exists(path) ? path : null;
    }

    public async Task<ActionOutcome> UninstallAsync(string id, CancellationToken cancellationToken = default)
    {
        var apps = await GetAppsAsync(cancellationToken);
        var app = apps.FirstOrDefault(a => a.Id == id);
        if (app is null)
        {
            return ActionOutcome.Fail("Programma non trovato: aggiorna l'elenco e riprova.");
        }

        var winget = FindWinget();
        if (winget is not null && CommandPolicy.IsSafePackageName(app.Name))
        {
            var psi = new ProcessStartInfo
            {
                FileName = winget,
                UseShellExecute = true,
                CreateNoWindow = false
            };
            foreach (var a in new[] { "uninstall", "--exact", "--name", app.Name, "--interactive" })
            {
                psi.ArgumentList.Add(a);
            }
            try
            {
                Process.Start(psi);
                return ActionOutcome.Ok($"Disinstallazione di \"{app.Name}\" avviata con winget in una finestra separata. Segui le indicazioni del programma di disinstallazione.",
                    "Comando: winget uninstall --exact --name \"" + app.Name + "\" --interactive");
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Avvio di winget non riuscito");
            }
        }

        if (app.ProductCode is not null && CommandPolicy.IsValidMsiProductCode(app.ProductCode))
        {
            var msiexec = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "msiexec.exe");
            var psi = new ProcessStartInfo
            {
                FileName = msiexec,
                UseShellExecute = true
            };
            psi.ArgumentList.Add("/x");
            psi.ArgumentList.Add(app.ProductCode);
            try
            {
                Process.Start(psi);
                return ActionOutcome.Ok($"Disinstallazione guidata di \"{app.Name}\" avviata tramite Windows Installer.",
                    "Comando: msiexec /x " + app.ProductCode);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Avvio di msiexec non riuscito");
            }
        }

        var settings = await OpenAppsSettingsAsync();
        return settings.Success
            ? ActionOutcome.Ok($"Si è aperta la pagina Impostazioni > App: cerca \"{app.Name}\" e usa il pulsante Disinstalla.")
            : settings;
    }

    public Task<ActionOutcome> OpenAppsSettingsAsync()
    {
        try
        {
            Process.Start(new ProcessStartInfo { FileName = "ms-settings:appsfeatures", UseShellExecute = true });
            return Task.FromResult(ActionOutcome.Ok("Impostazioni di Windows > App e funzionalità aperte."));
        }
        catch (Exception ex)
        {
            return Task.FromResult(ActionOutcome.Fail("Impossibile aprire le Impostazioni: " + ex.Message));
        }
    }
}
