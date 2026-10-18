package dev.worldtrace.model;

import dev.worldtrace.util.InputValidation;

import java.util.Objects;
import java.util.UUID;

public record TracePin(
        String id,
        String label,
        String structureId,
        String dimensionId,
        int x,
        int y,
        int z,
        int radius,
        String note,
        long createdAtMillis,
        boolean resolved) {

    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 128;
    public static final int DEFAULT_RADIUS = 48;

    public TracePin {
        id = Objects.requireNonNull(Objects.requireNonNull(id, "id"), "id").trim();
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        label = InputValidation.normalizeLabel(label);
        note = InputValidation.normalizeNotes(note);
        dimensionId = Objects.requireNonNull(dimensionId, "dimensionId").trim();
        structureId = structureId == null || structureId.isBlank()
                ? null
                : InputValidation.truncate(structureId.trim(), 128);
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        createdAtMillis = Math.max(0L, createdAtMillis);
    }

    public static TracePin landmark(String label, String dimensionId, int x, int y, int z, long now) {
        return new TracePin(UUID.randomUUID().toString(), label, null, dimensionId, x, y, z,
                DEFAULT_RADIUS, "", now, false);
    }

    public static TracePin structure(String label, String structureId, String dimensionId,
                                     int x, int y, int z, int radius, String note, long now) {
        return new TracePin(UUID.randomUUID().toString(), label, structureId, dimensionId, x, y, z,
                radius, note, now, false);
    }

    public EntryCategory category() {
        return structureId == null ? EntryCategory.LANDMARK : EntryCategory.STRUCTURE;
    }

    public TracePin withResolved(boolean value) {
        return new TracePin(id, label, structureId, dimensionId, x, y, z, radius, note,
                createdAtMillis, value);
    }

    public TracePin withRadius(int newRadius) {
        return new TracePin(id, label, structureId, dimensionId, x, y, z, newRadius, note,
                createdAtMillis, resolved);
    }
}
