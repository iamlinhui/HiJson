package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/wailsapp/wails/v2/pkg/runtime"
)

const defaultLocale = "zh-CN"

// translations holds backend-facing strings keyed by locale.
var translations = map[string]map[string]string{
	"zh-CN": {
		"err.readFile":  "读取文件失败: %v",
		"err.empty":     "JSON文本为空",
		"err.invalid":   "非法JSON字符串",
		"err.noFile":    "未选择文件",
		"err.saveFile":  "保存文件失败: %v",
		"err.noStartup": "no startup file",
		"msg.saved":     "保存成功",
		"dlg.openTitle": "打开JSON文件",
		"dlg.saveTitle": "保存JSON文件",
		"filter.json":   "JSON Files",
		"filter.all":    "All Files",
	},
	"en": {
		"err.readFile":  "Failed to read file: %v",
		"err.empty":     "JSON text is empty",
		"err.invalid":   "Invalid JSON string",
		"err.noFile":    "No file selected",
		"err.saveFile":  "Failed to save file: %v",
		"err.noStartup": "no startup file",
		"msg.saved":     "Saved successfully",
		"dlg.openTitle": "Open JSON File",
		"dlg.saveTitle": "Save JSON File",
		"filter.json":   "JSON Files",
		"filter.all":    "All Files",
	},
}

// App struct
type App struct {
	ctx        context.Context
	locale     string
	windowHwnd uintptr // native window handle for OS title-bar theming
}

// NewApp creates a new App application struct
func NewApp() *App {
	return &App{locale: defaultLocale}
}

// startup is called when the app starts
func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
	// Match the native window title bar to the persisted theme right away, so it
	// doesn't flash the system-default colour before the frontend reconciles.
	a.windowHwnd = findMainWindow()
	setWindowDarkMode(a.windowHwnd, loadStoredTheme() == "dark")
}

// SetAppWindowDark toggles the native window title bar between dark and light
// to match the in-app theme. Called by the frontend whenever the theme changes
// (and once on load, to reconcile). It is a no-op on non-Windows platforms.
// The choice is persisted so the next launch can apply it before the frontend
// has loaded.
func (a *App) SetAppWindowDark(dark bool) {
	theme := "light"
	if dark {
		theme = "dark"
	}
	saveStoredTheme(theme)
	if a.windowHwnd == 0 {
		a.windowHwnd = findMainWindow()
	}
	setWindowDarkMode(a.windowHwnd, dark)
}

// storedThemePath returns where the last chosen theme is remembered, so the
// native title bar can follow it on the next launch before the frontend (and
// its localStorage value) has loaded.
func storedThemePath() (string, error) {
	dir, err := os.UserConfigDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "HiJson", "theme"), nil
}

// loadStoredTheme reads the persisted theme ("dark"/"light"), defaulting to
// dark to match the frontend's default.
func loadStoredTheme() string {
	if path, err := storedThemePath(); err == nil {
		if data, err := os.ReadFile(path); err == nil {
			if theme := strings.TrimSpace(string(data)); theme == "dark" || theme == "light" {
				return theme
			}
		}
	}
	return "dark"
}

// saveStoredTheme persists the theme choice for the next launch.
func saveStoredTheme(theme string) {
	if path, err := storedThemePath(); err == nil {
		if err := os.MkdirAll(filepath.Dir(path), 0755); err == nil {
			_ = os.WriteFile(path, []byte(theme), 0644)
		}
	}
}

// SetLocale switches the backend language used for error messages and dialogs.
func (a *App) SetLocale(locale string) {
	if _, ok := translations[locale]; ok {
		a.locale = locale
	}
}

// tr translates a message key according to the current locale.
func (a *App) tr(key string) string {
	if tbl, ok := translations[a.locale]; ok {
		if s, ok := tbl[key]; ok {
			return s
		}
	}
	if tbl, ok := translations[defaultLocale]; ok {
		if s, ok := tbl[key]; ok {
			return s
		}
	}
	return key
}

