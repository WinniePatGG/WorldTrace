package dev.worldtrace.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.worldtrace.model.EntryCategory;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.model.TracePin;
import dev.worldtrace.util.InputValidation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes {@link WorldData} as JSON.
 *
 * <p>The codec is intentionally forgiving on the way in and strict on the way out. Every record is
 * validated individually: a single malformed entry is reported and skipped instead of failing the
 * whole file, because losing an entire exploration history over one bad row is unacceptable.
 */
public final class TraceCodec {

    public static final int SCHEMA_VERSION = 1;

    private static final String KEY_SCHEMA = "schemaVersion";
    private static final String KEY_WORLD = "world";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_PINS = "pins";
    private static final String KEY_DESTINATION = "destinationEntryId";
    private static final String KEY_TOTAL_DISTANCE = "totalDistanceTravelled";

    private TraceCodec() {
    }

    // ------------------------------------------------------------------ write

    public static JsonObject encode(WorldData data) {
        JsonObject root = new JsonObject();
        root.addProperty(KEY_SCHEMA, SCHEMA_VERSION);
        root.add(KEY_WORLD, encodeIdentity(data.identity()));
        root.add(KEY_DESTINATION, data.destinationEntryId() == null
                ? com.google.gson.JsonNull.INSTANCE
                : new JsonPrimitive(data.destinationEntryId()));
        root.addProperty(KEY_TOTAL_DISTANCE, data.totalDistanceTravelled());

        JsonArray entries = new JsonArray();
        for (TraceEntry entry : data.entries()) {
            entries.add(encodeEntry(entry));
        }
        root.add(KEY_ENTRIES, entries);

        JsonArray pins = new JsonArray();
        for (TracePin pin : data.pins()) {
            pins.add(encodePin(pin));
        }
        root.add(KEY_PINS, pins);
        return root;
    }

    private static JsonObject encodeIdentity(WorldIdentity identity) {
        JsonObject world = new JsonObject();
        world.addProperty("id", identity.id());
        world.addProperty("displayName", identity.displayName());
        world.addProperty("kind", identity.kind().name());
        world.addProperty("lastPlayedAt", identity.lastPlayedAtMillis());
        return world;
    }

    /** Package-private so tests can build a single-record document without a wrapper. */
    static JsonObject encodeEntry(TraceEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("id", entry.id());
        json.addProperty("name", entry.name());
        json.addProperty("sourceName", entry.sourceName());
        json.addProperty("category", entry.category().serialized());
        json.addProperty("dimension", entry.dimensionId());
        json.addProperty("x", entry.x());
        json.addProperty("y", entry.y());
        json.addProperty("z", entry.z());
        json.addProperty("firstSeenAt", entry.firstSeenAtMillis());
        json.addProperty("lastSeenAt", entry.lastSeenAtMillis());
        json.addProperty("visitCount", entry.visitCount());
        json.addProperty("biome", entry.biomeId());
        json.addProperty("structure", entry.structureId());
        json.addProperty("notes", entry.notes());
        json.addProperty("favorite", entry.favorite());
        json.addProperty("confirmed", entry.confirmed());
        json.addProperty("screenshot", entry.screenshotRef());

        JsonArray tags = new JsonArray();
        for (String tag : entry.tags()) {
            tags.add(new JsonPrimitive(tag));
        }
        json.add("tags", tags);
        return json;
    }

    private static JsonObject encodePin(TracePin pin) {
        JsonObject json = new JsonObject();
        json.addProperty("id", pin.id());
        json.addProperty("label", pin.label());
        json.addProperty("structure", pin.structureId());
        json.addProperty("dimension", pin.dimensionId());
        json.addProperty("x", pin.x());
        json.addProperty("y", pin.y());
        json.addProperty("z", pin.z());
        json.addProperty("radius", pin.radius());
        json.addProperty("note", pin.note());
        json.addProperty("createdAt", pin.createdAtMillis());
        json.addProperty("resolved", pin.resolved());
        return json;
    }

    // ------------------------------------------------------------------- read

