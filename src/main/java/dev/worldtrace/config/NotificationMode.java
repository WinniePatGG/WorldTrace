package dev.worldtrace.config;

public enum NotificationMode {

    OFF,

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
