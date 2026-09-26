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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;

@ModuleInfo(
        id = "autopotion",
        displayName = "AutoPotion",
        aliases = {"AutoPot", "SmartPot"},
        category = ModuleCategory.PLAYER,
        description = "Automatically throws splash potions of Healing, Speed, and Strength."
)
public final class AutoPotion extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> healthThreshold =
            num("autoPotionHealthThreshold", "health_threshold", 12.0f, 2.0f, 20.0f);

    private final BooleanValue heal =
            bool("autoPotionHeal", "heal", true);

    private final BooleanValue speed =
            bool("autoPotionSpeed", "speed", false);

    private final BooleanValue strength =
            bool("autoPotionStrength", "strength", false);

    private final BooleanValue silentPitch =
            bool("autoPotionSilentPitch", "silent_pitch", true);

    private final NumberValue<Integer> delayTicks =
            num("autoPotionDelayTicks", "delay_ticks", 3, 1, 10);

    private int cooldown = 0;
    private boolean aiming = false;

    @Override
    public void onDisable() {
        cooldown = 0;
        aiming = false;
        RotationManager.INSTANCE.release(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            aiming = false;
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            aiming = false;
            return;
        }

        int slot = findTargetPotionSlot(player);
        if (slot == -1) {
            aiming = false;
            return;
        }

        aiming = true;
        InventorySwap.INSTANCE.leaseHotbar(this, slot, 2);
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
        cooldown = delayTicks.get();
    }

    @EventHandler(priority = 20)
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled() || !silentPitch.get() || !aiming || mc.player == null) return;
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;

        LocalPlayer player = mc.player;
        RotationTarget target = new RotationTarget(
                new Rotation(player.getYRot(), 90.0f),
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

    private int findTargetPotionSlot(LocalPlayer player) {
        float health = player.getHealth() + player.getAbsorptionAmount();

        // 1. Health check
        if (heal.get() && health <= healthThreshold.get()) {
            int slot = findPotionSlotWith(player, MobEffects.INSTANT_HEALTH);
            if (slot != -1) return slot;
            slot = findPotionSlotWith(player, MobEffects.REGENERATION);
            if (slot != -1) return slot;
        }

        // 2. Speed check
        if (speed.get() && !player.hasEffect(MobEffects.SPEED)) {
            int slot = findPotionSlotWith(player, MobEffects.SPEED);
            if (slot != -1) return slot;
        }

        // 3. Strength check
        if (strength.get() && !player.hasEffect(MobEffects.STRENGTH)) {
            int slot = findPotionSlotWith(player, MobEffects.STRENGTH);
            if (slot != -1) return slot;
        }

        return -1;
    }

    private int findPotionSlotWith(LocalPlayer player, Holder<MobEffect> targetEffect) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !stack.is(Items.SPLASH_POTION)) continue;

            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null) continue;

            for (MobEffectInstance instance : contents.getAllEffects()) {
                if (instance.getEffect().equals(targetEffect)) {
                    return i;
                }
            }
        }
        return -1;
    }
}
