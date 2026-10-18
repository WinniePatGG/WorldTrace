package dev.worldtrace.model;

import java.util.Locale;
import java.util.Optional;

public enum EntryCategory {

    BIOME("Biome", 0x7FC4E0, "biome"),

    STRUCTURE("Structure", 0xE0B061, "structure"),

    LANDMARK("Landmark", 0x9FD98C, "landmark"),

    BOOKMARK("Bookmark", 0xC9A0E8, "bookmark"),

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

    public int rgb() {
        return rgb;
    }

    public int argb() {
        return 0xFF000000 | rgb;
    }

    public String id() {
        return id;
    }

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

    public boolean isAutomatic() {
        return this != BOOKMARK;
    }
}
