using System.Text;
using PCCheck.Core.Models;

namespace PCCheck.Core.Services;

/// <summary>Raccoglie tutti i dati in un unico rapporto, calcola le segnalazioni e produce la versione HTML.</summary>
public sealed class ReportService
{
    private readonly IDefenderService _defender;
    private readonly IPerformanceService _performance;
    private readonly IStartupService _startup;
    private readonly IInstalledAppsService _apps;
    private readonly ITempCleanerService _temp;
    private readonly ISystemInfoService _system;

    public ReportService(IDefenderService defender, IPerformanceService performance, IStartupService startup,
        IInstalledAppsService apps, ITempCleanerService temp, ISystemInfoService system)
    {
        _defender = defender;
        _performance = performance;
        _startup = startup;
        _apps = apps;
        _temp = temp;
        _system = system;
    }

    public async Task<SystemReport> BuildAsync(int sampleMilliseconds, CancellationToken cancellationToken = default)
    {
        var defenderTask = _defender.GetStatusAsync(cancellationToken);
        var threatsTask = _defender.GetThreatsAsync(cancellationToken);
        var perfTask = _performance.GetSnapshotAsync(sampleMilliseconds, cancellationToken);
        var startupTask = _startup.GetEntriesAsync(cancellationToken);
        var appsTask = _apps.GetAppsAsync(cancellationToken);
        var tempTask = _temp.AnalyzeAsync(false, cancellationToken);
        await Task.WhenAll(defenderTask, threatsTask, perfTask, startupTask, appsTask, tempTask);

        var defender = defenderTask.Result;
        var threats = threatsTask.Result;
        var perf = perfTask.Result;
        var startup = startupTask.Result;
        var apps = appsTask.Result;
        var temp = tempTask.Result;

        return new SystemReport
        {
            GeneratedAt = DateTimeOffset.Now,
            System = _system.GetInfo(),
            Defender = defender,
            Threats = threats,
            Scan = _defender.GetScanStatus(),
            Performance = perf,
            Startup = startup,
            Apps = apps,
            Temp = temp,
            Findings = BuildFindings(defender, threats, perf, startup, apps, temp)
        };
    }

