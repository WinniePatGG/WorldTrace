package dev.worldtrace.journal;

import dev.worldtrace.model.TraceEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JournalFilter {

    private JournalFilter() {
    }

    public static List<TraceEntry> apply(List<TraceEntry> source, JournalQuery query) {
        List<TraceEntry> result = new ArrayList<>(source.size());
        String[] tokens = tokenize(query.searchText());
        boolean needsTokens = tokens.length > 0;
        boolean hasCategoryFilter = query.hasCategoryFilter();
        boolean hasDimensionFilter = query.hasDimensionFilter();
        boolean favoritesOnly = query.favoritesOnly();
        int[] coordinateQuery = parseCoordinateQuery(query.searchText());

        for (TraceEntry entry : source) {
            if (favoritesOnly && !entry.favorite()) {
                continue;
            }
            if (hasCategoryFilter && !query.isCategorySelected(entry.category())) {
                continue;
            }
            if (hasDimensionFilter && !query.isDimensionSelected(entry.dimensionId())) {
                continue;
            }
            if (needsTokens && !matches(entry, tokens, coordinateQuery)) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    static String[] tokenize(String raw) {
        if (raw == null) {
            return new String[0];
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.toLowerCase(Locale.ROOT).split("\\s+");
    }

    private static boolean matches(TraceEntry entry, String[] tokens, int[] coordinateQuery) {

        if (coordinateQuery != null
                && entry.x() == coordinateQuery[0]
                && entry.y() == coordinateQuery[1]
                && entry.z() == coordinateQuery[2]) {
            return true;
        }
        String haystack = entry.searchableText();
        for (String token : tokens) {
            if (!haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isCoordinateQuery(String raw) {
        return parseCoordinateQuery(raw) != null;
    }

    static int[] parseCoordinateQuery(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        String[] parts = trimmed.split("[,\\s]+");
        if (parts.length != 3) {
            return null;
        }
        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            try {
                values[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return values;
    }
}
