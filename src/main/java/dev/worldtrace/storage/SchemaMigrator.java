package dev.worldtrace.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class SchemaMigrator {

    private SchemaMigrator() {
    }

    static int migrate(JsonObject root, LoadReport.Builder report) {
        int version = readVersion(root);
        int original = version == TraceCodec.SCHEMA_VERSION ? LoadReport.NO_MIGRATION : version;

        while (version < TraceCodec.SCHEMA_VERSION) {
            switch (version) {
                case 0 -> migrateV0ToV1(root);
                default -> {

                    report.warn("No migration path from schema " + version + "; leaving data untouched.");
                    return original;
                }
            }
            int next = readVersion(root);
            if (next <= version) {

                report.warn("Migration from schema " + version + " did not advance the version.");
                return original;
            }
            version = next;
        }
        return original;
    }

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

    private static void migrateV0ToV1(JsonObject root) {
        JsonArray entries = root.has("entries") && root.get("entries").isJsonArray()
                ? root.getAsJsonArray("entries")
                : new JsonArray();

        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();

            if (!entry.has("name") && entry.has("title")) {
                entry.add("name", entry.get("title"));
            }

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
