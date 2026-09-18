/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@ModuleInfo(
        id = "autodeliver",
        displayName = "Auto Deliver",
        description = "Automates depositing target quest and trade items into DonutSMP delivery screens.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"orderdeliver", "questdeliver"}
)
public class AutoDeliver extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final StringValue targetItem =
            text("deliver_target_item", "target_item", "diamond");
    private final NumberValue<Integer> delayTicks =
            num("deliver_delay", "delay_ticks", 3, 1, 40);

    private int timer = 0;

    @Override
    public void onEnable() {
        timer = 0;
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.gameMode == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu == mc.player.inventoryMenu) return;

        if (ClientScreen.current() instanceof AbstractContainerScreen<?> containerScreen) {
            String title = containerScreen.getTitle().getString().toLowerCase();
            if (!title.contains("deliver") && !title.contains("quest") && !title.contains("order")) {
                return;
            }

            String targetFilter = targetItem.get().trim().toLowerCase();

            // Find matching item in player inventory slots to shift-click into delivery container
            for (Slot slot : menu.slots) {
                if (slot.container != mc.player.getInventory()) continue;

                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;

                String hover = stack.getHoverName().getString().toLowerCase();
                String id = stack.getItem().toString().toLowerCase();

                if (targetFilter.isEmpty() || hover.contains(targetFilter) || id.contains(targetFilter)) {
                    mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                    timer = delayTicks.get();
                    return;
                }
            }
        }
    }
}
