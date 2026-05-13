# SWAT layout contract

## Strategy: wrap, don't reimplement

SWAT delegates layout to the host's native container on every backend:

| Container | macOS | Linux | Windows |
| --- | --- | --- | --- |
| Row, Column | `NSStackView` + Auto Layout | `GtkBox` | `StackPanel` (planned) |
| Grid | `NSGridView` | `GtkGrid` | `Grid` (planned) |
| ScrollContainer | `NSScrollView` | `GtkScrolledWindow` | `ScrollViewer` (planned) |
| Splitter | `NSSplitView` | `GtkPaned` | `Grid` w/ splitter (planned) |
| Frame, Tabs, Expander | native equivalents | native equivalents | (planned) |

There is **no Java-side layout pass**. There is no `Layout` interface, no
`computeSize()` method, no `relayout()` call. The Java side passes a
configuration record and a tree of children to the native peer; the native
toolkit's own measure/arrange (or measure/allocate) pass owns position and
size computation. Window resizes, font-size changes, RTL flips, dynamic-type
reflow, and accessibility scaling work because the native toolkit handles
them automatically — not because we re-implement them in Java.

The single explicit exception is `Canvas`, where the developer paints inside
a fixed-rectangle drawing surface. Anything outside that is governed by the
contract below.

The rationale for this strategy — including why SWT chose the opposite path
in 2001 and why platform convergence makes the wrapping strategy correct in
2026 — lives in the per-PR plan at the time of adoption and in
`swat-peer-comparision.md`.

## The contract

### Row and Column

A Row arranges its children left-to-right; a Column arranges them top-to-bottom.
"Main axis" is the arrangement direction; "cross axis" is perpendicular.

**Container-level config** (`ContainerConfig`):
- `spacing` — pixels between adjacent children. Each backend treats this as
  a precise pixel value where its native primitive allows; the exact pixel
  result on HiDPI displays follows whatever scaling the host applies to the
  rest of the UI.
- `padding` — pixels of inset on all four sides of the container's content
  rectangle.
- `crossAxis` — default `Alignment` for children along the cross axis when
  the child does not set `ChildLayoutConfig.alignSelf`. Defaults to
  `STRETCH`.

**Per-child config** (`ChildLayoutConfig`):
- `expand` — if `true`, this child absorbs main-axis slack. Multiple
  expanding children share slack equally.
- `alignSelf` — cross-axis alignment override. `null` means inherit
  `ContainerConfig.crossAxis`.

### Alignment values

| Value | Meaning on cross axis | Meaning when used as `crossAxis` |
| --- | --- | --- |
| `START` | Pin to leading/top edge; child uses intrinsic size | All children pin leading/top |
| `CENTER` | Center on cross axis; intrinsic size | All children centered |
| `END` | Pin to trailing/bottom edge; intrinsic size | All children pin trailing/bottom |
| `STRETCH` | Fill cross axis (minus padding) | All children fill cross axis |
| `BASELINE` | (Horizontal Row only) align first text baseline | Approximated as `CENTER` in a Column |

### Main-axis slack

When the container has more main-axis space than the sum of children's
intrinsic main-axis sizes:

1. Children with `expand = true` divide the slack equally.
2. If no child has `expand = true`, slack distribution is backend-defined:
   - macOS: `NSStackViewDistributionFill` routes slack to the lowest hugging
     priority — in practice, the first widget without a high
     content-hugging value (Labels, TextFields, Frames absorb; Buttons,
     Switches, Spinners do not).
   - GTK: `GtkBox` without `hexpand`/`vexpand` set on any child leaves
     trailing slack unused; children stay at their intrinsic sizes packed at
     the leading edge.

The portable rule: **if you want predictable distribution of slack, mark at
least one child with `expand`**. The default behavior is "let the platform
decide," which is rarely what you want for resizable windows.

### Grid

A Grid arranges children at explicit `(column, row)` coordinates with
optional spans. Cell coordinates are 0-indexed; `(0, 0)` is the top-left.
Sparse layouts are allowed — empty cells are permitted.

**Container-level config** (`GridConfig`):
- `columnSpacing` — pixels between adjacent columns.
- `rowSpacing` — pixels between adjacent rows.
- `columnCount`, `rowCount` — inferred by the Grid builder from the maximum
  `column + columnSpan` and `row + rowSpan` across all placements. They are
  carried in the config because some backends (NSGridView) need explicit
  dimensions at construction time.
