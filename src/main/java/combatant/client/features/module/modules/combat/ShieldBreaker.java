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
import combatant.client.util.combat.ProtocolAttackExecutor;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Automatically switches to an axe and attacks when the target blocks with a shield, disabling their guard.
 * Ported and adapted from 67Client's ShieldBreakerModule.
 */
@ModuleInfo(
        id = "shieldbreaker",
        displayName = "ShieldBreaker",
        description = "Automatically switches to an axe and strikes when the target blocks with a shield, disabling their guard.",
        category = ModuleCategory.COMBAT,
        subCategory = ModuleSubCategory.OFFENSE,
        aliases = {"shieldbreak", "autobreakshield", "axebreak"}
)
public class ShieldBreaker extends Module {

    private final BooleanValue switchBack =
            bool("shieldbreaker_switch_back", "switch_back", true);
    private final NumberValue<Integer> switchDelay =
            num("shieldbreaker_delay", "switch_delay", 3, 0, 10);

    private final Minecraft mc = Minecraft.getInstance();
    private int previousSlot = -1;
    private int tickCounter = 0;
    private boolean waitingToSwapBack = false;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        previousSlot = -1;
        tickCounter = 0;
        waitingToSwapBack = false;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (waitingToSwapBack) {
            tickCounter++;
            if (tickCounter >= switchDelay.get()) {
                if (previousSlot >= 0 && previousSlot < 9) {
                    ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$setSelectedSlot(previousSlot);
                }
                resetState();
            }
            return;
        }

        LivingEntity target = findBlockingTarget();
        if (target == null) return;

        // Target is actively blocking with a shield
        int axeSlot = findAxeSlot();
        if (axeSlot == -1) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        if (currentSlot == axeSlot) {
            // Already holding axe, just attack
            ProtocolAttackExecutor.attackOnce(mc, mc.player, target, false, false);
            return;
        }

        // Swap to axe and strike
        previousSlot = currentSlot;
        ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$setSelectedSlot(axeSlot);
        ProtocolAttackExecutor.attackOnce(mc, mc.player, target, false, false);

        if (switchBack.get()) {
            waitingToSwapBack = true;
            tickCounter = 0;
        } else {
            resetState();
        }
    }

    private LivingEntity findBlockingTarget() {
        // 1. Check crosshair target
        if (mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
            if (isShieldBlocking(living)) {
                return living;
            }
        }

        // 2. Check nearby players
        double maxDistSq = 4.5 * 4.5;
        for (Player other : mc.level.players()) {
            if (other == mc.player || !other.isAlive() || other.isSpectator()) continue;
            if (other.distanceToSqr(mc.player) <= maxDistSq && isShieldBlocking(other)) {
                return other;
            }
        }

        return null;
    }

    private boolean isShieldBlocking(LivingEntity living) {
        if (!living.isUsingItem()) return false;
        ItemStack used = living.getUseItem();
        return used.is(Items.SHIELD);
    }

    private int findAxeSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.AXES)) {
                return i;
            }
        }
        return -1;
    }
}
