package dev.worldtrace.storage;

import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.model.TracePin;

import java.util.List;

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
