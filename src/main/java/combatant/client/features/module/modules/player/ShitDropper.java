/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.inventory.manager.InventoryManager;
import combatant.client.util.player.inventory.manager.InventoryPriority;

import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "shitdropper",
        displayName = "InventoryCleaner",
        aliases = {"inventorycleaner", "cleaner", "trashdropper", "invcleaner", "shitdropper"},
        category = ModuleCategory.PLAYER,
        description = "Intelligently cleans junk, useless clutter, and low-tier duplicate tools from inventory."
)
public class ShitDropper extends Module {

    private static final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue keepHotbar = bool("cleanerKeepHotbar", "keep_hotbar", true);
    private final ItemIdSetValue customTrash = itemList("cleanerCustomTrash", "custom_trash", TextListSetting.PickerMode.ALL);

    // Automatic smart cleaner constants
    private static final int DELAY_TICKS = 2;
    private static final int STACKS_PER_TICK = 1;

    private int cooldownTicks;

    @Override
    public void onEnable() {
        cooldownTicks = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return;
        if (mc.player.hurtTime > 0) return; // Pause during combat

        var handler = mc.player.containerMenu;
        if (handler == null || !handler.getCarried().isEmpty()) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        var targets = customTrash.get();
        boolean hasManualTargets = targets != null && !targets.isEmpty();

        List<Integer> trashSlots = new ArrayList<>();

        for (int screenSlot = 0; screenSlot < handler.slots.size(); screenSlot++) {
            var slot = handler.slots.get(screenSlot);
            if (slot == null || !slot.hasItem()) continue;

            if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory inv)) continue;
            if (inv.player != mc.player) continue;

            int invIndex = slot.getContainerSlot();
            if (invIndex < 0 || invIndex >= 36) continue;
            if (keepHotbar.get() && invIndex < 9) continue;

            var stack = slot.getItem();
            if (stack == null || stack.isEmpty()) continue;

            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (hasManualTargets && targets.contains(id)) {
                trashSlots.add(screenSlot);
            } else if (isSmartJunk(stack)) {
                trashSlots.add(screenSlot);
            }
        }

        if (trashSlots.isEmpty()) return;

        int toDrop = Math.min(STACKS_PER_TICK, trashSlots.size());
        for (int i = 0; i < toDrop; i++) {
            int slotIdx = trashSlots.get(i);
            InventoryManager.INSTANCE.submitThrow(InventoryPriority.LOW, slotIdx, true, this);
        }

        cooldownTicks = DELAY_TICKS;
    }

    private boolean isSmartJunk(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.SPIDER_EYE)
                || stack.is(Items.WHEAT_SEEDS)
                || stack.is(Items.BEETROOT_SEEDS)
                || stack.is(Items.GLASS_BOTTLE)
                || stack.is(Items.BOWL)) {
            return true;
        }

        // Automatic cleanup of low-tier wooden, leather, stone, and golden gear
        String name = stack.getItem().toString().toLowerCase();
        return name.contains("wooden_") || name.contains("leather_") || name.contains("stone_") || name.contains("golden_");
    }
}
