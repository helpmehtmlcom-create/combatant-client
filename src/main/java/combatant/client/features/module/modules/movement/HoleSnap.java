/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "holesnap",
        displayName = "HoleSnap",
        category = ModuleCategory.MOVEMENT,
        aliases = {"holerush", "holepull"},
        description = "Pulls the player into the nearest safe hole."
)
public final class HoleSnap extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range = num("range", 4.0, 1.0, 6.0);
    private final NumberValue<Double> speed = num("speed", 0.8, 0.1, 2.0);
    private final BooleanValue autoDisable = bool("autoDisable", true);
    private final BooleanValue timer = bool("timer", true);

    private BlockPos targetHole;
    private boolean timerActive;

    @Override
    public void onEnable() {
        targetHole = null;
        timerActive = false;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            setEnabled(false);
            return;
        }

        targetHole = findNearestHole(player, level, range.get());
        if (targetHole == null) {
            setEnabled(false);
            return;
        }

        if (timer.get()) {
            Timer.setExternalTickTimer(1.2f);
            timerActive = true;
        }
    }

    @Override
    public void onDisable() {
        clearTimer();
        targetHole = null;
    }

    private void clearTimer() {
        if (timerActive) {
            Timer.clearExternalTickTimer();
            timerActive = false;
        }
    }

    @EventHandler
    private void onTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            if (isEnabled()) {
                setEnabled(false);
            }
            return;
        }

        if (targetHole == null || !isValidHole(level, targetHole)) {
            targetHole = findNearestHole(player, level, range.get());
            if (targetHole == null) {
                setEnabled(false);
                return;
            }
        }

        Vec3 target = new Vec3(targetHole.getX() + 0.5, targetHole.getY(), targetHole.getZ() + 0.5);
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
        double hDist = Math.hypot(dx, dz);

        if (hDist > range.get() + 1.0) {
            setEnabled(false);
            return;
        }

        if (hDist < 0.15 && player.getY() <= targetHole.getY() + 0.5) {
            player.setPos(target.x, player.getY(), target.z);
            double motionY = Math.min(0.0, player.getDeltaMovement().y);
            player.setDeltaMovement(0.0, motionY, 0.0);
            if (autoDisable.get()) {
                setEnabled(false);
            }
        }
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (event == null || event.getType() != MoverType.SELF) {
            return;
        }

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || targetHole == null) {
            return;
        }

        if (!isValidHole(level, targetHole)) {
            targetHole = findNearestHole(player, level, range.get());
            if (targetHole == null) {
                setEnabled(false);
                return;
            }
        }

        Vec3 target = new Vec3(targetHole.getX() + 0.5, targetHole.getY(), targetHole.getZ() + 0.5);
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
        double hDist = Math.hypot(dx, dz);

        if (hDist > range.get() + 1.0) {
            setEnabled(false);
            return;
        }

        if (hDist < 0.15 && player.getY() <= targetHole.getY() + 0.5) {
            player.setPos(target.x, player.getY(), target.z);
            double motionY = Math.min(0.0, player.getDeltaMovement().y);
            player.setDeltaMovement(0.0, motionY, 0.0);
            event.setMovement(new Vec3(0.0, Math.min(0.0, event.getMovement().y), 0.0));
            if (autoDisable.get()) {
                setEnabled(false);
            }
            return;
        }

        if (hDist > 0.0001) {
            double pullSpeed = speed.get();
            double moveDist = Math.min(hDist, pullSpeed);
            double motionX = (dx / hDist) * moveDist;
            double motionZ = (dz / hDist) * moveDist;

            event.setMovement(new Vec3(motionX, event.getMovement().y, motionZ));
            player.setDeltaMovement(motionX, player.getDeltaMovement().y, motionZ);
        }
    }

    private BlockPos findNearestHole(LocalPlayer player, ClientLevel level, double maxRange) {
        if (player == null || level == null) {
            return null;
        }

        BlockPos playerPos = player.blockPosition();
        int rCeil = (int) Math.ceil(maxRange);
        double maxRangeSq = maxRange * maxRange;

        BlockPos nearest = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -rCeil; dx <= rCeil; dx++) {
            for (int dy = -rCeil; dy <= rCeil; dy++) {
                for (int dz = -rCeil; dz <= rCeil; dz++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    double distSq = player.distanceToSqr(center);
                    if (distSq > maxRangeSq) {
                        continue;
                    }

                    if (isValidHole(level, pos)) {
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            nearest = pos;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private static boolean isValidHole(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        if (pos.getY() < level.getMinY() || pos.getY() + 1 >= level.getMaxY()) {
            return false;
        }

        if (!isPassable(level, pos) || !isPassable(level, pos.above())) {
            return false;
        }

        if (!isSafeBlock(level.getBlockState(pos.below()))) {
            return false;
        }

        return isSafeBlock(level.getBlockState(pos.north()))
                && isSafeBlock(level.getBlockState(pos.south()))
                && isSafeBlock(level.getBlockState(pos.east()))
                && isSafeBlock(level.getBlockState(pos.west()));
    }

    private static boolean isPassable(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            return false;
        }
        if (!state.getFluidState().isEmpty()
                && !state.getFluidState().is(Fluids.WATER)
                && !state.getFluidState().is(Fluids.FLOWING_WATER)) {
            return false;
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isSafeBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block == Blocks.BEDROCK
                || block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.RESPAWN_ANCHOR;
    }
}