// GetStartupFile checks if a file was passed as a command-line argument (e.g. drag onto icon)
func (a *App) GetStartupFile() *JsonResult {
	args := os.Args
	if len(args) > 1 {
		filePath := args[1]
		if _, err := os.Stat(filePath); err == nil {
			return a.ReadFilePath(filePath)
		}
	}
	return &JsonResult{Success: false, Error: a.tr("err.noStartup"), Code: "err.noStartup"}
}

// ReadFilePath reads a file by its path and returns formatted content
func (a *App) ReadFilePath(filePath string) *JsonResult {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return &JsonResult{Success: false, Error: fmt.Sprintf(a.tr("err.readFile"), err), Code: "err.readFile"}
	}
	content := string(data)
	// Try to format
	if json.Valid([]byte(strings.TrimSpace(content))) {
		result := a.FormatJSON(content)
		if result.Success {
			return result
		}
	}
	return &JsonResult{Success: true, Text: content}
}

// FormatJSON formats JSON with indentation (preserves key order)
func (a *App) FormatJSON(input string) *JsonResult {
	input = strings.TrimSpace(input)
	if input == "" {
		return &JsonResult{Success: false, Error: a.tr("err.empty"), Code: "err.empty"}
	}
	// Validate JSON
	if !json.Valid([]byte(input)) {
		return &JsonResult{Success: false, Error: a.tr("err.invalid"), Code: "err.invalid"}
	}
	// Use json.Indent to preserve key order
	var buf bytes.Buffer
	if err := json.Indent(&buf, []byte(input), "", "  "); err != nil {
		return &JsonResult{Success: false, Error: err.Error()}
	}
	return &JsonResult{Success: true, Text: buf.String()}
}

// FormatSortedJSON formats JSON with sorted keys
func (a *App) FormatSortedJSON(input string) *JsonResult {
	return a.processJSON(input, true, false, false)
}

// CompressJSON compresses JSON (removes whitespace)
func (a *App) CompressJSON(input string) *JsonResult {
	input = strings.TrimSpace(input)
	if input == "" {
		return &JsonResult{Success: false, Error: a.tr("err.empty"), Code: "err.empty"}
	}
	if !json.Valid([]byte(input)) {
		return &JsonResult{Success: false, Error: a.tr("err.invalid"), Code: "err.invalid"}
	}
	var buf bytes.Buffer
	if err := json.Compact(&buf, []byte(input)); err != nil {
		return &JsonResult{Success: false, Error: err.Error()}
	}
	return &JsonResult{Success: true, Text: buf.String()}
}

// FilterJSON removes null/empty values from JSON while preserving key order
func (a *App) FilterJSON(input string) *JsonResult {
	input = strings.TrimSpace(input)
	if input == "" {
		return &JsonResult{Success: false, Error: a.tr("err.empty"), Code: "err.empty"}
	}
	if !json.Valid([]byte(input)) {
		return &JsonResult{Success: false, Error: a.tr("err.invalid"), Code: "err.invalid"}
	}

	filtered, err := filterOrdered(json.NewDecoder(strings.NewReader(input)))
	if err != nil {
		return &JsonResult{Success: false, Error: err.Error()}
	}
	if filtered == nil {
		return &JsonResult{Success: true, Text: "null"}
	}
	out, err := json.Marshal(filtered)
	if err != nil {
		return &JsonResult{Success: false, Error: err.Error()}
	}
	var buf bytes.Buffer
	if err := json.Indent(&buf, out, "", "  "); err != nil {
		return &JsonResult{Success: false, Error: err.Error()}
	}
	return &JsonResult{Success: true, Text: buf.String()}
}

// orderedMap preserves insertion order of keys
type orderedMap struct {
	keys   []string
	values map[string]interface{}
}

func (o *orderedMap) MarshalJSON() ([]byte, error) {
	var buf bytes.Buffer
	buf.WriteByte('{')
	first := true
	for _, k := range o.keys {
		v := o.values[k]
		if first {
			first = false
		} else {
			buf.WriteByte(',')
		}
		key, _ := marshalNoHTML(k)
		buf.Write(key)
		buf.WriteByte(':')
		val, err := marshalNoHTML(v)
		if err != nil {
			return nil, err
		}
		buf.Write(val)
	}
	buf.WriteByte('}')
	return buf.Bytes(), nil
}

