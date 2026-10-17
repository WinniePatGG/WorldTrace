package dev.worldtrace.discovery;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Set;

/**
 * A bounded, resumable scan for structure marker blocks.
 *
 * <p>Verifying a pin means looking at real blocks, and doing that in one go would be a visible
 * frame-time spike. The scan therefore walks a pre-computed integer box and processes a fixed number
 * of positions per tick, so the cost is spread across frames and the player never notices.
 *
 * <p>The scan only reads blocks in chunks the client has already loaded; it never requests a chunk.
 */
public final class BlockScanJob {

    private final BlockPos.Mutable cursor = new BlockPos.Mutable();
    private final Set<Block> markers;
    private final World world;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    private int offset;
    private boolean finished;
    private boolean foundMarker;

    public BlockScanJob(World world, BlockPos center, int radius, Set<Block> markers) {
        this.world = world;
        this.markers = Set.copyOf(markers);
        this.minX = center.getX() - radius;
        this.minY = center.getY() - radius;
        this.minZ = center.getZ() - radius;
        this.sizeX = radius * 2 + 1;
        this.sizeY = radius * 2 + 1;
        this.sizeZ = radius * 2 + 1;
        this.cursor.set(minX, minY, minZ);
    }

    /**
     * Examines at most {@code budget} positions.
     *
     * @return true when the scan found a marker and can stop early
     */
    public boolean step(int budget) {
        if (finished) {
            return foundMarker;
        }
        int limit = Math.max(1, budget);
        for (int i = 0; i < limit && !finished; i++) {
            if (matches()) {
                foundMarker = true;
                finished = true;
                break;
            }
            advance();
        }
        return foundMarker;
    }

    private boolean matches() {
        // Only inspect chunks the client already has. This is a read, never a load request.
        if (!world.isChunkLoaded(cursor.getX() >> 4, cursor.getZ() >> 4)) {
            return false;
        }
        return markers.contains(world.getBlockState(cursor).getBlock());
    }

    private void advance() {
        int x = cursor.getX();
        int y = cursor.getY();
        int z = cursor.getZ();

        x++;
        if (x > minX + sizeX - 1) {
            x = minX;
            z++;
            if (z > minZ + sizeZ - 1) {
                z = minZ;
                y++;
                if (y > minY + sizeY - 1) {
                    finished = true;
                }
            }
        }
        cursor.set(x, y, z);
        offset++;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean foundMarker() {
        return foundMarker;
    }

    /** Fraction of the box that has been walked, for progress reporting. */
    public float progress() {
        int total = Math.max(1, sizeX * sizeY * sizeZ);
        return Math.min(1.0f, (float) offset / total);
    }
}