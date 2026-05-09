package io.github.swat.backend.gtk;

import io.github.swat.spi.ExpanderConfig;
import io.github.swat.spi.ExpanderPeer;
import io.github.swat.spi.Peer;

import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

final class GtkExpanderPeer implements ExpanderPeer {

    private final MemorySegment widget;
    private volatile Consumer<Boolean> trigger;
    private volatile boolean lastState;

    GtkExpanderPeer(ExpanderConfig cfg) {
        MemorySegment w = Gtk.gtk_expander_new_with_label(cfg.title());
        this.widget = Gtk.g_object_ref(w);
        Gtk.gtk_expander_set_expanded(widget, cfg.initialExpanded());
        this.lastState = cfg.initialExpanded();
        GtkSignals.connectVoid3(widget, "notify::expanded", () -> {
            boolean now = Gtk.gtk_expander_get_expanded(widget);
            if (now == lastState) return;
            lastState = now;
            Consumer<Boolean> t = trigger;
            if (t != null) {
                try { t.accept(now); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment widget() { return widget; }

    @Override public void setTitle(String title) { Gtk.gtk_expander_set_label(widget, title); }
    @Override public void setContent(Peer content) {
        Gtk.gtk_expander_set_child(widget, GtkContainerPeer.peerWidget(content));
    }
    @Override public void setExpanded(boolean on) {
        lastState = on;
        Gtk.gtk_expander_set_expanded(widget, on);
    }
    @Override public boolean isExpanded() { return Gtk.gtk_expander_get_expanded(widget); }
    @Override public void onExpandedChange(Consumer<Boolean> trigger) { this.trigger = trigger; }
    @Override public void close() { Gtk.g_object_unref(widget); }
}
