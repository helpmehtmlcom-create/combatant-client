/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.combat.ProtocolAttackExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Times a fully-charged Mace smash attack on the way down while falling on DonutSMP.
 * Ported and adapted from 67Client's MaceBomberModule.
 */
@ModuleInfo(
        id = "macebomber",
        displayName = "MaceBomber",
        description = "Times a fully-charged Mace smash attack on the way down while falling to maximize smash damage.",
        category = ModuleCategory.COMBAT,
        subCategory = ModuleSubCategory.OFFENSE,
        aliases = {"macedrop", "macesmash", "bomber"}
)
public class MaceBomber extends Module {

    private final NumberValue<Double> minFallDistance =
            num("macebomber_fall_dist", "fall_distance", 2.5, 1.0, 10.0);
    private final BooleanValue autoMace =
            bool("macebomber_auto_mace", "auto_mace", true);
    private final BooleanValue switchBack =
            bool("macebomber_switch_back", "switch_back", true);

    private final Minecraft mc = Minecraft.getInstance();
    private int previousSlot = -1;
    private int cooldown = 0;

    @Override
    public void onEnable() {
        previousSlot = -1;
        cooldown = 0;
    }

    @Override
    public void onDisable() {
        previousSlot = -1;
        cooldown = 0;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // Check if player is falling from sufficient height
        boolean isFalling = mc.player.fallDistance >= minFallDistance.get()
                || mc.player.getDeltaMovement().y < -0.45;

        if (!isFalling) return;

        LivingEntity target = findTarget();
        if (target == null) return;

        int maceSlot = findMaceSlot();
        if (maceSlot == -1) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        boolean holdingMace = currentSlot == maceSlot || mc.player.getMainHandItem().is(Items.MACE);

        if (!holdingMace && autoMace.get()) {
            previousSlot = currentSlot;
            ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$setSelectedSlot(maceSlot);
            holdingMace = true;
        }

        if (holdingMace && mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
            // Execute mace smash attack
            ProtocolAttackExecutor.attackOnce(mc, mc.player, target, false, false);
            cooldown = 10;

            if (switchBack.get() && previousSlot != -1 && previousSlot != maceSlot) {
                ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$setSelectedSlot(previousSlot);
                previousSlot = -1;
            }
        }
    }

    private LivingEntity findTarget() {
        if (mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
            if (living.isAlive() && living != mc.player) return living;
        }

        double maxDistSq = 4.0 * 4.0;
        for (Player other : mc.level.players()) {
            if (other == mc.player || !other.isAlive() || other.isSpectator()) continue;
            if (other.distanceToSqr(mc.player) <= maxDistSq) {
                return other;
            }
        }
        return null;
    }

    private int findMaceSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.MACE)) {
                return i;
            }
        }
        return -1;
    }
}
