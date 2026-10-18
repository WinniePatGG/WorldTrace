package dev.worldtrace.discovery;

import dev.worldtrace.model.TraceEntry;

public record DiscoveryEvent(TraceEntry entry, Kind kind) {

    public enum Kind {

        CREATED,

        REVISITED,

        UPDATED,

        REMOVED
    }
}
