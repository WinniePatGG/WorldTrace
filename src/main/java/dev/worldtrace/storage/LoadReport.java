package dev.worldtrace.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record LoadReport(
        int entriesLoaded,
        int entriesSkipped,
        int pinsLoaded,
        int pinsSkipped,
        int migratedFromSchema,
        boolean readOnly,
        List<String> warnings) {

    public static final int NO_MIGRATION = -1;

    public LoadReport {
        warnings = List.copyOf(warnings);
    }

    public static LoadReport empty() {
        return new LoadReport(0, 0, 0, 0, NO_MIGRATION, false, List.of());
    }

    public boolean isClean() {
        return entriesSkipped == 0 && pinsSkipped == 0 && warnings.isEmpty();
    }

    public String summary() {
        if (isClean()) {
            return entriesLoaded + " entries loaded";
        }
        return entriesLoaded + " entries loaded, " + entriesSkipped + " skipped ("
                + pinsLoaded + " pins, " + pinsSkipped + " skipped)";
    }

    public LoadReport withWarning(String warning) {
        List<String> updated = new ArrayList<>(warnings);
        updated.add(warning);
        return new LoadReport(entriesLoaded, entriesSkipped, pinsLoaded, pinsSkipped,
                migratedFromSchema, readOnly, updated);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int entriesLoaded;
        private int entriesSkipped;
        private int pinsLoaded;
        private int pinsSkipped;
        private int migratedFromSchema = NO_MIGRATION;
        private boolean readOnly;
        private final List<String> warnings = new ArrayList<>();

        public Builder entriesLoaded(int value) {
            this.entriesLoaded = value;
            return this;
        }

        public Builder entryLoaded() {
            this.entriesLoaded++;
            return this;
        }

        public Builder entrySkipped() {
            this.entriesSkipped++;
            return this;
        }

        public Builder pinLoaded() {
            this.pinsLoaded++;
            return this;
        }

        public Builder pinSkipped() {
            this.pinsSkipped++;
            return this;
        }

        public Builder migratedFrom(int schema) {
            this.migratedFromSchema = schema;
            return this;
        }

        public Builder readOnly(boolean value) {
            this.readOnly = value;
            return this;
        }

        public Builder warn(String warning) {
            warnings.add(warning);
            return this;
        }

        public int warningsSize() {
            return warnings.size();
        }

        public LoadReport build() {
            return new LoadReport(entriesLoaded, entriesSkipped, pinsLoaded, pinsSkipped,
                    migratedFromSchema, readOnly, Collections.unmodifiableList(new ArrayList<>(warnings)));
        }
    }
}
