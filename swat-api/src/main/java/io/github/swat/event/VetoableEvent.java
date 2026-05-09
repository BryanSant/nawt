package io.github.swat.event;

/**
 * Base class for events whose default action can be cancelled by handlers.
 * Concrete event types extend this class and add their own getters; the
 * {@link #veto()} / {@link #isVetoed()} pair is final and inherited.
 *
 * <p>Veto fits cleanly when the host platform offers a pre-action hook that
 * can return false to abort. Examples in SWAT:
 * <ul>
 *   <li>{@link WindowCloseEvent} — macOS {@code windowShouldClose:} returns
 *       BOOL; GTK {@code close-request} returns gboolean. Both vetoable.</li>
 *   <li>Drag start — already implicit in {@code Widget.dragText(Supplier)};
 *       returning {@code null} from the supplier aborts the drag on both
 *       backends, so no separate vetoable event is needed.</li>
 * </ul>
 *
 * <p>Veto fits awkwardly when one backend has a pre-action hook and the
 * other only has a post-change signal. {@code Tabs} is the canonical
 * example: macOS {@code tabView:shouldSelectTabViewItem:} can veto cleanly,
 * but GTK 4's {@code GtkNotebook} only fires {@code switch-page} after the
 * fact — emulating veto requires programmatically switching back, which is
 * a UX wart. SWAT does not currently expose a vetoable tab-change event.
 *
 * <p>Veto is <em>not</em> recommended for focus changes — preventing focus
 * from leaving a control breaks keyboard accessibility and screen readers.
 */
public abstract class VetoableEvent {
    private boolean vetoed;

    /** For subclasses. */
    protected VetoableEvent() {}

    /** Suppress the action this event represents. Idempotent; once vetoed,
     *  subsequent calls are no-ops. */
    public final void veto() { vetoed = true; }

    /** True if any handler has called {@link #veto()}. */
    public final boolean isVetoed() { return vetoed; }
}
