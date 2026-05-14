package cc.nawt.backend.gtk;

import cc.nawt.spi.MenuActionConfig;
import cc.nawt.spi.MenuActionPeer;

import java.lang.foreign.MemorySegment;

final class GtkMenuActionPeer implements MenuActionPeer {

    private final MemorySegment action; // GSimpleAction*, owned by the action group
    private final String actionName;    // e.g. "act_5"
    private volatile String label;
    private volatile Runnable trigger;

    GtkMenuActionPeer(MenuActionConfig cfg) {
        this.label = cfg.text();
        long id = GtkActions.nextActionId();
        this.actionName = "act_" + id;

        // GSimpleAction with no parameter
        MemorySegment a = Gtk.g_simple_action_new(actionName);
        // Connect "activate" — three-arg signal: (GSimpleAction*, GVariant*, gpointer user_data)
        GtkSignals.connectVoid3(a, "activate", () -> {
            Runnable t = trigger;
            if (t != null) {
                try { t.run(); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
        if (!cfg.enabled()) Gtk.g_simple_action_set_enabled(a, false);
        Gtk.g_action_map_add_action(GtkActions.group(), a);
        // group retains; we keep a reference for setEnabled but the group holds the lifecycle
        this.action = a;
    }

    String label() { return label; }
    String actionName() { return actionName; }
    String detailedAction() { return GtkActions.PREFIX + "." + actionName; }

    @Override
    public void setText(String text) { this.label = text; /* parent menu rebuilds on change */ }

    @Override
    public void setEnabled(boolean enabled) {
        Gtk.g_simple_action_set_enabled(action, enabled);
    }

    @Override
    public void onSelect(Runnable trigger) { this.trigger = trigger; }

    @Override
    public void close() {
        Gtk.g_object_unref(action);
        // Note: action remains in the group's map; for MVP we don't remove from the map.
    }
}
