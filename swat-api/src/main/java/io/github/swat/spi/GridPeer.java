package io.github.swat.spi;

public non-sealed interface GridPeer extends Peer {
    /**
     * Place a child at the given cell with an optional span. Coordinates are
     * 0-indexed; {@code (col, row) = (0, 0)} is the top-left cell.
     *
     * @param child        peer to attach
     * @param column       leftmost column the child occupies
     * @param row          topmost row the child occupies
     * @param columnSpan   number of columns occupied (≥ 1)
     * @param rowSpan      number of rows occupied (≥ 1)
     * @param hints        per-child layout hints. {@code alignSelf} controls
     *                     alignment within the cell (applied to both axes);
     *                     {@code expand} lowers content-hugging priority so
     *                     this child absorbs slack within its column/row.
     */
    void attach(Peer child, int column, int row, int columnSpan, int rowSpan, ChildLayoutConfig hints);
}
