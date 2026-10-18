package dev.worldtrace.navigation;

import dev.worldtrace.discovery.DiscoveryManager;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.util.Distance;
import dev.worldtrace.util.NameFormatting;

import java.util.Optional;

public final class NavigationManager {

    private final DiscoveryManager manager;

    public NavigationManager(DiscoveryManager manager) {
        this.manager = manager;
    }

    public Optional<TraceEntry> destination() {
        return manager.destination();
    }

    public boolean setDestination(String entryId) {
        return manager.setDestination(entryId);
    }

    public void clearDestination() {
        manager.clearDestination();
    }

    public Optional<String> destinationName() {
        return destination().map(TraceEntry::name);
    }

    public Optional<NavigationCore.Result> currentResult() {
        Optional<TraceEntry> destination = destination();
        if (destination.isEmpty() || !NavigationOverlay.hasPlayer()) {
            return Optional.empty();
        }
        return NavigationOverlay.currentFor(destination.get());
    }

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
