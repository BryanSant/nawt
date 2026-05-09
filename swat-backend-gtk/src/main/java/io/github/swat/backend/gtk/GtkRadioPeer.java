package io.github.swat.backend.gtk;

import io.github.swat.spi.RadioConfig;
import io.github.swat.spi.RadioPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

/**
 * Radio = GtkCheckButton with a group set via {@code gtk_check_button_set_group}.
 * GTK4 deprecated GtkRadioButton in favor of GtkCheckButton + grouping.
 */
final class GtkRadioPeer implements RadioPeer {

    private final MemorySegment widget;
    private volatile Consumer<Boolean> trigger;

    GtkRadioPeer(RadioConfig cfg) {
        MemorySegment w = Gtk.gtk_check_button_new_with_label(cfg.text());
        this.widget = Gtk.g_object_ref(w);
        Gtk.gtk_check_button_set_active(widget, cfg.initialSelected());
        GtkSignals.connectVoid(widget, "toggled", () -> {
            Consumer<Boolean> t = trigger;
            if (t != null) {
                try { t.accept(isSelected()); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment widget() { return widget; }

    @Override public void setText(String text) { Gtk.gtk_check_button_set_label(widget, text); }
    @Override public void setSelected(boolean selected) { Gtk.gtk_check_button_set_active(widget, selected); }
    @Override public boolean isSelected() { return Gtk.gtk_check_button_get_active(widget); }

    @Override public void groupWith(RadioPeer other) {
        if (!(other instanceof GtkRadioPeer g)) {
            throw new IllegalArgumentException("Cannot group across backends: " + other.getClass());
        }
        Gtk.gtk_check_button_set_group(g.widget, this.widget);
    }

    @Override public void onToggle(Consumer<Boolean> trigger) { this.trigger = trigger; }
    @Override public void close() { Gtk.g_object_unref(widget); }
}
