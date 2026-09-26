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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "inventorysorter",
        displayName = "InventorySorter",
        aliases = {"InvSorter", "AutoSort"},
        category = ModuleCategory.PLAYER,
        description = "Automatically sorts and stacks items in your inventory."
)
public final class InventorySorter extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue stackMatching =
            bool("invSorterStackMatching", "stack_matching", true);

    private final BooleanValue sortInventory =
            bool("invSorterSortInventory", "sort_inventory", true);

    private final NumberValue<Integer> delayTicks =
            num("invSorterDelayTicks", "delay_ticks", 2, 1, 10);

    private int cooldown = 0;

    @Override
    public void onDisable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;

        if (ClientScreen.current() instanceof AbstractContainerScreen<?>) {
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // 1. Stack matching incomplete stacks
        if (stackMatching.get()) {
            for (int i = 9; i < 36; i++) {
                ItemStack stackI = player.getInventory().getItem(i);
                if (stackI.isEmpty() || !stackI.isStackable() || stackI.getCount() >= stackI.getMaxStackSize()) {
                    continue;
                }

                for (int j = i + 1; j < 36; j++) {
                    ItemStack stackJ = player.getInventory().getItem(j);
                    if (stackJ.isEmpty()) continue;

                    if (ItemStack.isSameItemSameComponents(stackI, stackJ)) {
                        int screenI = InventorySwap.mapInventoryToScreenSlot(i);
                        int screenJ = InventorySwap.mapInventoryToScreenSlot(j);

                        InventorySwap.INSTANCE.swapScreenSlots(screenJ, screenI);
                        cooldown = delayTicks.get();
                        return;
                    }
                }
            }
        }

        // 2. Sort main inventory by item category
        if (sortInventory.get()) {
            for (int i = 9; i < 35; i++) {
                ItemStack stackI = player.getInventory().getItem(i);
                int scoreI = scoreItem(stackI);

                for (int j = i + 1; j < 36; j++) {
                    ItemStack stackJ = player.getInventory().getItem(j);
                    int scoreJ = scoreItem(stackJ);

                    if (scoreJ < scoreI && !stackJ.isEmpty()) {
                        int screenI = InventorySwap.mapInventoryToScreenSlot(i);
                        int screenJ = InventorySwap.mapInventoryToScreenSlot(j);

                        InventorySwap.INSTANCE.swapScreenSlots(screenI, screenJ);
                        cooldown = delayTicks.get();
                        return;
                    }
                }
            }
        }
    }

    private int scoreItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 999;

        // Weapons (1)
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.MACE) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) {
            return 1;
        }

        // Tools (2)
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
            return 2;
        }

        // Armor (3)
        if (stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR) || stack.is(Items.ELYTRA)) {
            return 3;
        }

        // Consumables / Totems (4)
        if (stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.EXPERIENCE_BOTTLE) || stack.getItem().components().has(net.minecraft.core.component.DataComponents.FOOD)) {
            return 4;
        }

        // Blocks / Crystals / Anchors (5)
        if (stack.is(Items.END_CRYSTAL) || stack.is(Items.RESPAWN_ANCHOR) || stack.getItem() instanceof BlockItem) {
            return 5;
        }

        // Other (6)
        return 6;
    }
}
