package cc.nawt.backend.gtk;

import cc.nawt.spi.ProgressBarConfig;
import cc.nawt.spi.ProgressBarPeer;

import java.lang.foreign.MemorySegment;

final class GtkProgressBarPeer implements ProgressBarPeer {

    private final MemorySegment widget;
    private boolean indeterminate;
    private long pulseSourceId;

    GtkProgressBarPeer(ProgressBarConfig cfg) {
        MemorySegment w = Gtk.gtk_progress_bar_new();
        this.widget = Gtk.g_object_ref(w);
        if (cfg.indeterminate()) setIndeterminate(true);
        else                     setValue(cfg.value());
    }

    MemorySegment widget() { return widget; }

    @Override public void setValue(double value) {
        if (value < 0) value = 0;
        if (value > 1) value = 1;
        Gtk.gtk_progress_bar_set_fraction(widget, value);
    }

    @Override public void setIndeterminate(boolean ind) {
        if (this.indeterminate == ind) return;
        this.indeterminate = ind;
        if (ind) {
            pulseSourceId = Gtk.g_timeout_add(80, GtkPulse.STUB,
                MemorySegment.ofAddress(GtkPulse.register(widget)));
        } else if (pulseSourceId != 0) {
            Gtk.g_source_remove(pulseSourceId);
            GtkPulse.unregister(pulseSourceId);
            pulseSourceId = 0;
            Gtk.gtk_progress_bar_set_fraction(widget, 0);
        }
    }

    @Override public void close() {
        if (pulseSourceId != 0) {
            Gtk.g_source_remove(pulseSourceId);
            GtkPulse.unregister(pulseSourceId);
            pulseSourceId = 0;
        }
        Gtk.g_object_unref(widget);
    }
}
