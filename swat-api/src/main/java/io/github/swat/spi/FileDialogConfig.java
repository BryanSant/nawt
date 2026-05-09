package io.github.swat.spi;

/**
 * Configuration for {@code showFileOpenDialog} and {@code showFileSaveDialog}.
 *
 * @param title             window title for the file picker
 * @param initialDirectory  filesystem path the picker opens at, or {@code null} for the system default
 * @param defaultName       suggested filename for save dialogs, or {@code null}
 */
public record FileDialogConfig(String title, String initialDirectory, String defaultName) {
    public FileDialogConfig {
        if (title == null) title = "";
        if (initialDirectory != null && initialDirectory.isBlank()) initialDirectory = null;
        if (defaultName != null && defaultName.isBlank()) defaultName = null;
    }
}
