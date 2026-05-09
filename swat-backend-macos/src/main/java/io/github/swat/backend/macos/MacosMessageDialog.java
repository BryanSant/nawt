package io.github.swat.backend.macos;

import io.github.swat.spi.MessageDialogConfig;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;

final class MacosMessageDialog {
    private MacosMessageDialog() {}

    // NSAlertStyle: Warning=0, Informational=1, Critical=2
    private static final long STYLE_WARNING = 0L;
    private static final long STYLE_INFORMATIONAL = 1L;
    private static final long STYLE_CRITICAL = 2L;

    // [alert runModal] returns NSAlertFirstButtonReturn = 1000, +1 per subsequent button.
    private static final long FIRST_BUTTON_RETURN = 1000L;

    static CompletableFuture<Integer> show(MessageDialogConfig cfg) {
        // NSAlert.runModal blocks the UI thread but pumps a nested event loop.
        // We complete the future synchronously and return.
        try (var pool = AutoreleasePool.push()) {
            MemorySegment alert = Objc.sendPtr(
                Objc.send_alloc(Objc.cls("NSAlert")), Objc.sel("init"));

            long alertStyle = switch (cfg.style()) {
                case INFO -> STYLE_INFORMATIONAL;
                case ERROR -> STYLE_CRITICAL;
                case WARNING, QUESTION -> STYLE_WARNING;
            };
            Objc.sendVoidLong(alert, Objc.sel("setAlertStyle:"), alertStyle);

            String headline = cfg.title().isBlank() ? cfg.message() : cfg.title();
            String body = informativeBody(cfg);

            Objc.sendVoid(alert, Objc.sel("setMessageText:"), NSString.from(headline));
            if (body != null && !body.isBlank()) {
                Objc.sendVoid(alert, Objc.sel("setInformativeText:"), NSString.from(body));
            }

            for (String b : cfg.buttons()) {
                Objc.sendPtr(alert, Objc.sel("addButtonWithTitle:"), NSString.from(b));
            }

            long rc = Objc.sendLong(alert, Objc.sel("runModal"));
            int chosen = (int) (rc - FIRST_BUTTON_RETURN);
            if (chosen < 0 || chosen >= cfg.buttons().size()) chosen = 0;

            Objc.sendVoid(alert, Objc.sel("release"));
            return CompletableFuture.completedFuture(chosen);
        } catch (Throwable t) {
            CompletableFuture<Integer> failed = new CompletableFuture<>();
            failed.completeExceptionally(t);
            return failed;
        }
    }

    private static String informativeBody(MessageDialogConfig cfg) {
        boolean hasTitle = !cfg.title().isBlank();
        if (hasTitle) {
            if (cfg.details() != null) return cfg.message() + "\n\n" + cfg.details();
            return cfg.message();
        } else {
            return cfg.details();
        }
    }
}
