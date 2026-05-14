package cc.nawt.spi;

/**
 * Configuration for {@code showFolderDialog}.
 *
 * @param title             window title for the folder picker
 * @param initialDirectory  filesystem path the picker opens at, or {@code null} for the system default
 */
public record FolderDialogConfig(String title, String initialDirectory) {
    public FolderDialogConfig {
        if (title == null) title = "";
        if (initialDirectory != null && initialDirectory.isBlank()) initialDirectory = null;
    }
}
