package cc.nawt.backend.gtk;

import cc.nawt.spi.SpinnerConfig;
import cc.nawt.spi.SpinnerPeer;

import java.lang.foreign.MemorySegment;

final class GtkSpinnerPeer implements SpinnerPeer {

    private final MemorySegment widget;
    private boolean active;

    GtkSpinnerPeer(SpinnerConfig cfg) {
        MemorySegment w = Gtk.gtk_spinner_new();
        this.widget = Gtk.g_object_ref(w);
        if (cfg.initialActive()) setActive(true);
    }

    MemorySegment widget() { return widget; }

    @Override public void setActive(boolean on) {
        if (this.active == on) return;
        this.active = on;
        if (on) Gtk.gtk_spinner_start(widget);
        else    Gtk.gtk_spinner_stop(widget);
    }

    @Override public boolean isActive() { return active; }

    @Override public void close() {
        if (active) Gtk.gtk_spinner_stop(widget);
        Gtk.g_object_unref(widget);
    }
}
