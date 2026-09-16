/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.MaceItem;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.draggable.impl.Itemizer;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.inventory.InventorySwap;

import java.util.function.Predicate;

@ModuleInfo(
        id = "offhand",
        displayName = "Offhand",
        aliases = {"autototem", "totem"},
        category = ModuleCategory.PLAYER,
        description = "Automatically manages offhand items, equipping totems, golden apples, crystals, or shields based on context."
)
public class Offhand extends Module {

    public enum Mode {
        TOTEM,
        GAPPLE,
        CRYSTAL,
        SHIELD
    }

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode =
            enumSetting("offhandMode", "mode", Mode.TOTEM, Mode.values());

    private final NumberValue<Float> healthThreshold =
            numCommon(
                    "offhand_threshold",
                    "health_threshold",
                    CommonSettingSchemas.PLAYER_HEALTH_THRESHOLD,
                    10.0f,
                    1.0f,
                    40.0f
            );

    private final NumberValue<Float> elytraHealth =
            numCommon(
                    "offhand_elytra_health",
                    "elytra_health",
                    CommonSettingSchemas.PLAYER_ELYTRA_HEALTH,
                    8.5f,
                    1.0f,
                    40.0f
            );

    private final NumberValue<Float> crystalDistance =
            numCommon(
                    "offhand_crystal_distance",
                    "crystal_distance",
                    CommonSettingSchemas.COMBAT_CRYSTAL_DISTANCE,
                    4.0f,
                    1.0f,
                    10.0f
            );

    private final BooleanValue fallCheck =
            boolCommon(
                    "offhand_fall_check",
                    "fall_check",
                    CommonSettingSchemas.PLAYER_FALL_CHECK,
                    true
            );

    private final BooleanValue lethalCheck =
            bool("offhandLethalCheck", "lethal_check", true);

    private final BooleanValue rightClickGapple =
            bool("offhandRightClickGapple", "rc_gapple", true);

    private final BooleanValue swordGapple =
            bool("offhandSwordGapple", "sword_gapple", true);

    private final BooleanValue rightClickCrystal =
            bool("offhandRightClickCrystal", "rc_crystal", false);

    private final BooleanValue saveTaliks =
            boolCommon(
                    "offhand_save_taliks",
                    "save_taliks",
                    CommonSettingSchemas.ITEMS_SAVE_UNENCHANTED,
                    true
            );

    private final BooleanValue returnItem =
            boolCommon(
                    "offhand_return_item",
                    "return_item",
                    CommonSettingSchemas.INVENTORY_RESTORE_ITEM,
                    true
            );

    private ItemStack previousOffhand = ItemStack.EMPTY;
    private int previousOffhandSlot = -1;
    private boolean usingTotemOverride = false;
    private boolean offhandSwapPending = false;

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        if (mc.player == null || mc.level == null || mc.isPaused()) {
            offhandSwapPending = false;
            return;
        }

        if (offhandSwapPending) {
            return;
        }

        LocalPlayer player = mc.player;
        float health = player.getHealth() + player.getAbsorptionAmount();

        // 1. Critical safety override: Totem
        boolean needTotem = shouldHoldTotem(player, health);
        if (needTotem) {
            if (!isHoldingItem(player, Items.TOTEM_OF_UNDYING)) {
                equipItem(player, Items.TOTEM_OF_UNDYING, true);
            }
            return;
        }

        // If emergency totem override ended and returnItem is true
        if (usingTotemOverride && returnItem.get()) {
            restorePrevious(player);
            return;
        }

        // 2. Right click context triggers
        if (shouldHoldGappleContext(player)) {
            if (!isHoldingGapple(player)) {
                equipGapple(player, false);
            }
            return;
        }

        if (shouldHoldCrystalContext(player)) {
            if (!isHoldingItem(player, Items.END_CRYSTAL)) {
                equipItem(player, Items.END_CRYSTAL, false);
            }
            return;
        }

