package io.github.swat.backend.macos;

import io.github.swat.spi.FrameConfig;
import io.github.swat.spi.FramePeer;
import io.github.swat.spi.Peer;

import java.lang.foreign.MemorySegment;

/** NSBox in primary style with a title — macOS's GroupBox analog. */
final class MacosFramePeer implements FramePeer {

    private static final long NS_TITLE_AT_TOP = 2L;

    private final MemorySegment view;
    private MemorySegment childView;

    MacosFramePeer(FrameConfig cfg) {
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSBox"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));
        Objc.sendVoidLong(v, Objc.sel("setBoxType:"), 0L);
        Objc.sendVoidLong(v, Objc.sel("setTitlePosition:"), NS_TITLE_AT_TOP);
        Objc.sendVoid(v, Objc.sel("setTitle:"), NSString.from(cfg.title()));
        Objc.sendVoidBool(v, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);
        // Frame should stretch to fill its parent's cross axis. Default
        // NSBox hugging would resist growth.
        MacosContainerPeer.setContentHuggingPriority(v, 1, 0L); // horizontal
        this.view = v;
    }

    MemorySegment view() { return view; }

    @Override public void setTitle(String title) {
        Objc.sendVoid(view, Objc.sel("setTitle:"), NSString.from(title));
    }

    @Override public void setContent(Peer content) {
        MemorySegment cv = MacosContainerPeer.peerView(content);
        if (this.childView != null && this.childView.address() != 0) {
            Objc.sendVoid(this.childView, Objc.sel("removeFromSuperview"));
        }
        // Add our content as a subview of NSBox's existing internal content
        // view (rather than replacing it via setContentView:). NSBox manages
        // the geometry of its own contentView — including title clearance and
        // border inset — and pinning our content to that view's edges via
        // Auto Layout makes our view track the proper content area.
        MemorySegment boxContent = Objc.sendPtr(view, Objc.sel("contentView"));
        Objc.sendVoid(boxContent, Objc.sel("addSubview:"), cv);
        pin(cv, "topAnchor", boxContent, "topAnchor");
        pin(cv, "leadingAnchor", boxContent, "leadingAnchor");
        pin(cv, "trailingAnchor", boxContent, "trailingAnchor");
        pin(cv, "bottomAnchor", boxContent, "bottomAnchor");
        this.childView = cv;
    }

    private static void pin(MemorySegment a, String aAnchor, MemorySegment b, String bAnchor) {
        MemorySegment anchorA = Objc.sendPtr(a, Objc.sel(aAnchor));
        MemorySegment anchorB = Objc.sendPtr(b, Objc.sel(bAnchor));
        MemorySegment c = Objc.sendPtr(anchorA, Objc.sel("constraintEqualToAnchor:"), anchorB);
        Objc.sendVoidBool(c, Objc.sel("setActive:"), true);
    }

    @Override public void close() { Objc.sendVoid(view, Objc.sel("release")); }
}
