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
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

@ModuleInfo(
        id = "autoweapon",
        displayName = "AutoWeapon",
        aliases = {"WeaponSelect", "AutoSword"},
        category = ModuleCategory.COMBAT,
        description = "Automatically switches to the most effective weapon in your hotbar when attacking"
)
public final class AutoWeapon extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue preferAxe =
            bool("autoWeaponPreferAxe", "prefer_axe", false);

    private final BooleanValue preferMace =
            bool("autoWeaponPreferMace", "prefer_mace", false);

    private final BooleanValue switchBack =
            bool("autoWeaponSwitchBack", "switch_back", false);

    private final NumberValue<Integer> switchDelay =
            num("autoWeaponSwitchDelay", "switch_delay", 10, 0, 40);

    private int previousSlot = -1;
    private int returnTicks = -1;

    public AutoWeapon() {
        super();
    }

    @Override
    public void onDisable() {
        restoreSlot();
    }

    @EventHandler
    public void onAttackEntity(AttackEntityEvent event) {
        if (!isEnabled()) return;
        switchToBestWeapon();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || ClientScreen.current() != null) return;

        if (mc.options.keyAttack.isDown() && mc.crosshairPickEntity instanceof LivingEntity) {
            switchToBestWeapon();
        } else if (returnTicks > 0) {
            returnTicks--;
            if (returnTicks == 0) {
                restoreSlot();
            }
        }
    }

    private void switchToBestWeapon() {
        LocalPlayer player = mc.player;
        if (player == null) return;

        int bestSlot = -1;
        double bestScore = -1.0;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            double score = scoreWeapon(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        if (bestSlot >= 0 && bestScore > 0) {
            int currentSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
            if (currentSlot != bestSlot) {
                if (previousSlot == -1 && switchBack.get()) {
                    previousSlot = currentSlot;
                }
                InventorySwap.INSTANCE.selectHotbar(bestSlot);
                returnTicks = switchDelay.get();
            } else if (switchBack.get()) {
                returnTicks = switchDelay.get();
            }
        }
    }

    private void restoreSlot() {
        if (switchBack.get() && previousSlot >= 0 && mc.player != null) {
            InventorySwap.INSTANCE.selectHotbar(previousSlot);
        }
        previousSlot = -1;
        returnTicks = -1;
    }

    private double scoreWeapon(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
        double score = 0.0;

        boolean isSword = path.endsWith("_sword");
        boolean isAxe = path.endsWith("_axe");
        boolean isMace = path.equals("mace");

        if (!isSword && !isAxe && !isMace) {
            return 0.0;
        }

        if (isMace && preferMace.get()) {
            score = 100.0;
        } else if (isAxe && preferAxe.get()) {
            score = 50.0;
        } else if (isSword) {
            score = 30.0;
        } else if (isAxe) {
            score = 25.0;
        } else if (isMace) {
            score = 20.0;
        }

        if (path.startsWith("netherite_")) score += 8.0;
        else if (path.startsWith("diamond_")) score += 7.0;
        else if (path.startsWith("iron_")) score += 6.0;
        else if (path.startsWith("stone_")) score += 5.0;
        else if (path.startsWith("golden_")) score += 4.0;
        else if (path.startsWith("wooden_")) score += 4.0;

        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants != null) {
            score += enchants.size() * 1.5;
        }

        return score;
    }
}
