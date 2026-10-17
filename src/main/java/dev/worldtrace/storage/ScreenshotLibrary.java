package dev.worldtrace.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the screenshot files an entry may reference.
 *
 * <p>WorldTrace never captures the framebuffer itself. It copies screenshots the player already took
 * with F2 out of the vanilla {@code screenshots} folder into the journal's own directory, so a
 * journal can be backed up or moved together with its images.
 *
 * <p>Files stay on the player's machine. Nothing is uploaded, ever.
 */
public final class ScreenshotLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldTrace/Screenshots");
    /** Screenshots newer than this are considered "the one the player just took". */
    private static final long RECENT_WINDOW_MILLIS = 15_000L;

    private final StoragePaths paths;

    public ScreenshotLibrary(StoragePaths paths) {
        this.paths = paths;
    }

    /** Recent screenshot files, newest first. Returns an empty list when there are none. */
    public List<Path> recent(Path gameDirectory, int limit) {
        Path screenshots = gameDirectory.resolve("screenshots");
        if (!Files.isDirectory(screenshots)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(screenshots)) {
            stream.filter(Files::isRegularFile)
                    .filter(ScreenshotLibrary::looksLikeImage)
                    .forEach(files::add);
        } catch (IOException e) {
            LOGGER.warn("Could not list the vanilla screenshots folder {}", screenshots, e);
            return List.of();
        }
        files.sort(Comparator.comparingLong(ScreenshotLibrary::lastModified).reversed());
        return files.size() <= limit ? List.copyOf(files) : List.copyOf(files.subList(0, limit));
    }

    /**
     * Copies the newest screenshot taken within the last few seconds into the journal folder.
     *
     * @return the path relative to the WorldTrace root, or {@code null} when there was nothing new
     */
    public String attachMostRecent(Path gameDirectory, WorldIdentity identity, String entryId) {
        List<Path> candidates = recent(gameDirectory, 5);
        if (candidates.isEmpty()) {
            return null;
        }
        Path newest = candidates.get(0);
        long modified = lastModified(newest);
        if (System.currentTimeMillis() - modified > RECENT_WINDOW_MILLIS) {
            return null;
        }
        try {
            Path targetDirectory = paths.screenshotsDirectory(identity);
            Files.createDirectories(targetDirectory);
            Path target = targetDirectory.resolve(entryId + "-" + sanitize(newest.getFileName().toString()));
            Files.copy(newest, target, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.debug("Attached screenshot {} to entry {}", newest.getFileName(), entryId);
            return paths.toRelativeString(target);
        } catch (IOException e) {
            LOGGER.error("Could not copy the screenshot {} into the WorldTrace folder", newest, e);
            return null;
        }
    }

    /** Absolute path of a stored reference, or {@code null} when it is missing from disk. */
    public Path resolve(String relativeReference) {
        if (relativeReference == null || relativeReference.isBlank()) {
            return null;
        }
        Path resolved = paths.resolveRelative(relativeReference);
        return Files.isRegularFile(resolved) ? resolved : null;
    }

    public boolean exists(String relativeReference) {
        return resolve(relativeReference) != null;
    }

    private static boolean looksLikeImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".png");
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static String sanitize(String fileName) {
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]+", "_");
        return Instant.now().toEpochMilli() + "_" + cleaned + ".png";
    }
}