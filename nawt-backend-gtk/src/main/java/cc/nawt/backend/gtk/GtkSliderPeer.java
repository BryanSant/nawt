package cc.nawt.backend.gtk;

import cc.nawt.spi.Orientation;
import cc.nawt.spi.SliderConfig;
import cc.nawt.spi.SliderPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.DoubleConsumer;

final class GtkSliderPeer implements SliderPeer {

    private static final int GTK_ORIENTATION_HORIZONTAL = 0;
    private static final int GTK_ORIENTATION_VERTICAL = 1;

    private final MemorySegment widget;
    private volatile DoubleConsumer trigger;

    GtkSliderPeer(SliderConfig cfg) {
        int o = cfg.orientation() == Orientation.VERTICAL
            ? GTK_ORIENTATION_VERTICAL : GTK_ORIENTATION_HORIZONTAL;
        double range = cfg.max() - cfg.min();
        double step = range > 0 ? range / 100.0 : 0.01;
        MemorySegment w = Gtk.gtk_scale_new_with_range(o, cfg.min(), cfg.max(), step);
        this.widget = Gtk.g_object_ref(w);
        Gtk.gtk_range_set_value(widget, cfg.initialValue());
        GtkSignals.connectVoid(widget, "value-changed", () -> {
            DoubleConsumer t = trigger;
            if (t != null) {
                try { t.accept(getValue()); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment widget() { return widget; }

    @Override public void setRange(double min, double max) { Gtk.gtk_range_set_range(widget, min, max); }
    @Override public void setValue(double value) { Gtk.gtk_range_set_value(widget, value); }
    @Override public double getValue() { return Gtk.gtk_range_get_value(widget); }
    @Override public void onValueChange(DoubleConsumer trigger) { this.trigger = trigger; }
    @Override public void close() { Gtk.g_object_unref(widget); }
}
