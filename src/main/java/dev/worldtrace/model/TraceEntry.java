package dev.worldtrace.model;

import dev.worldtrace.util.InputValidation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record TraceEntry(
        String id,
        String name,
        String sourceName,
        EntryCategory category,
        String dimensionId,
        int x,
        int y,
        int z,
        long firstSeenAtMillis,
        long lastSeenAtMillis,
        int visitCount,
        String biomeId,
        String structureId,
        String notes,
        List<String> tags,
        boolean favorite,
        String screenshotRef,
        boolean confirmed,
        String discoveryKey) {

    public static final int MAX_DIMENSION_ID_LENGTH = 128;

    public static final int MAX_DISCOVERY_KEY_LENGTH = 256;

    public TraceEntry {
        id = requireText(id, UUID.randomUUID().toString());
        sourceName = InputValidation.normalizeName(sourceName, InputValidation.FALLBACK_NAME);
        name = InputValidation.normalizeName(name, sourceName);
        category = Objects.requireNonNull(category, "category");
        dimensionId = normalizeDimension(dimensionId);
        firstSeenAtMillis = Math.max(0L, firstSeenAtMillis);
        lastSeenAtMillis = Math.max(0L, lastSeenAtMillis);
        if (lastSeenAtMillis < firstSeenAtMillis) {
            lastSeenAtMillis = firstSeenAtMillis;
        }
        visitCount = Math.max(0, visitCount);
        biomeId = blankToNull(biomeId, MAX_DIMENSION_ID_LENGTH);
        structureId = blankToNull(structureId, MAX_DIMENSION_ID_LENGTH);
        notes = InputValidation.normalizeNotes(notes);
        tags = InputValidation.normalizeTags(tags);
        screenshotRef = blankToNull(screenshotRef, MAX_DIMENSION_ID_LENGTH);
        discoveryKey = blankToNull(discoveryKey, MAX_DISCOVERY_KEY_LENGTH);
    }

    public static TraceEntry create(EntryCategory category, String dimensionId, int x, int y, int z, long now) {
        return new TraceEntry(
                UUID.randomUUID().toString(),
                InputValidation.FALLBACK_NAME,
                InputValidation.FALLBACK_NAME,
                category,
                dimensionId,
                x,
                y,
                z,
                now,
                now,
                1,
                null,
                null,
                "",
                List.of(),
                false,
                null,
                false,
                null);
    }

    public static TraceEntry discovered(EntryCategory category, String dimensionId, int x, int y, int z,
                                        long now, String discoveryKey) {
        return new TraceEntry(
                UUID.randomUUID().toString(),
                InputValidation.FALLBACK_NAME,
                InputValidation.FALLBACK_NAME,
                category,
                dimensionId,
                x,
                y,
                z,
                now,
                now,
                1,
                null,
                null,
                "",
                List.of(),
                false,
                null,
                false,
                discoveryKey);
    }

    public boolean isManual() {
        return category == EntryCategory.BOOKMARK;
    }

    public boolean hasDiscoveryKey() {
        return discoveryKey != null;
    }

    public boolean hasTag(String tag) {
        String normalized = InputValidation.normalizeTag(tag);
        return !normalized.isEmpty() && tags.contains(normalized);
    }

    public String searchableText() {
        StringBuilder builder = new StringBuilder(64);
        builder.append(name).append(' ')
                .append(sourceName).append(' ')
                .append(category.displayName()).append(' ')
                .append(dimensionId).append(' ')
                .append(x).append(' ').append(y).append(' ').append(z).append(' ')
                .append(notes).append(' ');
        if (biomeId != null) {
            builder.append(biomeId).append(' ');
        }
        if (structureId != null) {
            builder.append(structureId).append(' ');
        }
        for (String tag : tags) {
            builder.append(tag).append(' ');
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    public TraceEntry withName(String newName) {
        return copy(newName, notes, tags, favorite, screenshotRef, confirmed);
    }

    public TraceEntry withNotes(String newNotes) {
        return copy(name, newNotes, tags, favorite, screenshotRef, confirmed);
    }

    public TraceEntry withTags(List<String> newTags) {
        return copy(name, notes, newTags, favorite, screenshotRef, confirmed);
    }

    public TraceEntry withFavorite(boolean newFavorite) {
        return copy(name, notes, tags, newFavorite, screenshotRef, confirmed);
    }

    public TraceEntry toggleFavorite() {
        return withFavorite(!favorite);
    }

    public TraceEntry withScreenshot(String reference) {
        return copy(name, notes, tags, favorite, reference, confirmed);
    }

    public TraceEntry withConfirmation(boolean isConfirmed) {
        return copy(name, notes, tags, favorite, screenshotRef, isConfirmed);
    }

    public TraceEntry recordVisit(long now) {
        return new TraceEntry(id, name, sourceName, category, dimensionId, x, y, z,
                firstSeenAtMillis, Math.max(now, firstSeenAtMillis), visitCount + 1, biomeId,
                structureId, notes, tags, favorite, screenshotRef, confirmed, discoveryKey);
    }

    public TraceEntry withNewIdentity(long now) {
        return new TraceEntry(UUID.randomUUID().toString(), name, sourceName, category, dimensionId,
                x, y, z, Math.max(now, firstSeenAtMillis), Math.max(now, firstSeenAtMillis), 1, biomeId,
                structureId, notes, tags, favorite, screenshotRef, confirmed, discoveryKey);
    }

    private TraceEntry copy(String newName, String newNotes, List<String> newTags, boolean newFavorite,
                            String newScreenshot, boolean newConfirmed) {
        return new TraceEntry(id, newName, sourceName, category, dimensionId, x, y, z,
                firstSeenAtMillis, lastSeenAtMillis, visitCount, biomeId, structureId, newNotes,
                newTags, newFavorite, newScreenshot, newConfirmed, discoveryKey);
    }

    private static String requireText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : InputValidation.truncate(trimmed, 64);
    }

    private static String blankToNull(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : InputValidation.truncate(trimmed, maxLength);
    }

    private static String normalizeDimension(String value) {
        String trimmed = blankToNull(value, MAX_DIMENSION_ID_LENGTH);
        if (trimmed == null) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
        return trimmed;
    }
}
