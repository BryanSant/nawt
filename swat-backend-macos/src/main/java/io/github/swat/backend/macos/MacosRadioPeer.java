package io.github.swat.backend.macos;

import io.github.swat.spi.RadioConfig;
import io.github.swat.spi.RadioPeer;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * NSButton with NSButtonTypeRadio. Radios placed in the same group share a
 * Java-managed group list so selecting one deselects the others — NSButton's
 * automatic radio-group behavior only applies when buttons share a target/action
 * inside the same superview, which we can't rely on cross-container.
 */
final class MacosRadioPeer implements RadioPeer {

    private static final long NS_BUTTON_TYPE_RADIO = 4L;

    private final MemorySegment view;
    private final MemorySegment target;
    private List<MacosRadioPeer> group;
    private volatile Consumer<Boolean> trigger;

    MacosRadioPeer(RadioConfig cfg) {
        this.target = Objc.sendPtr(Delegates.newToggleTarget(), Objc.sel("retain"));

        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSButton"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));
        Objc.sendVoidLong(v, Objc.sel("setButtonType:"), NS_BUTTON_TYPE_RADIO);
        Objc.sendVoid(v, Objc.sel("setTitle:"), NSString.from(cfg.text()));
        Objc.sendVoidLong(v, Objc.sel("setState:"), cfg.initialSelected() ? 1L : 0L);

        Objc.sendVoid(v, Objc.sel("setTarget:"), target);
        Objc.sendVoid(v, Objc.sel("setAction:"), Delegates.TOGGLE_ACTION_SEL);
        this.view = v;

        List<MacosRadioPeer> g = new ArrayList<>();
        g.add(this);
        this.group = g;

        Delegates.TOGGLE_HANDLERS.put(target.address(), this::handleClick);
    }

    MemorySegment view() { return view; }

    private void handleClick() {
        boolean selected = isSelected();
        if (selected) {
            for (MacosRadioPeer peer : group) {
                if (peer != this && peer.isSelected()) {
                    peer.setSelectedSilently(false);
                    Consumer<Boolean> peerTrigger = peer.trigger;
                    if (peerTrigger != null) {
                        try { peerTrigger.accept(false); }
                        catch (Throwable th) { th.printStackTrace(); }
                    }
                }
            }
        }
        Consumer<Boolean> t = trigger;
        if (t != null) {
            try { t.accept(selected); }
            catch (Throwable th) { th.printStackTrace(); }
        }
    }

    private void setSelectedSilently(boolean selected) {
        Objc.sendVoidLong(view, Objc.sel("setState:"), selected ? 1L : 0L);
    }

    @Override public void setText(String text) {
        Objc.sendVoid(view, Objc.sel("setTitle:"), NSString.from(text));
    }

    @Override public void setSelected(boolean selected) {
        setSelectedSilently(selected);
        if (selected) {
            for (MacosRadioPeer peer : group) {
                if (peer != this && peer.isSelected()) peer.setSelectedSilently(false);
            }
        }
    }

    @Override public boolean isSelected() {
        return Objc.sendLong(view, Objc.sel("state")) != 0;
    }

    @Override public void groupWith(RadioPeer other) {
        if (!(other instanceof MacosRadioPeer m)) {
            throw new IllegalArgumentException("Cannot group across backends: " + other.getClass());
        }
        List<MacosRadioPeer> merged = new ArrayList<>(this.group);
        for (MacosRadioPeer p : m.group) {
            if (!merged.contains(p)) merged.add(p);
        }
        for (MacosRadioPeer p : merged) p.group = merged;
    }

    @Override public void onToggle(Consumer<Boolean> trigger) { this.trigger = trigger; }

    @Override public void close() {
        Delegates.TOGGLE_HANDLERS.remove(target.address());
        Objc.sendVoid(view, Objc.sel("setTarget:"), Objc.NIL);
        group.remove(this);
        Objc.sendVoid(view, Objc.sel("release"));
        Objc.sendVoid(target, Objc.sel("release"));
    }
}
