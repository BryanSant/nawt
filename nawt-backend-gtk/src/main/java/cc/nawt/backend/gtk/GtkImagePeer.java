package cc.nawt.backend.gtk;

import cc.nawt.ClipShape;
import cc.nawt.spi.ImageConfig;
import cc.nawt.spi.ImagePeer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class GtkImagePeer implements ImagePeer {

    private final MemorySegment widget;

    GtkImagePeer(ImageConfig cfg) {
        MemorySegment w = Gtk.gtk_picture_new();
        this.widget = Gtk.g_object_ref(w);
        if (cfg.path() != null && !cfg.path().isEmpty()) setPath(cfg.path());
        else if (cfg.data() != null) setData(cfg.data());
    }

    MemorySegment widget() { return widget; }

    @Override public void setPath(String path) {
        Gtk.gtk_picture_set_filename(widget, (path == null || path.isEmpty()) ? null : path);
    }

    @Override public void setData(byte[] data) {
        if (data == null || data.length == 0) {
            Gtk.gtk_picture_set_paintable(widget, MemorySegment.NULL);
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(data.length);
            MemorySegment.copy(data, 0, buf, ValueLayout.JAVA_BYTE, 0, data.length);
            // g_bytes_new copies into its own buffer; our arena segment can
            // safely release at the end of the try-with-resources.
            MemorySegment bytes = Gtk.g_bytes_new(buf, data.length);
            try {
                MemorySegment texture = Gtk.gdk_texture_new_from_bytes(bytes, MemorySegment.NULL);
                if (texture == null || texture.address() == 0) {
                    throw new RuntimeException("gdk_texture_new_from_bytes failed (unsupported format?)");
                }
                Gtk.gtk_picture_set_paintable(widget, texture);
                // The picture took its own ref via set_paintable; drop ours.
                Gtk.g_object_unref(texture);
            } finally {
                Gtk.g_bytes_unref(bytes);
            }
        }
    }

    @Override public void setClipShape(ClipShape shape) {
        if (shape == null) return; // no-op: matches "no clip" default
        throw new UnsupportedOperationException(
            "Image.clipShape is not yet implemented on the GTK backend. "
            + "Check Toolkit.supports(Capability.IMAGE_CLIP) before calling.");
    }

    @Override public void close() {
        Gtk.g_object_unref(widget);
    }
}
