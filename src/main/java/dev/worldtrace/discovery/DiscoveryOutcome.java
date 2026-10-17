package dev.worldtrace.discovery;

import dev.worldtrace.model.TraceEntry;

/**
 * Outcome of a discovery attempt.
 *
 * @param created        true when a brand new entry was added to the journal
 * @param revisitAnnounced true when the entry already existed and the config asked to announce it
 * @param entry          the stored entry after the attempt, or {@code null} when the journal is full
 */
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