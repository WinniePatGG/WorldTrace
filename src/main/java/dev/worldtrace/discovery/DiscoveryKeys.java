package dev.worldtrace.discovery;

/**
 * Builds the canonical deduplication keys stored on every automatically discovered entry.
 *
 * <p>The key answers exactly one question: "is this the same thing as something already in the
 * journal?". It deliberately does <em>not</em> include a position component. Two structures that a
 * player pin twenty blocks apart are distinct pins and therefore distinct keys, and two visits to the
 * same biome are the same biome wherever in the world they happen. Position-based clustering would
 * merge unrelated structures and split single discoveries, which is precisely the failure mode this
 * design avoids.
 *
 * <p>The key is persisted with the entry, so deduplication keeps working across sessions even after
 * the player renames the entry.
 */
public final class DiscoveryKeys {

    private DiscoveryKeys() {
    }

    public static String biome(String dimensionId, String biomeId) {
        return "BIOME|" + normalize(dimensionId) + "|" + normalize(biomeId);
    }

    public static String milestone(String dimensionId, String milestoneKey) {
        return "MILESTONE|" + normalize(dimensionId) + "|" + normalize(milestoneKey);
    }

    public static String pin(String dimensionId, String pinId) {
        return "PIN|" + normalize(dimensionId) + "|" + normalize(pinId);
    }

    /** True for keys that only WorldTrace produces; manual bookmarks have none. */
    public static boolean isAutomatic(String key) {
        return key != null && (key.startsWith("BIOME|") || key.startsWith("MILESTONE|") || key.startsWith("PIN|"));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}