        // 3. Default Mode item
        switch (mode.get()) {
            case TOTEM -> {
                if (!isHoldingItem(player, Items.TOTEM_OF_UNDYING)) {
                    equipItem(player, Items.TOTEM_OF_UNDYING, false);
                }
            }
            case GAPPLE -> {
                if (!isHoldingGapple(player)) {
                    equipGapple(player, false);
                }
            }
            case CRYSTAL -> {
                if (!isHoldingItem(player, Items.END_CRYSTAL)) {
                    equipItem(player, Items.END_CRYSTAL, false);
                }
            }
            case SHIELD -> {
                if (!isHoldingItem(player, Items.SHIELD)) {
                    equipItem(player, Items.SHIELD, false);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        offhandSwapPending = false;
        clearRestoreState();
    }

    private boolean shouldHoldTotem(LocalPlayer player, float health) {
        if (player.isFallFlying() && health <= elytraHealth.get()) {
            return true;
        }

        if (health <= healthThreshold.get()) {
            return true;
        }

        if (fallCheck.get() && (player.fallDistance > 10.0f || (player.fallDistance > 3.0f && health <= 14.0f))) {
            return true;
        }

        double closestCrystal = getClosestCrystalDistance(player);
        if (closestCrystal <= crystalDistance.get()) {
            return true;
        }

        if (lethalCheck.get() && closestCrystal <= 6.0 && health <= 12.0f) {
            return true;
        }

        return false;
    }

    private boolean shouldHoldGappleContext(LocalPlayer player) {
        if (!rightClickGapple.get() && !swordGapple.get()) return false;
        if (mc.options == null || !mc.options.keyUse.isDown()) return false;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()) return false;

        if (swordGapple.get() && mainHand.is(ItemTags.SWORDS)) {
            return true;
        }

        if (rightClickGapple.get()) {
            return mainHand.is(ItemTags.SWORDS) || mainHand.is(ItemTags.AXES)
                    || mainHand.is(ItemTags.PICKAXES) || mainHand.getItem() instanceof MaceItem;
        }

        return false;
    }

    private boolean shouldHoldCrystalContext(LocalPlayer player) {
        if (!rightClickCrystal.get()) return false;
        if (mc.options == null || !mc.options.keyUse.isDown()) return false;

        ItemStack mainHand = player.getMainHandItem();
        return !mainHand.isEmpty() && mainHand.is(Items.OBSIDIAN);
    }

    public boolean shouldHoldTotemNow(LocalPlayer player) {
        if (!isEnabled() || player == null) return false;
        return shouldHoldTotem(player, player.getHealth() + player.getAbsorptionAmount());
    }

    public boolean canProvideTotemNow(LocalPlayer player) {
        if (!isEnabled()) return false;
        if (player == null || mc.gameMode == null) return false;
        if (isHoldingItem(player, Items.TOTEM_OF_UNDYING)) return true;
        if (offhandSwapPending) return false;
        return findTotemSlot(player) != -1;
    }

    public boolean ensureTotemForDanger(LocalPlayer player) {
        if (!canProvideTotemNow(player)) return false;
        if (isHoldingItem(player, Items.TOTEM_OF_UNDYING)) return true;

        int slot = findTotemSlot(player);
        if (slot == -1) return false;

        rememberPreviousOffhand(player, slot, true);
        return swapToOffhand(player, slot, true);
    }

    public boolean isTotemSwapPending() {
        return offhandSwapPending;
    }

    private void equipItem(LocalPlayer player, Item item, boolean isTotemOverride) {
        if (player == null || mc.gameMode == null) return;
        int slot = item == Items.TOTEM_OF_UNDYING ? findTotemSlot(player) : find(player, item);
        if (slot == -1) return;

        rememberPreviousOffhand(player, slot, isTotemOverride);
        swapToOffhand(player, slot, isTotemOverride);
    }

    private void equipGapple(LocalPlayer player, boolean isTotemOverride) {
        if (player == null || mc.gameMode == null) return;
        int slot = find(player, stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE));
        if (slot == -1) {
            slot = find(player, stack -> stack.is(Items.GOLDEN_APPLE));
        }
        if (slot == -1) return;

        rememberPreviousOffhand(player, slot, isTotemOverride);
        swapToOffhand(player, slot, isTotemOverride);
    }

