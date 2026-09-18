/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.NumberValue;
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
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "autoboneorder",
        displayName = "Auto Bone Order",
        description = "Automates ordering and purchasing bones on DonutSMP for infinite bonemeal tree and crop farms.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"boneorder", "bonefarm"}
)
public class AutoBoneOrder extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> clickDelay =
            num("bone_order_click_delay", "click_delay", 1, 1, 40);
    private final NumberValue<Integer> waitTime =
            num("bone_order_wait_time", "wait_time", 1, 1, 30);

    private int delayTimer = 0;

    @Override
    public void onEnable() {
        delayTimer = 0;
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.gameMode == null) return;

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu != null && menu != mc.player.inventoryMenu && ClientScreen.current() instanceof AbstractContainerScreen<?>) {
            for (Slot slot : menu.slots) {
                if (slot.container == mc.player.getInventory()) continue;

                ItemStack stack = slot.getItem();
                if (stack.is(Items.BONE) || stack.is(Items.BONE_BLOCK) || stack.is(Items.BONE_MEAL)) {
                    mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                    delayTimer = clickDelay.get();
                    return;
                }
            }
        } else if (ClientScreen.current() == null) {
            if (mc.player.connection != null) {
                mc.player.connection.sendCommand("order bones");
                delayTimer = waitTime.get() * 20;
            }
        }
    }
}
