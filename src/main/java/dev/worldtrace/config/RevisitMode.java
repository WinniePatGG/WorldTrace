package dev.worldtrace.config;

/**
 * Controls what happens when a discovery that already exists is encountered again.
 *
 * <p>Revisiting a known biome is normal gameplay and must not spam the journal, but a player who
 * treats the journal as a travel log may want every visit counted or even announced.
 */
public enum RevisitMode {

    /** Re-entries update the visit counter and last-seen time silently. */
    COUNT_ONLY,
    /** Same as {@link #COUNT_ONLY} but the player is also told. */
    ANNOUNCE,
    /** Every re-entry becomes its own entry, preserving the full travel chronology. */
    RECORD_SEPARATE;

    public static RevisitMode fromString(String raw, RevisitMode fallback) {
        if (raw == null) {
            return fallback;
        }
        for (RevisitMode mode : values()) {
            if (mode.name().equalsIgnoreCase(raw.trim())) {
                return mode;
            }
        }
        return fallback;
    }
}