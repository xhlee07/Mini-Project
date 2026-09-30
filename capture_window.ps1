# Legacy standalone helper. smoke_ui.py now uses capture_ctfly.py on Tk's own
# thread, avoiding cross-process paint waits. The target must pump its event loop.
param([long]$WindowHandle, [int]$Width, [int]$Height, [string]$OutputFile)
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class CtFlyCapture {
    [DllImport("user32.dll", SetLastError=true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool PrintWindow(IntPtr window, IntPtr dc, uint flags);
}
'@
$bitmap = New-Object System.Drawing.Bitmap($Width, $Height)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
try {
    $deviceContext = $graphics.GetHdc()
    try {
        $captured = [CtFlyCapture]::PrintWindow([IntPtr]$WindowHandle, $deviceContext, 1)
        if (-not $captured) { throw 'Could not render the CTFLY client window.' }
    } finally {
        $graphics.ReleaseHdc($deviceContext)
    }
    $bitmap.Save($OutputFile, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $graphics.Dispose()
    $bitmap.Dispose()
}
