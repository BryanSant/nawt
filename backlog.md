# SWAT backlog

Known gaps and follow-up work, ordered by priority.

SWAT targets three platforms only: **macOS/AppKit**, **Windows/WinUI 3**, and **Linux/GTK 4 + libadwaita**. Every backlog entry is scoped to one or more of those three. The Linux target is GTK 4 *and* libadwaita as a single product — when an entry says "GTK", read it as "GTK 4 + libadwaita 1.5+", and prefer Adwaita primitives wherever libadwaita refines a GTK widget. See `README.md` for the full mission statement and `swat-peer-comparision.md` for the per-component analysis.

## Tier 1 follow-ups (gaps in the current implementation)

### Drag-and-drop — text payloads only
- macOS uses an `NSPanGestureRecognizer` on the source view (with `delaysPrimaryMouseButtonEvents=NO`) and KVO-style isa-swizzling on the destination view to install `draggingEntered:` / `performDragOperation:` overrides. Both sides go through `MacosDnD`.
- GTK uses `GtkDragSource` (`prepare` signal returns a `GdkContentProvider` built from a `GValue<G_TYPE_STRING>`) and `GtkDropTarget` (`drop` signal extracts the `GValue`). Both controllers attach via `gtk_widget_add_controller`. See `GtkDnD`.
- **Tier-2 follow-ups**: non-text payloads (files, custom MIME types), drag-over hover feedback, multiple `NSPasteboardType`s / `GdkContentFormats`, drop position coordinates surfaced to the consumer, drag image customisation.

