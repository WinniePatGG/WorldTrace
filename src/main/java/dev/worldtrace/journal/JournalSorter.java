package dev.worldtrace.journal;

import dev.worldtrace.model.TraceEntry;

import java.util.Comparator;
import java.util.List;

/**
 * Journal sort orders.
 *
 * <p>Every comparator ends with the entry id as a tie-breaker so the ordering is total: entries that
 * share a timestamp or a name never swap places between two sorts of the same data, which would make
 * the selection jump around while the player scrolls.
 */
public final class JournalSorter {

    public static final Comparator<TraceEntry> NEWEST =
            Comparator.comparingLong(TraceEntry::firstSeenAtMillis).reversed()
                    .thenComparing(JournalSorter::nameKey)
                    .thenComparing(TraceEntry::id);

    public static final Comparator<TraceEntry> OLDEST =
            Comparator.comparingLong(TraceEntry::firstSeenAtMillis)
                    .thenComparing(JournalSorter::nameKey)
                    .thenComparing(TraceEntry::id);

    public static final Comparator<TraceEntry> NAME =
            Comparator.comparing(JournalSorter::nameKey)
                    .thenComparingLong(TraceEntry::firstSeenAtMillis)
                    .thenComparing(TraceEntry::id);

    public static final Comparator<TraceEntry> FAVORITES =
            Comparator.comparing(TraceEntry::favorite).reversed()
                    .thenComparingLong(TraceEntry::firstSeenAtMillis)
                    .thenComparing(JournalSorter::nameKey)
                    .thenComparing(TraceEntry::id);

    private JournalSorter() {
    }

    public static Comparator<TraceEntry> comparator(SortMode mode) {
        return switch (mode) {
            case NEWEST -> NEWEST;
            case OLDEST -> OLDEST;
            case NAME -> NAME;
            case FAVORITES -> FAVORITES;
        };
    }

    /** Returns a new list; the input is left untouched. */
    public static List<TraceEntry> sorted(List<TraceEntry> source, SortMode mode) {
        List<TraceEntry> copy = new java.util.ArrayList<>(source);
        copy.sort(comparator(mode));
        return copy;
    }

    private static String nameKey(TraceEntry entry) {
        return entry.name().toLowerCase(java.util.Locale.ROOT);
    }
}