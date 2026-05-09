# SWAT Peer Comparison: AppKit, WinUI 3, GTK 4 + libadwaita — and Qt 6 as a reference

A component-by-component analysis of the native UI toolkits SWAT abstracts over (or learns from). The goal is to map the **common core** any cross-platform API can rely on, and to flag the **edges** — capabilities unique to one platform, deprecations to avoid, and asymmetries any portable abstraction will have to reconcile.

**SWAT's three target platforms — and only three — are bolded.** Qt 6 is included as a **reference** because it is the one widely-shipped toolkit that has independently tackled all three of our targets, and its API choices illuminate where the cross-platform middle ground actually lies. Qt is *not* itself a SWAT backend.

Versions surveyed (current as of 2026):

| Framework | Surface area | Version assumed | Owner | Role in SWAT |
| --- | --- | --- | --- | --- |
| **AppKit** | Cocoa/Objective-C/Swift, macOS-only | macOS 14 Sonoma / 15 Sequoia | Apple | **Target** — `swat-backend-macos` |
| **WinUI 3** | XAML/C#, Windows desktop | Windows App SDK 1.7 / 1.8 / 2.0 | Microsoft | **Target** — `swat-backend-windows` (planned) |
| **GTK 4 + libadwaita** | C/GObject, GNOME-native | GTK 4.10+, libadwaita 1.5+ | GNOME | **Target** — `swat-backend-gtk` (libadwaita is **required**, not optional) |
| Qt 6 (Widgets + Quick) | C++/QML, cross-platform | 6.7 / 6.8 | Qt Group | Reference only |

The Linux entry deserves special note: SWAT treats GTK 4 and libadwaita as **a single target**, not as a base + optional layer. Bare GTK 4 misses the modern Linux desktop's window chrome (`AdwApplicationWindow`, `AdwHeaderBar`, `AdwToolbarView`), modern dialog flow (`AdwAlertDialog`, `AdwDialog`), color-scheme tracking (`AdwStyleManager`), spring animations (`AdwSpringAnimation`), and adaptive shell (`AdwNavigationSplitView`, `AdwClamp`). Every contemporary GNOME application — Files, Maps, Calendar, GNOME Builder — depends on libadwaita; an app that ships only bare GTK 4 looks like 2018. SWAT therefore mandates libadwaita 1.5+; the GTK backend's `supports()` predicate will return false on hosts without it once the libadwaita FFM bindings land alongside the Tier-2 HeaderBar work.

Qt is the odd one out — it is itself a cross-platform framework with **two parallel UI stacks** (Widgets and Quick). Both stacks are noted where they materially differ; otherwise "Qt" means the union. Where Qt is the only column to offer a feature, that feature is by definition out of SWAT's portable core (since Qt isn't a SWAT backend) — but it tells us whether the feature is achievable cross-platform at all, and what shape an API for it might take.

A note on philosophy before the catalog: each toolkit makes opinionated choices about where the OS ends and the app begins. AppKit assumes one global menu bar and tightly integrated system services. WinUI 3 puts an HWND under everything but hides it; many WinRT APIs require manual `IInitializeWithWindow` interop. GTK 4 expects client-side decorations and the freedesktop portal stack. Qt prides itself on covering the same surface on every OS, sometimes by reimplementing it. These choices propagate into every component below.

---

## 1. Foundation — object model, properties, events

| Concern | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Object base | `NSObject` | `DependencyObject` | `GObject` | `QObject` |
| Property system | KVO/KVC + `@Published`/`@Observable` (Swift) | `DependencyProperty` + `INotifyPropertyChanged` | `GObject` properties + `notify::` signal | `Q_PROPERTY` + `QProperty<T>` (bindable) |
| Signal/event mechanism | Target/action selector + responder chain | Routed events + `Click`/`Changed` handlers | `g_signal_*` named signals | Signals/slots (type-safe) |
| Reflection | Objective-C runtime / Swift Mirror | XAML loader + reflection on attributes | GObject introspection (gobject-introspection) | Meta-Object Compiler (`moc`) |
| Event loop | `NSRunLoop` (modes) | `DispatcherQueue` | `GMainLoop` / `GMainContext` | `QEventLoop` per thread |
| UI thread rule | Main thread only (`@MainActor`) | UI dispatcher only | Main thread only | Main thread only |
| Background → UI marshal | `DispatchQueue.main.async` / Swift Concurrency | `DispatcherQueue.TryEnqueue` | `g_idle_add` / context invocation | Queued signals (`Qt::QueuedConnection`) |
| Application object | `NSApplication` + delegate | `Microsoft.UI.Xaml.Application` + `App.xaml` | `GtkApplication` (extends `GApplication`) | `QApplication` / `QGuiApplication` / `QCoreApplication` |
| Single-instance / activation | `NSApplicationDelegate` callbacks | `AppInstance.GetActivatedEventArgs` + redirect | Built into `GApplication` (D-Bus) | Build-yourself (`QSharedMemory` / `QLocalServer`) |

**Convergence.** All four pin UI work to a single thread, expose a property+observer pattern, and provide an event-driven application object. Cross-thread marshaling exists in every one.

**Divergence.** Signal/event mechanics differ deeply: AppKit relies on the target/action + responder chain idiom (UI events bubble up through views to the window to the app delegate); WinUI 3 has true XAML routed events with tunneling/bubbling; GTK 4 replaced its old per-widget signals with **event controllers** as composable objects you attach to widgets; Qt's signals/slots are direct, type-checked, and cross-thread-safe via queued connections — they are not a routing system. Any SWAT abstraction must pick one model and adapt the rest.

**Reflection cost.** WinUI 3 and Qt have heavyweight code generation (XAML compiler, `moc`); AppKit and GTK rely on runtime introspection (Objective-C runtime, GObject type system). A Java SPI sits closer to the GObject model.

---

## 2. Windowing — top-level surfaces and modality

| Concept | AppKit | WinUI 3 | GTK 4 | Qt 6 (Widgets / Quick) |
| --- | --- | --- | --- | --- |
| Top-level | `NSWindow`, `NSPanel` | `Window`, `AppWindow` | `GtkWindow`, `GtkApplicationWindow`, `AdwApplicationWindow` | `QMainWindow`/`QWidget` / `ApplicationWindow`,`Window` |
| Multi-window | First-class | First-class | First-class | First-class |
| Modal app-window | `runModal(for:)` | **No true OS modal** — emulated with `OverlappedPresenter.IsModal` + disable owner | `GtkWindow.modal` (deprecated `GtkDialog`) | `QDialog::exec()` / `Dialog.modal` |
| Sheet-style attached modal | `beginSheet(_:completionHandler:)` (native sheets) | Approximated by `ContentDialog` | Adwaita uses `AdwDialog` (adaptive) | None native; emulate |
| Popover | `NSPopover` | `Flyout` / `MenuFlyout` / `TeachingTip` | `GtkPopover` / `GtkPopoverMenu` | `Popup` (Quick); `QMenu`-as-popover |
| Tabbed windows | System window-tabbing (`NSWindow.tabbingMode`) | None native | None native | None native |
| Document/scene model | `NSDocument` framework | None | None | None |
| Title-bar customization | `fullSizeContentView` + reposition traffic lights | `Window.SystemBackdrop` + `TitleBar` control (1.6+) | `GtkHeaderBar` / `AdwHeaderBar` (CSD-mandatory) | `QMainWindow` titlebar fixed; Quick `ApplicationWindow.header` |
| Translucent backdrop | `NSVisualEffectView` (Vibrancy) | `MicaBackdrop`, `DesktopAcrylicBackdrop` | None native (theme-driven only) | None native |

**Convergence.** Every framework supports multiple windows and popovers; every framework offers some way to integrate content into the title-bar area.

