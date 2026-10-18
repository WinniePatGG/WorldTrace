package dev.worldtrace.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JsonFileStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldTrace/Storage");

    private JsonFileStore() {
    }

    public static Optional<JsonObject> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Optional<JsonObject> parsed = parse(file);
        if (parsed.isPresent()) {
            return parsed;
        }

        Path backup = backupFile(file);
        if (!Files.isRegularFile(backup)) {
            return Optional.empty();
        }
        LOGGER.warn("World data file {} is unreadable; attempting recovery from {}", file, backup);
        Optional<JsonObject> recovered = parse(backup);
        recovered.ifPresent(json -> LOGGER.warn("Recovered {} from backup {}", file, backup));
        return recovered;
    }

    private static Optional<JsonObject> parse(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                LOGGER.warn("World data file {} is empty.", file);
                return Optional.empty();
            }
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                LOGGER.error("World data file {} does not contain a JSON object.", file);
                return Optional.empty();
            }
            return Optional.of(element.getAsJsonObject());
        } catch (IOException e) {
            LOGGER.error("Could not read world data file {}", file, e);
            return Optional.empty();
        } catch (JsonSyntaxException e) {
            LOGGER.error("World data file {} contains malformed JSON; it will be quarantined.", file, e);
            quarantine(file);
            return Optional.empty();
        }
    }

    public static void write(Path file, JsonObject root) {
        Path parent = file.getParent();
        Path temp = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String payload = root.toString();
            temp = (parent == null ? file : parent.resolve(file.getFileName() + ".tmp"));
            Files.writeString(temp, payload, StandardCharsets.UTF_8);

            if (Files.isRegularFile(file)) {
                copyToBackup(file);
            }
            move(temp, file);
            temp = null;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write world data to " + file, e);
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            LOGGER.warn("Atomic move is not supported for {}; falling back to a replacing move.", target);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyToBackup(Path file) {
        try {
            Files.copy(file, backupFile(file), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not refresh the backup copy of {}", file, e);
        }
    }

    private static void quarantine(Path file) {
        Path quarantined = file.resolveSibling(
                file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file, quarantined, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.error("Quarantined unreadable world data: {}", quarantined);
        } catch (IOException e) {
            LOGGER.error("Could not quarantine corrupt world data file {}", file, e);
        }
    }

    public static Path backupFile(Path file) {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.warn("Could not delete temporary file {}", path, e);
        }
    }
}
