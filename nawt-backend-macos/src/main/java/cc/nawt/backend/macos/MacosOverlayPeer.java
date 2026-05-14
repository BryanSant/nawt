package cc.nawt.backend.macos;

import cc.nawt.spi.Alignment;
import cc.nawt.spi.OverlayConfig;
import cc.nawt.spi.OverlayPeer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;

/**
 * Z-stack: a host {@code NSView} contains the background (pinned on all four
 * edges) and the foreground (intrinsic size, positioned by Auto Layout
 * constraints derived from the requested {@link Alignment}). The background
 * is added first so the foreground renders above it.
 */
final class MacosOverlayPeer implements OverlayPeer {

    private final MemorySegment view; // host NSView, retained

    MacosOverlayPeer(OverlayConfig cfg) {
        MemorySegment host = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSView")), Objc.sel("init"));
        Objc.sendVoidBool(host, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        MemorySegment bg = MacosContainerPeer.peerView(cfg.background());
        MemorySegment fg = MacosContainerPeer.peerView(cfg.foreground());

        Objc.sendVoid(host, Objc.sel("addSubview:"), bg);
        Objc.sendVoid(host, Objc.sel("addSubview:"), fg);

        Objc.sendVoidBool(bg, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);
        Objc.sendVoidBool(fg, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        // Background pins to every edge of the host.
        pinEqual(bg, "leadingAnchor", host, "leadingAnchor");
        pinEqual(bg, "trailingAnchor", host, "trailingAnchor");
        pinEqual(bg, "topAnchor", host, "topAnchor");
        pinEqual(bg, "bottomAnchor", host, "bottomAnchor");

        // Foreground positions by alignment, sized to its intrinsic content.
        Alignment a = cfg.alignment();
        switch (a) {
            case CENTER, BASELINE -> {
                pinEqual(fg, "centerXAnchor", host, "centerXAnchor");
                pinEqual(fg, "centerYAnchor", host, "centerYAnchor");
            }
            case START -> {
                pinEqual(fg, "leadingAnchor", host, "leadingAnchor");
                pinEqual(fg, "topAnchor", host, "topAnchor");
            }
            case END -> {
                pinEqual(fg, "trailingAnchor", host, "trailingAnchor");
                pinEqual(fg, "bottomAnchor", host, "bottomAnchor");
            }
            case STRETCH -> {
                pinEqual(fg, "leadingAnchor", host, "leadingAnchor");
                pinEqual(fg, "trailingAnchor", host, "trailingAnchor");
                pinEqual(fg, "topAnchor", host, "topAnchor");
                pinEqual(fg, "bottomAnchor", host, "bottomAnchor");
            }
        }

        this.view = Objc.sendPtr(host, Objc.sel("retain"));
    }

    MemorySegment view() { return view; }

    private static void pinEqual(MemorySegment a, String aAnchor, MemorySegment b, String bAnchor) {
        MemorySegment anchorA = Objc.sendPtr(a, Objc.sel(aAnchor));
        MemorySegment anchorB = Objc.sendPtr(b, Objc.sel(bAnchor));
        MemorySegment c;
        try {
            c = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(anchorA, Objc.sel("constraintEqualToAnchor:"), anchorB);
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoidBool(c, Objc.sel("setActive:"), true);
    }

    @Override public void close() {
        Objc.sendVoid(view, Objc.sel("release"));
    }
}
