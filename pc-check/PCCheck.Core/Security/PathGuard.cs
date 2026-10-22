namespace PCCheck.Core.Security;

/// <summary>Controlli sui percorsi: ogni operazione su file deve restare dentro cartelle note e consentite.</summary>
public static class PathGuard
{
    private static readonly StringComparison Comparison =
        OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal;

    /// <summary>True se <paramref name="candidate"/> e' uguale a <paramref name="root"/> o si trova al suo interno.</summary>
    public static bool IsInside(string root, string candidate)
    {
        if (string.IsNullOrWhiteSpace(root) || string.IsNullOrWhiteSpace(candidate))
        {
            return false;
        }
        string fullRoot;
        string fullCandidate;
        try
        {
            fullRoot = Normalize(root);
            fullCandidate = Normalize(candidate);
        }
        catch (Exception ex) when (ex is ArgumentException or NotSupportedException or PathTooLongException)
        {
            return false;
        }

        if (string.Equals(fullRoot, fullCandidate, Comparison))
        {
            return true;
        }
        var rootWithSeparator = fullRoot + Path.DirectorySeparatorChar;
        return fullCandidate.StartsWith(rootWithSeparator, Comparison);
    }

    /// <summary>True se il percorso e' strettamente dentro la radice (non la radice stessa).</summary>
    public static bool IsStrictlyInside(string root, string candidate)
    {
        if (!IsInside(root, candidate))
        {
            return false;
        }
        return !string.Equals(Normalize(root), Normalize(candidate), Comparison);
    }

    public static string Normalize(string path)
    {
        var full = Path.GetFullPath(path);
        full = full.Replace(Path.AltDirectorySeparatorChar, Path.DirectorySeparatorChar);
        return Path.TrimEndingDirectorySeparator(full);
    }

    /// <summary>Cartella dati dell'applicazione (es. %LOCALAPPDATA%\PC Check) creata se assente.</summary>
    public static string GetAppDataDirectory()
    {
        var baseDir = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        if (string.IsNullOrEmpty(baseDir))
        {
            baseDir = Path.GetTempPath();
        }
        var dir = Path.Combine(baseDir, "PC Check");
        Directory.CreateDirectory(dir);
        return dir;
    }
}
