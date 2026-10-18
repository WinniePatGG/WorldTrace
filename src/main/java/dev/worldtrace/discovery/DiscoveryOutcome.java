package dev.worldtrace.discovery;

import dev.worldtrace.model.TraceEntry;

public record DiscoveryOutcome(TraceEntry entry, boolean created, boolean revisitAnnounced) {

    public static DiscoveryOutcome created(TraceEntry entry) {
        return new DiscoveryOutcome(entry, true, false);
    }

    public static DiscoveryOutcome revisited(TraceEntry entry, boolean announced) {
        return new DiscoveryOutcome(entry, false, announced);
    }

    public static DiscoveryOutcome rejected() {
        return new DiscoveryOutcome(null, false, false);
    }

    public boolean accepted() {
        return entry != null;
    }
}
