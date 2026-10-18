package dev.worldtrace.discovery;

import dev.worldtrace.model.TracePin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class PinTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldTrace/Discovery");

    private static final int SCAN_BUDGET_PER_TICK = 4096;
    private static final int TICKS_BETWEEN_CHECKS = 10;

    private final DiscoveryManager manager;
    private final List<BlockScanJob> activeScans = new ArrayList<>();
    private final List<PendingPin> pending = new ArrayList<>();

    private int tickCounter;
    private String lastDimensionId;

    public PinTracker(DiscoveryManager manager) {
        this.manager = manager;
    }

    public void reset() {
        activeScans.clear();
        pending.clear();
        tickCounter = 0;
        lastDimensionId = null;
    }

    public void tick(MinecraftClient client) {
        advanceScans(client.world);
        if (++tickCounter < TICKS_BETWEEN_CHECKS) {
            return;
        }
        tickCounter = 0;

        ClientWorld world = client.world;
        PlayerEntity player = client.player;
        if (world == null || player == null) {
            return;
        }
        String dimensionId = world.getRegistryKey().getValue().toString();
        if (!dimensionId.equals(lastDimensionId)) {
            lastDimensionId = dimensionId;

            activeScans.clear();
            pending.clear();
        }
        checkPins(world, player, dimensionId);
    }

    private void checkPins(ClientWorld world, PlayerEntity player, String dimensionId) {
        BlockPos playerPos = player.getBlockPos();
        for (TracePin pin : manager.unresolvedPins()) {
            if (!pin.dimensionId().equals(dimensionId)) {
                continue;
            }
            if (!isWithinReach(playerPos, pin)) {
                continue;
            }
            manager.resolvePin(pin);
            if (pin.structureId() == null) {

                manager.recordPin(pin, true, System.currentTimeMillis());
                continue;
            }
            startVerification(world, pin);
        }
    }

    private boolean isWithinReach(BlockPos playerPos, TracePin pin) {
        long dx = (long) playerPos.getX() - pin.x();
        long dy = (long) playerPos.getY() - pin.y();
        long dz = (long) playerPos.getZ() - pin.z();
        return dx * dx + dy * dy + dz * dz <= (long) pin.radius() * pin.radius();
    }

    private void startVerification(ClientWorld world, TracePin pin) {
        StructureCatalog.StructureType type = StructureCatalog.lookup(pin.structureId());
        if (type == null) {

            LOGGER.debug("Pin {} references unknown structure '{}'; recording an unconfirmed visit.",
                    pin.id(), pin.structureId());
            manager.recordPin(pin, false, System.currentTimeMillis());
            return;
        }
        BlockPos center = new BlockPos(pin.x(), pin.y(), pin.z());
        activeScans.add(new BlockScanJob(world, center, pin.radius(), type.markers()));
        pending.add(new PendingPin(pin));
        LOGGER.debug("Verifying pin {} ({}) near {}", pin.id(), type.displayName(), center.toShortString());
    }

    private void advanceScans(ClientWorld world) {
        if (activeScans.isEmpty() || world == null) {
            return;
        }
        int perScan = Math.max(256, SCAN_BUDGET_PER_TICK / activeScans.size());
        for (int i = activeScans.size() - 1; i >= 0; i--) {
            BlockScanJob scan = activeScans.get(i);
            PendingPin pin = pending.get(i);
            boolean found = scan.step(perScan);
            if (scan.isFinished()) {
                activeScans.remove(i);
                pending.remove(i);
                manager.recordPin(pin.pin(), found, System.currentTimeMillis());
            }
        }
    }

    public int pendingCount() {
        return manager.unresolvedPins().size();
    }

    private record PendingPin(TracePin pin) {
    }
}
