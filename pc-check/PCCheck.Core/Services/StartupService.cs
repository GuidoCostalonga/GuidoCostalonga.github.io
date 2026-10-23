using System.Runtime.Versioning;
using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Logging;
using Microsoft.Win32;
using PCCheck.Core.Infrastructure;
using PCCheck.Core.Models;
using PCCheck.Core.Security;

namespace PCCheck.Core.Services;

/// <summary>
/// Legge le voci di avvio dal registro (chiavi Run/RunOnce di HKCU e HKLM) e dalle cartelle Esecuzione automatica.
/// L'attivazione/disattivazione usa la stessa chiave StartupApproved di Gestione attività: reversibile, nessuna cancellazione.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class StartupService : IStartupService
{
    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string RunOnceKey = @"Software\Microsoft\Windows\CurrentVersion\RunOnce";
    private const string ApprovedRun = @"Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run";
    private const string ApprovedRun32 = @"Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run32";
    private const string ApprovedFolder = @"Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\StartupFolder";

    private readonly ILogger<StartupService> _logger;

    public StartupService(ILogger<StartupService> logger)
    {
        _logger = logger;
    }

    private sealed record Source(RegistryHive Hive, RegistryView View, string Key, string ApprovedKey, string Scope, string Label);

    private static readonly Source[] RegistrySources =
    {
        new(RegistryHive.CurrentUser, RegistryView.Default, RunKey, ApprovedRun, "Utente", @"HKCU\...\Run"),
        new(RegistryHive.CurrentUser, RegistryView.Default, RunOnceKey, ApprovedRun, "Utente", @"HKCU\...\RunOnce"),
        new(RegistryHive.LocalMachine, RegistryView.Registry64, RunKey, ApprovedRun, "Sistema", @"HKLM\...\Run"),
        new(RegistryHive.LocalMachine, RegistryView.Registry64, RunOnceKey, ApprovedRun, "Sistema", @"HKLM\...\RunOnce"),
        new(RegistryHive.LocalMachine, RegistryView.Registry32, RunKey, ApprovedRun32, "Sistema", @"HKLM\...\WOW6432Node\...\Run")
    };

    public Task<IReadOnlyList<StartupEntry>> GetEntriesAsync(CancellationToken cancellationToken = default)
    {
        return Task.Run<IReadOnlyList<StartupEntry>>(() => ReadAll(), cancellationToken);
    }

    private List<StartupEntry> ReadAll()
    {
        var entries = new List<StartupEntry>();
        var trusted = ProcessSampler.DefaultTrustedRoots();

        foreach (var source in RegistrySources)
        {
            try
            {
                using var baseKey = RegistryKey.OpenBaseKey(source.Hive, source.View);
                using var key = baseKey.OpenSubKey(source.Key, writable: false);
                if (key is null)
                {
                    continue;
                }
                using var approved = baseKey.OpenSubKey(source.ApprovedKey, writable: false);
                foreach (var valueName in key.GetValueNames())
                {
                    if (string.IsNullOrEmpty(valueName))
                    {
                        continue;
                    }
                    var command = key.GetValue(valueName)?.ToString() ?? "";
                    var enabled = ReadApproved(approved, valueName);
                    var exePath = ExtractExecutable(command);
                    entries.Add(new StartupEntry
                    {
                        Id = MakeId(source.Label, valueName),
                        Name = valueName,
                        Command = command,
                        Location = source.Label,
                        Scope = source.Scope,
                        Kind = StartupKind.Registry,
                        Enabled = enabled,
                        CanToggle = true,
                        RequiresAdmin = source.Hive == RegistryHive.LocalMachine,
                        Publisher = ReadCompany(exePath),
                        Unknown = ProcessSampler.IsUnknownLocation(exePath, trusted)
                    });
                }
            }
            catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException or IOException)
            {
                _logger.LogWarning(ex, "Lettura della chiave di avvio {Key} non riuscita", source.Label);
            }
        }

        entries.AddRange(ReadFolder(Environment.GetFolderPath(Environment.SpecialFolder.Startup), RegistryHive.CurrentUser, "Utente", "Cartella Esecuzione automatica (utente)", trusted));
        entries.AddRange(ReadFolder(Environment.GetFolderPath(Environment.SpecialFolder.CommonStartup), RegistryHive.LocalMachine, "Sistema", "Cartella Esecuzione automatica (tutti gli utenti)", trusted));

        return entries.OrderBy(e => e.Scope).ThenBy(e => e.Name, StringComparer.OrdinalIgnoreCase).ToList();
    }

    private IEnumerable<StartupEntry> ReadFolder(string folder, RegistryHive hive, string scope, string label, IReadOnlyList<string> trusted)
    {
        var result = new List<StartupEntry>();
        if (string.IsNullOrEmpty(folder) || !Directory.Exists(folder))
        {
            return result;
        }
        try
        {
            using var baseKey = RegistryKey.OpenBaseKey(hive, RegistryView.Default);
            using var approved = baseKey.OpenSubKey(ApprovedFolder, writable: false);
            foreach (var file in Directory.EnumerateFiles(folder))
            {
                var name = Path.GetFileName(file);
                if (string.Equals(name, "desktop.ini", StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }
                result.Add(new StartupEntry
                {
                    Id = MakeId(label, name),
                    Name = name,
                    Command = file,
                    Location = label,
                    Scope = scope,
                    Kind = StartupKind.Folder,
                    Enabled = ReadApproved(approved, name),
                    CanToggle = true,
                    RequiresAdmin = hive == RegistryHive.LocalMachine,
                    Publisher = null,
                    Unknown = false
                });
            }
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Security.SecurityException)
        {
            _logger.LogWarning(ex, "Lettura della cartella di avvio {Folder} non riuscita", folder);
        }
        return result;
    }

    /// <summary>Nella chiave StartupApproved il primo byte vale 2 (attivo) o 3 (disattivato); se la voce manca, e' attiva.</summary>
    public static bool ReadApproved(RegistryKey? approved, string valueName)
    {
        if (approved is null)
        {
            return true;
        }
        try
        {
            return approved.GetValue(valueName) is byte[] { Length: > 0 } data ? IsEnabledFlag(data[0]) : true;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            return true;
        }
    }

    public static bool IsEnabledFlag(byte flag) => flag % 2 == 0;

    public static byte[] BuildApprovedValue(bool enabled, DateTimeOffset when)
    {
        var data = new byte[12];
        data[0] = enabled ? (byte)0x02 : (byte)0x03;
        if (!enabled)
        {
            var fileTime = when.ToFileTime();
            BitConverter.TryWriteBytes(data.AsSpan(4, 8), fileTime);
        }
        return data;
    }

    public async Task<ActionOutcome> SetEnabledAsync(string id, bool enabled, CancellationToken cancellationToken = default)
    {
        var entries = await GetEntriesAsync(cancellationToken);
        var entry = entries.FirstOrDefault(e => e.Id == id);
        if (entry is null)
        {
            return ActionOutcome.Fail("Voce di avvio non trovata: l'elenco potrebbe essere cambiato, aggiornalo e riprova.");
        }
        if (!CommandPolicy.IsSafeRegistryValueName(entry.Name))
        {
            return ActionOutcome.Fail("Il nome della voce contiene caratteri non gestibili in sicurezza.");
        }

        var approvedKeyPath = ResolveApprovedKey(entry);
        var data = BuildApprovedValue(enabled, DateTimeOffset.Now);
        var verb = enabled ? "attivata" : "disattivata";

        if (!entry.RequiresAdmin)
        {
            try
            {
                using var key = Registry.CurrentUser.CreateSubKey(approvedKeyPath, writable: true);
                key.SetValue(entry.Name, data, RegistryValueKind.Binary);
                return ActionOutcome.Ok($"Voce \"{entry.Name}\" {verb}. La modifica ha effetto al prossimo accesso ed è reversibile da qui o da Gestione attività.");
            }
            catch (Exception ex) when (ex is UnauthorizedAccessException or System.Security.SecurityException or IOException)
            {
                _logger.LogWarning(ex, "Scrittura StartupApproved non riuscita per {Name}", entry.Name);
                return ActionOutcome.Fail("Scrittura nel registro non riuscita: " + ex.Message);
            }
        }

        // Voci HKLM: scrittura mirata con reg.exe elevato (UAC), argomenti passati come lista.
        var regExe = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "reg.exe");
        var args = new[]
        {
            "add", @"HKLM\" + approvedKeyPath,
            "/v", entry.Name,
            "/t", "REG_BINARY",
            "/d", CommandPolicy.ToRegBinaryHex(data),
            "/f"
        };
        var result = await ElevationHelper.RunElevatedAsync(regExe, args, TimeSpan.FromMinutes(2), cancellationToken);
        if (result.UserCancelled)
        {
            return ActionOutcome.Cancelled("Permessi di amministratore non concessi: nessuna modifica effettuata.");
        }
        if (!result.Started || result.ExitCode != 0)
        {
            return ActionOutcome.Fail(result.Error ?? $"reg.exe ha restituito il codice {result.ExitCode}.", requiresAdmin: true);
        }
        return ActionOutcome.Ok($"Voce di sistema \"{entry.Name}\" {verb} con permessi di amministratore. Modifica reversibile.");
    }

    private static string ResolveApprovedKey(StartupEntry entry)
    {
        if (entry.Kind == StartupKind.Folder)
        {
            return ApprovedFolder;
        }
        return entry.Location.Contains("WOW6432Node", StringComparison.OrdinalIgnoreCase) ? ApprovedRun32 : ApprovedRun;
    }

    public static string MakeId(string location, string name)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(location.ToLowerInvariant() + "|" + name.ToLowerInvariant()));
        return Convert.ToHexString(bytes, 0, 10).ToLowerInvariant();
    }

    /// <summary>Estrae il percorso dell'eseguibile da una riga di comando (gestisce virgolette e variabili d'ambiente).</summary>
    public static string? ExtractExecutable(string command)
    {
        if (string.IsNullOrWhiteSpace(command))
        {
            return null;
        }
        var text = Environment.ExpandEnvironmentVariables(command.Trim());
        string candidate;
        if (text.StartsWith('"'))
        {
            var end = text.IndexOf('"', 1);
            candidate = end > 1 ? text[1..end] : text.Trim('"');
        }
        else
        {
            var exeIndex = text.IndexOf(".exe", StringComparison.OrdinalIgnoreCase);
            candidate = exeIndex > 0 ? text[..(exeIndex + 4)] : text.Split(' ', 2)[0];
        }
        return candidate.Length == 0 ? null : candidate;
    }

    private static string? ReadCompany(string? exePath)
    {
        if (string.IsNullOrEmpty(exePath) || !File.Exists(exePath))
        {
            return null;
        }
        try
        {
            var info = System.Diagnostics.FileVersionInfo.GetVersionInfo(exePath);
            return string.IsNullOrWhiteSpace(info.CompanyName) ? null : info.CompanyName.Trim();
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            return null;
        }
    }
}
