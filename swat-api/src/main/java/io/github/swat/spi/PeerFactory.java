package io.github.swat.spi;

import java.util.concurrent.CompletableFuture;

/**
 * SPI implemented by each platform backend (macOS/AppKit, GTK4, etc.).
 * Discovered via {@link java.util.ServiceLoader}; selected by
 * {@code Toolkit.detect()}.
 */
public interface PeerFactory {

    /** Stable identifier for the backend, e.g. {@code "macos"}, {@code "gtk"}. */
    String platformId();

    /**
     * Set the application name as shown by the host (macOS application menu's
     * bold title, GTK process / notification name, etc.). Best called before
     * the first window is shown, but most backends accept dynamic updates.
     * Default no-op for backends that don't have a concept of an app name.
     */
    default void setApplicationName(String name) {}

    /**
     * Platform-meaningful capabilities this backend supports. Default empty —
     * backends override to declare what they opt into. Callers query via
     * {@code Toolkit.supports(Capability)}.
     */
    default Capabilities capabilities() { return Capabilities.none(); }

    /**
     * True if this backend can run on the current host. May probe for native
     * libraries; must not perform irreversible initialization.
     */
    boolean supports();

    /** Create the platform's UI loop. Called once per launch. */
    UiLoop createUiLoop();

    WindowPeer createWindow(WindowConfig config);

    LabelPeer createLabel(LabelConfig config);

    ButtonPeer createButton(ButtonConfig config);

    TextFieldPeer createTextField(TextFieldConfig config);

    ContainerPeer createContainer(ContainerConfig config);

    ListViewPeer createListView(ListViewConfig config);

    CheckboxPeer createCheckbox(CheckboxConfig config);

    SwitchPeer createSwitch(SwitchConfig config);

    RadioPeer createRadio(RadioConfig config);

    SliderPeer createSlider(SliderConfig config);

    ProgressBarPeer createProgressBar(ProgressBarConfig config);

    SpinnerPeer createSpinner(SpinnerConfig config);

    DropDownPeer createDropDown(DropDownConfig config);

    FramePeer createFrame(FrameConfig config);

    ScrollContainerPeer createScrollContainer(ScrollContainerConfig config);

    TabsPeer createTabs(TabsConfig config);

    SplitterPeer createSplitter(SplitterConfig config);

    ExpanderPeer createExpander(ExpanderConfig config);

    GridPeer createGrid(GridConfig config);

    TreePeer createTree(TreeConfig config);

    ImagePeer createImage(ImageConfig config);

    CanvasPeer createCanvas(CanvasConfig config);

    /**
     * Create a header-bar peer. The bar's title is taken from the host
     * {@link WindowPeer}'s title; the config supplies pre-built start/end
     * peers that will be packed into the bar.
     */
    HeaderBarPeer createHeaderBar(HeaderBarConfig config);

    /** Create a system tray / status icon. Multiple per app are permitted. */
    SystemTrayPeer createSystemTray(SystemTrayConfig config);

    /** Apply a tooltip (hover hint) to the given peer; null or empty clears it. */
    void setTooltip(Peer peer, String text);

    /** Open the given URL in the platform's default handler (web browser, mailto, etc.). */
    void openUrl(String url);

    /** Read the system clipboard's text contents, or "" if empty/unsupported. */
    String clipboardText();

    /** Replace the system clipboard's text contents. */
    void setClipboardText(String text);

    /** Post a system notification (banner / toast). */
    void notify(String title, String body);

    /**
     * Configure {@code peer} as a drag source. The supplier is invoked at drag
     * start to produce the text payload; null disables the source. Tier-1
     * support is limited to text payloads.
     */
    void setDragSource(Peer peer, java.util.function.Supplier<String> textProvider);

    /**
     * Configure {@code peer} as a drop target. The consumer is invoked when a
     * drop completes; null disables the target. Tier-1 support is limited to
     * text payloads.
     */
    void setDropTarget(Peer peer, java.util.function.Consumer<String> textHandler);

    MenuActionPeer createMenuAction(MenuActionConfig config);

    MenuSeparatorPeer createMenuSeparator();

    MenuPeer createMenu(MenuConfig config);

    MenuBarPeer createMenuBar();

    /**
     * Show a modal message dialog. Must be invoked on the UI thread. Returns a
     * future that completes with the (zero-based) index of the button the user
     * chose. macOS may complete it synchronously (NSAlert nests the run loop);
     * GTK completes it asynchronously when the GtkAlertDialog callback fires.
     */
    CompletableFuture<Integer> showMessageDialog(MessageDialogConfig config);

    /**
     * Open a file picker. Future completes with the absolute path of the
     * chosen file, or {@code null} if the user cancelled. macOS may complete
     * synchronously (NSOpenPanel runModal); GTK completes asynchronously
     * (GtkFileDialog open/finish). Must be invoked on the UI thread.
     */
    CompletableFuture<String> showFileOpenDialog(FileDialogConfig config);

    /**
     * Save-file picker. Future completes with the chosen path or {@code null}.
     */
    CompletableFuture<String> showFileSaveDialog(FileDialogConfig config);

    /**
     * Folder picker. Future completes with the chosen folder's absolute path
     * or {@code null}.
     */
    CompletableFuture<String> showFolderDialog(FolderDialogConfig config);
}
