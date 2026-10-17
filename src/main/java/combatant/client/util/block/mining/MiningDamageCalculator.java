/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MiningDamageCalculator {

    private MiningDamageCalculator() {
    }

    /**
     * Calculates the break progress per tick for a given player, tool stack, block state, and position.
     * Accurately factors in:
     * - Base tool destroy speed
     * - Efficiency enchantment levels
     * - Haste / Conduit Power effects
     * - Mining Fatigue effects
     * - Submerged in water & Aqua Affinity
     * - On ground / airborne status
     * - Tool harvest capability vs hardness
     */
    public static float calculateDestroyProgress(Player player, ItemStack tool, BlockState state, BlockPos pos) {
        if (player == null || state == null || state.isAir()) return 0.0f;
        Level level = player.level();
        if (level == null) return 0.0f;

        float hardness = state.getDestroySpeed(level, pos);
        if (hardness == -1.0f) {
            return 0.0f; // Unbreakable (Bedrock, Barrier, End Portal)
        }
        if (hardness == 0.0f) {
            return 1.0f; // Instant break (Torches, flowers, etc.)
        }

        ItemStack effectiveTool = (tool != null && !tool.isEmpty()) ? tool : ItemStack.EMPTY;
        float speed = getEffectiveDestroySpeed(player, effectiveTool, state);

        boolean canHarvest = canHarvestBlock(player, effectiveTool, state);
        int divider = canHarvest ? 30 : 100;

        return speed / hardness / (float) divider;
    }

    /**
     * Computes the effective mining speed of a tool against a block state, taking into account
     * player status effects, enchantments, and physical environment.
     */
    public static float getEffectiveDestroySpeed(Player player, ItemStack tool, BlockState state) {
        float speed = !tool.isEmpty() ? tool.getDestroySpeed(state) : 1.0f;

        // Efficiency enchantment bonus (only applies if base speed > 1.0f)
        if (speed > 1.0f) {
            int efficiency = getEfficiencyLevel(tool, player.level());
            if (efficiency > 0) {
                speed += (float) (efficiency * efficiency + 1);
            }
        }

        // Haste / Conduit Power (each level adds +20%)
        MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
        MobEffectInstance conduit = player.getEffect(MobEffects.CONDUIT_POWER);
        int hasteLevel = -1;
        if (haste != null) {
            hasteLevel = Math.max(hasteLevel, haste.getAmplifier());
        }
        if (conduit != null) {
            hasteLevel = Math.max(hasteLevel, conduit.getAmplifier());
        }
        if (hasteLevel >= 0) {
            speed *= 1.0f + (float) (hasteLevel + 1) * 0.2f;
        }

        // Mining Fatigue
        MobEffectInstance fatigue = player.getEffect(MobEffects.MINING_FATIGUE);
        if (fatigue != null) {
            float fatigueMultiplier = switch (fatigue.getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
            speed *= fatigueMultiplier;
        }

        // Underwater penalty
        if (player.isEyeInFluid(FluidTags.WATER) && !hasAquaAffinity(player)) {
            speed /= 5.0f;
        }

        // Airborne penalty
        if (!player.onGround()) {
            speed /= 5.0f;
        }

        return Math.max(0.0f, speed);
    }

    /**
     * Determines whether the given tool can harvest drops for the block state.
     */
    public static boolean canHarvestBlock(Player player, ItemStack tool, BlockState state) {
        if (!state.requiresCorrectToolForDrops()) {
            return true;
        }
        if (tool == null || tool.isEmpty()) {
            return false;
        }
        return tool.isCorrectToolForDrops(state);
    }

    /**
     * Calculates the estimated number of ticks to break the block.
     * Returns 0 for instant break.
     */
    public static int calculateTicksToBreak(Player player, ItemStack tool, BlockState state, BlockPos pos) {
        float progressPerTick = calculateDestroyProgress(player, tool, state, pos);
        if (progressPerTick >= 1.0f) {
            return 0;
        }
        if (progressPerTick <= 0.0f) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(1.0f / progressPerTick);
    }

    /**
     * Finds the best hotbar tool (0..8) to break the specified block, evaluated by highest progress per tick.
     * Returns -1 if no tool is better than bare hands or if pos is air/unbreakable.
     */
    public static int findBestHotbarTool(Player player, BlockState state, BlockPos pos) {
        if (player == null || state == null || state.isAir() || pos == null) return -1;
        if (state.getBlock() == Blocks.BEDROCK) return -1;

        int bestSlot = -1;
        float bestProgress = calculateDestroyProgress(player, ItemStack.EMPTY, state, pos);

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) continue;

            float progress = calculateDestroyProgress(player, stack, state, pos);
            if (progress > bestProgress) {
                bestProgress = progress;
                bestSlot = slot;
            }
        }

        // If no tool is better than bare hand, use currently selected slot if valid or 0
        if (bestSlot == -1) {
            bestSlot = player.getInventory().getSelectedSlot();
        }
        return bestSlot;
    }

    /**
     * Finds the best tool in the entire inventory (0..35) to break the specified block.
     */
    public static int findBestInventoryTool(Player player, BlockState state, BlockPos pos) {
        if (player == null || state == null || state.isAir() || pos == null) return -1;
        if (state.getBlock() == Blocks.BEDROCK) return -1;

        int bestSlot = -1;
        float bestProgress = calculateDestroyProgress(player, ItemStack.EMPTY, state, pos);

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) continue;

            float progress = calculateDestroyProgress(player, stack, state, pos);
            if (progress > bestProgress) {
                bestProgress = progress;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    /**
     * Extracts the efficiency level of the tool.
     */
    public static int getEfficiencyLevel(ItemStack tool, Level level) {
        if (tool == null || tool.isEmpty()) return 0;

        if (level != null) {
            try {
                Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                Enchantment eff = registry.getValue(Enchantments.EFFICIENCY);
                if (eff != null) {
                    Holder<Enchantment> holder = registry.wrapAsHolder(eff);
                    int lvl = EnchantmentHelper.getItemEnchantmentLevel(holder, tool);
                    if (lvl > 0) return lvl;
                }
            } catch (Exception ignored) {
            }
        }

        ItemEnchantments enchantments = tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.unwrapKey().isPresent()) {
                ResourceKey<Enchantment> key = holder.unwrapKey().get();
                if (key.equals(Enchantments.EFFICIENCY) || key.identifier().getPath().contains("efficiency")) {
                    return entry.getIntValue();
                }
            }
        }
        return 0;
    }

    /**
     * Checks if the player has Aqua Affinity (from helmet or other armor).
     */
    public static boolean hasAquaAffinity(Player player) {
        if (player == null) return false;
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) return false;

        Level level = player.level();
        if (level != null) {
            try {
                Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                Enchantment aqua = registry.getValue(Enchantments.AQUA_AFFINITY);
                if (aqua != null) {
                    Holder<Enchantment> holder = registry.wrapAsHolder(aqua);
                    if (EnchantmentHelper.getItemEnchantmentLevel(holder, helmet) > 0) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        ItemEnchantments enchantments = helmet.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.unwrapKey().isPresent()) {
                ResourceKey<Enchantment> key = holder.unwrapKey().get();
                if (key.equals(Enchantments.AQUA_AFFINITY) || key.identifier().getPath().contains("aqua_affinity")) {
                    return true;
                }
            }
        }
        return false;
    }
}
