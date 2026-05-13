package io.github.swat;

import io.github.swat.spi.ChildLayoutConfig;
import io.github.swat.spi.GridConfig;
import io.github.swat.spi.GridPeer;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-dimensional grid container. Children are placed at explicit
 * {@code (column, row)} coordinates with optional spans. Sibling indices may
 * be sparse — empty cells are allowed.
 *
 * <p>The grid's overall dimensions are inferred from the maximum
 * {@code column + columnSpan} and {@code row + rowSpan} across all
 * placements. Spacing between columns and rows is uniform; for a padded
 * outer margin, wrap the Grid in a {@code Column} or {@code Frame} with
 * padding (see {@code LAYOUT.md}).
 *
 * <p>Per-child alignment within a cell is governed by
 * {@link ChildLayoutConfig#alignSelf()}; the default is {@code STRETCH}
 * (fill the cell). {@link ChildLayoutConfig#expand()} lowers the child's
 * content-hugging priority so its column/row absorbs slack when the grid
 * is larger than its intrinsic content.
 */
public final class Grid implements Container {

    private final GridPeer peer;
    private final List<Widget> children;

    private Grid(GridPeer peer, List<Widget> children) {
        this.peer = peer;
        this.children = children;
    }

    public static Builder builder() { return new Builder(); }

    public List<Widget> children() { return children; }

    @Override public Grid tooltip(String text) { Container.super.tooltip(text); return this; }
    @Override public Grid dragText(java.util.function.Supplier<String> textProvider) { Container.super.dragText(textProvider); return this; }
    @Override public Grid acceptText(java.util.function.Consumer<String> textHandler) { Container.super.acceptText(textHandler); return this; }

    @Override public GridPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            for (Widget child : children) child.close();
            peer.close();
        });
    }

    public static final class Builder {
        private int columnSpacing = 8;
        private int rowSpacing = 8;
        private boolean columnsHomogeneous = false;
        private boolean rowsHomogeneous = false;
        private boolean squareCells = false;
        private final List<Placement> placements = new ArrayList<>();

        private Builder() {}

        public Builder columnSpacing(int px) { this.columnSpacing = px; return this; }
        public Builder rowSpacing(int px) { this.rowSpacing = px; return this; }
        public Builder spacing(int px) { this.columnSpacing = px; this.rowSpacing = px; return this; }

        /** Force all columns to equal width and all rows to equal height. */
        public Builder homogeneous(boolean h) {
            this.columnsHomogeneous = h; this.rowsHomogeneous = h; return this;
        }
        public Builder homogeneousColumns(boolean h) { this.columnsHomogeneous = h; return this; }
        public Builder homogeneousRows(boolean h) { this.rowsHomogeneous = h; return this; }

        /**
         * Constrain each cell to {@code width == height}. With
         * {@link #homogeneous(boolean) homogeneous(true)} this yields a
         * uniform grid of square cells, sized to the larger of intrinsic
         * width or intrinsic height across all cells.
         */
        public Builder squareCells(boolean s) { this.squareCells = s; return this; }
        public Builder square() { return squareCells(true); }

        public Builder put(Widget child, int column, int row) {
            return put(child, column, row, 1, 1, ChildLayoutConfig.DEFAULT);
        }
        public Builder put(Widget child, int column, int row, ChildLayoutConfig hints) {
            return put(child, column, row, 1, 1, hints);
        }
        public Builder put(Widget child, int column, int row, int columnSpan, int rowSpan) {
            return put(child, column, row, columnSpan, rowSpan, ChildLayoutConfig.DEFAULT);
        }
        public Builder put(Widget child, int column, int row, int columnSpan, int rowSpan, ChildLayoutConfig hints) {
            if (child == null) throw new IllegalArgumentException("child must not be null");
            if (column < 0 || row < 0) throw new IllegalArgumentException("column/row must be non-negative");
            if (columnSpan < 1 || rowSpan < 1) throw new IllegalArgumentException("spans must be >= 1");
            placements.add(new Placement(child, column, row, columnSpan, rowSpan,
                hints == null ? ChildLayoutConfig.DEFAULT : hints));
            return this;
        }

        public Grid build() {
            List<Placement> snapshot = List.copyOf(placements);
            int cols = 0, rows = 0;
            for (Placement p : snapshot) {
                cols = Math.max(cols, p.column + p.columnSpan);
                rows = Math.max(rows, p.row + p.rowSpan);
            }
            final int columnCount = cols;
            final int rowCount = rows;
            return Ui.onUi(() -> {
                GridPeer p = Toolkit.requireLaunched().peerFactory()
                    .createGrid(new GridConfig(columnCount, rowCount,
                        columnSpacing, rowSpacing,
                        columnsHomogeneous, rowsHomogeneous,
                        squareCells));
                List<Widget> kids = new ArrayList<>(snapshot.size());
                for (Placement pl : snapshot) {
                    p.attach(pl.child.peer(), pl.column, pl.row, pl.columnSpan, pl.rowSpan, pl.hints);
                    kids.add(pl.child);
                }
                return new Grid(p, List.copyOf(kids));
            });
        }

        private record Placement(Widget child, int column, int row,
                                 int columnSpan, int rowSpan, ChildLayoutConfig hints) {}
    }
}
