package dev.worldtrace.storage;

import java.nio.file.Path;

/**
 * Resolves every file WorldTrace is allowed to touch.
 *
 * <p>Layout inside the game directory:
 * <pre>
 *   worldtrace/
 *     config.json                 mod-wide preferences
 *     worlds/&lt;world-id&gt;.json      one journal per save or server
 *     worlds/&lt;world-id&gt;.json.bak  previous good copy, used for recovery
 *     screenshots/&lt;world-id&gt;/      copied screenshots referenced by entries
 * </pre>
 *
 * <p>Nothing outside the game directory is ever read or written, and nothing is ever uploaded.
 */
public final class StoragePaths {

    private final Path root;

    public StoragePaths(Path gameDirectory) {
        this.root = gameDirectory.resolve("worldtrace").toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path configFile() {
        return root.resolve("config.json");
    }

    public Path worldFile(WorldIdentity identity) {
        return worldsDirectory().resolve(identity.id() + ".json");
    }

    public Path worldsDirectory() {
        return root.resolve("worlds");
    }

    public Path screenshotsDirectory(WorldIdentity identity) {
        return root.resolve("screenshots").resolve(identity.id());
    }

    /**
     * Screenshots referenced by entries are stored relative to the WorldTrace root so a journal can
     * be moved between installations without rewriting absolute paths.
     */
    public Path resolveRelative(String relative) {
        return root.resolve(relative).normalize();
    }

    public String toRelativeString(Path absolute) {
        return root.relativize(absolute.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
}