/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "holeminer",
        displayName = "HoleMiner",
        category = ModuleCategory.COMBAT
)
public final class HoleMiner extends Module {

    private final NumberValue<Double> targetRange = num("target_range", 5.0, 2.0, 8.0);
    private final BooleanValue mineBurrow = bool("mine_burrow", true);
    private final BooleanValue mineSurround = bool("mine_surround", true);

    private final Minecraft mc = Minecraft.getInstance();
    private BlockPos currentMiningPos = null;

    @Override
    public void onDisable() {
        currentMiningPos = null;
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.getConnection() == null) return;
        LocalPlayer player = mc.player;

        // Find closest enemy player
        Player closestEnemy = null;
        double closestDist = Double.MAX_VALUE;

        for (Player other : mc.level.players()) {
            if (other == player || other.isSpectator() || other.isDeadOrDying()) continue;
            double d = player.distanceTo(other);
            if (d <= targetRange.get() && d < closestDist) {
                closestDist = d;
                closestEnemy = other;
            }
        }

        if (closestEnemy == null) {
            currentMiningPos = null;
            return;
        }

        BlockPos enemyPos = closestEnemy.blockPosition();

        // 1. Check if enemy is burrowed inside a block
        if (mineBurrow.get()) {
            BlockState inFeet = mc.level.getBlockState(enemyPos);
            if (!inFeet.isAir() && (inFeet.is(Blocks.OBSIDIAN) || inFeet.is(Blocks.CRYING_OBSIDIAN)
                    || inFeet.is(Blocks.ENDER_CHEST) || inFeet.is(Blocks.ANVIL) || inFeet.is(Blocks.CHIPPED_ANVIL)
                    || inFeet.is(Blocks.DAMAGED_ANVIL) || inFeet.is(Blocks.RESPAWN_ANCHOR))) {
                startMining(enemyPos);
                return;
            }
        }

        // 2. Check enemy surround blocks
        if (mineSurround.get()) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos surround = enemyPos.relative(dir);
                BlockState state = mc.level.getBlockState(surround);
                if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.ENDER_CHEST)) {
                    startMining(surround);
                    return;
                }
            }
        }
    }

    private void startMining(BlockPos pos) {
        if (pos.equals(currentMiningPos)) return;
        currentMiningPos = pos;

        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    pos,
                    Direction.UP
            ));
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    pos,
                    Direction.UP
            ));
            if (mc.player != null) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }
}
