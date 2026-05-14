package cc.nawt.samples.landmarks;

import cc.nawt.Button;
import cc.nawt.Capability;
import cc.nawt.ClipShape;
import cc.nawt.Column;
import cc.nawt.Coordinate;
import cc.nawt.Divider;
import cc.nawt.HeaderBar;
import cc.nawt.Image;
import cc.nawt.Label;
import cc.nawt.Map;
import cc.nawt.NavigationSplit;
import cc.nawt.Overlay;
import cc.nawt.Row;
import cc.nawt.Sidebar;
import cc.nawt.SystemIcon;
import cc.nawt.Toolkit;
import cc.nawt.Ui;
import cc.nawt.Widget;
import cc.nawt.Window;
import cc.nawt.dialog.MessageDialog;
import cc.nawt.spi.Alignment;
import cc.nawt.spi.ChildLayoutConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * NAWT port of Apple's SwiftUI "Landmarks" macOS tutorial: a
 * {@link NavigationSplit} with a source-list {@link Sidebar} on the leading
 * pane and a detail view on the trailing pane. The detail view stacks a
 * MapKit map and a circular profile image via {@link Overlay}, followed by
 * the landmark's name, location, and description.
 *
 * <p>The toolbar carries a "Favourites" toggle that filters the sidebar.
 */
public final class Landmarks {

    private static final List<Landmark> ALL = LandmarkData.all();

    private static Sidebar<Landmark> sidebar;
    private static NavigationSplit split;
    private static Widget currentDetail;
    private static Button favoritesToggle;
    private static boolean favoritesOnly;

    private Landmarks() {}

    public static void main(String[] args) {
        Toolkit.launch("Landmarks", Landmarks::buildUi);
    }

    private static void buildUi() {
        Toolkit.onAbout(Landmarks::showAbout);

        if (!Toolkit.supports(Capability.NAVIGATION_SPLIT)
            || !Toolkit.supports(Capability.SIDEBAR)
            || !Toolkit.supports(Capability.MAP)) {
            MessageDialog.builder()
                .style(MessageDialog.Style.ERROR)
                .title("Unsupported backend")
                .message("Landmarks requires NavigationSplit, Sidebar, and Map support — "
                    + "currently macOS only.")
                .buttons("Close")
                .show();
            Toolkit.shutdown();
            return;
        }

        sidebar = Sidebar.of(visibleLandmarks(), Landmarks::sidebarRow);
        sidebar.onSelectionChangeSync(e -> {
            Landmark l = e.selectedItem();
            if (l == null) return;
            installDetail(buildDetail(l));
        });

        // Start with the first landmark selected so the detail pane is populated.
        currentDetail = buildDetail(ALL.get(0));
        split = NavigationSplit.builder()
            .sidebar(sidebar)
            .detail(currentDetail)
            .sidebarMinWidth(220)
            .sidebarWidth(280)
            .build();

        favoritesToggle = Button.of(SystemIcon.STAR, "Favourites").onClick(e ->
            Ui.invokeLater(Landmarks::toggleFavoritesFilter));

        Window.builder()
            .title("Landmarks")
            .size(1100, 720)
            .resizable(true)
            .headerBar(HeaderBar.builder().start(favoritesToggle).build())
            .content(split)
            .build()
            .show();

        // Schedule the initial selection on the UI loop — the table view needs
        // its data source to be wired up before selectRowIndexes: takes effect.
        Ui.invokeLater(() -> sidebar.selectIndex(0));
    }

    private static List<Landmark> visibleLandmarks() {
        if (!favoritesOnly) return ALL;
        List<Landmark> out = new ArrayList<>();
        for (Landmark l : ALL) if (l.isFavorite()) out.add(l);
        return out;
    }

