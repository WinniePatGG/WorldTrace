package dev.worldtrace.discovery;

import dev.worldtrace.config.WorldTraceConfig;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.model.TracePin;
import dev.worldtrace.testsupport.Fixtures;
import dev.worldtrace.testsupport.TestWorlds;
import dev.worldtrace.navigation.NavigationOverlay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Volume and timing behaviour that a real long-running survival world exercises.
 */
class DiscoveryScaleTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @AfterEach
    void clearOverlay() {
        NavigationOverlay.clear();
    }

    @Test
    @DisplayName("A large journal loads quickly and filters without stalling")
    void largeJournalStaysResponsive() {
        TestWorlds worlds = TestWorlds.create(Path.of(System.getProperty("java.io.tmpdir")),
                WorldTraceConfig.defaults());
        worlds.manager().loadWorld(Fixtures.world("Scale"), null);

        int entries = 20_000;
        for (int i = 0; i < entries; i++) {
            String dimension = i % 3 == 0 ? OVERWORLD : NETHER;
            worlds.manager().recordBiome("minecraft:biome_" + i, dimension, i, 64, -i, Fixtures.T0 + i);
        }
        assertEquals(entries, worlds.manager().store().entryCount());

        long start = System.nanoTime();
        List<TraceEntry> filtered = dev.worldtrace.journal.JournalFilter.apply(
                worlds.manager().store().entryList(), new dev.worldtrace.journal.JournalQuery());
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertEquals(entries, filtered.size());
        assertTrue(elapsedMillis < 2_000,
                "Filtering 20k entries took " + elapsedMillis + " ms, which would be visible as a hitch");
        worlds.close();
    }

    @Test
    @DisplayName("Only genuine duplicates are rejected, even under heavy repetition")
    void duplicatesAreTheOnlyRejections(@TempDir Path dir) {
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(Fixtures.world("Repetition"), null);

            List<TraceEntry> created = new ArrayList<>();
            for (int i = 0; i < 5_000; i++) {
                // The same biome, repeated over and over, from many different positions.
                var outcome = worlds.manager().recordBiome("minecraft:plains", OVERWORLD, i, 64, i, Fixtures.T0 + i);
                if (outcome.created()) {
                    created.add(outcome.entry());
                }
            }

            assertEquals(1, created.size(), "Five thousand sightings of one biome is one discovery");
            assertEquals(1, worlds.manager().store().entryCount());
            TraceEntry entry = worlds.manager().store().entries().iterator().next();
            assertEquals(5_000, entry.visitCount(), "Every sighting is still counted as a visit");
        }
    }

    @Test
    @DisplayName("The background writer coalesces a burst of saves into a consistent file")
    void burstOfSavesIsCoalesced(@TempDir Path dir) throws Exception {
        WorldIdentityHolder holder = new WorldIdentityHolder();
        CountDownLatch done = new CountDownLatch(1);

        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(holder.identity(), null);
            worlds.manager().recordBiome("minecraft:plains", OVERWORLD, 0, 64, 0, Fixtures.T0);

            Thread writer = new Thread(() -> {
                for (int i = 0; i < 500; i++) {
                    worlds.manager().createBookmark("Bookmark " + i, OVERWORLD, i, 64, i, Fixtures.T0 + i);
                }
                done.countDown();
            });
            writer.start();

            assertTrue(done.await(30, TimeUnit.SECONDS), "Recording must not block on disk");
            worlds.flush();
        }

        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(holder.identity(), null);
            assertEquals(501, worlds.manager().store().entryCount(),
                    "Every queued change must eventually reach disk");
        }
    }

    @Test
    @DisplayName("Distance accumulates and drives milestones exactly once each")
    void distanceAccumulates(@TempDir Path dir) {
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(Fixtures.world("Distance"), null);
            var milestones = new MilestoneTracker(worlds.manager());

            for (int step = 0; step < 1_000; step++) {
                worlds.manager().addDistanceTravelled(10);
                milestones.onDistanceMoved(new net.minecraft.util.math.BlockPos(step * 10, 64, 0), Fixtures.T0);
            }
            assertEquals(10_000.0, worlds.manager().totalDistanceTravelled(), 0.001);

            long travelMilestones = worlds.manager().store().entryList().stream()
                    .filter(e -> e.name().contains("travelled"))
                    .count();
            assertEquals(3, travelMilestones, "1 km, 5 km and 10 km thresholds should have fired once each");

            // Feeding the same total again must not replay milestones.
            for (int step = 0; step < 1_000; step++) {
                worlds.manager().addDistanceTravelled(0);
                milestones.onDistanceMoved(new net.minecraft.util.math.BlockPos(0, 64, 0), Fixtures.T0);
            }
            assertEquals(10_000.0, worlds.manager().totalDistanceTravelled(), 0.001);
            assertEquals(3, worlds.manager().store().entryList().stream()
                    .filter(e -> e.name().contains("travelled")).count());
        }
    }

    @Test
    @DisplayName("Coverage milestones fire once each as the journal fills")
    void coverageMilestonesFireOnce(@TempDir Path dir) {
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(Fixtures.world("Coverage"), null);
            var milestones = new MilestoneTracker(worlds.manager());
            var position = new net.minecraft.util.math.BlockPos(1, 64, 1);

            for (int i = 0; i < 6; i++) {
                worlds.manager().recordBiome("minecraft:biome_" + i, OVERWORLD, i, 64, i, Fixtures.T0 + i);
                milestones.onCoverageSample(position, Fixtures.T0 + i);
            }
            // Running the sampler again with the same counts must not add anything.
            milestones.onCoverageSample(position, Fixtures.T0 + 100);

            long coverage = worlds.manager().store().entryList().stream()
                    .filter(e -> e.name().startsWith("Discovered"))
                    .count();
            assertEquals(1, coverage, "Only the 5-biome threshold was crossed");
        }
    }

    @Test
    @DisplayName("Pins never grow without bound and resolved pins are skipped")
    void pinsAreBounded(@TempDir Path dir) {
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(Fixtures.world("Many pins"), null);

            for (int i = 0; i < 500; i++) {
                worlds.manager().addPin(
                        TracePin.structure("Pin " + i, "minecraft:desert_pyramid", OVERWORLD, i * 100, 60, 0, 48, "", Fixtures.T0));
            }
            assertEquals(500, worlds.manager().unresolvedPins().size());

            TracePin first = worlds.manager().store().pinList().get(0);
            assertTrue(worlds.manager().resolvePin(first));
            assertEquals(499, worlds.manager().unresolvedPins().size());
        }
    }

    @Test
    @DisplayName("A world with no discoveries still persists correctly")
    void emptyWorldRoundTrip(@TempDir Path dir) {
        dev.worldtrace.storage.WorldIdentity world = Fixtures.world("Empty");
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(world, null);
            assertEquals(0, worlds.manager().store().entryCount());
            worlds.flush();
        }
        try (TestWorlds worlds = TestWorlds.create(dir, WorldTraceConfig.defaults())) {
            worlds.manager().loadWorld(world, null);
            assertEquals(0, worlds.manager().store().entryCount());
            assertNotNull(worlds.manager().store().identity());
            assertFalse(worlds.manager().store().readOnly());
        }
    }

    private static final class WorldIdentityHolder {
        private final dev.worldtrace.storage.WorldIdentity identity = Fixtures.world("Burst");

        dev.worldtrace.storage.WorldIdentity identity() {
            return identity;
        }
    }
}