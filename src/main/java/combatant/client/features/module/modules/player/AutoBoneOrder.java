/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.sounds.SoundEvents;
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
            num("bone_order_click_delay", "click_delay", 2, 1, 40);
    private final NumberValue<Integer> maxClickDelay =
            num("bone_order_max_click_delay", "max_click_delay", 5, 1, 40);
    private final BooleanValue randomizeDelay =
            bool("bone_order_randomize_delay", "randomize_delay", true);
    private final NumberValue<Integer> waitTime =
            num("bone_order_wait_time", "wait_time", 2, 1, 30);
    private final BooleanValue checkInventory =
            bool("bone_order_check_inv", "check_inventory", true);
    private final BooleanValue autoClose =
            bool("bone_order_auto_close", "auto_close", true);

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
            if (checkInventory.get() && isInventoryFull()) {
                if (autoClose.get()) {
                    mc.player.closeContainer();
                }
                return;
            }

            boolean foundBones = false;
            for (Slot slot : menu.slots) {
                if (slot.container == mc.player.getInventory()) continue;

                ItemStack stack = slot.getItem();
                if (stack.is(Items.BONE) || stack.is(Items.BONE_BLOCK) || stack.is(Items.BONE_MEAL)) {
                    foundBones = true;
                    mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                    int delay = randomizeDelay.get()
                            ? ThreadLocalRandom.current().nextInt(clickDelay.get(), Math.max(clickDelay.get(), maxClickDelay.get()) + 1)
                            : clickDelay.get();
                    delayTimer = Math.max(1, delay);
                    return;
                }
            }

            // If no more bones in the container and autoClose is enabled
            if (!foundBones && autoClose.get()) {
                mc.player.closeContainer();
                delayTimer = 10;
            }
        } else if (ClientScreen.current() == null) {
            if (checkInventory.get() && isInventoryFull()) {
                return;
            }
            if (mc.player.connection != null) {
                mc.player.connection.sendCommand("order bones");
                delayTimer = waitTime.get() * 20;
            }
        }
    }

    private boolean isInventoryFull() {
        if (mc.player == null) return false;
        // Check main 36 inventory slots (0 to 35)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                return false;
            }
            if ((stack.is(Items.BONE) || stack.is(Items.BONE_BLOCK) || stack.is(Items.BONE_MEAL))
                    && stack.getCount() < stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }
}
