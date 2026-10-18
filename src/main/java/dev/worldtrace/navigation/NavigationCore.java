package dev.worldtrace.navigation;

import dev.worldtrace.util.Distance;

public final class NavigationCore {

    public enum DistanceKind {

        SAME_DIMENSION,

        OTHER_DIMENSION,

        UNKNOWN
    }

    public record Position(String dimensionId, int x, int y, int z) {
    }

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

    public static String compassLabel(double yaw) {
        String[] labels = {"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};
        int index = (int) Math.round(Distance.wrapDegrees(yaw) / 45.0) & 0x7;
        return labels[index];
    }

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
