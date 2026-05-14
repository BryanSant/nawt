package cc.nawt.backend.gtk;

import cc.nawt.Painter;
import cc.nawt.spi.CanvasConfig;
import cc.nawt.spi.CanvasPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

final class GtkCanvasPeer implements CanvasPeer {

    private final MemorySegment widget;
    private final long token;
    private volatile Consumer<Painter> trigger;

    GtkCanvasPeer(CanvasConfig cfg) {
        MemorySegment w = Gtk.gtk_drawing_area_new();
        this.widget = Gtk.g_object_ref(w);
        Gtk.gtk_drawing_area_set_content_width(widget, cfg.width());
        Gtk.gtk_drawing_area_set_content_height(widget, cfg.height());

        this.token = GtkDrawCallback.register(cr -> {
            Consumer<Painter> t = trigger;
            if (t == null) return;
            try { t.accept(new GtkPainter(cr)); }
            catch (Throwable th) { th.printStackTrace(); }
        });

        Gtk.gtk_drawing_area_set_draw_func(widget, GtkDrawCallback.STUB,
            MemorySegment.ofAddress(token), MemorySegment.NULL);
    }

    MemorySegment widget() { return widget; }

    @Override public void onPaint(Consumer<Painter> trigger) { this.trigger = trigger; }
    @Override public void invalidate() { Gtk.gtk_widget_queue_draw(widget); }

    @Override public void close() {
        GtkDrawCallback.unregister(token);
        Gtk.g_object_unref(widget);
    }
}
