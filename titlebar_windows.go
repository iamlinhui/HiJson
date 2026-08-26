//go:build windows

package main

import (
	"syscall"
	"unsafe"
)

// windowTitle must match the window Title set in main.go's wails.Run options.
const windowTitle = "HiJson"

var (
	dwmapi                    = syscall.NewLazyDLL("dwmapi.dll")
	procDwmSetWindowAttribute = dwmapi.NewProc("DwmSetWindowAttribute")
	user32                    = syscall.NewLazyDLL("user32.dll")
	procFindWindowW           = user32.NewProc("FindWindowW")
)

// findMainWindow returns the native handle of the app's top-level window by
// looking it up via its title.
func findMainWindow() uintptr {
	title, _ := syscall.UTF16PtrFromString(windowTitle)
	hwnd, _, _ := procFindWindowW.Call(0, uintptr(unsafe.Pointer(title)))
	return hwnd
}

// setWindowDarkMode toggles the immersive dark title bar on the given window
// and overrides the title bar / border colour so it matches the app background.
//
//   - DWMWA_USE_IMMERSIVE_DARK_MODE (attribute 20 on Win10 2004+/Win11, 19 on
//     older Win10) flips the caption between dark and light and the button
//     glyphs.
//   - DWMWA_CAPTION_COLOR (35) and DWMWA_BORDER_COLOR (34) set the exact title
//     bar / border colour on Windows 11, eliminating the mismatch between the
//     default dark title bar (~#202020) and the app's toolbar strip. Win10
//     ignores these (harmless no-op).
//
// Colours are COLORREF values (0x00BBGGRR); the values below are symmetric so
// byte order doesn't matter. They mirror the toolbar colours in
// frontend/dist/index.html (--bg-secondary: #2d2d2d dark / #f3f3f3 light,
// --border: #d4d4d4 light).
func setWindowDarkMode(hwnd uintptr, dark bool) {
	if hwnd == 0 {
		return
	}
	var immersive int32
	if dark {
		immersive = 1
	}
	for _, attr := range []uint32{20, 19} {
		procDwmSetWindowAttribute.Call(
			hwnd,
			uintptr(attr),
			uintptr(unsafe.Pointer(&immersive)),
			unsafe.Sizeof(immersive),
		)
	}
	var caption, border int32
	if dark {
		caption = 0x2D2D2D
		border = 0x2D2D2D
	} else {
		caption = 0xF3F3F3
		border = 0xD4D4D4
	}
	procDwmSetWindowAttribute.Call(hwnd, 35, uintptr(unsafe.Pointer(&caption)), unsafe.Sizeof(caption))
	procDwmSetWindowAttribute.Call(hwnd, 34, uintptr(unsafe.Pointer(&border)), unsafe.Sizeof(border))
}
