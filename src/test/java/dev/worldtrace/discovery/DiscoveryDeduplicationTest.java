package dev.worldtrace.discovery;

import dev.worldtrace.config.RevisitMode;
import dev.worldtrace.config.WorldTraceConfig;
import dev.worldtrace.model.EntryCategory;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.model.TracePin;
import dev.worldtrace.testsupport.TestWorlds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryDeduplicationTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final long T0 = 1_700_000_000_000L;

    private TestWorlds world(Path dir, RevisitMode mode) {
        return TestWorlds.create(dir, WorldTraceConfig.builder().revisitMode(mode).build());
    }

    @Test
    @DisplayName("The same biome in the same dimension is only recorded once")
    void sameBiomeIsRecordedOnce(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir, RevisitMode.COUNT_ONLY)) {
            worlds.manager().loadWorld(dev.worldtrace.testsupport.Fixtures.world("Dedupe"), null);

            DiscoveryOutcome first = worlds.manager().recordBiome("minecraft:plains", OVERWORLD, 10, 64, 10, T0);
            DiscoveryOutcome second = worlds.manager().recordBiome("minecraft:plains", OVERWORLD, 900, 64, 900, T0 + 5);

            assertTrue(first.created());
            assertFalse(second.created(), "A biome already in the journal must not create a second entry");
            assertEquals(first.entry().id(), second.entry().id());
            assertEquals(1, worlds.manager().store().entryCount());
        }
    }

    @Test
    @DisplayName("Revisits bump the visit counter and last-seen time")
    void revisitUpdatesCounter(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir, RevisitMode.COUNT_ONLY)) {
            worlds.manager().loadWorld(dev.worldtrace.testsupport.Fixtures.world("Revisit"), null);
            worlds.manager().recordBiome("minecraft:desert", OVERWORLD, 0, 64, 0, T0);

            DiscoveryOutcome revisit = worlds.manager().recordBiome("minecraft:desert", OVERWORLD, 40, 64, 40, T0 + 9_000);

            assertFalse(revisit.created());
            assertFalse(revisit.revisitAnnounced());
            assertEquals(2, revisit.entry().visitCount());
            assertEquals(T0 + 9_000, revisit.entry().lastSeenAtMillis());
            assertEquals(T0, revisit.entry().firstSeenAtMillis(), "The original discovery time must be preserved");
            assertEquals(1, worlds.manager().store().entryCount());
        }
    }

    @Test
    @DisplayName("ANNOUNCE mode still deduplicates but flags the revisit")
    void announceModeDeduplicates(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir, RevisitMode.ANNOUNCE)) {
            worlds.manager().loadWorld(dev.worldtrace.testsupport.Fixtures.world("Announce"), null);
            worlds.manager().recordBiome("minecraft:taiga", OVERWORLD, 0, 64, 0, T0);

            DiscoveryOutcome revisit = worlds.manager().recordBiome("minecraft:taiga", OVERWORLD, 5, 64, 5, T0 + 1);

            assertFalse(revisit.created());
            assertTrue(revisit.revisitAnnounced());
            assertEquals(1, worlds.manager().store().entryCount());
        }
    }

    @Test
    @DisplayName("RECORD_SEPARATE keeps every visit but never merges entries")
    void recordSeparateKeepsChronology(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir, RevisitMode.RECORD_SEPARATE)) {
            worlds.manager().loadWorld(dev.worldtrace.testsupport.Fixtures.world("Separate"), null);
            worlds.manager().recordBiome("minecraft:taiga", OVERWORLD, 0, 64, 0, T0);
            worlds.manager().recordBiome("minecraft:taiga", OVERWORLD, 0, 64, 0, T0 + 1_000);
            worlds.manager().recordBiome("minecraft:taiga", OVERWORLD, 0, 64, 0, T0 + 2_000);

            assertEquals(3, worlds.manager().store().entryCount());
        }
    }

    @Test
    @DisplayName("The same biome id in two dimensions is two separate discoveries")
    void sameBiomeAcrossDimensions(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir, RevisitMode.COUNT_ONLY)) {
            worlds.manager().loadWorld(dev.worldtrace.testsupport.Fixtures.world("Dimensions"), null);

            DiscoveryOutcome overworld = worlds.manager().recordBiome("minecraft:warped_forest", OVERWORLD, 0, 64, 0, T0);
            DiscoveryOutcome nether = worlds.manager().recordBiome("minecraft:warped_forest", NETHER, 0, 64, 0, T0);

            assertTrue(overworld.created());
            assertTrue(nether.created());
            assertNotEquals(overworld.entry().id(), nether.entry().id());
            assertEquals(2, worlds.manager().store().entryCount());
        }
    }

    @Test
    @DisplayName("Renaming an entry does not break deduplication")
    void renamingKeepsDeduplication(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir, RevisitMode.COUNT_ONLY)) {
            worlds.manager().loadWorld(dev.worldtrace.testsupport.Fixtures.world("Rename"), null);
            DiscoveryOutcome first = worlds.manager().recordBiome("minecraft:plains", OVERWORLD, 0, 64, 0, T0);
            worlds.manager().updateEntry(first.entry().id(), "Home Sweet Savanna", "", java.util.List.of(),
                    false, null);

            DiscoveryOutcome again = worlds.manager().recordBiome("minecraft:plains", OVERWORLD, 300, 64, 300, T0 + 2);

            assertFalse(again.created());
            assertEquals(1, worlds.manager().store().entryCount());
            assertEquals("Home Sweet Savanna", again.entry().name());
            assertEquals("Plains", again.entry().sourceName(), "The identifier-derived name must survive a rename");
            assertEquals("minecraft:plains", again.entry().biomeId());
        }
    }

    @Test
    @DisplayName("Two nearby pins never collapse into one structure discovery")
    void nearbyPinsStayDistinct(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir, RevisitMode.COUNT_ONLY)) {
            worlds.manager().loadWorld(dev.worldtrace.testsupport.Fixtures.world("Pins"), null);

            TracePin first = worlds.manager().addPin(
                    TracePin.structure("Temple", "minecraft:desert_pyramid", OVERWORLD, 100, 60, 100, 48, "", T0));
            TracePin second = worlds.manager().addPin(
                    TracePin.structure("Temple", "minecraft:desert_pyramid", OVERWORLD, 130, 60, 120, 48, "", T0));

            worlds.manager().recordPin(first, true, T0 + 1);
            worlds.manager().recordPin(second, true, T0 + 2);

            DiscoveryOutcome repeat = worlds.manager().recordPin(second, true, T0 + 3);

            assertFalse(repeat.created());
            assertEquals(2, worlds.manager().store().entryCount());
            assertEquals(EntryCategory.STRUCTURE, first.category());
        }
    }

    @Test
    @DisplayName("An unverified structure is recorded but explicitly not confirmed")
    void unconfirmedStructureIsMarked(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir, RevisitMode.COUNT_ONLY)) {
            worlds.manager().loadWorld(dev.worldtrace.testsupport.Fixtures.world("Unconfirmed"), null);
            TracePin pin = worlds.manager().addPin(
                    TracePin.structure("Fortress", "minecraft:fortress", NETHER, 8, 64, 8, 48, "", T0));

            DiscoveryOutcome outcome = worlds.manager().recordPin(pin, false, T0 + 5);

            assertTrue(outcome.created());
            assertFalse(outcome.entry().confirmed());
            assertEquals(EntryCategory.STRUCTURE, outcome.entry().category());
            assertTrue(outcome.entry().name().contains("unconfirmed"));
        }
    }

    @Test
    @DisplayName("Milestones are deduplicated per key and per dimension")
    void milestonesAreDeduplicated(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir, RevisitMode.COUNT_ONLY)) {
            worlds.manager().loadWorld(dev.worldtrace.testsupport.Fixtures.world("Milestones"), null);

            assertTrue(worlds.manager().recordMilestone("travel:1000", "1 km travelled", "*", 0, 64, 0, T0).created());
            assertFalse(worlds.manager().recordMilestone("travel:1000", "1 km travelled", "*", 50, 64, 50, T0 + 1).created());
            assertTrue(worlds.manager().recordMilestone("travel:5000", "5 km travelled", "*", 0, 64, 0, T0 + 2).created());

            assertTrue(worlds.manager().recordMilestone("floor:" + NETHER, "Floor", NETHER, 0, 0, 0, T0 + 3).created());
            assertTrue(worlds.manager().recordMilestone("floor:" + OVERWORLD, "Floor", OVERWORLD, 0, -64, 0, T0 + 4).created());

            assertEquals(4, worlds.manager().store().entryCount());
        }
    }

    @Test
    @DisplayName("Discovery keys are stable, readable and independent of position")
    void discoveryKeysArePositionIndependent() {
        String a = DiscoveryKeys.biome("minecraft:overworld", "minecraft:cherry_grove");
        String b = DiscoveryKeys.biome("minecraft:overworld", "minecraft:cherry_grove");
        String c = DiscoveryKeys.biome("minecraft:the_nether", "minecraft:cherry_grove");

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertTrue(DiscoveryKeys.isAutomatic(a));
        assertTrue(DiscoveryKeys.isAutomatic(DiscoveryKeys.pin("minecraft:overworld", "abc")));
        assertFalse(DiscoveryKeys.isAutomatic(null));
        assertFalse(DiscoveryKeys.isAutomatic("BOOKMARK|minecraft:overworld|x"));
    }

    @Test
    @DisplayName("Automatic discoveries stop once the configured entry limit is reached")
    void entryLimitStopsAutomaticDiscoveries(@TempDir Path dir) {
        WorldTraceConfig config = WorldTraceConfig.builder().maxEntries(100).build();
        try (TestWorlds worlds = TestWorlds.create(dir, config)) {
            worlds.manager().loadWorld(dev.worldtrace.testsupport.Fixtures.world("Limit"), null);
            for (int i = 0; i < 100; i++) {
                assertTrue(worlds.manager().recordBiome("minecraft:biome" + i, OVERWORLD, i, 64, i, T0 + i).created());
            }
            assertTrue(worlds.manager().wouldExceedEntryLimit());

            DiscoveryOutcome blocked = worlds.manager().recordBiome("minecraft:overflow", OVERWORLD, 0, 64, 0, T0);
            assertFalse(blocked.created());
            assertEquals(100, worlds.manager().store().entryCount());

            TraceEntry bookmark = worlds.manager().createBookmark("My camp", OVERWORLD, 1, 64, 1, T0);
            assertNotNull(bookmark);
            assertEquals(101, worlds.manager().store().entryCount());
        }
    }
}
