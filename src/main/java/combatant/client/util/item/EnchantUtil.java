/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
/**
 * Shared helpers for enchantment validation/formatting and DataComponents inspection.
 */
public enum EnchantUtil {
    ;

    /**
     * Checks if the provided enchantment level exceeds its max level.
     */
    public static boolean isInvalidLevel(Holder<Enchantment> enchant, int level) {
        if (enchant == null || level <= 0) return false;
        Enchantment value = enchant.value();
        int max = value.getMaxLevel();
        return level > max;
    }

    /**
     * Extracts enchantment level from DataComponents.ENCHANTMENTS (or DataComponents.STORED_ENCHANTMENTS
     * for enchanted books). Iterates itemEnchantments.entrySet(), matching entry.getKey().is(key).
     *
     * @param key the enchantment resource key
     * @param stack the item stack to inspect
     * @return the enchantment level, or 0 if not present or item is empty
     */
    public static int getLevel(ResourceKey<Enchantment> key, ItemStack stack) {
        if (key == null || stack == null || stack.isEmpty()) {
            return 0;
        }

        ItemEnchantments enchantments = stack.is(Items.ENCHANTED_BOOK)
                ? stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        if (enchantments.isEmpty()) {
            enchantments = stack.is(Items.ENCHANTED_BOOK)
                    ? stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                    : stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        }

        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(key)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    /**
     * Checks if the item stack has the given enchantment.
     *
     * @param key the enchantment resource key
     * @param stack the item stack to inspect
     * @return true if the enchantment level is greater than 0
     */
    public static boolean hasEnchantment(ResourceKey<Enchantment> key, ItemStack stack) {
        return getLevel(key, stack) > 0;
    }

    /**
     * Gets the Protection level on the stack.
     *
     * @param stack the item stack to inspect
     * @return Protection level or 0
     */
    public static int getProtectionLevel(ItemStack stack) {
        return getLevel(Enchantments.PROTECTION, stack);
    }

    /**
     * Gets the Blast Protection level on the stack.
     *
     * @param stack the item stack to inspect
     * @return Blast Protection level or 0
     */
    public static int getBlastProtectionLevel(ItemStack stack) {
        return getLevel(Enchantments.BLAST_PROTECTION, stack);
    }

    /**
     * Gets the Feather Falling level on the stack.
     *
     * @param stack the item stack to inspect
     * @return Feather Falling level or 0
     */
    public static int getFeatherFallingLevel(ItemStack stack) {
        return getLevel(Enchantments.FEATHER_FALLING, stack);
    }

    /**
     * Gets the Sharpness level on the stack.
     *
     * @param stack the item stack to inspect
     * @return Sharpness level or 0
     */
    public static int getSharpnessLevel(ItemStack stack) {
        return getLevel(Enchantments.SHARPNESS, stack);
    }

    /**
     * Gets the Efficiency level on the stack.
     *
     * @param stack the item stack to inspect
     * @return Efficiency level or 0
     */
    public static int getEfficiencyLevel(ItemStack stack) {
        return getLevel(Enchantments.EFFICIENCY, stack);
    }

    /**
     * Checks if the item stack has Mending.
     *
     * @param stack the item stack to inspect
     * @return true if Mending is present
     */
    public static boolean hasMending(ItemStack stack) {
        return hasEnchantment(Enchantments.MENDING, stack);
    }

    /**
     * Checks if the item stack has Silk Touch.
     *
     * @param stack the item stack to inspect
     * @return true if Silk Touch is present
     */
    public static boolean hasSilkTouch(ItemStack stack) {
        return hasEnchantment(Enchantments.SILK_TOUCH, stack);
    }

    /**
     * Checks if the item stack has Fortune.
     *
     * @param stack the item stack to inspect
     * @return true if Fortune is present
     */
    public static boolean hasFortune(ItemStack stack) {
        return hasEnchantment(Enchantments.FORTUNE, stack);
    }

    /**
     * Checks if the item stack has Infinity.
     *
     * @param stack the item stack to inspect
     * @return true if Infinity is present
     */
    public static boolean hasInfinity(ItemStack stack) {
        return hasEnchantment(Enchantments.INFINITY, stack);
    }
}
