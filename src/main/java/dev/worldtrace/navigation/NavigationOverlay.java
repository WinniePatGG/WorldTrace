package dev.worldtrace.navigation;

import dev.worldtrace.model.TraceEntry;

import java.util.Optional;

/**
 * Publishes the player's current position and facing so navigation guidance can be computed
 * anywhere on the client thread without re-querying Minecraft objects.
 *
 * <p>Updated once per tick by the client wiring and cleared on disconnect. Keeping this as a tiny
 * static holder is what lets {@link dev.worldtrace.journal.JournalScreen} draw distance and heading
 * information without importing Minecraft classes into the drawing code.
 */
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

    /** Distance and heading to {@code entry}, or empty when the player's position is unknown. */
    public static Optional<NavigationCore.Result> currentFor(TraceEntry entry) {
        if (entry == null || position == null) {
            return Optional.empty();
        }
        NavigationCore.Position target =
                new NavigationCore.Position(entry.dimensionId(), entry.x(), entry.y(), entry.z());
        return Optional.of(NavigationCore.evaluate(position, target, yaw));
    }

    /** Distance and heading to a watch-pin style position. */
    public static Optional<NavigationCore.Result> currentFor(String dimensionId, int x, int y, int z) {
        if (position == null) {
            return Optional.empty();
        }
        NavigationCore.Position target = new NavigationCore.Position(dimensionId, x, y, z);
        return Optional.of(NavigationCore.evaluate(position, target, yaw));
    }
}