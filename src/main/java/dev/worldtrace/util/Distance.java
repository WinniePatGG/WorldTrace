package dev.worldtrace.util;

import java.util.Locale;

public final class Distance {

    private Distance() {
    }

    public static double euclidean(int x1, int y1, int z1, int x2, int y2, int z2) {
        double dx = (double) x1 - x2;
        double dy = (double) y1 - y2;
        double dz = (double) z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double horizontal(int x1, int z1, int x2, int z2) {
        double dx = (double) x1 - x2;
        double dz = (double) z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static String formatDistance(double blocks) {
        if (Double.isNaN(blocks) || Double.isInfinite(blocks) || blocks < 0) {
            return "unknown";
        }
        if (blocks < 1000) {
            return Math.round(blocks) + " m";
        }
        return String.format(Locale.ROOT, "%.2f km", blocks / 1000.0);
    }

    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped > 180.0) {
            wrapped -= 360.0;
        }
        if (wrapped <= -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }
}
