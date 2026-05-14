package cc.nawt.backend.macos;

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

import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;

public final class MacosPeerFactory implements PeerFactory {

    public MacosPeerFactory() {}

    @Override public String platformId() { return "macos"; }

    @Override
    public void setAboutHandler(Runnable handler) {
        // The app menu is installed at MacosUiLoop.bootstrap time and the
        // About item already targets Delegates.APP_ABOUT_HANDLER via a
        // shared delegate, so all we need to do here is swap the handler.
        Delegates.APP_ABOUT_HANDLER = handler;
    }

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

    @Override public cc.nawt.spi.Capabilities capabilities() {
        return cc.nawt.spi.Capabilities.of(
            cc.nawt.Capability.HEADER_BAR,
            cc.nawt.Capability.TOAST_OVERLAY,
            cc.nawt.Capability.DRAG_TEXT,
            cc.nawt.Capability.GLOBAL_MENU_BAR,
            cc.nawt.Capability.SYSTEM_TRAY);
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
    @Override public cc.nawt.spi.HeaderBarPeer createHeaderBar(cc.nawt.spi.HeaderBarConfig cfg) {
        return new MacosHeaderBarPeer(cfg);
    }
    @Override public cc.nawt.spi.SystemTrayPeer createSystemTray(cc.nawt.spi.SystemTrayConfig cfg) {
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

    @Override public CompletableFuture<String> showFileOpenDialog(cc.nawt.spi.FileDialogConfig cfg) {
        return MacosFileDialog.showOpen(cfg);
    }
    @Override public CompletableFuture<String> showFileSaveDialog(cc.nawt.spi.FileDialogConfig cfg) {
        return MacosFileDialog.showSave(cfg);
    }
    @Override public CompletableFuture<String> showFolderDialog(cc.nawt.spi.FolderDialogConfig cfg) {
        return MacosFileDialog.showFolder(cfg);
    }
}
