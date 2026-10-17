package dev.worldtrace.storage;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns all world-file I/O.
 *
 * <p>Loading happens on the game thread because it must complete before the journal can be opened,
 * but a world file with a few thousand entries parses in single-digit milliseconds. Writing is
 * always handed to a dedicated daemon thread: the immutable snapshot is taken on the game thread
 * (cheap — it only copies references) and serialised plus written off-thread.
 *
 * <p>Saves are coalesced. Calling {@link #save} repeatedly — for example once per discovery while
 * walking — results in at most one pending write; if the journal changes while a write is in
 * flight, exactly one follow-up write follows it. The render thread therefore never waits on disk.
 */
public final class WorldDataRepository implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldTrace/Storage");
    private static final Duration FLUSH_TIMEOUT = Duration.ofSeconds(5);

    private final StoragePaths paths;
    private final ExecutorService writer;
    private final AtomicReference<WorldData> pending = new AtomicReference<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);

    private volatile boolean readOnly;
    private volatile boolean shutdown;

    public WorldDataRepository(StoragePaths paths) {
        this.paths = paths;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "WorldTrace-Storage");
            thread.setDaemon(true);
            return thread;
        };
        this.writer = Executors.newSingleThreadExecutor(factory);
    }

    /**
     * Modification time of {@code file}, or the current time when it cannot be determined. Used as
     * the fallback timestamp for records whose own timestamp is missing from a hand-edited file.
     */
    private static long readLastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            LOGGER.debug("Could not read the modification time of {}", file, e);
            return System.currentTimeMillis();
        }
    }

    /**
     * Reads the journal for {@code identity}, falling back to an empty journal when the file is
     * absent. Corrupt files are quarantined by {@link JsonFileStore} and recovered from the
     * {@code .bak} copy when possible.
     */
    public LoadResult load(WorldIdentity identity) {
        Path file = paths.worldFile(identity);
        Optional<JsonObject> json = JsonFileStore.read(file);
        if (json.isEmpty()) {
            return new LoadResult(WorldData.empty(identity), LoadReport.empty(), false);
        }
        long fallbackTimestamp = readLastModified(file);
        try {
            TraceCodec.DecodeResult decoded = TraceCodec.decode(json.get(), identity, fallbackTimestamp);
            LoadReport report = decoded.report();
            report.warnings().forEach(warning -> LOGGER.warn("World data ({}): {}", identity.id(), warning));
            if (report.migratedFromSchema() != LoadReport.NO_MIGRATION) {
                LOGGER.info("Migrated world data for '{}' from schema {} to {}",
                        identity.displayName(), report.migratedFromSchema(), TraceCodec.SCHEMA_VERSION);
            }
            LOGGER.info("Loaded {} entries for world '{}' from {}",
                    report.entriesLoaded(), identity.displayName(), file);
            readOnly = report.readOnly();
            return new LoadResult(decoded.data(), report, report.readOnly());
        } catch (RuntimeException e) {
            LOGGER.error("World data file {} could not be decoded; starting with an empty journal. "
                    + "The file is preserved and can be restored manually.", file, e);
            readOnly = true;
            return new LoadResult(WorldData.empty(identity),
                    LoadReport.builder().warn("Decoding failed: " + e).build(), true);
        }
    }

    /**
     * Records {@code snapshot} as the newest state and schedules a coalesced write. Safe to call on
     * the game thread at any frequency; it never blocks on disk.
     */
    public void save(WorldData snapshot) {
        if (shutdown) {
            return;
        }
        pending.set(snapshot);
        dirty.set(true);
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            writer.execute(this::drain);
        } catch (RuntimeException e) {
            drainScheduled.set(false);
            LOGGER.error("Could not schedule a WorldTrace save", e);
        }
    }

    /** Runs on the storage thread. Drains the dirty flag until nothing newer shows up. */
    private void drain() {
        try {
            while (dirty.get()) {
                dirty.set(false);
                WorldData snapshot = pending.get();
                if (snapshot != null) {
                    writeNow(snapshot);
                }
            }
        } finally {
            drainScheduled.set(false);
            if (dirty.get()) {
                // A change arrived between the loop exit and the flag reset; run again.
                scheduleDrain();
            }
        }
    }

    private void writeNow(WorldData snapshot) {
        if (readOnly) {
            LOGGER.warn("Skipping save for '{}': the file was written by a newer WorldTrace version.",
                    snapshot.identity().displayName());
            return;
        }
        Path file = paths.worldFile(snapshot.identity());
        try {
            JsonObject json = TraceCodec.encode(snapshot);
            JsonFileStore.write(file, json);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to save world data for '{}' to {}. The journal is still intact in "
                    + "memory and will be retried on the next change or on shutdown.",
                    snapshot.identity().displayName(), file, e);
        }
    }

    /**
     * Waits for queued writes to finish. Used when leaving a world so the file on disk is current.
     * Bounded, so a stuck disk can never hang the client.
     *
     * @return true when the queue drained before the timeout
     */
    public boolean flush(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!dirty.get() && !drainScheduled.get()) {
                return true;
            }
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        LOGGER.warn("Timed out after {} while flushing WorldTrace data; some changes may not be on "
                + "disk yet.", timeout);
        return false;
    }

    @Override
    public void close() {
        flush(FLUSH_TIMEOUT);
        shutdown = true;
        writer.shutdown();
        try {
            if (!writer.awaitTermination(2, TimeUnit.SECONDS)) {
                LOGGER.warn("WorldTrace storage thread did not terminate cleanly.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Everything the caller needs to explain the state of a freshly loaded journal. */
    public record LoadResult(WorldData data, LoadReport report, boolean readOnly) {
    }
}