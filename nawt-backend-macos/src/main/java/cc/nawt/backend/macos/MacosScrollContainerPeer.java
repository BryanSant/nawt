package cc.nawt.backend.macos;

import cc.nawt.spi.Peer;
import cc.nawt.spi.ScrollContainerConfig;
import cc.nawt.spi.ScrollContainerPeer;

import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;

final class MacosScrollContainerPeer implements ScrollContainerPeer {

    // NSClipView subclass whose -isFlipped returns YES. Without this, the
    // default (non-flipped) clip view places the document view's frame (0,0)
    // at the bottom-left of the visible area, so content "appears at the
    // bottom" of the scroll view.
    //
    // The IMP for -isFlipped is bound to libsystem's pthread_main_np — a C
    // function with no args that returns 1 on the main thread. AppKit only
    // queries isFlipped from the main thread, so this behaves identically to
    // a hand-written `return YES` while sidestepping the FFM upcall stub
    // that crashes during JVM shutdown when AppKit drains autorelease pools
    // after main() returns.
    private static final MemorySegment FLIPPED_CLIP_CLASS = registerFlippedClipClass();

    private final MemorySegment view;

    MacosScrollContainerPeer(ScrollContainerConfig cfg) {
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSScrollView"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));
        Objc.sendVoidBool(v, Objc.sel("setHasVerticalScroller:"), cfg.vertical());
        Objc.sendVoidBool(v, Objc.sel("setHasHorizontalScroller:"), cfg.horizontal());
        Objc.sendVoidBool(v, Objc.sel("setAutohidesScrollers:"), true);
        Objc.sendVoidBool(v, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        // Replace the default NSClipView with our flipped subclass. Leave
        // translatesAutoresizingMaskIntoConstraints at its default so
        // NSScrollView's `tile` method can position the clip view via frames
        // (the standard pattern).
        MemorySegment flippedClip = Objc.sendPtr(
            Objc.send_alloc(FLIPPED_CLIP_CLASS), Objc.sel("init"));
        Objc.sendVoid(v, Objc.sel("setContentView:"), flippedClip);
        Objc.sendVoid(flippedClip, Objc.sel("release"));

        this.view = v;
    }

    MemorySegment view() { return view; }

    @Override public void setContent(Peer content) {
        MemorySegment cv = MacosContainerPeer.peerView(content);
        Objc.sendVoid(view, Objc.sel("setDocumentView:"), cv);

        // Pin top/leading/trailing — no bottom — so the document view is at
        // its intrinsic content height. With Fill-distribution stack views,
        // an intrinsic-height column has no slack to redistribute, which
        // prevents gaps from appearing between children. Vertical scrolling
        // kicks in when content exceeds the visible area.
        MemorySegment clip = Objc.sendPtr(view, Objc.sel("contentView"));
        pin(cv, "topAnchor", clip, "topAnchor");
        pin(cv, "leadingAnchor", clip, "leadingAnchor");
        pin(cv, "trailingAnchor", clip, "trailingAnchor");
    }

    private static void pin(MemorySegment a, String aAnchor, MemorySegment b, String bAnchor) {
        MemorySegment anchorA = Objc.sendPtr(a, Objc.sel(aAnchor));
        MemorySegment anchorB = Objc.sendPtr(b, Objc.sel(bAnchor));
        MemorySegment c = Objc.sendPtr(anchorA, Objc.sel("constraintEqualToAnchor:"), anchorB);
        Objc.sendVoidBool(c, Objc.sel("setActive:"), true);
    }

    @Override public void close() { Objc.sendVoid(view, Objc.sel("release")); }

    /* ---------- Flipped NSClipView subclass ---------- */

    private static MemorySegment registerFlippedClipClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSClipView"), "NawtFlippedClipView");
        MemorySegment imp = Linker.nativeLinker().defaultLookup()
            .find("pthread_main_np")
            .orElseThrow(() -> new RuntimeException("pthread_main_np not found"));
        Objc.addMethod(cls, Objc.sel("isFlipped"), imp, "B@:");
        Objc.registerClassPair(cls);
        return cls;
    }
}
