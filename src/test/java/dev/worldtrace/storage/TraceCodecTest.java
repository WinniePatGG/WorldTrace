package dev.worldtrace.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.worldtrace.model.EntryCategory;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.model.TracePin;
import dev.worldtrace.testsupport.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Serialisation, validation of hostile input, and schema migration. */
class TraceCodecTest {

    private static final String OVERWORLD = "minecraft:overworld";

    private static TraceEntry sample() {
        return new TraceEntry(
                "entry-1", "My Base", "My Base", EntryCategory.BOOKMARK, OVERWORLD,
                -120, 64, 305, 1_700_000_000_000L, 1_700_000_500_000L, 3,
                null, null, "diamond storage\nsecond line", List.of("base", "home"), true,
                "screenshots/sp-entry-1/abc.png", true, null);
    }

    /**
     * An automatically discovered entry. Its discovery key is what stops a biome being recorded
     * again after a reload, so it has to survive a full encode/decode cycle.
     */
    private static TraceEntry discovered() {
        return new TraceEntry(
                "entry-2", "Plains", "Plains", EntryCategory.BIOME, OVERWORLD,
                12, 64, -40, 1_700_000_000_000L, 1_700_000_900_000L, 4,
                "minecraft:plains", null, "", List.of(), false, null, true,
                "BIOME|" + OVERWORLD + "|minecraft:plains");
    }

    private static WorldIdentity identity() {
        return Fixtures.world("Codec");
    }

    @Test
    @DisplayName("A full round trip preserves every field")
    void roundTrip() {
        TraceEntry original = sample();
        WorldData data = new WorldData(identity(), List.of(original),
                List.of(TracePin.structure("Temple", "minecraft:desert_pyramid", OVERWORLD, 1, 2, 3, 48, "note", 42L)),
                original.id(), 12_345.5);

        TraceCodec.DecodeResult decoded = TraceCodec.decode(TraceCodec.encode(data), identity(), 1L);

        assertTrue(decoded.report().isClean(), "A clean round trip must not report warnings");
        assertEquals(1, decoded.report().entriesLoaded());
        assertEquals(1, decoded.report().pinsLoaded());
        assertEquals(original, decoded.data().entries().get(0));
        assertEquals(data.pins().get(0), decoded.data().pins().get(0));
        assertEquals(original.id(), decoded.data().destinationEntryId());
        assertEquals(12_345.5, decoded.data().totalDistanceTravelled());
    }

    @Test
    @DisplayName("The discovery key survives a round trip")
    void discoveryKeySurvivesARoundTrip() {
        TraceEntry original = discovered();
        TraceCodec.DecodeResult decoded = TraceCodec.decode(
                TraceCodec.encode(new WorldData(identity(), List.of(original), List.of(), null, 0)),
                identity(), 1L);

        assertEquals(original, decoded.data().entries().get(0));
        assertEquals(original.discoveryKey(), decoded.data().entries().get(0).discoveryKey());
    }

    @Test
    @DisplayName("A manual entry round trips with no discovery key")
    void manualEntryHasNoDiscoveryKey() {
        TraceEntry original = sample();
        TraceCodec.DecodeResult decoded = TraceCodec.decode(
                TraceCodec.encode(new WorldData(identity(), List.of(original), List.of(), null, 0)),
                identity(), 1L);

        assertEquals(original, decoded.data().entries().get(0));
        assertNull(decoded.data().entries().get(0).discoveryKey());
    }

    @Test
    @DisplayName("An empty journal decodes to empty collections, not nulls")
    void emptyDocument() {
        TraceCodec.DecodeResult decoded = TraceCodec.decode(new JsonObject(), identity(), 1L);
        assertEquals(0, decoded.report().entriesLoaded());
        assertTrue(decoded.data().entries().isEmpty());
        assertTrue(decoded.data().pins().isEmpty());
        assertNull(decoded.data().destinationEntryId());
    }

    @Test
    @DisplayName("A single invalid record is skipped without losing the others")
    void invalidRecordIsSkipped() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("world", new JsonObject());

        JsonArray entries = new JsonArray();
        entries.add(TraceCodec.encodeEntry(sample()));
        entries.add(entryWithCategory("minecraft:not_a_category"));
        entries.add(entryMissing("y"));
        entries.add(entryWithStringCoordinates());
        entries.add(TraceCodec.encodeEntry(sample()));
        root.add("entries", entries);

