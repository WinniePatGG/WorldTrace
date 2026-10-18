package dev.worldtrace.storage;

import java.nio.file.Path;

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

    public Path resolveRelative(String relative) {
        return root.resolve(relative).normalize();
    }

    public String toRelativeString(Path absolute) {
        return root.relativize(absolute.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
}
