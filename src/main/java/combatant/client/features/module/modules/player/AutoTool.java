/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.MultiPlayerGameModeAccessor;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.block.mining.MiningDamageCalculator;

//todo Description
@ModuleInfo(
        id = "autotool",
        displayName = "AutoTool",
        category = ModuleCategory.PLAYER
)
public class AutoTool extends Module {

    private static final String SETTING_RESTORE_DELAY = "restore_delay_ms";
    private final NumberValue<Integer> restoreDelayMs =
            num("autoToolRestoreDelayMs", SETTING_RESTORE_DELAY, 300, 0, 1000);

    private final Minecraft mc = Minecraft.getInstance();

    private int originalSlot = -1;
    private long lastBreakMs = 0L;

    @Override
    public void onDisable() {
        if (mc != null && mc.player != null && originalSlot >= 0) {
            InventorySwap.INSTANCE.selectHotbar(originalSlot);
        }
        originalSlot = -1;
        lastBreakMs = 0L;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.options == null || mc.gameMode == null) {
            resetState();
            return;
        }

        boolean breakingNow = mc.options.keyAttack.isDown() && mc.gameMode.isDestroying();
        if (breakingNow && mc.gameMode instanceof MultiPlayerGameModeAccessor accessor) {
            BlockPos pos = accessor.combatant$getCurrentBreakingPos();
            if (pos != null) {
                int bestSlot = findBestHotbarTool(pos);
                int selected = ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$getSelectedSlot();
                if (bestSlot >= 0 && bestSlot != selected) {
                    if (originalSlot < 0) {
                        originalSlot = selected;
                    }
                    InventorySwap.INSTANCE.selectHotbar(bestSlot);
                }
                lastBreakMs = System.currentTimeMillis();
                return;
            }
        }

        if (originalSlot >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastBreakMs >= restoreDelayMs.get()) {
                InventorySwap.INSTANCE.selectHotbar(originalSlot);
                originalSlot = -1;
            }
        }
    }

    private int findBestHotbarTool(BlockPos pos) {
        if (mc.player == null || mc.level == null) return -1;
        BlockState state = mc.level.getBlockState(pos);
        return MiningDamageCalculator.findBestHotbarTool(mc.player, state, pos);
    }

    private void resetState() {
        originalSlot = -1;
        lastBreakMs = 0L;
    }
}