**Divergence.**
- **Modality.** AppKit's `NSWindow` sheets are a unique and beloved feature: a window-modal child window that animates from the parent. WinUI 3 has no equivalent and resorts to `ContentDialog` overlays inside the same XamlRoot. GTK 4 deprecated `GtkDialog` in 4.10 in favor of async dialog *objects* (no widget at all). Qt's `QDialog::exec()` is a true blocking modal but visually bare.
- **CSD vs. SSD.** GTK 4 is the only framework that effectively *requires* client-side decorations on Wayland — the header bar is part of your app, not the OS. AppKit and WinUI 3 own the title-bar and let you opt in to extending into it. Qt delegates to the host platform.
- **Backdrops.** Translucent system materials (Mica/Acrylic on Windows, NSVisualEffectView on macOS) are **first-class** on AppKit and WinUI 3, **absent** on GTK and Qt.
- **Window tabs.** Apple's system-wide window tabbing (Cmd-Shift-Backtick) is exposed only via AppKit. No peer.

**Implication for SWAT.** A portable `Window` API can guarantee multi-window, modal, and a popover primitive. Sheets, system backdrops, document models, and tabbed windows are platform extras best surfaced via opt-in capability flags rather than the core API.

---

## 3. Layout

| Primitive | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Stack (axis-oriented) | `NSStackView` (with gravity areas) | `StackPanel` | `GtkBox` | `QHBoxLayout`/`QVBoxLayout` / `RowLayout`/`ColumnLayout` |
| 2D grid | `NSGridView` | `Grid` (`*` star sizing) | `GtkGrid` | `QGridLayout` / `GridLayout` |
| Form (label/field rows) | (manual) | (manual) | `AdwPreferencesGroup` rows | `QFormLayout` |
| Wrapping flow | (manual) | `WrapGrid`/`VariableSizedWrapGrid` | `GtkFlowBox` | `Flow` (Quick) |
| Splitter | `NSSplitView` / `NSSplitViewController` | `SplitView`, `TwoPaneView` | `GtkPaned`, `AdwNavigationSplitView`, `AdwOverlaySplitView` | `QSplitter` / `SplitView` |
| Z-stack / overlay | (layer composition) | `Grid` (last child on top) | `GtkOverlay` | `QStackedLayout` (one visible) / `Item` z-order |
| Constraint-based | `NSLayoutConstraint` (Auto Layout) — first-class | (none — Grid is canonical) | `GtkConstraintLayout` (rare) | `Anchors` (Quick) |
| Center / 3-slot | `NSStackView` gravity areas | `RelativePanel` | `GtkCenterBox` | (compose with stretches) |
| Absolute / canvas | manual frame | `Canvas` | `GtkFixed` | manual / `Item` x,y |
| Width clamp (readable line) | (manual) | `Viewbox` (scaling) | `AdwClamp` | (manual) |
| Virtualized custom layout | `NSCollectionViewCompositionalLayout` | `ItemsRepeater` + `Layout` | `GtkListView` factory + custom layout manager | `ListView` / `TableView` with delegates |

**Convergence.** Stack + grid + splitter + scroll exist everywhere. Every toolkit also offers a layered/overlay primitive in some form.

**Divergence.**
- AppKit's first layout citizen is **constraint-based** (Auto Layout); the others are **flex/grid-first**. Quick's `Anchors` is the closest analog but is generally avoided in favor of Layouts.
- GTK 4 made the *unusual* decision to split layout from container — any widget can host a `GtkLayoutManager`. The other three keep layout as a property of dedicated container widgets.
- WinUI 3's `Grid` with star sizing is more powerful than GTK's `GtkGrid` and is the de facto WinUI layout — you rarely write nested stacks.
- `AdwClamp` (width-cap for readable line lengths) and the WinUI `TwoPaneView` (dual-screen-aware) have no peers.

**Implication for SWAT.** A small layout core — Row, Column, Grid, Stack, Scroll — covers every backend. The current SWAT API (`Row`, `Column`, `Container`) lines up well; consider adding a Grid before Splitter.

---

## 4. Basic widgets

The core control vocabulary is largely shared; the table maps direct equivalents.

| Control | AppKit | WinUI 3 | GTK 4 | Qt 6 (Widgets / Quick) |
| --- | --- | --- | --- | --- |
| Push button | `NSButton` (`.momentaryPushIn`) | `Button` | `GtkButton` | `QPushButton` / `Button` |
| Toggle button | `NSButton` (`.pushOnPushOff` / `.toggle`) | `ToggleButton` | `GtkToggleButton` | `QPushButton.checkable` / `Button.checkable` |
| Hyperlink | `NSButton` w/ attributed title or `NSTextField` link | `HyperlinkButton` | `GtkLinkButton` | `QLabel` w/ HTML or `Button` |
| Split button | `NSPopUpButton` (close enough) | `SplitButton`, `DropDownButton`, `ToggleSplitButton` | `AdwSplitButton` | (compose) |
| Segmented | `NSSegmentedControl` | `SelectorBar` (1.4+) | `GtkBox` w/ `.linked` CSS class | `QButtonGroup` |
| Label | `NSTextField(labelWithString:)` | `TextBlock` | `GtkLabel` (or `GtkInscription` for cells) | `QLabel` / `Label` / `Text` |
| Text input (1-line) | `NSTextField` | `TextBox` | `GtkEntry` | `QLineEdit` / `TextField` |
| Text input (multiline) | `NSTextView` in `NSScrollView` | `TextBox` (AcceptsReturn) / `RichEditBox` | `GtkTextView` | `QTextEdit`/`QPlainTextEdit` / `TextArea` |
| Password | `NSSecureTextField` | `PasswordBox` | `GtkPasswordEntry` | `QLineEdit.PasswordEchoMode` |
| Search | `NSSearchField` | `AutoSuggestBox` | `GtkSearchEntry` | `QLineEdit` (style) |
| Number / spin | `NSStepper` (paired manually) | `NumberBox` (with expressions!) | `GtkSpinButton` | `QSpinBox` / `SpinBox` |
| Checkbox | `NSButton` (`.switch` style) | `CheckBox` (tri-state) | `GtkCheckButton` | `QCheckBox` / `CheckBox` |
| Radio | `NSButton` (`.radio`) — auto-grouping by action | `RadioButton`/`RadioButtons` (group container) | `GtkCheckButton` + `set_group()` (no `GtkRadioButton`) | `QRadioButton` / `RadioButton` |
| Switch (iOS-style) | `NSSwitch` (10.15+) | `ToggleSwitch` | `GtkSwitch` | `Switch` (Quick) — none in Widgets |
| Slider | `NSSlider` (linear/circular) | `Slider` | `GtkScale` | `QSlider`/`QDial` / `Slider`/`Dial`/`RangeSlider` |
| Progress bar | `NSProgressIndicator` (.bar) | `ProgressBar` | `GtkProgressBar` | `QProgressBar` / `ProgressBar` |
| Spinner / busy | `NSProgressIndicator` (.spinning) | `ProgressRing` | `GtkSpinner` | `BusyIndicator` (Quick) — `QProgressBar(0,0)` in Widgets |
| Date picker | `NSDatePicker` | `DatePicker`/`CalendarDatePicker`/`CalendarView` | `GtkCalendar` | `QDateEdit`/`QCalendarWidget` — none in Quick Controls |
| Time picker | `NSDatePicker` (.hourMinute) | `TimePicker` | (none) | `QTimeEdit` |
| Color picker (inline) | `NSColorWell` | `ColorPicker` | (none — use `GtkColorDialog`) | `QColorDialog` only |
| Image view | `NSImageView` | `Image` | `GtkPicture` (content) / `GtkImage` (icons) | `QLabel` w/ pixmap / `Image` |
| Rating | `NSLevelIndicator(.rating)` | `RatingControl` | (none) | (none) |
| Level / capacity gauge | `NSLevelIndicator` | (none) | `GtkLevelBar` | (none) |
| Drop-down | `NSPopUpButton` | `ComboBox` | `GtkDropDown` | `QComboBox` / `ComboBox` |
| Pickers (wheel) | `UIPickerView` is iOS — not in AppKit | (none) | (none) | `Tumbler` (Quick) |

**Convergence.** Buttons, labels, text fields (single + multi), checkboxes, radios, switches, sliders, progress, spinners, drop-downs, and image views are present in all four — these are the safe **core widget vocabulary** for a portable API.

