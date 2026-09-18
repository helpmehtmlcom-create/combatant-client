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
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

@ModuleInfo(
        id = "spearswap",
        displayName = "Spear Swap",
        description = "Rapid weapon swap macro for 1.21.11 / 26.x tridents and spears for maximum piercing damage bursts.",
        category = ModuleCategory.COMBAT,
        subCategory = ModuleSubCategory.OFFENSE,
        aliases = {"tridentswap", "spearmacro"}
)
public class SpearSwap extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> switchBackDelay =
            num("spear_switch_back_delay", "delay_ticks", 2, 1, 10);
    private final BooleanValue switchBack =
            bool("spear_switch_back", "switch_back", true);

    private int originalSlot = -1;
    private int delayTimer = 0;

    @Override
    public void onDisable() {
        if (originalSlot >= 0 && mc.player != null) {
            InventorySwap.INSTANCE.selectHotbar(originalSlot);
        }
        originalSlot = -1;
        delayTimer = 0;
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null) return;

        if (delayTimer > 0) {
            delayTimer--;
            if (delayTimer == 0 && switchBack.get() && originalSlot >= 0) {
                InventorySwap.INSTANCE.selectHotbar(originalSlot);
                originalSlot = -1;
            }
            return;
        }

        // Trigger swap when attacking or targeting an enemy player
        if (mc.options.keyAttack.isDown() && mc.hitResult instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (target instanceof Player && target.isAlive()) {
                int spearSlot = findSpearSlot();
                int current = ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$getSelectedSlot();
                if (spearSlot >= 0 && spearSlot != current) {
                    originalSlot = current;
                    InventorySwap.INSTANCE.selectHotbar(spearSlot);
                    mc.gameMode.attack(mc.player, target);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    delayTimer = switchBackDelay.get();
                }
            }
        }
    }

    private int findSpearSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.TRIDENT)) {
                return i;
            }
        }
        return -1;
    }
}
