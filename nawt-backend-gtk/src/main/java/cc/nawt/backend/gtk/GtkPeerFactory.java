package cc.nawt.backend.gtk;

import cc.nawt.spi.ButtonConfig;
import cc.nawt.spi.ButtonPeer;
import cc.nawt.spi.CheckboxConfig;
import cc.nawt.spi.CheckboxPeer;
import cc.nawt.spi.ContainerConfig;
import cc.nawt.spi.ContainerPeer;
import cc.nawt.spi.DropDownConfig;
import cc.nawt.spi.DropDownPeer;
import cc.nawt.spi.ExpanderConfig;
import cc.nawt.spi.ExpanderPeer;
import cc.nawt.spi.FrameConfig;
import cc.nawt.spi.FramePeer;
import cc.nawt.spi.GridConfig;
import cc.nawt.spi.GridPeer;
import cc.nawt.spi.ScrollContainerConfig;
import cc.nawt.spi.ScrollContainerPeer;
import cc.nawt.spi.SplitterConfig;
import cc.nawt.spi.SplitterPeer;
import cc.nawt.spi.TabsConfig;
import cc.nawt.spi.TabsPeer;
import cc.nawt.spi.TreeConfig;
import cc.nawt.spi.TreePeer;
import cc.nawt.spi.ImageConfig;
import cc.nawt.spi.ImagePeer;
import cc.nawt.spi.CanvasConfig;
import cc.nawt.spi.CanvasPeer;
import cc.nawt.spi.LabelConfig;
import cc.nawt.spi.LabelPeer;
import cc.nawt.spi.ListViewConfig;
import cc.nawt.spi.ListViewPeer;
import cc.nawt.spi.ProgressBarConfig;
import cc.nawt.spi.ProgressBarPeer;
import cc.nawt.spi.RadioConfig;
import cc.nawt.spi.RadioPeer;
import cc.nawt.spi.SliderConfig;
import cc.nawt.spi.SliderPeer;
import cc.nawt.spi.SpinnerConfig;
import cc.nawt.spi.SpinnerPeer;
import cc.nawt.spi.SwitchConfig;
import cc.nawt.spi.SwitchPeer;
import cc.nawt.spi.MenuActionConfig;
import cc.nawt.spi.MenuActionPeer;
import cc.nawt.spi.MenuBarPeer;
import cc.nawt.spi.MenuConfig;
import cc.nawt.spi.MenuPeer;
import cc.nawt.spi.MenuSeparatorPeer;
import cc.nawt.spi.MessageDialogConfig;
import cc.nawt.spi.Peer;
import cc.nawt.spi.PeerFactory;
import cc.nawt.spi.TextFieldConfig;
import cc.nawt.spi.TextFieldPeer;
import cc.nawt.spi.UiLoop;
import cc.nawt.spi.WindowConfig;
import cc.nawt.spi.WindowPeer;

import java.util.concurrent.CompletableFuture;

public final class GtkPeerFactory implements PeerFactory {

    public GtkPeerFactory() {}

    @Override public String platformId() { return "gtk"; }

    @Override
    public void setApplicationName(String name) {
        if (name == null) return;
        Gtk.g_set_application_name(name);
    }

    private volatile Runnable aboutHandler;

    @Override
    public void setAboutHandler(Runnable handler) {
        this.aboutHandler = handler;
    }

    /** Latest About handler registered via {@link cc.nawt.Toolkit#onAbout},
     *  or {@code null} if none. Read at {@link GtkHeaderBarPeer} construction time
     *  to decide whether to auto-append an About item to a burger menu. */
    Runnable aboutHandler() { return aboutHandler; }

    private volatile cc.nawt.spi.Capabilities cachedCapabilities;

