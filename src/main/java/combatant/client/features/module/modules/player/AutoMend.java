/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.SlotResult;

@ModuleInfo(
        id = "automend",
        displayName = "AutoMend",
        description = "Automatically scans armor and held items for Mending, silently throwing experience bottles until fully repaired.",
        category = ModuleCategory.PLAYER
)
public final class AutoMend extends Module {

    private static final EquipmentSlot[] SCAN_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
    };

    private final NumberValue<Integer> repairThreshold =
            num("repair_threshold", "repair_threshold", 90, 10, 100);
    private final BooleanValue silentRotation =
            bool("automendSilentRotation", "silent_rotation", true);

    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onDisable() {
        InventorySwap.INSTANCE.releaseHotbar(this);
        RotationManager.INSTANCE.release(this);
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return;
        LocalPlayer player = mc.player;

        boolean needsRepair = false;
        boolean allFullDurability = true;

        for (EquipmentSlot slot : SCAN_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem() || !hasMending(stack)) continue;

            int maxDamage = stack.getMaxDamage();
            int currentDamage = stack.getDamageValue();
            if (currentDamage > 0) {
                allFullDurability = false;
                double duraPct = 100.0 * (maxDamage - currentDamage) / (double) maxDamage;
                if (duraPct < repairThreshold.get()) {
                    needsRepair = true;
                }
            }
        }

        // Automatically stop throwing when all equipment reaches full durability or meets threshold
        if (!needsRepair || allFullDurability) {
            InventorySwap.INSTANCE.releaseHotbar(this);
            return;
        }
        if (silentRotation.get()) {
            Rotation rot = new Rotation(player.getYRot(), 90.0f, false);
            RotationManager.INSTANCE.snapServerRotation(rot, 45, this, 1);
        }

        // Check if offhand already has exp bottles
        if (player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE)) {
            mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
            player.swing(InteractionHand.OFF_HAND);
            return;
        }
        // Find exp bottle in hotbar or inventory
        SlotResult hotbarExp = InventorySwap.INSTANCE.findHotbar(s -> s.is(Items.EXPERIENCE_BOTTLE));
        if (!hotbarExp.found()) {
            InventorySwap.INSTANCE.releaseHotbar(this);
            return;
        }

        // Silently lease hotbar slot and throw
        boolean leased = InventorySwap.INSTANCE.leaseHotbar(this, hotbarExp.slot(), 1);
        if (leased) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private boolean hasMending(ItemStack stack) {
        if (mc.level == null || stack == null || stack.isEmpty()) return false;
        Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment mending = registry.getValue(Enchantments.MENDING);
        if (mending == null) return false;
        Holder<Enchantment> holder = registry.wrapAsHolder(mending);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack) > 0;
    }
}
