using PCCheck.Core.Models;

namespace PCCheck.Core.Services.Mock;

/// <summary>Dati dimostrativi realistici: nessuna chiamata a WMI o a MpCmdRun.exe.</summary>
public sealed class MockDefenderService : IDefenderService
{
    private readonly object _lock = new();
    private ScanStatus _status = new();
    private CancellationTokenSource? _scanCts;
    private readonly List<string> _output = new();

    public Task<DefenderStatus> GetStatusAsync(CancellationToken cancellationToken = default)
    {
        var now = DateTimeOffset.Now;
        return Task.FromResult(new DefenderStatus
        {
            Available = true,
            AntivirusEnabled = true,
            AntispywareEnabled = true,
            RealTimeProtectionEnabled = true,
            BehaviorMonitorEnabled = true,
            OnAccessProtectionEnabled = true,
            NetworkInspectionEnabled = true,
            TamperProtected = true,
            SignatureVersion = "1.421.1587.0",
            SignatureLastUpdated = now.AddHours(-6),
            SignatureAgeDays = 0,
            EngineVersion = "1.1.24090.11",
            PlatformVersion = "4.18.24090.11",
            LastQuickScanEnd = now.AddDays(-9),
            LastFullScanEnd = now.AddDays(-41),
            QuickScanAgeDays = 9,
            FullScanAgeDays = 41,
            Warnings = new[] { "Nessuna scansione rapida negli ultimi 7 giorni (dato dimostrativo)." },
            ScannerPath = @"C:\ProgramData\Microsoft\Windows Defender\Platform\4.18.24090.11-0\MpCmdRun.exe"
        });
    }

    public Task<IReadOnlyList<ThreatInfo>> GetThreatsAsync(CancellationToken cancellationToken = default)
    {
        var now = DateTimeOffset.Now;
        IReadOnlyList<ThreatInfo> list = new[]
        {
            new ThreatInfo
            {
                Id = "2147735503",
                Name = "PUA:Win32/Presenoker",
                Severity = "Bassa",
                SeverityLevel = 1,
                Category = "Software potenzialmente indesiderato",
                Status = "In quarantena",
                IsActive = false,
                DetectedAt = now.AddDays(-3),
                LastChangeAt = now.AddDays(-3),
                Resources = new[] { @"file:_C:\Users\utente.demo\Downloads\DriverUpdaterSetup.exe" },
                ProcessName = "msedge.exe"
            },
            new ThreatInfo
            {
                Id = "2147725359",
                Name = "Trojan:Win32/Wacatac.B!ml",
                Severity = "Grave",
                SeverityLevel = 5,
                Category = "Trojan",
                Status = "Rimossa",
                IsActive = false,
                DetectedAt = now.AddDays(-27),
                LastChangeAt = now.AddDays(-27),
                Resources = new[] { @"file:_C:\Users\utente.demo\AppData\Local\Temp\7zS8A3F\setup_crack.exe" },
                ProcessName = "explorer.exe"
            }
        };
        return Task.FromResult(list);
    }

    public Task<ScanStatus> StartScanAsync(ScanType type, CancellationToken cancellationToken = default)
    {
        lock (_lock)
        {
            if (_status.State == ScanState.Running)
            {
                return Task.FromResult(_status);
            }
            _output.Clear();
            _output.Add("Scan starting...");
            _scanCts = new CancellationTokenSource();
            var started = DateTimeOffset.Now;
            _status = new ScanStatus
            {
                State = ScanState.Running,
                Type = type,
                StartedAt = started,
                Message = type == ScanType.Quick ? "Scansione rapida dimostrativa in corso." : "Scansione completa dimostrativa in corso.",
                Output = _output.ToArray(),
                Command = $"\"C:\\ProgramData\\Microsoft\\Windows Defender\\Platform\\4.18.24090.11-0\\MpCmdRun.exe\" -Scan -ScanType {(int)type}"
            };
            var token = _scanCts.Token;
            var duration = type == ScanType.Quick ? TimeSpan.FromSeconds(12) : TimeSpan.FromSeconds(30);
            _ = Task.Run(async () =>
            {
                var steps = new[] { @"C:\Windows\System32", @"C:\Program Files", @"C:\Users\utente.demo\AppData", @"C:\Users\utente.demo\Downloads", "Memoria dei processi" };
                try
                {
                    foreach (var step in steps)
                    {
                        await Task.Delay(duration / steps.Length, token);
                        lock (_lock)
                        {
                            _output.Add($"Scanning {step} ...");
                            _status = Clone(_status, _status.Message);
                        }
                    }
                    lock (_lock)
                    {
                        _output.Add("Scan finished.");
                        _output.Add("Scanning C:\\ found no threats.");
                        _status = new ScanStatus
                        {
                            State = ScanState.Completed,
                            Type = type,
                            StartedAt = started,
                            FinishedAt = DateTimeOffset.Now,
                            ExitCode = 0,
                            Message = "Scansione dimostrativa terminata: nessuna minaccia trovata.",
                            Output = _output.ToArray(),
                            Command = _status.Command
                        };
                    }
                }
                catch (OperationCanceledException)
                {
                    lock (_lock)
                    {
                        _status = new ScanStatus
                        {
                            State = ScanState.Cancelled,
                            Type = type,
                            StartedAt = started,
                            FinishedAt = DateTimeOffset.Now,
                            Message = "Scansione dimostrativa interrotta dall'utente.",
                            Output = _output.ToArray(),
                            Command = _status.Command
                        };
                    }
                }
            }, CancellationToken.None);
            return Task.FromResult(_status);
        }
    }

    private ScanStatus Clone(ScanStatus s, string message) => new()
    {
        State = s.State,
        Type = s.Type,
        StartedAt = s.StartedAt,
        FinishedAt = s.FinishedAt,
        ExitCode = s.ExitCode,
        Message = message,
        Output = _output.ToArray(),
        Command = s.Command
    };

    public ScanStatus GetScanStatus()
    {
        lock (_lock)
        {
            return _status;
        }
    }

    public Task<ScanStatus> CancelScanAsync()
    {
        lock (_lock)
        {
            _scanCts?.Cancel();
            return Task.FromResult(_status);
        }
    }
}
