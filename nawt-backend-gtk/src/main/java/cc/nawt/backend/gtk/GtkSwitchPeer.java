package cc.nawt.backend.gtk;

import cc.nawt.spi.SwitchConfig;
import cc.nawt.spi.SwitchPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

final class GtkSwitchPeer implements SwitchPeer {

    private final MemorySegment widget;
    private volatile Consumer<Boolean> trigger;
    private volatile boolean lastState;

    GtkSwitchPeer(SwitchConfig cfg) {
        MemorySegment w = Gtk.gtk_switch_new();
        this.widget = Gtk.g_object_ref(w);
        Gtk.gtk_switch_set_active(widget, cfg.initialOn());
        this.lastState = cfg.initialOn();
        GtkSignals.connectVoid3(widget, "notify::active", () -> {
            boolean now = Gtk.gtk_switch_get_active(widget);
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

    @Override public void setOn(boolean on) {
        lastState = on;
        Gtk.gtk_switch_set_active(widget, on);
    }
    @Override public boolean isOn() { return Gtk.gtk_switch_get_active(widget); }
    @Override public void onToggle(Consumer<Boolean> trigger) { this.trigger = trigger; }
    @Override public void close() { Gtk.g_object_unref(widget); }
}
