package dev.worldtrace.journal;

/**
 * Responsive metrics for the journal window.
 *
 * <p>The journal has to work from a tiny 320x240 window at GUI scale 4 up to a 4K screen at scale 1.
 * Rather than scaling widgets (which would fight the vanilla renderer), the layout keeps a single
 * row height and padding, and adapts in three steps:
 *
 * <ol>
 *   <li><b>Roomy</b> — both panes side by side, generous padding.</li>
 *   <li><b>Compact</b> — tighter padding, shorter rows, the list column narrows.</li>
 *   <li><b>Narrow</b> — only one pane at a time; the detail replaces the list and a Back control
 *       returns. Nothing becomes unreachable.</li>
 * </ol>
 */
public final class JournalLayout {

    public enum Mode {
        ROOMY,
        COMPACT,
        NARROW
    }

    private final int width;
    private final int height;
    private final Mode mode;
    private final int padding;
    private final int rowHeight;
    private final int listWidth;
    private int filterLines;
    private int visibleRows;

    public JournalLayout(int width, int height) {
        this.width = Math.max(160, width);
        this.height = Math.max(120, height);
        if (this.width < 300 || this.height < 180) {
            this.mode = Mode.NARROW;
        } else if (this.width < 420 || this.height < 240) {
            this.mode = Mode.COMPACT;
        } else {
            this.mode = Mode.ROOMY;
        }
        this.padding = switch (mode) {
            case ROOMY -> 10;
            case COMPACT -> 6;
            case NARROW -> 4;
        };
        // Tall enough for a name plus the dimension and coordinates line at every density.
        this.rowHeight = mode == Mode.ROOMY ? 24 : 22;
        this.listWidth = mode == Mode.NARROW
                ? this.width - padding * 2
                : Math.max(120, Math.min((int) (this.width * 0.42), this.width - padding * 2 - 180));
        this.filterLines = 1;
        this.visibleRows = Math.max(1, (this.height - verticalChrome()) / rowHeight);
    }

    /**
     * The category chip row wraps when the window is too narrow. The caller measures the chips and
     * reports how many lines they need, so the content area below is always correct.
     */
    public void setFilterLines(int lines) {
        int clamped = Math.max(1, lines);
        if (clamped == this.filterLines) {
            return;
        }
        this.filterLines = clamped;
        this.visibleRows = Math.max(1, (this.height - verticalChrome()) / rowHeight);
    }

    public int filterLines() {
        return filterLines;
    }

    /** Total vertical space consumed by the header, filter rows and footer. */
    private int verticalChrome() {
        return padding * 2 + headerHeight() + searchRowHeight() + filterRowHeight() * filterLines
                + footerHeight() + 8;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Mode mode() {
        return mode;
    }

    public boolean narrow() {
        return mode == Mode.NARROW;
    }

    public int padding() {
        return padding;
    }

    public int rowHeight() {
        return rowHeight;
    }

    public int listWidth() {
        return listWidth;
    }

    public int visibleRows() {
        return visibleRows;
    }

    public int headerHeight() {
        return switch (mode) {
            case ROOMY -> 30;
            case COMPACT -> 26;
            case NARROW -> 22;
        };
    }

    public int searchRowHeight() {
        return mode == Mode.NARROW ? 18 : 20;
    }

    public int filterRowHeight() {
        return mode == Mode.NARROW ? 16 : 18;
    }

    public int footerHeight() {
        return mode == Mode.NARROW ? 18 : 20;
    }

    public int headerTop() {
        return padding;
    }

    public int searchTop() {
        return headerTop() + headerHeight();
    }

    public int filterTop() {
        return searchTop() + searchRowHeight();
    }

    public int contentTop() {
        return filterTop() + filterRowHeight() * filterLines + 4;
    }

    public int footerTop() {
        return height - padding - footerHeight();
    }

    public int contentBottom() {
        return footerTop() - 4;
    }

    public int listLeft() {
        return padding;
    }

    public int listRight() {
        // In narrow mode the list owns the full content width, because the detail pane replaces it
        // rather than sharing the row.
        return narrow() ? width - padding : listLeft() + listWidth;
    }

    public int detailLeft() {
        return narrow() ? padding : listRight() + padding;
    }

    public int detailRight() {
        return width - padding;
    }

    /** Inner width of a list row, excluding its left colour bar and padding. */
    public int rowContentWidth() {
        return listWidth - padding * 2 - 10;
    }
}