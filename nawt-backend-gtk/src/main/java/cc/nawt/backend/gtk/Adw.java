package cc.nawt.backend.gtk;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * FFM bindings to libadwaita 1. Loaded on demand once {@code GtkPeerFactory}
 * has confirmed both libgtk-4 and libadwaita-1 are present on the host. The
 * field initializers throw {@link RuntimeException} if a symbol is missing,
 * so this class must not be referenced on hosts that lack libadwaita.
 *
 * <p>libadwaita is required, not optional — see {@code README.md}. NAWT's
 * Linux backend uses Adwaita primitives ({@code AdwWindow},
 * {@code AdwToolbarView}, {@code AdwHeaderBar}, …) wherever they refine the
 * GTK 4 base.
 */
final class Adw {
    private Adw() {}

    private static final SymbolLookup LIB =
        SymbolLookup.libraryLookup("libadwaita-1.so.0", Gtk.GLOBAL);

    private static MethodHandle bind(String symbol, FunctionDescriptor fd) {
        return Linker.nativeLinker().downcallHandle(
            LIB.find(symbol).orElseThrow(() ->
                new RuntimeException("Missing libadwaita symbol: " + symbol)),
            fd);
    }

    private static final MethodHandle ADW_INIT =
        bind("adw_init", FunctionDescriptor.ofVoid());

    /* ---------- Window / ToolbarView / HeaderBar ---------- */

