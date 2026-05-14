package cc.nawt.samples.landmarks;

import cc.nawt.Coordinate;

public record Landmark(
    int id,
    String name,
    String park,
    String state,
    String description,
    String imageResource,
    boolean isFavorite,
    Category category,
    Coordinate coordinate) {}
