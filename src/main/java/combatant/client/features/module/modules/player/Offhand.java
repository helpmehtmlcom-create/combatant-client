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
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.draggable.impl.Itemizer;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.manager.InventoryManager;
import combatant.client.util.player.inventory.manager.InventoryPriority;
import combatant.client.util.world.ExplosionDamageUtil;

import java.util.function.Predicate;

@ModuleInfo(
        id = "offhand",
        displayName = "Offhand",
        aliases = {"autototem", "totem"},
        category = ModuleCategory.PLAYER,
        description = "Manages offhand items with guaranteed critical-priority totem swaps during danger or low health."
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
            num("health_threshold", "health_threshold", 10.0f, 1.0f, 36.0f);

    private boolean offhandSwapPending = false;

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null || mc.isPaused() || !mc.player.isAlive()) {
            offhandSwapPending = false;
            return;
        }

        // Process inventory transactions
        InventoryManager.INSTANCE.tick();

        if (offhandSwapPending && InventoryManager.INSTANCE.hasPending(InventoryPriority.CRITICAL)) {
            return;
        }
        offhandSwapPending = false;

        LocalPlayer player = mc.player;
        float health = player.getHealth() + player.getAbsorptionAmount();

        // 1. Critical safety override: Totem during low health or combat danger
        boolean needTotem = shouldHoldTotem(player, health);
        if (needTotem) {
            if (!isHoldingItem(player, Items.TOTEM_OF_UNDYING)) {
                equipTotemCritical(player);
            }
            return;
        }

        // 2. Normal mode item
        switch (mode.get()) {
            case TOTEM -> {
                if (!isHoldingItem(player, Items.TOTEM_OF_UNDYING)) {
                    equipTotemCritical(player);
                }
            }
            case GAPPLE -> {
                if (!isHoldingGapple(player)) {
                    equipGapple(player);
                }
            }
            case CRYSTAL -> {
                if (!isHoldingItem(player, Items.END_CRYSTAL)) {
                    equipItemNormal(player, Items.END_CRYSTAL);
                }
            }
            case SHIELD -> {
                if (!isHoldingItem(player, Items.SHIELD)) {
                    equipItemNormal(player, Items.SHIELD);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        offhandSwapPending = false;
        InventoryManager.INSTANCE.clear(this);
    }

    /**
     * Determines whether player requires a Totem due to health threshold, lethal crystal danger, or fall danger.
     */
    private boolean shouldHoldTotem(LocalPlayer player, float health) {
        if (health <= healthThreshold.get()) {
            return true;
        }

        // Fall danger safety
        if (player.fallDistance > 10.0f || (player.fallDistance > 3.0f && health <= 14.0f)
                || (player.isFallFlying() && health <= 10.0f)) {
            return true;
        }

        // Crystal danger check: nearby crystals with lethal or high burst damage
        for (EndCrystal crystal : mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(8.0),
                c -> c != null && !c.isRemoved()
        )) {
            float crystalDist = (float) player.position().distanceTo(crystal.position());
            if (crystalDist <= 4.0f) {
                return true;
            }
            float damage = ExplosionDamageUtil.calculateCrystalDamage(crystal.position(), player);
            if (damage >= health - 2.0f || damage >= 10.0f) {
                return true;
            }
        }

        return false;
    }

    public boolean shouldHoldTotemNow(LocalPlayer player) {
        if (!isEnabled() || player == null) return false;
        return shouldHoldTotem(player, player.getHealth() + player.getAbsorptionAmount());
    }

    public boolean canProvideTotemNow(LocalPlayer player) {
        if (!isEnabled() || player == null) return false;
        if (isHoldingItem(player, Items.TOTEM_OF_UNDYING)) return true;
        return findTotemSlot(player) != -1;
    }

    public boolean ensureTotemForDanger(LocalPlayer player) {
        if (!canProvideTotemNow(player)) return false;
        if (isHoldingItem(player, Items.TOTEM_OF_UNDYING)) return true;
        return equipTotemCritical(player);
    }

    public boolean isTotemSwapPending() {
        return offhandSwapPending || InventoryManager.INSTANCE.hasPending(InventoryPriority.CRITICAL);
    }

    private boolean equipTotemCritical(LocalPlayer player) {
        int slot = findTotemSlot(player);
        if (slot == -1) return false;

        int screenSlot = InventorySwap.mapInventoryToScreenSlot(slot);
        if (screenSlot == -1) return false;

        offhandSwapPending = true;
        ItemStack stack = player.getInventory().getItem(slot).copy();
        InventoryManager.INSTANCE.submitHotbarSwap(InventoryPriority.CRITICAL, screenSlot, 40, this);
        Itemizer.showAutoTotem(stack);
        return true;
    }

    private void equipGapple(LocalPlayer player) {
        int slot = find(player, stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE));
        if (slot == -1) {
            slot = find(player, stack -> stack.is(Items.GOLDEN_APPLE));
        }
        if (slot == -1) return;

        int screenSlot = InventorySwap.mapInventoryToScreenSlot(slot);
        if (screenSlot != -1) {
            InventoryManager.INSTANCE.submitHotbarSwap(InventoryPriority.NORMAL, screenSlot, 40, this);
        }
    }

    private void equipItemNormal(LocalPlayer player, Item item) {
        int slot = find(player, item);
        if (slot == -1) return;

        int screenSlot = InventorySwap.mapInventoryToScreenSlot(slot);
        if (screenSlot != -1) {
            InventoryManager.INSTANCE.submitHotbarSwap(InventoryPriority.NORMAL, screenSlot, 40, this);
        }
    }

    private int findTotemSlot(LocalPlayer player) {
        // Prioritize non-enchanted totems first to save custom enchanted talismans/totems
        int nonEnchanted = find(player, stack -> stack.is(Items.TOTEM_OF_UNDYING) && !stack.isEnchanted());
        if (nonEnchanted != -1) {
            return nonEnchanted;
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

    private boolean isHoldingItem(LocalPlayer player, Item item) {
        return player != null && player.getOffhandItem().is(item);
    }

    private boolean isHoldingGapple(LocalPlayer player) {
        if (player == null) return false;
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(Items.GOLDEN_APPLE) || offhand.is(Items.ENCHANTED_GOLDEN_APPLE);
    }
}
