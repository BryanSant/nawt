package cc.nawt.backend.gtk;

import cc.nawt.spi.Peer;
import cc.nawt.spi.TabsConfig;
import cc.nawt.spi.TabsPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.IntConsumer;

final class GtkTabsPeer implements TabsPeer {

    private final MemorySegment widget;
    private volatile IntConsumer trigger;
    private volatile int lastPage = -1;

    GtkTabsPeer(TabsConfig cfg) {
        MemorySegment w = Gtk.gtk_notebook_new();
        this.widget = Gtk.g_object_ref(w);
        GtkSignals.connectVoid3(widget, "notify::page", () -> {
            int now = selectedTab();
            if (now == lastPage) return;
            lastPage = now;
            IntConsumer t = trigger;
            if (t != null) {
                try { t.accept(now); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment widget() { return widget; }

    @Override public void appendTab(String title, Peer content) {
        MemorySegment tab = Gtk.gtk_label_new(title);
        Gtk.gtk_notebook_append_page(widget, GtkContainerPeer.peerWidget(content), tab);
    }

    @Override public int selectedTab() { return Gtk.gtk_notebook_get_current_page(widget); }
    @Override public void selectTab(int index) { Gtk.gtk_notebook_set_current_page(widget, index); }
    @Override public void onTabChange(IntConsumer trigger) { this.trigger = trigger; }
    @Override public void close() { Gtk.g_object_unref(widget); }
}
