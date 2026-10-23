using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;

namespace PCCheck.Core.Security;

/// <summary>
/// Barriera unica per ogni richiesta:
/// 1) l'indirizzo remoto deve essere di loopback;
/// 2) l'header Host deve essere 127.0.0.1 o localhost (anti DNS rebinding);
/// 3) le chiamate /api/* devono presentare il token di sessione nell'header X-PC-Check-Token;
/// 4) un eventuale header Origin deve corrispondere all'origine locale (anti CSRF);
/// 5) a ogni risposta vengono aggiunti CSP e altri header di protezione.
/// </summary>
public sealed class ApiSecurityMiddleware
{
    public const string ContentSecurityPolicy =
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; " +
        "img-src 'self' data:; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    private readonly RequestDelegate _next;
    private readonly SessionToken _token;
    private readonly ILogger<ApiSecurityMiddleware> _logger;

    public ApiSecurityMiddleware(RequestDelegate next, SessionToken token, ILogger<ApiSecurityMiddleware> logger)
    {
        _next = next;
        _token = token;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        var response = context.Response;
        response.Headers["Content-Security-Policy"] = ContentSecurityPolicy;
        response.Headers["X-Content-Type-Options"] = "nosniff";
        response.Headers["X-Frame-Options"] = "DENY";
        response.Headers["Referrer-Policy"] = "no-referrer";
        response.Headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()";
        response.Headers["Cross-Origin-Opener-Policy"] = "same-origin";
        response.Headers["Cross-Origin-Resource-Policy"] = "same-origin";

        if (!LoopbackHostValidator.IsLoopbackAddress(context.Connection.RemoteIpAddress))
        {
            _logger.LogWarning("Richiesta rifiutata da indirizzo non locale {Address}", context.Connection.RemoteIpAddress);
            await Reject(context, StatusCodes.Status403Forbidden, "Sono ammesse solo connessioni locali.");
            return;
        }

        var hostHeader = context.Request.Headers.Host.ToString();
        if (!LoopbackHostValidator.IsLoopbackHost(hostHeader))
        {
            _logger.LogWarning("Richiesta rifiutata per header Host non consentito: {Host}", hostHeader);
            await Reject(context, StatusCodes.Status400BadRequest, "Header Host non consentito.");
            return;
        }

        var origin = context.Request.Headers.Origin.ToString();
        if (!string.IsNullOrEmpty(origin) && !IsLoopbackOrigin(origin))
        {
            _logger.LogWarning("Richiesta rifiutata per Origin non locale: {Origin}", origin);
            await Reject(context, StatusCodes.Status403Forbidden, "Origine non consentita.");
            return;
        }

        if (context.Request.Path.StartsWithSegments("/api"))
        {
            response.Headers["Cache-Control"] = "no-store";
            var provided = context.Request.Headers[SessionToken.HeaderName].ToString();
            if (!_token.Matches(provided))
            {
                await Reject(context, StatusCodes.Status401Unauthorized, "Token di sessione mancante o non valido. Riapri PC Check dal collegamento iniziale.");
                return;
            }
        }

        await _next(context);
    }

    public static bool IsLoopbackOrigin(string origin)
    {
        if (string.Equals(origin, "null", StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }
        if (!Uri.TryCreate(origin, UriKind.Absolute, out var uri))
        {
            return false;
        }
        if (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps)
        {
            return false;
        }
        var host = uri.HostNameType == UriHostNameType.IPv6 ? $"[{uri.DnsSafeHost}]" : uri.Host;
        return LoopbackHostValidator.IsLoopbackHost(uri.IsDefaultPort ? host : $"{host}:{uri.Port}");
    }

    private static async Task Reject(HttpContext context, int statusCode, string message)
    {
        context.Response.StatusCode = statusCode;
        context.Response.ContentType = "application/json; charset=utf-8";
        await context.Response.WriteAsJsonAsync(new { error = message });
    }
}
