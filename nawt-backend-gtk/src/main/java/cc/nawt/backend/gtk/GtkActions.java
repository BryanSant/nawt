package cc.nawt.backend.gtk;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Toolkit-level GSimpleActionGroup. Each {@link GtkMenuActionPeer} registers
 * a GSimpleAction here with a unique name; the resulting action prefix
 * {@code nawt.action_N} is referenced by {@code GMenuItem} entries. The group
 * is inserted on each {@link GtkMenuBarPeer} widget so the menu items can
 * resolve their actions via the standard widget→ancestor lookup.
 */
final class GtkActions {
    private GtkActions() {}

    static final String PREFIX = "nawt";

    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private static volatile MemorySegment GROUP;

    /** Lazily create the group (must be called on UI thread, after gtk_init). */
    static MemorySegment group() {
        MemorySegment g = GROUP;
        if (g != null) return g;
        synchronized (GtkActions.class) {
            if (GROUP == null) {
                GROUP = Gtk.g_simple_action_group_new();
            }
            return GROUP;
        }
    }

    static long nextActionId() { return NEXT_ID.getAndIncrement(); }
}
