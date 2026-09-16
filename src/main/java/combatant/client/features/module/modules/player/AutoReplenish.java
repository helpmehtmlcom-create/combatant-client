/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.inventory.manager.InventoryManager;
import combatant.client.util.player.inventory.manager.InventoryPriority;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;

@ModuleInfo(
        id = "autoreplenish",
        displayName = "AutoReplenish",
        aliases = {"replenish", "refill"},
        category = ModuleCategory.PLAYER,
        description = "Automatically replenishes stacks in your hotbar when depleted or low."
)
public class AutoReplenish extends Module {

    private final NumberValue<Integer> threshold = num("threshold", 16, 1, 32);

    private static final int DEFAULT_DELAY = 2;
    private final Minecraft mc = Minecraft.getInstance();
    private final Item[] lastHotbarItems = new Item[9];
    private int cooldownTicks;

    public AutoReplenish() {
        super();
    }

    @Override
    public void onEnable() {
        cooldownTicks = 0;
        Arrays.fill(lastHotbarItems, null);
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return;
        if (ClientScreen.current() != null) return;
        if (mc.player.inventoryMenu == null) return;
        if (!mc.player.isAlive() || mc.player.isSpectator()) return;
        if (mc.player.containerMenu != null && !mc.player.containerMenu.getCarried().isEmpty()) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack hotbarStack = mc.player.getInventory().getItem(hotbarSlot);
            Item targetItem = null;
            int currentCount = 0;

            if (!hotbarStack.isEmpty()) {
                Item item = hotbarStack.getItem();
                lastHotbarItems[hotbarSlot] = item;
                if (isReplenishable(item)
                        && hotbarStack.getCount() <= threshold.get()
                        && hotbarStack.getCount() < hotbarStack.getMaxStackSize()) {
                    targetItem = item;
                    currentCount = hotbarStack.getCount();
                }
            } else {
                Item lastItem = lastHotbarItems[hotbarSlot];
                if (lastItem != null && isReplenishable(lastItem)) {
                    targetItem = lastItem;
                    currentCount = 0;
                }
            }

            if (targetItem == null) continue;

            int bestSlot = -1;
            int bestCount = currentCount;

            // Search main player inventory (slots 9..35)
            for (int invSlot = 9; invSlot <= 35; invSlot++) {
                ItemStack invStack = mc.player.inventoryMenu.getSlot(invSlot).getItem();
                if (invStack.isEmpty() || !invStack.is(targetItem)) continue;

                if (invStack.getCount() > bestCount) {
                    bestCount = invStack.getCount();
                    bestSlot = invSlot;
                }
            }

            if (bestSlot != -1) {
                lastHotbarItems[hotbarSlot] = targetItem;
                InventoryManager.INSTANCE.submitHotbarSwap(InventoryPriority.NORMAL, bestSlot, hotbarSlot, this);
                cooldownTicks = DEFAULT_DELAY;
                break;
            } else if (hotbarStack.isEmpty()) {
                lastHotbarItems[hotbarSlot] = null;
            }
        }
    }

    /**
     * Automatically considers all stackable combat items, blocks, and consumables replenishable.
     */
    public boolean isReplenishable(Item item) {
        if (item == null || item == Items.AIR) return false;
        return item.getDefaultInstance().getMaxStackSize() > 1;
    }

    public boolean isItemTracked(Item item) {
        return isReplenishable(item);
    }

    public NumberValue<Integer> getThreshold() {
        return threshold;
    }
}
