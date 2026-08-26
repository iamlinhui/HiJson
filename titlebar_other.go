//go:build !windows

package main

// findMainWindow is a no-op on non-Windows platforms.
func findMainWindow() uintptr { return 0 }

// setWindowDarkMode is a no-op on non-Windows platforms.
func setWindowDarkMode(hwnd uintptr, dark bool) {}
