package io.github.swat.backend.gtk;

import io.github.swat.spi.TextFieldConfig;
import io.github.swat.spi.TextFieldPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.BiConsumer;

final class GtkTextFieldPeer implements TextFieldPeer {

    private final MemorySegment widget;
    private volatile String lastText;
    private volatile BiConsumer<String, String> trigger;

    GtkTextFieldPeer(TextFieldConfig cfg) {
        this.lastText = cfg.initialText() == null ? "" : cfg.initialText();
        MemorySegment w = Gtk.gtk_entry_new();
        this.widget = Gtk.g_object_ref(w);
        if (!lastText.isEmpty()) {
            Gtk.gtk_editable_set_text(widget, lastText);
        }
        GtkSignals.connectVoid(widget, "changed", this::fireChange);
    }

    MemorySegment widget() { return widget; }

    private void fireChange() {
        BiConsumer<String, String> t = trigger;
        if (t == null) return;
        String oldText = lastText;
        String newText = readText();
        lastText = newText;
        try { t.accept(oldText, newText); }
        catch (Throwable th) { th.printStackTrace(); }
    }

    private String readText() {
        String s = Gtk.gtk_editable_get_text(widget);
        return s == null ? "" : s;
    }

    @Override public void setText(String text) {
        String newText = text == null ? "" : text;
        Gtk.gtk_editable_set_text(widget, newText);
        lastText = newText;
    }

    @Override public String getText() { return readText(); }

    @Override public void onTextChange(BiConsumer<String, String> trigger) { this.trigger = trigger; }

    @Override public void close() { Gtk.g_object_unref(widget); }
}
