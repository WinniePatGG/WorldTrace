package dev.worldtrace.journal;

public enum SortMode {

    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME("Name (A-Z)"),
    FAVORITES("Favorites first");

    private final String displayName;

    SortMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public SortMode next() {
        SortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static SortMode fromString(String raw, SortMode fallback) {
        if (raw == null) {
            return fallback;
        }
        for (SortMode mode : values()) {
            if (mode.name().equalsIgnoreCase(raw.trim())) {
                return mode;
            }
        }
        return fallback;
    }
}
