package io.github.swat.backend.gtk;

import io.github.swat.spi.FileDialogConfig;
import io.github.swat.spi.FolderDialogConfig;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GtkFileDialog (4.10+) async pickers. Each kind ({@code open}, {@code save},
 * {@code selectFolder}) shares a {@code GAsyncReadyCallback} upcall that
 * dispatches via per-call tokens to the right {@code finish} routine.
 */
final class GtkFileDialog {
    private GtkFileDialog() {}

    private enum Kind { OPEN, SAVE, FOLDER }

    private static final AtomicLong NEXT_TOKEN = new AtomicLong(1);

    private record Pending(MemorySegment dialog, CompletableFuture<String> future, Kind kind) {}
    private static final ConcurrentHashMap<Long, Pending> IN_FLIGHT = new ConcurrentHashMap<>();

    private static final MemorySegment ASYNC_CALLBACK_STUB;

    static {
        try {
            MethodHandle mh = MethodHandles.lookup().findStatic(
                GtkFileDialog.class, "finishCallback",
                MethodType.methodType(void.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class));
            ASYNC_CALLBACK_STUB = Linker.nativeLinker().upcallStub(
                mh,
                FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR, Gtk.PTR),
                Gtk.GLOBAL);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static CompletableFuture<String> showOpen(FileDialogConfig cfg) {
        return launch(cfg.title(), cfg.initialDirectory(), null, Kind.OPEN);
    }

    static CompletableFuture<String> showSave(FileDialogConfig cfg) {
        return launch(cfg.title(), cfg.initialDirectory(), cfg.defaultName(), Kind.SAVE);
    }

    static CompletableFuture<String> showFolder(FolderDialogConfig cfg) {
        return launch(cfg.title(), cfg.initialDirectory(), null, Kind.FOLDER);
    }

    private static CompletableFuture<String> launch(String title, String initialDir,
                                                    String defaultName, Kind kind) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            MemorySegment dialog = Gtk.gtk_file_dialog_new();
            if (title != null && !title.isBlank()) {
                Gtk.gtk_file_dialog_set_title(dialog, title);
            }
            if (initialDir != null && !initialDir.isBlank()) {
                MemorySegment folder = Gtk.g_file_new_for_path(initialDir);
                Gtk.gtk_file_dialog_set_initial_folder(dialog, folder);
                Gtk.g_object_unref(folder);
            }
            if (kind == Kind.SAVE && defaultName != null && !defaultName.isBlank()) {
                Gtk.gtk_file_dialog_set_initial_name(dialog, defaultName);
            }

            long token = NEXT_TOKEN.getAndIncrement();
            IN_FLIGHT.put(token, new Pending(dialog, future, kind));
            MemorySegment userData = MemorySegment.ofAddress(token);

            switch (kind) {
                case OPEN -> Gtk.gtk_file_dialog_open(dialog,
                    MemorySegment.NULL, MemorySegment.NULL, ASYNC_CALLBACK_STUB, userData);
                case SAVE -> Gtk.gtk_file_dialog_save(dialog,
                    MemorySegment.NULL, MemorySegment.NULL, ASYNC_CALLBACK_STUB, userData);
                case FOLDER -> Gtk.gtk_file_dialog_select_folder(dialog,
                    MemorySegment.NULL, MemorySegment.NULL, ASYNC_CALLBACK_STUB, userData);
            }
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    @SuppressWarnings("unused") // upcalled from GTK
    private static void finishCallback(MemorySegment source, MemorySegment result,
                                       MemorySegment userData) {
        long token = userData.address();
        Pending pending = IN_FLIGHT.remove(token);
        if (pending == null) return;
        try {
            MemorySegment gFile = switch (pending.kind()) {
                case OPEN -> Gtk.gtk_file_dialog_open_finish(source, result, MemorySegment.NULL);
                case SAVE -> Gtk.gtk_file_dialog_save_finish(source, result, MemorySegment.NULL);
                case FOLDER -> Gtk.gtk_file_dialog_select_folder_finish(source, result, MemorySegment.NULL);
            };
            if (gFile == null || gFile.address() == 0) {
                pending.future().complete(null);
                return;
            }
            try {
                MemorySegment cstr = Gtk.g_file_get_path(gFile);
                if (cstr == null || cstr.address() == 0) {
                    pending.future().complete(null);
                    return;
                }
                String path = cstr.reinterpret(Long.MAX_VALUE).getString(0);
                Gtk.g_free(cstr);
                pending.future().complete(path);
            } finally {
                Gtk.g_object_unref(gFile);
            }
        } catch (Throwable t) {
            pending.future().completeExceptionally(t);
        } finally {
            try { Gtk.g_object_unref(pending.dialog()); }
            catch (Throwable ignored) {}
        }
    }
}
