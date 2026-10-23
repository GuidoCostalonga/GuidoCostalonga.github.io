using System.Net;
using System.Net.Sockets;

namespace PCCheck.Core.Infrastructure;

/// <summary>Trova una porta TCP libera sull'interfaccia di loopback; in caso di errore usa la porta di riserva.</summary>
public static class PortFinder
{
    public static int FindFreePort(int fallbackPort = 5000)
    {
        try
        {
            var listener = new TcpListener(IPAddress.Loopback, 0);
            listener.Start();
            try
            {
                return ((IPEndPoint)listener.LocalEndpoint).Port;
            }
            finally
            {
                listener.Stop();
            }
        }
        catch (SocketException)
        {
            return fallbackPort;
        }
    }

    public static bool IsPortFree(int port)
    {
        try
        {
            var listener = new TcpListener(IPAddress.Loopback, port);
            listener.Start();
            listener.Stop();
            return true;
        }
        catch (SocketException)
        {
            return false;
        }
    }
}
