package dev.worldtrace.discovery;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records the first time the player stands in a biome, and feeds observed movement to the
 * milestone tracker.
 *
 * <p>Both are read straight from the client world the player is already standing in. Nothing is
 * scanned, no chunk is requested, and work only happens when the player actually moves to a new
 * block, so an idle client costs one integer comparison per tick.
 */
public final class BiomeTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldTrace/Discovery");

    private final DiscoveryManager manager;
    private final MilestoneTracker milestones;

    private String lastDimensionId;
    private String lastBiomeId;
    private BlockPos lastPosition;
    private BlockPos lastDistancePosition;

    public BiomeTracker(DiscoveryManager manager, MilestoneTracker milestones) {
        this.manager = manager;
        this.milestones = milestones;
    }

    public void reset() {
        lastDimensionId = null;
        lastBiomeId = null;
        lastPosition = null;
        lastDistancePosition = null;
    }

    public void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        PlayerEntity player = client.player;
        if (world == null || player == null) {
            return;
        }
        BlockPos position = player.getBlockPos();
        String dimensionId = world.getRegistryKey().getValue().toString();

        if (!dimensionId.equals(lastDimensionId)) {
            // A dimension change resets everything: biome context is per dimension and distances
            // must not carry across worlds with unrelated scales.
            lastDimensionId = dimensionId;
            lastBiomeId = null;
            lastPosition = null;
            lastDistancePosition = null;
        }

        accumulateDistance(world, position);

        if (position.equals(lastPosition)) {
            return;
        }
        lastPosition = position.toImmutable();

        String biomeId = readBiome(world, position);
        if (biomeId == null || biomeId.equals(lastBiomeId)) {
            return;
        }
        lastBiomeId = biomeId;

        if (manager.wouldExceedEntryLimit()) {
            return;
        }
        manager.recordBiome(biomeId, dimensionId, position.getX(), position.getY(), position.getZ(),
                System.currentTimeMillis());
    }

    /** Current biome registry id, or {@code null} before the player moves. Used by the HUD. */
    public String currentBiomeId() {
        return lastBiomeId;
    }

    /**
     * Sums the distance covered since the last observation. Only movement that actually changed the
     * block position counts, so standing still never inflates the total.
     */
    private void accumulateDistance(ClientWorld world, BlockPos position) {
        BlockPos previous = lastDistancePosition;
        lastDistancePosition = position.toImmutable();
        if (previous == null || previous.equals(position)) {
            return;
        }
        double dx = position.getX() - previous.getX();
        double dy = position.getY() - previous.getY();
        double dz = position.getZ() - previous.getZ();
        double travelled = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (travelled <= 0) {
            return;
        }
        manager.addDistanceTravelled(travelled);
        milestones.onDistanceMoved(position, System.currentTimeMillis());
    }

    /**
     * Reads the biome registry id at {@code position}. Modded biomes work exactly like vanilla ones
     * because the id comes from whichever registry the dimension actually uses.
     */
    private static String readBiome(ClientWorld world, BlockPos position) {
        try {
            RegistryEntry<Biome> biome = world.getGeneratorStoredBiome(
                    position.getX(), position.getY(), position.getZ());
            if (biome == null) {
                return null;
            }
            return biome.getKey().map(key -> key.getValue().toString()).orElse(null);
        } catch (RuntimeException e) {
            // A dimension without biome data must never crash a client tick; skip this position.
            LOGGER.warn("Could not read the biome at {}; skipping it.", position.toShortString(), e);
            return null;
        }
    }
}