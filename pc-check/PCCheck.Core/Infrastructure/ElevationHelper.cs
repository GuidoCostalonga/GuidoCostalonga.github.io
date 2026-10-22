using System.ComponentModel;
using System.Diagnostics;
using System.Security.Principal;

namespace PCCheck.Core.Infrastructure;

/// <summary>
/// Avvia processi elevati (UAC, verb "runas") mirati a un singolo comando.
/// L'app principale resta sempre con i permessi normali dell'utente.
/// </summary>
public static class ElevationHelper
{
    private const int ErrorCancelled = 1223;

    public static bool IsAdministrator()
    {
        if (!OperatingSystem.IsWindows())
        {
            return false;
        }
        try
        {
            using var identity = WindowsIdentity.GetCurrent();
            var principal = new WindowsPrincipal(identity);
            return principal.IsInRole(WindowsBuiltInRole.Administrator);
        }
        catch
        {
            return false;
        }
    }

    public sealed class ElevatedResult
    {
        public bool Started { get; init; }
        public bool UserCancelled { get; init; }
        public int? ExitCode { get; init; }
        public string? Error { get; init; }
    }

    /// <summary>
    /// Esegue <paramref name="fileName"/> con gli argomenti indicati chiedendo l'elevazione.
    /// Gli argomenti sono passati come lista: nessuna shell interpreta la riga di comando.
    /// </summary>
    public static async Task<ElevatedResult> RunElevatedAsync(string fileName, IReadOnlyList<string> arguments, TimeSpan timeout, CancellationToken cancellationToken = default)
    {
        if (!OperatingSystem.IsWindows())
        {
            return new ElevatedResult { Error = "L'elevazione UAC e' disponibile solo su Windows." };
        }
        if (!File.Exists(fileName))
        {
            return new ElevatedResult { Error = $"Eseguibile non trovato: {fileName}" };
        }

        var psi = new ProcessStartInfo
        {
            FileName = fileName,
            UseShellExecute = true,
            Verb = "runas",
            WindowStyle = ProcessWindowStyle.Hidden,
            CreateNoWindow = true
        };
        foreach (var arg in arguments)
        {
            psi.ArgumentList.Add(arg);
        }

        try
        {
            using var process = Process.Start(psi);
            if (process is null)
            {
                return new ElevatedResult { Error = "Impossibile avviare il processo elevato." };
            }
            using var cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            cts.CancelAfter(timeout);
            try
            {
                await process.WaitForExitAsync(cts.Token);
            }
            catch (OperationCanceledException)
            {
                return new ElevatedResult { Started = true, Error = "Il processo elevato non ha terminato entro il tempo previsto." };
            }
            return new ElevatedResult { Started = true, ExitCode = process.ExitCode };
        }
        catch (Win32Exception ex) when (ex.NativeErrorCode == ErrorCancelled)
        {
            return new ElevatedResult { UserCancelled = true, Error = "Richiesta di permessi amministrativi annullata dall'utente." };
        }
        catch (Exception ex)
        {
            return new ElevatedResult { Error = ex.Message };
        }
    }

    /// <summary>Percorso dell'eseguibile corrente, usato per avviare una copia elevata di PC Check per un singolo compito.</summary>
    public static string? GetSelfExecutablePath()
    {
        var path = Environment.ProcessPath;
        if (string.IsNullOrEmpty(path) || !File.Exists(path))
        {
            return null;
        }
        return path;
    }
}
