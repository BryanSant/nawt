package io.github.swat.backend.macos;

import io.github.swat.spi.ImageConfig;
import io.github.swat.spi.ImagePeer;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/** NSImageView wrapping an NSImage. */
final class MacosImagePeer implements ImagePeer {

    private final MemorySegment view;
    private MemorySegment image;

    MacosImagePeer(ImageConfig cfg) {
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSImageView"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));
        Objc.sendVoidBool(v, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);
        // NSImageScaleProportionallyUpOrDown = 0
        Objc.sendVoidLong(v, Objc.sel("setImageScaling:"), 0L);
        this.view = v;

        if (cfg.path() != null && !cfg.path().isEmpty()) setPath(cfg.path());
        else if (cfg.data() != null) setData(cfg.data());
    }

    MemorySegment view() { return view; }

    private void replaceImage(MemorySegment newImage) {
        if (image != null && image.address() != 0) {
            Objc.sendVoid(image, Objc.sel("release"));
        }
        image = newImage;
        Objc.sendVoid(view, Objc.sel("setImage:"), newImage == null ? Objc.NIL : newImage);
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
            // [NSData dataWithBytes:length:]
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

    @Override public void close() {
        replaceImage(null);
        Objc.sendVoid(view, Objc.sel("release"));
    }
}
