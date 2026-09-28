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
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

@ModuleInfo(
        id = "autoreplenish",
        displayName = "AutoReplenish",
        aliases = {"AutoRefill", "HotbarRefill"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.autoreplenish.description")
public final class AutoReplenish extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> threshold = num("autoReplenishThreshold", "threshold", 16, 1, 63);
    private final NumberValue<Integer> delayTicks = num("autoReplenishDelayTicks", "delay_ticks", 2, 1, 20);
    private final BooleanValue refillEmpty = bool("autoReplenishRefillEmpty", "refill_empty", true);

    private final ItemStack[] lastHotbar = new ItemStack[9];
    private ClientLevel level;
    private int cooldown;
    private int generation;
    private boolean refillPending;

    public AutoReplenish() {
        Arrays.fill(lastHotbar, ItemStack.EMPTY);
    }

    @Override
    public void onEnable() {
        resetState();
        level = mc.level;
    }

    @Override
    public void onDisable() {
        generation++;
        resetState();
        level = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            generation++;
            resetState();
            level = null;
            return;
        }
        if (level != mc.level) {
            generation++;
            resetState();
            level = mc.level;
        }
        if (ClientScreen.current() instanceof AbstractContainerScreen<?>) return;
        if (refillPending) return;
        if (cooldown > 0) {
            cooldown--;
            rememberHotbar(player);
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            ItemStack expected = desiredTemplate(slot, current);
            if (expected == null) {
                if (!current.isEmpty()) lastHotbar[slot] = current.copy();
                continue;
            }
            int minimumCount = current.isEmpty() ? 0 : current.getCount();
            if (findBestSource(player, expected, minimumCount) < 0) continue;
            if (queueRefill(slot)) return;
        }
    }

    private ItemStack desiredTemplate(int hotbarSlot, ItemStack current) {
        if (!current.isEmpty()) {
            lastHotbar[hotbarSlot] = current.copy();
            if (!current.isStackable() || current.getCount() > threshold.get() || current.getCount() >= current.getMaxStackSize()) {
                return null;
            }
            return current;
        }
        if (!refillEmpty.get()) return null;
        ItemStack previous = lastHotbar[hotbarSlot];
        return previous == null || previous.isEmpty() ? null : previous;
    }

    private boolean queueRefill(int hotbarSlot) {
        int ticket = ++generation;
        refillPending = true;
        boolean accepted = InventorySwap.INSTANCE.command(
                InventoryActionKind.INVENTORY_CLICK,
                InventorySwapPolicy.LEGIT,
                () -> {
                    try {
                        if (!isEnabled() || generation != ticket || mc.player == null || mc.level == null) return;
                        if (ClientScreen.current() instanceof AbstractContainerScreen<?>) return;
                        performRefill(mc.player, hotbarSlot);
                    } finally {
                        if (generation == ticket) refillPending = false;
                    }
                }
        );
        if (!accepted) {
            refillPending = false;
            return false;
        }
        return true;
    }

    private void performRefill(LocalPlayer player, int hotbarSlot) {
        ItemStack current = player.getInventory().getItem(hotbarSlot);
        ItemStack target = desiredTemplate(hotbarSlot, current);
        if (target == null) return;

        int source = findBestSource(player, target, current.isEmpty() ? 0 : current.getCount());
        if (source < 0) return;

        int sourceScreen = InventorySwap.mapInventoryToScreenSlot(source);
        if (sourceScreen < 0 || !InventorySwap.INSTANCE.clickSwap(sourceScreen, hotbarSlot)) return;
        cooldown = delayTicks.get();
    }

    private int findBestSource(LocalPlayer player, ItemStack target, int minimumCount) {
        int bestSlot = -1;
        int bestCount = minimumCount;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, target)) continue;
            if (stack.getCount() <= bestCount) continue;
            bestSlot = slot;
            bestCount = stack.getCount();
        }
        return bestSlot;
    }

    private void rememberHotbar(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) lastHotbar[slot] = stack.copy();
        }
    }

    private void resetState() {
        Arrays.fill(lastHotbar, ItemStack.EMPTY);
        cooldown = 0;
        refillPending = false;
    }
}
