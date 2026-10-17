package dev.worldtrace.discovery;

import dev.worldtrace.util.Distance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Records exploration milestones that a client can genuinely observe.
 *
 * <p>Three families, all derived from data the player already has:
 * <ul>
 *   <li><b>Distance</b> — cumulative blocks walked across the save.</li>
 *   <li><b>Depth</b> — reaching the floor or the ceiling of a dimension.</li>
 *   <li><b>Coverage</b> — how many biomes or confirmed structures the journal holds.</li>
 * </ul>
 *
 * <p>Thresholds are fixed rather than configurable: they are part of the product's sense of progress,
 * and making them adjustable would only let a player's journal drift out of step with everyone
 * else's. Each threshold fires once per save; the running totals live in the
 * {@link DiscoveryManager}, so reloading a world cannot replay old milestones.
 */
public final class MilestoneTracker {

    /** Cumulative distance thresholds in blocks. */
    static final List<Double> DISTANCE_THRESHOLDS =
            List.of(1_000d, 5_000d, 10_000d, 25_000d, 50_000d, 100_000d, 250_000d);

    static final List<Integer> BIOME_THRESHOLDS = List.of(5, 15, 30, 50);
    static final List<Integer> STRUCTURE_THRESHOLDS = List.of(1, 3, 5, 10);

    private final DiscoveryManager manager;

    private int reachedDistanceIndex;
    private int reachedBiomeIndex;
    private int reachedStructureIndex;
    private String floorDimensionId;
    private String ceilingDimensionId;

    public MilestoneTracker(DiscoveryManager manager) {
        this.manager = manager;
    }

    public void reset() {
        reachedDistanceIndex = 0;
        reachedBiomeIndex = 0;
        reachedStructureIndex = 0;
        floorDimensionId = null;
        ceilingDimensionId = null;
    }

    /**
     * Called for every step the player makes. Cheap: the distance list has seven entries and the
     * while-loop only advances when a threshold is actually crossed.
     */
    public void onDistanceMoved(BlockPos position, long now) {
        if (!manager.config().milestoneDiscoveries() || manager.wouldExceedEntryLimit()) {
            return;
        }
        double total = manager.totalDistanceTravelled();
        while (reachedDistanceIndex < DISTANCE_THRESHOLDS.size()
                && total >= DISTANCE_THRESHOLDS.get(reachedDistanceIndex)) {
            double threshold = DISTANCE_THRESHOLDS.get(reachedDistanceIndex);
            reachedDistanceIndex++;
            manager.recordMilestone("travel:" + (long) threshold,
                    Distance.formatDistance(threshold) + " travelled",
                    "*", position.getX(), position.getY(), position.getZ(), now);
        }
    }

    /**
     * Checks the vertical extremes of the player's current dimension. Uses the dimension's own
     * height limits, so the Overworld, the Nether and modded dimensions each report correctly.
     */
    public void onPositionSample(ClientWorld world, BlockPos position, long now) {
        if (!manager.config().milestoneDiscoveries() || manager.wouldExceedEntryLimit()) {
            return;
        }
        String dimensionId = world.getRegistryKey().getValue().toString();

        if (!dimensionId.equals(floorDimensionId) && position.getY() <= world.getBottomY() + 1) {
            floorDimensionId = dimensionId;
            manager.recordMilestone("floor:" + dimensionId, "Reached the " + describeDimension(dimensionId)
                    + " floor", dimensionId, position.getX(), position.getY(), position.getZ(), now);
        }
        if (!dimensionId.equals(ceilingDimensionId) && position.getY() >= world.getTopY() - 1) {
            ceilingDimensionId = dimensionId;
            manager.recordMilestone("ceiling:" + dimensionId, "Reached the " + describeDimension(dimensionId)
                    + " ceiling", dimensionId, position.getX(), position.getY(), position.getZ(), now);
        }
    }

    /**
     * Checks how much of the world the player has covered. Driven off journal counts, which are the
     * only coverage information a client honestly possesses.
     */
    public void onCoverageSample(BlockPos position, long now) {
        if (!manager.config().milestoneDiscoveries() || manager.wouldExceedEntryLimit()) {
            return;
        }
        int biomes = manager.biomeCount();
        while (reachedBiomeIndex < BIOME_THRESHOLDS.size()
                && biomes >= BIOME_THRESHOLDS.get(reachedBiomeIndex)) {
            int threshold = BIOME_THRESHOLDS.get(reachedBiomeIndex);
            reachedBiomeIndex++;
            manager.recordMilestone("biomes:" + threshold, "Discovered " + threshold + " biomes",
                    "*", position.getX(), position.getY(), position.getZ(), now);
        }

        int structures = manager.confirmedStructureCount();
        while (reachedStructureIndex < STRUCTURE_THRESHOLDS.size()
                && structures >= STRUCTURE_THRESHOLDS.get(reachedStructureIndex)) {
            int threshold = STRUCTURE_THRESHOLDS.get(reachedStructureIndex);
            reachedStructureIndex++;
            manager.recordMilestone("structures:" + threshold,
                    "Confirmed " + threshold + (threshold == 1 ? " structure" : " structures"),
                    "*", position.getX(), position.getY(), position.getZ(), now);
        }
    }

    private static String describeDimension(String dimensionId) {
        return dev.worldtrace.util.NameFormatting.prettifyIdentifier(dimensionId);
    }
}