    private boolean swapToOffhand(LocalPlayer player, int slot, boolean isTotemOverride) {
        if (player == null || mc.gameMode == null || offhandSwapPending) return false;

        offhandSwapPending = true;
        ItemStack displayStack = player.getInventory().getItem(slot).copy();
        boolean accepted = InventorySwap.INSTANCE.swapInventoryToOffhand(slot, () -> {
            if (isTotemOverride) {
                usingTotemOverride = true;
            }
            offhandSwapPending = false;
            Itemizer.showAutoTotem(displayStack);
        });
        if (!accepted) {
            offhandSwapPending = false;
        }
        return accepted;
    }

    private void restorePrevious(LocalPlayer player) {
        if (player == null || mc.gameMode == null) return;

        if (previousOffhand.isEmpty()) {
            clearRestoreState();
            return;
        }

        if (!player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            clearRestoreState();
            return;
        }

        int restoreSlot = resolveRestoreSlot(player);
        if (restoreSlot == -1) {
            clearRestoreState();
            return;
        }

        requestOffhandSwap(restoreSlot, this::clearRestoreState);
    }

    private void rememberPreviousOffhand(LocalPlayer player, int newSlot, boolean isTotemOverride) {
        if (player == null) return;
        if (usingTotemOverride) return;

        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            previousOffhand = ItemStack.EMPTY;
            previousOffhandSlot = -1;
            return;
        }

        previousOffhand = offhand.copy();
        previousOffhandSlot = newSlot;
    }

    private boolean requestOffhandSwap(int slot, Runnable afterSwap) {
        if (offhandSwapPending) return false;

        offhandSwapPending = true;
        boolean accepted = InventorySwap.INSTANCE.swapInventoryToOffhand(slot, () -> {
            if (afterSwap != null) {
                afterSwap.run();
            }
            offhandSwapPending = false;
        });
        if (!accepted) {
            offhandSwapPending = false;
        }
        return accepted;
    }

    private int findTotemSlot(LocalPlayer player) {
        if (saveTaliks.get()) {
            int nonEnchanted = find(player, stack ->
                    stack.is(Items.TOTEM_OF_UNDYING) && !stack.isEnchanted()
            );

            if (nonEnchanted != -1) {
                return nonEnchanted;
            }
        }

        return find(player, Items.TOTEM_OF_UNDYING);
    }

    private int find(LocalPlayer player, Item item) {
        return find(player, stack -> stack.is(item));
    }

    private int find(LocalPlayer player, Predicate<ItemStack> predicate) {
        if (player == null || predicate == null) return -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return i;
            }
        }

        return -1;
    }

    private int findItem(LocalPlayer player, ItemStack target) {
        if (player == null || target == null || target.isEmpty()) return -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, target)) {
                return i;
            }
        }

        return -1;
    }

    private int resolveRestoreSlot(LocalPlayer player) {
        if (previousOffhandSlot >= 0 && previousOffhandSlot < 36) {
            ItemStack stack = player.getInventory().getItem(previousOffhandSlot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, previousOffhand)) {
                return previousOffhandSlot;
            }
        }

        return findItem(player, previousOffhand);
    }

    private double getClosestCrystalDistance(LocalPlayer player) {
        if (player == null || mc.level == null) {
            return Double.MAX_VALUE;
        }

        double minDist = Double.MAX_VALUE;
        double range = crystalDistance.get();

        for (EndCrystal crystal : mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(range),
                crystal -> crystal != null && !crystal.isRemoved()
        )) {
            double dist = player.position().distanceTo(crystal.position());
            if (dist < minDist) {
                minDist = dist;
            }
        }

        return minDist;
    }

    private boolean isHoldingItem(LocalPlayer player, Item item) {
        return player != null && player.getOffhandItem().is(item);
    }

    private boolean isHoldingGapple(LocalPlayer player) {
        if (player == null) return false;
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(Items.GOLDEN_APPLE) || offhand.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private void clearRestoreState() {
        previousOffhand = ItemStack.EMPTY;
        previousOffhandSlot = -1;
        usingTotemOverride = false;
    }
}
