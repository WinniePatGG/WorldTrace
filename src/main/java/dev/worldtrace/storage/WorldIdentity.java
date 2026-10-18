package dev.worldtrace.storage;

import dev.worldtrace.util.InputValidation;

import java.util.Locale;
import java.util.Objects;

public record WorldIdentity(String id, String displayName, Kind kind, long lastPlayedAtMillis) {

    public enum Kind {
        SINGLEPLAYER,
        MULTIPLAYER
    }

    public WorldIdentity {
        Objects.requireNonNull(id, "id");
        displayName = InputValidation.normalizeName(displayName, "Unnamed world");
        Objects.requireNonNull(kind, "kind");
    }

    public static WorldIdentity singleplayer(String levelName) {
        String raw = levelName == null || levelName.isBlank() ? "singleplayer" : levelName.trim();
        return new WorldIdentity(buildId("sp", raw), raw, Kind.SINGLEPLAYER, System.currentTimeMillis());
    }

    public static WorldIdentity multiplayer(String address) {
        String raw = address == null || address.isBlank() ? "server" : address.trim();
        return new WorldIdentity(buildId("mp", raw), raw, Kind.MULTIPLAYER, System.currentTimeMillis());
    }

    public WorldIdentity withOverride(String override) {
        if (override == null || override.isBlank()) {
            return this;
        }
        return new WorldIdentity(slug(override.trim().toLowerCase(Locale.ROOT)), displayName, kind,
                lastPlayedAtMillis);
    }

    public WorldIdentity touch(long now) {
        return new WorldIdentity(id, displayName, kind, now);
    }

    private static String buildId(String prefix, String raw) {
        String hash = Integer.toUnsignedString(raw.hashCode(), 36);
        return prefix + "-" + slug(raw.toLowerCase(Locale.ROOT)) + "-" + hash;
    }

    static String slug(String raw) {
        String slug = raw.replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-.]+|[-.]+$", "");
        if (slug.length() > 48) {
            slug = slug.substring(0, 48).replaceAll("[-.]+$", "");
        }
        if (slug.isEmpty()) {
            slug = "world";
        }
        return slug;
    }
}
