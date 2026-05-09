<img width="850" height="422" alt="swat-ui" src="https://github.com/user-attachments/assets/84bffa93-7be4-4f97-b17c-5e72f6c1dfbc" />

# SWAT — System Window Abstraction Toolkit

SWAT is a thin Java layer over the host OS's native UI toolkit. Every widget is a real native component — `NSButton` on macOS, `GtkButton` on Linux, `Microsoft.UI.Xaml.Controls.Button` on Windows — driven directly through Java 25's Foreign Function & Memory API. No JNI, no JNA, no bundled C wrapper, no shipped `.so` / `.dll` / `.dylib`. SWAT is **not** a portable runtime that paints its own widgets like Swing or Compose Desktop, not a Skia-rendered canvas dressed up to look native, and not a pluggable Look-and-Feel imitation. The host provides the UI; SWAT just talks to it. The trade is explicit: SWAT will only ever target macOS, Linux (GTK 4 + libadwaita), and Windows (WinUI 3) — but on each of those, apps inherit native look, feel, theming, accessibility, IME, accent colors, and animation for free, because there is nothing in between the Java code and the OS toolkit.

## Supported platforms

| Platform | Backend | Required runtime |
| --- | --- | --- |
| **macOS** | AppKit (Cocoa) | macOS 14+ |
| **Linux** | **GTK 4 + libadwaita** | GTK 4.10+ and libadwaita 1.5+ |
| **Windows** | WinUI 3 | Windows App SDK 1.7+ |

## Heritage: a spiritual successor to AWT and SWT

SWAT is the third Java attempt at the same idea: *one Java API, real native widgets underneath.*

- **AWT** (1995) introduced the "peer" model — thin Java objects backed by native widgets. The premise was right, the execution was constrained by its time. AWT had to ship on every Java platform, so it collapsed to the lowest common denominator of Motif, Win32, and classic Mac. JNI was the only way down to native code, threading rules across X11/Win32/Mac were irreconcilable, and the API ossified before the host toolkits matured. Sun gave up and built Swing on top of it.
- **SWT** (Eclipse, 2001) revived the peer model with conviction: real `GtkButton`, real `HWND`, real Carbon/Cocoa controls, no compromises on look and feel. It powered Eclipse for two decades and proved the approach works at scale. But SWT inherited JNI's costs — every supported platform needed a hand-written, hand-shipped native fragment (`libswt-gtk-*.so`, `swt-win32-*.dll`, `libswt-cocoa-*.jnilib`), each pinned to a specific OS/arch/widget-toolkit triple. Adding a platform meant a new C codebase. Keeping up with GTK 2 → 3 → 4 meant rewriting the C codebase. The build matrix and the lifecycle pain are why SWT never escaped Eclipse's gravity well.

SWAT keeps the peer model — the part both AWT and SWT got right — and discards the part that broke them: **JNI and shipped native fragments**. There is no C in this repository. There is no `.so` / `.dll` / `.dylib` we compile or distribute. The Java module loads the host's already-installed AppKit / GTK / WinUI shared libraries directly through `java.lang.foreign` and calls them. That single change — possible only on a recent JDK — is what makes "native widgets from Java" worth attempting again.

## How SWAT relates to the alternatives

| Toolkit | Strategy | What you get | What it costs |
| --- | --- | --- | --- |
| **AWT** | Native peers via JNI, lowest-common-denominator API | Real native widgets (in 1996 terms) | Frozen at the LCD; JNI for every platform; abandoned |
| **Swing** | Pure-Java rendering on top of an AWT `Canvas` | Total cross-platform consistency, deep API | Never actually native — pluggable Look-and-Feel imitates the host but always lags it; no real accessibility, IME, or system theming |
| **SWT** | Native peers via per-platform JNI fragments | Genuinely native look and feel | Hand-written C glue per platform/version; heavy build/release matrix; widget gaps when the host toolkit moves faster than the C bindings |
| **JavaFX** | Own scene graph rendered through Prism (and Skia in newer forks) | Modern API, animations, GPU acceleration | Looks like JavaFX everywhere; not native widgets |
| **Skia / Skiko / Compose Desktop** | One Skia-painted UI per pixel on every OS | Pixel-perfect identical output, designer-friendly | Identical output is the *anti*-goal here — no real `NSButton` means no AppKit accent tracking, no GNOME accent color, no platform IME, second-class accessibility |
| **SWAT** | Native peers via Panama FFM directly to the host toolkit | Genuinely native look, feel, accessibility, theming, animation | Three platforms only; each backend is opinionated about its host (e.g., GNOME-class Linux, not KDE) |

Swing and Skia/Skiko optimize for *the same UI everywhere*. SWAT optimizes for *the right UI on each host*. Both are legitimate goals; they are not the same goal. If you want one canvas painted identically on every desktop, Compose Multiplatform or JavaFX is the better tool. If you want a Mac app that an AppKit user can't tell from Swift, a GNOME app a GTK user can't tell from Vala, and a Windows app a WinUI user can't tell from C# — that's what SWAT is for.

## Why this approach is finally viable

The peer model was always the right idea. Two recent shifts make it practical in 2026 in a way it wasn't in 1995 or 2001.

**1. Java 25 ships stable FFM.** The Foreign Function & Memory API (`java.lang.foreign`) was finalized in JDK 22 and has shipped as a stable, non-incubating API in every release since. SWAT targets JDK 25 (LTS) with `--enable-native-access`. That gives us:

- Direct calls into `libobjc`, `libgtk-4`, `libadwaita-1`, and the WinRT ABI with no C, no `javah`, no per-platform `.so` to compile and ship.
- `MemorySegment` / `Arena` / `MemoryLayout` for safe, scoped, structured native memory — far better than `sun.misc.Unsafe` or the manual `byte[]` marshalling that AWT and SWT had to invent.
- Method handles that are JIT-friendly and GC-safe, so the call into a `gtk_button_new` is genuinely cheap.
- A clean module boundary: each backend is a JPMS module, selected at runtime via `ServiceLoader`.

SWT predates all of this. SWT was *forced* to invent its own hand-written C bridge per platform because, in 2001, that was the only way Java could call a C ABI at speed. It is no longer the only way, and it is no longer the best way.

**2. The three modern desktop toolkits have converged.** AppKit, WinUI 3, and GTK 4 + libadwaita have, independently, arrived at strikingly similar widget vocabularies:

- Title-bar / header-bar fusion (`NSToolbar` unified style, `AdwHeaderBar` in `AdwToolbarView`, WinUI `TitleBar`).
- First-class dark mode with system accent color tracking.
- Async file/save/folder pickers as the standard idiom (`NSOpenPanel`, `GtkFileDialog` 4.10, `FileOpenPicker`).
- Toast / banner overlays (`AdwToastOverlay`, WinUI `InfoBar`, custom `NSBox` is the conventional Mac shape).
- Adaptive split views, sidebars, and navigation patterns that map across the three (`NSSplitViewController`, `AdwNavigationSplitView`, `NavigationView`).
- Spring/easing animations, popovers, and modern accessibility trees.

This convergence is what lets a single SWAT API like `HeaderBar` cleanly resolve to all three peers without resorting to LCD compromises. AWT couldn't do this in 1995 because Motif, Win32, and Mac OS Classic had genuinely different mental models. The 2026 trio does not.

Put together: Panama FFM removes the *implementation* obstacle that crushed SWT's release process, and toolkit convergence removes the *design* obstacle that crushed AWT's API. Neither was true the last time someone tried this in Java.

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