// marshalNoHTML marshals v as JSON without escaping <, >, & (so URLs and
// HTML-ish strings stay readable).
func marshalNoHTML(v interface{}) ([]byte, error) {
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(v); err != nil {
		return nil, err
	}
	return bytes.TrimRight(buf.Bytes(), "\n"), nil
}

// filterOrdered parses JSON preserving key order and removes empty values
func filterOrdered(dec *json.Decoder) (interface{}, error) {
	t, err := dec.Token()
	if err != nil {
		return nil, err
	}
	switch v := t.(type) {
	case json.Delim:
		if v == '{' {
			om := &orderedMap{values: make(map[string]interface{})}
			for dec.More() {
				kt, err := dec.Token()
				if err != nil {
					return nil, err
				}
				key := kt.(string)
				val, err := filterOrdered(dec)
				if err != nil {
					return nil, err
				}
				if !isEmpty(val) {
					om.keys = append(om.keys, key)
					om.values[key] = val
				}
			}
			// consume closing }
			dec.Token()
			if len(om.keys) == 0 {
				return nil, nil
			}
			return om, nil
		} else if v == '[' {
			var arr []interface{}
			for dec.More() {
				val, err := filterOrdered(dec)
				if err != nil {
					return nil, err
				}
				if !isEmpty(val) {
					arr = append(arr, val)
				}
			}
			// consume closing ]
			dec.Token()
			if len(arr) == 0 {
				return nil, nil
			}
			return arr, nil
		}
	case nil:
		return nil, nil
	case string:
		if v == "" || strings.EqualFold(v, "null") {
			return nil, nil
		}
		return v, nil
	default:
		return v, nil
	}
	return nil, nil
}

func isEmpty(v interface{}) bool {
	return v == nil
}

// DeepParseJSON expands nested JSON strings while preserving key order
func (a *App) DeepParseJSON(input string) *JsonResult {
	input = strings.TrimSpace(input)
	if input == "" {
		return &JsonResult{Success: false, Error: a.tr("err.empty"), Code: "err.empty"}
	}
	if !json.Valid([]byte(input)) {
		return &JsonResult{Success: false, Error: a.tr("err.invalid"), Code: "err.invalid"}
	}
	// First format to get consistent output
	var buf bytes.Buffer
	if err := json.Indent(&buf, []byte(input), "", "  "); err != nil {
		return &JsonResult{Success: false, Error: err.Error()}
	}
	// Repeatedly expand embedded JSON strings until no more changes
	result := buf.String()
	for i := 0; i < 10; i++ {
		expanded := expandJSONStrings(result)
		if expanded == result {
			break
		}
		result = expanded
	}
	return &JsonResult{Success: true, Text: result}
}

// expandJSONStrings finds string values containing JSON and expands them in place
func expandJSONStrings(input string) string {
	var result strings.Builder
	i := 0
	runes := []byte(input)
	n := len(runes)

	for i < n {
		if runes[i] == '"' {
			// Find the full JSON string value
			strStart := i
			i++ // skip opening quote
			for i < n {
				if runes[i] == '\\' {
					i += 2
					continue
				}
				if runes[i] == '"' {
					i++ // skip closing quote
					break
				}
				i++
			}
			rawStr := string(runes[strStart:i])
			// Unquote the string
			var unquoted string
			if err := json.Unmarshal([]byte(rawStr), &unquoted); err == nil {
				trimmed := strings.TrimSpace(unquoted)
				if len(trimmed) >= 2 && ((trimmed[0] == '{' && trimmed[len(trimmed)-1] == '}') || (trimmed[0] == '[' && trimmed[len(trimmed)-1] == ']')) {
					if json.Valid([]byte(trimmed)) {
						// Determine indentation: look back to find the indent of current line
						indent := ""
						lineStart := strStart - 1
						for lineStart >= 0 && runes[lineStart] != '\n' {
							lineStart--
						}
						lineStart++
						// Find colon position to get the base indent
						for j := lineStart; j < strStart; j++ {
							if runes[j] == ' ' {
								indent += " "
							} else {
								break
							}
						}
						var buf bytes.Buffer
						if err := json.Indent(&buf, []byte(trimmed), indent, "  "); err == nil {
							result.WriteString(buf.String())
							continue
						}
					}
				}
			}
			result.WriteString(rawStr)
		} else {
			result.WriteByte(runes[i])
			i++
		}
	}
	return result.String()
}

