package dev.worldtrace.config;

public enum RevisitMode {

    COUNT_ONLY,

    ANNOUNCE,

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