- `columnsHomogeneous` / `rowsHomogeneous` (also `.homogeneous(true)` as a
  shortcut for both) — force all columns to equal width / all rows to equal
  height, regardless of intrinsic content size. Default `false` (column and
  row sizes track their widest/tallest cell).

**Per-placement config** (passed to `Grid.builder().put(...)`):
- `column`, `row` — cell coordinates.
- `columnSpan`, `rowSpan` — number of columns/rows the child occupies
  (default 1, 1).
- `ChildLayoutConfig.alignSelf` — alignment within the cell, applied to
  both axes uniformly. Default cell behavior is `STRETCH` (fill the cell);
  set `alignSelf` to override.
- `ChildLayoutConfig.expand` — lowers content-hugging priority so the
  child's column and row absorb slack when the grid is larger than its
  intrinsic content.

A Grid does **not** have a `padding` knob. For an outer margin, wrap the
Grid in a `Column`, `Row`, or `Frame` with padding.

## Per-platform mapping

### macOS (`NSStackView` + Auto Layout)

- `ContainerConfig.crossAxis` → `NSStackView.setAlignment:` with the
  matching `NSLayoutAttribute` (Leading / CenterX / Trailing / FirstBaseline).
- `ChildLayoutConfig.alignSelf = STRETCH` → adds a width-or-height-equal
  constraint to the stack at priority 350 (sub-required so it doesn't
  propagate to `NSWindow.fittingSize`).
- `ChildLayoutConfig.alignSelf` non-STRETCH → no width/height constraint;
  child uses its intrinsic cross-axis size, positioned by the stack's
  setAlignment.
- `ChildLayoutConfig.expand = true` →
  `setContentHuggingPriority:forOrientation:` lowered to 100 along the main
  axis (default is 250), so `NSStackViewDistributionFill` routes slack here
  first.

**Known deviation:** macOS `NSStackView.setAlignment:` is a single
stack-wide value. Mixing positional alignment per child within one Row or
Column (e.g., one child `START`, another `END`) follows the container's
`crossAxis` for positioning even when `alignSelf` differs. The STRETCH ↔
intrinsic-cross-axis-size toggle via `alignSelf` is fully supported. For
mixed positional alignment, wrap each child in its own Row/Column.

#### macOS Grid (`NSGridView`)

- `GridConfig.columnCount` / `rowCount` →
  `+[NSGridView gridViewWithNumberOfColumns:rows:]`. The grid is allocated
  at the size computed by the Grid builder.
- `GridConfig.columnSpacing` / `rowSpacing` → `setColumnSpacing:` /
  `setRowSpacing:`.
- Grid-level default placement is `NSGridCellPlacementFill` on both axes.
- Per-placement `ChildLayoutConfig.alignSelf` → `NSGridCell.setXPlacement:`
  and `setYPlacement:` with the matching `NSGridCellPlacement` value
  (Leading / Trailing / Center / Fill). `BASELINE` maps to
  `setRowAlignment: NSGridRowAlignmentFirstBaseline`.
- Spans (`columnSpan > 1` or `rowSpan > 1`) →
  `mergeCellsInHorizontalRange:verticalRange:` with two `NSRange` structs.
- `ChildLayoutConfig.expand` lowers content-hugging priority to 100 on both
  axes so the child's row and column absorb available slack.
- `columnsHomogeneous` / `rowsHomogeneous` — NSGridView has no native flag,
  so the backend records the first non-spanning cell's content view and adds
  required-priority `widthAnchor` / `heightAnchor` equality constraints
  between every subsequent non-spanning cell and that reference. With the
  grid's default placement of Fill, content sizes track their cells, so
  equal content sizes ⇒ equal column/row sizes. Spanning cells are
  excluded (their size spans multiple columns/rows by design).
  **Known deviation:** when `homogeneous` is enabled, cells with
  `alignSelf` set to a non-STRETCH value still get the equality constraint
  applied to their content view — the column/row stays uniform but the
  content within that cell may visually fill the cell rather than honor
  the explicit alignment. Mix `homogeneous=true` with `alignSelf` only
  when you accept this.

### Linux (`GtkBox`)

- `ContainerConfig.crossAxis` → applied as the default per-child
  `gtk_widget_set_halign` (vertical box) or `gtk_widget_set_valign`
  (horizontal box). GTK does not have a single "box alignment" property;
  cross-axis alignment is genuinely per-child.
