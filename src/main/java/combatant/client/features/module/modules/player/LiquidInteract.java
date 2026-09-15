/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.CrosshairTargetUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;

@ModuleInfo(
        id = "liquidinteract",
        displayName = "LiquidInteract",
        category = ModuleCategory.PLAYER,
        description = "Allows placing blocks and interacting with water and lava.",
        aliases = {"liquid_interact", "liquids", "waterinteract"}
)
public final class LiquidInteract extends Module {

    private final BooleanValue water = bool("liquidinteract_water", "water", true);
    private final BooleanValue lava = bool("liquidinteract_lava", "lava", true);
    private final BooleanValue flowing = bool("liquidinteract_flowing", "flowing", false);

    private final Minecraft mc = Minecraft.getInstance();

    public LiquidInteract() {
    }

    public boolean isWater() {
        return water.get();
    }

    public boolean isLava() {
        return lava.get();
    }

    public boolean isFlowing() {
        return flowing.get();
    }

    public BooleanValue getWater() {
        return water;
    }

    public BooleanValue getLava() {
        return lava;
    }

    public BooleanValue getFlowing() {
        return flowing;
    }

    /**
     * Checks whether the given fluid state matches the configured settings.
     *
     * @param fluidState the fluid state to test
     * @return true if the fluid matches settings and can be interacted with
     */
    public boolean matches(FluidState fluidState) {
        if (fluidState == null || fluidState.isEmpty()) {
            return false;
        }
        if (!flowing.get() && !fluidState.isSource()) {
            return false;
        }
        if (water.get() && fluidState.is(FluidTags.WATER)) {
            return true;
        }
        if (lava.get() && fluidState.is(FluidTags.LAVA)) {
            return true;
        }
        return false;
    }

    /**
     * Static query determining if the given fluid state should be treated as interactable/solid.
     *
     * @param fluidState the fluid state to evaluate
     * @return true if LiquidInteract is enabled and matches the fluid
     */
    public static boolean canInteract(FluidState fluidState) {
        if (fluidState == null || fluidState.isEmpty()) {
            return false;
        }
        LiquidInteract module = Modules.get(LiquidInteract.class);
        return module != null && module.isEnabled() && module.matches(fluidState);
    }

    /**
     * Static query determining if the fluid at the given position should be treated as interactable.
     *
     * @param level the block getter / level
     * @param pos   the block position
     * @return true if LiquidInteract is enabled and matches the fluid at pos
     */
    public static boolean canInteract(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        return canInteract(level.getFluidState(pos));
    }

    /**
     * Static query determining if the fluid in the given block state should be treated as interactable.
     *
     * @param blockState the block state
     * @return true if LiquidInteract is enabled and matches the fluid
     */
    public static boolean canInteract(BlockState blockState) {
        if (blockState == null) {
            return false;
        }
        return canInteract(blockState.getFluidState());
    }

    /**
     * Checks if the LiquidInteract module is currently enabled.
     *
     * @return true if enabled
     */
    public static boolean isLiquidInteractEnabled() {
        LiquidInteract module = Modules.get(LiquidInteract.class);
        return module != null && module.isEnabled();
    }

    /**
     * Returns the appropriate ClipContext.Fluid handling based on LiquidInteract settings.
     *
     * @return ClipContext.Fluid.ANY if flowing is allowed, SOURCE_ONLY if enabled, or NONE if disabled
     */
    public static ClipContext.Fluid getFluidClip() {
        LiquidInteract module = Modules.get(LiquidInteract.class);
        if (module == null || !module.isEnabled()) {
            return ClipContext.Fluid.NONE;
        }
        return module.isFlowing() ? ClipContext.Fluid.ANY : ClipContext.Fluid.SOURCE_ONLY;
    }

    /**
     * Raycasts against both blocks and liquids using player reach and camera angles.
     *
     * @param player    the local player
     * @param tickDelta the partial tick time
     * @return the hit result, or null if missed / unavailable
     */
    public BlockHitResult raycastLiquid(LocalPlayer player, float tickDelta) {
        if (player == null) return null;
        return raycastLiquid(player, player.blockInteractionRange(), tickDelta);
    }

    /**
     * Raycasts against both blocks and liquids using specified range.
     *
     * @param player    the local player
     * @param range     the max raycast distance
     * @param tickDelta the partial tick time
     * @return the hit result, or null if missed / unavailable
     */
    public BlockHitResult raycastLiquid(LocalPlayer player, double range, float tickDelta) {
        if (player == null || mc.level == null) return null;
        Vec3 start = player.getEyePosition(tickDelta);
        Vec3 viewVec = player.getViewVector(tickDelta).normalize();
        Vec3 end = start.add(viewVec.scale(range));
        return raycastLiquid(start, end);
    }