        TraceCodec.DecodeResult decoded = TraceCodec.decode(root, identity(), 1L);

        assertEquals(2, decoded.report().entriesLoaded());
        assertEquals(3, decoded.report().entriesSkipped());
        assertEquals(2, decoded.data().entries().size());
        assertFalse(decoded.report().isClean());
        assertTrue(decoded.report().warnings().stream()
                .anyMatch(warning -> warning.contains("unknown category 'minecraft:not_a_category'")));
    }

    @Test
    @DisplayName("A record with no dimension is rejected rather than silently defaulted")
    void missingDimensionIsRejected() {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", "Nowhere");
        entry.addProperty("category", "BIOME");
        entry.addProperty("x", 0);
        entry.addProperty("y", 64);
        entry.addProperty("z", 0);

        TraceCodec.DecodeResult decoded = TraceCodec.decode(rootWithEntries(entry), identity(), 1L);
        assertEquals(0, decoded.report().entriesLoaded());
        assertEquals(1, decoded.report().entriesSkipped());
    }

    @Test
    @DisplayName("Missing timestamps fall back to the supplied default instead of 1970")
    void missingTimestampUsesFallback() {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", "Old Entry");
        entry.addProperty("category", "BIOME");
        entry.addProperty("dimension", OVERWORLD);
        entry.addProperty("x", 1);
        entry.addProperty("y", 2);
        entry.addProperty("z", 3);

        long fallback = 1_234_567_890L;
        TraceCodec.DecodeResult decoded = TraceCodec.decode(rootWithEntries(entry), identity(), fallback);

        assertEquals(1, decoded.report().entriesLoaded());
        assertEquals(fallback, decoded.data().entries().get(0).firstSeenAtMillis());
    }

    @Test
    @DisplayName("A record without an id is repaired with a generated one")
    void missingIdIsRepaired() {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", "No Id");
        entry.addProperty("category", "BIOME");
        entry.addProperty("dimension", OVERWORLD);
        entry.addProperty("x", 1);
        entry.addProperty("y", 2);
        entry.addProperty("z", 3);

        TraceEntry decodedEntry = TraceCodec.decode(rootWithEntries(entry), identity(), 1L)
                .data().entries().get(0);
        assertNotNull(decodedEntry.id());
        assertEquals(36, decodedEntry.id().length(), "A repaired id should be a UUID");
    }

    @Test
    @DisplayName("A blank name falls back to the source name")
    void blankNameFallsBack() {
        JsonObject entry = TraceCodec.encodeEntry(sample());
        entry.addProperty("name", "   ");
        entry.addProperty("sourceName", "Plains");

        TraceEntry decodedEntry = TraceCodec.decode(rootWithEntries(entry), identity(), 1L)
                .data().entries().get(0);
        assertEquals("Plains", decodedEntry.name());
    }

    @Test
    @DisplayName("Overlong text is truncated at load time")
    void overlongTextIsTruncated() {
        String huge = "n".repeat(5_000);
        JsonObject entry = TraceCodec.encodeEntry(sample());
        entry.addProperty("notes", huge);
        entry.addProperty("name", huge);

        TraceEntry decodedEntry = TraceCodec.decode(rootWithEntries(entry), identity(), 1L)
                .data().entries().get(0);
        assertTrue(decodedEntry.notes().length() <= 1_000);
        assertTrue(decodedEntry.name().length() <= 48);
    }

    @Test
    @DisplayName("Timestamps stored in seconds are migrated to milliseconds")
    void legacySchemaZeroIsMigrated() {
        JsonObject root = new JsonObject();
        JsonObject world = new JsonObject();
        world.addProperty("id", identity().id());
        world.addProperty("displayName", "Legacy");
        root.add("world", world);

        JsonArray entries = new JsonArray();
        JsonObject legacy = new JsonObject();
        legacy.addProperty("id", "legacy-1");
        legacy.addProperty("title", "Old Title");
        legacy.addProperty("category", "BIOME");
        legacy.addProperty("dimension", OVERWORLD);
        legacy.addProperty("biome", "minecraft:plains");
        legacy.addProperty("x", 100);
        legacy.addProperty("y", 64);
        legacy.addProperty("z", -200);
        legacy.addProperty("time", 1_700_000_000L);
        entries.add(legacy);
        root.add("entries", entries);

        TraceCodec.DecodeResult decoded = TraceCodec.decode(root, identity(), 1L);

        assertEquals(0, decoded.report().migratedFromSchema());
        assertEquals(1, decoded.report().entriesLoaded());

        TraceEntry migrated = decoded.data().entries().get(0);
        assertEquals("Old Title", migrated.name());
        assertEquals("Old Title", migrated.sourceName(), "The migration derives sourceName from the old title");
        assertEquals(1_700_000_000_000L, migrated.firstSeenAtMillis());
        assertEquals(1_700_000_000_000L, migrated.lastSeenAtMillis());
        assertEquals(1, migrated.visitCount());
        assertTrue(migrated.tags().isEmpty());
        assertFalse(migrated.favorite());
    }

    @Test
    @DisplayName("A file from a newer schema is read but never overwritten")
    void newerSchemaIsReadOnly() {
        JsonObject root = TraceCodec.encode(new WorldData(identity(), List.of(sample()), List.of(), null, 0));
        root.addProperty("schemaVersion", 99);

        TraceCodec.DecodeResult decoded = TraceCodec.decode(root, identity(), 1L);

        assertTrue(decoded.report().readOnly());
        assertTrue(decoded.report().warnings().stream().anyMatch(w -> w.contains("newer WorldTrace version")));
        // The records are still readable, so the player can see their history.
        assertEquals(1, decoded.data().entries().size());
    }

    @Test
    @DisplayName("An entry with a blank dimension id is rejected by the model itself")
    void modelRejectsBlankDimension() {
        assertThrows(IllegalArgumentException.class, () -> new TraceEntry(
                "id", "n", "n", EntryCategory.BIOME, "   ", 0, 0, 0, 1, 1, 1,
                null, null, "", List.of(), false, null, true, null));
    }

    @Test
    @DisplayName("Malformed JSON is quarantined and a backup is recovered")
    void malformedJsonIsRecoveredFromBackup(@TempDir Path dir) throws Exception {
        StoragePaths paths = new StoragePaths(dir);
        Path file = paths.worldFile(identity());
        Files.createDirectories(file.getParent());

        JsonObject valid = TraceCodec.encode(new WorldData(identity(), List.of(sample()), List.of(), null, 0));
        Files.writeString(file, valid.toString(), StandardCharsets.UTF_8);
        JsonFileStore.write(file, valid);
        Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);

        JsonObject recovered = JsonFileStore.read(file).orElseThrow();
        assertTrue(recovered.has("entries"));
        assertTrue(Files.list(dir.resolve("worldtrace/worlds")).anyMatch(
                path -> path.getFileName().toString().contains(".corrupt-")));
    }

    @Test
    @DisplayName("Encoding produces the documented schema version")
    void encodesSchemaVersion() {
        JsonObject root = TraceCodec.encode(WorldData.empty(identity()));
        assertEquals(TraceCodec.SCHEMA_VERSION, root.get("schemaVersion").getAsInt());
        assertNotNull(JsonParser.parseString(root.toString()));
    }

    // ------------------------------------------------------------- fixtures

    private static JsonObject rootWithEntries(JsonObject entry) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        JsonObject world = new JsonObject();
        world.addProperty("id", identity().id());
        world.addProperty("displayName", "Fixture");
        root.add("world", world);
        JsonArray entries = new JsonArray();
        entries.add(entry);
        root.add("entries", entries);
        return root;
    }

    private static JsonObject entryWithCategory(String category) {
        JsonObject entry = TraceCodec.encodeEntry(sample());
        entry.addProperty("category", category);
        return entry;
    }

    private static JsonObject entryMissing(String field) {
        JsonObject entry = TraceCodec.encodeEntry(sample());
        entry.remove(field);
        return entry;
    }

    private static JsonObject entryWithStringCoordinates() {
        JsonObject entry = TraceCodec.encodeEntry(sample());
        entry.add("x", new com.google.gson.JsonPrimitive("not a number"));
        return entry;
    }
}
