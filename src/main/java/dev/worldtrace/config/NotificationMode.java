package dev.worldtrace.config;

/** How a newly recorded discovery is announced to the player. */
public enum NotificationMode {

    /** No announcement at all; the journal still records everything. */
    OFF,
    /** A short line above the hotbar. Non-blocking and out of the way. */
    ACTION_BAR;

    public static NotificationMode fromString(String raw, NotificationMode fallback) {
        if (raw == null) {
            return fallback;
        }
        for (NotificationMode mode : values()) {
            if (mode.name().equalsIgnoreCase(raw.trim())) {
                return mode;
            }
        }
        return fallback;
    }
}