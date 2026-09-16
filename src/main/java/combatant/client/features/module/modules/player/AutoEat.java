/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.draggable.impl.Itemizer;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.item.FoodUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.world.ExplosionDamageUtil;

@ModuleInfo(
        id = "autoeat",
        displayName = "AutoEat",
        description = "Automatically eats optimal food or golden apples based on health and hunger, pausing during combat danger.",
        category = ModuleCategory.PLAYER
)
public class AutoEat extends Module {

    private final NumberValue<Integer> hungerThreshold =
            num("hunger_threshold", "hunger_threshold", 16, 1, 19);
    private final NumberValue<Float> healthThreshold =
            num("health_threshold", "health_threshold", 12.0f, 1.0f, 36.0f);

    private final Minecraft mc = Minecraft.getInstance();

    private boolean forcingUse = false;
    private boolean useKeyWasPressed = false;
    private int prevSelectedSlot = -1;
    private boolean restoreSelected = false;
    private int restoreInvSlot = -1;
    private int restoreHotbarSlot = -1;
    private boolean restoreMainPending = false;

    @Override
    public void onDisable() {
        if (mc.player != null) {
            stopAutoUse(mc.player);
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;

        LocalPlayer player = mc.player;

        if (player.isSpectator() || player.getAbilities().instabuild || !player.isAlive()) {
            stopAutoUse(player);
            return;
        }

        // Automatic combat safety: Pause eating if taking damage or in immediate explosion danger
        if (isTakingDamage(player) || isInExplosionDanger(player)) {
            stopAutoUse(player);
            return;
        }

        // If currently eating, continue holding key until finished
        if (player.isUsingItem()) {
            if (FoodUtil.isFood(player.getUseItem())) {
                if (forcingUse && !mc.options.keyUse.isDown()) {
                    mc.options.keyUse.setDown(true);
                }
                return;
            }
            stopAutoUse(player);
            return;
        }

        int hunger = player.getFoodData().getFoodLevel();
        float health = player.getHealth() + player.getAbsorptionAmount();
        boolean lowHealth = health <= healthThreshold.get();
        boolean hungry = hunger <= hungerThreshold.get();

        // Not hungry and health is sufficient
        if (!lowHealth && !hungry) {
            stopAutoUse(player);
            return;
        }

        // Check if offhand already has the best food
        ItemStack offhand = player.getOffhandItem();
        if (isBestHandFood(offhand, lowHealth)) {
            startUse(player, InteractionHand.OFF_HAND);
            return;
        }

        // Check if main hand already has the best food
        ItemStack mainHand = player.getMainHandItem();
        if (isBestHandFood(mainHand, lowHealth)) {
            startUse(player, InteractionHand.MAIN_HAND);
            return;
        }

        // Select the optimal food slot from inventory
        int bestSlot = findBestFoodSlot(player, lowHealth);
        if (bestSlot == -1) {
            stopAutoUse(player);
            return;
        }

        if (!ensureFoodInMainHand(player, bestSlot)) {
            stopAutoUse(player);
            return;
        }

        startUse(player, InteractionHand.MAIN_HAND);
    }

    /**
     * Determines whether the player recently took damage or is being hurt.
     */
    private boolean isTakingDamage(LocalPlayer player) {
        return player.hurtTime > 0;
    }

    /**
     * Checks for nearby active crystals that pose immediate explosion danger.
     */
    private boolean isInExplosionDanger(LocalPlayer player) {
        if (mc.level == null) return false;
        for (EndCrystal crystal : mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(8.0),
                c -> c != null && !c.isRemoved()
        )) {
            float damage = ExplosionDamageUtil.calculateCrystalDamage(crystal.position(), player);
            if (damage >= 4.0f || crystal.position().distanceTo(player.position()) <= 4.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the stack held in hand is appropriate for the current state.
     */
    private boolean isBestHandFood(ItemStack stack, boolean lowHealth) {
        if (!FoodUtil.isFood(stack)) return false;

        if (lowHealth) {
            return stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.GOLDEN_APPLE);
        } else {
            // When only hungry, prefer regular food or chorus fruit over wasting golden apples
            return !isGoldenApple(stack) || !hasRegularFood(mc.player);
        }
    }

    /**
     * Finds the best food in player inventory.
     * Prioritizes Golden Apples when health is low, regular food / chorus fruit when only hungry.
     */
    private int findBestFoodSlot(LocalPlayer player, boolean lowHealth) {
        int bestSlot = -1;
        float bestScore = Float.NEGATIVE_INFINITY;

        if (lowHealth) {
            // Prioritize Enchanted Golden Apple first
            for (int i = 0; i < 36; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                    return i;
                }
            }

            // Prioritize regular Golden Apple second
            for (int i = 0; i < 36; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(Items.GOLDEN_APPLE)) {
                    return i;
                }
            }
        }

        // Search regular foods or chorus fruit (avoiding gapples if only hungry)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!FoodUtil.isFood(stack)) continue;

            // If not low on health, avoid wasting golden apples unless no other food exists
            if (!lowHealth && isGoldenApple(stack)) {
                continue;
            }

            float score = scoreFoodItem(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        // Fallback: if only hungry and no regular food exists, allow golden apple as last resort
        if (bestSlot == -1 && !lowHealth) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (FoodUtil.isFood(stack)) {
                    return i;
                }
            }
        }