// SmartUnwrap expands stringified JSON values nested inside a document, even
// when their inner quotes are under-escaped (a common shape in real API data,
// e.g. {"cardExtInfo":"{\"agreements\":\"[{\"name\":\"x\"}]\"}"}). Strict JSON
// parsing rejects such content, so a tolerant parser is used: a string value
// whose content begins with { or [ is read by bracket-balancing rather than by
// quote-scanning, then recursively unwrapped.
func (a *App) SmartUnwrap(input string) *JsonResult {
	text := strings.TrimSpace(input)
	if text == "" {
		return &JsonResult{Success: false, Error: a.tr("err.empty"), Code: "err.empty"}
	}
	dec := json.NewDecoder(strings.NewReader(text))
	v, err := decodeOrdered(dec)
	if err != nil {
		return &JsonResult{Success: false, Error: a.tr("err.invalid"), Code: "err.invalid"}
	}
	v = smartUnwrapValue(v)
	out, err := marshalNoHTML(v)
	if err != nil {
		return &JsonResult{Success: false, Error: err.Error()}
	}
	var buf bytes.Buffer
	if err := json.Indent(&buf, out, "", "  "); err != nil {
		return &JsonResult{Success: false, Error: err.Error()}
	}
	return &JsonResult{Success: true, Text: buf.String()}
}

// decodeOrdered parses JSON preserving key order (no value filtering).
func decodeOrdered(dec *json.Decoder) (interface{}, error) {
	t, err := dec.Token()
	if err != nil {
		return nil, err
	}
	switch v := t.(type) {
	case json.Delim:
		if v == '{' {
			om := &orderedMap{values: make(map[string]interface{})}
			for dec.More() {
				kt, err := dec.Token()
				if err != nil {
					return nil, err
				}
				key := kt.(string)
				val, err := decodeOrdered(dec)
				if err != nil {
					return nil, err
				}
				om.keys = append(om.keys, key)
				om.values[key] = val
			}
			dec.Token() // consume closing }
			return om, nil
		} else if v == '[' {
			var arr []interface{}
			for dec.More() {
				val, err := decodeOrdered(dec)
				if err != nil {
					return nil, err
				}
				arr = append(arr, val)
			}
			dec.Token() // consume closing ]
			return arr, nil
		}
		return nil, fmt.Errorf("unexpected delim: %v", v)
	default:
		return v, nil
	}
}

// smartUnwrapValue walks an ordered tree and unwraps any string value whose
// content is itself JSON (object/array) into a real nested structure.
func smartUnwrapValue(v interface{}) interface{} {
	switch val := v.(type) {
	case *orderedMap:
		for _, k := range val.keys {
			val.values[k] = smartUnwrapValue(val.values[k])
		}
		return val
	case []interface{}:
		for i := range val {
			val[i] = smartUnwrapValue(val[i])
		}
		return val
	case string:
		if parsed, ok := tolerantParse(val); ok {
			switch p := parsed.(type) {
			case *orderedMap:
				return smartUnwrapValue(p)
			case []interface{}:
				return smartUnwrapValue(p)
			}
		}
		return val
	default:
		return val
	}
}

// tparser is a tolerant JSON parser. It differs from encoding/json by reading
// string values whose content begins with { or [ via bracket-balancing, which
// lets it recover stringified JSON whose inner quotes are under-escaped.
type tparser struct {
	s []byte
	i int
}

func tolerantParse(s string) (interface{}, bool) {
	p := &tparser{s: []byte(s)}
	v, ok := p.value()
	if !ok {
		return nil, false
	}
	p.skip()
	if p.i != len(p.s) {
		return nil, false
	}
	return v, true
}

