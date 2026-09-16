/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "fastfall",
        displayName = "FastFall",
        category = ModuleCategory.MOVEMENT,
        description = "Pulls the player rapidly down toward the ground when falling to minimize air time."
)
public final class FastFall extends Module {

    public enum Mode {
        MOTION,
        STEP,
        PACKET
    }

    private final EnumValue<Mode> mode = enumMode("mode", Mode.MOTION);
    private final NumberValue<Double> speed = num("speed", 2.5, 0.5, 10.0);
    private final NumberValue<Double> minFallDistance = num("min_fall_distance", 0.5, 0.1, 4.0);
    private final BooleanValue holeOnly = bool("hole_only", true);
    private final BooleanValue noLiquid = bool("no_liquid", true);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;

        if (player.isFallFlying() || player.getAbilities().flying || player.isPassenger()) return;
        if (noLiquid.get() && (player.isInWater() || player.isInLava())) return;
        if (player.onClimbable()) return;

        // Must be in air and moving downwards or started falling
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y >= 0.0 || player.onGround()) return;
        if (player.fallDistance < minFallDistance.get() && -velocity.y < 0.1) return;

        if (holeOnly.get() && !isDescendingIntoHole(player)) {
            return;
        }

        switch (mode.get()) {
            case MOTION -> {
                double targetSpeed = -Math.abs(speed.get());
                player.setDeltaMovement(velocity.x, Math.min(velocity.y, targetSpeed), velocity.z);
            }
            case STEP -> {
                double dropStep = -Math.min(1.0, speed.get() * 0.4);
                player.setDeltaMovement(velocity.x, dropStep, velocity.z);
            }
            case PACKET -> {
                double drop = -Math.min(1.5, speed.get() * 0.5);
                player.setDeltaMovement(velocity.x, drop, velocity.z);
            }
        }
    }

    private boolean isDescendingIntoHole(LocalPlayer player) {
        BlockPos playerPos = player.blockPosition();
        // Check 1 to 5 blocks below player
        for (int dy = 1; dy <= 5; dy++) {
            BlockPos checkPos = playerPos.below(dy);
            BlockState floor = mc.level.getBlockState(checkPos);
            if (!floor.isAir()) {
                BlockPos holePos = checkPos.above();
                return isHole(holePos);
            }
        }
        return false;
    }

    private boolean isHole(BlockPos pos) {
        if (!mc.level.getBlockState(pos).isAir()) return false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            BlockState state = mc.level.getBlockState(side);
            if (state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
                    || state.is(Blocks.NETHERITE_BLOCK) || state.is(Blocks.RESPAWN_ANCHOR)) {
                continue;
            }
            if (!state.isSolidRender()) {
                return false;
            }
        }
        return true;
    }
}