    public static IReadOnlyList<Finding> BuildFindings(DefenderStatus defender, IReadOnlyList<ThreatInfo> threats, PerformanceSnapshot perf,
        IReadOnlyList<StartupEntry> startup, IReadOnlyList<InstalledApp> apps, TempAnalysis temp)
    {
        var findings = new List<Finding>();

        var activeThreats = threats.Where(t => t.IsActive || t.Status is "Rilevata" or "Rimozione non riuscita" or "Quarantena non riuscita" or "Pulizia non riuscita").ToList();
        if (activeThreats.Count > 0)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Critical,
                Kind = "threat",
                Section = "scan",
                Title = $"{activeThreats.Count} minacce certe ancora attive secondo Microsoft Defender",
                Description = "Defender ha rilevato minacce non ancora rimosse: " + string.Join(", ", activeThreats.Take(3).Select(t => t.Name)) +
                              ". Apri Sicurezza di Windows per completare la rimozione e avvia una scansione completa."
            });
        }
        else if (threats.Count > 0)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Info,
                Kind = "threat-history",
                Section = "scan",
                Title = $"{threats.Count} minacce nella cronologia, tutte gestite",
                Description = "Le rilevazioni passate risultano pulite, rimosse o in quarantena. Nessuna azione urgente."
            });
        }

        if (!defender.Available)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.High,
                Kind = "defender-unavailable",
                Section = "scan",
                Title = "Stato di Microsoft Defender non disponibile",
                Description = defender.Error ?? "Impossibile leggere lo stato dell'antivirus. Verifica che sia attivo un antivirus in Sicurezza di Windows."
            });
        }
        else
        {
            if (!defender.AntivirusEnabled)
            {
                findings.Add(new Finding
                {
                    Severity = FindingSeverity.High,
                    Kind = "defender-off",
                    Section = "scan",
                    Title = "Antivirus Microsoft Defender disattivato",
                    Description = "Se non è installato un altro antivirus, il computer è senza protezione. Riattiva Defender da Sicurezza di Windows."
                });
            }
            else if (!defender.RealTimeProtectionEnabled)
            {
                findings.Add(new Finding
                {
                    Severity = FindingSeverity.High,
                    Kind = "realtime-off",
                    Section = "scan",
                    Title = "Protezione in tempo reale disattivata",
                    Description = "I file aperti e scaricati non vengono controllati. Riattivala da Sicurezza di Windows > Protezione da virus e minacce."
                });
            }
            if (defender.SignatureAgeDays is > 7)
            {
                findings.Add(new Finding
                {
                    Severity = FindingSeverity.Medium,
                    Kind = "signatures-old",
                    Section = "scan",
                    Title = $"Definizioni antivirus vecchie di {defender.SignatureAgeDays} giorni",
                    Description = "Esegui Windows Update o l'aggiornamento della protezione da Sicurezza di Windows."
                });
            }
            if (defender.QuickScanAgeDays is null or > 7)
            {
                findings.Add(new Finding
                {
                    Severity = FindingSeverity.Low,
                    Kind = "scan-old",
                    Section = "scan",
                    Title = "Nessuna scansione rapida recente",
                    Description = "Avvia una scansione rapida dalla sezione Scansione: richiede pochi minuti."
                });
            }
        }

        if (perf.CpuPercent >= 80)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Medium,
                Kind = "cpu-high",
                Section = "performance",
                Title = $"CPU al {perf.CpuPercent:0}% durante il campionamento",
                Description = "Controlla i processi ad alto impatto nella sezione Prestazioni."
            });
        }
        if (perf.MemoryPercent >= 90)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Medium,
                Kind = "memory-high",
                Section = "performance",
                Title = $"Memoria quasi esaurita ({perf.MemoryPercent:0}%)",
                Description = "Chiudi i programmi non necessari o valuta un aumento della RAM."
            });
        }
        foreach (var disk in perf.Disks.Where(d => d.TotalBytes > 0 && d.FreeBytes * 100.0 / d.TotalBytes < 10))
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Medium,
                Kind = "disk-full",
                Section = "temp",
                Title = $"Disco {disk.Name} quasi pieno ({TempCleanerService.FormatBytes(disk.FreeBytes)} liberi)",
                Description = "Un disco quasi pieno rallenta Windows. Libera spazio dai file temporanei e dai programmi inutilizzati."
            });
        }
        if (perf.HighImpactProcesses.Count > 0)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Info,
                Kind = "high-impact",
                Section = "performance",
                Title = $"{perf.HighImpactProcesses.Count} programmi ad alto impatto di risorse",
                Description = string.Join(", ", perf.HighImpactProcesses.Take(4).Select(p => $"{p.Name} ({p.CpuPercent:0}% CPU, {TempCleanerService.FormatBytes(p.MemoryBytes)})")) +
                              ". Non sono necessariamente dannosi: sono i programmi che consumano di più in questo momento."
            });
        }
        var unknownProcesses = perf.TopProcesses.Where(p => p.Unknown).ToList();
        if (unknownProcesses.Count > 0)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Low,
                Kind = "unknown-process",
                Section = "performance",
                Title = $"{unknownProcesses.Count} processi avviati da cartelle insolite",
                Description = string.Join(", ", unknownProcesses.Take(4).Select(p => p.Name)) +
                              ": l'eseguibile non si trova in Programmi o Windows. Verifica che si tratti di software che conosci."
            });
        }

        var enabledStartup = startup.Count(s => s.Enabled);
        if (enabledStartup > 10)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Low,
                Kind = "startup-many",
                Section = "startup",
                Title = $"{enabledStartup} programmi all'avvio",
                Description = "Molti programmi all'avvio rallentano l'accensione. Disattiva quelli che non servono subito (modifica reversibile)."
            });
        }
        var unknownStartup = startup.Where(s => s.Enabled && s.Unknown).ToList();
        if (unknownStartup.Count > 0)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Low,
                Kind = "startup-unknown",
                Section = "startup",
                Title = $"{unknownStartup.Count} voci di avvio da cartelle insolite",
                Description = string.Join(", ", unknownStartup.Take(4).Select(s => s.Name)) + ": programmi sconosciuti da verificare, non necessariamente dannosi."
            });
        }

        var suspiciousApps = apps.Where(a => a.Hints.Count > 1 || a.Hints.Any(h => h.StartsWith("Nome tipico", StringComparison.Ordinal))).ToList();
        if (suspiciousApps.Count > 0)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Low,
                Kind = "apps-suspicious",
                Section = "apps",
                Title = $"{suspiciousApps.Count} programmi da verificare",
                Description = string.Join(", ", suspiciousApps.Take(4).Select(a => a.Name)) + ": il nome o la provenienza ricordano software indesiderato. Se non li riconosci, disinstallali."
            });
        }
        var unknownPublisher = apps.Count(a => a.UnknownPublisher);
        if (unknownPublisher > 0)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Info,
                Kind = "apps-unknown-publisher",
                Section = "apps",
                Title = $"{unknownPublisher} programmi senza editore dichiarato",
                Description = "Non è un segnale di pericolo di per sé, ma vale la pena controllare che siano programmi che hai installato tu."
            });
        }

        if (temp.TotalReclaimableBytes >= 1024L * 1024 * 1024)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Low,
                Kind = "temp-large",
                Section = "temp",
                Title = $"{TempCleanerService.FormatBytes(temp.TotalReclaimableBytes)} di file temporanei",
                Description = "Puoi liberare spazio dalla sezione Interventi: i file in uso vengono saltati automaticamente."
            });
        }

        if (findings.Count == 0)
        {
            findings.Add(new Finding
            {
                Severity = FindingSeverity.Info,
                Kind = "all-good",
                Section = "overview",
                Title = "Nessun problema rilevante",
                Description = "Protezione attiva, nessuna minaccia attiva e risorse nella norma. Ricorda: nessun software può garantire la totale assenza di malware."
            });
        }

        return findings.OrderByDescending(f => f.Severity).ToList();
    }

    public static string RenderHtml(SystemReport report)
    {
        var sb = new StringBuilder();
        // Codifica solo i caratteri speciali HTML: le lettere accentate restano leggibili nel file.
        static string H(string? s) => s is null ? "" : s.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;").Replace("\"", "&quot;").Replace("'", "&#39;");
        string Bytes(long b) => TempCleanerService.FormatBytes(b);
        string Date(DateTimeOffset? d) => d.HasValue ? d.Value.ToString("dd/MM/yyyy HH:mm") : "n.d.";
        string Severity(FindingSeverity s) => s switch
        {
            FindingSeverity.Critical => "Grave",
            FindingSeverity.High => "Alta",
            FindingSeverity.Medium => "Media",
            FindingSeverity.Low => "Bassa",
            _ => "Informativa"
        };

        sb.Append("<!DOCTYPE html><html lang=\"it\"><head><meta charset=\"utf-8\"><title>Rapporto PC Check</title>");
        sb.Append("<style>body{font-family:Segoe UI,Arial,sans-serif;margin:32px;color:#1b1f24;max-width:1000px}h1{color:#0b4f8a}h2{border-bottom:2px solid #e3e8ef;padding-bottom:4px;margin-top:32px}table{border-collapse:collapse;width:100%;margin:8px 0 16px}th,td{border:1px solid #d9dee6;padding:6px 8px;text-align:left;font-size:14px;vertical-align:top}th{background:#f2f5f9}.sev-Grave{color:#b00020;font-weight:700}.sev-Alta{color:#c2410c;font-weight:700}.sev-Media{color:#b45309}.sev-Bassa{color:#1d4ed8}.sev-Informativa{color:#374151}.note{background:#fff7e6;border:1px solid #f5c26b;padding:10px;border-radius:6px}.ok{color:#15803d}.ko{color:#b00020}</style></head><body>");
        sb.Append("<h1>Rapporto PC Check</h1>");
        sb.Append($"<p>Generato il {H(report.GeneratedAt.ToString("dd/MM/yyyy HH:mm"))} su <strong>{H(report.System.MachineName)}</strong> ({H(report.System.OsDescription)}), utente {H(report.System.UserName)}.");
        if (report.System.DemoMode)
        {
            sb.Append(" <strong>Modalità dimostrativa: dati fittizi.</strong>");
        }
        sb.Append("</p>");
        sb.Append($"<p class=\"note\">{H(report.Disclaimer)}</p>");

        sb.Append("<h2>Segnalazioni</h2><table><tr><th>Gravità</th><th>Titolo</th><th>Dettaglio</th></tr>");
        foreach (var f in report.Findings)
        {
            var sev = Severity(f.Severity);
            sb.Append($"<tr><td class=\"sev-{sev}\">{sev}</td><td>{H(f.Title)}</td><td>{H(f.Description)}</td></tr>");
        }
        sb.Append("</table>");

        var d = report.Defender;
        sb.Append("<h2>Microsoft Defender</h2><table>");
        sb.Append($"<tr><th>Disponibile</th><td>{(d.Available ? "<span class=\"ok\">Sì</span>" : "<span class=\"ko\">No</span> " + H(d.Error))}</td></tr>");
        sb.Append($"<tr><th>Antivirus attivo</th><td>{YesNo(d.AntivirusEnabled)}</td></tr>");
        sb.Append($"<tr><th>Protezione in tempo reale</th><td>{YesNo(d.RealTimeProtectionEnabled)}</td></tr>");
        sb.Append($"<tr><th>Monitoraggio comportamento</th><td>{YesNo(d.BehaviorMonitorEnabled)}</td></tr>");
        sb.Append($"<tr><th>Protezione manomissioni</th><td>{YesNo(d.TamperProtected)}</td></tr>");
        sb.Append($"<tr><th>Definizioni</th><td>{H(d.SignatureVersion)} del {Date(d.SignatureLastUpdated)} ({(d.SignatureAgeDays?.ToString() ?? "n.d.")} giorni)</td></tr>");
        sb.Append($"<tr><th>Motore / piattaforma</th><td>{H(d.EngineVersion)} / {H(d.PlatformVersion)}</td></tr>");
        sb.Append($"<tr><th>Ultima scansione rapida</th><td>{Date(d.LastQuickScanEnd)}</td></tr>");
        sb.Append($"<tr><th>Ultima scansione completa</th><td>{Date(d.LastFullScanEnd)}</td></tr>");
        sb.Append("</table>");

        sb.Append($"<h3>Minacce rilevate ({report.Threats.Count})</h3>");
        if (report.Threats.Count == 0)
        {
            sb.Append("<p>Nessuna minaccia nella cronologia di Defender.</p>");
        }
        else
        {
            sb.Append("<table><tr><th>Nome</th><th>Gravità</th><th>Categoria</th><th>Stato</th><th>Rilevata</th><th>Risorse</th></tr>");
            foreach (var t in report.Threats)
            {
                sb.Append($"<tr><td>{H(t.Name)}</td><td>{H(t.Severity)}</td><td>{H(t.Category)}</td><td>{H(t.Status)}{(t.IsActive ? " (attiva)" : "")}</td><td>{Date(t.DetectedAt)}</td><td>{H(string.Join("; ", t.Resources))}</td></tr>");
            }
            sb.Append("</table>");
        }

        var p = report.Performance;
        sb.Append("<h2>Prestazioni</h2><table>");
        sb.Append($"<tr><th>CPU</th><td>{p.CpuPercent:0.#}% su {p.CpuCores} core (campione di {p.SampleMilliseconds} ms)</td></tr>");
        sb.Append($"<tr><th>Memoria</th><td>{Bytes(p.MemoryUsedBytes)} di {Bytes(p.MemoryTotalBytes)} ({p.MemoryPercent:0.#}%)</td></tr>");
        sb.Append($"<tr><th>Acceso da</th><td>{TimeSpan.FromSeconds(p.UptimeSeconds):d\\g\\ hh\\:mm}</td></tr>");
        sb.Append($"<tr><th>Processi</th><td>{p.ProcessCount}</td></tr></table>");
        sb.Append("<table><tr><th>Disco</th><th>Tipo</th><th>Totale</th><th>Liberi</th><th>Uso</th></tr>");
        foreach (var disk in p.Disks)
        {
            sb.Append($"<tr><td>{H(disk.Name)} {H(disk.Label)}</td><td>{H(disk.DriveType)} ({H(disk.Format)})</td><td>{Bytes(disk.TotalBytes)}</td><td>{Bytes(disk.FreeBytes)}</td><td>{disk.UsedPercent:0.#}%</td></tr>");
        }
        sb.Append("</table><table><tr><th>Processo</th><th>PID</th><th>CPU</th><th>Memoria</th><th>Percorso</th><th>Note</th></tr>");
        foreach (var proc in p.TopProcesses)
        {
            var notes = new List<string>();
            if (proc.HighImpact) notes.Add("alto impatto");
            if (proc.Unknown) notes.Add("cartella insolita");
            if (proc.Protected) notes.Add("sistema");
            sb.Append($"<tr><td>{H(proc.Name)}</td><td>{proc.Pid}</td><td>{proc.CpuPercent:0.#}%</td><td>{Bytes(proc.MemoryBytes)}</td><td>{H(proc.Path)}</td><td>{H(string.Join(", ", notes))}</td></tr>");
        }
        sb.Append("</table>");

        sb.Append($"<h2>Programmi all'avvio ({report.Startup.Count})</h2><table><tr><th>Nome</th><th>Stato</th><th>Ambito</th><th>Origine</th><th>Comando</th></tr>");
        foreach (var s in report.Startup)
        {
            sb.Append($"<tr><td>{H(s.Name)}</td><td>{(s.Enabled ? "Attivo" : "Disattivato")}</td><td>{H(s.Scope)}</td><td>{H(s.Location)}</td><td>{H(s.Command)}</td></tr>");
        }
        sb.Append("</table>");

        sb.Append($"<h2>Programmi installati ({report.Apps.Count})</h2><table><tr><th>Nome</th><th>Editore</th><th>Versione</th><th>Installato</th><th>Dimensione</th><th>Da verificare</th></tr>");
        foreach (var a in report.Apps)
        {
            sb.Append($"<tr><td>{H(a.Name)}</td><td>{H(a.Publisher ?? "(non dichiarato)")}</td><td>{H(a.Version)}</td><td>{(a.InstallDate?.ToString("dd/MM/yyyy") ?? "")}</td><td>{(a.EstimatedSizeBytes.HasValue ? Bytes(a.EstimatedSizeBytes.Value) : "")}</td><td>{H(string.Join("; ", a.Hints))}</td></tr>");
        }
        sb.Append("</table>");

        sb.Append("<h2>File temporanei</h2><table><tr><th>Cartella</th><th>File</th><th>Spazio</th><th>Più vecchi di 7 giorni</th><th>Note</th></tr>");
        foreach (var t in report.Temp.Targets)
        {
            sb.Append($"<tr><td>{H(t.Path)}</td><td>{t.FileCount:N0}</td><td>{Bytes(t.TotalBytes)}</td><td>{Bytes(t.OlderThan7DaysBytes)}</td><td>{H(t.Error)}{(t.RequiresAdmin ? " Richiede permessi di amministratore." : "")}</td></tr>");
        }
        sb.Append("</table>");
        sb.Append("<p><small>Rapporto generato localmente da PC Check. Nessun dato è stato inviato all'esterno.</small></p>");
        sb.Append("</body></html>");
        return sb.ToString();

        static string YesNo(bool v) => v ? "<span class=\"ok\">Sì</span>" : "<span class=\"ko\">No</span>";
    }
}
