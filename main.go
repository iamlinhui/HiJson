package main

import (
	"embed"

	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"github.com/wailsapp/wails/v2/pkg/options/assetserver"
	"github.com/wailsapp/wails/v2/pkg/options/mac"
)

//go:embed all:frontend/dist
var assets embed.FS

// Version is the application version. It is overridden at build time via:
//
//	wails build -ldflags "-X main.Version=1.0.1"
//
// Keep it in sync with wails.json "productVersion" for dev builds.
var Version = "1.0.0"

func main() {
	app := NewApp()

	err := wails.Run(&options.App{
		Title:  "HiJson",
		Width:  1200,
		Height: 750,
		AssetServer: &assetserver.Options{
			Assets: assets,
		},
		OnStartup:   app.startup,
		DragAndDrop: &options.DragAndDrop{EnableFileDrop: true},
		Mac: &mac.Options{
			TitleBar: &mac.TitleBar{
				FullSizeContent: true,
			},
			Appearance: mac.DefaultAppearance,
		},
		Bind: []interface{}{
			app,
		},
	})

	if err != nil {
		println("Error:", err.Error())
	}
}
