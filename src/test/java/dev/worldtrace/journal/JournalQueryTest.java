package dev.worldtrace.journal;

import dev.worldtrace.model.EntryCategory;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalQueryTest {

    private List<TraceEntry> entries;

    @BeforeEach
    void setUp() {
        entries = new ArrayList<>();
        entries.add(Fixtures.biome("minecraft:plains", "minecraft:overworld", 10, 20, Fixtures.T0));
        entries.add(Fixtures.biome("minecraft:cherry_grove", "minecraft:overworld", 300, -80, Fixtures.T0 + 1_000));
        entries.add(Fixtures.biome("minecraft:crimson_forest", "minecraft:the_nether", -400, 900, Fixtures.T0 + 2_000));
        entries.add(Fixtures.biome("minecraft:snowy_taiga", "minecraft:overworld", 55, 55, Fixtures.T0 + 3_000));

        TraceEntry bookmark = Fixtures.bookmark("b1", "Hidden Base", Fixtures.T0 + 4_000)
                .withNotes("Chest of diamonds, two shulkers")
                .withTags(List.of("base", "storage"))
                .withFavorite(true);
        entries.add(bookmark);

        TraceEntry second = Fixtures.bookmark("b2", "Ancient Ruin", Fixtures.T0 + 5_000)
                .withTags(List.of("ruins"));
        entries.add(second);
    }

    @Test
    @DisplayName("An empty query returns everything")
    void emptyQueryReturnsAll() {
        JournalQuery query = new JournalQuery();
        assertEquals(entries.size(), JournalFilter.apply(entries, query).size());
        assertFalse(query.isFiltered());
    }

    @Test
    @DisplayName("Search matches names case-insensitively")
    void searchMatchesName() {
        JournalQuery query = new JournalQuery().setSearchText("cherry");
        List<TraceEntry> result = JournalFilter.apply(entries, query);
        assertEquals(1, result.size());
        assertEquals("Cherry Grove", result.get(0).name());
    }

    @Test
    @DisplayName("Search matches notes and tags as well as names")
    void searchMatchesNotesAndTags() {
        assertEquals(1, JournalFilter.apply(entries, new JournalQuery().setSearchText("shulkers")).size());
        assertEquals(1, JournalFilter.apply(entries, new JournalQuery().setSearchText("ruins")).size());
    }

    @Test
    @DisplayName("Multiple search tokens narrow the result (AND)")
    void multipleTokensAreAnded() {
        assertEquals(1, JournalFilter.apply(entries, new JournalQuery().setSearchText("base storage")).size());
        assertEquals(0, JournalFilter.apply(entries, new JournalQuery().setSearchText("base nether")).size());
    }

    @Test
    @DisplayName("An exact coordinate triplet finds the entry")
    void coordinateSearch() {
        List<TraceEntry> result = JournalFilter.apply(entries, new JournalQuery().setSearchText("-400 64 900"));
        assertEquals(1, result.size());
        assertEquals("Crimson Forest", result.get(0).name());
        assertTrue(JournalFilter.isCoordinateQuery("10, 64, -20"));
        assertFalse(JournalFilter.isCoordinateQuery("cherry grove"));
    }

    @Test
    @DisplayName("Category filter keeps only the selected categories")
    void categoryFilter() {
        JournalQuery query = new JournalQuery();
        query.toggleCategory(EntryCategory.BOOKMARK);
        List<TraceEntry> result = JournalFilter.apply(entries, query);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(e -> e.category() == EntryCategory.BOOKMARK));

        query.toggleCategory(EntryCategory.BIOME);
        assertEquals(6, JournalFilter.apply(entries, query).size());
    }

    @Test
    @DisplayName("Dimension filter works independently of the search text")
    void dimensionFilterIsIndependent() {
        JournalQuery query = new JournalQuery().setSearchText("base");
        query.toggleDimension("minecraft:the_nether");

        assertEquals(0, JournalFilter.apply(entries, query).size());

        query.toggleDimension("minecraft:the_nether");
        assertEquals(1, JournalFilter.apply(entries, query).size(),
                "Clearing the dimension filter must not clear the search box");
    }

    @Test
    @DisplayName("Favorites-only combined with a search")
    void favoritesCombinedWithSearch() {
        JournalQuery query = new JournalQuery().setSearchText("base");
        query.setFavoritesOnly(true);
        assertEquals(1, JournalFilter.apply(entries, query).size());

        query.setSearchText("ruin");
        assertEquals(0, JournalFilter.apply(entries, query).size(),
                "The favorites filter must not be reset by changing the search text");
    }

    @Test
    @DisplayName("All filters combined")
    void allFiltersCombined() {
        JournalQuery query = new JournalQuery().setSearchText("e");
        query.toggleCategory(EntryCategory.BIOME);
        query.toggleDimension("minecraft:overworld");
        query.setFavoritesOnly(false);

        List<TraceEntry> result = JournalFilter.apply(entries, query);
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(e -> e.dimensionId().equals("minecraft:overworld")));
        assertTrue(result.stream().allMatch(e -> e.category() == EntryCategory.BIOME));
    }

    @Test
    @DisplayName("Newest and oldest are exact reverses of each other")
    void newestAndOldest() {
        List<TraceEntry> newest = JournalSorter.sorted(entries, SortMode.NEWEST);
        List<TraceEntry> oldest = JournalSorter.sorted(entries, SortMode.OLDEST);

        assertEquals("Ancient Ruin", newest.get(0).name());
        assertEquals("Plains", oldest.get(0).name());
        assertEquals(newest.size(), oldest.size());
        for (int i = 0; i < newest.size(); i++) {
            assertEquals(newest.get(i).id(), oldest.get(oldest.size() - 1 - i).id());
        }
    }

    @Test
    @DisplayName("Alphabetical sort is case-insensitive")
    void alphabeticalSort() {
        List<TraceEntry> sorted = JournalSorter.sorted(entries, SortMode.NAME);
        String previous = "";
        for (TraceEntry entry : sorted) {
            String name = entry.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(previous.compareTo(name) <= 0, "'" + previous + "' should not come after '" + name + "'");
            previous = name;
        }
        assertEquals("Ancient Ruin", sorted.get(0).name());
    }

    @Test
    @DisplayName("Favorites sort floats favorites to the top, newest first within each group")
    void favoritesFirst() {
        entries.add(Fixtures.bookmark("b3", "Favorite Two", Fixtures.T0).withFavorite(true));
        List<TraceEntry> sorted = JournalSorter.sorted(entries, SortMode.FAVORITES);

        assertTrue(sorted.get(0).favorite());
        assertTrue(sorted.get(1).favorite());
        assertFalse(sorted.get(2).favorite());
    }

    @Test
    @DisplayName("Sorting is stable for entries sharing a timestamp")
    void sortingIsTotal() {
        List<TraceEntry> sameTime = List.of(
                Fixtures.bookmark("z", "Zebra", Fixtures.T0),
                Fixtures.bookmark("a", "Apple", Fixtures.T0));

        List<TraceEntry> first = JournalSorter.sorted(sameTime, SortMode.NEWEST);
        List<TraceEntry> second = JournalSorter.sorted(sameTime, SortMode.NEWEST);
        assertEquals(first.stream().map(TraceEntry::id).toList(), second.stream().map(TraceEntry::id).toList());
    }

    @Test
    @DisplayName("Sorting never mutates the source list")
    void sortingDoesNotMutateInput() {
        List<TraceEntry> source = new ArrayList<>(entries);
        List<String> before = source.stream().map(TraceEntry::id).toList();
        JournalSorter.sorted(source, SortMode.NAME);
        assertEquals(before, source.stream().map(TraceEntry::id).toList());
    }

    @Test
    @DisplayName("A no-result search returns an empty list rather than everything")
    void noResultsIsEmpty() {
        JournalQuery query = new JournalQuery().setSearchText("definitely-not-present");
        assertTrue(JournalFilter.apply(entries, query).isEmpty());
        assertTrue(query.isFiltered());
    }

    @Test
    @DisplayName("Query equality reflects the active filters")
    void queryEquality() {
        JournalQuery a = new JournalQuery().setSearchText("Base");
        JournalQuery b = new JournalQuery().setSearchText("base");
        assertEquals(a, b);

        b.toggleCategory(EntryCategory.BIOME);
        assertFalse(a.equals(b));
    }

    @Test
    @DisplayName("SortMode cycles through every option and round-trips")
    void sortModeCycling() {
        SortMode mode = SortMode.NEWEST;
        int count = 0;
        do {
            mode = mode.next();
            count++;
        } while (mode != SortMode.NEWEST && count < 10);
        assertEquals(4, count);
        assertEquals(SortMode.NEWEST, SortMode.fromString("newest", SortMode.NAME));
        assertEquals(SortMode.NAME, SortMode.fromString("not-a-mode", SortMode.NAME));
    }
}
