package dev.worldtrace.navigation;

import dev.worldtrace.model.TraceEntry;

import java.util.Optional;

public final class NavigationOverlay {

    private static NavigationCore.Position position;
    private static double yaw;

    private NavigationOverlay() {
    }

    public static void update(String dimensionId, int x, int y, int z, double playerYaw) {
        position = new NavigationCore.Position(dimensionId, x, y, z);
        yaw = playerYaw;
    }

    public static void clear() {
        position = null;
    }

    public static boolean hasPlayer() {
        return position != null;
    }

    public static NavigationCore.Position playerPosition() {
        return position;
    }

    public static Optional<NavigationCore.Result> currentFor(TraceEntry entry) {
        if (entry == null || position == null) {
            return Optional.empty();
        }
        NavigationCore.Position target =
                new NavigationCore.Position(entry.dimensionId(), entry.x(), entry.y(), entry.z());
        return Optional.of(NavigationCore.evaluate(position, target, yaw));
    }

    public static Optional<NavigationCore.Result> currentFor(String dimensionId, int x, int y, int z) {
        if (position == null) {
            return Optional.empty();
        }
        NavigationCore.Position target = new NavigationCore.Position(dimensionId, x, y, z);
        return Optional.of(NavigationCore.evaluate(position, target, yaw));
    }
}
