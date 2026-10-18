package dev.worldtrace.journal;

import dev.worldtrace.model.TraceEntry;

import java.util.Comparator;
import java.util.List;

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

    public static List<TraceEntry> sorted(List<TraceEntry> source, SortMode mode) {
        List<TraceEntry> copy = new java.util.ArrayList<>(source);
        copy.sort(comparator(mode));
        return copy;
    }

    private static String nameKey(TraceEntry entry) {
        return entry.name().toLowerCase(java.util.Locale.ROOT);
    }
}