func (p *tparser) skip() {
	for p.i < len(p.s) && (p.s[p.i] == ' ' || p.s[p.i] == '\t' || p.s[p.i] == '\n' || p.s[p.i] == '\r') {
		p.i++
	}
}

func (p *tparser) value() (interface{}, bool) {
	p.skip()
	if p.i >= len(p.s) {
		return nil, false
	}
	switch p.s[p.i] {
	case '{':
		return p.parseObj()
	case '[':
		return p.parseArr()
	case '"':
		return p.parseStr()
	default:
		return p.parseLit()
	}
}

func (p *tparser) parseObj() (interface{}, bool) {
	p.i++ // consume {
	om := &orderedMap{values: make(map[string]interface{})}
	for {
		p.skip()
		if p.i >= len(p.s) {
			return nil, false
		}
		if p.s[p.i] == '}' {
			p.i++
			return om, true
		}
		if p.s[p.i] != '"' {
			return nil, false
		}
		key, ok := p.parseNormalStr()
		if !ok {
			return nil, false
		}
		p.skip()
		if p.i >= len(p.s) || p.s[p.i] != ':' {
			return nil, false
		}
		p.i++ // consume :
		val, ok := p.value()
		if !ok {
			return nil, false
		}
		om.keys = append(om.keys, key)
		om.values[key] = val
		p.skip()
		if p.i >= len(p.s) {
			return nil, false
		}
		switch p.s[p.i] {
		case ',':
			p.i++
			continue
		case '}':
			p.i++
			return om, true
		default:
			return nil, false
		}
	}
}

func (p *tparser) parseArr() (interface{}, bool) {
	p.i++ // consume [
	var arr []interface{}
	for {
		p.skip()
		if p.i >= len(p.s) {
			return nil, false
		}
		if p.s[p.i] == ']' {
			p.i++
			return arr, true
		}
		val, ok := p.value()
		if !ok {
			return nil, false
		}
		arr = append(arr, val)
		p.skip()
		if p.i >= len(p.s) {
			return nil, false
		}
		switch p.s[p.i] {
		case ',':
			p.i++
			continue
		case ']':
			p.i++
			return arr, true
		default:
			return nil, false
		}
	}
}

// parseStr reads a string value. If its content begins with { or [ it is
// treated as a stringified JSON value and unwrapped; otherwise it is read as a
// normal JSON string.
func (p *tparser) parseStr() (interface{}, bool) {
	p.i++ // consume opening "
	if p.i >= len(p.s) {
		return nil, false
	}
	if p.s[p.i] == '{' || p.s[p.i] == '[' {
		return p.parseStringified()
	}
	s, ok := p.parseNormalStrContent()
	return s, ok
}

// parseNormalStr reads a full quoted string (p.i at opening ") and returns its
// unquoted Go value.
func (p *tparser) parseNormalStr() (string, bool) {
	p.i++ // consume opening "
	return p.parseNormalStrContent()
}

// parseNormalStrContent reads to the closing quote (p.i just after opening ")
// respecting backslash escapes, and JSON-unquotes the result.
func (p *tparser) parseNormalStrContent() (string, bool) {
	start := p.i
	for p.i < len(p.s) {
		c := p.s[p.i]
		if c == '\\' {
			p.i += 2
			continue
		}
		if c == '"' {
			break
		}
		p.i++
	}
	if p.i >= len(p.s) {
		return "", false
	}
	raw := "\"" + string(p.s[start:p.i]) + "\""
	p.i++ // consume closing "
	var s string
	if err := json.Unmarshal([]byte(raw), &s); err == nil {
		return s, true
	}
	return "", false
}

// parseStringified reads a stringified JSON value (content begins with {/[)
// by bracket-balancing, then recursively tolerant-parses the extracted content.
func (p *tparser) parseStringified() (interface{}, bool) {
	start := p.i
	depth := 0
	for p.i < len(p.s) {
		switch p.s[p.i] {
		case '{', '[':
			depth++
		case '}', ']':
			depth--
		}
		p.i++
		if depth == 0 {
			break
		}
	}
	if depth != 0 {
		return nil, false
	}
	content := string(p.s[start:p.i])
	p.skip()
	if p.i < len(p.s) && p.s[p.i] == '"' {
		p.i++ // consume the string value's closing quote
	}
	if parsed, ok := tolerantParse(content); ok {
		switch parsed.(type) {
		case *orderedMap, []interface{}:
			return parsed, true
		}
	}
	// Fall back: keep the raw content as a plain string value.
	return content, true
}

