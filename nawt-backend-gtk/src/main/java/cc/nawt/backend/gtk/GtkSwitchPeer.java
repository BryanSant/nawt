package cc.nawt.backend.gtk;

import cc.nawt.spi.SwitchConfig;
import cc.nawt.spi.SwitchPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

final class GtkSwitchPeer implements SwitchPeer {

    private final MemorySegment box;
    private final MemorySegment sw;
    private volatile Consumer<Boolean> trigger;
    private volatile boolean lastState;

    GtkSwitchPeer(SwitchConfig cfg) {
        MemorySegment s = Gtk.gtk_switch_new();
        this.sw = Gtk.g_object_ref(s);
        Gtk.gtk_switch_set_active(sw, cfg.initialOn());
        this.lastState = cfg.initialOn();
        GtkSignals.connectVoid3(sw, "notify::active", () -> {
            boolean now = Gtk.gtk_switch_get_active(sw);
            if (now == lastState) return;
            lastState = now;
            Consumer<Boolean> t = trigger;
            if (t != null) {
                try { t.accept(now); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
        // GtkSwitch visibly stretches its toggle track when given halign=FILL,
        // which the container applies under the default STRETCH cross-axis.
        // Wrap it in a box so the parent's FILL lands on the wrapper while the
        // switch inside stays anchored at its natural compact size.
        MemorySegment b = Gtk.gtk_box_new(Gtk.GTK_ORIENTATION_HORIZONTAL, 0);
        Gtk.gtk_widget_set_halign(sw, Gtk.GTK_ALIGN_START);
        Gtk.gtk_widget_set_valign(sw, Gtk.GTK_ALIGN_CENTER);
        Gtk.gtk_box_append(b, sw);
        this.box = Gtk.g_object_ref(b);
    }

    MemorySegment widget() { return box; }

    @Override public void setOn(boolean on) {
        lastState = on;
        Gtk.gtk_switch_set_active(sw, on);
    }
    @Override public boolean isOn() { return Gtk.gtk_switch_get_active(sw); }
    @Override public void onToggle(Consumer<Boolean> trigger) { this.trigger = trigger; }
    @Override public void close() {
        Gtk.g_object_unref(sw);
        Gtk.g_object_unref(box);
    }
}
