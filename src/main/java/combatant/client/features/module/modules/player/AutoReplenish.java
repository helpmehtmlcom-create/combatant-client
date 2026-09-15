/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@ModuleInfo(
        id = "autoreplenish",
        displayName = "AutoReplenish",
        aliases = {"replenish", "refill"},
        category = ModuleCategory.PLAYER
)
public class AutoReplenish extends Module {

    public static final String KEY_CRYSTALS = "crystals";
    public static final String KEY_TOTEMS = "totems";
    public static final String KEY_GAPPLES = "gapples";
    public static final String KEY_EXP = "exp";
    public static final String KEY_OBSIDIAN = "obsidian";
    public static final String KEY_ANCHORS = "anchors";
    public static final String KEY_GLOWSTONE = "glowstone";
    public static final String KEY_FIREWORKS = "fireworks";
    public static final String KEY_PEARLS = "pearls";

    private final NumberValue<Integer> threshold = num("threshold", 8, 1, 32);
    private final NumberValue<Integer> delay = num("delay", 3, 1, 10);
    private final BooleanMapValue items = group("items", defaultItems());

    private final Minecraft mc = Minecraft.getInstance();
    private final Item[] lastHotbarItems = new Item[9];
    private int cooldownTicks;

    public AutoReplenish() {
        super();
    }

    private static Map<String, Boolean> defaultItems() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put(KEY_CRYSTALS, true);
        map.put(KEY_TOTEMS, true);
        map.put(KEY_GAPPLES, true);
        map.put(KEY_EXP, true);
        map.put(KEY_OBSIDIAN, true);
        map.put(KEY_ANCHORS, true);
        map.put(KEY_GLOWSTONE, true);
        map.put(KEY_FIREWORKS, true);
        map.put(KEY_PEARLS, true);
        return map;
    }

    @Override
    public void onEnable() {
        cooldownTicks = 0;
        Arrays.fill(lastHotbarItems, null);
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return;
        if (ClientScreen.current() != null) return;
        if (mc.player.inventoryMenu == null) return;
        if (!mc.player.isAlive() || mc.player.isSpectator()) return;
        if (mc.player.containerMenu != null && !mc.player.containerMenu.getCarried().isEmpty()) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack hotbarStack = mc.player.getInventory().getItem(hotbarSlot);
            Item targetItem = null;
            int currentCount = 0;

            if (!hotbarStack.isEmpty()) {
                Item item = hotbarStack.getItem();
                lastHotbarItems[hotbarSlot] = item;
                if (isItemTracked(item)
                        && hotbarStack.getCount() <= threshold.get()
                        && hotbarStack.getCount() < hotbarStack.getMaxStackSize()) {
                    targetItem = item;
                    currentCount = hotbarStack.getCount();
                }
            } else {
                Item lastItem = lastHotbarItems[hotbarSlot];
                if (lastItem != null && isItemTracked(lastItem)) {
                    targetItem = lastItem;
                    currentCount = 0;
                }
            }

            if (targetItem == null) continue;

            int bestSlot = -1;
            int bestCount = currentCount;

            for (int invSlot = 9; invSlot <= 35; invSlot++) {
                ItemStack invStack = mc.player.inventoryMenu.getSlot(invSlot).getItem();
                if (invStack.isEmpty() || !invStack.is(targetItem)) continue;

                if (invStack.getCount() > bestCount) {
                    bestCount = invStack.getCount();
                    bestSlot = invSlot;
                }
            }

            if (bestSlot != -1) {
                mc.gameMode.handleContainerInput(
                        mc.player.inventoryMenu.containerId,
                        bestSlot,
                        hotbarSlot,
                        ContainerInput.SWAP,
                        mc.player
                );
                lastHotbarItems[hotbarSlot] = targetItem;
                cooldownTicks = Math.max(1, delay.get());
                break;
            } else if (hotbarStack.isEmpty()) {
                lastHotbarItems[hotbarSlot] = null;
            }
        }
    }

    public boolean isItemTracked(Item item) {
        if (item == null || item == Items.AIR) return false;
        if (items.get(KEY_CRYSTALS) && item == Items.END_CRYSTAL) return true;
        if (items.get(KEY_TOTEMS) && item == Items.TOTEM_OF_UNDYING) return true;
        if (items.get(KEY_GAPPLES) && (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)) return true;
        if (items.get(KEY_EXP) && item == Items.EXPERIENCE_BOTTLE) return true;
        if (items.get(KEY_OBSIDIAN) && item == Items.OBSIDIAN) return true;
        if (items.get(KEY_ANCHORS) && item == Items.RESPAWN_ANCHOR) return true;
        if (items.get(KEY_GLOWSTONE) && item == Items.GLOWSTONE) return true;
        if (items.get(KEY_FIREWORKS) && item == Items.FIREWORK_ROCKET) return true;
        if (items.get(KEY_PEARLS) && item == Items.ENDER_PEARL) return true;
        return false;
    }

    public NumberValue<Integer> getThreshold() {
        return threshold;
    }

    public NumberValue<Integer> getDelay() {
        return delay;
    }

    public BooleanMapValue getItems() {
        return items;
    }
}
