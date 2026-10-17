package dev.worldtrace.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The kind of thing a journal entry describes.
 *
 * <p>The set is intentionally small: it drives the filter chips in the journal, the accent colour
 * used by the detail panel, and the deduplication strategy used by the discovery manager.
 */
public enum EntryCategory {

    /** A biome the player physically stood in. Fully client-side and unambiguous. */
    BIOME("Biome", 0x7FC4E0, "biome"),
    /** A structure confirmed through a watch pin, either verified against world blocks or not. */
    STRUCTURE("Structure", 0xE0B061, "structure"),
    /** A landmark the player pinned themselves without expecting a specific structure. */
    LANDMARK("Landmark", 0x9FD98C, "landmark"),
    /** A player created bookmark at an arbitrary position. */
    BOOKMARK("Bookmark", 0xC9A0E8, "bookmark"),
    /** An exploration milestone such as a distance or depth record. */
    MILESTONE("Milestone", 0xE58F6B, "milestone");

    private final String displayName;
    private final int rgb;
    private final String id;

    EntryCategory(String displayName, int rgb, String id) {
        this.displayName = displayName;
        this.rgb = rgb;
        this.id = id;
    }

    public String displayName() {
        return displayName;
    }

    /** Packed 0xRRGGBB accent colour used by the GUI. */
    public int rgb() {
        return rgb;
    }

    /** Opaque ARGB value for direct use with the vanilla drawing helpers. */
    public int argb() {
        return 0xFF000000 | rgb;
    }

    public String id() {
        return id;
    }

    /** Stable, locale independent id used in the storage format. */
    public String serialized() {
        return name();
    }

    public static Optional<EntryCategory> fromSerialized(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (EntryCategory category : values()) {
            if (category.name().equals(normalized)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    public static Optional<EntryCategory> fromId(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (EntryCategory category : values()) {
            if (category.id.equals(normalized)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    /** Categories that only WorldTrace can create; used to guard the "delete" affordance. */
    public boolean isAutomatic() {
        return this != BOOKMARK;
    }
}