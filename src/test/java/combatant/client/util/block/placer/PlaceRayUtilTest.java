package combatant.client.util.block.placer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceRayUtilTest {
    private static final BlockPos BLOCK = new BlockPos(0, -61, 4);
    private static final Vec3 EYE = new Vec3(0.5, -58.38, 0.5);

    private static Vec3 toward(Vec3 point) {
        return point.subtract(EYE).normalize();
    }

    @Test
    void rayThroughTopFaceHitsBlock() {
        assertTrue(PlaceRayUtil.rayHitsBlock(EYE, toward(new Vec3(0.5, -60.0, 4.5)), BLOCK, 6.0));
    }

    @Test
    void rayThroughSideFaceHitsBlock() {
        assertTrue(PlaceRayUtil.rayHitsBlock(EYE, toward(new Vec3(0.5, -60.5, 4.0)), BLOCK, 6.0));
    }

    @Test
    void eyeBelowBlockTopStillHitsBlockThroughASide() {
        // Target on higher ground: the eye is below the top face, the ray aimed at the top centre enters a side first.
        BlockPos high = new BlockPos(0, -59, 3);
        Vec3 eye = new Vec3(0.5, -59.62, 0.5);
        Vec3 dir = new Vec3(0.5, -58.0, 3.5).subtract(eye).normalize();
        assertTrue(PlaceRayUtil.rayHitsBlock(eye, dir, high, 6.0));
    }

    @Test
    void rayMissingBlockOrOutOfRangeFails() {
        assertFalse(PlaceRayUtil.rayHitsBlock(EYE, new Vec3(0, 1, 0), BLOCK, 6.0));
        assertFalse(PlaceRayUtil.rayHitsBlock(EYE, toward(new Vec3(0.5, -60.0, 4.5)), BLOCK, 1.0));
    }

    @Test
    void eyeInsideBlockAlwaysHits() {
        assertTrue(PlaceRayUtil.rayHitsBlock(EYE, new Vec3(0, 1, 0), new BlockPos(0, -59, 0), 6.0));
    }

    @Test
    void supportClickUsesTheNeighbourTheRayTouches() {
        // Looking horizontally south at head height: the ray meets (0,-59,3), which sits south of the target.
        BlockPos support = new BlockPos(0, -59, 3);
        BlockPos target = new BlockPos(0, -59, 2);
        BlockHitResult click = PlaceRayUtil.visibleSupportClick(
                EYE, new Vec3(0, 0, 1), target, 6.0, Set.of(support, new BlockPos(0, -59, 5))::contains);
        assertNotNull(click);
        assertEquals(support, click.getBlockPos());
        assertEquals(Direction.NORTH, click.getDirection());
        assertEquals(3.0, click.getLocation().z, 1.0e-9);
    }

    @Test
    void supportClickIsNullWhenTheRayTouchesNoSupport() {
        // Eye level ray never reaches the layer below the feet.
        BlockPos target = new BlockPos(0, -60, 1);
        BlockPos floor = new BlockPos(0, -61, 1);
        assertNull(PlaceRayUtil.visibleSupportClick(EYE, new Vec3(0, 0, 1), target, 6.0, Set.of(floor)::contains));
    }

    @Test
    void supportClickIgnoresNonSolidNeighbours() {
        BlockPos target = new BlockPos(0, -59, 2);
        assertNull(PlaceRayUtil.visibleSupportClick(EYE, new Vec3(0, 0, 1), target, 6.0, p -> false));
    }
}
