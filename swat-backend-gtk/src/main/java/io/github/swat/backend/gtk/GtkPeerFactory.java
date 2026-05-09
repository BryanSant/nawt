package io.github.swat.backend.gtk;

import io.github.swat.spi.ButtonConfig;
import io.github.swat.spi.ButtonPeer;
import io.github.swat.spi.CheckboxConfig;
import io.github.swat.spi.CheckboxPeer;
import io.github.swat.spi.ContainerConfig;
import io.github.swat.spi.ContainerPeer;
import io.github.swat.spi.DropDownConfig;
import io.github.swat.spi.DropDownPeer;
import io.github.swat.spi.ExpanderConfig;
import io.github.swat.spi.ExpanderPeer;
import io.github.swat.spi.FrameConfig;
import io.github.swat.spi.FramePeer;
import io.github.swat.spi.ScrollContainerConfig;
import io.github.swat.spi.ScrollContainerPeer;
import io.github.swat.spi.SplitterConfig;
import io.github.swat.spi.SplitterPeer;
import io.github.swat.spi.TabsConfig;
import io.github.swat.spi.TabsPeer;
import io.github.swat.spi.TreeConfig;
import io.github.swat.spi.TreePeer;
import io.github.swat.spi.ImageConfig;
import io.github.swat.spi.ImagePeer;
import io.github.swat.spi.CanvasConfig;
import io.github.swat.spi.CanvasPeer;
import io.github.swat.spi.LabelConfig;
import io.github.swat.spi.LabelPeer;
import io.github.swat.spi.ListViewConfig;
import io.github.swat.spi.ListViewPeer;
import io.github.swat.spi.ProgressBarConfig;
import io.github.swat.spi.ProgressBarPeer;
import io.github.swat.spi.RadioConfig;
import io.github.swat.spi.RadioPeer;
import io.github.swat.spi.SliderConfig;
import io.github.swat.spi.SliderPeer;
import io.github.swat.spi.SpinnerConfig;
import io.github.swat.spi.SpinnerPeer;
import io.github.swat.spi.SwitchConfig;
import io.github.swat.spi.SwitchPeer;
import io.github.swat.spi.MenuActionConfig;
import io.github.swat.spi.MenuActionPeer;
import io.github.swat.spi.MenuBarPeer;
import io.github.swat.spi.MenuConfig;
import io.github.swat.spi.MenuPeer;
import io.github.swat.spi.MenuSeparatorPeer;
import io.github.swat.spi.MessageDialogConfig;
import io.github.swat.spi.Peer;
import io.github.swat.spi.PeerFactory;
import io.github.swat.spi.TextFieldConfig;
import io.github.swat.spi.TextFieldPeer;
import io.github.swat.spi.UiLoop;
import io.github.swat.spi.WindowConfig;
import io.github.swat.spi.WindowPeer;

import java.util.concurrent.CompletableFuture;

public final class GtkPeerFactory implements PeerFactory {

    public GtkPeerFactory() {}

    @Override public String platformId() { return "gtk"; }

    private volatile io.github.swat.spi.Capabilities cachedCapabilities;

    @Override public io.github.swat.spi.Capabilities capabilities() {
        io.github.swat.spi.Capabilities c = cachedCapabilities;
        if (c != null) return c;
        synchronized (this) {
            if (cachedCapabilities == null) {
                java.util.List<io.github.swat.Capability> caps = new java.util.ArrayList<>();
                caps.add(io.github.swat.Capability.HEADER_BAR);
                caps.add(io.github.swat.Capability.TOAST_OVERLAY);
                caps.add(io.github.swat.Capability.DRAG_TEXT);
                if (probeStatusNotifierWatcher()) {
                    caps.add(io.github.swat.Capability.SYSTEM_TRAY);
                }
                cachedCapabilities = io.github.swat.spi.Capabilities.of(
                    caps.toArray(new io.github.swat.Capability[0]));
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
    @Override public TreePeer createTree(TreeConfig cfg) { return new GtkTreePeer(cfg); }
    @Override public ImagePeer createImage(ImageConfig cfg) { return new GtkImagePeer(cfg); }
    @Override public CanvasPeer createCanvas(CanvasConfig cfg) { return new GtkCanvasPeer(cfg); }
    @Override public io.github.swat.spi.HeaderBarPeer createHeaderBar(io.github.swat.spi.HeaderBarConfig cfg) {
        return new GtkHeaderBarPeer(cfg);
    }
    @Override public io.github.swat.spi.SystemTrayPeer createSystemTray(io.github.swat.spi.SystemTrayConfig cfg) {
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

    @Override public CompletableFuture<String> showFileOpenDialog(io.github.swat.spi.FileDialogConfig cfg) {
        return GtkFileDialog.showOpen(cfg);
    }
    @Override public CompletableFuture<String> showFileSaveDialog(io.github.swat.spi.FileDialogConfig cfg) {
        return GtkFileDialog.showSave(cfg);
    }
    @Override public CompletableFuture<String> showFolderDialog(io.github.swat.spi.FolderDialogConfig cfg) {
        return GtkFileDialog.showFolder(cfg);
    }
}
