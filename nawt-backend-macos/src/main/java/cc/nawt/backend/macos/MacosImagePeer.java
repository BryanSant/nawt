package cc.nawt.backend.macos;

import cc.nawt.ClipShape;
import cc.nawt.spi.ImageConfig;
import cc.nawt.spi.ImagePeer;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * The widget's exposed view is a plain NSView "host" that we own; the actual
 * NSImageView lives inside it, pinned to fill on all four edges. The host's
 * CALayer holds the size constraints and the corner-radius mask, leaving the
 * NSImageView to render through its standard drawing path — which is what
 * makes the image visible inside an NSStackView (rather than vanishing the
 * way a layer-backed NSImageView does in some parent contexts).
 */
final class MacosImagePeer implements ImagePeer {

    private static final MemoryLayout NSSIZE = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("width"),
        ValueLayout.JAVA_DOUBLE.withName("height"));

    private final MemorySegment host;       // NSView, retained — the peer's exposed view
    private final MemorySegment imageView;  // NSImageView, retained — host's content
    private MemorySegment image;            // NSImage, retained
    private MemorySegment widthConstraint;
    private MemorySegment heightConstraint;
    private int widthHint;
    private int heightHint;
    private ClipShape clipShape;

    MacosImagePeer(ImageConfig cfg) {
        MemorySegment hostView = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSView")), Objc.sel("init"));
        Objc.sendVoidBool(hostView, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);
        Objc.sendVoidBool(hostView, Objc.sel("setWantsLayer:"), true);

        MemorySegment iv = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSImageView")), Objc.sel("init"));
        Objc.sendVoidBool(iv, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);
        // NSImageScaleProportionallyUpOrDown = 0 — scale to fit the imageView's bounds.
        Objc.sendVoidLong(iv, Objc.sel("setImageScaling:"), 0L);

        // Pin imageView to fill host on all four edges.
        Objc.sendVoid(hostView, Objc.sel("addSubview:"), iv);
        pinEqualAnchor(iv, "leadingAnchor", hostView, "leadingAnchor");
        pinEqualAnchor(iv, "trailingAnchor", hostView, "trailingAnchor");
        pinEqualAnchor(iv, "topAnchor", hostView, "topAnchor");
        pinEqualAnchor(iv, "bottomAnchor", hostView, "bottomAnchor");

        this.host = Objc.sendPtr(hostView, Objc.sel("retain"));
        this.imageView = Objc.sendPtr(iv, Objc.sel("retain"));

        if (cfg.path() != null && !cfg.path().isEmpty()) setPath(cfg.path());
        else if (cfg.data() != null) setData(cfg.data());

        if (cfg.clipShape() != null) setClipShape(cfg.clipShape());
    }

    /** The host view that container peers add into their NSStackView. */
    MemorySegment view() { return host; }

    private void replaceImage(MemorySegment newImage) {
        if (image != null && image.address() != 0) {
            Objc.sendVoid(image, Objc.sel("release"));
        }
        image = newImage;
        Objc.sendVoid(imageView, Objc.sel("setImage:"),
            newImage == null ? Objc.NIL : newImage);
    }

    @Override public void setPath(String path) {
        if (path == null || path.isEmpty()) { replaceImage(null); return; }
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSImage"));
        MemorySegment img = Objc.sendPtr(alloc, Objc.sel("initWithContentsOfFile:"), NSString.from(path));
        replaceImage(img);
    }

    @Override public void setData(byte[] data) {
        if (data == null || data.length == 0) { replaceImage(null); return; }
        MemorySegment img;
        try (var arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(data.length);
            buf.asByteBuffer().put(data);
            MemorySegment ns;
            try {
                ns = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSUINT))
                    .invoke(Objc.cls("NSData"), Objc.sel("dataWithBytes:length:"),
                        buf, (long) data.length);
            } catch (Throwable t) { throw new RuntimeException(t); }
            MemorySegment alloc = Objc.send_alloc(Objc.cls("NSImage"));
            img = Objc.sendPtr(alloc, Objc.sel("initWithData:"), ns);
        }
        replaceImage(img);
    }

    @Override public void setClipShape(ClipShape shape) {
        this.clipShape = shape;
        applyClip();
    }

    @Override public void setSize(int width, int height) {
        this.widthHint = Math.max(0, width);
        this.heightHint = Math.max(0, height);
        widthConstraint = replaceAnchorConstraint(host, "widthAnchor", widthHint, widthConstraint);
        heightConstraint = replaceAnchorConstraint(host, "heightAnchor", heightHint, heightConstraint);
        // Size change can affect the corner-radius needed for a CIRCLE clip;
        // re-apply so the mask tracks the new known dimensions.
        if (clipShape != null) applyClip();
    }

    /** Install the cornerRadius mask on the HOST view's layer (not the NSImageView's). */
    private void applyClip() {
        MemorySegment layer = Objc.sendPtr(host, Objc.sel("layer"));
        if (layer == null || layer.address() == 0) return;

        if (clipShape == null) {
            setCornerRadius(layer, 0.0);
            Objc.sendVoidBool(layer, Objc.sel("setMasksToBounds:"), false);
            return;
        }
        switch (clipShape) {
            case ClipShape.Circle ignored -> {
                int min = widthHint > 0 && heightHint > 0
                    ? Math.min(widthHint, heightHint)
                    : Math.max(widthHint, heightHint);
                double radius = min > 0 ? min / 2.0 : 9999.0;
                setCornerRadius(layer, radius);
                Objc.sendVoidBool(layer, Objc.sel("setMasksToBounds:"), true);
            }
            case ClipShape.RoundedRect rr -> {
                setCornerRadius(layer, rr.cornerRadius());
                Objc.sendVoidBool(layer, Objc.sel("setMasksToBounds:"), true);
            }
        }
    }

    private static void pinEqualAnchor(MemorySegment a, String aAnchor, MemorySegment b, String bAnchor) {
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

    private static MemorySegment replaceAnchorConstraint(
            MemorySegment v, String anchorName, int value, MemorySegment existing) {
        if (existing != null && existing.address() != 0) {
            Objc.sendVoidBool(existing, Objc.sel("setActive:"), false);
            Objc.sendVoid(existing, Objc.sel("release"));
        }
        if (value <= 0) return MemorySegment.NULL;
        MemorySegment anchor = Objc.sendPtr(v, Objc.sel(anchorName));
        MemorySegment c;
        try {
            c = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(anchor, Objc.sel("constraintEqualToConstant:"), (double) value);
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoidBool(c, Objc.sel("setActive:"), true);
        return Objc.sendPtr(c, Objc.sel("retain"));
    }

    private static void setCornerRadius(MemorySegment layer, double r) {
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(layer, Objc.sel("setCornerRadius:"), r);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override public void close() {
        if (widthConstraint != null && widthConstraint.address() != 0) {
            Objc.sendVoid(widthConstraint, Objc.sel("release"));
        }
        if (heightConstraint != null && heightConstraint.address() != 0) {
            Objc.sendVoid(heightConstraint, Objc.sel("release"));
        }
        replaceImage(null);
        Objc.sendVoid(imageView, Objc.sel("release"));
        Objc.sendVoid(host, Objc.sel("release"));
    }
}