func (p *tparser) parseLit() (interface{}, bool) {
	start := p.i
	for p.i < len(p.s) {
		c := p.s[p.i]
		if (c >= '0' && c <= '9') || c == '-' || c == '.' || c == 'e' || c == 'E' || c == '+' ||
			c == 't' || c == 'r' || c == 'u' || c == 'f' || c == 'a' || c == 'l' || c == 's' || c == 'n' {
			p.i++
		} else {
			break
		}
	}
	if p.i == start {
		return nil, false
	}
	tok := string(p.s[start:p.i])
	var v interface{}
	if json.Unmarshal([]byte(tok), &v) == nil {
		return v, true
	}
	return nil, false
}

// RemoveNewlines removes \n from text
func (a *App) RemoveNewlines(input string) string {
	return strings.ReplaceAll(input, "\n", "")
}

// RemoveBackslashes removes \ from text
func (a *App) RemoveBackslashes(input string) string {
	return strings.ReplaceAll(input, "\\", "")
}

// UnescapeString unescapes Java/JSON escape sequences
func (a *App) UnescapeString(input string) string {
	// Try JSON unquote
	var s string
	wrapped := `"` + input + `"`
	if err := json.Unmarshal([]byte(wrapped), &s); err == nil {
		return s
	}
	return input
}

// UnescapeDeepJSON repeatedly JSON-unquotes a string value, peeling layers of
// escape characters. e.g. "\"{\\\"a\\\":1}\"" -> {"a":1}. If the final result
// is valid JSON it is pretty-printed.
func (a *App) UnescapeDeepJSON(input string) *JsonResult {
	text := strings.TrimSpace(input)
	if text == "" {
		return &JsonResult{Success: false, Error: a.tr("err.empty"), Code: "err.empty"}
	}
	// Peel one layer of JSON string escaping per iteration while the text is a
	// quoted JSON string whose unquoted form differs.
	for i := 0; i < 32; i++ {
		if len(text) < 2 || text[0] != '"' || text[len(text)-1] != '"' {
			break
		}
		var s string
		if err := json.Unmarshal([]byte(text), &s); err != nil {
			break
		}
		if s == text {
			break
		}
		text = s
	}
	// Pretty-print when the unescaped result is itself valid JSON.
	if json.Valid([]byte(text)) {
		var buf bytes.Buffer
		if err := json.Indent(&buf, []byte(text), "", "  "); err == nil {
			text = buf.String()
		}
	}
	return &JsonResult{Success: true, Text: text}
}

// OpenFile opens a file dialog and reads the file
func (a *App) OpenFile() *JsonResult {
	file, err := runtime.OpenFileDialog(a.ctx, runtime.OpenDialogOptions{
		Title: a.tr("dlg.openTitle"),
		Filters: []runtime.FileFilter{
			{DisplayName: a.tr("filter.json"), Pattern: "*.json;*.txt"},
			{DisplayName: a.tr("filter.all"), Pattern: "*.*"},
		},
	})
	if err != nil || file == "" {
		return &JsonResult{Success: false, Error: a.tr("err.noFile"), Code: "err.noFile"}
	}

	data, err := os.ReadFile(file)
	if err != nil {
		return &JsonResult{Success: false, Error: fmt.Sprintf(a.tr("err.readFile"), err), Code: "err.readFile"}
	}

	content := string(data)
	// Auto format
	result := a.processJSON(content, false, false, false)
	if !result.Success {
		// Return raw content even if not valid JSON
		return &JsonResult{Success: true, Text: content}
	}
	return result
}