    /**
     * Decodes a document produced by {@link #encode}.
     *
     * @param fallbackTimestamp used for records whose timestamp fields are missing, so that a
     *                         hand-edited file still yields usable ordering data
     */
    public static DecodeResult decode(JsonObject root, WorldIdentity expectedIdentity, long fallbackTimestamp) {
        if (root == null) {
            throw new IllegalArgumentException("root document must not be null");
        }
        LoadReport.Builder report = LoadReport.builder();
        int fromVersion = SchemaMigrator.migrate(root, report);
        if (fromVersion != LoadReport.NO_MIGRATION) {
            report.migratedFrom(fromVersion);
        }
        if (fromVersion > SCHEMA_VERSION) {
            // Newer file than this build understands: read it best-effort but never overwrite it.
            report.readOnly(true);
            report.warn("World data was written by a newer WorldTrace version (schema "
                    + fromVersion + ", this build understands " + SCHEMA_VERSION
                    + "). It is opened read-only so nothing is destroyed.");
        }

        WorldIdentity identity = decodeIdentity(root.getAsJsonObject(KEY_WORLD), expectedIdentity, report);

        List<TraceEntry> entries = new ArrayList<>();
        JsonArray entriesArray = optArray(root, KEY_ENTRIES);
        if (entriesArray != null) {
            int index = 0;
            for (JsonElement element : entriesArray) {
                try {
                    entries.add(decodeEntry(element, fallbackTimestamp));
                    report.entryLoaded();
                } catch (RecordRejectedException e) {
                    report.entrySkipped();
                    if (report.warningsSize() < MAX_REPORTED_WARNINGS) {
                        report.warn("entries[" + index + "] skipped: " + e.getMessage());
                    }
                }
                index++;
            }
        } else {
            report.warn("Field '" + KEY_ENTRIES + "' is missing or not an array.");
        }

        List<TracePin> pins = new ArrayList<>();
        JsonArray pinsArray = optArray(root, KEY_PINS);
        if (pinsArray != null) {
            int index = 0;
            for (JsonElement element : pinsArray) {
                try {
                    pins.add(decodePin(element));
                    report.pinLoaded();
                } catch (RecordRejectedException e) {
                    report.pinSkipped();
                    if (report.warningsSize() < MAX_REPORTED_WARNINGS) {
                        report.warn("pins[" + index + "] skipped: " + e.getMessage());
                    }
                }
                index++;
            }
        }

        String destination = optString(root, KEY_DESTINATION);
        double totalDistance = Math.max(0, optDouble(root, KEY_TOTAL_DISTANCE, 0));

        WorldData data = new WorldData(identity, entries, pins, destination, totalDistance);
        return new DecodeResult(data, report.build());
    }

    private static final int MAX_REPORTED_WARNINGS = 20;

    private static WorldIdentity decodeIdentity(JsonObject json, WorldIdentity fallback, LoadReport.Builder report) {
        if (json == null) {
            report.warn("Field '" + KEY_WORLD + "' is missing; using the current world identity.");
            return fallback;
        }
        String id = optString(json, "id");
        String displayName = optString(json, "displayName");
        String kind = optString(json, "kind");
        long lastPlayed = Math.max(0L, optLong(json, "lastPlayedAt", 0L));

        WorldIdentity.Kind parsedKind = fallback.kind();
        if (kind != null) {
            try {
                parsedKind = WorldIdentity.Kind.valueOf(kind);
            } catch (IllegalArgumentException e) {
                report.warn("Unknown world kind '" + kind + "', falling back to " + fallback.kind() + ".");
            }
        }
        if (id == null || id.isBlank()) {
            report.warn("Stored world id is missing; reusing the identity of the world being loaded.");
            id = fallback.id();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = fallback.displayName();
        }
        return new WorldIdentity(id, displayName, parsedKind, lastPlayed);
    }

