package dev.worldtrace.storage;

import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.model.TracePin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mutable, single-world repository holding the journal in memory.
 *
 * <p>All access happens on the game thread; {@link #snapshot()} is the only method that hands data
 * to another thread and it returns a fully immutable copy. Keeping the mutation API this small makes
 * it obvious where saves are triggered, and keeps {@link #dirty} meaningful.
 */
public final class TraceStore {

    private final Map<String, TraceEntry> entries = new LinkedHashMap<>();
    private final Map<String, TracePin> pins = new LinkedHashMap<>();

    private WorldIdentity identity;
    private LoadReport loadReport = LoadReport.empty();
    private String destinationEntryId;
    private double totalDistanceTravelled;
    private boolean dirty;
    private boolean readOnly;

    public TraceStore(WorldIdentity identity) {
        this.identity = identity;
    }

    /** Replaces the whole content, e.g. after loading a world file. */
    public void restore(WorldData data, LoadReport report, boolean readOnly) {
        this.identity = data.identity();
        entries.clear();
        pins.clear();
        for (TraceEntry entry : data.entries()) {
            entries.put(entry.id(), entry);
        }
        for (TracePin pin : data.pins()) {
            pins.put(pin.id(), pin);
        }
        this.destinationEntryId = data.destinationEntryId();
        this.totalDistanceTravelled = data.totalDistanceTravelled();
        this.loadReport = report;
        this.readOnly = readOnly;
        this.dirty = false;
    }

    public void clearForNewWorld(WorldIdentity newIdentity, LoadReport report) {
        entries.clear();
        pins.clear();
        identity = newIdentity;
        destinationEntryId = null;
        totalDistanceTravelled = 0;
        loadReport = report;
        readOnly = false;
        dirty = false;
    }

    // ---------------------------------------------------------------- entries

    public Optional<TraceEntry> entry(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(entries.get(id));
    }

    public Collection<TraceEntry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public List<TraceEntry> entryList() {
        return new ArrayList<>(entries.values());
    }

    public int entryCount() {
        return entries.size();
    }

    /**
     * Inserts or replaces an entry by id and marks the journal dirty.
     *
     * @return true when the journal changed
     */
    public boolean putEntry(TraceEntry entry) {
        TraceEntry previous = entries.put(entry.id(), entry);
        markDirty();
        return previous == null || !previous.equals(entry);
    }

    public boolean removeEntry(String id) {
        boolean removed = entries.remove(id) != null;
        if (removed && id.equals(destinationEntryId)) {
            destinationEntryId = null;
        }
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public void replaceAllEntries(Collection<TraceEntry> replacement) {
        entries.clear();
        for (TraceEntry entry : replacement) {
            entries.put(entry.id(), entry);
        }
        markDirty();
    }

    // ------------------------------------------------------------------- pins

    public Optional<TracePin> pin(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(pins.get(id));
    }

    public Collection<TracePin> pins() {
        return Collections.unmodifiableCollection(pins.values());
    }

    public List<TracePin> pinList() {
        return new ArrayList<>(pins.values());
    }

    public boolean putPin(TracePin pin) {
        TracePin previous = pins.put(pin.id(), pin);
        markDirty();
        return previous == null || !previous.equals(pin);
    }

    public boolean removePin(String id) {
        boolean removed = pins.remove(id) != null;
        if (removed) {
            markDirty();
        }
        return removed;
    }

    // ------------------------------------------------------------ world state

    public WorldIdentity identity() {
        return identity;
    }

    public LoadReport loadReport() {
        return loadReport;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public String destinationEntryId() {
        return destinationEntryId;
    }

    public void setDestinationEntryId(String id) {
        if (java.util.Objects.equals(this.destinationEntryId, id)) {
            return;
        }
        this.destinationEntryId = id;
        markDirty();
    }

    public double totalDistanceTravelled() {
        return totalDistanceTravelled;
    }

    public void setTotalDistanceTravelled(double value) {
        double sanitized = Double.isNaN(value) || value < 0 ? 0 : value;
        if (sanitized != this.totalDistanceTravelled) {
            this.totalDistanceTravelled = sanitized;
            markDirty();
        }
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    /** Immutable copy handed to the storage thread. */
    public WorldData snapshot() {
        return new WorldData(identity, new ArrayList<>(entries.values()), new ArrayList<>(pins.values()),
                destinationEntryId, totalDistanceTravelled);
    }
}