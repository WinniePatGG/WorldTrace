package dev.worldtrace.testsupport;

import dev.worldtrace.model.EntryCategory;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.storage.WorldIdentity;

import java.util.UUID;

/**
 * Deterministic fixtures for the test suite.
 *
 * <p>Timestamps are fixed rather than read from the clock so ordering assertions never become flaky,
 * and ids are supplied explicitly so a failing test points at a specific record.
 */
public final class Fixtures {

    public static final long T0 = 1_700_000_000_000L;

    private Fixtures() {
    }

    public static WorldIdentity world(String name) {
        return WorldIdentity.singleplayer(name);
    }

    public static TraceEntry biome(String biomeId, String dimension, int x, int z, long foundAt) {
        return new TraceEntry(
                UUID.nameUUIDFromBytes(("biome:" + biomeId).getBytes()).toString(),
                dev.worldtrace.util.NameFormatting.prettifyIdentifier(biomeId),
                dev.worldtrace.util.NameFormatting.prettifyIdentifier(biomeId),
                EntryCategory.BIOME, dimension, x, 64, z, foundAt, foundAt, 1,
                biomeId, null, "", java.util.List.of(), false, null, true,
                dev.worldtrace.discovery.DiscoveryKeys.biome(dimension, biomeId));
    }

    public static TraceEntry bookmark(String id, String name, long createdAt) {
        return new TraceEntry(id, name, name, EntryCategory.BOOKMARK, "minecraft:overworld",
                10, 64, -20, createdAt, createdAt, 1, null, null, "", java.util.List.of(),
                false, null, true, null);
    }

    public static TraceEntry builder(String id, String name, String dimension, int x, long createdAt) {
        return new TraceEntry(id, name, name, EntryCategory.BOOKMARK, dimension, x, 64, x,
                createdAt, createdAt, 1, null, null, "", java.util.List.of(), false, null, true, null);
    }
}