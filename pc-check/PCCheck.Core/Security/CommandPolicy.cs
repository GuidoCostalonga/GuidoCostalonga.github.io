using System.Text.RegularExpressions;
using PCCheck.Core.Models;

namespace PCCheck.Core.Security;

/// <summary>
/// Regole che decidono quali comandi esterni possono essere avviati e con quali argomenti.
/// Nessuna stringa proveniente dall'interfaccia o dal registro viene mai passata a una shell.
/// </summary>
public static class CommandPolicy
{
    private static readonly Regex ProductCodeRegex = new(
        @"^\{[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\}$",
        RegexOptions.Compiled | RegexOptions.CultureInvariant);

    /// <summary>Argomenti ammessi per MpCmdRun.exe. Qualsiasi altro tipo di scansione viene rifiutato.</summary>
    public static IReadOnlyList<string> BuildDefenderScanArguments(ScanType type)
    {
        return type switch
        {
            ScanType.Quick => new[] { "-Scan", "-ScanType", "1" },
            ScanType.Full => new[] { "-Scan", "-ScanType", "2" },
            _ => throw new ArgumentOutOfRangeException(nameof(type), "Tipo di scansione non ammesso.")
        };
    }

    public static bool TryParseScanType(string? text, out ScanType type)
    {
        switch (text?.Trim().ToLowerInvariant())
        {
            case "quick":
            case "rapida":
            case "1":
                type = ScanType.Quick;
                return true;
            case "full":
            case "completa":
            case "2":
                type = ScanType.Full;
                return true;
            default:
                type = ScanType.Quick;
                return false;
        }
    }

    /// <summary>Un ProductCode MSI valido ha la forma {GUID}: e' l'unico argomento ammesso per msiexec /x.</summary>
    public static bool IsValidMsiProductCode(string? value) =>
        !string.IsNullOrEmpty(value) && ProductCodeRegex.IsMatch(value);

    /// <summary>
    /// Nome di pacchetto accettabile per <c>winget uninstall --name</c>: niente caratteri di controllo,
    /// niente virgolette o simboli usati dalle shell, lunghezza ragionevole.
    /// </summary>
    public static bool IsSafePackageName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name) || name.Length > 200)
        {
            return false;
        }
        foreach (var c in name)
        {
            if (char.IsControl(c))
            {
                return false;
            }
            if (c is '"' or '\'' or '&' or '|' or '<' or '>' or '^' or '%' or '`' or '$' or ';' or '\n' or '\r')
            {
                return false;
            }
        }
        return true;
    }

    /// <summary>Nome di valore di registro ammesso per reg.exe (voci di avvio).</summary>
    public static bool IsSafeRegistryValueName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name) || name.Length > 255)
        {
            return false;
        }
        foreach (var c in name)
        {
            if (char.IsControl(c) || c == '"' || c == '\\' || c == '/')
            {
                return false;
            }
        }
        return true;
    }

    /// <summary>Un PID valido per taskkill: intero positivo, mai i processi di base del sistema (0 = Idle, 4 = System).</summary>
    public static bool IsKillablePid(int pid) => pid > 4 && pid < int.MaxValue;

    /// <summary>Converte un array di byte nel formato esadecimale richiesto da reg.exe per REG_BINARY.</summary>
    public static string ToRegBinaryHex(byte[] data) => Convert.ToHexString(data);
}
