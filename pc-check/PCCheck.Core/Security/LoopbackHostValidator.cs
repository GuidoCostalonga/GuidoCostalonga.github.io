using System.Net;

namespace PCCheck.Core.Security;

/// <summary>
/// Verifica che l'header Host di una richiesta punti solo all'interfaccia di loopback.
/// Blocca attacchi di DNS rebinding (un sito esterno che risolve un proprio nome su 127.0.0.1).
/// </summary>
public static class LoopbackHostValidator
{
    private static readonly string[] AllowedNames = { "127.0.0.1", "localhost", "::1" };

    public static bool IsLoopbackHost(string? hostHeader, int? expectedPort = null)
    {
        if (string.IsNullOrWhiteSpace(hostHeader) || hostHeader.Length > 255)
        {
            return false;
        }

        if (!TrySplit(hostHeader.Trim(), out var host, out var port))
        {
            return false;
        }

        if (expectedPort.HasValue && port.HasValue && port.Value != expectedPort.Value)
        {
            return false;
        }

        host = host.ToLowerInvariant();
        foreach (var allowed in AllowedNames)
        {
            if (string.Equals(host, allowed, StringComparison.Ordinal))
            {
                return true;
            }
        }
        return false;
    }

    public static bool IsLoopbackAddress(IPAddress? address)
    {
        if (address is null)
        {
            // Connessioni in-process (test host) non hanno un indirizzo remoto.
            return true;
        }
        if (address.IsIPv4MappedToIPv6)
        {
            address = address.MapToIPv4();
        }
        return IPAddress.IsLoopback(address);
    }

    /// <summary>Separa host e porta gestendo IPv6 tra parentesi quadre.</summary>
    internal static bool TrySplit(string value, out string host, out int? port)
    {
        host = "";
        port = null;

        if (value.StartsWith('['))
        {
            var end = value.IndexOf(']');
            if (end < 0)
            {
                return false;
            }
            host = value.Substring(1, end - 1);
            var rest = value[(end + 1)..];
            if (rest.Length == 0)
            {
                return true;
            }
            if (!rest.StartsWith(':'))
            {
                return false;
            }
            return TryParsePort(rest[1..], out port);
        }

        var colon = value.LastIndexOf(':');
        if (colon < 0)
        {
            host = value;
            return host.Length > 0 && !host.Contains('/') && !host.Contains('@');
        }

        // Piu' di un ':' senza parentesi quadre: IPv6 grezzo non ammesso nell'header Host.
        if (value.IndexOf(':') != colon)
        {
            return false;
        }

        host = value[..colon];
        if (host.Length == 0 || host.Contains('/') || host.Contains('@'))
        {
            return false;
        }
        return TryParsePort(value[(colon + 1)..], out port);
    }

    private static bool TryParsePort(string text, out int? port)
    {
        port = null;
        if (text.Length == 0 || text.Length > 5)
        {
            return false;
        }
        foreach (var c in text)
        {
            if (c < '0' || c > '9')
            {
                return false;
            }
        }
        var value = int.Parse(text);
        if (value < 1 || value > 65535)
        {
            return false;
        }
        port = value;
        return true;
    }
}