        return bestSlot;
    }

    private boolean isGoldenApple(ItemStack stack) {
        return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private boolean hasRegularFood(LocalPlayer player) {
        if (player == null) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (FoodUtil.isFood(stack) && !isGoldenApple(stack)) {
                return true;
            }
        }
        return false;
    }

    private float scoreFoodItem(ItemStack stack) {
        if (stack.is(Items.CHORUS_FRUIT)) {
            return 15.0f;
        }
        int nutrition = FoodUtil.getNutrition(stack);
        float saturation = FoodUtil.getSaturation(stack);
        return nutrition + (saturation * 2.0f);
    }

    private boolean ensureFoodInMainHand(LocalPlayer player, int bestSlot) {
        PlayerInventoryAccessor inv = (PlayerInventoryAccessor) player.getInventory();

        if (restoreMainPending) {
            inv.combatant$setSelectedSlot(restoreHotbarSlot);
            return true;
        }

        if (bestSlot < 0) return false;

        // Slot is already in hotbar (0-8)
        if (bestSlot < 9) {
            int selected = inv.combatant$getSelectedSlot();
            if (selected != bestSlot) {
                prevSelectedSlot = selected;
                restoreSelected = true;
                inv.combatant$setSelectedSlot(bestSlot);
            }
            return true;
        }

        // Slot is in main inventory (9-35), swap into hotbar
        int targetHotbar = findEmptyHotbarSlot(player);
        int selected = inv.combatant$getSelectedSlot();
        if (targetHotbar == -1) targetHotbar = selected;

        if (targetHotbar != selected) {
            prevSelectedSlot = selected;
            restoreSelected = true;
        }
        inv.combatant$setSelectedSlot(targetHotbar);

        if (player.inventoryMenu == null) return false;

        restoreInvSlot = bestSlot;
        restoreHotbarSlot = targetHotbar;
        restoreMainPending = true;

        InventorySwap.INSTANCE.swapScreenSlots(
                InventorySwap.mapInventoryToScreenSlot(bestSlot),
                InventorySwap.mapHotbarToScreenSlot(targetHotbar)
        );
        return true;
    }

    private int findEmptyHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private void startUse(LocalPlayer player, InteractionHand hand) {
        if (!forcingUse) {
            useKeyWasPressed = mc.options.keyUse.isDown();
        }

        if (!mc.options.keyUse.isDown()) {
            mc.options.keyUse.setDown(true);
        }
        forcingUse = true;

        ItemStack used = hand == InteractionHand.OFF_HAND
                ? player.getOffhandItem().copy()
                : player.getMainHandItem().copy();
        InteractionResult r = mc.gameMode.useItem(player, hand);
        if (r.consumesAction()) {
            player.swing(hand);
            Itemizer.showAutoEat(used);
        }
    }

    private void stopAutoUse(LocalPlayer player) {
        if (forcingUse && !useKeyWasPressed) {
            mc.options.keyUse.setDown(false);
        }
        forcingUse = false;

        if (restoreMainPending) {
            restoreMainPending = false;
            restoreMainSwap(player);
        }

        if (restoreSelected) {
            restoreSelected = false;
            if (prevSelectedSlot >= 0 && prevSelectedSlot < 9) {
                ((PlayerInventoryAccessor) player.getInventory()).combatant$setSelectedSlot(prevSelectedSlot);
            }
            prevSelectedSlot = -1;
        }
    }

    private void restoreMainSwap(LocalPlayer player) {
        if (restoreInvSlot < 0 || restoreHotbarSlot < 0) return;
        if (player.inventoryMenu == null) return;

        InventorySwap.INSTANCE.swapScreenSlots(
                InventorySwap.mapInventoryToScreenSlot(restoreInvSlot),
                InventorySwap.mapHotbarToScreenSlot(restoreHotbarSlot)
        );

        restoreInvSlot = -1;
        restoreHotbarSlot = -1;
    }
}
