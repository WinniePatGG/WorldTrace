package dev.worldtrace.discovery;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public String currentBiomeId() {
        return lastBiomeId;
    }

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

    private static String readBiome(ClientWorld world, BlockPos position) {
        try {
            RegistryEntry<Biome> biome = world.getGeneratorStoredBiome(
                    position.getX(), position.getY(), position.getZ());
            if (biome == null) {
                return null;
            }
            return biome.getKey().map(key -> key.getValue().toString()).orElse(null);
        } catch (RuntimeException e) {

            LOGGER.warn("Could not read the biome at {}; skipping it.", position.toShortString(), e);
            return null;
        }
    }
}
