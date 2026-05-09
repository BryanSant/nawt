package io.github.swat.samples;

import io.github.swat.Button;
import io.github.swat.Column;
import io.github.swat.Label;
import io.github.swat.ListView;
import io.github.swat.Toolkit;
import io.github.swat.Ui;
import io.github.swat.Window;
import io.github.swat.dialog.MessageDialog;
import io.github.swat.menu.Menu;
import io.github.swat.menu.MenuAction;
import io.github.swat.menu.MenuBar;
import io.github.swat.menu.MenuSeparator;

import java.util.List;

/** Interactive demo: window with menu bar, a list view, and a button that pops a confirm dialog. */
public final class Demo {

    private Demo() {}

    public static void main(String[] args) {
        Toolkit.launch(Demo::buildUi);
    }

    private static void buildUi() {
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
            .add(io.github.swat.Row.of(pickFile, pickFolder))
            .build();

        MenuBar menuBar = MenuBar.of(
            Menu.builder("File")
                .action("New",  e -> Ui.invokeLater(() -> status.text("File → New clicked.")))
                .action("Open", e -> Ui.invokeLater(() -> status.text("File → Open clicked.")))
                .add(MenuSeparator.of())
                .action("Close window", e -> Ui.invokeLater(Toolkit::shutdown))
                .build(),
            Menu.builder("Help")
                .action("About SWAT", e -> {
                    MessageDialog.builder()
                        .style(MessageDialog.Style.INFO)
                        .title("About SWAT")
                        .message("A spiritual successor to AWT/SWT.")
                        .details("Built on Java 25, FFM, and virtual threads.")
                        .buttons("Close")
                        .show();
                })
                .build()
        );

        Window.builder()
            .title("SWAT Demo")
            .size(420, 360)
            .menuBar(menuBar)
            .content(content)
            .build()
            .show();
    }
}