- `ChildLayoutConfig.alignSelf` → overrides the container default with the
  matching `GtkAlign` value (FILL / START / END / CENTER / BASELINE).
- `ChildLayoutConfig.expand = true` → `gtk_widget_set_hexpand` (horizontal
  box) or `gtk_widget_set_vexpand` (vertical box). GTK splits slack equally
  among siblings with the expand flag set.

GTK has no positional-alignment limitation analogous to macOS — `alignSelf`
fully overrides per child.

#### Linux Grid (`GtkGrid`)

- `gtk_grid_new()` creates an empty grid; columns and rows grow as needed,
  so `GridConfig.columnCount` / `rowCount` are advisory on this backend.
- `GridConfig.columnSpacing` / `rowSpacing` → `gtk_grid_set_column_spacing` /
  `gtk_grid_set_row_spacing`.
- Each placement calls `gtk_grid_attach(grid, child, col, row, width, height)`
  with `width = columnSpan` and `height = rowSpan` — `GtkGrid` natively
  supports cell spans without a separate merge step.
- Per-placement `ChildLayoutConfig.alignSelf` → `gtk_widget_set_halign`
  and `gtk_widget_set_valign` on the child with the matching `GtkAlign`
  value. Default is `GTK_ALIGN_FILL` on both axes.
- `ChildLayoutConfig.expand` → `gtk_widget_set_hexpand(child, true)` and
  `gtk_widget_set_vexpand(child, true)`.
- `columnsHomogeneous` / `rowsHomogeneous` → `gtk_grid_set_column_homogeneous`
  and `gtk_grid_set_row_homogeneous`. GTK applies this at the column/row
  level (not per-content-view), so cells with `alignSelf` set to non-STRETCH
  values keep their explicit alignment inside the uniformly-sized cell — no
  deviation, unlike the macOS implementation.

### Windows (`StackPanel`, planned)

When the Windows backend lands, the mapping will be:
- `ContainerConfig.crossAxis` → `StackPanel.HorizontalAlignment` / 
  `VerticalAlignment` on the panel, plus per-child alignment for overrides.
- `ChildLayoutConfig.alignSelf` → per-child `HorizontalAlignment` /
  `VerticalAlignment`.
- `ChildLayoutConfig.expand = true` → `StackPanel` has no native expand
  flag, so this maps to placing the panel in a `Grid` row/column with star
  sizing and pinning the expanding child to fill the cell.

## What is intentionally not in the contract

- **Per-child weight or proportional split.** GTK has no native per-child
  weight, and emulating it requires manual size requests that defeat
  intrinsic sizing. Equal split for multiple expanding children is the only
  guarantee. If you need a 2:1 split, nest containers or specify explicit
  sizes on the child.
- **Java-side `setBounds` / absolute positioning.** The only place absolute
  positioning is correct is inside a `Canvas`, where the developer is
  responsible for painting at chosen coordinates within a fixed rectangle.
- **Cross-platform pixel determinism.** A `spacing(12)` Row will produce
  visually similar output everywhere, but the platforms differ in HiDPI
  rounding, text baselines, default control insets, and accent/focus rings.
  SWAT optimizes for native correctness, not pixel-identical output. If you
  need identical pixels everywhere, the README's comparison table already
  names the right tool — and it is not SWAT.

## Verification

Every backend that adds a new layout knob must keep the smoke tests passing:

- `:swat-samples:tier1Smoke` constructs a Row with `.expand(...)`, a Column
  with mixed `alignSelf` per child, and a Grid with a spanning cell and a
  cell using `alignSelf=CENTER`. Construction-only — confirms the SPI path
  doesn't throw.
- `:swat-samples:tier1` includes a visible "Layout (expand + alignSelf)"
  frame. The expanding TextField should absorb the slack between a fixed
  leading Label and a fixed trailing Button when the window is resized; the
  alignment Column should show three Buttons at START, CENTER, END
  positions across the container's cross axis.
- `:swat-samples:tier1` also includes a "Grid (form layout + span)" frame.
  Label cells stay at intrinsic width while TextField cells with
  `EXPAND` absorb the remaining column width; the final "Save profile"
  button spans both columns and is right-aligned via `alignSelf=END`.

When a backend cannot honor a contract rule exactly, the deviation is
documented in the "Known deviation" notes above. New deviations must be
added here in the same PR that introduces them.
