/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Notifier;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.screen.ClientScreen;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@ModuleInfo(
        id = "inventorysorter",
        displayName = "InventorySorter",
        aliases = {"autosort", "invsorter", "kitsaver"},
        category = ModuleCategory.PLAYER,
        description = "Sorts and rearranges your inventory to match customizable kit layouts with smooth slot swaps."
)
public class InventorySorter extends Module {

    private static final String ACTION_SORT = "sort";
    private static final String ACTION_SAVE = "save";
    private static final int OFFHAND_INV_SLOT = 40;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final BooleanValue sortOnOpen =
            bool("invsorter_sort_on_open", "sort_on_open", false);
    private String activeKit = "CrystalPvP";
    private static final int DEFAULT_DELAY = 2;
    private static final boolean SORT_OFFHAND = true;
    private final Minecraft mc = Minecraft.getInstance();
    private final Map<String, Kit> loadedKits = new LinkedHashMap<>();
    private final Queue<SwapAction> pendingSwaps = new ArrayDeque<>();
    private int cooldown = 0;
    private boolean wasInventoryOpen = false;

    public InventorySorter() {
        action(ACTION_SORT, "R");
        action(ACTION_SAVE, "NONE");
        loadKitsFromDisk();
    }

    @Override
    public void onEnable() {
        pendingSwaps.clear();
        cooldown = 0;
        wasInventoryOpen = false;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null) {
            return;
        }

        if (isActionPressedOnce(ACTION_SORT)) {
            sort();
            return;
        }

        if (isActionPressedOnce(ACTION_SAVE)) {
            saveCurrentInventory(activeKit);
            return;
        }

