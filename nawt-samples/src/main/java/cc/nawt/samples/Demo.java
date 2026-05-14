package cc.nawt.samples;

import cc.nawt.Button;
import cc.nawt.Capability;
import cc.nawt.Column;
import cc.nawt.HeaderBar;
import cc.nawt.Label;
import cc.nawt.ListView;
import cc.nawt.Toolkit;
import cc.nawt.Ui;
import cc.nawt.Window;
import cc.nawt.dialog.MessageDialog;
import cc.nawt.menu.Menu;
import cc.nawt.menu.MenuAction;
import cc.nawt.menu.MenuBar;
import cc.nawt.menu.MenuSeparator;

import java.util.List;

/** Interactive demo: window with menu bar, a list view, and a button that pops a confirm dialog. */
public final class Demo {

    private Demo() {}

    public static void main(String[] args) {
        Toolkit.launch("NAWT Demo", Demo::buildUi);
    }

    private static void buildUi() {
        // About goes in the platform's primary surface (macOS App menu / GTK
        // burger menu). The framework places it; samples just register intent.
        Toolkit.onAbout(Demo::showAbout);

        Label status = Label.of("Pick an item.");
        ListView fruits = ListView.of("Apple", "Banana", "Cherry", "Date", "Elderberry");

        fruits.onSelectionChange(e -> {
            String text = e.value() == null ? "Nothing selected." : "Selected: " + e.value();
            Ui.invokeLater(() -> status.text(text));
        });

        Button confirm = Button.of("Delete selected").onClick(e -> {
            int idx = fruits.selectedIndex();
            if (idx < 0) {
                MessageDialog.builder()
                    .style(MessageDialog.Style.INFO)
                    .title("No selection")
                    .message("Select an item first.")
                    .buttons("OK")
                    .show();
                return;
            }
            String item = fruits.items().get(idx);
            int choice = MessageDialog.builder()
                .style(MessageDialog.Style.QUESTION)
                .title("Confirm delete")
                .message("Delete \"" + item + "\"?")
                .details("This cannot be undone.")
                .buttons("Cancel", "Delete")
                .defaultButton(0)
                .show();
            if (choice == 1) {
                List<String> next = new java.util.ArrayList<>(fruits.items());
                next.remove(idx);
                Ui.invokeLater(() -> {
                    fruits.items(next);
                    status.text("Deleted: " + item);
                });
            }
        });

        Button pickFile = Button.of("Open file…").onClick(e ->
            Toolkit.showFileOpenDialog("Pick a file").thenAccept(path ->
                Ui.invokeLater(() -> status.text(path == null ? "Cancelled" : "Picked: " + path))));

        Button pickFolder = Button.of("Open folder…").onClick(e ->
            Toolkit.showFolderDialog("Pick a folder").thenAccept(path ->
                Ui.invokeLater(() -> status.text(path == null ? "Cancelled" : "Folder: " + path))));

        Column content = Column.builder()
            .spacing(12).padding(16)
            .add(status)
            .add(fruits)
            .add(confirm)
            .add(cc.nawt.Row.of(pickFile, pickFolder))
            .build();

        // Sample-specific "primary" commands. About is NOT in this list — it's
        // registered via Toolkit.onAbout above and the framework places it
        // natively (burger menu on GTK, App menu on macOS). HEADER_BAR_MENU
        // gates only the placement of these sample-owned items.
        Menu fileMenu = Menu.builder("File")
            .action("New",  e -> Ui.invokeLater(() -> status.text("File → New clicked.")))
            .action("Open", e -> Ui.invokeLater(() -> status.text("File → Open clicked.")))
            .add(MenuSeparator.of())
            .action("Close window", e -> Ui.invokeLater(Toolkit::shutdown))
            .build();

        boolean burgerMenuSupported = Toolkit.supports(Capability.HEADER_BAR_MENU);

        Window.Builder windowBuilder = Window.builder()
            .title("Demo")
            .size(420, 360)
            .content(content);

        if (burgerMenuSupported) {
            // Header-bar burger menu carries the sample's primary commands;
            // the framework appends an About item below in a trailing section.
            Menu burger = Menu.builder("")
                .action("Option 1", e -> Ui.invokeLater(() -> status.text("Option 1")))
                .action("Option 2", e -> Ui.invokeLater(() -> status.text("Option 2")))
                .action("Option 3", e -> Ui.invokeLater(() -> status.text("Option 3")))
                .build();
            windowBuilder
                .menuBar(MenuBar.of(fileMenu))
                .headerBar(HeaderBar.builder().menu(burger).build());
        } else {
            // No burger-menu idiom on this platform — surface the same items as
            // a top-level "Demo" menu in the global/window menu bar.
            Menu demoMenu = Menu.builder("Demo")
                .action("Option 1", e -> Ui.invokeLater(() -> status.text("Option 1")))
                .action("Option 2", e -> Ui.invokeLater(() -> status.text("Option 2")))
                .action("Option 3", e -> Ui.invokeLater(() -> status.text("Option 3")))
                .build();
            windowBuilder.menuBar(MenuBar.of(fileMenu, demoMenu));
        }

        windowBuilder.build().show();
    }

    private static void showAbout() {
        MessageDialog.builder()
            .style(MessageDialog.Style.INFO)
            .title("About NAWT")
            .message("A spiritual successor to AWT/SWT.")
            .details("Built on Java 25, FFM, and virtual threads.")
            .buttons("Close")
            .show();
    }
}
