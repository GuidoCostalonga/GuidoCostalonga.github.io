using System.Diagnostics;

namespace PCCheck.Core.Infrastructure;

/// <summary>Apre il browser predefinito sull'URL locale. Solo URL http://127.0.0.1 o http://localhost vengono accettati.</summary>
public static class BrowserLauncher
{
    public static bool TryOpen(string url, out string? error)
    {
        error = null;
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttp ||
            !Security.LoopbackHostValidator.IsLoopbackHost(uri.IsDefaultPort ? uri.Host : $"{uri.Host}:{uri.Port}"))
        {
            error = "URL non locale: apertura rifiutata.";
            return false;
        }

        try
        {
            if (OperatingSystem.IsWindows())
            {
                Process.Start(new ProcessStartInfo { FileName = url, UseShellExecute = true });
            }
            else if (OperatingSystem.IsMacOS())
            {
                Process.Start(new ProcessStartInfo("open") { ArgumentList = { url }, UseShellExecute = false });
            }
            else
            {
                Process.Start(new ProcessStartInfo("xdg-open") { ArgumentList = { url }, UseShellExecute = false });
            }
            return true;
        }
        catch (Exception ex)
        {
            error = ex.Message;
            return false;
        }
    }
}
