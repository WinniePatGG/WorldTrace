package dev.worldtrace.discovery;

import dev.worldtrace.config.WorldTraceConfig;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.model.TracePin;
import dev.worldtrace.testsupport.Fixtures;
import dev.worldtrace.testsupport.TestWorlds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Creating, editing and deleting manual bookmarks, plus pin and event plumbing. */
class BookmarkLifecycleTest {

    private static final String OVERWORLD = "minecraft:overworld";

    private TestWorlds world(Path dir) {
        return TestWorlds.create(dir, WorldTraceConfig.defaults());
    }

    @Test
    @DisplayName("A bookmark with no name gets a readable fallback")
    void bookmarkFallbackName(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir)) {
            worlds.manager().loadWorld(Fixtures.world("Bookmarks"), null);
            TraceEntry entry = worlds.manager().createBookmark(null, OVERWORLD, 12, 70, -34, Fixtures.T0);

            assertEquals("Bookmark at 12, 70, -34", entry.name());
            assertTrue(entry.isManual());
            assertNull(entry.discoveryKey(), "Manual bookmarks never take part in deduplication");
        }
    }

    @Test
    @DisplayName("Bookmarks at the same spot are separate entries, not duplicates")
    void bookmarksAreNeverDeduplicated(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir)) {
            worlds.manager().loadWorld(Fixtures.world("Same spot"), null);
            TraceEntry first = worlds.manager().createBookmark("Camp A", OVERWORLD, 5, 64, 5, Fixtures.T0);
            TraceEntry second = worlds.manager().createBookmark("Camp B", OVERWORLD, 5, 64, 5, Fixtures.T0 + 1);

            assertFalse(first.id().equals(second.id()));
            assertEquals(2, worlds.manager().store().entryCount());
        }
    }

    @Test
    @DisplayName("Editing updates name, notes and tags but never the position")
    void editingPreservesPosition(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir)) {
            worlds.manager().loadWorld(Fixtures.world("Edit"), null);
            TraceEntry original = worlds.manager().createBookmark("Old name", OVERWORLD, 100, 64, -250, Fixtures.T0);

            Optional<TraceEntry> updated = worlds.manager().updateEntry(original.id(),
                    "  Renamed   place  ", "  Some notes  ", List.of("Home", "home", "!!!", "storage"),
                    true, "screenshots/shot.png");

            assertTrue(updated.isPresent());
            TraceEntry entry = updated.orElseThrow();
            assertEquals("Renamed place", entry.name(), "Whitespace is collapsed");
            assertEquals("Some notes", entry.notes());
            assertEquals(List.of("home", "storage"), entry.tags(),
                    "Tags are normalised, de-duplicated and keep their original order");
            assertTrue(entry.favorite());
            assertEquals(100, entry.x());
            assertEquals(-250, entry.z());
            assertEquals(OVERWORLD, entry.dimensionId());
        }
    }

    @Test
    @DisplayName("Editing an unknown id is a no-op")
    void editingUnknownId(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir)) {
            worlds.manager().loadWorld(Fixtures.world("Missing"), null);
            assertTrue(worlds.manager().updateEntry("nope", "x", "", List.of(), false, null).isEmpty());
            assertFalse(worlds.manager().deleteEntry("nope"));
            assertTrue(worlds.manager().setFavorite("nope", true).isEmpty());
        }
    }

    @Test
    @DisplayName("Favourites toggle back and forth")
    void favouriteToggle(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir)) {
            worlds.manager().loadWorld(Fixtures.world("Favourite"), null);
            TraceEntry entry = worlds.manager().createBookmark("Home", OVERWORLD, 0, 64, 0, Fixtures.T0);

            assertFalse(worlds.manager().store().entry(entry.id()).orElseThrow().favorite());
            assertTrue(worlds.manager().toggleFavorite(entry.id()));
            assertTrue(worlds.manager().store().entry(entry.id()).orElseThrow().favorite());
            assertTrue(worlds.manager().toggleFavorite(entry.id()));
            assertFalse(worlds.manager().store().entry(entry.id()).orElseThrow().favorite());
        }
    }

    @Test
    @DisplayName("Deleting a bookmark removes it from the journal")
    void deletingBookmark(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir)) {
            worlds.manager().loadWorld(Fixtures.world("Delete"), null);
            TraceEntry keep = worlds.manager().createBookmark("Keep", OVERWORLD, 1, 64, 1, Fixtures.T0);
            TraceEntry remove = worlds.manager().createBookmark("Remove", OVERWORLD, 2, 64, 2, Fixtures.T0);

            assertTrue(worlds.manager().deleteEntry(remove.id()));
            assertEquals(1, worlds.manager().store().entryCount());
            assertTrue(worlds.manager().store().entry(keep.id()).isPresent());
            assertTrue(worlds.manager().store().entry(remove.id()).isEmpty());
        }
    }

    @Test
    @DisplayName("Listeners observe creations, edits, revisits and deletions")
    void listenersSeeEveryChange(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir)) {
            worlds.manager().loadWorld(Fixtures.world("Events"), null);
            List<DiscoveryEvent.Kind> kinds = new java.util.ArrayList<>();
            worlds.manager().addListener(event -> kinds.add(event.kind()));

            TraceEntry entry = worlds.manager().createBookmark("Base", OVERWORLD, 0, 64, 0, Fixtures.T0);
            worlds.manager().toggleFavorite(entry.id());
            worlds.manager().recordBiome("minecraft:plains", OVERWORLD, 0, 64, 0, Fixtures.T0);
            worlds.manager().recordBiome("minecraft:plains", OVERWORLD, 1, 64, 1, Fixtures.T0 + 1);
            worlds.manager().deleteEntry(entry.id());

            assertEquals(List.of(
                    DiscoveryEvent.Kind.CREATED,
                    DiscoveryEvent.Kind.UPDATED,
                    DiscoveryEvent.Kind.CREATED,
                    DiscoveryEvent.Kind.REVISITED,
                    DiscoveryEvent.Kind.REMOVED), kinds);
        }
    }

    @Test
    @DisplayName("A failing listener does not break the journal")
    void listenerFailureIsIsolated(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir)) {
            worlds.manager().loadWorld(Fixtures.world("Bad listener"), null);
            List<String> seen = new java.util.ArrayList<>();
            worlds.manager().addListener(event -> {
                throw new IllegalStateException("boom");
            });
            worlds.manager().addListener(event -> seen.add(event.entry().name()));

            TraceEntry entry = worlds.manager().createBookmark("Still works", OVERWORLD, 0, 64, 0, Fixtures.T0);

            assertNotNull(entry);
            assertEquals(List.of("Still works"), seen);
        }
    }

    @Test
    @DisplayName("Pins are added, listed, resolved and removed")
    void pinLifecycle(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir)) {
            worlds.manager().loadWorld(Fixtures.world("Pins"), null);

            TracePin pin = worlds.manager().addPin(
                    TracePin.structure("Temple", "minecraft:desert_pyramid", OVERWORLD, 8, 60, 8, 48, "from a map", Fixtures.T0));
            assertEquals(1, worlds.manager().store().pinList().size());
            assertEquals(1, worlds.manager().unresolvedPins().size());
            assertEquals(dev.worldtrace.model.EntryCategory.STRUCTURE, pin.category());

            assertTrue(worlds.manager().resolvePin(pin));
            assertTrue(worlds.manager().unresolvedPins().isEmpty());

            assertTrue(worlds.manager().removePin(pin.id()));
            assertTrue(worlds.manager().store().pinList().isEmpty());
            assertFalse(worlds.manager().removePin(pin.id()));
        }
    }

    @Test
    @DisplayName("A pin's radius is clamped into a sane range")
    void pinRadiusIsClamped() {
        TracePin tooSmall = TracePin.structure("x", "minecraft:desert_pyramid", OVERWORLD, 0, 0, 0, 1, "", 1L);
        TracePin tooLarge = TracePin.structure("x", "minecraft:desert_pyramid", OVERWORLD, 0, 0, 0, 9_999, "", 1L);

        assertEquals(TracePin.MIN_RADIUS, tooSmall.radius());
        assertEquals(TracePin.MAX_RADIUS, tooLarge.radius());
    }

    @Test
    @DisplayName("A pin without a structure id is a landmark pin")
    void landmarkPinCategory() {
        TracePin pin = TracePin.landmark("My overlook", OVERWORLD, 1, 70, 2, Fixtures.T0);
        assertEquals(dev.worldtrace.model.EntryCategory.LANDMARK, pin.category());
        assertNull(pin.structureId());
    }

    @Test
    @DisplayName("Discovery events for created entries carry the stored entry")
    void createdEventCarriesEntry(@TempDir Path dir) {
        try (TestWorlds worlds = world(dir)) {
            worlds.manager().loadWorld(Fixtures.world("Payload"), null);
            java.util.List<TraceEntry> created = new java.util.ArrayList<>();
            worlds.manager().addListener(event -> {
                if (event.kind() == DiscoveryEvent.Kind.CREATED) {
                    created.add(event.entry());
                }
            });

            worlds.manager().recordBiome("minecraft:sunflower_plains", OVERWORLD, 7, 64, 7, Fixtures.T0);

            assertEquals(1, created.size());
            assertEquals("Sunflower Plains", created.get(0).name());
            assertEquals("minecraft:sunflower_plains", created.get(0).biomeId());
            assertEquals(1, created.get(0).visitCount());
        }
    }
}