    /**
     * Raycasts along a line segment between start and end vectors, evaluating blocks and matching liquids.
     *
     * @param start start point (e.g. eye position)
     * @param end   end point
     * @return the closest BlockHitResult for blocks or fluids, or miss result
     */
    public BlockHitResult raycastLiquid(Vec3 start, Vec3 end) {
        if (mc.level == null) return null;
        ClipContext context = new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY,
                mc.player
        );
        return BlockGetter.traverseBlocks(start, end, context, (ctx, pos) -> {
            BlockState blockState = mc.level.getBlockState(pos);
            FluidState fluidState = mc.level.getFluidState(pos);

            VoxelShape blockShape = ctx.getBlockShape(blockState, mc.level, pos);
            BlockHitResult blockHit = mc.level.clipWithInteractionOverride(start, end, pos, blockShape, blockState);

            BlockHitResult fluidHit = null;
            if (matches(fluidState)) {
                VoxelShape fluidShape = ctx.getFluidShape(fluidState, mc.level, pos);
                if (!fluidShape.isEmpty()) {
                    fluidHit = fluidShape.clip(start, end, pos);
                }
            }

            if (blockHit == null) return fluidHit;
            if (fluidHit == null) return blockHit;

            double blockDist = start.distanceToSqr(blockHit.getLocation());
            double fluidDist = start.distanceToSqr(fluidHit.getLocation());
            return fluidDist <= blockDist ? fluidHit : blockHit;
        }, ctx -> {
            Vec3 diff = ctx.getFrom().subtract(ctx.getTo());
            return BlockHitResult.miss(
                    ctx.getTo(),
                    Direction.getApproximateNearest(diff.x, diff.y, diff.z),
                    BlockPos.containing(ctx.getTo())
            );
        });
    }

    /**
     * Static helper for raycasting with LiquidInteract support.
     *
     * @param player    the local player
     * @param tickDelta partial tick time
     * @return the hit result, or null if LiquidInteract is not active
     */
    public static BlockHitResult raycast(LocalPlayer player, float tickDelta) {
        LiquidInteract module = Modules.get(LiquidInteract.class);
        if (module == null || !module.isEnabled() || player == null) {
            return null;
        }
        return module.raycastLiquid(player, tickDelta);
    }

    /**
     * Static helper for raycasting with LiquidInteract support and custom range.
     *
     * @param player    the local player
     * @param range     maximum interaction range
     * @param tickDelta partial tick time
     * @return the hit result, or null if LiquidInteract is not active
     */
    public static BlockHitResult raycast(LocalPlayer player, double range, float tickDelta) {
        LiquidInteract module = Modules.get(LiquidInteract.class);
        if (module == null || !module.isEnabled() || player == null) {
            return null;
        }
        return module.raycastLiquid(player, range, tickDelta);
    }

    /**
     * Static helper for line raycasting with LiquidInteract support.
     *
     * @param start start vector
     * @param end   end vector
     * @return the hit result, or null if LiquidInteract is not active
     */
    public static BlockHitResult raycast(Vec3 start, Vec3 end) {
        LiquidInteract module = Modules.get(LiquidInteract.class);
        if (module == null || !module.isEnabled()) {
            return null;
        }
        return module.raycastLiquid(start, end);
    }

    @EventHandler(priority = 100)
    private void onCrosshairTargetUpdate(CrosshairTargetUpdateEvent event) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        BlockHitResult liquidRay = raycastLiquid(player, event.getTickDelta());
        if (liquidRay == null || liquidRay.getType() == HitResult.Type.MISS) {
            return;
        }

        FluidState hitFluid = mc.level.getFluidState(liquidRay.getBlockPos());
        if (!matches(hitFluid)) {
            return;
        }

        HitResult currentHit = event.getHitResult();
        Vec3 eyePos = player.getEyePosition(event.getTickDelta());
        double liquidDistSq = eyePos.distanceToSqr(liquidRay.getLocation());

        if (currentHit == null || currentHit.getType() == HitResult.Type.MISS) {
            event.setHitResult(liquidRay);
            event.setTargetedEntity(null);
        } else {
            double currentDistSq = eyePos.distanceToSqr(currentHit.getLocation());
            if (liquidDistSq < currentDistSq) {
                event.setHitResult(liquidRay);
                event.setTargetedEntity(null);
            }
        }
    }
}
