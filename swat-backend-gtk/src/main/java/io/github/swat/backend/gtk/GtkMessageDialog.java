package io.github.swat.backend.gtk;

import io.github.swat.spi.MessageDialogConfig;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GTK message dialog using {@code AdwAlertDialog}. AdwAlertDialog is the
 * libadwaita refinement of {@code GtkAlertDialog} — same async {@code choose}
 * shape but with named string responses, a separate close-response (Esc),
 * and integrated styling that matches the rest of the Adwaita UX.
 *
 * <p>Buttons are mapped to responses with IDs {@code "0"}, {@code "1"}, … so
 * the SPI's integer-indexed contract round-trips cleanly through Adwaita's
 * string-keyed response model.
 */
final class GtkMessageDialog {
    private GtkMessageDialog() {}

    private static final AtomicLong NEXT_TOKEN = new AtomicLong(1);

    private record Pending(CompletableFuture<Integer> future, int buttonCount, int defaultIdx) {}
    private static final ConcurrentHashMap<Long, Pending> IN_FLIGHT = new ConcurrentHashMap<>();

    /** GAsyncReadyCallback signature: {@code void(GObject*, GAsyncResult*, gpointer)}. */
    private static final MemorySegment ASYNC_CALLBACK_STUB;

    static {
        try {
            MethodHandle mh = MethodHandles.lookup().findStatic(
                GtkMessageDialog.class, "alertChooseFinished",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            ASYNC_CALLBACK_STUB = Linker.nativeLinker().upcallStub(
                mh,
                FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR, Gtk.PTR),
                Gtk.GLOBAL);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static CompletableFuture<Integer> show(MessageDialogConfig cfg) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        try {
            String heading = cfg.title().isBlank() ? cfg.message() : cfg.title();
            String body = cfg.title().isBlank()
                ? (cfg.details() == null ? "" : cfg.details())
                : combineMessageAndDetails(cfg.message(), cfg.details());

            MemorySegment dialog = Adw.adw_alert_dialog_new(heading, body);

            List<String> buttons = cfg.buttons();
            for (int i = 0; i < buttons.size(); i++) {
                Adw.adw_alert_dialog_add_response(dialog, Integer.toString(i), buttons.get(i));
            }
            String defaultId = Integer.toString(cfg.defaultButtonIndex());
            Adw.adw_alert_dialog_set_default_response(dialog, defaultId);
            // Esc / outside-click resolves to the same response as the default
            // button, matching the "default action" expectation users have on
            // every other backend.
            Adw.adw_alert_dialog_set_close_response(dialog, defaultId);

            long token = NEXT_TOKEN.getAndIncrement();
            IN_FLIGHT.put(token, new Pending(future, buttons.size(), cfg.defaultButtonIndex()));

            Adw.adw_alert_dialog_choose(
                dialog,
                MemorySegment.NULL,    // parent — we don't track an active window in tier-2
                MemorySegment.NULL,    // cancellable
                ASYNC_CALLBACK_STUB,
                MemorySegment.ofAddress(token));
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    private static String combineMessageAndDetails(String message, String details) {
        if (details == null || details.isBlank()) return message;
        if (message == null || message.isBlank()) return details;
        return message + "\n\n" + details;
    }

    @SuppressWarnings("unused") // upcalled from GTK
    private static void alertChooseFinished(MemorySegment source, MemorySegment result,
                                            MemorySegment userData) {
        long token = userData.address();
        Pending pending = IN_FLIGHT.remove(token);
        if (pending == null) return;
        try {
            MemorySegment cstr = Adw.adw_alert_dialog_choose_finish(source, result);
            int idx = parseResponseId(cstr, pending.buttonCount(), pending.defaultIdx());
            pending.future().complete(idx);
        } catch (Throwable t) {
            pending.future().completeExceptionally(t);
        }
        // adw_alert_dialog_new returns a floating ref that adw_alert_dialog_choose
        // sinks into the parent window. We never owned a ref, so we don't unref.
    }

    private static int parseResponseId(MemorySegment cstr, int buttonCount, int defaultIdx) {
        if (cstr == null || cstr.address() == 0) return defaultIdx;
        String s = cstr.reinterpret(Long.MAX_VALUE).getString(0);
        try {
            int idx = Integer.parseInt(s);
            if (idx < 0 || idx >= buttonCount) return defaultIdx;
            return idx;
        } catch (NumberFormatException e) {
            return defaultIdx;
        }
    }
}
