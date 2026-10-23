using System.Reflection;
using System.Runtime.InteropServices;
using PCCheck.Core.Infrastructure;
using PCCheck.Core.Models;

namespace PCCheck.Core.Services;

public sealed class SystemInfoService : ISystemInfoService
{
    private readonly bool _demo;
    private readonly IInstalledAppsService _apps;
    private readonly DateTimeOffset _startedAt = DateTimeOffset.Now;

    public SystemInfoService(AppOptions options, IInstalledAppsService apps)
    {
        _demo = options.Demo;
        _apps = apps;
    }

    public SystemInfo GetInfo()
    {
        var version = Assembly.GetExecutingAssembly().GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion
                      ?? Assembly.GetExecutingAssembly().GetName().Version?.ToString() ?? "1.0.0";
        var plus = version.IndexOf('+');
        if (plus > 0)
        {
            version = version[..plus];
        }
        return new SystemInfo
        {
            Version = version,
            DemoMode = _demo,
            MachineName = _demo ? "PC-DEMO" : Environment.MachineName,
            UserName = _demo ? "utente.demo" : Environment.UserName,
            OsDescription = _demo ? "Microsoft Windows 11 Pro 23H2 (build 22631) - dati dimostrativi" : RuntimeInformation.OSDescription,
            IsWindows = OperatingSystem.IsWindows(),
            IsAdministrator = !_demo && ElevationHelper.IsAdministrator(),
            WingetAvailable = _apps.IsWingetAvailable(),
            StartedAt = _startedAt
        };
    }
}

/// <summary>Opzioni di avvio lette da riga di comando, variabili d'ambiente e appsettings.json.</summary>
public sealed class AppOptions
{
    public bool Demo { get; init; }
    public bool OpenBrowser { get; init; } = true;
    public int Port { get; init; }
    public int FallbackPort { get; init; } = 5000;
}
