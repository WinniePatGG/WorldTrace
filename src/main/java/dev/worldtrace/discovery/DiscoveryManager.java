package dev.worldtrace.discovery;

import dev.worldtrace.config.RevisitMode;
import dev.worldtrace.config.WorldTraceConfig;
import dev.worldtrace.model.EntryCategory;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.model.TracePin;
import dev.worldtrace.storage.LoadReport;
import dev.worldtrace.storage.ScreenshotLibrary;
import dev.worldtrace.storage.StoragePaths;
import dev.worldtrace.storage.TraceStore;
import dev.worldtrace.storage.WorldData;
import dev.worldtrace.storage.WorldDataRepository;
import dev.worldtrace.storage.WorldIdentity;
import dev.worldtrace.util.InputValidation;
import dev.worldtrace.util.NameFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The journal's service layer: owns the loaded world, the deduplication index and every mutation.
 *
 * <p>Deliberately free of Minecraft types so the whole discovery pipeline — recording, revisiting,
 * editing, deleting — is testable without launching the game. The trackers in this package and the
 * screens in {@code dev.worldtrace.journal} talk to this class, never to the store directly.
 *
 * <p>All methods must be called from the game thread.
 */
public final class DiscoveryManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldTrace/Discovery");
    private static final Duration SAVE_FLUSH_TIMEOUT = Duration.ofSeconds(3);

    private final StoragePaths paths;
    private final WorldDataRepository repository;
    private final ScreenshotLibrary screenshots;
    private final java.nio.file.Path gameDirectory;
    private final TraceStore store = new TraceStore(WorldIdentity.singleplayer("unloaded"));
    private final Map<String, String> discoveryIndex = new HashMap<>();
    private final List<Consumer<DiscoveryEvent>> listeners = new CopyOnWriteArrayList<>();

    private WorldTraceConfig config = WorldTraceConfig.defaults();
    private boolean worldLoaded;
    private boolean entryLimitWarned;

    public DiscoveryManager(StoragePaths paths, WorldDataRepository repository, ScreenshotLibrary screenshots,
                            java.nio.file.Path gameDirectory) {
        this.paths = paths;
        this.repository = repository;
        this.screenshots = screenshots;
        this.gameDirectory = gameDirectory;
    }

    public StoragePaths paths() {
        return paths;
    }

    public TraceStore store() {
        return store;
    }

    public void setConfig(WorldTraceConfig config) {
        this.config = config;
    }

    public WorldTraceConfig config() {
        return config;
    }

    public boolean worldLoaded() {
        return worldLoaded;
    }

    public LoadReport loadReport() {
        return store.loadReport();
    }

    public void addListener(Consumer<DiscoveryEvent> listener) {
        listeners.add(listener);
    }

    // ------------------------------------------------------------ world scope

    /**
     * Swaps the in-memory journal to {@code identity}, loading it from disk if it exists. Called on
     * every world join so data from a previous save can never leak into the new one.
     */
    public void loadWorld(WorldIdentity identity, WorldTraceConfig effectiveConfig) {
        if (effectiveConfig != null) {
            this.config = effectiveConfig;
        }
        WorldIdentity resolved = identity.withOverride(config.worldIdOverride());
        WorldDataRepository.LoadResult result = repository.load(resolved);
        store.restore(result.data(), result.report(), result.readOnly());
        rebuildIndex();
        worldLoaded = true;
        entryLimitWarned = false;

        if (!result.report().isClean()) {
            LOGGER.warn("World '{}' journal loaded with issues: {}", resolved.displayName(),
                    result.report().summary());
        }
        if (result.readOnly()) {
            LOGGER.warn("World '{}' is open read-only; changes will not be written to disk.",
                    resolved.displayName());
        }
    }

    /** Flushes and detaches the current world. The next join loads a fresh journal. */
    public void unloadWorld() {
        if (worldLoaded && store.dirty()) {
            save();
            repository.flush(SAVE_FLUSH_TIMEOUT);
        }
        discoveryIndex.clear();
        worldLoaded = false;
    }

    @Override
    public void close() {
        unloadWorld();
    }

    private void rebuildIndex() {
        discoveryIndex.clear();
        for (TraceEntry entry : store.entries()) {
            String key = entry.discoveryKey();
            if (key == null) {
                continue;
            }
            // First writer wins: the oldest entry with a given key is the canonical one.
            String existing = discoveryIndex.putIfAbsent(key, entry.id());
            if (existing != null) {
                LOGGER.debug("Duplicate discovery key {} on entries {} and {}; keeping {}",
                        key, existing, entry.id(), existing);
            }
        }
    }

    // --------------------------------------------------------------- recording

    /**
     * Records a biome the player physically stood in.
     *
     * <p>This is the only fully automatic discovery WorldTrace makes, because it is the only one a
     * Fabric client can verify from data the server actually sends.
     */
    public DiscoveryOutcome recordBiome(String biomeId, String dimensionId, int x, int y, int z, long now) {
        if (!config.biomeDiscoveries() || biomeId == null || biomeId.isBlank()) {
            return DiscoveryOutcome.rejected();
        }
        String prettyName = NameFormatting.prettifyIdentifier(biomeId);
        return record(EntryCategory.BIOME, DiscoveryKeys.biome(dimensionId, biomeId), prettyName,
                dimensionId, x, y, z, biomeId, null, true, now);
    }

    /** Records an exploration milestone such as a distance or depth record. */
    public DiscoveryOutcome recordMilestone(String milestoneKey, String displayName, String dimensionId,
                                            int x, int y, int z, long now) {
        if (!config.milestoneDiscoveries() || milestoneKey == null || milestoneKey.isBlank()) {
            return DiscoveryOutcome.rejected();
        }
        return record(EntryCategory.MILESTONE, DiscoveryKeys.milestone(dimensionId, milestoneKey),
                InputValidation.normalizeName(displayName, "Milestone"),
                dimensionId, x, y, z, null, null, true, now);
    }

    /**
     * Turns a reached watch pin into a discovery.
     *
     * @param verified true when marker blocks for the expected structure were actually found in
     *                 loaded chunks; false means the player was in the right place but no
     *                 signature blocks were seen, which the journal states explicitly
     */
    public DiscoveryOutcome recordPin(TracePin pin, boolean verified, long now) {
        if (!config.structurePinDiscoveries() || pin == null) {
            return DiscoveryOutcome.rejected();
        }
        EntryCategory category = pin.category();
        String name = category == EntryCategory.STRUCTURE
                ? NameFormatting.prettifyIdentifier(pin.structureId()) + (verified ? "" : " (unconfirmed)")
                : InputValidation.normalizeName(pin.label(), "Landmark");
        return record(category, DiscoveryKeys.pin(pin.dimensionId(), pin.id()), name,
                pin.dimensionId(), pin.x(), pin.y(), pin.z(), null, pin.structureId(), verified, now);
    }

    /**
     * Shared entry point for every automatic discovery.
     *
     * @param confirmed whether the finding was corroborated by observable world data
     */
    private DiscoveryOutcome record(EntryCategory category, String key, String displayName,
                                    String dimensionId, int x, int y, int z, String biomeId,
                                    String structureId, boolean confirmed, long now) {
        String existingId = discoveryIndex.get(key);
        if (existingId != null) {
            Optional<TraceEntry> existing = store.entry(existingId);
            if (existing.isPresent()) {
                return handleRevisit(existing.get(), now);
            }
            // Index and store disagreed (for example an entry was removed by hand in the file);
            // fall through and recreate it so the biome is not lost.
            discoveryIndex.remove(key);
        }

        if (wouldExceedEntryLimit()) {
            // Only automatic discoveries are blocked here; manual bookmarks go through
            // createBookmark, because the player explicitly asked for that record.
            LOGGER.warn("Journal is at the configured limit of {} entries; '{}' was not recorded.",
                    config.maxEntries(), displayName);
            return DiscoveryOutcome.rejected();
        }

        TraceEntry entry = new TraceEntry(
                java.util.UUID.randomUUID().toString(),
                displayName,
                displayName,
                category,
                dimensionId,
                x,
                y,
                z,
                now,
                now,
                1,
                biomeId,
                structureId,
                "",
                List.of(),
                false,
                null,
                confirmed,
                key);

        if (!store.putEntry(entry)) {
            return DiscoveryOutcome.rejected();
        }
        discoveryIndex.put(key, entry.id());
        publish(new DiscoveryEvent(entry, DiscoveryEvent.Kind.CREATED));
        scheduleSave();
        return DiscoveryOutcome.created(entry);
    }

    private DiscoveryOutcome handleRevisit(TraceEntry existing, long now) {
        RevisitMode mode = config.revisitMode();
        if (mode == RevisitMode.RECORD_SEPARATE) {
            if (wouldExceedEntryLimit()) {
                // The cap wins: falling back to counting keeps the journal bounded.
                TraceEntry counted = existing.recordVisit(now);
                store.putEntry(counted);
                scheduleSave();
                return DiscoveryOutcome.revisited(counted, false);
            }
            // A separate entry keeps the full travel chronology, at the cost of more records.
            TraceEntry duplicate = existing.withNewIdentity(now);
            if (!store.putEntry(duplicate)) {
                return DiscoveryOutcome.rejected();
            }
            publish(new DiscoveryEvent(duplicate, DiscoveryEvent.Kind.CREATED));
            scheduleSave();
            return DiscoveryOutcome.created(duplicate);
        }

        TraceEntry updated = existing.recordVisit(now);
        store.putEntry(updated);
        publish(new DiscoveryEvent(updated, DiscoveryEvent.Kind.REVISITED));
        scheduleSave();
        return DiscoveryOutcome.revisited(updated, mode == RevisitMode.ANNOUNCE);
    }

    /**
     * Refuses new automatic discoveries once the configured cap is hit, so a long-running world can
     * never grow the journal without bound.
     */
    public boolean wouldExceedEntryLimit() {
        return store.entryCount() >= config.maxEntries();
    }

    private void warnEntryLimitOnce() {
        if (!entryLimitWarned && wouldExceedEntryLimit()) {
            entryLimitWarned = true;
            LOGGER.warn("WorldTrace reached the configured journal limit of {} entries. "
                    + "Automatic discoveries are paused; raise 'maxEntries' in worldtrace/config.json "
                    + "or delete old bookmarks to resume.", config.maxEntries());
        }
    }

    // ---------------------------------------------------------------- bookmarks

    /**
     * Creates a manual bookmark at a position. Manual entries are never deduplicated, so bookmarking
     * the same spot twice gives two bookmarks the player can name differently.
     */
    public TraceEntry createBookmark(String requestedName, String dimensionId, int x, int y, int z, long now) {
        String fallback = "Bookmark at " + x + ", " + y + ", " + z;
        String name = InputValidation.normalizeName(requestedName, fallback);
        TraceEntry entry = new TraceEntry(
                java.util.UUID.randomUUID().toString(),
                name,
                name,
                EntryCategory.BOOKMARK,
                dimensionId,
                x,
                y,
                z,
                now,
                now,
                1,
                null,
                null,
                "",
                List.of(),
                false,
                null,
                true,
                null);
        store.putEntry(entry);
        publish(new DiscoveryEvent(entry, DiscoveryEvent.Kind.CREATED));
        scheduleSave();
        return entry;
    }

    /** Applies player edits. Returns the stored entry, or empty when the id is unknown. */
    public Optional<TraceEntry> updateEntry(String id, String name, String notes, List<String> tags,
                                            boolean favorite, String screenshotRef) {
        Optional<TraceEntry> existing = store.entry(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        TraceEntry updated = existing.get()
                .withName(name)
                .withNotes(notes)
                .withTags(tags)
                .withFavorite(favorite)
                .withScreenshot(screenshotRef);
        store.putEntry(updated);
        publish(new DiscoveryEvent(updated, DiscoveryEvent.Kind.UPDATED));
        scheduleSave();
        return Optional.of(updated);
    }

    public Optional<TraceEntry> setFavorite(String id, boolean favorite) {
        Optional<TraceEntry> existing = store.entry(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        TraceEntry updated = existing.get().withFavorite(favorite);
        store.putEntry(updated);
        publish(new DiscoveryEvent(updated, DiscoveryEvent.Kind.UPDATED));
        scheduleSave();
        return Optional.of(updated);
    }

    public boolean toggleFavorite(String id) {
        return store.entry(id)
                .map(TraceEntry::toggleFavorite)
                .map(updated -> {
                    store.putEntry(updated);
                    publish(new DiscoveryEvent(updated, DiscoveryEvent.Kind.UPDATED));
                    scheduleSave();
                    return true;
                })
                .orElse(false);
    }

    public boolean attachScreenshot(String id, String relativeReference) {
        Optional<TraceEntry> existing = store.entry(id);
        if (existing.isEmpty()) {
            return false;
        }
        TraceEntry updated = existing.get().withScreenshot(relativeReference);
        store.putEntry(updated);
        scheduleSave();
        return true;
    }

    /**
     * Copies the screenshot the player just took into the journal folder and links it.
     *
     * @return the stored reference, or {@code null} when no recent screenshot existed
     */
    public String attachMostRecentScreenshot(String id) {
        Optional<TraceEntry> existing = store.entry(id);
        if (existing.isEmpty()) {
            return null;
        }
        String reference = screenshots.attachMostRecent(gameDirectory, store.identity(), id);
        if (reference != null) {
            attachScreenshot(id, reference);
        }
        return reference;
    }

    public boolean deleteEntry(String id) {
        Optional<TraceEntry> existing = store.entry(id);
        if (existing.isEmpty()) {
            return false;
        }
        store.removeEntry(id);
        String key = existing.get().discoveryKey();
        if (key != null && id.equals(discoveryIndex.get(key))) {
            discoveryIndex.remove(key);
        }
        publish(new DiscoveryEvent(existing.get(), DiscoveryEvent.Kind.REMOVED));
        scheduleSave();
        return true;
    }

    // --------------------------------------------------------------------- pins

    public TracePin addPin(TracePin pin) {
        store.putPin(pin);
        scheduleSave();
        return pin;
    }

    public boolean removePin(String id) {
        boolean removed = store.removePin(id);
        if (removed) {
            // A pin that produced a discovery keeps its entry; only the expectation is dropped.
            scheduleSave();
        }
        return removed;
    }

    public boolean resolvePin(TracePin pin) {
        boolean updated = store.putPin(pin.withResolved(true));
        if (updated) {
            scheduleSave();
        }
        return updated;
    }

    public List<TracePin> unresolvedPins() {
        List<TracePin> result = new ArrayList<>();
        for (TracePin pin : store.pins()) {
            if (!pin.resolved()) {
                result.add(pin);
            }
        }
        return result;
    }

    // ----------------------------------------------------------------- progress

    public void addDistanceTravelled(double blocks) {
        if (blocks <= 0 || Double.isNaN(blocks) || Double.isInfinite(blocks)) {
            return;
        }
        store.setTotalDistanceTravelled(store.totalDistanceTravelled() + blocks);
    }

    public double totalDistanceTravelled() {
        return store.totalDistanceTravelled();
    }

    public int biomeCount() {
        int count = 0;
        for (TraceEntry entry : store.entries()) {
            if (entry.category() == EntryCategory.BIOME) {
                count++;
            }
        }
        return count;
    }

    public int confirmedStructureCount() {
        int count = 0;
        for (TraceEntry entry : store.entries()) {
            if (entry.category() == EntryCategory.STRUCTURE && entry.confirmed()) {
                count++;
            }
        }
        return count;
    }

    // -------------------------------------------------------------- destination

    public String destinationEntryId() {
        return store.destinationEntryId();
    }

    /** Selects the active destination; {@code null} clears it. Unknown ids are rejected. */
    public boolean setDestination(String entryId) {
        if (entryId != null && store.entry(entryId).isEmpty()) {
            return false;
        }
        store.setDestinationEntryId(entryId);
        scheduleSave();
        return true;
    }

    public void clearDestination() {
        setDestination(null);
    }

    public Optional<TraceEntry> destination() {
        return store.entry(store.destinationEntryId());
    }

    // -------------------------------------------------------------- persistence

    /** Writes the current snapshot if anything changed. Cheap to call; safe to call often. */
    public void save() {
        if (!store.dirty()) {
            return;
        }
        store.clearDirty();
        repository.save(store.snapshot());
    }

    private void scheduleSave() {
        store.markDirty();
        warnEntryLimitOnce();
        save();
    }

    private void publish(DiscoveryEvent event) {
        for (Consumer<DiscoveryEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                LOGGER.error("A WorldTrace discovery listener failed for {}", event.entry().id(), e);
            }
        }
    }
}