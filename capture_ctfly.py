"""Render this process's Tk client window for visual QA, without desktop capture."""
import ctypes
from ctypes import wintypes
import struct
import zlib


def capture(app,path):
    app.update_idletasks()
    width,height=app.winfo_width(),app.winfo_height()
    user=ctypes.windll.user32
    gdi=ctypes.windll.gdi32
    user.GetDC.argtypes=[wintypes.HWND]
    user.GetDC.restype=wintypes.HDC
    user.ReleaseDC.argtypes=[wintypes.HWND,wintypes.HDC]
    user.PrintWindow.argtypes=[wintypes.HWND,wintypes.HDC,wintypes.UINT]
    gdi.CreateCompatibleDC.argtypes=[wintypes.HDC]
    gdi.CreateCompatibleDC.restype=wintypes.HDC
    gdi.CreateCompatibleBitmap.argtypes=[wintypes.HDC,ctypes.c_int,ctypes.c_int]
    gdi.CreateCompatibleBitmap.restype=wintypes.HBITMAP
    gdi.SelectObject.argtypes=[wintypes.HDC,wintypes.HANDLE]
    gdi.SelectObject.restype=wintypes.HANDLE
    gdi.DeleteObject.argtypes=[wintypes.HANDLE]
    gdi.DeleteDC.argtypes=[wintypes.HDC]
    gdi.GetDIBits.argtypes=[wintypes.HDC,wintypes.HBITMAP,wintypes.UINT,wintypes.UINT,ctypes.c_void_p,ctypes.c_void_p,wintypes.UINT]
    hwnd=app.winfo_id()
    dc=user.GetDC(hwnd)
    memory=gdi.CreateCompatibleDC(dc)
    bitmap=gdi.CreateCompatibleBitmap(dc,width,height)
    previous=gdi.SelectObject(memory,bitmap)
    try:
        # Run on Tk's owning thread: WM_PRINT is synchronous and must not wait
        # for a subprocess while Tk is waiting for that subprocess to finish.
        if not user.PrintWindow(hwnd,memory,3):
            raise RuntimeError('Could not render the CTFLY client window.')
        gdi.SelectObject(memory,previous)
        info=ctypes.create_string_buffer(struct.pack('<IiiHHIIiiII',40,width,-height,1,32,0,width*height*4,0,0,0,0))
        bits=ctypes.create_string_buffer(width*height*4)
        if not gdi.GetDIBits(memory,bitmap,0,height,bits,info,0):
            raise RuntimeError('Could not read CTFLY window pixels.')
        raw=bits.raw
        rgb=bytearray(width*height*3)
        rgb[0::3],rgb[1::3],rgb[2::3]=raw[2::4],raw[1::4],raw[0::4]
        scanlines=b''.join(b'\0'+rgb[y*width*3:(y+1)*width*3] for y in range(height))
        def chunk(kind,data):
            return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
        path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',width,height,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(scanlines))+chunk(b'IEND',b''))
    finally:
        gdi.SelectObject(memory,previous)
        gdi.DeleteObject(bitmap)
        gdi.DeleteDC(memory)
        user.ReleaseDC(hwnd,dc)