        boolean isInvOpen = ClientScreen.current() instanceof InventoryScreen;
        if (sortOnOpen.get() && isInvOpen && !wasInventoryOpen) {
            sort();
        }
        wasInventoryOpen = isInvOpen;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (!pendingSwaps.isEmpty()) {
            SwapAction swap = pendingSwaps.poll();
            if (swap != null) {
                executeSwap(swap);
                cooldown = DEFAULT_DELAY;
            }
        }
    }

    public void sort() {
        if (mc.player == null) return;

        Kit kit = getKit(activeKit);
        if (kit == null) {
            kit = getDefaultPvPKit();
        }

        pendingSwaps.clear();
        planSorting(kit);

        if (pendingSwaps.isEmpty()) {
            sendClientMessage("Inventory already matches " + kit.name());
            return;
        }

        sendClientMessage("Sorting inventory to match " + kit.name() + " (" + pendingSwaps.size() + " moves)...");
    }

    private Kit getDefaultPvPKit() {
        Map<Integer, String> slots = new LinkedHashMap<>();
        slots.put(0, "minecraft:netherite_sword");
        slots.put(1, "minecraft:netherite_pickaxe");
        slots.put(2, "minecraft:end_crystal");
        slots.put(3, "minecraft:enchanted_golden_apple");
        slots.put(4, "minecraft:obsidian");
        slots.put(5, "minecraft:ender_pearl");
        slots.put(6, "minecraft:totem_of_undying");
        slots.put(7, "minecraft:experience_bottle");
        slots.put(8, "minecraft:firework_rocket");
        slots.put(OFFHAND_INV_SLOT, "minecraft:totem_of_undying");
        return new Kit("CrystalPvP", slots);
    }

    private void planSorting(Kit kit) {
        if (mc.player == null) return;

        // Virtual model of slots: slot -> itemId
        String[] currentLayout = new String[41];
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            currentLayout[i] = getItemId(stack);
        }
        ItemStack offhand = mc.player.getOffhandItem();
        currentLayout[OFFHAND_INV_SLOT] = getItemId(offhand);

        // Track target slots (0..8 hotbar, 40 offhand, 9..35 main)
        for (Map.Entry<Integer, String> entry : kit.slots().entrySet()) {
            int targetSlot = entry.getKey();
            String desiredId = entry.getValue();
            if (targetSlot < 0 || targetSlot >= currentLayout.length) continue;
            if (desiredId == null || desiredId.isEmpty() || desiredId.equals("minecraft:air")) continue;
            if (targetSlot == OFFHAND_INV_SLOT && !SORT_OFFHAND) continue;

            if (desiredId.equalsIgnoreCase(currentLayout[targetSlot])) {
                continue; // already correct item in slot
            }

            // Search for an item matching desiredId in the inventory
            // Prefer picking from main inventory (9..35) first so hotbar isn't displaced
            int foundSlot = findCandidateSlot(currentLayout, kit, desiredId);
            if (foundSlot != -1 && foundSlot != targetSlot) {
                pendingSwaps.add(new SwapAction(foundSlot, targetSlot));
                // Update virtual layout
                String temp = currentLayout[targetSlot];
                currentLayout[targetSlot] = currentLayout[foundSlot];
                currentLayout[foundSlot] = temp;
            }
        }
    }

    private int findCandidateSlot(String[] layout, Kit kit, String desiredId) {
        // Priority 1: Main inventory slots (9..35) that are not already in their correct target slot
        for (int i = 9; i < 36; i++) {
            if (desiredId.equalsIgnoreCase(layout[i])) {
                String expectedHere = kit.slots().get(i);
                if (expectedHere == null || !expectedHere.equalsIgnoreCase(desiredId)) {
                    return i;
                }
            }
        }

        // Priority 2: Any hotbar slot (0..8) that is not in its correct target slot
        for (int i = 0; i < 9; i++) {
            if (desiredId.equalsIgnoreCase(layout[i])) {
                String expectedHere = kit.slots().get(i);
                if (expectedHere == null || !expectedHere.equalsIgnoreCase(desiredId)) {
                    return i;
                }
            }
        }

        // Priority 3: Any slot matching
        for (int i = 0; i < layout.length; i++) {
            if (desiredId.equalsIgnoreCase(layout[i])) {
                return i;
            }
        }

        return -1;
    }

    private void executeSwap(SwapAction swap) {
        if (mc.player == null) return;

        int from = swap.fromSlot;
        int to = swap.toSlot;

        if (to == OFFHAND_INV_SLOT) {
            InventorySwap.INSTANCE.swapInventoryToOffhand(from);
        } else if (to >= 0 && to < 9) {
            InventorySwap.INSTANCE.swapInventoryToHotbar(from, to);
        } else {
            int screenFrom = InventorySwap.mapInventoryToScreenSlot(from);
            int screenTo = InventorySwap.mapInventoryToScreenSlot(to);
            if (screenFrom != -1 && screenTo != -1) {
                InventorySwap.INSTANCE.swapScreenSlots(screenFrom, screenTo);
            }
        }
    }

    public void saveCurrentInventory(String kitName) {
        if (mc.player == null) return;
        String name = kitName == null || kitName.trim().isEmpty() ? activeKit : kitName.trim();

        Map<Integer, String> slots = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            String id = getItemId(stack);
            if (!id.equals("minecraft:air")) {
                slots.put(i, id);
            }
        }

        ItemStack offhand = mc.player.getOffhandItem();
        String offhandId = getItemId(offhand);
        if (!offhandId.equals("minecraft:air")) {
            slots.put(OFFHAND_INV_SLOT, offhandId);
        }

        Kit kit = new Kit(name, slots);
        loadedKits.put(name.toLowerCase(Locale.ROOT), kit);
        saveKitsToDisk();

        activeKit = name;
        String msg = "[InventorySorter] Saved current layout as kit '" + name + "' (" + slots.size() + " items)";
        sendClientMessage(msg);
        Notifier.success(msg);
    }

    public boolean loadKit(String kitName) {
        if (kitName == null) return false;
        Kit kit = loadedKits.get(kitName.trim().toLowerCase(Locale.ROOT));
        if (kit != null) {
            activeKit = kit.name();
            return true;
        }
        return false;
    }

    public boolean deleteKit(String kitName) {
        if (kitName == null) return false;
        String key = kitName.trim().toLowerCase(Locale.ROOT);
        if (loadedKits.containsKey(key)) {
            loadedKits.remove(key);
            saveKitsToDisk();
            return true;
        }
        return false;
    }

    public List<String> getKitNames() {
        return new ArrayList<>(loadedKits.values().stream().map(Kit::name).toList());
    }

    public Kit getKit(String name) {
        if (name == null) return null;
        return loadedKits.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public String getActiveKit() {
        return activeKit;
    }

    public void setActiveKit(String name) {
        if (name != null) {
            activeKit = name;
        }
    }

    private String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:air";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private void sendClientMessage(String text) {
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(text));
        }
    }

    private Path getKitsFilePath() {
        Path dir = mc.gameDirectory.toPath().resolve("combatant");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {}
        return dir.resolve("kits.json");
    }

    private void loadKitsFromDisk() {
        loadedKits.clear();
        createDefaultKits();

        Path path = getKitsFilePath();
        if (!Files.exists(path)) {
            saveKitsToDisk();
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Type type = new TypeToken<Map<String, Map<Integer, String>>>() {}.getType();
            Map<String, Map<Integer, String>> data = GSON.fromJson(reader, type);
            if (data != null) {
                for (Map.Entry<String, Map<Integer, String>> entry : data.entrySet()) {
                    String name = entry.getKey();
                    Map<Integer, String> slots = entry.getValue();
                    if (name != null && slots != null) {
                        loadedKits.put(name.toLowerCase(Locale.ROOT), new Kit(name, slots));
                    }
                }
            }
        } catch (Exception e) {
            DebugLog.warn("Failed to load kits from disk: %s", e.getMessage());
        }
    }

    private void saveKitsToDisk() {
        Path path = getKitsFilePath();
        try (Writer writer = Files.newBufferedWriter(path)) {
            Map<String, Map<Integer, String>> data = new LinkedHashMap<>();
            for (Kit kit : loadedKits.values()) {
                data.put(kit.name(), kit.slots());
            }
            GSON.toJson(data, writer);
        } catch (Exception e) {
            DebugLog.warn("Failed to save kits to disk: %s", e.getMessage());
        }
    }

    private void createDefaultKits() {
        // CrystalPvP default layout
        Map<Integer, String> cpvp = new LinkedHashMap<>();
        cpvp.put(0, "minecraft:netherite_sword");
        cpvp.put(1, "minecraft:end_crystal");
        cpvp.put(2, "minecraft:respawn_anchor");
        cpvp.put(3, "minecraft:glowstone");
        cpvp.put(4, "minecraft:obsidian");
        cpvp.put(5, "minecraft:enchanted_golden_apple");
        cpvp.put(6, "minecraft:totem_of_undying");
        cpvp.put(7, "minecraft:experience_bottle");
        cpvp.put(8, "minecraft:ender_pearl");
        cpvp.put(OFFHAND_INV_SLOT, "minecraft:totem_of_undying");
        loadedKits.put("crystalpvp", new Kit("CrystalPvP", cpvp));

        // SwordPvP default layout
        Map<Integer, String> spvp = new LinkedHashMap<>();
        spvp.put(0, "minecraft:netherite_sword");
        spvp.put(1, "minecraft:enchanted_golden_apple");
        spvp.put(2, "minecraft:ender_pearl");
        spvp.put(3, "minecraft:cobweb");
        spvp.put(4, "minecraft:bow");
        spvp.put(5, "minecraft:arrow");
        spvp.put(6, "minecraft:splash_potion");
    }

    public record Kit(String name, Map<Integer, String> slots) {}

    private record SwapAction(int fromSlot, int toSlot) {}

    public enum SortMode {
        SMOOTH,
        INSTANT
    }
}