**Divergence and unique offerings.**
- **`NumberBox` (WinUI 3) accepts arithmetic expressions** ("3+4*2") — unique.
- **`NSDatePicker.style = .clockAndCalendar`** is a uniquely AppKit wall-clock face.
- **`GtkLevelBar`** with named offset thresholds (low/medium/high) is uniquely GTK.
- **`RatingControl` and `NSLevelIndicator(.rating)`** exist; GTK and Qt have no native star rating.
- **`Tumbler`** (iOS-style picker wheel) is Quick-only.
- **`SelectorBar`** (modern segmented) was added to WinUI 3 in 1.4 — older cousin `Pivot` still exists.
- **`AdwSplitButton`** matches `SplitButton`; AppKit has no purpose-built split button.
- **`AutoSuggestBox`** is a richer search-with-suggestions primitive than `NSSearchField` or `GtkSearchEntry` (which are pure text inputs).

**Implication for SWAT.** The current SPI (`Button`, `Label`, `TextField`) is on solid ground; expanding to checkbox/radio/slider/progress/dropdown is unambiguous. Date/time pickers and color wells are platform-shaped enough that wrapping them is reasonable but the visual will vary substantially.

---

## 5. Collection / item-view widgets

| Control | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Flat list (virtualized) | `NSTableView` (1-column) | `ListView`, `ItemsView` (1.5+) | `GtkListView` (factory + `GListModel`), `GtkListBox` | `QListView` / `ListView` |
| Tabular | `NSTableView` (multi-column) | (none in box; `CommunityToolkit.WinUI.Controls.DataGrid`) | `GtkColumnView` | `QTableView` / `TableView` |
| Tree | `NSOutlineView` | `TreeView` | `GtkColumnView` + `GtkTreeListModel` (`GtkTreeView` deprecated) | `QTreeView` / `TreeView` (added 6.3) |
| Grid of items | `NSCollectionView` (compositional or flow layout) | `GridView`, `ItemsView` | `GtkGridView` | `QListView(IconMode)` / `GridView` |
| Drop-down list | `NSPopUpButton` | `ComboBox` (editable since 1.0) | `GtkDropDown` | `QComboBox` / `ComboBox` |
| Pages / carousel | `NSPageController` | `FlipView` + `PipsPager` | `AdwCarousel` | `SwipeView` / `PathView` |
| Source list / sidebar | `NSOutlineView` styled `.sourceList` | `NavigationView` (when `PaneDisplayMode=Left`) | `AdwNavigationSplitView` + `GtkListBox` | `QListView` styled |
| Miller columns | `NSBrowser` | (none) | (none) | `QColumnView` |
| Reveal / swipe / undo overlays | (build yourself) | `SwipeControl`, `PullToRefresh` | `AdwToastOverlay` | (build yourself) |
| Breadcrumb | `NSPathControl` | `BreadcrumbBar` | (none) | (build) |
| Annotated scrollbar | (none) | `AnnotatedScrollBar` (1.5+) | (none) | (none) |
| Semantic zoom (over-list overview) | (none) | `SemanticZoom` | (none) | (none) |

**Convergence.** Flat list, tree, tabular table, item grid, drop-down, and carousel/page exist in every framework — though all four took different design paths to get there.

**Architectural divergence.**
- **AppKit** uses `NSTableViewDiffableDataSource` and view-based row recycling (`makeView(withIdentifier:owner:)`).
- **WinUI 3** uses `ItemsSource` + `DataTemplate` with implicit virtualization (`ListView` is essentially a virtualized `ItemsControl`).
- **GTK 4** introduced an entirely new `GListModel` + `GtkListItemFactory` model. Crucially, `GtkTreeView`/`GtkListStore` and the cell-renderer stack are **deprecated since 4.10** — old GTK code that uses them is on borrowed time.
- **Qt** has the canonical Model/View architecture (`QAbstractItemModel`) plus convenience widgets (`QListWidget`, `QTableWidget`, `QTreeWidget`) that own their own models. Quick's `TreeView` is recent (6.3) — older code lacked it.

**Unique to one framework.**
- **AppKit `NSBrowser`** (Finder-style Miller columns); also Qt's `QColumnView`.
- **WinUI 3 `AnnotatedScrollBar`** (labeled tic marks for huge lists).
- **WinUI 3 `SemanticZoom`** (zoom out to A-Z index, zoom back in).
- **WinUI 3 `SwipeControl` / `PullToRefresh`** (mobile-style gestures on lists).
- **WinUI 3 `BreadcrumbBar`** and AppKit's `NSPathControl` — no GTK or Qt peer.
- **No `DataGrid` in WinUI 3 in box** — must use CommunityToolkit. Notable hole.

