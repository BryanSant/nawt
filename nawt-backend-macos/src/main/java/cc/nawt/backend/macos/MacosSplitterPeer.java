package cc.nawt.backend.macos;

import cc.nawt.spi.Orientation;
import cc.nawt.spi.Peer;
import cc.nawt.spi.SplitterConfig;
import cc.nawt.spi.SplitterPeer;

import java.lang.foreign.MemorySegment;

final class MacosSplitterPeer implements SplitterPeer {

    private final MemorySegment view;
    private MemorySegment startView;
    private MemorySegment endView;

    MacosSplitterPeer(SplitterConfig cfg) {
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSSplitView"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));
        // NSSplitViewDividerStyleThin = 2
        Objc.sendVoidLong(v, Objc.sel("setDividerStyle:"), 2L);
        Objc.sendVoidBool(v, Objc.sel("setVertical:"), cfg.orientation() == Orientation.HORIZONTAL);
        Objc.sendVoidBool(v, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);
        // Splitter should stretch in both axes when placed inside a stack —
        // its panes share the available space.
        MacosContainerPeer.setContentHuggingPriority(v, 1, 0L);
        MacosContainerPeer.setContentHuggingPriority(v, 1, 1L);
        this.view = v;
    }

    MemorySegment view() { return view; }

    /** Detach {@code current} from the splitter (if non-null) and arrange the
     *  new {@code child}'s view as the next arranged subview. Returns the new
     *  slot value so callers can update their stored field. */
    private MemorySegment replaceSlot(MemorySegment current, Peer child) {
        MemorySegment cv = MacosContainerPeer.peerView(child);
        if (current != null && current.address() != 0) {
            Objc.sendVoid(current, Objc.sel("removeFromSuperview"));
        }
        Objc.sendVoid(view, Objc.sel("addArrangedSubview:"), cv);
        return cv;
    }

    @Override public void setStart(Peer child) { startView = replaceSlot(startView, child); }
    @Override public void setEnd(Peer child) { endView = replaceSlot(endView, child); }

    @Override public void close() { Objc.sendVoid(view, Objc.sel("release")); }
}
