using System.Security.Cryptography;
using System.Text;

namespace PCCheck.Core.Security;

/// <summary>
/// Token di sessione generato all'avvio. Viene passato una sola volta al browser nell'URL iniziale
/// e poi richiesto in ogni chiamata API tramite l'header <c>X-PC-Check-Token</c>.
/// </summary>
public sealed class SessionToken
{
    public const string HeaderName = "X-PC-Check-Token";
    public const string QueryParameterName = "token";
    public const string EnvironmentVariableName = "PC_CHECK_SESSION_TOKEN";
    public const int MinimumLength = 32;
    private const int RandomBytes = 32;

    private readonly byte[] _valueBytes;

    public SessionToken(string value)
    {
        if (string.IsNullOrWhiteSpace(value) || value.Length < MinimumLength)
        {
            throw new ArgumentException($"Il token deve avere almeno {MinimumLength} caratteri.", nameof(value));
        }
        if (!IsSafeCharset(value))
        {
            throw new ArgumentException("Il token contiene caratteri non ammessi.", nameof(value));
        }
        Value = value;
        _valueBytes = Encoding.UTF8.GetBytes(value);
    }

    public string Value { get; }

    /// <summary>Genera un token casuale di 256 bit in formato base64url (43 caratteri).</summary>
    public static SessionToken Generate()
    {
        var bytes = RandomNumberGenerator.GetBytes(RandomBytes);
        return new SessionToken(ToBase64Url(bytes));
    }

    /// <summary>
    /// Usa il token presente nella variabile d'ambiente PC_CHECK_SESSION_TOKEN se e' abbastanza robusto,
    /// altrimenti ne genera uno nuovo. Il valore scelto viene riportato nella variabile d'ambiente del processo.
    /// </summary>
    public static SessionToken FromEnvironmentOrGenerate()
    {
        var env = Environment.GetEnvironmentVariable(EnvironmentVariableName);
        SessionToken token;
        if (!string.IsNullOrWhiteSpace(env) && env.Length >= MinimumLength && IsSafeCharset(env))
        {
            token = new SessionToken(env);
        }
        else
        {
            token = Generate();
        }
        Environment.SetEnvironmentVariable(EnvironmentVariableName, token.Value);
        return token;
    }

    /// <summary>Confronto a tempo costante: non rivela a che punto le stringhe differiscono.</summary>
    public bool Matches(string? candidate)
    {
        if (string.IsNullOrEmpty(candidate))
        {
            return false;
        }
        var candidateBytes = Encoding.UTF8.GetBytes(candidate);
        if (candidateBytes.Length != _valueBytes.Length)
        {
            // Consuma comunque un confronto per non distinguere troppo i casi di lunghezza diversa.
            CryptographicOperations.FixedTimeEquals(_valueBytes, _valueBytes);
            return false;
        }
        return CryptographicOperations.FixedTimeEquals(_valueBytes, candidateBytes);
    }

    public static bool IsSafeCharset(string value)
    {
        foreach (var c in value)
        {
            var ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok)
            {
                return false;
            }
        }
        return true;
    }

    public static string ToBase64Url(byte[] bytes) =>
        Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');

    public override string ToString() => "[token di sessione nascosto]";
}
