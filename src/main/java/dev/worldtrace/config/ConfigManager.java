package dev.worldtrace.config;

import com.google.gson.JsonObject;
import dev.worldtrace.storage.JsonFileStore;
import dev.worldtrace.storage.StoragePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

public final class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldTrace/Config");

    private final StoragePaths paths;
    private volatile WorldTraceConfig config = WorldTraceConfig.defaults();

    public ConfigManager(StoragePaths paths) {
        this.paths = paths;
    }

    public WorldTraceConfig get() {
        return config;
    }

    public void load() {
        Path file = paths.configFile();
        Optional<JsonObject> json = JsonFileStore.read(file);
        if (json.isEmpty()) {
            config = WorldTraceConfig.defaults();

            LOGGER.info("No WorldTrace config found; writing defaults to {}", file);
            save();
            return;
        }
        try {
            config = WorldTraceConfig.fromJson(json.get());
            config.loadWarnings().forEach(warning -> LOGGER.warn("Config: {}", warning));
            LOGGER.info("Loaded WorldTrace config from {}", file);
        } catch (RuntimeException e) {
            LOGGER.error("Could not parse {}; using defaults for this session. The file is preserved.",
                    file, e);
            config = WorldTraceConfig.defaults();
        }
    }

    public void update(WorldTraceConfig updated) {
        config = updated;
        save();
    }

    public void save() {
        try {
            JsonFileStore.write(paths.configFile(), config.toJson());
        } catch (RuntimeException e) {
            LOGGER.error("Could not save the WorldTrace config to {}", paths.configFile(), e);
        }
    }
}
