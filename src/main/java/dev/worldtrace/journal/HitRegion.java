package dev.worldtrace.journal;

/**
 * A clickable rectangle produced by the journal layout.
 *
 * <p>Regions are derived from the same {@link JournalLayout} that draws the widgets, so hit testing
 * and rendering can never disagree. Carrying a payload (a list index, a category ordinal) avoids the
 * need for a closure per hit target.
 */
record HitRegion(int action, int payload, int x1, int y1, int x2, int y2) {

    boolean contains(int mouseX, int mouseY) {
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }

    int width() {
        return x2 - x1;
    }

    int height() {
        return y2 - y1;
    }
}