/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.SpeedMine;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

@ModuleInfo(
        id = "antitrap",
        displayName = "AntiTrap",
        description = "Detects and counters DonutSMP player traps, breaking cobwebs at feet and avoiding falling anvils.",
        category = ModuleCategory.COMBAT,
        subCategory = ModuleSubCategory.DEFENSE,
        aliases = {"antiweb", "trapprotect", "webbreaker"}
)
public class AntiTrap extends Module {

    private final BooleanValue antiCobweb =
            bool("antitrap_cobweb", "cobweb", true);
    private final BooleanValue antiAnvil =
            bool("antitrap_anvil", "anvil", true);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        // 1. Anti-Cobweb check
        if (antiCobweb.get()) {
            BlockPos feet = player.blockPosition();
            BlockPos head = feet.above();

            if (mc.level.getBlockState(feet).is(Blocks.COBWEB)) {
                breakBlock(player, feet);
                return;
            }
            if (mc.level.getBlockState(head).is(Blocks.COBWEB)) {
                breakBlock(player, head);
                return;
            }
        }

        // 2. Anti-Falling Anvil check
        if (antiAnvil.get()) {
            BlockPos headPos = player.blockPosition().above(2);
            for (int dy = 1; dy <= 6; dy++) {
                BlockPos checkPos = headPos.above(dy);
                BlockState state = mc.level.getBlockState(checkPos);
                if (state.getBlock() instanceof AnvilBlock) {
                    breakBlock(player, checkPos);
                    break;
                }
            }
        }
    }

    private void breakBlock(LocalPlayer player, BlockPos pos) {
        SpeedMine speedMine = Modules.get(SpeedMine.class);
        if (speedMine != null && speedMine.isEnabled()) {
            speedMine.startMining(pos, Direction.UP);
        } else if (mc.gameMode != null) {
            mc.gameMode.startDestroyBlock(pos, Direction.UP);
        }
    }
}
