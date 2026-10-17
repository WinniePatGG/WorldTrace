package dev.worldtrace.testsupport;

import dev.worldtrace.config.WorldTraceConfig;
import dev.worldtrace.discovery.DiscoveryManager;
import dev.worldtrace.storage.ScreenshotLibrary;
import dev.worldtrace.storage.StoragePaths;
import dev.worldtrace.storage.WorldDataRepository;
import dev.worldtrace.storage.WorldIdentity;

import java.nio.file.Path;

/**
 * Builds a fully wired {@link DiscoveryManager} backed by a temporary directory, so tests exercise
 * the real storage stack instead of a mock.
 */
public final class TestWorlds implements AutoCloseable {

    private final Path gameDirectory;
    private final WorldDataRepository repository;
    private final DiscoveryManager manager;

    private TestWorlds(Path gameDirectory, WorldDataRepository repository, DiscoveryManager manager) {
        this.gameDirectory = gameDirectory;
        this.repository = repository;
        this.manager = manager;
    }

    public static TestWorlds create(Path gameDirectory, WorldTraceConfig config) {
        StoragePaths paths = new StoragePaths(gameDirectory);
        WorldDataRepository repository = new WorldDataRepository(paths);
        ScreenshotLibrary screenshots = new ScreenshotLibrary(paths);
        DiscoveryManager manager =
                new DiscoveryManager(paths, repository, screenshots, gameDirectory);
        manager.setConfig(config);
        return new TestWorlds(gameDirectory, repository, manager);
    }

    public DiscoveryManager manager() {
        return manager;
    }

    public WorldDataRepository repository() {
        return repository;
    }

    public StoragePaths paths() {
        return new StoragePaths(gameDirectory);
    }

    /** Flushes pending writes and waits for them, mimicking leaving a world. */
    public void flush() {
        manager.save();
        repository.flush(java.time.Duration.ofSeconds(5));
    }

    @Override
    public void close() {
        flush();
        repository.close();
    }
}