/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.util.click.AttackPressing;
import combatant.client.util.combat.AttackUtil;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "antitotem",
        displayName = "AntiTotem",
        aliases = {"TotemBypass", "PopTimer"},
        category = ModuleCategory.COMBAT,
        description = "Times attacks and crystal breaks to eliminate opponents during totem vulnerability windows."
)
public final class AntiTotem extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> healthThreshold =
            num("antiTotemHealthThreshold", "health_threshold", 8.0f, 2.0f, 16.0f);

    private final NumberValue<Double> targetRange =
            num("antiTotemTargetRange", "target_range", 5.0, 2.0, 7.0);

    private final BooleanValue checkOffhand =
            bool("antiTotemCheckOffhand", "check_offhand", true);

    private final BooleanValue prioritizeWeapon =
            bool("antiTotemPrioritizeWeapon", "prioritize_weapon", true);

    private final BooleanValue silentSwap =
            bool("antiTotemSilentSwap", "silent_swap", true);

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;

        Player vulnerable = findVulnerableOpponent(player);
        if (vulnerable == null) return;

        if (AttackPressing.INSTANCE.isCooldownComplete(player, vulnerable, false, 0)) {
            if (prioritizeWeapon.get() && silentSwap.get()) {
                int weaponSlot = findWeaponSlot(player);
                if (weaponSlot != -1) {
                    InventorySwap.INSTANCE.leaseHotbar(this, weaponSlot, 2);
                }
            }

            AttackUtil.attackCurrentItem(mc, vulnerable, true);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private Player findVulnerableOpponent(LocalPlayer player) {
        if (mc.level == null) return null;

        Player best = null;
        double maxDistSq = targetRange.get() * targetRange.get();
        float threshold = healthThreshold.get();

        for (Player opponent : mc.level.players()) {
            if (opponent.equals(player) || opponent.isSpectator() || opponent.isCreative()) continue;

            String name = opponent.getName().getString();
            if (PlayerRelations.get().getFriends().contains(name)) continue;

            double distSq = player.distanceToSqr(opponent);
            if (distSq > maxDistSq) continue;

            float oppHealth = opponent.getHealth() + opponent.getAbsorptionAmount();
            boolean hasOffhandTotem = opponent.getOffhandItem().is(Items.TOTEM_OF_UNDYING);

            if (checkOffhand.get() && hasOffhandTotem) {
                continue;
            }

            if (oppHealth <= threshold) {
                best = opponent;
                break;
            }
        }

        return best;
    }

    private int findWeaponSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.MACE))) {
                return i;
            }
        }
        return -1;
    }
}
