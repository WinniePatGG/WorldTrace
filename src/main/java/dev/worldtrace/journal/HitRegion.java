package dev.worldtrace.journal;

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
