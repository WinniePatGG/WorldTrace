package dev.worldtrace.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Timestamp rendering for the journal. WorldTrace stores wall-clock milliseconds so that the
 * "discovered on" line remains meaningful between sessions; in-game time is not persisted because
 * it is not comparable across dimensions.
 */
public final class Timestamps {

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter COMPACT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    private Timestamps() {
    }

    public static String formatFull(long epochMillis) {
        if (epochMillis <= 0) {
            return "unknown";
        }
        return DISPLAY.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String formatCompact(long epochMillis) {
        if (epochMillis <= 0) {
            return "unknown";
        }
        return COMPACT.format(Instant.ofEpochMilli(epochMillis));
    }

    /** Human readable "how long ago", used in the journal detail panel. */
    public static String formatRelative(long epochMillis, long nowMillis) {
        if (epochMillis <= 0) {
            return "unknown";
        }
        long delta = Math.max(0, nowMillis - epochMillis);
        long minutes = delta / 60_000L;
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = hours / 24;
        if (days < 30) {
            return days + (days == 1 ? " day ago" : " days ago");
        }
        long months = days / 30;
        if (months < 12) {
            return months + (months == 1 ? " month ago" : " months ago");
        }
        long years = days / 365;
        return years + (years == 1 ? " year ago" : " years ago");
    }
}