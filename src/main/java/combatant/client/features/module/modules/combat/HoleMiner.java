/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.target.TargetManager;
import combatant.client.util.target.TargetingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

@ModuleInfo(
        id = "holeminer",
        displayName = "HoleMiner",
        category = ModuleCategory.COMBAT
)
public final class HoleMiner extends Module {

    private final NumberValue<Double> range = numCommon(
            "holeminerRange",
            "range",
            CommonSettingSchemas.COMBAT_RANGE,
            5.0,
            2.0,
            8.0
    );
    private final NumberValue<Integer> delay = num("delay", 0, 0, 20);
    private final EnumValue<TargetingUtil.TargetPriority> priority = enumCommon(
            "holeminerPriority",
            "priority",
            CommonSettingSchemas.COMBAT_PRIORITY,
            TargetingUtil.TargetPriority.DISTANCE,
            TargetingUtil.TargetPriority.values()
    );
    private final BooleanValue mineBurrow = bool("mine_burrow", true);
    private final BooleanValue mineSurround = bool("mine_surround", true);

    private final Minecraft mc = Minecraft.getInstance();
    private BlockPos currentMiningPos = null;
    private int ticksWaiting = 0;

    @Override
    public void onDisable() {
        currentMiningPos = null;
        ticksWaiting = 0;
        TargetManager.clear(TargetManager.Source.MODULE);
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.getConnection() == null) return;
        LocalPlayer player = mc.player;

        if (delay.get() > 0 && ticksWaiting++ < delay.get()) {
            return;
        }
        ticksWaiting = 0;

        // Unified target resolution using TargetManager:
        // Automatically ignores friends/teammates and prioritizes targets according to configuration
        LivingEntity resolved = TargetManager.resolveTarget(player, mc.level, range.get(), priority.get());
        if (!(resolved instanceof Player enemy) || !TargetingUtil.isValidPlayerTarget(player, enemy, range.get())) {
            currentMiningPos = null;
            TargetManager.clear(TargetManager.Source.MODULE);
            return;
        }

        TargetManager.setModuleTarget(enemy);
        BlockPos enemyPos = enemy.blockPosition();

        // 1. Check if enemy is burrowed inside a block
        if (mineBurrow.get() && TargetingUtil.isBurrowed(enemy, mc.level)) {
            BlockState inFeet = mc.level.getBlockState(enemyPos);
            if (TargetingUtil.isBlastResistant(inFeet) || inFeet.blocksMotion()) {
                startMining(enemyPos);
                return;
            }
        }

        // 2. Check enemy surround blocks
        if (mineSurround.get()) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos surround = enemyPos.relative(dir);
                BlockState state = mc.level.getBlockState(surround);
                if (TargetingUtil.isBlastResistant(state)) {
                    startMining(surround);
                    return;
                }
            }
        }
    }

    private void startMining(BlockPos pos) {
        if (pos.equals(currentMiningPos)) return;
        currentMiningPos = pos;
        TargetManager.markCombat();

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
