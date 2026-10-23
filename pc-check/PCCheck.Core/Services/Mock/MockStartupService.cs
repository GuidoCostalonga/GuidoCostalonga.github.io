using PCCheck.Core.Models;

namespace PCCheck.Core.Services.Mock;

public sealed class MockStartupService : IStartupService
{
    private readonly object _lock = new();
    private readonly List<StartupEntry> _entries;

    public MockStartupService()
    {
        _entries = new List<StartupEntry>
        {
            Make("OneDrive", @"""C:\Users\utente.demo\AppData\Local\Microsoft\OneDrive\OneDrive.exe"" /background", @"HKCU\...\Run", "Utente", true, false, "Microsoft Corporation", false),
            Make("com.squirrel.Teams.Teams", @"C:\Users\utente.demo\AppData\Local\Microsoft\Teams\Update.exe --processStart ""Teams.exe"" --process-start-args ""--system-initiated""", @"HKCU\...\Run", "Utente", true, false, "Microsoft Corporation", false),
            Make("Spotify", @"""C:\Users\utente.demo\AppData\Roaming\Spotify\Spotify.exe"" --autostart --minimized", @"HKCU\...\Run", "Utente", true, false, "Spotify AB", true),
            Make("Discord", @"C:\Users\utente.demo\AppData\Local\Discord\Update.exe --processStart Discord.exe", @"HKCU\...\Run", "Utente", false, false, "Discord Inc.", true),
            Make("SpeedUpMyPC", @"""C:\Users\utente.demo\AppData\Local\Temp\SpeedUpMyPC\SpeedUpMyPC.exe"" /silent", @"HKCU\...\Run", "Utente", true, false, null, true),
            Make("WeatherBar Helper", @"C:\Users\utente.demo\AppData\Roaming\WeatherBar\wbhelper.exe", @"HKCU\...\Run", "Utente", true, false, null, true),
            Make("SecurityHealth", @"%windir%\system32\SecurityHealthSystray.exe", @"HKLM\...\Run", "Sistema", true, true, "Microsoft Corporation", false),
            Make("RtkAudUService", @"""C:\Windows\System32\DriverStore\FileRepository\realtekservice.inf_amd64_1f3c4e4a7f2c1b1a\RtkAudUService64.exe"" -background", @"HKLM\...\Run", "Sistema", true, true, "Realtek Semiconductor", false),
            Make("Adobe Updater Startup Utility.lnk", @"C:\ProgramData\Microsoft\Windows\Start Menu\Programs\StartUp\Adobe Updater Startup Utility.lnk", "Cartella Esecuzione automatica (tutti gli utenti)", "Sistema", true, true, "Adobe Inc.", false, StartupKind.Folder),
            Make("Promemoria.lnk", @"C:\Users\utente.demo\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\Promemoria.lnk", "Cartella Esecuzione automatica (utente)", "Utente", true, false, null, false, StartupKind.Folder)
        };
    }

    private static StartupEntry Make(string name, string command, string location, string scope, bool enabled, bool admin, string? publisher, bool unknown, StartupKind kind = StartupKind.Registry) => new()
    {
        Id = StartupService.MakeId(location, name),
        Name = name,
        Command = command,
        Location = location,
        Scope = scope,
        Kind = kind,
        Enabled = enabled,
        CanToggle = true,
        RequiresAdmin = admin,
        Publisher = publisher,
        Unknown = unknown
    };

    public Task<IReadOnlyList<StartupEntry>> GetEntriesAsync(CancellationToken cancellationToken = default)
    {
        lock (_lock)
        {
            return Task.FromResult<IReadOnlyList<StartupEntry>>(_entries.ToList());
        }
    }

    public Task<ActionOutcome> SetEnabledAsync(string id, bool enabled, CancellationToken cancellationToken = default)
    {
        lock (_lock)
        {
            var index = _entries.FindIndex(e => e.Id == id);
            if (index < 0)
            {
                return Task.FromResult(ActionOutcome.Fail("Voce di avvio non trovata."));
            }
            var e = _entries[index];
            _entries[index] = new StartupEntry
            {
                Id = e.Id, Name = e.Name, Command = e.Command, Location = e.Location, Scope = e.Scope, Kind = e.Kind,
                Enabled = enabled, CanToggle = e.CanToggle, RequiresAdmin = e.RequiresAdmin, Publisher = e.Publisher, Unknown = e.Unknown
            };
            var verb = enabled ? "attivata" : "disattivata";
            var admin = e.RequiresAdmin ? " (simulazione di richiesta UAC)" : "";
            return Task.FromResult(ActionOutcome.Ok($"Voce \"{e.Name}\" {verb}{admin}. La modifica è reversibile."));
        }
    }
}
