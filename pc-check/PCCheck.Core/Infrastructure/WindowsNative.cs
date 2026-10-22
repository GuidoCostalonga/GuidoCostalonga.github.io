using System.Runtime.InteropServices;
using System.Runtime.Versioning;

namespace PCCheck.Core.Infrastructure;

/// <summary>Chiamate native minime a kernel32 per memoria e tempi di CPU del sistema.</summary>
[SupportedOSPlatform("windows")]
internal static class WindowsNative
{
    [StructLayout(LayoutKind.Sequential)]
    internal struct MemoryStatusEx
    {
        public uint Length;
        public uint MemoryLoad;
        public ulong TotalPhys;
        public ulong AvailPhys;
        public ulong TotalPageFile;
        public ulong AvailPageFile;
        public ulong TotalVirtual;
        public ulong AvailVirtual;
        public ulong AvailExtendedVirtual;
    }

    [StructLayout(LayoutKind.Sequential)]
    internal struct FileTime
    {
        public uint Low;
        public uint High;
        public readonly ulong Value => ((ulong)High << 32) | Low;
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GlobalMemoryStatusEx(ref MemoryStatusEx buffer);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetSystemTimes(out FileTime idle, out FileTime kernel, out FileTime user);

    internal static bool TryGetMemory(out long totalBytes, out long availableBytes)
    {
        totalBytes = 0;
        availableBytes = 0;
        var status = new MemoryStatusEx { Length = (uint)Marshal.SizeOf<MemoryStatusEx>() };
        if (!GlobalMemoryStatusEx(ref status))
        {
            return false;
        }
        totalBytes = (long)status.TotalPhys;
        availableBytes = (long)status.AvailPhys;
        return true;
    }

    /// <summary>Tempi cumulativi di sistema (in tick da 100 ns): idle e totale (kernel + user; kernel include idle).</summary>
    internal static bool TryGetSystemTimes(out ulong idle, out ulong total)
    {
        idle = 0;
        total = 0;
        if (!GetSystemTimes(out var i, out var k, out var u))
        {
            return false;
        }
        idle = i.Value;
        total = k.Value + u.Value;
        return true;
    }
}
