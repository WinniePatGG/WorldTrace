package dev.worldtrace.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Forward-migration of stored world data.
 *
 * <p>Every saved document carries {@code schemaVersion}. A file without the field is treated as
 * schema 0 (the shape used before versioning existed) and is upgraded in place. The rule for
 * upgrades is that they may only ever <em>add</em> information or fix an obvious mistake — they must
 * never delete records, because the user cannot recover a lost exploration history.
 */
final class SchemaMigrator {

    private SchemaMigrator() {
    }

    /**
     * Brings {@code root} up to {@link TraceCodec#SCHEMA_VERSION} and reports the version the
     * document was originally written with ({@link LoadReport#NO_MIGRATION} when already current).
     */
    static int migrate(JsonObject root, LoadReport.Builder report) {
        int version = readVersion(root);
        int original = version == TraceCodec.SCHEMA_VERSION ? LoadReport.NO_MIGRATION : version;

        while (version < TraceCodec.SCHEMA_VERSION) {
            switch (version) {
                case 0 -> migrateV0ToV1(root);
                default -> {
                    // No handler can be reached for a version below SCHEMA_VERSION that we do not
                    // know about, because readVersion() clamps unknown values to the current one.
                    report.warn("No migration path from schema " + version + "; leaving data untouched.");
                    return original;
                }
            }
            int next = readVersion(root);
            if (next <= version) {
                // Defensive: a migration that forgets to bump the version would loop forever.
                report.warn("Migration from schema " + version + " did not advance the version.");
                return original;
            }
            version = next;
        }
        return original;
    }

    /**
     * Reads the declared schema version. A missing or unparseable marker is treated as schema 0;
     * a marker higher than this build understands is returned verbatim so {@link TraceCodec} can
     * open the file read-only instead of overwriting a newer journal.
     */
    private static int readVersion(JsonObject root) {
        JsonElement element = root.get("schemaVersion");
        if (element == null || !element.isJsonPrimitive()) {
            root.addProperty("schemaVersion", 0);
            return 0;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            root.addProperty("schemaVersion", 0);
            return 0;
        }
    }

    /**
     * Schema 0 was the pre-release format: entries existed but a handful of fields were optional
     * and the schema marker was absent. Everything missing here is derived from what is present.
     */
    private static void migrateV0ToV1(JsonObject root) {
        JsonArray entries = root.has("entries") && root.get("entries").isJsonArray()
                ? root.getAsJsonArray("entries")
                : new JsonArray();

        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();

            // `title` was the pre-release spelling of `name`.
            if (!entry.has("name") && entry.has("title")) {
                entry.add("name", entry.get("title"));
            }
            // `time` was a plain epoch-second value; `firstSeenAt` uses milliseconds.
            if (!entry.has("firstSeenAt") && entry.has("time")) {
                long epochSeconds = entry.get("time").getAsLong();
                entry.addProperty("firstSeenAt", epochSeconds * 1000L);
            }
            if (!entry.has("lastSeenAt") && entry.has("firstSeenAt")) {
                entry.add("lastSeenAt", entry.get("firstSeenAt"));
            }
            if (!entry.has("visitCount")) {
                entry.addProperty("visitCount", 1);
            }
            if (!entry.has("favorite")) {
                entry.addProperty("favorite", false);
            }
            if (!entry.has("tags")) {
                entry.add("tags", new JsonArray());
            }
            if (!entry.has("notes")) {
                entry.addProperty("notes", "");
            }
            // Pre-release files had no sourceName; the identifier-derived name is the best source.
            if (!entry.has("sourceName") && entry.has("name")) {
                entry.add("sourceName", entry.get("name"));
            }
        }

        if (!root.has("pins")) {
            root.add("pins", new JsonArray());
        }
        if (!root.has("totalDistanceTravelled")) {
            root.addProperty("totalDistanceTravelled", 0);
        }
        root.addProperty("schemaVersion", 1);
    }
}