**Implication for SWAT.** Flat list and tree are universal and reasonable as core SPI. A `Table`/`Grid` abstraction is also viable everywhere except WinUI 3 (where you'd need to depend on or vendor a DataGrid). Breadcrumbs, semantic zoom, swipe-to-reveal — leave to platform specifics.

---

## 6. Container & navigation widgets

| Pattern | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Tabs (chrome) | `NSTabView` / `NSTabViewController` | `TabView` (Edge-style), `Pivot` (legacy), `MenuBar`-as-tabs | `AdwTabView` + `AdwTabBar` (`GtkNotebook` legacy) | `QTabWidget` / `TabBar`+`StackLayout` |
| Stacked / one-of-N | `NSTabViewController` (without tabs) | (use `Frame.Navigate`) | `GtkStack` / `AdwViewStack` | `QStackedWidget` / `StackLayout` |
| Disclosure / expander | `NSDisclosureView` (legacy), use `NSStackView` toggling | `Expander` | `GtkExpander`, `AdwExpanderRow` | `QToolBox` (accordion) / (compose) |
| Splitter | `NSSplitViewController` | `SplitView`, `NavigationView` | `AdwNavigationSplitView`, `AdwOverlaySplitView` | `QSplitter` / `SplitView` |
| Navigation history | `NSPageController` (limited) | `NavigationView` + `Frame.Navigate` | `AdwNavigationView` (push/pop) | `StackView` (Quick) — none in Widgets |
| Side / nav pane | source-list `NSOutlineView` in `NSSplitViewItem(.sidebar)` | `NavigationView` | `AdwNavigationSplitView` | (build with `QSplitter` + `QListView`) |
| Inspector pane (right side) | `NSSplitViewItem.Behavior.inspector` (macOS 14+) | `SplitView` (RightInline) | `AdwOverlaySplitView` (right side) | `QDockWidget` |
| Dockable / floatable panels | (no first-class) | (no first-class) | (no first-class) | **`QDockWidget`** (uniquely Qt) |
| MDI | (`NSWindow` per doc) | (separate `Window` instances) | (separate windows) | **`QMdiArea`** |
| Edge drawer (slide-in) | (deprecated `NSDrawer`) | (none — emulate) | (none in stock GTK; AdwOverlaySplitView is closest) | `Drawer` (Quick) |
| Toast (transient) | (build) | `InfoBar` (persistent), `TeachingTip` | `AdwToast` + `AdwToastOverlay` | (build) |
| In-app banner | (build) | `InfoBar` | `AdwBanner` | (build) |
| Scroll viewport | `NSScrollView` | `ScrollViewer`, `ScrollView` (newer) | `GtkScrolledWindow` | `QScrollArea` / `Flickable`+`ScrollView` |
| Frame / titled box | `NSBox` | `Border` + `TextBlock` | `GtkFrame` | `QGroupBox`, `QFrame` / `Frame`, `GroupBox` |
| Tooltip | `NSView.toolTip` | `ToolTipService.ToolTip` | `gtk_widget_set_tooltip_text()` | `QWidget.toolTip` / `ToolTip` |

**Convergence.** Tabs, stacked-views, splitters, scroll containers, expanders, and tooltips are universal.

**Divergence and unique offerings.**
- **`QDockWidget`** is Qt's most distinctive container — fully draggable, floatable, tabbable inspector panels around a `QMainWindow`. **No peer.**
- **`QMdiArea`** for MDI workspaces — rare in modern apps but still uniquely Qt.
- **`NavigationView`** (WinUI 3) and **`AdwNavigationSplitView`/`AdwViewSwitcher`** (libadwaita) are first-class adaptive shells with built-in responsive collapse — AppKit and Qt require manual composition.
- **`AdwTabView`** has unique features other frameworks lack: drag-out tabs to new windows, pinning, transferable between windows, indicator/loading state per tab. WinUI 3's `TabView` is similarly capable; AppKit and Qt are simpler.
- **`AdwToast`/`AdwBanner`** and WinUI 3's **`InfoBar`/`TeachingTip`** are polished in-app notification primitives. AppKit and Qt expect you to build your own.
- **AppKit's `NSSplitViewItem.Behavior.inspector`** (Sonoma 14+) names a right-side inspector pane explicitly; the others have shapes, not names.

**Implication for SWAT.** Tabs, splitter, expander, scroll are safe core. Adaptive sidebar/nav shells (`NavigationView`/`AdwNavigationSplitView`) are too platform-shaped to abstract — pick a least-common-denominator and let backends layer their own.

---

## 7. Menus & toolbars

| Surface | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Menu bar (top of screen vs. window) | **System-wide, screen-top** (`NSApp.mainMenu`) — no per-window menu bar | Per-window (`MenuBar`) | Per-window (`GtkPopoverMenuBar` driven by `GMenu`) | Per-window (`QMenuBar`); auto-promotes to system menu on macOS |
| Context menu | `NSView.menu` / `menu(for: NSEvent)` | `MenuFlyout`, `CommandBarFlyout` | `GtkPopoverMenu` | `QMenu` exec at point / `Menu` (Quick) |
| Programmatic menu model | `NSMenu`/`NSMenuItem` (imperative) | `MenuFlyout` (XAML) | **`GMenuModel`** (data, action-based — declarative) | `QAction` + `QMenu` |
| Toolbar | `NSToolbar` (highly system-integrated, customization sheet built-in) | `CommandBar` / `AppBar` | (Removed — use `GtkHeaderBar` / `AdwHeaderBar` conventions) | `QToolBar` / `ToolBar` |
| Status bar | (status bar in Mac apps is uncommon; `NSStatusBar` is the *menu-bar-extras* API) | (build) | (build) | `QStatusBar` / (build in Quick) |
| Touch bar | `NSTouchBar` (legacy hardware) | n/a | n/a | n/a |
| Keyboard shortcut object | `NSMenuItem.keyEquivalent` + `NSEvent` interception | `KeyboardAccelerator` | `GtkShortcutController` + `GtkShortcut` | `QShortcut` / `Action` |
| Standard command catalogue | (none — write your own) | **`StandardUICommand`** (30+ predefined commands w/ icons + accels) | (none — write your own) | (none — write your own) |

**Convergence.** Every framework has menu bars (somewhere), context menus, and keyboard shortcuts.

**Divergence — major.**
- **AppKit's menu bar lives at the screen top, not on each window.** Cross-platform abstractions must have a model where the menu bar is logically per-window but AppKit collapses it to the global slot. Qt does this automatically (`QMenuBar` on macOS auto-promotes); GTK and WinUI 3 do not exist on macOS at all.
- **GTK 4 uses `GMenuModel` — a data-only menu description bound to `GAction`s.** The other three are imperative. This makes GTK menus dynamically reflectable but more verbose.
- **GTK 4 removed `GtkToolbar` outright.** The replacement is "put buttons in a `GtkHeaderBar` or `AdwHeaderBar`." This is a notable cultural shift.
- **WinUI 3's `StandardUICommand`** is unique — a battery of pre-defined Cut/Copy/Paste/Save/Open/Share commands with icons, accelerators, and localized labels.
- **AppKit's `NSToolbar`** is the most system-integrated of the bunch, with built-in customization sheets and unified-window integration.
- **`QStatusBar`** is unique to Qt as a first-class control — the others build their own.

**Implication for SWAT.** A portable menu API has to design *for* the macOS global menu (one application menu bar, `App` menu, `File`, `Edit`, `View`, `Window`, `Help` conventions) and let it be applied per-window on Windows and Linux. Toolbars are simple buttons; nothing exotic is portable.

---

## 8. Dialogs

| Dialog | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Generic | `NSAlert` (modal/sheet) | `ContentDialog` | `GtkAlertDialog` (4.10+) / `AdwAlertDialog` | `QDialog` / `Dialog` |
| Open file | `NSOpenPanel` | `FileOpenPicker` (HWND interop) / `Microsoft.Windows.Storage.Pickers` (1.7+) | `GtkFileDialog` (4.10+) | `QFileDialog` / `FileDialog` |
| Save file | `NSSavePanel` | `FileSavePicker` | `GtkFileDialog` | `QFileDialog` |
| Pick folder | `NSOpenPanel` (canChooseDirectories) | `FolderPicker` (interop quirks pre-1.7) | `GtkFileDialog` | `QFileDialog::getExistingDirectory` |
| Color | `NSColorPanel` (shared singleton) | `ColorPicker` (control) — no separate dialog | `GtkColorDialog` | `QColorDialog` |
| Font | `NSFontPanel` (shared singleton) | (none) | `GtkFontDialog` | `QFontDialog` |
| Print / page setup | `NSPrintPanel` + `NSPrintInfo` | `PrintManager` (Win11 only currently) | `GtkPrintDialog` (4.14+) | `QPrintDialog` |
| Wizard | (none) | (none) | (none) | `QWizard` |
| Progress | (build) | (build) | (build) | `QProgressDialog` |
| Generic input prompt | (build with `NSAlert.accessoryView`) | (build) | (build) | `QInputDialog` |

**Convergence.** Alert, file open/save, folder picker, color, font, print all have peers. The set of canonical native dialogs is the most consistent surface area in this whole comparison.

**Divergence.**
- **AppKit's `NSColorPanel` and `NSFontPanel` are singletons** owned by the app — they receive change events through the responder chain (`changeColor:`, `changeFont:`). The others are normal dialogs.
- **GTK 4 deprecated all dialog *widgets*** in 4.10. New code uses dialog *objects* that are async-only and integrate with Flatpak portals.
- **WinUI 3 file pickers** are infamous for the `IInitializeWithWindow` HWND-marshaling footgun; this was finally fixed in WinAppSDK 1.7 with a new `Microsoft.Windows.Storage.Pickers` namespace that takes a `WindowId`. Old code is still everywhere.
- **`QWizard`** (multi-step wizard) is unique to Qt.
- **`QProgressDialog`** and **`QInputDialog`** are unique conveniences; the others want you to roll a `ContentDialog`/`NSAlert` with a custom accessory view.

**Implication for SWAT.** Alert + file open/save are universal and easy to expose. Color, font, and print are native everywhere but mappings to Qt/Windows controls require care. Wizards: ship as a SWAT-level abstraction, not a peer.

---

## 9. Graphics — drawing and 2D scene

| Capability | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Immediate-mode 2D | Core Graphics (`CGContext`) | Win2D (separate NuGet) | Cairo (still available) | `QPainter` |
| Retained scene / nodes | Core Animation (`CALayer`) | `Microsoft.UI.Composition` (Visual layer) | `GskRenderNode` (built into GTK 4) | Quick scene graph (`QSGNode`) |
| Vector shapes (declarative) | (build with paths) | `Microsoft.UI.Xaml.Shapes.{Rectangle,Ellipse,Line,Path}` | (Cairo + custom widget) | `QGraphicsView` / `Shape` (Quick) |
| Custom drawing widget | `NSView.draw(_:)`, `wantsLayer = true` | `Canvas` (XAML) / Win2D `CanvasControl` | `GtkDrawingArea` (Cairo) / custom widget snapshot | `QWidget.paintEvent` / `Canvas` (QML) / custom `QQuickItem` |
| GPU API integration | Metal (`MTKView`, `CAMetalLayer`) | Direct3D 11/12 via SwapChainPanel + `CompositionAPI` | `GtkGLArea` (OpenGL only) | RHI: Vulkan / Metal / D3D11/12 / OpenGL |
| Render backend | Core Animation / Metal | DirectX | GSK (Vulkan/NGL/Cairo) | RHI |
| 3D scene library | (build on Metal) | (build on D3D) | (none — drop to GL) | **`QtQuick3D`** |
| Particle system | `CAEmitterLayer` | (none in box) | (none) | `Particles` (Quick) |

**Convergence.** All four expose an immediate-mode 2D canvas and a retained-mode scene/composition layer.

**Divergence.**
- **GTK 4's GSK is a built-in retained scene graph** — the toolkit itself moved to GPU rendering with no extra dependency. Notable.
- **Win2D is a separate NuGet** and gives the most powerful immediate-mode story on Windows (image effects via Direct2D).
- **Qt has the broadest GPU coverage** through RHI: a single shader written for Quick can run on any backend, with `qsb` cross-compilation.
- **`QtQuick3D`** is the only first-class declarative 3D scene API in this comparison.
- **AppKit Core Animation** is uniquely tightly integrated with the windowing system: `CALayer` is what backs every layer-backed view.

**Implication for SWAT.** A simple Canvas API (draw paths, gradients, images, text) is universal. GPU/3D is not — surface only if SWAT explicitly includes a backend per-platform GPU integration story.

---

## 10. Media & web

| Capability | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Audio/video player widget | `AVPlayerView` (AVKit) | `MediaPlayerElement` (with `MediaTransportControls`) | `GtkVideo` driven by `GtkMediaStream` (GStreamer backend) | `QVideoWidget` / `VideoOutput` |
| Picture-in-picture | First-class (`AVKit`) | First-class (`MediaPlayerElement`) | (depends on backend) | (build) |
| Web view | `WKWebView` (WebKit) | `WebView2` (Edge Chromium) | `WebKitWebView` (separate `webkitgtk-6.0`) | `QWebEngineView`/`WebEngineView` (Chromium, separate module) |
| PDF view | `PDFView` (PDFKit) | (none in box) | (none — use `evince`/`poppler` libraries) | `PdfPageView`/`PdfMultiPageView` (`QtPDF`, 6.4+) |
| Lottie | (third-party) | `AnimatedVisualPlayer` + LottieGen | (third-party) | (third-party) |
| Camera capture | AVCaptureSession | `CameraCaptureUI` (1.7+) / `MediaCapture` | (PipeWire/GStreamer manual) | `QCamera`/`QMediaCaptureSession` |
| Charts | (third-party) | (CommunityToolkit) | (third-party) | `QtCharts` (commercial), `QtDataVisualization` |

**Convergence.** Every framework ships a video player, a web view, and an image control.

**Divergence.**
- **WebKit underlies AppKit**, **Edge Chromium underlies WinUI 3**, **WebKitGTK underlies GTK**, and **Chromium underlies Qt**. Different engines, different feature parity, different licensing implications.
- **AppKit `PDFKit` and Qt `QtPDF`** are the only first-class PDF renderers; WinUI 3 and GTK have no in-box PDF.
- **WinUI 3 `AnimatedVisualPlayer`** is the only first-class Lottie/codegen vector animation player.
- **`QtCharts`/`QtDataVisualization`** are uniquely cross-platform; the others rely on third-party.

**Implication for SWAT.** Video and web view are universal but require dragging in heavyweight engines on each backend. Defer until users ask.

---

## 11. Text — rich, attributed, complex script

| Aspect | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Rich-text widget | `NSTextView` (in `NSScrollView`) | `RichEditBox` | `GtkTextView` + `GtkTextBuffer` + `GtkTextTag` | `QTextEdit` / `TextEdit` |
| Attributed string | `NSAttributedString` (Foundation), `AttributedString` (Swift) | `Inlines` collection in `TextBlock` (`Run`/`Bold`/`Hyperlink`/`InlineUIContainer`) | `PangoAttrList` + `GtkTextTag` | `QTextDocument` + `QTextCursor` |
| HTML import | Yes (`NSAttributedString` HTML init) | (manual) | (manual) | Yes (`QTextDocument::setHtml`) |
| Markdown | (Swift `AttributedString`) | (manual) | (manual) | Yes (`QTextDocument::setMarkdown` since 5.14) |
| Layout engine | Text Kit 2 (`NSTextLayoutManager`) — modern; Text Kit 1 still default for some configurations | DirectWrite (DWriteCore) | Pango | HarfBuzz |
| Code editing extras | (build) | (build) | **`GtkSourceView`** (separate library) | `QSyntaxHighlighter` (basic) |
| RTL / complex script | Native (CoreText) | DirectWrite | Pango | HarfBuzz |

**Convergence.** All four ship a multi-line rich-text editor backed by a robust text-shaping engine. All can read attributed/styled text.

**Divergence.**
- **`GtkSourceView`** (separate GTK library) is the most fully-featured native code editor — syntax highlighting, snippets, brackets, completion. Used by GNOME apps like Builder. AppKit, WinUI, and Qt require third-party (or build-your-own).
- **AppKit's Text Kit 2** is the most sophisticated viewport-aware text layout engine but adoption is gradual.
- **`QTextDocument` accepts HTML and Markdown directly** — convenient for documentation views.

**Implication for SWAT.** Plain-text and basic styled-text are portable. Code editing is a bottomless pit; defer to a separate component.

---

## 12. Input & events

| Aspect | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Pointer model | `NSEvent` types per device | Unified `PointerRoutedEventArgs` (mouse/touch/pen/contact/pressure/tilt) | Event controllers on widgets | Unified `QPointerEvent`/`QEventPoint` (Qt 6) |
| Mouse hover / tracking | `NSTrackingArea` | `PointerEntered`/`Exited` | `GtkEventControllerMotion` | `enterEvent`/`leaveEvent` / `HoverHandler` |
| Keyboard accelerator | Menu item `keyEquivalent` | `KeyboardAccelerator` (with `ScopeOwner`) | `GtkShortcutController` + `GtkShortcut` | `QShortcut` / `Action` |
| Gesture recognizers | `NSGestureRecognizer` family | `ManipulationStarted/Delta/Completed`, `Tapped`, `Holding` | `GtkGesture*` family (Click/Drag/LongPress/Swipe/Zoom/Rotate/Pan) | `QGestureRecognizer` / `TapHandler`/`DragHandler`/`PinchHandler` |
| Routing model | Responder chain | XAML routed events (bubble + tunnel) | Event controllers on individual widgets — no chain | Per-widget virtual handlers + event filters |
| Force / pressure | `NSEvent.pressure`, Force Touch | `Pressure` on pointer | `GtkGestureStylus`, `GdkDevice` | `QPointerEvent` pressure |
| Tablet / stylus | `NSEvent.tabletPoint` / `tabletProximity` | (pressure only — no first-class ink in WinUI 3) | `GtkGestureStylus` | `QTabletEvent` |
| IME | `NSTextInputClient` | Built-in | `GtkIMContext` | `QInputMethod` |
| Drag-and-drop | `NSDraggingSource`/`Destination` + `NSPasteboard` | `DragStarting`/`Drop` on `UIElement` + `DataPackage` | `GtkDragSource`/`GtkDropTarget` controllers | `QDrag` + `QMimeData` |

**Convergence.** All four support unified pointer/touch/keyboard, accelerators, gestures, IME, and DnD.

**Divergence — major.**
- **AppKit's responder chain** is a unique routing strategy: events walk from the focused view up through superviews to the window to the application. WinUI 3 has bubbling/tunneling. Qt has per-object virtual handlers + filters. **GTK 4 has no chain at all** — controllers are local.
- **GTK 4's event controllers as composable objects** is the most modular model. You attach a `GtkGestureClick` to *any* widget; you can add and remove them at runtime.
- **AppKit `NSEvent.tabletPoint` and Force Touch** are uniquely deep stylus integration.
- **WinUI 3 has no first-class inking** — `InkCanvas` and `InkToolbar` from UWP have not been ported (notable hole).

**Implication for SWAT.** Click, hover, focus, key, drag-out and drag-in are all expressible via per-widget callbacks (the SWT/Swing pattern). Anything richer (responder chain, tunneling) is platform-specific.

---

## 13. Accessibility

| Concern | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| API surface | `NSAccessibility` (informal + formal protocol) | `AutomationProperties` + `AutomationPeer` | `GtkAccessible` interface (ATK is gone) | `QAccessible` / `QAccessibleInterface` |
| Underlying tech | NSAccessibility | UI Automation (UIA) | AT-SPI on Linux/BSD; bridges to NSAccessibility on macOS, UIA on Windows | UIA / NSAccessibility / AT-SPI / Android / iOS |
| Built-in widget conformance | Yes (must implement for custom views) | Yes | Yes | Yes |
| Live regions | (build) | `AutomationProperties.LiveSetting` + `AutomationNotificationKind` | `live` accessibility property | (build) |
| Inspector tool | Accessibility Inspector (Xcode) | Inspect.exe, Accessibility Insights | Accerciser | Accessible Inspector |

**Convergence.** All four expose role/name/description/value/state, integrate with platform AT, and ship inspector tools.

**Divergence.** Mostly cosmetic — different role enumerations and property names. GTK 4's `GtkAccessible` model is the cleanest of the four (GObject interface, role at construction, properties/relations/states orthogonal). WinUI 3's `AutomationPeer` is the most ceremony-heavy.

**Implication for SWAT.** A small set of common a11y properties (role, name, description, value, enabled/checked/selected) maps cleanly. Each backend translates.

---

## 14. Theming & appearance

| Capability | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Light / dark | `NSAppearance` (`.aqua`, `.darkAqua`) | Theme dictionaries (`Light`/`Dark`/`HighContrast`) | `AdwStyleManager.color-scheme` | `QPalette` / per-style |
| High contrast | Accessibility appearances | First-class (`HighContrast` dictionary) | (CSS-driven) | `QStyleHints` |
| System accent color | `NSColor.controlAccentColor` | `SystemAccentColor` | `@accent_color` (Adwaita) | (build) |
| Translucent material / blur | **`NSVisualEffectView`** (Vibrancy: titlebar, sidebar, popover, sheet, hudWindow, …) | **Mica** (`MicaBackdrop`), **Acrylic** (`DesktopAcrylicBackdrop`, `AcrylicBrush`) | (none in box) | (none in box) |
| Style language | (programmatic) | XAML styles + theme dictionaries + implicit styles | **CSS** (selectors, pseudo-classes, custom properties) | **Qt Style Sheets** (CSS-like) for Widgets; per-control attached props for Quick |
| Pluggable styles / look | `NSAppearance` only | Same Fluent style; Mica/Acrylic toggle | Adwaita stylesheet (or Adwaita-only on GNOME) | **Many**: Fusion, Material, Universal, Imagine, native macOS/Windows/iOS, FluentWinUI3 |
| Symbol font / icons | **SF Symbols** (5000+ glyphs, weighted, hierarchical/palette/multicolor) | **Segoe Fluent Icons** (Win11) / Segoe MDL2 Assets | Icon themes (freedesktop spec) + symbolic icons | `QIcon::fromTheme` (freedesktop) — provide your own theme on non-Linux |

**Convergence.** Light/dark, accent color, and an icon system exist on all four. Style customization is possible everywhere.

**Divergence — major.**
- **AppKit and WinUI 3 own translucent system materials** (Vibrancy / Mica / Acrylic). GTK and Qt have nothing equivalent in the box. This is the single biggest visual-fidelity gap when porting.
- **AppKit ships SF Symbols (5000+ glyphs).** Segoe Fluent Icons is the WinUI 3 analog. GTK relies on icon themes; Qt expects you to bring your own.
- **Qt is the only framework with truly pluggable controls styles.** Material, Universal, FluentWinUI3, and native macOS/iOS are all swappable at runtime via `QQuickStyle::setStyle`.
- **GTK CSS** is the cleanest declarative styling syntax of the four — but its scope is purely visual (no layout).

**Implication for SWAT.** A common theme abstraction over light/dark + accent + named tokens (text, link, error, warning) is portable. Vibrancy/Mica/SF Symbols are *not* portable; expose as opt-in capabilities or skip.

---

## 15. Animation

| Capability | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Property animation | `NSAnimationContext` + `view.animator()` proxy | `Storyboard` + `DoubleAnimation` etc. | (No core engine) — `AdwTimedAnimation` (libadwaita) | `QPropertyAnimation` / `Behavior on prop` (Quick) |
| Spring physics | `CASpringAnimation` | `SpringScalarNaturalMotionAnimation` (Composition) | `AdwSpringAnimation` (libadwaita) | `SpringAnimation` (Quick) |
| Implicit (auto-animate on prop change) | Layer implicit animations | `ImplicitAnimationCollection` (Composition) | (none) | `Behavior` (Quick) |
| Connected / shared element | (build) | `ConnectedAnimationService` | (build) | (build) |
| Lottie | (third-party) | `AnimatedVisualPlayer` + LottieGen codegen | (third-party) | (third-party) |
| Theme transitions | (build) | `EntranceThemeTransition`/`PaneThemeTransition`/etc. | `GtkRevealer`/`GtkStack` transitions | Quick `Transition` + `State` |
| Page navigation transition | (build) | `Frame.Navigate` + `NavigationTransitionInfo` | `AdwNavigationView` push/pop | `StackView` (Quick) |
| State machine | (build) | (none) | (none) | **`QStateMachine`** (now `QtStateMachine`/`QtScxml` module) |

**Convergence.** Property animation with easing curves exists everywhere.

**Divergence.**
- **GTK 4 core has no general-purpose animation engine.** Libadwaita filled this gap with `AdwTimedAnimation` / `AdwSpringAnimation`. Plain GTK 4 apps must compose manually.
- **WinUI 3 has the richest theme-transition library** out of the box (FadeIn, PopIn, EdgeUI, Reposition, Pane, etc.).
- **`ConnectedAnimationService`** (WinUI 3) for hero-element transitions across pages is unique.
- **`QStateMachine`** is unique to Qt — UML/SCXML state charts at the framework level.

**Implication for SWAT.** A simple tween API (animate property over duration with easing) is portable. Connected/shared-element transitions and state machines are platform extras.

---

## 16. Clipboard & drag-and-drop

| Capability | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| Clipboard handle | `NSPasteboard.general` | `Clipboard` (`Windows.ApplicationModel.DataTransfer`) | `GdkClipboard` (per-display) | `QClipboard` (`QGuiApplication::clipboard()`) |
| Multiple named pasteboards | Yes (`general`, `find`, `font`, `ruler`, `drag`) | No | Primary + clipboard (X11/Wayland) | Clipboard, mouse selection (X11), find buffer (macOS) |
| Type system | UTI strings | `DataPackage` formats (text, html, bitmap, storage items, custom) | `GdkContentFormats` + `GValue` | `QMimeData` (MIME types) |
| Drag source | `beginDraggingSession(with:event:source:)` | `CanDrag` + `DragStarting` event | `GtkDragSource` controller | `QDrag::exec()` / `Drag` attached property |
| Drop target | `registerForDraggedTypes` + `NSDraggingDestination` | `AllowDrop` + `Drop` event | `GtkDropTarget` controller | `dragEnterEvent`/`dropEvent` / `DropArea` |
| Promised file transfers | Yes (`fileContents` + provider blocks) | (build) | (build) | (build) |

**Convergence.** All four expose a typed clipboard and a typed drag-out / drag-in mechanism with at least text/URL/image/files.

**Divergence.**
- **AppKit's promised file transfers** (deliver bytes only when actually dropped) are uniquely first-class.
- **GTK has X11/Wayland's "primary selection"** as a separate clipboard — middle-click paste — which has no equivalent on Windows or macOS.

**Implication for SWAT.** Text + URL list + custom MIME-typed data covers 95% of real apps and maps cleanly to all four.

---

## 17. Notifications & system integration

| Capability | AppKit | WinUI 3 | GTK 4 | Qt 6 |
| --- | --- | --- | --- | --- |
| User notification | `UNUserNotificationCenter` | `AppNotificationManager` + `AppNotificationBuilder` | `GNotification` (via `GApplication`) | `QSystemTrayIcon::showMessage` (basic) |
| Push notification | `APNs` (separate stack) | `PushNotificationManager` (1.2+) | (none — via portal) | (none) |
| Status bar / tray icon | **`NSStatusItem`** (menu-bar extras) | **No first-party WinUI 3 control** — P/Invoke `Shell_NotifyIcon` or H.NotifyIcon.WinUI | **Removed in GTK 4** — must use `StatusNotifierItem` D-Bus or libayatana-appindicator | **`QSystemTrayIcon`** — fully cross-platform |
| Dock badge / taskbar badge | `NSDockTile` (`badgeLabel`, `contentView`) | `BadgeUpdateManager` (packaged only) | (none) | (none — overlay icon only on Windows) |
| Jump list | (none) | `Windows.UI.StartScreen.JumpList` (packaged) | (none) | (none) |
| Share sheet | `NSSharingService` / `NSSharingServicePicker` | `DataTransferManager` (HWND interop) | (none) | `QtAndroid` only |
| Open file / URL | `NSWorkspace.shared.open` | `Launcher.LaunchUriAsync` | `GAppInfo.launch_default_for_uri` | `QDesktopServices::openUrl` |
| Standard paths | Foundation `URL` system directories | `KnownFolders`, `ApplicationData` | `g_get_user_*_dir`, `XDG_*` | `QStandardPaths` |
| Spell checker (system) | `NSSpellChecker` | (build via Win API) | (none in box) | (none) |
| In-app find (system) | **`NSTextFinder`** (shared find bar across views) | (build) | (build) | (build) |

**Convergence.** OS notifications and standard paths are present in all four (in some form).

**Divergence — major.**
- **`QSystemTrayIcon`** is the only truly cross-platform tray-icon API in this set. AppKit has its own (`NSStatusItem`); GTK 4 *removed* its API; WinUI 3 never had one. This is by far the largest **API hole** for cross-platform menu-bar/tray apps.
- **`NSDockTile` and `BadgeUpdateManager`** are dock/taskbar badge surfaces; GTK and Qt have no equivalent. Apps that want badge counts must conditionally implement.
- **`NSSharingService`** is the only first-class system share-sheet integration.
- **`NSTextFinder`** (system find bar shared across all `NSTextView`/`NSTableView` instances) is unique.

**Implication for SWAT.** Notifications + open URL + standard paths are portable. Tray icon, dock badge, share sheet, and global hotkey are *the* surface where SWAT users will hit "this works on platform X but not Y" — design with capability-detection in mind.

---

## 18. Specialized & unique components per framework

The most useful part of this comparison: things that exist in *only one* of the four. Anything that is platform-specific should be exposed (if at all) via opt-in extension surfaces, not the SWAT core API.

### AppKit
- **`NSToolbar` with system customization sheet** — most polished toolbar story.
- **System window tabs** — `NSWindow.tabbingMode`.
- **Sheets** as window-modal child windows.
- **`NSVisualEffectView`** — the canonical translucent material API.
- **SF Symbols** — 5000+ glyphs, weight/scale/rendering modes.
- **`NSTextFinder`** — system find bar.
- **`NSSpellChecker`** — system spell-check service.
- **`NSSharingService`** — system share sheet.
- **`NSDocument`** + **`NSWindowRestoration`** — opinionated document app framework with state restoration.
- **`NSStatusItem`** — menu-bar extras.
- **Force Touch / `NSPressureConfiguration`** and the (legacy) **Touch Bar**.
- **`NSHapticFeedbackManager`** — trackpad haptics.
- **`NSPathControl`** — path breadcrumb.
- **`NSLevelIndicator`** — capacity/rating meter.
- **`NSBrowser`** — Miller-columns hierarchical browser.
- **Continuity Camera / Continuity Markup / Universal Clipboard / Handoff** via `NSPasteboard.general` and `NSUserActivity`.

### WinUI 3
- **`NavigationView`** — flagship adaptive pane+content shell with hierarchical menu.
- **`TabView`** — Edge-style document tabs with drag-out-to-new-window.
- **`StandardUICommand`** — pre-built command catalogue (Cut/Copy/Paste/Save/Open/Share/etc.).
- **Mica + Acrylic** backdrops.
- **`InfoBar` / `InfoBadge` / `TeachingTip`** — Fluent in-app messaging.
- **`PersonPicture`** — avatar control.
- **`AnnotatedScrollBar`** — labeled scrollbar marks.
- **`SemanticZoom`** — zoomed-out overview of a list.
- **`SwipeControl`** — swipe-to-reveal commands on list items.
- **`PullToRefresh`**.
- **`BreadcrumbBar`**.
- **`SelectorBar`** — modern segmented.
- **`NumberBox` with arithmetic expression evaluation**.
- **`AnimatedVisualPlayer`** — Lottie/codegen vector player.
- **`AnimatedIcon`** inside chevrons, etc.
- **`TwoPaneView`** — dual-screen-aware layout.
- **`ConnectedAnimationService`** — hero-element transitions.
- **WebView2** (Edge Chromium).
- **`MapControl`** (ported in 1.5) — first-party 2D/3D map control.

### GTK 4 + libadwaita

This section is the **most important reference for the SWAT Linux backend** — every libadwaita primitive listed below is on the SWAT roadmap, in priority order driven by application demand. SWAT's GTK backend is structurally a libadwaita backend that uses GTK 4 widgets where Adwaita has nothing more refined to offer.

- **`GMenuModel`** — declarative, action-based menu data.
- **CSS-based theming** — closest to web styling syntax.
- **`AdwBreakpoint`** — declarative responsive breakpoints with `setters` that change properties when active.
- **`AdwNavigationView` / `AdwNavigationSplitView` / `AdwOverlaySplitView`** — adaptive layouts that collapse on narrow widths.
- **`AdwViewSwitcher`** — adaptive switcher (icon+label pills wide, icon-only narrow, bar at bottom on mobile).
- **`AdwTabView` / `AdwTabBar` / `AdwTabOverview`** — full-featured tab UX with drag-out, pinning, transfer between windows.
- **`AdwClamp`** — width-cap container for readable line lengths.
- **`AdwToast` / `AdwToastOverlay`** — transient in-app notifications.
- **`AdwBanner`** — persistent in-window banner.
- **`AdwAvatar`** — circular avatar with initials fallback.
- **`AdwActionRow` / `AdwExpanderRow` / `AdwComboRow` / `AdwSpinRow` / `AdwSwitchRow` / `AdwEntryRow`** — preferences-style list rows.
- **`AdwAboutDialog` / `AdwPreferencesDialog`** — standardized GNOME app dialogs.
- **`AdwSpringAnimation`** with proper physics + velocity hand-off.
- **`GtkLevelBar`** with named offset thresholds.
- **`GtkSourceView`** (separate library) — full-featured code editor.
- **`GtkEmojiChooser`** + **`GtkShortcutsWindow`** — system patterns.
- **`GtkConstraintLayout`** with VFL parser.
- **`GtkPicture` vs. `GtkImage`** distinction (content vs. icon).

### Qt 6
- **`QDockWidget`** — floatable / dockable / tabbable inspector panels around a `QMainWindow`. **The most distinctive Qt feature.**
- **`QMdiArea`** — MDI workspace.
- **`QSystemTrayIcon`** — the only truly cross-platform tray icon.
- **`QWizard`** — multi-step wizard with field registration.
- **`QProgressDialog`** + **`QInputDialog`** — built-in single-prompt and progress dialogs.
- **`QStatusBar`** — first-class status bar widget.
- **`Tumbler`** (Quick) — iOS-style picker wheel.
- **`PathView`** (Quick) — items along an arbitrary `Path`.
- **`QStateMachine`** / `QtSCXML` — UML state charts.
- **`QtQuick3D`** — declarative 3D scene library.
- **`QtCharts` / `QtDataVisualization`** — first-party plotting and 3D charts.
- **`QtPDF`** — first-party PDF rendering (6.4+).
- **`Particles`** + **`ShaderEffect`** (Quick) — particle systems and post-processing shaders cross-compiled by `qsb`.
- **`QtSerialPort` / `QtBluetooth` / `QtNfc` / `QtPositioning` / `QtLocation`** — hardware/IoT integrations and Maps.
- **Multiple swappable Quick Controls styles** (Material, Universal, Imagine, FluentWinUI3, native macOS/iOS, Fusion).
- **QML language** — declarative property bindings, JavaScript expressions, AOT-compilable to C++ (`qmltc`).
- **Qt Designer / Qt Creator / Qt Design Studio** — first-class visual designers including a Figma bridge.

---

## 19. The intersection — a "safe portable core"

For SWAT to honestly claim native-feel cross-platform support across all four backends, the API surface should consider this set as the **safe core** — features that map directly to peers everywhere with minimal asymmetry:

**Foundation:** main-thread-only UI, posted invocations to UI thread, application lifecycle hooks (will-launch / did-launch / will-terminate).

**Windows:** create / show / close / focus a top-level window, multi-window, modal window, popover anchored to a view.

**Layout:** Row, Column, Grid, Splitter, Scroll, Overlay/Z-stack.

**Widgets:** Button, ToggleButton, Label, TextField (single + multi line), Password, Checkbox, Radio, Switch, Slider, Spinner/Number, ProgressBar, Spinner/Busy, ImageView, DropDown.

**Collections:** flat list (with virtualization), tree, drop-down, tabular table (with caveat that WinUI 3 needs CommunityToolkit), simple grid.

**Containers/navigation:** TabView, Stack, Expander, ScrollView, GroupBox/Frame, Tooltip.

**Menus:** menu bar (per-window with macOS auto-promote), context menu, keyboard accelerator. Menu items: text, icon, accelerator, enabled, checked, submenu, separator.

**Dialogs:** alert (with up to 3 buttons), file open/save, folder pick, color, font, print.

**Graphics:** Canvas with paths, gradients, transforms, text drawing, image drawing.

**Text:** rich-text editor, attributed string with bold/italic/underline/color/link, HTML import.

**Input:** click, double-click, hover-enter/leave, focus-in/out, key-down/up, drag-out, drag-in, accelerator.

**Accessibility:** role, name, description, value, enabled/checked/selected.

**Theming:** light/dark/system, accent color, named tokens (text, link, error, success, warning).

**Animation:** tween a property over duration with easing curve. (Spring on a best-effort basis — libadwaita & WinUI Composition & Quick `SpringAnimation` & `CASpringAnimation` all have it, just with different parameter shapes.)

**Clipboard / DnD:** text, URL list, image bytes, custom MIME-typed payload.

**System:** OS notification, open URL, get standard paths.

This set is roughly the SWT/Swing core plus modern essentials. It is also where the current SWAT API (`Window`, `Container`, `Row`, `Column`, `Button`, `Label`, `TextField`) is heading — and the catalog above is a solid extension target.

---

## 20. Asymmetries SWAT must explicitly decide

Not every feature in the safe core has identical semantics across backends. The following design questions arise from the comparison and can't be deferred:

1. **Menu bar location.** macOS has a screen-top global menu; the rest are per-window. The portable abstraction should be "logical menu bar per window" with macOS automatically promoting one of them. Qt does this; SWAT should follow.

2. **Sheet-style modal dialog vs. centered modal.** AppKit sheets are visually distinctive and beloved; the others lack them. Either model "alert" as "platform best-fit modal" (sheet on macOS, ContentDialog on WinUI 3, AdwAlertDialog on GTK, QDialog on Qt) or expose only the centered-modal LCD.

3. **CSD / titlebar customization.** GTK 4 effectively requires CSD; AppKit and WinUI 3 want extending into the titlebar; Qt delegates. Either expose "extend content into titlebar (where supported)" as a window flag or stay out of the titlebar entirely.

4. **Tree vs. tabular.** GTK 4 deprecated `GtkTreeView`; the modern path is `GtkColumnView` + `GtkTreeListModel`. A SWAT Tree API should map to the modern path on GTK and accept that older GTK code will look different.

5. **DataGrid hole on WinUI 3.** If SWAT wants to expose a tabular Table widget, the WinUI 3 backend will need to depend on `CommunityToolkit.WinUI.Controls.DataGrid` (or build one). Worth flagging in the SPI.

6. **Tray icon.** Only Qt has a unified API. AppKit has its own. WinUI 3 has none (must P/Invoke). GTK 4 *removed* it. SWAT should treat tray icon as a *capability*, not part of the core surface.

7. **Translucent / Mica / Vibrancy.** First-class on AppKit and WinUI 3, absent on GTK and Qt. Capability-detect.

8. **SF Symbols / Segoe Fluent Icons.** Different glyph sets, different naming, different licensing for redistribution. Either ship a SWAT-managed icon set or accept that icon names won't be portable.

9. **Wizard, ProgressDialog, InputDialog, MDI, Dock widgets.** Qt-only. Don't expose; build as SWAT-level abstractions on top of the safe core if needed.

10. **Animation primitives.** All four have property animation. Spring is everywhere except plain GTK. Connected/shared-element transitions are not portable.

11. **Web view, video, PDF, code editor.** Each backend ships a fundamentally different engine; expose only the smallest LCD (set URL / play video) and accept that depth-first features (JS bridge, captions, annotations) will differ.

12. **GTK 4 + libadwaita coupling.** GTK 4 alone is missing the modern responsive shell, the modern dialog flow, color-scheme tracking, and the animation engine. The SWAT GTK backend is effectively a libadwaita backend.

---

## 21. One-page summary

If you remember nothing else from this document, remember these statements:

- **SWAT targets three platforms only: macOS/AppKit, Windows/WinUI 3, and Linux/GTK 4 + libadwaita.** Qt 6 appears in this document only as a reference — the one mainstream toolkit that has independently solved cross-platform native UI, and therefore an indispensable design reference even though it is not itself a SWAT backend.
- **Buttons, labels, text inputs, checkboxes, radios, switches, sliders, progress, spinners, drop-downs, lists, trees, tabs, splitters, scrolls, alerts, file pickers, color/font dialogs, clipboard, drag-and-drop, light/dark theming, and OS notifications are portable across all three target backends** (and Qt confirms the shape). That set is large enough for most apps.
- **AppKit and WinUI 3 have rich proprietary materials** (Vibrancy, Mica, Acrylic, SF Symbols, Segoe Fluent Icons) and **system service integrations** (sheets, share sheets, dock badges, system tray on macOS) that have no direct peer on Linux. SWAT exposes these as opt-in platform hints, not as portable core API.
- **The Linux target is GTK 4 *plus* libadwaita, treated as a single product.** Bare GTK 4 lacks the modern responsive shell (`AdwApplicationWindow`, `AdwHeaderBar`, `AdwToolbarView`), the modern dialog flow (`AdwAlertDialog`, `AdwDialog`), color-scheme tracking (`AdwStyleManager`), and the spring animation engine. SWAT's GTK backend mandates libadwaita 1.5+ and uses Adwaita primitives anywhere libadwaita refines a GTK widget.
- **GTK 4 is in the middle of a major transition.** `GtkTreeView`, `GtkDialog` family, `GtkColorButton`, `GtkFontButton`, `GtkComboBox`, `GtkFileChooserButton`, plus the entire menu and toolbar widget hierarchy are deprecated or removed. Modern GTK 4 code uses `GListModel`-driven views (`GtkColumnView` + `GtkTreeListModel`), async dialog objects (`GtkAlertDialog`, `AdwAlertDialog`), `GMenuModel`, and libadwaita header bar / toolbar conventions. SWAT's GTK backend is built directly to the modern path.
- **WinUI 3 has notable gaps versus UWP**: no `InkCanvas`, no `DataGrid`, no first-class `NotifyIcon`, file-picker HWND-interop quirks (mostly fixed in 1.7), no XAML Designer in Visual Studio, larger footprint. Plan around them.
- **Qt confirms which features are portable.** Where Qt has a unified cross-platform API for something (clipboard, DnD, drag-out tabs, splitters, tray icon), it is strong evidence the feature *can* be expressed portably and informs the SWAT API shape. Where Qt has a feature with no peer in any of AppKit/WinUI/GTK (e.g. `QDockWidget`, `QMdiArea`, `QStateMachine`), SWAT does not expose it — building such things on top of the safe portable core is application work, not toolkit work.

The current SWAT SPI (`Peer`, `PeerFactory`, `WindowConfig`, `ButtonConfig`, `LabelConfig`, `TextFieldConfig`, `ContainerConfig`, plus `Orientation` and `UiLoop`) is well-shaped for this safe core. Extension targets in priority order:

1. Checkbox + Radio + Switch (universal, low risk).
2. Slider + ProgressBar + Spinner (universal, low risk).
3. DropDown / Selector (universal).
4. ScrollContainer (universal).
5. List (flat, virtualized) — universal but model design needs care: GTK's `GListModel` factory pattern is the modern target.
6. MenuBar + ContextMenu + KeyboardAccelerator — design once for the macOS global-menu reality.
7. Alert + FileOpen/Save dialogs.
8. Image (with simple stretch/aspect).

Beyond this, every framework starts to diverge enough that capability flags or backend-specific extension surfaces are the better tool.
