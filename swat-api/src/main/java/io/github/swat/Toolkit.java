package io.github.swat;

import io.github.swat.spi.PeerFactory;
import io.github.swat.spi.UiLoop;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entry point. Boots a backend, drives the platform event loop on the calling
 * thread, and exposes the active {@link PeerFactory} to widgets.
 *
 * <p>Typical usage:
 * <pre>{@code
 *   public static void main(String[] args) {
 *     Toolkit.launch(() -> {
 *       Window.builder().title("Hello").size(400, 300)
 *         .content(...).build().show();
 *     });
 *   }
 * }</pre>
 */
public final class Toolkit {

    private static final AtomicReference<Toolkit> CURRENT = new AtomicReference<>();

    private final PeerFactory peerFactory;
    private final UiLoop uiLoop;
    private final Set<Window> openWindows = ConcurrentHashMap.newKeySet();

    private Toolkit(PeerFactory pf, UiLoop loop) {
        this.peerFactory = pf;
        this.uiLoop = loop;
    }

    /** Returns the active toolkit, or null if not launched. */
    public static Toolkit tryCurrent() {
        return CURRENT.get();
    }

    /** Returns the active toolkit, throwing if not launched. */
    public static Toolkit requireLaunched() {
        Toolkit t = CURRENT.get();
        if (t == null) {
            throw new IllegalStateException(
                "Swat toolkit has not been launched. Call Toolkit.launch(...) before using widgets.");
        }
        return t;
    }

    public PeerFactory peerFactory() { return peerFactory; }
    public UiLoop uiLoop() { return uiLoop; }

    void registerWindow(Window w) { openWindows.add(w); }

    void unregisterWindow(Window w) {
        openWindows.remove(w);
        if (openWindows.isEmpty()) {
            uiLoop.quit();
        }
    }

    /**
     * Launch the toolkit and run {@code appMain} as the application. Blocks
     * until the last window closes (or {@link #shutdown()} is called). Must be
     * called from the JVM's main thread on macOS — NSApplication requires it.
     */
    public static void launch(Runnable appMain) {
        PeerFactory pf = detect();
        UiLoop loop = pf.createUiLoop();
        Toolkit t = new Toolkit(pf, loop);
        if (!CURRENT.compareAndSet(null, t)) {
            throw new IllegalStateException("Toolkit already launched");
        }
        loop.bootstrap();
        Thread app = Thread.ofVirtual().name("swat-app").unstarted(() -> {
            try {
                appMain.run();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });
        app.start();
        loop.run();
    }

    /** Request orderly shutdown. Safe to call from any thread. */
    public static void shutdown() {
        Toolkit t = CURRENT.get();
        if (t != null) t.uiLoop.quit();
    }

    /** Open the given URL in the platform's default handler. */
    public static void openUrl(String url) {
        Ui.runOnUi(() -> requireLaunched().peerFactory.openUrl(url));
    }

    /** Read the system clipboard's text contents, or "" if empty. */
    public static String clipboardText() {
        return Ui.onUi(() -> requireLaunched().peerFactory.clipboardText());
    }

    /** Replace the system clipboard's text contents. */
    public static void setClipboardText(String text) {
        Ui.runOnUi(() -> requireLaunched().peerFactory.setClipboardText(text));
    }

    /** Post a system notification (banner / toast). */
    public static void notify(String title, String body) {
        Ui.runOnUi(() -> requireLaunched().peerFactory.notify(title, body));
    }

    /**
     * Test whether the active backend declares support for {@code capability}.
     * Capabilities cover platform-meaningful features (header bars, toasts,
     * drag-and-drop, system tray, translucent backdrops, …) that don't apply
     * uniformly across macOS, Linux, and Windows.
     */
    public static boolean supports(Capability capability) {
        return requireLaunched().peerFactory.capabilities().has(capability);
    }

    /**
     * Show an open-file picker. The future completes with the absolute path of
     * the chosen file, or {@code null} if the user cancelled. Backed by
     * {@code NSOpenPanel} on macOS and {@code GtkFileDialog} on Linux.
     */
    public static java.util.concurrent.CompletableFuture<String> showFileOpenDialog(
            io.github.swat.spi.FileDialogConfig config) {
        return Ui.onUi(() -> requireLaunched().peerFactory.showFileOpenDialog(config));
    }

    /** Convenience: open-file picker with just a title. */
    public static java.util.concurrent.CompletableFuture<String> showFileOpenDialog(String title) {
        return showFileOpenDialog(new io.github.swat.spi.FileDialogConfig(title, null, null));
    }

    /**
     * Show a save-file picker. The future completes with the absolute path of
     * the chosen destination, or {@code null} if the user cancelled.
     */
    public static java.util.concurrent.CompletableFuture<String> showFileSaveDialog(
            io.github.swat.spi.FileDialogConfig config) {
        return Ui.onUi(() -> requireLaunched().peerFactory.showFileSaveDialog(config));
    }

    /** Convenience: save-file picker with just a title and a default file name. */
    public static java.util.concurrent.CompletableFuture<String> showFileSaveDialog(
            String title, String defaultName) {
        return showFileSaveDialog(new io.github.swat.spi.FileDialogConfig(title, null, defaultName));
    }

    /**
     * Show a folder picker. The future completes with the absolute path of the
     * chosen folder, or {@code null} if the user cancelled.
     */
    public static java.util.concurrent.CompletableFuture<String> showFolderDialog(
            io.github.swat.spi.FolderDialogConfig config) {
        return Ui.onUi(() -> requireLaunched().peerFactory.showFolderDialog(config));
    }

    /** Convenience: folder picker with just a title. */
    public static java.util.concurrent.CompletableFuture<String> showFolderDialog(String title) {
        return showFolderDialog(new io.github.swat.spi.FolderDialogConfig(title, null));
    }

    /**
     * Resolve the backend {@link PeerFactory}: honors the
     * {@code -Dswat.backend=<id>} system property, otherwise picks the first
     * factory whose {@link PeerFactory#supports() supports()} returns true.
     */
    public static PeerFactory detect() {
        String override = System.getProperty("swat.backend");
        List<PeerFactory> factories = new ArrayList<>();
        for (PeerFactory f : ServiceLoader.load(PeerFactory.class)) {
            factories.add(f);
        }
        if (factories.isEmpty()) {
            throw new IllegalStateException(
                "No swat backend on the module path. Add a runtime dependency on "
                + "io.github.swat.backend.macos or io.github.swat.backend.gtk.");
        }
        if (override != null && !override.isBlank()) {
            for (PeerFactory f : factories) {
                if (f.platformId().equalsIgnoreCase(override)) {
                    if (!f.supports()) {
                        throw new IllegalStateException(
                            "Backend '" + override + "' is on the path but does not support this host.");
                    }
                    return f;
                }
            }
            throw new IllegalStateException(
                "Backend override '" + override + "' not found. Available: "
                + factories.stream().map(PeerFactory::platformId).toList());
        }
        for (PeerFactory f : factories) {
            if (f.supports()) return f;
        }
        throw new IllegalStateException(
            "No swat backend supports this host. Available: "
            + factories.stream().map(PeerFactory::platformId).toList());
    }
}
