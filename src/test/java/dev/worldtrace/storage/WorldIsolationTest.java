package dev.worldtrace.storage;

import dev.worldtrace.config.WorldTraceConfig;
import dev.worldtrace.discovery.DiscoveryOutcome;
import dev.worldtrace.model.EntryCategory;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.model.TracePin;
import dev.worldtrace.testsupport.Fixtures;
import dev.worldtrace.testsupport.TestWorlds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIsolationTest {

    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    @DisplayName("World identity is stable for the same save and unique for different saves")
    void sameNameDifferentIds(@TempDir Path dir) {

        assertEquals(WorldIdentity.singleplayer("New World").id(),
                WorldIdentity.singleplayer("  New World  ").id());
        assertEquals(WorldIdentity.singleplayer("Alpha").id(), WorldIdentity.singleplayer("Alpha").id());

        assertNotEquals(WorldIdentity.singleplayer("A/B").id(), WorldIdentity.singleplayer("A-B").id());
        assertNotEquals(WorldIdentity.singleplayer("Alpha").id(), WorldIdentity.singleplayer("Beta").id());
    }

    @Test
    @DisplayName("Singleplayer and multiplayer identities never collide")
    void kindPrefixesAreDistinct(@TempDir Path dir) {
        assertNotEquals(WorldIdentity.singleplayer("localhost").id(),
                WorldIdentity.multiplayer("localhost").id());
    }

    @Test
    @DisplayName("World ids are always safe file names")
    void idsAreSafeFileNames() {
        String id = WorldIdentity.singleplayer("../../etc/passwd <>:\"|?").id();
        assertTrue(id.matches("[a-z0-9._-]+"), "Generated id must be filename safe but was " + id);
        assertFalse(id.contains(".."));
    }

    @Test
    @DisplayName("Switching worlds never leaks discoveries into the new world")
    void switchingWorldsClearsEntries(@TempDir Path dir) {
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(Fixtures.world("First"), null);
            worlds.manager().recordBiome("minecraft:plains", OVERWORLD, 1, 64, 1, Fixtures.T0);
            worlds.manager().createBookmark("Camp", OVERWORLD, 2, 64, 2, Fixtures.T0);
            assertEquals(2, worlds.manager().store().entryCount());
            worlds.flush();

            worlds.manager().loadWorld(Fixtures.world("Second"), null);
            assertEquals(0, worlds.manager().store().entryCount(),
                    "A fresh world must start with an empty journal");
            assertEquals(0, worlds.manager().store().pinList().size());

            worlds.manager().recordBiome("minecraft:swamp", OVERWORLD, 9, 64, 9, Fixtures.T0);
            worlds.flush();

            worlds.manager().loadWorld(Fixtures.world("First"), null);
            assertEquals(2, worlds.manager().store().entryCount(), "The original world must be intact");
            assertTrue(worlds.manager().store().entryList().stream()
                    .anyMatch(entry -> "minecraft:swamp".equals(entry.biomeId()) == false));
        }
    }

    @Test
    @DisplayName("Data persists across a simulated restart")
    void persistsAcrossRestart(@TempDir Path dir) {
        WorldIdentity world = Fixtures.world("Persist");
        try (TestWorlds first = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            first.manager().loadWorld(world, null);
            first.manager().recordBiome("minecraft:badlands", OVERWORLD, 100, 70, -200, Fixtures.T0);
            TraceEntry bookmark = first.manager().createBookmark("Watchtower", OVERWORLD, 5, 80, 5, Fixtures.T0);
            first.manager().updateEntry(bookmark.id(), "Watchtower", "built on a hill", java.util.List.of("home"),
                    true, null);
            first.manager().addPin(TracePin.structure("Ocean Monument", "minecraft:ocean_monument", OVERWORLD,
                    3, 60, 4, 48, "check the loot", Fixtures.T0));
            first.manager().addDistanceTravelled(4_242.5);
            first.manager().setDestination(bookmark.id());
            first.flush();

            assertTrue(Files.isRegularFile(first.paths().worldFile(world)));
        }

        try (TestWorlds second = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            second.manager().loadWorld(world, null);

            assertEquals(2, second.manager().store().entryCount());
            assertEquals(1, second.manager().store().pinList().size());
            assertEquals(4_242.5, second.manager().totalDistanceTravelled());
            assertTrue(second.manager().store().destinationEntryId() != null);

            TraceEntry restored = second.manager().store().entry(second.manager().destinationEntryId())
                    .orElseThrow();
            assertEquals("Watchtower", restored.name());
            assertTrue(restored.favorite());
            assertEquals("built on a hill", restored.notes());
            assertEquals(java.util.List.of("home"), restored.tags());
        }
    }

    @Test
    @DisplayName("The world id override redirects storage to a different journal")
    void worldIdOverrideRedirects(@TempDir Path dir) {
        WorldIdentity world = Fixtures.world("Colliding Names");
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(world, null);
            worlds.manager().recordBiome("minecraft:plains", OVERWORLD, 0, 64, 0, Fixtures.T0);
            worlds.flush();

            Path original = worlds.paths().worldFile(world);
            assertTrue(Files.isRegularFile(original));
        }

        WorldTraceConfig override = WorldTraceConfig.builder().worldIdOverride("my-save").build();
        try (TestWorlds worlds = TestWorlds.create(dir, override)) {
            worlds.manager().loadWorld(world, null);
            assertEquals("my-save", worlds.manager().store().identity().id());
            assertEquals(0, worlds.manager().store().entryCount());
        }
    }

    @Test
    @DisplayName("A file written by a newer version is never overwritten")
    void newerFileIsReadOnly(@TempDir Path dir) throws Exception {
        WorldIdentity world = Fixtures.world("Future");
        try (TestWorlds setup = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            setup.manager().loadWorld(world, null);
            setup.manager().recordBiome("minecraft:plains", OVERWORLD, 0, 64, 0, Fixtures.T0);
            setup.flush();

            Path file = setup.paths().worldFile(world);
            String contents = Files.readString(file);
            Files.writeString(file, contents.replace("\"schemaVersion\":1", "\"schemaVersion\":42"));
        }

        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(world, null);
            assertTrue(worlds.manager().store().readOnly());
            assertEquals(1, worlds.manager().store().entryCount(), "Records are still readable");

            worlds.manager().recordBiome("minecraft:desert", OVERWORLD, 1, 64, 1, Fixtures.T0);
            worlds.flush();
        }

        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(world, null);
            assertTrue(worlds.manager().store().readOnly(), "The file must still be untouched");
            assertEquals(1, worlds.manager().store().entryCount());
        }
    }

    @Test
    @DisplayName("Repeated saves are coalesced and the last state wins")
    void repeatedSavesConverge(@TempDir Path dir) {
        WorldIdentity world = Fixtures.world("Coalesce");
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(world, null);
            for (int i = 0; i < 200; i++) {
                worlds.manager().recordBiome("minecraft:biome" + i, OVERWORLD, i, 64, i, Fixtures.T0 + i);
            }
            worlds.flush();

            worlds.manager().loadWorld(world, null);
            assertEquals(200, worlds.manager().store().entryCount());
        }
    }

    @Test
    @DisplayName("A corrupt world file is quarantined and the journal starts empty")
    void corruptFileIsQuarantined(@TempDir Path dir) throws Exception {
        WorldIdentity world = Fixtures.world("Corrupt");
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(world, null);
            worlds.manager().recordBiome("minecraft:plains", OVERWORLD, 0, 64, 0, Fixtures.T0);
            worlds.flush();

            Files.writeString(worlds.paths().worldFile(world), "not json at all");
        }

        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(world, null);
            assertEquals(0, worlds.manager().store().entryCount());
            try (var files = Files.list(worlds.paths().worldsDirectory())) {
                assertTrue(files.anyMatch(path -> path.getFileName().toString().contains(".corrupt-")),
                        "The unreadable file must be preserved for manual recovery");
            }
        }
    }

    @Test
    @DisplayName("Deduplication survives a save and reload cycle")
    void deduplicationSurvivesReload(@TempDir Path dir) {
        WorldIdentity world = Fixtures.world("Reload");
        try (TestWorlds first = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            first.manager().loadWorld(world, null);
            first.manager().recordBiome("minecraft:plains", OVERWORLD, 0, 64, 0, Fixtures.T0);
            first.manager().recordMilestone("biomes:5", "Discovered 5 biomes", "*", 0, 64, 0, Fixtures.T0);
            assertEquals(2, first.manager().store().entryCount());
            first.flush();
        }

        try (TestWorlds second = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            second.manager().loadWorld(world, null);
            assertEquals(2, second.manager().store().entryCount(),
                    "Reopening a world must not resurrect entries");

            DiscoveryOutcome biome = second.manager()
                    .recordBiome("minecraft:plains", OVERWORLD, 800, 64, 800, Fixtures.T0 + 60_000);
            DiscoveryOutcome milestone = second.manager()
                    .recordMilestone("biomes:5", "Discovered 5 biomes", "*", 800, 64, 800, Fixtures.T0 + 60_001);

            assertFalse(biome.created(), "A biome already in the journal must not be recorded again");
            assertFalse(milestone.created(), "A milestone already reached must not fire twice");
            assertEquals(2, second.manager().store().entryCount());
            assertEquals(2, biome.entry().visitCount());
            second.flush();
        }

        try (TestWorlds third = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            third.manager().loadWorld(world, null);
            assertEquals(2, third.manager().store().entryCount());
            third.manager().recordBiome("minecraft:plains", OVERWORLD, 0, 64, 0, Fixtures.T0 + 120_000);
            assertEquals(2, third.manager().store().entryCount(),
                    "Deduplication has to keep working across repeated sessions");
        }
    }

    @Test
    @DisplayName("The destination is cleared when its entry is deleted")
    void deletingDestinationClearsIt(@TempDir Path dir) {
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(Fixtures.world("Destination"), null);
            TraceEntry bookmark = worlds.manager().createBookmark("Base", OVERWORLD, 0, 64, 0, Fixtures.T0);
            worlds.manager().setDestination(bookmark.id());
            assertEquals(bookmark.id(), worlds.manager().destinationEntryId());

            assertTrue(worlds.manager().deleteEntry(bookmark.id()));
            assertNull(worlds.manager().destinationEntryId());
            assertTrue(worlds.manager().destination().isEmpty());
        }
    }

    @Test
    @DisplayName("Setting a destination rejects unknown ids")
    void destinationRejectsUnknownId(@TempDir Path dir) {
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(Fixtures.world("Unknown"), null);
            assertFalse(worlds.manager().setDestination("does-not-exist"));
            assertNull(worlds.manager().destinationEntryId());
        }
    }

    @Test
    @DisplayName("Deleting a discovery frees its key so it can be recorded again")
    void deletingAllowsRediscovery(@TempDir Path dir) {
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(Fixtures.world("Rediscover"), null);
            TraceEntry first = worlds.manager().recordBiome("minecraft:snowy_plains", OVERWORLD, 0, 64, 0, Fixtures.T0)
                    .entry();
            assertEquals(EntryCategory.BIOME, first.category());

            worlds.manager().deleteEntry(first.id());
            assertEquals(0, worlds.manager().store().entryCount());

            DiscoveryOutcome again = worlds.manager().recordBiome("minecraft:snowy_plains", OVERWORLD, 400, 64, 400, Fixtures.T0 + 9);
            assertTrue(again.created());
            assertEquals(400, again.entry().x());
        }
    }
}
