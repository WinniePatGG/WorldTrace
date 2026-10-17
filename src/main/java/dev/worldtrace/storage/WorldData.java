package dev.worldtrace.storage;

import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.model.TracePin;

import java.util.List;

/**
 * Immutable snapshot of everything WorldTrace persists for a single world.
 *
 * <p>Snapshots are taken on the game thread (cheap: they only copy references) and handed to the
 * background writer, so the disk never touches the render thread and the writer can never observe
 * a half-mutated collection.
 */
public record WorldData(
        WorldIdentity identity,
        List<TraceEntry> entries,
        List<TracePin> pins,
        String destinationEntryId,
        double totalDistanceTravelled) {

    public WorldData {
        entries = List.copyOf(entries);
        pins = List.copyOf(pins);
        if (destinationEntryId != null && destinationEntryId.isBlank()) {
            destinationEntryId = null;
        }
        if (Double.isNaN(totalDistanceTravelled) || totalDistanceTravelled < 0) {
            totalDistanceTravelled = 0;
        }
    }

    public static WorldData empty(WorldIdentity identity) {
        return new WorldData(identity, List.of(), List.of(), null, 0);
    }

    public int size() {
        return entries.size();
    }
}