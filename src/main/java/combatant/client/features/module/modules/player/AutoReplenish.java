/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

@ModuleInfo(
        id = "autoreplenish",
        displayName = "AutoReplenish",
        aliases = {"AutoRefill", "HotbarRefill"},
        category = ModuleCategory.PLAYER,
        description = "Automatically restocks depleted or empty hotbar stacks from your main inventory."
)
public final class AutoReplenish extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> threshold =
            num("autoReplenishThreshold", "threshold", 16, 1, 64);

    private final NumberValue<Integer> delayTicks =
            num("autoReplenishDelayTicks", "delay_ticks", 2, 1, 10);

    private final BooleanValue refillEmpty =
            bool("autoReplenishRefillEmpty", "refill_empty", true);

    private final ItemStack[] lastHotbar = new ItemStack[9];
    private int cooldown = 0;

    public AutoReplenish() {
        Arrays.fill(lastHotbar, ItemStack.EMPTY);
    }

    @Override
    public void onDisable() {
        Arrays.fill(lastHotbar, ItemStack.EMPTY);
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (ClientScreen.current() instanceof AbstractContainerScreen<?>) {
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (!stack.isEmpty()) {
                lastHotbar[i] = stack.copy();

                if (stack.isStackable() && stack.getCount() <= threshold.get() && stack.getCount() < stack.getMaxStackSize()) {
                    int invSlot = findMatchingInventorySlot(player, stack);
                    if (invSlot != -1) {
                        InventorySwap.INSTANCE.swapInventoryToHotbar(invSlot, i);
                        cooldown = delayTicks.get();
                        return;
                    }
                }
            } else if (refillEmpty.get()) {
                ItemStack prev = lastHotbar[i];
                if (prev != null && !prev.isEmpty()) {
                    int invSlot = findMatchingInventorySlot(player, prev);
                    if (invSlot != -1) {
                        InventorySwap.INSTANCE.swapInventoryToHotbar(invSlot, i);
                        cooldown = delayTicks.get();
                        return;
                    }
                }
            }
        }
    }

    private int findMatchingInventorySlot(LocalPlayer player, ItemStack target) {
        if (player == null || target == null || target.isEmpty()) return -1;

        // Search main inventory slots 9..35
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, target)) {
                return i;
            }
        }

        return -1;
    }
}
