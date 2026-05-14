package cc.nawt.samples.landmarks;

import cc.nawt.Coordinate;

import java.util.List;

/**
 * Hard-coded sample data — twelve US national-park landmarks, four marked as
 * initial favourites. Coordinates are approximate visitor-centre or central
 * feature locations. Descriptions are short paragraphs intended to fill the
 * detail view's body text.
 */
public final class LandmarkData {

    private LandmarkData() {}

    public static List<Landmark> all() {
        return List.of(
            new Landmark(1, "Yosemite Valley",
                "Yosemite National Park", "California",
                "A glacier-carved valley framed by sheer granite cliffs — Half Dome to the east, "
                    + "El Capitan to the west — laced with waterfalls in spring and meadows along the Merced River.",
                "landmark-01.png", true, Category.MOUNTAINS,
                Coordinate.of(37.7456, -119.5936)),

            new Landmark(2, "Grand Canyon",
                "Grand Canyon National Park", "Arizona",
                "A 277-mile-long, mile-deep gorge carved by the Colorado River over millions of years. "
                    + "The South Rim offers the canonical sweeping views; the river runs roughly 18 miles below.",
                "landmark-02.png", true, Category.RIVERS,
                Coordinate.of(36.1069, -112.1129)),

            new Landmark(3, "Crater Lake",
                "Crater Lake National Park", "Oregon",
                "A 1,949-foot-deep caldera lake — the deepest in the United States — formed when Mount Mazama collapsed "
                    + "about 7,700 years ago. Famously intense blue, fed entirely by rain and snow.",
                "landmark-03.png", false, Category.LAKES,
                Coordinate.of(42.9446, -122.1090)),

            new Landmark(4, "Glacier Bay",
                "Glacier Bay National Park", "Alaska",
                "A 3.3-million-acre coastal wilderness where tidewater glaciers calve into the Pacific. Best seen by boat; "
                    + "humpback whales and brown bears share the shoreline.",
                "landmark-04.png", false, Category.LAKES,
                Coordinate.of(58.6658, -136.9002)),

            new Landmark(5, "Yellowstone",
                "Yellowstone National Park", "Wyoming",
                "America's first national park, sitting atop a 1,500-square-mile super-volcano. Half the world's geysers "
                    + "are here, including Old Faithful; bison and elk wander the Lamar Valley.",
                "landmark-05.png", true, Category.RIVERS,
                Coordinate.of(44.4280, -110.5885)),

            new Landmark(6, "Zion",
                "Zion National Park", "Utah",
                "A red-rock canyon carved by the Virgin River. The Narrows hike walks the river itself between thousand-foot "
                    + "sandstone walls; Angels Landing scrambles the spine above.",
                "landmark-06.png", false, Category.MOUNTAINS,
                Coordinate.of(37.2982, -113.0263)),

            new Landmark(7, "Acadia",
                "Acadia National Park", "Maine",
                "Granite headlands along the North Atlantic coast. Cadillac Mountain catches the first sunrise on the U.S. "
                    + "mainland; carriage roads loop through spruce and birch forest.",
                "landmark-07.png", false, Category.MOUNTAINS,
                Coordinate.of(44.3386, -68.2733)),

            new Landmark(8, "Olympic",
                "Olympic National Park", "Washington",
                "Three ecosystems in one park: temperate rainforest in the Hoh Valley, glaciated peaks above 7,000 feet on "
                    + "Mount Olympus, and seventy miles of wild Pacific coastline.",
                "landmark-08.png", false, Category.MOUNTAINS,
                Coordinate.of(47.8021, -123.6044)),

            new Landmark(9, "Rocky Mountain",
                "Rocky Mountain National Park", "Colorado",
                "Trail Ridge Road climbs above 12,000 feet through alpine tundra; the Mummy Range and Longs Peak frame the "
                    + "horizon. Elk bugle through Moraine Park each September.",
                "landmark-09.png", true, Category.MOUNTAINS,
                Coordinate.of(40.3428, -105.6836)),

            new Landmark(10, "Great Smoky Mountains",
                "Great Smoky Mountains National Park", "Tennessee",
                "Misty ridges and old-growth Appalachian forest. The most-visited national park in the U.S.; black bears, "
                    + "synchronous fireflies, and twelve hundred miles of trail.",
                "landmark-10.png", false, Category.MOUNTAINS,
                Coordinate.of(35.6131, -83.5532)),

            new Landmark(11, "Big Sur",
                "Pfeiffer Big Sur State Park", "California",
                "The Santa Lucia Range plunges directly into the Pacific along Highway 1. Redwoods inland, sea cliffs "
                    + "outward, sea otters in the kelp below.",
                "landmark-11.png", false, Category.MOUNTAINS,
                Coordinate.of(36.2704, -121.8081)),

            new Landmark(12, "Lake Tahoe",
                "Lake Tahoe Basin", "California",
                "A 1,645-foot-deep alpine lake straddling the Nevada line, surrounded by Sierra Nevada peaks. Famed for "
                    + "transparency — visibility well over sixty feet in clear water.",
                "landmark-12.png", false, Category.LAKES,
                Coordinate.of(39.0968, -120.0324))
        );
    }
}
