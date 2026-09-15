/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;

@ModuleInfo(
        id = "antiweakness",
        displayName = "AntiWeakness",
        category = ModuleCategory.COMBAT
)
public final class AntiWeakness extends Module {

    private final BooleanValue silent = bool("silent", true);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    public void onAttack(AttackEntityEvent event) {
        if (!isEnabled() || mc.player == null) return;
        LocalPlayer player = mc.player;

        if (player.hasEffect(MobEffects.WEAKNESS)) {
            int weaponSlot = findWeaponSlot();
            int currentSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
            if (weaponSlot != -1 && weaponSlot != currentSlot) {
                ((PlayerInventoryAccessor) player.getInventory()).combatant$setSelectedSlot(weaponSlot);
            }
        }
    }

    private int findWeaponSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES)) {
                return i;
            }
        }
        return -1;
    }
}