    private static void toggleFavoritesFilter() {
        favoritesOnly = !favoritesOnly;
        favoritesToggle.icon(favoritesOnly ? SystemIcon.STAR_FILL : SystemIcon.STAR);
        sidebar.items(visibleLandmarks());
        Ui.invokeLater(() -> sidebar.selectIndex(0));
    }

    /**
     * Build a sidebar row: a 40pt circular thumbnail, the name, a secondary
     * "Park, State" subtitle, and a small star if this landmark is favourited.
     */
    private static Widget sidebarRow(Landmark l) {
        Image thumb = Image.fromResource(Landmark.class, l.imageResource())
            .size(40, 40)
            .clipShape(ClipShape.CIRCLE);

        Column nameColumn = Column.builder()
            .spacing(2)
            .alignCross(Alignment.START)
            .add(Label.of(l.name()).fontSize(13))
            .add(Label.of(l.park() + ", " + l.state()).fontSize(11).secondary())
            .build();

        Row.Builder row = Row.builder()
            .spacing(10)
            .padding(8)
            .alignCross(Alignment.CENTER)
            .add(thumb)
            .add(nameColumn, ChildLayoutConfig.EXPAND);

        if (l.isFavorite()) {
            row.add(Label.of("★").fontSize(13).secondary());
        }
        return row.build();
    }

    /**
     * Build the detail pane: an overlay of a MapKit hero map and a circular
     * profile image, followed by the landmark name, "Park, State" line, a
     * divider, and the description.
     */
    private static Widget buildDetail(Landmark l) {
        Coordinate c = l.coordinate();

        Map map = Map.builder()
            .center(c)
            .zoom(11.0)
            .pin(c, l.name())
            .interactive(true)
            .build();

        // Sidebar thumbnails render correctly because clipShape(CIRCLE) sets
        // wantsLayer=true and routes NSImageView through its layer. Without a
        // clip the detail hero hits the standard drawing path, which appears
        // not to render reliably inside this NavigationSplit detail Column.
        // Keep clipShape on so the image uses the layer-backed path.
        Image hero = Image.fromResource(Landmark.class, l.imageResource())
            .size(180, 180)
            .clipShape(ClipShape.CIRCLE);

        Label title = Label.of(l.name()).fontSize(28);
        Label location = Label.of(l.park() + "  •  " + l.state()).secondary();
        Label aboutHeading = Label.of("About " + l.name()).fontSize(17);
        Label description = Label.of(l.description());

        // MKMapView's Metal-backed layer doesn't composite reliably with
        // sibling NSView layers, so the circular profile sits below the map
        // in its own row rather than overlaid on it. Inline the hero with
        // alignSelf=CENTER so the Column doesn't stretch a 180pt image to
        // the column's full width via the default STRETCH cross-axis rule.
        // Overlay: map fills the band, hero floats centered on top — matches
        // the SwiftUI Landmarks visual where the circular profile photo
        // straddles the map. We previously suspected MKMapView's Metal-backed
        // layer wouldn't composite with sibling NSViews; testing now that
        // the rest of the layout is correct.
        Overlay mapWithHero = Overlay.of(map, hero, Alignment.CENTER);

        return Column.builder()
            .spacing(12).padding(20)
            .add(mapWithHero, ChildLayoutConfig.EXPAND)
            .add(title)
            .add(location)
            .add(Divider.horizontal())
            .add(aboutHeading)
            .add(description)
            .build();
    }

    private static void installDetail(Widget newDetail) {
        Widget old = currentDetail;
        currentDetail = newDetail;
        split.detail(newDetail);
        if (old != null) {
            Ui.invokeLater(old::close);
        }
    }

    private static void showAbout() {
        MessageDialog.builder()
            .style(MessageDialog.Style.INFO)
            .title("About Landmarks")
            .message("Landmarks")
            .details("A NAWT port of Apple's SwiftUI Landmarks tutorial. Twelve sample national-park "
                + "landmarks, source-list sidebar, MapKit detail map.")
            .buttons("Close")
            .show();
    }
}
