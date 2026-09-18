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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Automatically swaps to a Mace when attacking to apply massive smash and burst damage, then swaps back.
 * Ported and adapted from 67Client's MaceSwapModule.
 */
@ModuleInfo(
        id = "maceswap",
        displayName = "MaceSwap",
        description = "Automatically swaps to a Mace when attacking to apply massive smash damage, then restores weapon.",
        category = ModuleCategory.COMBAT,
        subCategory = ModuleSubCategory.OFFENSE,
        aliases = {"maceassist", "quickmace", "maceswitch"}
)
public class MaceSwap extends Module {

    private final BooleanValue onlySword =
            bool("maceswap_only_sword", "only_sword", true);
    private final BooleanValue onlyAxe =
            bool("maceswap_only_axe", "only_axe", false);
    private final BooleanValue switchBack =
            bool("maceswap_switch_back", "switch_back", true);
    private final NumberValue<Integer> switchDelay =
            num("maceswap_switch_delay", "switch_delay", 2, 0, 10);

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

        if (!mc.options.keyAttack.isDown()) return;

        if (mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
            if (!target.isAlive() || target == mc.player) return;

            ItemStack held = mc.player.getMainHandItem();
            boolean isSword = held.is(ItemTags.SWORDS);
            boolean isAxe = held.is(ItemTags.AXES);

            if (onlySword.get() && !isSword) return;
            if (onlyAxe.get() && !isAxe) return;

            int maceSlot = findMaceSlot();
            if (maceSlot == -1) return;

            int currentSlot = mc.player.getInventory().getSelectedSlot();
            if (currentSlot == maceSlot) return;

            previousSlot = currentSlot;
            ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$setSelectedSlot(maceSlot);
            ProtocolAttackExecutor.attackOnce(mc, mc.player, target, false, false);

            if (switchBack.get()) {
                waitingToSwapBack = true;
                tickCounter = 0;
            } else {
                resetState();
            }
        }
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
