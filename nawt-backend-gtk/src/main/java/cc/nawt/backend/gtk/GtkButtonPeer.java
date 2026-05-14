package cc.nawt.backend.gtk;

import cc.nawt.spi.ButtonConfig;
import cc.nawt.spi.ButtonPeer;

import java.lang.foreign.MemorySegment;

final class GtkButtonPeer implements ButtonPeer {

    private final MemorySegment widget;
    private volatile Runnable trigger;

    GtkButtonPeer(ButtonConfig cfg) {
        MemorySegment w = Gtk.gtk_button_new_with_label(cfg.text());
        this.widget = Gtk.g_object_ref(w);
        GtkSignals.connectVoid(widget, "clicked", () -> {
            Runnable t = trigger;
            if (t != null) {
                try { t.run(); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
        if (cfg.fontSize() > 0) {
            applyFontSize(widget, cfg.fontSize());
        }
    }

    MemorySegment widget() { return widget; }

    @Override public void setText(String text) { Gtk.gtk_button_set_label(widget, text); }

    @Override public void onClick(Runnable trigger) { this.trigger = trigger; }

    @Override
    public void setFontSize(int points) {
        applyFontSize(widget, points);
    }

    /**
     * Apply a font size to the button's inner GtkLabel via Pango attributes.
     * GtkButton created with a label string keeps a GtkLabel child whose
     * attributes survive {@code gtk_button_set_label} text updates.
     * {@code points <= 0} clears the attribute list, restoring default sizing.
     */
    private static void applyFontSize(MemorySegment button, int points) {
        MemorySegment child = Gtk.gtk_button_get_child(button);
        if (child == null || child.address() == 0) return;
        if (points <= 0) {
            Gtk.gtk_label_set_attributes(child, MemorySegment.NULL);
            return;
        }
        MemorySegment attrs = Pango.pango_attr_list_new();
        Pango.pango_attr_list_insert(attrs, Pango.pango_attr_size_new(points * Pango.SCALE));
        Gtk.gtk_label_set_attributes(child, attrs);
        Pango.pango_attr_list_unref(attrs);
    }

    @Override public void close() { Gtk.g_object_unref(widget); }
}
