# HiJson

English | [简体中文](./README.md)

A lightweight JSON tool desktop app built with [Wails](https://wails.io/). It supports JSON formatting, compression, filtering, deep parsing, and smart unwrapping of nested stringified JSON — handy for inspecting API responses and debugging multi-layer escaped data.

## Features

### JSON Processing
- **Format** — indent while preserving key order
- **Sort & Format** — sort keys alphabetically, then format
- **Compress** — strip whitespace into compact JSON
- **Filter empties** — remove `null`, empty strings, empty objects/arrays (key order preserved)
- **Deep Parse** — auto-expand JSON nested inside string values
- **Smart Unwrap** — tolerantly unwrap stringified JSON nested inside strings, even when inner quotes are under-escaped (recovered via bracket balancing — common in real API data)

### Text Cleanup (Edit menu)
- **Unescape** — peel quote layers when the whole text is a multi-layer escaped string
- **Remove `\n` / Remove `\`** — strip newlines / backslashes
- **Clear / Paste** — one-click clear, paste & format

### Browse & Search
- **Tree view** — collapsible JSON tree with syntax-colored keys/values
- **Node table** — key/value table for the selected node
- **Find text / Find node** — two search modes with previous/next navigation
- **Context menu** — copy key, value, key:value, path, node content (plain or formatted)

### Other
- **Tabs** — open multiple JSON documents side by side
- **Drag & drop** — drop JSON files onto the app icon or window
- **File I/O** — open and save JSON files via native dialogs
- **i18n** — built-in Simplified Chinese / English, switchable from the toolbar; preference remembered
- **Themes** — dark / light theme toggle; preference remembered
- **Shortcuts** — common actions bound to keyboard shortcuts (see below)

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+N` | New tab |
| `Ctrl+F` | Format |
| `Ctrl+G` | Sort & format |
| `Ctrl+H` | Compress |
| `Ctrl+B` | Filter empties |
| `Ctrl+E` | Deep parse |
| `Ctrl+D` | Clear |
| `Ctrl+W` | Close current tab |
| `Ctrl+O` | Open file |
| `Ctrl+S` | Save file |
| `Esc` | Close search bar / menu |

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Go 1.24 |
| Desktop framework | Wails v2.12 |
| Frontend | HTML / JS (embedded, no build step) |

## Getting Started

### Prerequisites

- [Go](https://go.dev/) ≥ 1.24
- [Wails CLI](https://wails.io/docs/gettingstarted/installation) v2

### Development

```bash
wails dev
```

### Build

```bash
wails build
```

The build output is in `build/bin/`.

## Project Structure

```
├── main.go                  # app entry
├── app.go                   # backend JSON processing (incl. tolerant parser)
├── app_test.go              # backend unit tests
├── wails.json               # Wails project config
├── frontend/
│   ├── dist/
│   │   └── index.html       # embedded frontend (single file: all UI + i18n)
│   └── wailsjs/             # Wails auto-generated frontend bindings
└── build/
    ├── bin/                 # executable output
    └── windows/            # Windows platform resources (icon, manifest, etc.)
```

## License

Copyright © 2026 Lynn
