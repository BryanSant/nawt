package cc.nawt.backend.macos;

import cc.nawt.spi.FileDialogConfig;
import cc.nawt.spi.FolderDialogConfig;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;

/**
 * NSOpenPanel / NSSavePanel pickers. {@code runModal} blocks the UI thread
 * until the user dismisses, and the returned future is completed
 * synchronously — same model as {@link MacosMessageDialog} / NSAlert.
 */
final class MacosFileDialog {
    private MacosFileDialog() {}

    /** {@code NSModalResponseOK = 1}. */
    private static final long NS_MODAL_RESPONSE_OK = 1L;

    static CompletableFuture<String> showOpen(FileDialogConfig cfg) {
        return runOpenLikePanel(cfg, /*chooseFiles*/ true, /*chooseDirs*/ false);
    }

    static CompletableFuture<String> showFolder(FolderDialogConfig cfg) {
        FileDialogConfig adapted = new FileDialogConfig(cfg.title(), cfg.initialDirectory(), null);
        return runOpenLikePanel(adapted, /*chooseFiles*/ false, /*chooseDirs*/ true);
    }

    static CompletableFuture<String> showSave(FileDialogConfig cfg) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try (var pool = AutoreleasePool.push()) {
            // [NSSavePanel savePanel] — autoreleased; retain so it survives the
            // pool drain at end of this scope (we explicitly release after).
            MemorySegment panel = Objc.sendPtr(Objc.cls("NSSavePanel"), Objc.sel("savePanel"));
            panel = Objc.sendPtr(panel, Objc.sel("retain"));
            try {
                applyTitle(panel, cfg.title());
                applyInitialDirectory(panel, cfg.initialDirectory());
                if (cfg.defaultName() != null) {
                    Objc.sendVoid(panel, Objc.sel("setNameFieldStringValue:"),
                        NSString.from(cfg.defaultName()));
                }
                long response = Objc.sendLong(panel, Objc.sel("runModal"));
                if (response == NS_MODAL_RESPONSE_OK) {
                    MemorySegment url = Objc.sendPtr(panel, Objc.sel("URL"));
                    future.complete(extractPath(url));
                } else {
                    future.complete(null);
                }
            } finally {
                Objc.sendVoid(panel, Objc.sel("release"));
            }
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    private static CompletableFuture<String> runOpenLikePanel(
            FileDialogConfig cfg, boolean chooseFiles, boolean chooseDirs) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try (var pool = AutoreleasePool.push()) {
            MemorySegment panel = Objc.sendPtr(Objc.cls("NSOpenPanel"), Objc.sel("openPanel"));
            panel = Objc.sendPtr(panel, Objc.sel("retain"));
            try {
                applyTitle(panel, cfg.title());
                applyInitialDirectory(panel, cfg.initialDirectory());
                Objc.sendVoidBool(panel, Objc.sel("setCanChooseFiles:"), chooseFiles);
                Objc.sendVoidBool(panel, Objc.sel("setCanChooseDirectories:"), chooseDirs);
                Objc.sendVoidBool(panel, Objc.sel("setAllowsMultipleSelection:"), false);
                long response = Objc.sendLong(panel, Objc.sel("runModal"));
                if (response == NS_MODAL_RESPONSE_OK) {
                    MemorySegment urls = Objc.sendPtr(panel, Objc.sel("URLs"));
                    if (urls == null || urls.address() == 0) {
                        future.complete(null);
                    } else {
                        MemorySegment first = Objc.sendPtr(urls, Objc.sel("firstObject"));
                        future.complete(extractPath(first));
                    }
                } else {
                    future.complete(null);
                }
            } finally {
                Objc.sendVoid(panel, Objc.sel("release"));
            }
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    private static void applyTitle(MemorySegment panel, String title) {
        if (title == null || title.isEmpty()) return;
        // -[NSSavePanel setMessage:] is the modern caption (setTitle: was deprecated).
        Objc.sendVoid(panel, Objc.sel("setMessage:"), NSString.from(title));
    }

    private static void applyInitialDirectory(MemorySegment panel, String path) {
        if (path == null || path.isEmpty()) return;
        // [NSURL fileURLWithPath:path]
        MemorySegment url = Objc.sendPtr(
            Objc.cls("NSURL"), Objc.sel("fileURLWithPath:"), NSString.from(path));
        if (url != null && url.address() != 0) {
            Objc.sendVoid(panel, Objc.sel("setDirectoryURL:"), url);
        }
    }

    private static String extractPath(MemorySegment url) {
        if (url == null || url.address() == 0) return null;
        MemorySegment ns = Objc.sendPtr(url, Objc.sel("path"));
        return NSString.toJava(ns);
    }
}
