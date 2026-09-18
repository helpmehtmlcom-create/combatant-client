/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Dispenser and dropper gambling rig for DonutSMP casino bases.
 * Rapidly pulls all non-winning slots into inventory leaving only the desired prize slot.
 * Ported and adapted from 67Client's GambleRiggerModule.
 */
@ModuleInfo(
        id = "gamblerigger",
        displayName = "GambleRigger",
        description = "Rigs dispenser/dropper casino gambles on DonutSMP by pulling all slots except the chosen one.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"riggamble", "casinorig", "dropperrig"}
)
public class GambleRigger extends Module {

    private final NumberValue<Integer> keepSlot =
            num("gamblerigger_keep_slot", "keep_slot", 4, 0, 8);
    private final BooleanValue autoExtract =
            bool("gamblerigger_auto_extract", "auto_extract", true);
    private final BooleanValue chatFeedback =
            bool("gamblerigger_feedback", "chat_feedback", true);

    private final Minecraft mc = Minecraft.getInstance();
    private int lastContainerId = -1;

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.gameMode == null) return;

        if (mc.player.containerMenu instanceof DispenserMenu menu) {
            if (menu.containerId != lastContainerId) {
                lastContainerId = menu.containerId;
                if (autoExtract.get()) {
                    triggerRig(menu);
                }
            }
        } else {
            lastContainerId = -1;
        }
    }

    public void triggerRig(DispenserMenu menu) {
        if (mc.player == null || mc.gameMode == null) return;

        int keep = keepSlot.get();
        int pulled = 0;

        for (int slot = 0; slot < 9; slot++) {
            if (slot == keep) continue;

            ItemStack stack = menu.getSlot(slot).getItem();
            if (!stack.isEmpty()) {
                // Quick move to inventory
                mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                pulled++;
            }
        }

        if (chatFeedback.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(
                    Component.literal(String.format("§a[GambleRigger] §7Pulled §e%d §7stacks, keeping slot §f#%d§7!",
                            pulled, keep + 1))
            );
        }
    }
}