// SaveFile saves content to a file
func (a *App) SaveFile(content string) *JsonResult {
	file, err := runtime.SaveFileDialog(a.ctx, runtime.SaveDialogOptions{
		Title: a.tr("dlg.saveTitle"),
		Filters: []runtime.FileFilter{
			{DisplayName: a.tr("filter.json"), Pattern: "*.json"},
			{DisplayName: a.tr("filter.all"), Pattern: "*.*"},
		},
	})
	if err != nil || file == "" {
		return &JsonResult{Success: false, Error: a.tr("err.noFile"), Code: "err.noFile"}
	}

	err = os.WriteFile(file, []byte(content), 0644)
	if err != nil {
		return &JsonResult{Success: false, Error: fmt.Sprintf(a.tr("err.saveFile"), err), Code: "err.saveFile"}
	}
	return &JsonResult{Success: true, Text: a.tr("msg.saved")}
}

// JsonResult represents the result of a JSON operation
type JsonResult struct {
	Success bool   `json:"success"`
	Text    string `json:"text"`
	Error   string `json:"error"`
	Code    string `json:"code"`
}

func (a *App) processJSON(input string, sorted bool, compress bool, _ bool) *JsonResult {
	input = strings.TrimSpace(input)
	if input == "" {
		return &JsonResult{Success: false, Error: a.tr("err.empty"), Code: "err.empty"}
	}

	var raw interface{}
	decoder := json.NewDecoder(strings.NewReader(input))
	decoder.UseNumber()
	if err := decoder.Decode(&raw); err != nil {
		return &JsonResult{Success: false, Error: err.Error()}
	}

	if sorted {
		raw = sortKeys(raw)
	}

	var out []byte
	var err error
	if compress {
		out, err = json.Marshal(raw)
	} else {
		out, err = json.MarshalIndent(raw, "", "  ")
	}
	if err != nil {
		return &JsonResult{Success: false, Error: err.Error()}
	}

	return &JsonResult{Success: true, Text: string(out)}
}

// sortKeys recursively sorts map keys
func sortKeys(v interface{}) interface{} {
	switch val := v.(type) {
	case map[string]interface{}:
		sorted := make(map[string]interface{})
		for k, item := range val {
			sorted[k] = sortKeys(item)
		}
		return sorted
	case []interface{}:
		result := make([]interface{}, len(val))
		for i, item := range val {
			result[i] = sortKeys(item)
		}
		return result
	default:
		return v
	}
}

// filterValue recursively removes null, empty string, empty map, empty array
func filterValue(v interface{}) interface{} {
	if v == nil {
		return nil
	}
	switch val := v.(type) {
	case map[string]interface{}:
		if len(val) == 0 {
			return nil
		}
		result := make(map[string]interface{})
		for k, item := range val {
			filtered := filterValue(item)
			if filtered != nil {
				result[k] = filtered
			}
		}
		if len(result) == 0 {
			return nil
		}
		return result
	case []interface{}:
		if len(val) == 0 {
			return nil
		}
		var result []interface{}
		for _, item := range val {
			filtered := filterValue(item)
			if filtered != nil {
				result = append(result, filtered)
			}
		}
		if len(result) == 0 {
			return nil
		}
		return result
	case string:
		if val == "" || strings.EqualFold(val, "null") {
			return nil
		}
		return val
	default:
		return val
	}
}

// deepExpand recursively expands JSON strings within values
func deepExpand(v interface{}) interface{} {
	if v == nil {
		return nil
	}
	switch val := v.(type) {
	case map[string]interface{}:
		result := make(map[string]interface{})
		for k, item := range val {
			result[k] = deepExpand(item)
		}
		return result
	case []interface{}:
		result := make([]interface{}, 0, len(val))
		for _, item := range val {
			result = append(result, deepExpand(item))
		}
		return result
	case string:
		trimmed := strings.TrimSpace(val)
		if len(trimmed) >= 2 && ((trimmed[0] == '{' && trimmed[len(trimmed)-1] == '}') || (trimmed[0] == '[' && trimmed[len(trimmed)-1] == ']')) {
			var parsed interface{}
			if err := json.Unmarshal([]byte(trimmed), &parsed); err == nil {
				return deepExpand(parsed)
			}
		}
		return val
	default:
		return val
	}
}
