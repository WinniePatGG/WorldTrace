package dev.worldtrace.model;

import dev.worldtrace.util.InputValidation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A single record in the world journal.
 *
 * <p>Instances are immutable; edits produce a copy through the {@code with*} helpers. The record
 * deliberately keeps {@link #sourceName()} separate from {@link #name()} so that renaming a biome
 * or bookmark never destroys the identifier-derived metadata used for deduplication, search and
 * the technical id column. {@link #discoveryKey()} carries the deduplication identity and survives
 * both renaming and reloads, which is what lets WorldTrace recognise a biome it already knows about.
 *
 * <p>This class has no Minecraft dependency on purpose: the storage codec, the search engine and
 * the tests all operate on plain Java data.
 */
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

    /** Longest stored dimension identifier we are willing to accept from a file. */
    public static final int MAX_DIMENSION_ID_LENGTH = 128;
    /** Bound for the serialised deduplication key; keeps pathological files from bloating memory. */
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

    /** Bookmarks are player-created, so they never carry a discovery key. */
    public boolean isManual() {
        return category == EntryCategory.BOOKMARK;
    }

    public boolean hasDiscoveryKey() {
        return discoveryKey != null;
    }

    /** Tags are compared case-insensitively; the record already stores them normalised. */
    public boolean hasTag(String tag) {
        String normalized = InputValidation.normalizeTag(tag);
        return !normalized.isEmpty() && tags.contains(normalized);
    }

    /**
     * Lowercased haystack used by the journal search box. Recomputed on demand rather than cached
     * because search runs on filter changes and on typing, not every frame.
     */
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

    /** Records a revisit without creating a duplicate entry. */
    public TraceEntry recordVisit(long now) {
        return new TraceEntry(id, name, sourceName, category, dimensionId, x, y, z,
                firstSeenAtMillis, Math.max(now, firstSeenAtMillis), visitCount + 1, biomeId,
                structureId, notes, tags, favorite, screenshotRef, confirmed, discoveryKey);
    }

    /**
     * Produces a genuinely separate entry for the same discovery, used by the
     * {@code RECORD_SEPARATE} revisit mode to keep the full travel chronology. The new record gets a
     * fresh id so it does not overwrite the original, but keeps the discovery key so the pair is
     * still recognisably the same thing.
     */
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