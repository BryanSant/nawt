package io.github.swat.spi;

/**
 * Configuration for a Grid container.
 *
 * @param columnCount         total number of columns (inferred by the Grid
 *                            builder from max {@code column + columnSpan})
 * @param rowCount            total number of rows (max {@code row + rowSpan})
 * @param columnSpacing       pixels between adjacent columns
 * @param rowSpacing          pixels between adjacent rows
 * @param columnsHomogeneous  if {@code true}, all columns are forced to equal
 *                            width regardless of intrinsic content size
 * @param rowsHomogeneous     if {@code true}, all rows are forced to equal
 *                            height regardless of intrinsic content size
 * @param squareCells         if {@code true}, each non-spanning cell is
 *                            constrained to have width equal to height.
 *                            Combined with {@code columnsHomogeneous} and
 *                            {@code rowsHomogeneous}, this yields a uniform
 *                            grid of square cells (calculator keypad,
 *                            color-swatch picker, etc.).
 */
public record GridConfig(int columnCount, int rowCount,
                         int columnSpacing, int rowSpacing,
                         boolean columnsHomogeneous, boolean rowsHomogeneous,
                         boolean squareCells) {
    public GridConfig {
        if (columnCount < 0) columnCount = 0;
        if (rowCount < 0) rowCount = 0;
        if (columnSpacing < 0) columnSpacing = 0;
        if (rowSpacing < 0) rowSpacing = 0;
    }

    public GridConfig(int columnCount, int rowCount, int columnSpacing, int rowSpacing) {
        this(columnCount, rowCount, columnSpacing, rowSpacing, false, false, false);
    }

    public GridConfig(int columnCount, int rowCount, int columnSpacing, int rowSpacing,
                      boolean columnsHomogeneous, boolean rowsHomogeneous) {
        this(columnCount, rowCount, columnSpacing, rowSpacing,
            columnsHomogeneous, rowsHomogeneous, false);
    }
}
