/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import combatant.client.util.aiming.data.Rotation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Picks the face and hit point that reproduce a schematic block state.
 *
 * <p>Rather than encode per-block placement rules, every candidate click is run through the block's own
 * {@code getStateForPlacement} with the player briefly turned to the aim rotation, exactly what the server
 * will do with the click. A click that yields the wanted state is exact; one that yields the right block in
 * another state (a stair facing the wrong way, a single slab where a double one goes) is approximate.</p>
 */
final class BuildPlanner {

    private static final double[] FACE_OFFSETS = {0.0, -0.3, 0.3};
    // The vertical centre of a side face is the top/bottom boundary for stairs, slabs and trapdoors: a click
    // planned there flips half on the smallest rotation rounding, so side faces are aimed clear of it.
    private static final double[] SIDE_HEIGHTS = {-0.25, 0.25};

    private BuildPlanner() {
    }

    record Plan(BlockPos target, BlockPos support, Direction face, Vec3 hit, Rotation rotation,
                boolean exact, double distanceSq) {
    }

    static Plan plan(LocalPlayer player, Level level, BlockPos target, BlockState desired, ItemStack stack,
                     double range, boolean aimAtHit) {
        if (!(stack.getItem() instanceof BlockItem)) return null;
        Vec3 eyes = player.getEyePosition();
        double rangeSq = range * range;
        Plan best = null;
        for (Direction toSupport : Direction.values()) {
            BlockPos support = target.relative(toSupport);
            BlockState supportState = level.getBlockState(support);
            if (supportState.canBeReplaced() || isInteractive(supportState)
                    || supportState.getShape(level, support).isEmpty()) continue;
            Direction face = toSupport.getOpposite();
            Plan candidate = bestOnFace(player, level, target, desired, stack, support, face, eyes, rangeSq, aimAtHit);
            if (candidate != null && better(candidate, best)) best = candidate;
        }
        return best;
    }

    private static Plan bestOnFace(LocalPlayer player, Level level, BlockPos target, BlockState desired, ItemStack stack,
                                   BlockPos support, Direction face, Vec3 eyes, double rangeSq, boolean aimAtHit) {
        Vec3 center = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        // A face pointing away from the eyes cannot be clicked without looking through the support block.
        double facing = (eyes.x - center.x) * face.getStepX() + (eyes.y - center.y) * face.getStepY()
                + (eyes.z - center.z) * face.getStepZ();
        if (facing <= 0.0) return null;

        Plan best = null;
        double[] heights = face.getAxis() == Direction.Axis.Y ? FACE_OFFSETS : SIDE_HEIGHTS;
        for (double u : FACE_OFFSETS) {
            for (double v : heights) {
                Vec3 point = pointOnFace(center, face, u, v);
                double distanceSq = eyes.distanceToSqr(point);
                if (distanceSq > rangeSq) continue;
                Rotation rotation = Rotation.lookingAt(point, eyes);
                BlockState result = simulate(player, level, stack, target, support, face, point,
                        aimAtHit ? rotation : null);
                if (result == null || result.getBlock() != desired.getBlock()) continue;
                Plan candidate = new Plan(target, support, face, point, rotation, result == desired, distanceSq);
                if (!better(candidate, best)) continue;
                BlockHitResult hit = trace(player, rotation, Math.sqrt(distanceSq) + 0.5);
                if (hit == null || !hit.getBlockPos().equals(support) || hit.getDirection() != face) continue;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean better(Plan candidate, Plan best) {
        if (best == null) return true;
        if (candidate.exact != best.exact) return candidate.exact;
        return candidate.distanceSq < best.distanceSq;
    }

    private static Vec3 pointOnFace(Vec3 center, Direction face, double u, double v) {
        return switch (face.getAxis()) {
            case X -> center.add(0.0, v, u);
            case Y -> center.add(u, 0.0, v);
            case Z -> center.add(u, v, 0.0);
        };
    }

    /**
     * The state the block item would place for this click. {@code rotation} temporarily replaces the player's
     * look so facing-dependent blocks resolve the way the server will see them; {@code null} keeps the
     * player's own rotation.
     */
    static BlockState simulate(LocalPlayer player, Level level, ItemStack stack, BlockPos target, BlockPos support,
                               Direction face, Vec3 hit, Rotation rotation) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        try {
            if (rotation != null) {
                player.setYRot(rotation.yaw());
                player.setXRot(rotation.pitch());
            }
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(hit, face, support, false));
            if (!context.canPlace() || !context.getClickedPos().equals(target)) return null;
            Block block = blockItem.getBlock();
            BlockState state = block.getStateForPlacement(context);
            if (state == null || !state.canSurvive(level, target)) return null;
            if (!level.isUnobstructed(state, target, CollisionContext.placementContext(player))) return null;
            return state;
        } catch (RuntimeException e) {
            return null;
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    static BlockHitResult trace(LocalPlayer player, Rotation rotation, double range) {
        if (rotation == null) return null;
        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(rotation.directionVector().scale(range));
        HitResult hit = player.level().clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    static boolean matches(BlockHitResult hit, Plan plan) {
        return hit != null && hit.getBlockPos().equals(plan.support()) && hit.getDirection() == plan.face();
    }

    /** Clicking these without sneaking opens or toggles them instead of placing against them. */
    static boolean isInteractive(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BaseEntityBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof BedBlock
                || block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof NoteBlock
                || block instanceof DiodeBlock
                || block instanceof LoomBlock
                || block instanceof StonecutterBlock
                || block instanceof GrindstoneBlock
                || block instanceof CartographyTableBlock
                || block instanceof SmithingTableBlock;
    }
}
