/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "automend",
        displayName = "AutoMend",
        category = ModuleCategory.PLAYER
)
public final class AutoMend extends Module {

    private final NumberValue<Integer> minDurability = num("min_durability_pct", 80, 10, 99);
    private final BooleanValue silent = bool("silent", true);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return;
        LocalPlayer player = mc.player;

        boolean needsRepair = false;
        for (ItemStack armor : player.getArmorSlots()) {
            if (!armor.isEmpty() && armor.isDamageableItem()) {
                double pct = 100.0 * (armor.getMaxDamage() - armor.getDamageValue()) / armor.getMaxDamage();
                if (pct < minDurability.get()) {
                    needsRepair = true;
                    break;
                }
            }
        }

        if (!needsRepair) return;

        int expSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(Items.EXPERIENCE_BOTTLE)) {
                expSlot = i;
                break;
            }
        }

        if (expSlot != -1) {
            int prev = player.getInventory().selected;
            player.getInventory().selected = expSlot;
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            player.swing(InteractionHand.MAIN_HAND);
            if (silent.get()) {
                player.getInventory().selected = prev;
            }
        }
    }
}
