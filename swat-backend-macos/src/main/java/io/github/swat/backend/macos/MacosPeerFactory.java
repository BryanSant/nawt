package io.github.swat.backend.macos;

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
import io.github.swat.spi.GridConfig;
import io.github.swat.spi.GridPeer;
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

import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;

public final class MacosPeerFactory implements PeerFactory {

    public MacosPeerFactory() {}

    @Override public String platformId() { return "macos"; }

    @Override
    public void setApplicationName(String name) {
        if (name == null) return;
        try (var pool = AutoreleasePool.push()) {
            // Updates `top`/`ps` and -[NSProcessInfo processName].
            MemorySegment processInfo = Objc.sendPtr(Objc.cls("NSProcessInfo"), Objc.sel("processInfo"));
            Objc.sendVoid(processInfo, Objc.sel("setProcessName:"), NSString.from(name));

            // Drive the bold app-menu title by poking CFBundleName into the
            // main bundle's info dictionary, exactly as OpenJDK's launcher
            // does for -Xdock:name=. For a non-bundled process the info
            // dictionary is an internal NSMutableDictionary, so
            // -[NSMutableDictionary setObject:forKey:] works. Must run
            // before NSApp.sharedApplication caches localizedName.
            MemorySegment bundle = Objc.sendPtr(Objc.cls("NSBundle"), Objc.sel("mainBundle"));
            MemorySegment infoDict = Objc.sendPtr(bundle, Objc.sel("infoDictionary"));
            if (infoDict != null && infoDict.address() != 0) {
                MemorySegment key = NSString.from("CFBundleName");
                MemorySegment value = NSString.from(name);
                try {
                    Objc.msgSend(java.lang.foreign.FunctionDescriptor.ofVoid(
                            Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                        .invoke(infoDict, Objc.sel("setObject:forKey:"), value, key);
                } catch (Throwable ignored) {
                    // If the info dictionary is genuinely immutable (an
                    // already-bundled app), silently skip — the bundle's
                    // CFBundleName from Info.plist wins anyway.
                }
            }
        }
    }

    @Override public io.github.swat.spi.Capabilities capabilities() {
        return io.github.swat.spi.Capabilities.of(
            io.github.swat.Capability.HEADER_BAR,
            io.github.swat.Capability.TOAST_OVERLAY,
            io.github.swat.Capability.DRAG_TEXT,
            io.github.swat.Capability.GLOBAL_MENU_BAR,
            io.github.swat.Capability.SYSTEM_TRAY);
    }

    @Override
    public boolean supports() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") || os.contains("darwin");
    }

    @Override public UiLoop createUiLoop() { return new MacosUiLoop(); }

    @Override public WindowPeer createWindow(WindowConfig cfg) { return new MacosWindowPeer(cfg); }
    @Override public LabelPeer createLabel(LabelConfig cfg) { return new MacosLabelPeer(cfg); }
    @Override public ButtonPeer createButton(ButtonConfig cfg) { return new MacosButtonPeer(cfg); }
    @Override public TextFieldPeer createTextField(TextFieldConfig cfg) { return new MacosTextFieldPeer(cfg); }
    @Override public ContainerPeer createContainer(ContainerConfig cfg) { return new MacosContainerPeer(cfg); }
    @Override public ListViewPeer createListView(ListViewConfig cfg) { return new MacosListViewPeer(cfg); }
    @Override public CheckboxPeer createCheckbox(CheckboxConfig cfg) { return new MacosCheckboxPeer(cfg); }
    @Override public SwitchPeer createSwitch(SwitchConfig cfg) { return new MacosSwitchPeer(cfg); }
    @Override public RadioPeer createRadio(RadioConfig cfg) { return new MacosRadioPeer(cfg); }
    @Override public SliderPeer createSlider(SliderConfig cfg) { return new MacosSliderPeer(cfg); }
    @Override public ProgressBarPeer createProgressBar(ProgressBarConfig cfg) { return new MacosProgressBarPeer(cfg); }
    @Override public SpinnerPeer createSpinner(SpinnerConfig cfg) { return new MacosSpinnerPeer(cfg); }
    @Override public DropDownPeer createDropDown(DropDownConfig cfg) { return new MacosDropDownPeer(cfg); }
    @Override public FramePeer createFrame(FrameConfig cfg) { return new MacosFramePeer(cfg); }
    @Override public ScrollContainerPeer createScrollContainer(ScrollContainerConfig cfg) { return new MacosScrollContainerPeer(cfg); }
    @Override public TabsPeer createTabs(TabsConfig cfg) { return new MacosTabsPeer(cfg); }
    @Override public SplitterPeer createSplitter(SplitterConfig cfg) { return new MacosSplitterPeer(cfg); }
    @Override public ExpanderPeer createExpander(ExpanderConfig cfg) { return new MacosExpanderPeer(cfg); }
    @Override public GridPeer createGrid(GridConfig cfg) { return new MacosGridPeer(cfg); }
    @Override public TreePeer createTree(TreeConfig cfg) { return new MacosTreePeer(cfg); }
    @Override public ImagePeer createImage(ImageConfig cfg) { return new MacosImagePeer(cfg); }
    @Override public CanvasPeer createCanvas(CanvasConfig cfg) { return new MacosCanvasPeer(cfg); }
    @Override public io.github.swat.spi.HeaderBarPeer createHeaderBar(io.github.swat.spi.HeaderBarConfig cfg) {
        return new MacosHeaderBarPeer(cfg);
    }
    @Override public io.github.swat.spi.SystemTrayPeer createSystemTray(io.github.swat.spi.SystemTrayConfig cfg) {
        return new MacosSystemTrayPeer(cfg);
    }

    @Override
    public void setTooltip(Peer peer, String text) {
        MemorySegment view = MacosContainerPeer.peerView(peer);
        MemorySegment ns = (text == null || text.isEmpty()) ? Objc.NIL : NSString.from(text);
        Objc.sendVoid(view, Objc.sel("setToolTip:"), ns);
    }

    @Override
    public void openUrl(String url) {
        if (url == null || url.isEmpty()) return;
        MemorySegment nsUrl = Objc.sendPtr(
            Objc.cls("NSURL"), Objc.sel("URLWithString:"), NSString.from(url));
        if (nsUrl.address() == 0) return;
        MemorySegment ws = Objc.sendPtr(Objc.cls("NSWorkspace"), Objc.sel("sharedWorkspace"));
        // -[NSWorkspace openURL:] returns BOOL; we don't care about the result
        try {
            java.lang.foreign.FunctionDescriptor fd = java.lang.foreign.FunctionDescriptor.of(
                Objc.BOOL, Objc.PTR, Objc.PTR, Objc.PTR);
            Objc.msgSend(fd).invoke(ws, Objc.sel("openURL:"), nsUrl);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override
    public String clipboardText() {
        MemorySegment pb = Objc.sendPtr(Objc.cls("NSPasteboard"), Objc.sel("generalPasteboard"));
        MemorySegment type = NSString.from("public.utf8-plain-text");
        MemorySegment ns = Objc.sendPtr(pb, Objc.sel("stringForType:"), type);
        if (ns.address() == 0) return "";
        String s = NSString.toJava(ns);
        return s == null ? "" : s;
    }

    @Override
    public void setClipboardText(String text) {
        MemorySegment pb = Objc.sendPtr(Objc.cls("NSPasteboard"), Objc.sel("generalPasteboard"));
        Objc.sendVoid(pb, Objc.sel("clearContents"));
        MemorySegment type = NSString.from("public.utf8-plain-text");
        MemorySegment ns = NSString.from(text == null ? "" : text);
        try {
            java.lang.foreign.FunctionDescriptor fd = java.lang.foreign.FunctionDescriptor.of(
                Objc.BOOL, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR);
            Objc.msgSend(fd).invoke(pb, Objc.sel("setString:forType:"), ns, type);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override
    public void notify(String title, String body) {
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSUserNotification"));
        MemorySegment n = Objc.sendPtr(alloc, Objc.sel("init"));
        Objc.sendVoid(n, Objc.sel("setTitle:"), NSString.from(title == null ? "" : title));
        Objc.sendVoid(n, Objc.sel("setInformativeText:"), NSString.from(body == null ? "" : body));
        MemorySegment center = Objc.sendPtr(
            Objc.cls("NSUserNotificationCenter"), Objc.sel("defaultUserNotificationCenter"));
        Objc.sendVoid(center, Objc.sel("deliverNotification:"), n);
        Objc.sendVoid(n, Objc.sel("release"));
    }

    @Override
    public void setDragSource(Peer peer, java.util.function.Supplier<String> textProvider) {
        MacosDnD.setDragSource(MacosContainerPeer.peerView(peer), textProvider);
    }

    @Override
    public void setDropTarget(Peer peer, java.util.function.Consumer<String> textHandler) {
        MacosDnD.setDropTarget(MacosContainerPeer.peerView(peer), textHandler);
    }

    @Override public MenuActionPeer createMenuAction(MenuActionConfig cfg) { return new MacosMenuActionPeer(cfg); }
    @Override public MenuSeparatorPeer createMenuSeparator() { return new MacosMenuSeparatorPeer(); }
    @Override public MenuPeer createMenu(MenuConfig cfg) { return new MacosMenuPeer(cfg); }
    @Override public MenuBarPeer createMenuBar() { return new MacosMenuBarPeer(); }

    @Override
    public CompletableFuture<Integer> showMessageDialog(MessageDialogConfig cfg) {
        return MacosMessageDialog.show(cfg);
    }

    @Override public CompletableFuture<String> showFileOpenDialog(io.github.swat.spi.FileDialogConfig cfg) {
        return MacosFileDialog.showOpen(cfg);
    }
    @Override public CompletableFuture<String> showFileSaveDialog(io.github.swat.spi.FileDialogConfig cfg) {
        return MacosFileDialog.showSave(cfg);
    }
    @Override public CompletableFuture<String> showFolderDialog(io.github.swat.spi.FolderDialogConfig cfg) {
        return MacosFileDialog.showFolder(cfg);
    }
}
