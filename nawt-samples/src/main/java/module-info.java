module cc.nawt.samples {
    requires cc.nawt.api;

    exports cc.nawt.samples;

    // Landmark image resources live in this package and are loaded by
    // Image.fromResource (in cc.nawt.api). JPMS encapsulates a package's
    // non-class resources unless the package is open to the caller.
    opens cc.nawt.samples.landmarks;
}
