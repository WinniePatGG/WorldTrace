package dev.worldtrace.journal;

import dev.worldtrace.model.EntryCategory;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class JournalQuery {

    private static final int MAX_DIMENSION_FILTERS = 32;

    private String searchText = "";
    private final Set<EntryCategory> categories = new LinkedHashSet<>();
    private final Set<String> dimensions = new LinkedHashSet<>();
    private boolean favoritesOnly;

    public JournalQuery() {
    }

    public static JournalQuery copyOf(JournalQuery other) {
        JournalQuery copy = new JournalQuery();
        copy.searchText = other.searchText;
        copy.categories.addAll(other.categories);
        copy.dimensions.addAll(other.dimensions);
        copy.favoritesOnly = other.favoritesOnly;
        return copy;
    }

    public String searchText() {
        return searchText;
    }

    public JournalQuery setSearchText(String text) {
        this.searchText = text == null ? "" : text;
        return this;
    }

    public Set<EntryCategory> categories() {
        return Set.copyOf(categories);
    }

    public boolean hasCategoryFilter() {
        return !categories.isEmpty();
    }

    public boolean isCategorySelected(EntryCategory category) {
        return categories.contains(category);
    }

    public void toggleCategory(EntryCategory category) {
        if (categories.contains(category)) {
            categories.remove(category);
        } else if (categories.size() < EntryCategory.values().length) {
            categories.add(category);
        }
    }

    public void clearCategories() {
        categories.clear();
    }

    public Set<String> dimensions() {
        return Set.copyOf(dimensions);
    }

    public boolean hasDimensionFilter() {
        return !dimensions.isEmpty();
    }

    public boolean isDimensionSelected(String dimensionId) {
        return dimensionId != null && dimensions.contains(dimensionId);
    }

    public void toggleDimension(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return;
        }
        if (dimensions.contains(dimensionId)) {
            dimensions.remove(dimensionId);
        } else if (dimensions.size() < MAX_DIMENSION_FILTERS) {
            dimensions.add(dimensionId);
        }
    }

    public void clearDimensions() {
        dimensions.clear();
    }

    public boolean favoritesOnly() {
        return favoritesOnly;
    }

    public void setFavoritesOnly(boolean value) {
        this.favoritesOnly = value;
    }

    public boolean isFiltered() {
        return !searchText.isBlank() || favoritesOnly
                || (hasCategoryFilter() && categories.size() < EntryCategory.values().length)
                || hasDimensionFilter();
    }

    public void clearAll() {
        searchText = "";
        categories.clear();
        dimensions.clear();
        favoritesOnly = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JournalQuery other)) {
            return false;
        }
        return favoritesOnly == other.favoritesOnly
                && searchText.equalsIgnoreCase(other.searchText)
                && categories.equals(other.categories)
                && dimensions.equals(other.dimensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchText.toLowerCase(java.util.Locale.ROOT), categories, dimensions, favoritesOnly);
    }
}
