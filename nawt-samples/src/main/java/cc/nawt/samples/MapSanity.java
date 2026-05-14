package cc.nawt.samples;

import cc.nawt.Capability;
import cc.nawt.Column;
import cc.nawt.Coordinate;
import cc.nawt.Map;
import cc.nawt.Toolkit;
import cc.nawt.Window;

/**
 * Manual sanity check for the {@link Map} widget: opens a window with an
 * interactive MapKit view centred on Yosemite Valley with a pin. Run with
 * {@code ./gradlew :nawt-samples:mapSanity}.
 */
public final class MapSanity {

    private MapSanity() {}

    public static void main(String[] args) {
        Toolkit.launch("Map Sanity", MapSanity::buildUi);
    }

    private static void buildUi() {
        if (!Toolkit.supports(Capability.MAP)) {
            System.err.println("Map widget not supported on this backend; aborting.");
            Toolkit.shutdown();
            return;
        }
        Coordinate yosemite = Coordinate.of(37.7456, -119.5936);

        Map map = Map.builder()
            .center(yosemite)
            .zoom(11.0)
            .pin(yosemite, "Yosemite Valley")
            .interactive(true)
            .build();

        Window.builder()
            .title("Map Sanity")
            .size(720, 540)
            .content(Column.builder().padding(0).spacing(0).expand(map).build())
            .build()
            .show();
    }
}
