/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import java.util.Collections;
import java.util.List;

public enum FoodUtil {
    ;

    public static boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.get(DataComponents.FOOD) != null;
    }

    public static int getFoodLevel(Player player) {
        if (player == null) return 0;
        FoodData hungerManager = player.getFoodData();
        if (hungerManager == null) return 0;
        return hungerManager.getFoodLevel();
    }

    public static float getSaturationLevel(Player player) {
        if (player == null) return 0f;
        FoodData hungerManager = player.getFoodData();
        if (hungerManager == null) return 0f;
        return hungerManager.getSaturationLevel();
    }

    /**
     * Returns the nutrition value of the given food stack, or 0 if not food.
     */
    public static int getNutrition(ItemStack stack) {
        FoodProperties fc = getFoodComponent(stack);
        return fc != null ? fc.nutrition() : 0;
    }

    /**
     * Returns the saturation modifier of the given food stack, or 0.0 if not food.
     */
    public static float getSaturation(ItemStack stack) {
        FoodProperties fc = getFoodComponent(stack);
        return fc != null ? fc.saturation() : 0f;
    }

    /**
     * Returns whether the food stack can be consumed even when the player is not hungry.
     */
    public static boolean canAlwaysEat(ItemStack stack) {
        FoodProperties fc = getFoodComponent(stack);
        return fc != null && fc.canAlwaysEat();
    }

    public static float scoreFood(ItemStack stack) {
        if (!isFood(stack)) return Float.NEGATIVE_INFINITY;
        int nutrition = getNutrition(stack);
        float saturation = getSaturation(stack);
        float score = nutrition * 10.0f + saturation * 20.0f;
        score += scoreEffects(stack);
        return score;
    }

    public static List<MobEffectInstance> getFoodEffects(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        List<MobEffectInstance> out = new java.util.ArrayList<>();
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) return Collections.emptyList();
        for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (effect instanceof ApplyStatusEffectsConsumeEffect apply) {
                for (MobEffectInstance inst : apply.effects()) {
                    out.add(new MobEffectInstance(inst));
                }
            }
        }
        return out;
    }

    private static float scoreEffects(ItemStack stack) {
        List<MobEffectInstance> effects = getFoodEffects(stack);
        if (effects.isEmpty()) return 0f;

        float score = 0f;
        for (MobEffectInstance inst : effects) {
            if (inst == null) continue;
            Holder<MobEffect> holder = inst.getEffect();
            if (holder == null) continue;

            int amp = inst.getAmplifier() + 1;
            if (holder.equals(MobEffects.POISON) || holder.value() == MobEffects.POISON.value()
                    || holder.equals(MobEffects.WITHER) || holder.value() == MobEffects.WITHER.value()
                    || holder.equals(MobEffects.SLOWNESS) || holder.value() == MobEffects.SLOWNESS.value()
                    || holder.equals(MobEffects.HUNGER) || holder.value() == MobEffects.HUNGER.value()) {
                score -= 40.0f * amp;
                continue;
            }

            MobEffect effect = holder.value();
            if (effect != null) {
                MobEffectCategory cat = effect.getCategory();
                float add = switch (cat) {
                    case BENEFICIAL -> 15.0f * amp;
                    case HARMFUL -> -30.0f * amp;
                    default -> 2.0f;
                };
                score += add;
            } else {
                score += 2.0f;
            }
        }
        return score;
    }

    /**
     * Returns the eat duration in ticks for the specified stack.
     * <p>
     * If the stack has {@link DataComponents#CONSUMABLE}, reads {@link Consumable#consumeSeconds()}
     * and converts it to ticks. Falls back to 32 ticks (standard food duration) if not consumable
     * or non-positive.
     */
    public static int getEatDuration(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            Consumable consumable = stack.get(DataComponents.CONSUMABLE);
            if (consumable != null) {
                float seconds = consumable.consumeSeconds();
                if (seconds > 0.0f) {
                    return (int) Math.ceil(seconds * 20.0f);
                }
            }
        }
        return 32;
    }

    /**
     * Returns whether the given stack is a regular or enchanted golden apple.
     */
    public static boolean isGoldenApple(ItemStack stack) {
        return stack != null && (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE));
    }

    /**
     * Returns whether the given stack is an enchanted golden apple.
     */
    public static boolean isEnchantedGoldenApple(ItemStack stack) {
        return stack != null && stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private static FoodProperties getFoodComponent(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.get(DataComponents.FOOD);
    }
}
