package dev.worldtrace.util;

import java.util.Locale;

/**
 * Pure geometry helpers shared by navigation, the HUD and the journal.
 *
 * <p>This class intentionally has no Minecraft dependency so that every calculation can be
 * exercised by unit tests without booting the game.
 */
public final class Distance {

    private Distance() {
    }

    public static double euclidean(int x1, int y1, int z1, int x2, int y2, int z2) {
        double dx = (double) x1 - x2;
        double dy = (double) y1 - y2;
        double dz = (double) z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Horizontal (XZ plane) distance, which is what a compass-style indicator cares about. */
    public static double horizontal(int x1, int z1, int x2, int z2) {
        double dx = (double) x1 - x2;
        double dz = (double) z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Formats a block distance the way Minecraft players expect: metres below a kilometre,
     * kilometres above it.
     */
    public static String formatDistance(double blocks) {
        if (Double.isNaN(blocks) || Double.isInfinite(blocks) || blocks < 0) {
            return "unknown";
        }
        if (blocks < 1000) {
            return Math.round(blocks) + " m";
        }
        return String.format(Locale.ROOT, "%.2f km", blocks / 1000.0);
    }

    /** Wraps an angle into the {@code (-180, 180]} range used by Minecraft yaw values. */
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