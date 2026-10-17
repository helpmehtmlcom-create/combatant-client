/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autoanchor;

import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import combatant.client.util.combat.CombatBlockUseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public final class AutoAnchorActionUtil {
    private AutoAnchorActionUtil() {
    }

    public static boolean placeAnchor(Minecraft mc,
                                      BlockHitResult hitResult,
                                      InventorySearchScope scope,
                                      InventorySwapVisibility visibility,
                                      boolean restore) {
        if (mc == null || hitResult == null || mc.player == null || mc.gameMode == null) return false;
        InteractionHand held = AutoAnchorInteractionUtil.heldAnchorHand(mc.player);
        return CombatBlockUseUtil.useHeldOrSwap(mc, held, AutoAnchorInteractionUtil::isAnchor,
                hitResult, scope, visibility, restore);
    }

    public static boolean chargeAnchor(Minecraft mc,
                                       BlockHitResult hitResult,
                                       InventorySearchScope scope,
                                       InventorySwapVisibility visibility,
                                       boolean restore) {
        if (mc == null || hitResult == null || mc.player == null || mc.gameMode == null) return false;
        InteractionHand held = AutoAnchorInteractionUtil.heldGlowstoneHand(mc.player);
        return CombatBlockUseUtil.useHeldOrSwap(mc, held, AutoAnchorInteractionUtil::isGlowstone,
                hitResult, scope, visibility, restore);
    }

    /** Uses a non-glowstone hand on the anchor; false when glowstone in the offhand has to charge the anchor first. */
    public static boolean detonateAnchor(Minecraft mc,
                                         BlockHitResult hitResult,
                                         InventorySearchScope scope,
                                         InventorySwapVisibility visibility,
                                         boolean restore) {
        if (mc == null || hitResult == null || mc.player == null || mc.level == null || mc.gameMode == null) return false;
        int charges = AutoAnchorInteractionUtil.anchorCharges(mc.level.getBlockState(hitResult.getBlockPos()));
        InteractionHand held = AutoAnchorInteractionUtil.detonatorHand(mc.player, charges);
        if (held == null) return false;
        return CombatBlockUseUtil.useHeldOrSwap(mc, held, AutoAnchorInteractionUtil::isDetonator,
                hitResult, scope, visibility, restore);
    }

    /** Glowstone in the offhand charges a part-charged anchor instead of letting a main-hand use detonate it. */
    public static boolean mustTopUpBeforeDetonating(Minecraft mc, int charges) {
        return mc != null && mc.player != null
                && AutoAnchorInteractionUtil.detonatorHand(mc.player, charges) == null;
    }

    public static boolean hasAnchor(Minecraft mc, InventorySearchScope scope) {
        if (mc == null || mc.player == null) return false;
        return CombatBlockUseUtil.hasHeldOrInventoryItem(
                AutoAnchorInteractionUtil.heldAnchorHand(mc.player), AutoAnchorInteractionUtil::isAnchor, scope);
    }

    public static boolean hasGlowstone(Minecraft mc, InventorySearchScope scope) {
        if (mc == null || mc.player == null) return false;
        return CombatBlockUseUtil.hasHeldOrInventoryItem(
                AutoAnchorInteractionUtil.heldGlowstoneHand(mc.player), AutoAnchorInteractionUtil::isGlowstone, scope);
    }
}
