package dev.worldtrace.storage;

import dev.worldtrace.util.InputValidation;

import java.util.Locale;
import java.util.Objects;

/**
 * Identifies which save the journal belongs to.
 *
 * <p>WorldTrace must never mix data between two different worlds. Vanilla does not expose the save
 * directory for integrated servers, so the identity is derived from the human readable world name
 * (singleplayer) or the server address (multiplayer) plus a short stable hash of that raw string.
 * The hash removes the collision risk of two saves sharing a display name; players who need to
 * force a distinct identity can set {@code worldIdOverride} in the config file.
 */
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

    /** Applies a player supplied override, keeping it inside the safe filename alphabet. */
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

    /**
     * Reduces arbitrary text to {@code [a-z0-9._-]} so the result is always a legal file name on
     * Windows, macOS and Linux. Runs of illegal characters collapse into a single dash.
     */
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