package cc.nawt.backend.gtk;

import cc.nawt.spi.CheckboxConfig;
import cc.nawt.spi.CheckboxPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

final class GtkCheckboxPeer implements CheckboxPeer {

    private final MemorySegment widget;
    private volatile Consumer<Boolean> trigger;

    GtkCheckboxPeer(CheckboxConfig cfg) {
        MemorySegment w = Gtk.gtk_check_button_new_with_label(cfg.text());
        this.widget = Gtk.g_object_ref(w);
        Gtk.gtk_check_button_set_active(widget, cfg.initialChecked());
        GtkSignals.connectVoid(widget, "toggled", () -> {
            Consumer<Boolean> t = trigger;
            if (t != null) {
                try { t.accept(isChecked()); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment widget() { return widget; }

    @Override public void setText(String text) { Gtk.gtk_check_button_set_label(widget, text); }
    @Override public void setChecked(boolean checked) { Gtk.gtk_check_button_set_active(widget, checked); }
    @Override public boolean isChecked() { return Gtk.gtk_check_button_get_active(widget); }
    @Override public void onToggle(Consumer<Boolean> trigger) { this.trigger = trigger; }
    @Override public void close() { Gtk.g_object_unref(widget); }
}
