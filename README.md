# SWAT — System Widget Abstraction Toolkit

A Java UI toolkit that draws **native widgets** on three desktop platforms — and only those three:

| Platform | Backend | Required runtime |
| --- | --- | --- |
| **macOS** | AppKit (Cocoa) via Panama FFM | macOS 14+ |
| **Linux** | **GTK 4 + libadwaita** via Panama FFM | GTK 4.10+ and libadwaita 1.5+ |
| **Windows** | WinUI 3 via Panama FFM (planned) | Windows App SDK 1.7+ |

SWAT is **not** a portable runtime that paints its own widgets. Every Button you see is a real `NSButton` / `GtkButton` / `Microsoft.UI.Xaml.Controls.Button` on the host. There is no embedded Skia, no Qt port, no Swing-style emulation layer. The cost of that choice is that SWAT will only ever support these three platforms; the benefit is that SWAT apps look, feel, and behave like the host OS expects — including system theming, accessibility, IME, accent colors, and animation.

## Mission

> Build the smallest credible Java API that lets a single codebase render **first-class native interfaces** on macOS (AppKit), Linux (GTK 4 + libadwaita), and Windows (WinUI 3) — with native look, feel, and integration on each.

A few load-bearing words in that sentence:

- **First-class native** — not a lookalike. SWAT calls AppKit, GTK, and WinRT/WinUI directly through Panama FFM (`java.lang.foreign`). No JNI, no shading, no Skia, no embedded toolkit.
- **Three platforms only** — every API decision is made against this matrix. We don't ship hooks for BSD-without-GTK, headless web, mobile, or embedded. Toolkit comparisons (Qt, Swing, JavaFX) are useful as reference points but are not back-end candidates.
- **Single codebase** — the SWAT API in `swat-api/` is the only thing application code imports. Widgets like `Button.of(...).onClick(...)` are platform-agnostic; the platform-specific peer is selected by `Toolkit.detect()` at launch.

## Why libadwaita is required, not optional

Bare GTK 4 is the **substrate**, not the design target. The modern Linux desktop (GNOME, Phosh, elementary OS, Pop!_OS) is shaped by **libadwaita**: `AdwApplicationWindow`, `AdwHeaderBar`, `AdwToolbarView`, `AdwAlertDialog`, `AdwClamp`, `AdwSpinner`, the GNOME 47+ color-scheme tracking, the spring animation engine, the responsive shell. Without libadwaita, a GTK 4 app on a contemporary GNOME desktop looks like a 2018 application — wrong borders, wrong dialog flow, no adaptive layout, no spring transitions, no accent-color tracking.

So SWAT's Linux backend treats GTK 4 + libadwaita as **one** target. Implementation rules:

1. **GTK 4 is the substrate.** Layouts, controls, event controllers, the main loop — that's all GTK.
2. **Adwaita is the design language.** Top-level windows are `AdwApplicationWindow`. Header bars are `AdwHeaderBar` inside `AdwToolbarView`. Alerts are `AdwAlertDialog`. Sidebars are `AdwNavigationSplitView`. Color scheme follows `AdwStyleManager`. Animations use `AdwSpringAnimation` where libadwaita has one and spring semantics matter.
3. **Adwaita wins ties.** When GTK 4 has a generic widget and libadwaita has a refined replacement (e.g. `GtkDialog` vs `AdwDialog`, `GtkActionRow` vs `AdwActionRow`), SWAT picks the Adwaita one.
4. **No bare-GTK fallback.** The Linux build requires libadwaita 1.5+; we do not ship a "GTK 4 minus Adwaita" code path. Once libadwaita FFM bindings land (with the Tier-2 HeaderBar work — see `backlog.md`), `GtkPeerFactory.supports()` will probe for libadwaita and return false on hosts without it. Until then the backend probes only `gtk_init`, so a libadwaita-less host will load the backend and fail later when an Adwaita-using widget is constructed.

This is a deliberate scoping decision. KDE / Qt / Xfce / non-GNOME desktops are out of scope for the SWAT GTK backend in the same way Windows-without-WinUI-3 is out of scope for the Windows backend. Each backend is opinionated about its host.

## Repo layout

```
swat-api/               public Java API + SPI (Peer, PeerFactory, *Config records)
swat-backend-macos/     AppKit backend (Objective-C runtime via FFM)
swat-backend-gtk/       GTK 4 + libadwaita backend (will absorb libadwaita FFM bindings as Tier-2 lands)
swat-backend-windows/   WinUI 3 backend (planned)
swat-samples/           runnable samples — HelloWorld, Demo, Tier1Demo, Tier1Smoke
backlog.md              prioritized work list (gaps, follow-ups, plan steps)
swat-peer-comparision.md   per-component comparison of AppKit / WinUI 3 / GTK 4 + libadwaita / Qt 6
```

The peer-comparison doc includes Qt 6 as a **reference** — Qt is the one widely-known toolkit that has tackled all three of our targets, so it's an indispensable design reference even though Qt is not itself a SWAT backend.

## Status

| Component | macOS | Linux (GTK 4 + Adwaita) | Windows |
| --- | --- | --- | --- |
| Tier 1 widgets (Window, Row/Column, Label, Button, TextField, ListView, Checkbox, Switch, Radio, Slider, ProgressBar, Spinner, DropDown, Frame, ScrollContainer, Tabs, Splitter, Expander, Tree, Image, Canvas) | Done | Done — Window now uses `AdwWindow` + `AdwToolbarView` | — |
| Menu bar + alerts + clipboard + open URL + notifications | Done | Done | — |
| Drag-and-drop (text payloads) | Done | Done | — |
| Tier-2 canary: HeaderBar | Done — `NSToolbar` unified style | Done — `AdwHeaderBar` in `AdwToolbarView` | — |
| Capability registry (`Capability` enum + `Toolkit.supports`) | Done | Done | — |
| Per-widget typed hints | Pending — lands per-widget | Pending — lands per-widget | — |
| SystemTray | Done — `NSStatusItem` | Done — pure-Java `org.kde.StatusNotifierItem` over GLib GDBus | — |
| Async file/save/folder pickers | Done — `NSOpenPanel`/`NSSavePanel` | Done — `GtkFileDialog` (4.10+) | — |
| Async color/font pickers | Pending — design needed | Pending | — |
| Toast overlay | Done — custom `NSBox` overlay | Done — `AdwToastOverlay` | — |

See `backlog.md` for the prioritized punch list.

## Build and run

```bash
./gradlew assemble                      # build everything
./gradlew :swat-samples:tier1Smoke      # non-interactive smoke test
./gradlew :swat-samples:tier1           # interactive demo of every Tier 1 widget
./gradlew :swat-samples:demo            # menu bar + dialogs sample
```

Backend selection is automatic via `ServiceLoader` — `MacosPeerFactory.supports()` returns true on macOS, `GtkPeerFactory.supports()` returns true on Linux/FreeBSD with GTK 4 (and, soon, libadwaita) installed. Override with `-Dswat.backend=macos` / `gtk` if you need to pin one explicitly.

## Design references

- `swat-peer-comparision.md` — the long-form per-component analysis. The four columns are AppKit, WinUI 3, GTK 4 + libadwaita (as one), and Qt 6 (reference only).
- `backlog.md` — prioritized gaps, planned Tier-2 widgets, and SPI ergonomics work.
