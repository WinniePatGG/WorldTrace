package dev.worldtrace.navigation;

import dev.worldtrace.discovery.DiscoveryManager;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.util.Distance;
import dev.worldtrace.util.NameFormatting;

import java.util.Optional;

/**
 * Keeps track of the player's active destination and answers navigation questions about it.
 *
 * <p>WorldTrace never moves the player. "Setting a destination" only affects the HUD readout and the
 * journal's distance field; getting there remains entirely the player's decision, which is why
 * there is no teleport command anywhere in this mod.
 */
public final class NavigationManager {

    private final DiscoveryManager manager;

    public NavigationManager(DiscoveryManager manager) {
        this.manager = manager;
    }

    public Optional<TraceEntry> destination() {
        return manager.destination();
    }

    /** Selects {@code entry} as the active destination. */
    public boolean setDestination(String entryId) {
        return manager.setDestination(entryId);
    }

    public void clearDestination() {
        manager.clearDestination();
    }

    public Optional<String> destinationName() {
        return destination().map(TraceEntry::name);
    }

    /**
     * Current navigation state for the HUD.
     *
     * <p>Returns empty when no destination is set or the player's position is unknown. A
     * cross-dimension destination returns a {@link NavigationCore.DistanceKind#OTHER_DIMENSION}
     * result rather than a fabricated distance.
     */
    public Optional<NavigationCore.Result> currentResult() {
        Optional<TraceEntry> destination = destination();
        if (destination.isEmpty() || !NavigationOverlay.hasPlayer()) {
            return Optional.empty();
        }
        return NavigationOverlay.currentFor(destination.get());
    }

    /** One-line HUD text for the destination readout. */
    public Optional<String> hudText() {
        Optional<TraceEntry> destination = destination();
        if (destination.isEmpty()) {
            return Optional.empty();
        }
        Optional<NavigationCore.Result> result = currentResult();
        if (result.isEmpty()) {
            return Optional.of(destination.get().name());
        }
        NavigationCore.Result navigation = result.get();
        return Optional.of(switch (navigation.kind()) {
            case SAME_DIMENSION -> destination.get().name() + " · "
                    + Distance.formatDistance(navigation.distanceBlocks()) + " "
                    + NavigationCore.relativeLabel(navigation.horizontalDelta());
            case OTHER_DIMENSION -> destination.get().name() + " · other dimension ("
                    + NameFormatting.prettifyIdentifier(destination.get().dimensionId()) + ")";
            case UNKNOWN -> destination.get().name();
        });
    }
}