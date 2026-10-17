package dev.worldtrace.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Boundary validation for every piece of user- or game-supplied text that ends up in a
 * {@link dev.worldtrace.model.TraceEntry}. Keeping this in one place guarantees that the GUI,
 * the storage codec and the discovery trackers all agree on what a "valid" value is.
 */
public final class InputValidation {

    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_NOTES_LENGTH = 1000;
    public static final int MAX_TAG_LENGTH = 24;
    public static final int MAX_TAGS = 12;
    public static final int MAX_LABEL_LENGTH = 64;

    public static final String FALLBACK_NAME = "Unnamed";
    public static final String FALLBACK_LABEL = "Waypoint";

    private InputValidation() {
    }

    /**
     * Trims, collapses internal whitespace and enforces a length cap.
     *
     * @param fallback value substituted when the input is empty or blank
     */
    public static String normalizeName(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String collapsed = raw.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return fallback;
        }
        return truncate(collapsed, MAX_NAME_LENGTH);
    }

    public static String normalizeName(String raw) {
        return normalizeName(raw, FALLBACK_NAME);
    }

    public static String normalizeLabel(String raw) {
        return normalizeName(raw, FALLBACK_LABEL);
    }

    /** Notes are optional, so an empty result stays empty instead of becoming a placeholder. */
    public static String normalizeNotes(String raw) {
        if (raw == null) {
            return "";
        }
        return truncate(raw.trim(), MAX_NOTES_LENGTH);
    }

    /**
     * Tags are used as search keys, so they are aggressively reduced to a lowercase slug.
     *
     * @return the normalised tag, or an empty string when nothing usable remains
     */
    public static String normalizeTag(String raw) {
        if (raw == null) {
            return "";
        }
        String slug = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_+-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        return truncate(slug, MAX_TAG_LENGTH);
    }

    /**
     * Normalises a tag collection: drops empties, removes duplicates and caps the total size.
     * Insertion order is preserved so the journal renders tags the way the player typed them.
     */
    public static List<String> normalizeTags(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String candidate : raw) {
            String tag = normalizeTag(candidate);
            if (!tag.isEmpty()) {
                result.add(tag);
            }
            if (result.size() >= MAX_TAGS) {
                break;
            }
        }
        return List.copyOf(new ArrayList<>(result));
    }

    /**
     * Parses a user supplied tag string (comma or whitespace separated) into normalised tags.
     */
    public static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return normalizeTags(List.of(raw.split("[,\\s]+")));
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * Escapes a value for embedding in a log line without breaking the log format.
     */
    public static String sanitizeForLog(String raw) {
        if (raw == null) {
            return "null";
        }
        return raw.replaceAll("[\\r\\n\\t]", " ").trim();
    }
}