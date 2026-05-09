package io.github.swat.backend.gtk;

import io.github.swat.spi.LabelConfig;
import io.github.swat.spi.LabelPeer;

import java.lang.foreign.MemorySegment;

final class GtkLabelPeer implements LabelPeer {

    private final MemorySegment widget;

    GtkLabelPeer(LabelConfig cfg) {
        MemorySegment w = Gtk.gtk_label_new(cfg.text());
        // gtk_widget_new returns a floating ref; sink to a regular ref by g_object_ref
        this.widget = Gtk.g_object_ref(w);
    }

    MemorySegment widget() { return widget; }

    @Override public void setText(String text) { Gtk.gtk_label_set_text(widget, text); }

    @Override public String getText() {
        String s = Gtk.gtk_label_get_text(widget);
        return s == null ? "" : s;
    }

    @Override public void close() { Gtk.g_object_unref(widget); }
}
