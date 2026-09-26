/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

@ModuleInfo(
        id = "automend",
        displayName = "AutoMend",
        aliases = {"AutoRepair", "Mend", "ExpMend"},
        category = ModuleCategory.PLAYER,
        description = "Automatically throws experience bottles at your feet until damaged armor/items are repaired."
)
public final class AutoMend extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue lookDown =
            bool("autoMendLookDown", "look_down", true);

    private final NumberValue<Float> pitch =
            visibleWhen(num("autoMendPitch", "pitch", 90.0f, 70.0f, 90.0f), lookDown::get);

    private final NumberValue<Integer> threshold =
            num("autoMendThreshold", "durability_threshold", 100, 50, 100);

    private final BooleanValue repairArmor =
            bool("autoMendRepairArmor", "repair_armor", true);

    private final BooleanValue repairHeld =
            bool("autoMendRepairHeld", "repair_held", true);

    private final BooleanValue autoDisable =
            bool("autoMendAutoDisable", "auto_disable", true);

    private final NumberValue<Integer> delay =
            num("autoMendDelay", "delay", 1, 0, 10);

    private int delayCounter = 0;

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.release(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
        delayCounter = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            setEnabled(false);
            return;
        }

        if (!hasRepairableItems(player)) {
            if (autoDisable.get()) {
                setEnabled(false);
            }
            return;
        }

        boolean offhandXp = player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE);
        int xpSlot = offhandXp ? -1 : findXpSlot(player);

        if (!offhandXp && xpSlot == -1) {
            if (autoDisable.get()) {
                setEnabled(false);
            }
            return;
        }

        if (delayCounter < delay.get()) {
            delayCounter++;
            return;
        }
        delayCounter = 0;

        if (offhandXp) {
            mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
            player.swing(InteractionHand.OFF_HAND);
        } else {
            InventorySwap.INSTANCE.leaseHotbar(this, xpSlot, 2);
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @EventHandler(priority = 20)
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled() || !lookDown.get() || mc.player == null) {
            return;
        }

        if (event.getType() != RotationUpdateEvent.Type.PRE) {
            return;
        }

        LocalPlayer player = mc.player;
        RotationTarget target = new RotationTarget(
                new Rotation(player.getYRot(), pitch.get()),
                player,
                List.of(),
                1,
                4.0f,
                false,
                MovementCorrection.SILENT,
                new RestrictedSingleUseAction(null)
        );
        RotationManager.INSTANCE.setRotationTarget(target, 50, this);
    }

    private boolean hasRepairableItems(LocalPlayer player) {
        if (player == null) return false;

        if (repairArmor.get()) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack armor = player.getItemBySlot(slot);
                if (isNeedsRepair(armor)) {
                    return true;
                }
            }
        }

        if (repairHeld.get()) {
            if (isNeedsRepair(player.getMainHandItem()) || isNeedsRepair(player.getOffhandItem())) {
                return true;
            }
        }

        return false;
    }

    private boolean isNeedsRepair(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem() || !stack.isDamaged()) {
            return false;
        }

        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) return false;

        int currentDamage = stack.getDamageValue();
        double durabilityPercent = (1.0 - (double) currentDamage / maxDamage) * 100.0;
        return durabilityPercent < threshold.get();
    }

    private int findXpSlot(LocalPlayer player) {
        if (player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.EXPERIENCE_BOTTLE)) {
                return i;
            }
        }
        return -1;
    }
}