### Tree — native expand/collapse
- macOS uses `NSOutlineView` with NSString path keys (`""`, `"0"`, `"0.1"`, …) as items; identity by `-isEqualToString:` lets reloads round-trip cleanly. Disclosure triangles, expand/collapse, and animation are all handled by AppKit.
- GTK uses the modern list-model stack: `GListStore` → `GtkTreeListModel` (autoexpand=true) → `GtkSingleSelection` → `GtkColumnView`, with `GtkStringObject` payloads holding the same path keys. A single `GtkSignalListItemFactory` handles `setup` (creates a `GtkTreeExpander`) and `bind` (resolves path → label).
- **Tier-2 follow-ups**: lazy children loading (currently the full tree is materialised eagerly via the SPI's `TreeNodeData`), per-node icons, multi-column trees, drag-reorder of nodes.

### Notification — macOS uses deprecated API
- macOS uses `NSUserNotification` / `NSUserNotificationCenter`, deprecated since macOS 11.
- Modern path is `UNUserNotificationCenter`, which requires a code-signed app bundle and an explicit user permission grant — non-trivial for a launched-from-CLI development setup.
- GTK uses libnotify, which is widely available but optional (graceful fallback prints to stderr).
- **Future**: migrate macOS to `UNUserNotificationCenter` once we have a packaged-app story; fall back to `NSUserNotification` when running unbundled.

### Canvas — minimal Painter API
- `Painter` covers solid color + fillRect + strokeRect + line. That's it.
- **Missing for non-trivial use**: stroke width, line caps/joins, paths (curves, arcs), text rendering, clipping, transforms, alpha blending modes, gradients, image draws.
- Sufficient for Tier 1 demos; extend as real samples need more.

## Plan items not yet started (from `swat-native-toolkit-plan.md`)

### Step 2 of rollout — Capability registry done; typed hints pending
- **Done**: `Capability` enum (`HEADER_BAR`, `TOAST_OVERLAY`, `DRAG_TEXT`, `GLOBAL_MENU_BAR`, `SYSTEM_TRAY`, `TRANSLUCENT_BACKDROP`, `SHEET_MODAL_DIALOG`), `Capabilities` wrapper (`spi/Capabilities.java`), `PeerFactory.capabilities()` with empty default, backend overrides declaring what they ship today, `Toolkit.supports(Capability)` public helper.
- **Pending**: per-widget hint sealed hierarchies (`WindowHint`, `HeaderBarHint`, `AlertHint`, …) attached to `*Config` records. Each hint type lands with its first concrete use case — e.g. `WindowHint.TranslucentBackdrop(Material)` ships when the macOS backend wires `NSVisualEffectView`. Building empty hint shells before there's a concrete impl would be premature.

### Step 3 — `SystemTray` end-to-end (both backends, with caveats)
- **macOS**: `MacosSystemTrayPeer` wrapping `NSStatusItem` (template-flagged image for dark-mode tinting, tooltip, menu via `setMenu:`).
- **Linux (pure-Java)**: `StatusNotifierItem.java` implements the `org.kde.StatusNotifierItem` D-Bus interface using GLib's GDBus (`GDBus.java` FFM bindings to libgio-2.0 + `GVariant.java` typed builders). Menu and actions are exported via `g_dbus_connection_export_menu_model` and `g_dbus_connection_export_action_group` — GLib handles the dbusmenu protocol translation, so we don't reimplement `com.canonical.dbusmenu`.
- **Watcher probe**: `GtkPeerFactory.capabilities()` calls `org.freedesktop.DBus.NameHasOwner("org.kde.StatusNotifierWatcher")` on the session bus. `Capability.SYSTEM_TRAY` is true only when a watcher is present (KDE always; GNOME with the AppIndicator extension; Cinnamon, etc.). Result is cached per factory.
- **Caveats — still pending on Linux**:
  - **Mid-lifetime `setMenu`** is not supported — the menu attached at construction is the one that gets exported. Swapping menus needs unexport + re-export plus emitting a `Menu` property change.
  - **Pixmap icons** are not supported — `IconName` + `IconThemePath` only. Apps must ship their icon as a freedesktop-themed PNG. Pixmap support would need the `(iiay)` array of (width, height, ARGB bytes) tuples.
  - **`ToolTip` struct** is omitted from the SNI XML; hosts fall back to displaying `Title` on hover, which gives equivalent UX. Real `(sa(iiay)ss)` tuple support would land with pixmap icons.
  - **Click handlers** (`Activate` / `SecondaryActivate` / `Scroll`) are no-ops; click delivery happens through the menu's `GAction` callbacks. SWAT doesn't currently expose a primary-click handler on the public `SystemTray` API anyway.
- Reference C implementation: `../libayatana-appindicator-glib/src/ayatana-appindicator.c`.

### Step 1 (done) — Tier model validated with `HeaderBar`
- SPI: `HeaderBarConfig`, `HeaderBarPeer`, `PeerFactory.createHeaderBar`, `WindowPeer.setHeaderBar`. Public `HeaderBar` (not in the Widget hierarchy — it's window chrome).
- macOS: `NSToolbar` in unified style with `window.toolbarStyle = .unified`. `SwatToolbarDelegate` registered in `Delegates.java`; items wrap their underlying NSView via `NSToolbarItem.setView:`.
- GTK: `AdwHeaderBar` inside `AdwToolbarView` inside `AdwWindow`. The libadwaita FFM bindings now live in `Adw.java`; `GtkPeerFactory.supports()` probes `libadwaita-1.so.0` for `adw_init`. Every window gets a default empty `AdwHeaderBar` so chrome is always present; `setHeaderBar` swaps the default for a user-supplied bar.
- Subsequent libadwaita widgets reuse `Adw.java`. **Done so far**: `AdwAlertDialog` (replaced `GtkAlertDialog`); `AdwToast` / `AdwToastOverlay` (every `GtkWindowPeer` now wraps content in an `AdwToastOverlay`, exposed via `Window.toast(String)`). Remaining candidates: `AdwNavigationSplitView` (responsive split layout), `AdwClamp` (readable line widths), `AdwSpringAnimation` (when SWAT grows an animation API).

### Step 4–6 — WinUI 3 backend (future, not started)
- `swat-backend-windows/` module against WinRT via Panama FFM.
- Revisit `UiLoop`/`WindowPeer` for `DispatcherQueue.TryEnqueue` and HWND ownership before any Windows code lands.
- `WindowsSystemTrayPeer` via `Shell_NotifyIcon` once the SPI is proven on macOS + Linux.

## SPI / API ergonomics

### Async picker dialogs — file/save/folder done; color/font open
- File-open / file-save / folder pickers ship on both backends. macOS uses `NSOpenPanel` / `NSSavePanel` (synchronous `runModal`); GTK uses `GtkFileDialog` (4.10+) async open/save/select_folder. SPI: `Toolkit.showFileOpenDialog`, `Toolkit.showFileSaveDialog`, `Toolkit.showFolderDialog`, all returning `CompletableFuture<String>` (path or `null` on cancel).
- **Color picker** is deferred: `NSColorPanel` is a global non-modal floating panel without a "show modal, return color, dismiss" pattern; GTK has the modern `GtkColorDialog` (4.10+) but the macOS UX gap means the Tier-2 surface needs design work. Same story for **font picker** (`NSFontPanel` vs `GtkFontDialog`).
- **Tier-2 follow-ups** for the existing pickers: file-type filters (macOS `setAllowedContentTypes:`, GTK `gtk_file_dialog_set_filters`), parent-window association, multi-select for file-open.

### `VetoableEvent` foundation done; new vetoable events case-by-case
- `VetoableEvent` is now an abstract class with built-in `vetoed` state — subclasses just add their own getters and inherit `veto()` / `isVetoed()` for free. `WindowCloseEvent` migrated. Future event types extend the base.
- Adoption analysis is in the class javadoc:
  - **Drag start**: already implicit in `Widget.dragText(Supplier)` — supplier returning `null` aborts on both backends; no separate event needed.
  - **Tab change**: macOS-clean (`tabView:shouldSelectTabViewItem:` returns BOOL), GTK-awkward (`GtkNotebook switch-page` is post-change only; veto would have to programmatically switch back). Not exposed.
  - **Focus changes**: not recommended; preventing focus from leaving breaks keyboard accessibility.
  - **Alert button activation**: not naturally vetoable; the user already chose.

### Document the peer escape hatch
- Add javadoc on `Widget.peer()` and `Peer` calling out:
  - Casting to backend-specific subtypes is supported but unstable.
  - Backend module names (`io.github.swat.backend.gtk` / `…macos`) are part of the contract for users targeting a specific backend.
  - Casting in cross-platform code is a smell; use capabilities (when added) instead.

### macOS global menu in API contract
- `MenuBar` is logical-per-window. The macOS backend collapses the key window's menu bar onto `NSApp.mainMenu`. Document this in `MenuBar.java` so cross-platform users aren't surprised that two windows can't show different menu bars simultaneously on macOS.

## Verification / test infrastructure

- **CI**: `.github/workflows/ci.yml` matrix — macOS 14 (AppKit) + Ubuntu 24.04 (GTK 4 + libadwaita). Each job runs `assemble check` plus the Tier-1 smoke. Linux job installs `libgtk-4-dev libadwaita-1-dev xvfb dbus-x11` and runs the smoke under `xvfb-run` + `dbus-run-session` (fresh X server + session bus so GDBus probes succeed).
- Add equivalent smoke tests for each new Tier-2 widget as it lands. Currently `Tier1Smoke` exercises HeaderBar and Toast but not SystemTray (creating a tray icon under Xvfb without a watcher should be a no-op, but the test isn't there yet).
- A GitHub repo needs to exist before the CI badge URL is meaningful — the workflow file is in place either way.

## Cleanup nits

- `Container.java` permits list and `Widget.java` permits list grew long; consider grouping into category interfaces (`Control`, `Container`, `Surface`) once Tier 2 lands.
