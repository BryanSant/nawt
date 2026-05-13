package io.github.swat.backend.gtk;

import io.github.swat.spi.LabelConfig;
import io.github.swat.spi.LabelPeer;

import java.lang.foreign.MemorySegment;

final class GtkLabelPeer implements LabelPeer {

    private final MemorySegment widget;
    private int currentSize;
    private boolean currentMonospace;

    GtkLabelPeer(LabelConfig cfg) {
        MemorySegment w = Gtk.gtk_label_new(cfg.text());
        this.widget = Gtk.g_object_ref(w);
        this.currentSize = cfg.fontSize();
        this.currentMonospace = cfg.monospace();
        if (currentSize > 0 || currentMonospace) applyAttrs();
    }

    MemorySegment widget() { return widget; }

    @Override public void setText(String text) { Gtk.gtk_label_set_text(widget, text); }

    @Override public String getText() {
        String s = Gtk.gtk_label_get_text(widget);
        return s == null ? "" : s;
    }

    @Override
    public void setFontSize(int points) {
        this.currentSize = Math.max(0, points);
        applyAttrs();
    }

    @Override
    public void setMonospace(boolean monospace) {
        this.currentMonospace = monospace;
        applyAttrs();
    }

    /**
     * Build a {@code PangoAttrList} from the current size + monospace state
     * and install it via {@code gtk_label_set_attributes}. The list takes
     * ownership of each inserted attribute; we drop our list reference after
     * GTK takes its own.
     *
     * <p>When both knobs are at their defaults, install a null attribute list
     * to clear any previously-applied attributes.
     */
    private void applyAttrs() {
        if (currentSize <= 0 && !currentMonospace) {
            Gtk.gtk_label_set_attributes(widget, MemorySegment.NULL);
            return;
        }
        MemorySegment attrs = Pango.pango_attr_list_new();
        if (currentSize > 0) {
            Pango.pango_attr_list_insert(attrs, Pango.pango_attr_size_new(currentSize * Pango.SCALE));
        }
        if (currentMonospace) {
            Pango.pango_attr_list_insert(attrs, Pango.pango_attr_family_new("monospace"));
        }
        Gtk.gtk_label_set_attributes(widget, attrs);
        Pango.pango_attr_list_unref(attrs);
    }

    @Override public void close() { Gtk.g_object_unref(widget); }
}
