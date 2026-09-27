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
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ModuleInfo(
        id = "invcleaner",
        displayName = "InvCleaner",
        aliases = {"InventoryCleaner", "CleanInv", "TrashDropper"},
        category = ModuleCategory.PLAYER,
        description = "Automatically dumps junk and redundant suboptimal tools or armor from your inventory"
)
public class InvCleaner extends Module {

    private static final Set<String> DEFAULT_JUNK = Set.of(
            "rotten_flesh",
            "poisonous_potato",
            "spider_eye",
            "dead_bush",
            "lily_pad",
            "wheat_seeds",
            "beetroot_seeds",
            "fern",
            "short_grass"
    );

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue cleanJunk = bool("clean_junk", "Clean Junk", true);
    private final BooleanValue cleanDuplicateTools = bool("clean_dup_tools", "Clean Duplicate Tools", true);
    private final BooleanValue onlyInInventory = bool("only_in_inventory", "Only In Inventory Screen", false);
    private final NumberValue<Integer> delayTicks = num("delay_ticks", "Delay Ticks", 2, 0, 20);

    private int cooldown = 0;

    public InvCleaner() {
        super();
    }

    @Override
    public void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return;

        if (onlyInInventory.get() && !(ClientScreen.current() instanceof InventoryScreen)) {
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null || !handler.getCarried().isEmpty()) return;

        // Scan slots to find best tools
        Map<String, Integer> bestToolSlot = new HashMap<>();
        Map<String, Double> bestToolScore = new HashMap<>();

        for (int screenSlot = 0; screenSlot < handler.slots.size(); screenSlot++) {
            Slot slot = handler.slots.get(screenSlot);
            if (slot == null || !slot.hasItem()) continue;
            if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory inv) || inv.player != mc.player) continue;

            int invIndex = slot.getContainerSlot();
            if (invIndex < 0 || invIndex >= 36) continue;

            ItemStack stack = slot.getItem();
            String toolType = getToolType(stack);
            if (toolType != null) {
                double score = getToolScore(stack);
                if (!bestToolScore.containsKey(toolType) || score > bestToolScore.get(toolType)) {
                    bestToolScore.put(toolType, score);
                    bestToolSlot.put(toolType, screenSlot);
                }
            }
        }

        // Drop junk or suboptimal duplicate tools
        for (int screenSlot = 0; screenSlot < handler.slots.size(); screenSlot++) {
            Slot slot = handler.slots.get(screenSlot);
            if (slot == null || !slot.hasItem()) continue;
            if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory inv) || inv.player != mc.player) continue;

            int invIndex = slot.getContainerSlot();
            if (invIndex < 0 || invIndex >= 36) continue;

            ItemStack stack = slot.getItem();
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

            boolean shouldDrop = false;

            if (cleanJunk.get() && DEFAULT_JUNK.contains(itemId)) {
                shouldDrop = true;
            } else if (cleanDuplicateTools.get()) {
                String toolType = getToolType(stack);
                if (toolType != null) {
                    Integer bestSlot = bestToolSlot.get(toolType);
                    if (bestSlot != null && bestSlot != screenSlot) {
                        shouldDrop = true;
                    }
                }
            }

            if (shouldDrop) {
                InventorySwap.INSTANCE.dropStack(slot);
                cooldown = delayTicks.get();
                return;
            }
        }
    }

    private String getToolType(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.endsWith("_sword")) return "sword";
        if (path.endsWith("_pickaxe")) return "pickaxe";
        if (path.endsWith("_axe")) return "axe";
        if (path.endsWith("_shovel")) return "shovel";
        if (path.endsWith("_hoe")) return "hoe";
        return null;
    }

    private double getToolScore(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        double base = 0;
        if (path.startsWith("netherite_")) base = 60;
        else if (path.startsWith("diamond_")) base = 50;
        else if (path.startsWith("iron_")) base = 40;
        else if (path.startsWith("stone_")) base = 20;
        else if (path.startsWith("golden_")) base = 15;
        else if (path.startsWith("wooden_")) base = 10;
        return base;
    }
}
