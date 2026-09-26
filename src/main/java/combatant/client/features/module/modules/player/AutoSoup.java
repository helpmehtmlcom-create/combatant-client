/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "autosoup",
        displayName = "AutoSoup",
        aliases = {"FastSoup", "SoupPvP"},
        category = ModuleCategory.PLAYER,
        description = "Automatically eats soup and clears empty bowls in KitPvP."
)
public final class AutoSoup extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> healthThreshold =
            num("autoSoupHealthThreshold", "health_threshold", 14.0f, 2.0f, 20.0f);

    private final BooleanValue dropBowl =
            bool("autoSoupDropBowl", "drop_bowl", true);

    private final NumberValue<Integer> delayTicks =
            num("autoSoupDelayTicks", "delay_ticks", 2, 0, 10);

    private int cooldown = 0;

    @Override
    public void onDisable() {
        cooldown = 0;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // 1. Drop bowl if currently held
        if (dropBowl.get()) {
            if (player.getMainHandItem().is(Items.BOWL)) {
                player.drop(false);
                cooldown = delayTicks.get();
                return;
            }
        }

        // 2. Check health and eat soup
        float health = player.getHealth() + player.getAbsorptionAmount();
        if (health <= healthThreshold.get()) {
            int soupSlot = findSoupSlot(player);
            if (soupSlot != -1) {
                InventorySwap.INSTANCE.leaseHotbar(this, soupSlot, 2);
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                player.swing(InteractionHand.MAIN_HAND);
                cooldown = delayTicks.get();
            }
        }
    }

    private int findSoupSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isSoup(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSoup(ItemStack stack) {
        return stack.is(Items.MUSHROOM_STEW)
                || stack.is(Items.RABBIT_STEW)
                || stack.is(Items.BEETROOT_SOUP)
                || stack.is(Items.SUSPICIOUS_STEW);
    }
}
