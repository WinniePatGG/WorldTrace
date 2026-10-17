package dev.worldtrace.discovery;

import dev.worldtrace.model.TraceEntry;

/**
 * Something that happened to the journal, published to the presentation layer (notifications, the
 * HUD, the journal screen) so that no tracker needs to know about the GUI.
 */
public record DiscoveryEvent(TraceEntry entry, Kind kind) {

    public enum Kind {
        /** A previously unknown discovery was added. */
        CREATED,
        /** A known discovery was encountered again. */
        REVISITED,
        /** An entry was edited by the player. */
        UPDATED,
        /** An entry was deleted. */
        REMOVED
    }
}