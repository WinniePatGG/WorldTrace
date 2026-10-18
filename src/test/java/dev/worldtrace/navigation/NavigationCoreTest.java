package dev.worldtrace.navigation;

import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.testsupport.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationCoreTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @AfterEach
    void clearOverlay() {
        NavigationOverlay.clear();
    }

    private static NavigationCore.Position position(String dimension, int x, int y, int z) {
        return new NavigationCore.Position(dimension, x, y, z);
    }

    @Test
    @DisplayName("Distance is the true three-dimensional block distance")
    void distanceIsThreeDimensional() {

        assertEquals(5.0, NavigationCore.evaluate(
                position(OVERWORLD, 0, 64, 0), position(OVERWORLD, 3, 64, 4)).distanceBlocks(), 1e-9);
        assertEquals(Math.sqrt(41.0), NavigationCore.evaluate(
                position(OVERWORLD, 0, 64, 0), position(OVERWORLD, 3, 68, 4)).distanceBlocks(), 1e-9);
        assertEquals(NavigationCore.DistanceKind.SAME_DIMENSION, NavigationCore
                .evaluate(position(OVERWORLD, 0, 64, 0), position(OVERWORLD, 3, 64, 4)).kind());
    }

    @Test
    @DisplayName("A destination in another dimension reports no distance at all")
    void otherDimensionHasNoDistance() {
        NavigationCore.Result result =
                NavigationCore.evaluate(position(OVERWORLD, 0, 64, 0), position(NETHER, 100, 64, 100));

        assertEquals(NavigationCore.DistanceKind.OTHER_DIMENSION, result.kind());
        assertTrue(Double.isNaN(result.distanceBlocks()),
                "A cross-dimension distance would be a fabrication");
        assertFalse(result.isDirectional());
    }

    @Test
    @DisplayName("Missing player information yields an UNKNOWN result instead of a guess")
    void unknownPosition() {
        assertEquals(NavigationCore.DistanceKind.UNKNOWN,
                NavigationCore.evaluate(null, position(OVERWORLD, 0, 0, 0)).kind());
        assertEquals(NavigationCore.DistanceKind.UNKNOWN,
                NavigationCore.evaluate(position(OVERWORLD, 0, 0, 0), null).kind());
        assertEquals(NavigationCore.DistanceKind.UNKNOWN, NavigationCore.Result.unknown().kind());
    }

    @Test
    @DisplayName("Heading is zero when the destination is straight ahead")
    void headingStraightAhead() {

        NavigationCore.Result result = NavigationCore.evaluate(
                position(OVERWORLD, 0, 64, 0), position(OVERWORLD, 0, 64, -50), 180.0);
        assertEquals(0.0, result.horizontalDelta(), 0.001);
        assertEquals("ahead", NavigationCore.relativeLabel(result.horizontalDelta()));
        assertEquals("North", NavigationCore.compassLabel(result.targetYaw()));
    }

    @Test
    @DisplayName("Heading turns clockwise to the player's right")
    void headingToTheRight() {

        NavigationCore.Result result = NavigationCore.evaluate(
                position(OVERWORLD, 0, 64, 0), position(OVERWORLD, 50, 64, 0), 180.0);
        assertEquals(90.0, result.horizontalDelta(), 0.001);
        assertEquals("right", NavigationCore.relativeLabel(result.horizontalDelta()));
        assertEquals("East", NavigationCore.compassLabel(result.targetYaw()));
    }

    @Test
    @DisplayName("A destination directly behind reads as behind")
    void headingBehind() {
        NavigationCore.Result result = NavigationCore.evaluate(
                position(OVERWORLD, 0, 64, 0), position(OVERWORLD, 0, 64, 50), 180.0);
        assertEquals(180.0, Math.abs(result.horizontalDelta()), 0.001);
        assertEquals("behind", NavigationCore.relativeLabel(result.horizontalDelta()));
        assertEquals("South", NavigationCore.compassLabel(result.targetYaw()));
    }

    @Test
    @DisplayName("Standing on the destination reports zero distance and no turn")
    void samePosition() {
        NavigationCore.Result result = NavigationCore.evaluate(
                position(OVERWORLD, 7, 70, 7), position(OVERWORLD, 7, 70, 7), 42.0);
        assertEquals(0.0, result.distanceBlocks(), 1e-9);
        assertEquals(0.0, result.horizontalDelta(), 1e-9);
    }

    @Test
    @DisplayName("Heading is wrapped into (-180, 180]")
    void headingWraps() {
        assertEquals(10.0, dev.worldtrace.util.Distance.wrapDegrees(370.0), 1e-9);
        assertEquals(-10.0, dev.worldtrace.util.Distance.wrapDegrees(-370.0), 1e-9);
        assertEquals(180.0, dev.worldtrace.util.Distance.wrapDegrees(180.0), 1e-9);
        assertEquals(-90.0, dev.worldtrace.util.Distance.wrapDegrees(270.0), 1e-9);
    }

    @Test
    @DisplayName("Compass labels use Minecraft's yaw convention")
    void compassLabels() {
        assertEquals("South", NavigationCore.compassLabel(0.0));
        assertEquals("West", NavigationCore.compassLabel(90.0));
        assertEquals("North", NavigationCore.compassLabel(180.0));
        assertEquals("East", NavigationCore.compassLabel(270.0));

        assertEquals("North-East", NavigationCore.compassLabel(-135.0));
    }

    @Test
    @DisplayName("Relative labels cover all eight sectors")
    void relativeLabels() {
        assertEquals("ahead", NavigationCore.relativeLabel(0.0));
        assertEquals("ahead-right", NavigationCore.relativeLabel(45.0));
        assertEquals("right", NavigationCore.relativeLabel(90.0));
        assertEquals("behind-right", NavigationCore.relativeLabel(135.0));
        assertEquals("behind", NavigationCore.relativeLabel(180.0));
        assertEquals("behind-left", NavigationCore.relativeLabel(-135.0));
        assertEquals("left", NavigationCore.relativeLabel(-90.0));
        assertEquals("ahead-left", NavigationCore.relativeLabel(-45.0));
    }

    @Test
    @DisplayName("Distance formatting switches to kilometres above 1000 blocks")
    void distanceFormatting() {
        assertEquals("0 m", dev.worldtrace.util.Distance.formatDistance(0));
        assertEquals("512 m", dev.worldtrace.util.Distance.formatDistance(512.4));
        assertEquals("1.00 km", dev.worldtrace.util.Distance.formatDistance(1000));
        assertEquals("12.35 km", dev.worldtrace.util.Distance.formatDistance(12345.6));
        assertEquals("unknown", dev.worldtrace.util.Distance.formatDistance(Double.NaN));
        assertEquals("unknown", dev.worldtrace.util.Distance.formatDistance(-5));
    }

    @Test
    @DisplayName("The overlay exposes the journal's navigation readout")
    void overlayDrivesJournalReadout() {
        TraceEntry entry = Fixtures.biome("minecraft:plains", OVERWORLD, 0, -100, Fixtures.T0);
        assertTrue(NavigationOverlay.currentFor(entry).isEmpty(),
                "Without a player position the journal must not show a distance");

        NavigationOverlay.update(OVERWORLD, 0, 64, 0, 180.0);
        NavigationCore.Result result = NavigationOverlay.currentFor(entry).orElseThrow();
        assertEquals(NavigationCore.DistanceKind.SAME_DIMENSION, result.kind());
        assertEquals(100.0, result.distanceBlocks(), 1e-9);

        TraceEntry nether = Fixtures.biome("minecraft:crimson_forest", NETHER, 0, -100, Fixtures.T0);
        assertEquals(NavigationCore.DistanceKind.OTHER_DIMENSION,
                NavigationOverlay.currentFor(nether).orElseThrow().kind());

        NavigationOverlay.clear();
        assertFalse(NavigationOverlay.hasPlayer());
    }
}
