package io.github.swat.backend.gtk;

import io.github.swat.spi.ButtonConfig;
import io.github.swat.spi.ButtonPeer;

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
    }

    MemorySegment widget() { return widget; }

    @Override public void setText(String text) { Gtk.gtk_button_set_label(widget, text); }

    @Override public void onClick(Runnable trigger) { this.trigger = trigger; }

    @Override public void close() { Gtk.g_object_unref(widget); }
}