    private static final MethodHandle ADW_WINDOW_NEW =
        bind("adw_window_new", FunctionDescriptor.of(Gtk.PTR));
    private static final MethodHandle ADW_WINDOW_SET_CONTENT =
        bind("adw_window_set_content", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));

    private static final MethodHandle ADW_TOOLBAR_VIEW_NEW =
        bind("adw_toolbar_view_new", FunctionDescriptor.of(Gtk.PTR));
    private static final MethodHandle ADW_TOOLBAR_VIEW_SET_CONTENT =
        bind("adw_toolbar_view_set_content", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));
    private static final MethodHandle ADW_TOOLBAR_VIEW_ADD_TOP_BAR =
        bind("adw_toolbar_view_add_top_bar", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));
    private static final MethodHandle ADW_TOOLBAR_VIEW_REMOVE =
        bind("adw_toolbar_view_remove", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));

    private static final MethodHandle ADW_HEADER_BAR_NEW =
        bind("adw_header_bar_new", FunctionDescriptor.of(Gtk.PTR));
    private static final MethodHandle ADW_HEADER_BAR_PACK_START =
        bind("adw_header_bar_pack_start", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));
    private static final MethodHandle ADW_HEADER_BAR_PACK_END =
        bind("adw_header_bar_pack_end", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));

    /* ---------- AdwToast / AdwToastOverlay ---------- */

    private static final MethodHandle ADW_TOAST_NEW =
        bind("adw_toast_new", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR));
    private static final MethodHandle ADW_TOAST_SET_TIMEOUT =
        bind("adw_toast_set_timeout", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.INT));

    private static final MethodHandle ADW_TOAST_OVERLAY_NEW =
        bind("adw_toast_overlay_new", FunctionDescriptor.of(Gtk.PTR));
    private static final MethodHandle ADW_TOAST_OVERLAY_SET_CHILD =
        bind("adw_toast_overlay_set_child", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));
    private static final MethodHandle ADW_TOAST_OVERLAY_ADD_TOAST =
        bind("adw_toast_overlay_add_toast", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));

    /* ---------- AdwAlertDialog ---------- */

    private static final MethodHandle ADW_ALERT_DIALOG_NEW =
        bind("adw_alert_dialog_new", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR, Gtk.PTR));
    private static final MethodHandle ADW_ALERT_DIALOG_ADD_RESPONSE =
        bind("adw_alert_dialog_add_response", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR, Gtk.PTR));
    private static final MethodHandle ADW_ALERT_DIALOG_SET_DEFAULT_RESPONSE =
        bind("adw_alert_dialog_set_default_response", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));
    private static final MethodHandle ADW_ALERT_DIALOG_SET_CLOSE_RESPONSE =
        bind("adw_alert_dialog_set_close_response", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));
    private static final MethodHandle ADW_ALERT_DIALOG_CHOOSE =
        bind("adw_alert_dialog_choose", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR));
    private static final MethodHandle ADW_ALERT_DIALOG_CHOOSE_FINISH =
        bind("adw_alert_dialog_choose_finish", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR, Gtk.PTR));

    /* ---------- Wrappers ---------- */

    static void adw_init() {
        try { ADW_INIT.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment adw_window_new() {
        try { return (MemorySegment) ADW_WINDOW_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_window_set_content(MemorySegment window, MemorySegment content) {
        try { ADW_WINDOW_SET_CONTENT.invoke(window, content); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment adw_toolbar_view_new() {
        try { return (MemorySegment) ADW_TOOLBAR_VIEW_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_toolbar_view_set_content(MemorySegment toolbarView, MemorySegment content) {
        try { ADW_TOOLBAR_VIEW_SET_CONTENT.invoke(toolbarView, content); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_toolbar_view_add_top_bar(MemorySegment toolbarView, MemorySegment widget) {
        try { ADW_TOOLBAR_VIEW_ADD_TOP_BAR.invoke(toolbarView, widget); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_toolbar_view_remove(MemorySegment toolbarView, MemorySegment widget) {
        try { ADW_TOOLBAR_VIEW_REMOVE.invoke(toolbarView, widget); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment adw_header_bar_new() {
        try { return (MemorySegment) ADW_HEADER_BAR_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_header_bar_pack_start(MemorySegment headerBar, MemorySegment widget) {
        try { ADW_HEADER_BAR_PACK_START.invoke(headerBar, widget); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_header_bar_pack_end(MemorySegment headerBar, MemorySegment widget) {
        try { ADW_HEADER_BAR_PACK_END.invoke(headerBar, widget); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /* ---------- AdwToast wrappers ---------- */

    static MemorySegment adw_toast_new(String title) {
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            return (MemorySegment) ADW_TOAST_NEW.invoke(
                arena.allocateFrom(title == null ? "" : title));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_toast_set_timeout(MemorySegment toast, int seconds) {
        try { ADW_TOAST_SET_TIMEOUT.invoke(toast, seconds); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment adw_toast_overlay_new() {
        try { return (MemorySegment) ADW_TOAST_OVERLAY_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_toast_overlay_set_child(MemorySegment overlay, MemorySegment child) {
        try { ADW_TOAST_OVERLAY_SET_CHILD.invoke(overlay, child); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_toast_overlay_add_toast(MemorySegment overlay, MemorySegment toast) {
        try { ADW_TOAST_OVERLAY_ADD_TOAST.invoke(overlay, toast); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /* ---------- AdwAlertDialog wrappers ---------- */

    static MemorySegment adw_alert_dialog_new(String heading, String body) {
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            MemorySegment h = (heading == null || heading.isEmpty())
                ? MemorySegment.NULL : arena.allocateFrom(heading);
            MemorySegment b = (body == null || body.isEmpty())
                ? MemorySegment.NULL : arena.allocateFrom(body);
            return (MemorySegment) ADW_ALERT_DIALOG_NEW.invoke(h, b);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_alert_dialog_add_response(MemorySegment dialog, String id, String label) {
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            ADW_ALERT_DIALOG_ADD_RESPONSE.invoke(
                dialog,
                arena.allocateFrom(id == null ? "" : id),
                arena.allocateFrom(label == null ? "" : label));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_alert_dialog_set_default_response(MemorySegment dialog, String id) {
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            ADW_ALERT_DIALOG_SET_DEFAULT_RESPONSE.invoke(
                dialog, arena.allocateFrom(id == null ? "" : id));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_alert_dialog_set_close_response(MemorySegment dialog, String id) {
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            ADW_ALERT_DIALOG_SET_CLOSE_RESPONSE.invoke(
                dialog, arena.allocateFrom(id == null ? "" : id));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void adw_alert_dialog_choose(MemorySegment dialog, MemorySegment parent,
                                        MemorySegment cancellable, MemorySegment callback,
                                        MemorySegment userData) {
        try { ADW_ALERT_DIALOG_CHOOSE.invoke(dialog, parent, cancellable, callback, userData); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** Returns a transfer-none {@code const char*} response ID — owned by the dialog. */
    static MemorySegment adw_alert_dialog_choose_finish(MemorySegment dialog, MemorySegment result) {
        try { return (MemorySegment) ADW_ALERT_DIALOG_CHOOSE_FINISH.invoke(dialog, result); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
}
