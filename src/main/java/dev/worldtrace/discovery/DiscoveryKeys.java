package dev.worldtrace.discovery;

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

    public static boolean isAutomatic(String key) {
        return key != null && (key.startsWith("BIOME|") || key.startsWith("MILESTONE|") || key.startsWith("PIN|"));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
