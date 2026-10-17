package combatant.client.features.module.modules.combat;

import combatant.client.features.module.modules.combat.autoanchor.AutoAnchorInteractionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoAnchorInteractionUtilTest {
    private static final BlockPos POS = new BlockPos(0, 0, 0);

    @Test
    void clipEntersThroughTheFaceNearestTheEye() {
        BlockHitResult hit = AutoAnchorInteractionUtil.clipBlock(new Vec3(3.5, 0.5, 0.5), new Vec3(0.5, 0.5, 0.5), POS);
        assertNotNull(hit);
        assertEquals(Direction.EAST, hit.getDirection());
        assertEquals(1.0, hit.getLocation().x, 1e-9);

        BlockHitResult above = AutoAnchorInteractionUtil.clipBlock(new Vec3(0.5, 3.0, 0.5), new Vec3(0.5, 0.5, 0.5), POS);
        assertNotNull(above);
        assertEquals(Direction.UP, above.getDirection());
    }

    @Test
    void rayEntersFaceOnlyThroughThatFace() {
        Vec3 eye = new Vec3(3.5, 0.5, 0.5);
        Vec3 dir = new Vec3(-1, 0, 0);
        assertTrue(AutoAnchorInteractionUtil.rayEntersFace(eye, dir, POS, Direction.EAST, 5.0));
        assertFalse(AutoAnchorInteractionUtil.rayEntersFace(eye, dir, POS, Direction.UP, 5.0));
        assertFalse(AutoAnchorInteractionUtil.rayEntersFace(eye, new Vec3(1, 0, 0), POS, Direction.EAST, 5.0));
    }

    @Test
    void faceFacingEyeNeedsTheEyeOnTheOuterSide() {
        Vec3 eye = new Vec3(0.5, 1.5, 0.5);
        assertTrue(AutoAnchorInteractionUtil.isFaceFacingEye(eye, POS, Direction.UP));
        assertFalse(AutoAnchorInteractionUtil.isFaceFacingEye(eye, POS, Direction.DOWN));
        assertFalse(AutoAnchorInteractionUtil.isFaceFacingEye(new Vec3(0.5, 0.7, 0.5), POS, Direction.UP));
    }
}