    @Override public cc.nawt.spi.Capabilities capabilities() {
        cc.nawt.spi.Capabilities c = cachedCapabilities;
        if (c != null) return c;
        synchronized (this) {
            if (cachedCapabilities == null) {
                java.util.List<cc.nawt.Capability> caps = new java.util.ArrayList<>();
                caps.add(cc.nawt.Capability.HEADER_BAR);
                caps.add(cc.nawt.Capability.HEADER_BAR_MENU);
                caps.add(cc.nawt.Capability.TOAST_OVERLAY);
                caps.add(cc.nawt.Capability.DRAG_TEXT);
                if (probeStatusNotifierWatcher()) {
                    caps.add(cc.nawt.Capability.SYSTEM_TRAY);
                }
                cachedCapabilities = cc.nawt.spi.Capabilities.of(
                    caps.toArray(new cc.nawt.Capability[0]));
            }
            return cachedCapabilities;
        }
    }

    /** Open a session-bus connection and ask {@code org.freedesktop.DBus} whether
     *  {@code org.kde.StatusNotifierWatcher} has an owner — true on KDE always,
     *  on GNOME with the AppIndicator extension, on Cinnamon, etc. */
    private static boolean probeStatusNotifierWatcher() {
        try {
            var bus = GDBus.g_bus_get_sync(GDBus.G_BUS_TYPE_SESSION);
            if (bus == null || bus.address() == 0) return false;
            try (var arena = java.lang.foreign.Arena.ofConfined()) {
                var param = GVariant.refSink(GVariant.newTuple(
                    arena, GVariant.newString("org.kde.StatusNotifierWatcher")));
                try {
                    var reply = GDBus.g_dbus_connection_call_sync(
                        bus,
                        "org.freedesktop.DBus", "/org/freedesktop/DBus",
                        "org.freedesktop.DBus", "NameHasOwner",
                        param, java.lang.foreign.MemorySegment.NULL,
                        GDBus.G_DBUS_CALL_FLAGS_NONE, 1000);
                    if (reply == null || reply.address() == 0) return false;
                    try {
                        var child = GVariant.getChildValue(reply, 0);
                        try { return GVariant.getBoolean(child); }
                        finally { GVariant.unref(child); }
                    } finally { GVariant.unref(reply); }
                } finally { GVariant.unref(param); }
            } finally { Gtk.g_object_unref(bus); }
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean supports() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!(os.contains("linux") || os.contains("freebsd"))) return false;
        if (!Gtk.available()) return false;
        // libadwaita is required, not optional — see README.md. Probe directly
        // here without touching the Adw class (whose static initializer would
        // throw on a host without libadwaita).
        try {
            return java.lang.foreign.SymbolLookup
                .libraryLookup("libadwaita-1.so.0", Gtk.GLOBAL)
                .find("adw_init").isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public UiLoop createUiLoop() { return new GtkUiLoop(); }

    @Override public WindowPeer createWindow(WindowConfig cfg) { return new GtkWindowPeer(cfg); }
    @Override public LabelPeer createLabel(LabelConfig cfg) { return new GtkLabelPeer(cfg); }
    @Override public ButtonPeer createButton(ButtonConfig cfg) { return new GtkButtonPeer(cfg); }
    @Override public TextFieldPeer createTextField(TextFieldConfig cfg) { return new GtkTextFieldPeer(cfg); }
    @Override public ContainerPeer createContainer(ContainerConfig cfg) { return new GtkContainerPeer(cfg); }
    @Override public ListViewPeer createListView(ListViewConfig cfg) { return new GtkListViewPeer(cfg); }
    @Override public CheckboxPeer createCheckbox(CheckboxConfig cfg) { return new GtkCheckboxPeer(cfg); }
    @Override public SwitchPeer createSwitch(SwitchConfig cfg) { return new GtkSwitchPeer(cfg); }
    @Override public RadioPeer createRadio(RadioConfig cfg) { return new GtkRadioPeer(cfg); }
    @Override public SliderPeer createSlider(SliderConfig cfg) { return new GtkSliderPeer(cfg); }
    @Override public ProgressBarPeer createProgressBar(ProgressBarConfig cfg) { return new GtkProgressBarPeer(cfg); }
    @Override public SpinnerPeer createSpinner(SpinnerConfig cfg) { return new GtkSpinnerPeer(cfg); }
    @Override public DropDownPeer createDropDown(DropDownConfig cfg) { return new GtkDropDownPeer(cfg); }
    @Override public FramePeer createFrame(FrameConfig cfg) { return new GtkFramePeer(cfg); }
    @Override public ScrollContainerPeer createScrollContainer(ScrollContainerConfig cfg) { return new GtkScrollContainerPeer(cfg); }
    @Override public TabsPeer createTabs(TabsConfig cfg) { return new GtkTabsPeer(cfg); }
    @Override public SplitterPeer createSplitter(SplitterConfig cfg) { return new GtkSplitterPeer(cfg); }
    @Override public ExpanderPeer createExpander(ExpanderConfig cfg) { return new GtkExpanderPeer(cfg); }
    @Override public GridPeer createGrid(GridConfig cfg) { return new GtkGridPeer(cfg); }
    @Override public TreePeer createTree(TreeConfig cfg) { return new GtkTreePeer(cfg); }
    @Override public ImagePeer createImage(ImageConfig cfg) { return new GtkImagePeer(cfg); }
    @Override public CanvasPeer createCanvas(CanvasConfig cfg) { return new GtkCanvasPeer(cfg); }
    @Override public cc.nawt.spi.HeaderBarPeer createHeaderBar(cc.nawt.spi.HeaderBarConfig cfg) {
        return new GtkHeaderBarPeer(cfg);
    }
    @Override public cc.nawt.spi.SystemTrayPeer createSystemTray(cc.nawt.spi.SystemTrayConfig cfg) {
        return new GtkSystemTrayPeer(cfg);
    }

    @Override
    public void setTooltip(Peer peer, String text) {
        Gtk.gtk_widget_set_tooltip_text(GtkContainerPeer.peerWidget(peer), text);
    }

    @Override
    public void openUrl(String url) {
        if (url == null || url.isEmpty()) return;
        try { Gtk.g_app_info_launch_default_for_uri(url); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    @Override
    public String clipboardText() {
        var display = Gtk.gdk_display_get_default();
        if (display == null || display.address() == 0) return "";
        var cb = Gtk.gdk_display_get_clipboard(display);
        if (cb == null || cb.address() == 0) return "";
        return GtkClipboardRead.read(cb);
    }

    @Override
    public void setClipboardText(String text) {
        var display = Gtk.gdk_display_get_default();
        if (display == null || display.address() == 0) return;
        var cb = Gtk.gdk_display_get_clipboard(display);
        if (cb == null || cb.address() == 0) return;
        Gtk.gdk_clipboard_set_text(cb, text == null ? "" : text);
    }

    @Override
    public void notify(String title, String body) { Notify.send(title, body); }

    @Override
    public void setDragSource(Peer peer, java.util.function.Supplier<String> textProvider) {
        GtkDnD.setDragSource(GtkContainerPeer.peerWidget(peer), textProvider);
    }

    @Override
    public void setDropTarget(Peer peer, java.util.function.Consumer<String> textHandler) {
        GtkDnD.setDropTarget(GtkContainerPeer.peerWidget(peer), textHandler);
    }

    @Override public MenuActionPeer createMenuAction(MenuActionConfig cfg) { return new GtkMenuActionPeer(cfg); }
    @Override public MenuSeparatorPeer createMenuSeparator() { return new GtkMenuSeparatorPeer(); }
    @Override public MenuPeer createMenu(MenuConfig cfg) { return new GtkMenuPeer(cfg); }
    @Override public MenuBarPeer createMenuBar() { return new GtkMenuBarPeer(); }

    @Override
    public CompletableFuture<Integer> showMessageDialog(MessageDialogConfig cfg) {
        return GtkMessageDialog.show(cfg);
    }

    @Override public CompletableFuture<String> showFileOpenDialog(cc.nawt.spi.FileDialogConfig cfg) {
        return GtkFileDialog.showOpen(cfg);
    }
    @Override public CompletableFuture<String> showFileSaveDialog(cc.nawt.spi.FileDialogConfig cfg) {
        return GtkFileDialog.showSave(cfg);
    }
    @Override public CompletableFuture<String> showFolderDialog(cc.nawt.spi.FolderDialogConfig cfg) {
        return GtkFileDialog.showFolder(cfg);
    }
}