    private static TraceEntry decodeEntry(JsonElement element, long fallbackTimestamp) {
        if (element == null || !element.isJsonObject()) {
            throw new RecordRejectedException("entry is not a JSON object");
        }
        JsonObject json = element.getAsJsonObject();

        String categoryRaw = optString(json, "category");
        if (categoryRaw == null) {
            throw new RecordRejectedException("missing field 'category'");
        }
        Optional<EntryCategory> category = EntryCategory.fromSerialized(categoryRaw);
        if (category.isEmpty()) {
            throw new RecordRejectedException("unknown category '" + InputValidation.sanitizeForLog(categoryRaw) + "'");
        }

        String dimension = optString(json, "dimension");
        if (dimension == null) {
            throw new RecordRejectedException("missing field 'dimension'");
        }

        int x = requireInt(json, "x");
        int y = requireInt(json, "y");
        int z = requireInt(json, "z");

        String sourceName = optString(json, "sourceName");
        String name = optString(json, "name");
        if (name == null && sourceName == null) {
            throw new RecordRejectedException("missing field 'name'");
        }

        String id = optString(json, "id");
        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        long firstSeen = Math.max(0L, optLong(json, "firstSeenAt", fallbackTimestamp));
        long lastSeen = Math.max(0L, optLong(json, "lastSeenAt", firstSeen));
        int visits = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, optLong(json, "visitCount", 1)));

        List<String> tags = new ArrayList<>();
        JsonArray tagArray = optArray(json, "tags");
        if (tagArray != null) {
            for (JsonElement tagElement : tagArray) {
                if (tagElement.isJsonPrimitive()) {
                    tags.add(tagElement.getAsString());
                }
            }
        }

        try {
            return new TraceEntry(
                    id,
                    name,
                    sourceName,
                    category.get(),
                    dimension,
                    x,
                    y,
                    z,
                    firstSeen,
                    lastSeen,
                    visits,
                    optString(json, "biome"),
                    optString(json, "structure"),
                    optString(json, "notes"),
                    tags,
                    optBoolean(json, "favorite", false),
                    optString(json, "screenshot"),
                    optBoolean(json, "confirmed", category.get() != EntryCategory.STRUCTURE),
                    optString(json, "discoveryKey"));
        } catch (IllegalArgumentException e) {
            throw new RecordRejectedException(e.getMessage() == null ? "invalid entry" : e.getMessage());
        }
    }

    private static TracePin decodePin(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new RecordRejectedException("pin is not a JSON object");
        }
        JsonObject json = element.getAsJsonObject();

        String dimension = optString(json, "dimension");
        if (dimension == null) {
            throw new RecordRejectedException("missing field 'dimension'");
        }
        String label = optString(json, "label");
        if (label == null) {
            throw new RecordRejectedException("missing field 'label'");
        }
        int x = requireInt(json, "x");
        int y = requireInt(json, "y");
        int z = requireInt(json, "z");

        return new TracePin(
                optString(json, "id"),
                label,
                optString(json, "structure"),
                dimension,
                x,
                y,
                z,
                (int) optDouble(json, "radius", TracePin.DEFAULT_RADIUS),
                optString(json, "note"),
                Math.max(0L, optLong(json, "createdAt", 0L)),
                optBoolean(json, "resolved", false));
    }

    // ---------------------------------------------------------------- helpers

    private static int requireInt(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new RecordRejectedException("field '" + key + "' is missing or not a number");
        }
        double value = element.getAsDouble();
        if (Double.isNaN(value) || Double.isInfinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new RecordRejectedException("field '" + key + "' is out of range");
        }
        return (int) value;
    }

    private static String optString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static double optDouble(JsonObject json, String key, double fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return fallback;
        }
        double value = primitive.getAsDouble();
        return Double.isNaN(value) || Double.isInfinite(value) ? fallback : value;
    }

    /** Integer-ish numeric field widened to {@code long}, rejecting non-finite values. */
    private static long optLong(JsonObject json, String key, long fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return fallback;
        }
        double value = primitive.getAsDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        if (value > Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (value < Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        return (long) value;
    }

    private static boolean optBoolean(JsonObject json, String key, boolean fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsInt() != 0;
        }
        return fallback;
    }

    private static JsonArray optArray(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        return element.getAsJsonArray();
    }

    /** Thrown internally when a single record cannot be turned into a valid model object. */
    private static final class RecordRejectedException extends RuntimeException {
        private RecordRejectedException(String message) {
            super(message);
        }
    }

    /** Result of a decode: the recovered data plus an honest account of what was lost. */
    public record DecodeResult(WorldData data, LoadReport report) {
    }
}