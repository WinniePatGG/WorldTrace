package dev.worldtrace.navigation;

import dev.worldtrace.util.Distance;

/**
 * Pure navigation maths shared by the HUD, the journal and the tests.
 *
 * <p>Directional guidance is only meaningful inside one dimension, so every evaluation explicitly
 * reports whether the player and the destination share a dimension. Cross-dimension results are
 * never faked into a distance, because a Euclidean distance between the Overworld and the Nether is
 * meaningless to a player and, more importantly, would be dishonest.
 */
public final class NavigationCore {

    public enum DistanceKind {
        /** Distance and heading are both meaningful. */
        SAME_DIMENSION,
        /** The destination is in another dimension; no straight-line guidance is possible. */
        OTHER_DIMENSION,
        /** The player's own position is unknown (not in a world). */
        UNKNOWN
    }

    /** A position in a named dimension. */
    public record Position(String dimensionId, int x, int y, int z) {
    }

    /**
     * @param kind            relationship between player and destination
     * @param distanceBlocks  3D distance when {@code kind == SAME_DIMENSION}, otherwise NaN
     * @param horizontalDelta signed degrees relative to the player's facing (0 = straight ahead,
     *                        positive = clockwise/right)
     * @param targetYaw       absolute yaw of the destination in Minecraft's convention, or NaN when
     *                        no heading is meaningful
     */
    public record Result(DistanceKind kind, double distanceBlocks, double horizontalDelta, double targetYaw) {

        public boolean isDirectional() {
            return kind == DistanceKind.SAME_DIMENSION;
        }

        public static Result unknown() {
            return new Result(DistanceKind.UNKNOWN, Double.NaN, 0.0, Double.NaN);
        }
    }

    private NavigationCore() {
    }

    public static Result evaluate(Position player, Position destination) {
        if (player == null || destination == null
                || player.dimensionId() == null || destination.dimensionId() == null) {
            return Result.unknown();
        }
        if (!player.dimensionId().equals(destination.dimensionId())) {
            return new Result(DistanceKind.OTHER_DIMENSION, Double.NaN, 0.0, Double.NaN);
        }
        double distance = Distance.euclidean(player.x(), player.y(), player.z(),
                destination.x(), destination.y(), destination.z());
        return new Result(DistanceKind.SAME_DIMENSION, distance, 0.0, Double.NaN);
    }

    /**
     * Computes the heading the player would have to turn, given the current yaw in Minecraft's
     * convention (0 = south / +Z, increasing clockwise).
     *
     * @param playerYaw  the player's current yaw in degrees
     */
    public static Result evaluate(Position player, Position destination, double playerYaw) {
        Result base = evaluate(player, destination);
        if (!base.isDirectional()) {
            return base;
        }
        double dx = (double) destination.x() - player.x();
        double dz = (double) destination.z() - player.z();
        if (dx == 0 && dz == 0) {
            return new Result(DistanceKind.SAME_DIMENSION, 0.0, 0.0, Distance.wrapDegrees(playerYaw));
        }
        double targetYaw = Distance.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        double delta = Distance.wrapDegrees(targetYaw - playerYaw);
        return new Result(DistanceKind.SAME_DIMENSION, base.distanceBlocks(), delta, targetYaw);
    }

    /**
     * Compass name for an absolute yaw in Minecraft's convention.
     * 0 = south, 90 = west, 180 = north, 270 = east.
     */
    public static String compassLabel(double yaw) {
        String[] labels = {"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};
        int index = (int) Math.round(Distance.wrapDegrees(yaw) / 45.0) & 0x7;
        return labels[index];
    }

    /**
     * Relative description of a heading offset, e.g. {@code "ahead"} or {@code "behind-left"}.
     * This is what the HUD shows, because "North" is meaningless without knowing which way the
     * player is facing.
     */
    public static String relativeLabel(double horizontalDelta) {
        double delta = Distance.wrapDegrees(horizontalDelta);
        int octant = (int) Math.round(delta / 45.0) & 0x7;
        return switch (octant) {
            case 0 -> "ahead";
            case 1 -> "ahead-right";
            case 2 -> "right";
            case 3 -> "behind-right";
            case 4 -> "behind";
            case 5 -> "behind-left";
            case 6 -> "left";
            default -> "ahead-left";
        };
    }
}