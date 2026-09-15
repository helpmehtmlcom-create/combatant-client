/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.component.DataComponents;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ModuleInfo(
        id = "cheststealer",
        displayName = "ChestStealer",
        aliases = {"stealer", "autoloot"},
        category = ModuleCategory.PLAYER,
        description = "Automatically loots containers, chests, and shulker boxes"
)
public class ChestStealer extends Module {

    private final NumberValue<Integer> delayTicks =
            num("chestStealerDelayTicks", "delayTicks", 1, 0, 10);
    private final BooleanValue smartLoot =
            bool("chestStealerSmartLoot", "smartLoot", true);
    private final BooleanValue autoClose =
            bool("chestStealerAutoClose", "autoClose", true);
    private final BooleanValue instant =
            bool("chestStealerInstant", "instant", false);
    private final BooleanValue shulkersOnly =
            bool("chestStealerShulkersOnly", "shulkersOnly", false);

    private final Minecraft mc = Minecraft.getInstance();

    private int timer = 0;
    private int lastContainerId = -1;
    private int ticksOpen = 0;

    @Override
    public void onEnable() {
        timer = 0;
        lastContainerId = -1;
        ticksOpen = 0;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return;

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu == mc.player.inventoryMenu || menu instanceof InventoryMenu) {
            lastContainerId = -1;
            ticksOpen = 0;
            timer = 0;
            return;
        }

        if (menu.containerId != lastContainerId) {
            lastContainerId = menu.containerId;
            ticksOpen = 0;
            timer = instant.get() ? 0 : delayTicks.get();
        }

        ticksOpen++;

        if (shulkersOnly.get() && !isShulkerContainer(menu)) {
            return;
        }

        // Separate container slots vs player inventory slots
        List<Slot> containerSlots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot == null || slot.container == null) continue;
            if (slot.container != mc.player.getInventory()) {
                containerSlots.add(slot);
            }
        }

        if (containerSlots.isEmpty()) {
            return;
        }

        // Collect lootable slots
        List<Slot> lootableSlots = new ArrayList<>();
        for (Slot slot : containerSlots) {
            if (!slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (smartLoot.get()) {
                if (isValuable(stack)) {
                    lootableSlots.add(slot);
                }
            } else {
                lootableSlots.add(slot);
            }
        }

        // Container is fully looted of target items
        if (lootableSlots.isEmpty()) {
            if (autoClose.get() && ticksOpen >= 1) {
                closeContainer();
            }
            return;
        }

        // Sort by priority if smartLoot is enabled
        if (smartLoot.get()) {
            lootableSlots.sort((a, b) -> {
                int prioA = getItemPriority(a.getItem());
                int prioB = getItemPriority(b.getItem());
                if (prioA != prioB) {
                    return Integer.compare(prioB, prioA);
                }
                return Integer.compare(a.index, b.index);
            });
        }

        int emptySlots = getEmptyPlayerSlots(menu);

        // Check if player inventory is full and cannot take any lootable items
        boolean canFitAny = emptySlots > 0 || lootableSlots.stream().anyMatch(s -> canStackIntoPlayer(menu, s.getItem()));
        if (!canFitAny) {
            if (autoClose.get()) {
                closeContainer();
            }
            return;
        }

        if (instant.get()) {
            int currentEmpty = emptySlots;
            for (Slot slot : lootableSlots) {
                if (currentEmpty <= 0 && !canStackIntoPlayer(menu, slot.getItem())) {
                    break;
                }
                boolean usedEmpty = !canStackIntoPlayer(menu, slot.getItem());
                lootSlot(menu, slot);
                if (usedEmpty) {
                    currentEmpty--;
                }
            }

            if (autoClose.get()) {
                closeContainer();
            }
        } else {
            if (timer > 0) {
                timer--;
                return;
            }

            Slot bestSlot = lootableSlots.get(0);
            lootSlot(menu, bestSlot);
            timer = delayTicks.get();
        }
    }

    private void lootSlot(AbstractContainerMenu menu, Slot slot) {
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
    }

    private void closeContainer() {
        if (mc.player != null) {
            mc.player.closeContainer();
        }
        if (ClientScreen.current() != null && !(ClientScreen.current() instanceof InventoryScreen)) {
            ClientScreen.show(null);
        }
    }

    private boolean isShulkerContainer(AbstractContainerMenu menu) {
        if (menu instanceof ShulkerBoxMenu) return true;
        if (ClientScreen.current() instanceof ShulkerBoxScreen) return true;
        if (ClientScreen.current() != null) {
            String title = ClientScreen.current().getTitle().getString().toLowerCase(Locale.ROOT);
            return title.contains("shulker");
        }
        return false;
    }

    private int getEmptyPlayerSlots(AbstractContainerMenu menu) {
        if (mc.player == null) return 0;
        int count = 0;
        for (Slot slot : menu.slots) {
            if (slot == null || slot.container != mc.player.getInventory()) continue;
            int invSlot = slot.getContainerSlot();
            if (invSlot >= 0 && invSlot < 36) {
                if (!slot.hasItem() || slot.getItem().isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean canStackIntoPlayer(AbstractContainerMenu menu, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isStackable() || mc.player == null) {
            return false;
        }
        for (Slot slot : menu.slots) {
            if (slot == null || slot.container != mc.player.getInventory()) continue;
            int invSlot = slot.getContainerSlot();
            if (invSlot < 0 || invSlot >= 36) continue;
            ItemStack current = slot.getItem();
            if (!current.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, current)
                    && current.getCount() < current.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private int getItemPriority(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;

        // 1. Totems
        if (stack.is(Items.TOTEM_OF_UNDYING)) {
            return 1000;
        }

        // 2. Gapples
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return 900;
        }
        if (stack.is(Items.GOLDEN_APPLE)) {
            return 850;
        }

        // 3. End Crystals & Crystal PvP essentials
        if (stack.is(Items.END_CRYSTAL)) {
            return 800;
        }
        if (stack.is(Items.RESPAWN_ANCHOR)) {
            return 790;
        }
        if (stack.is(Items.GLOWSTONE) || stack.is(Items.OBSIDIAN) || stack.is(Items.CRYING_OBSIDIAN)) {
            return 780;
        }

        // 4. Shulker Boxes
        if (isShulkerBox(stack)) {
            return 750;
        }

        // 5. Elytra
        if (stack.is(Items.ELYTRA)) {
            return 700;
        }

        // 6. Netherite equipment and items
        if (isNetherite(stack)) {
            return 650;
        }

        // 7. Experience Bottles
        if (stack.is(Items.EXPERIENCE_BOTTLE)) {
            return 600;
        }

        // 8. Armor (Diamond, etc.)
        if (isArmor(stack)) {
            return 550;
        }

        // 9. Weapons and Tools
        if (isWeaponOrTool(stack)) {
            return 500;
        }

        // 10. Pearls, Chorus Fruit, Wind Charges, Fireworks, Arrows
        if (stack.is(Items.ENDER_PEARL) || stack.is(Items.CHORUS_FRUIT) || stack.is(Items.WIND_CHARGE)) {
            return 450;
        }
        if (stack.is(Items.FIREWORK_ROCKET) || stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW)) {
            return 400;
        }

        return -1;
    }

    private boolean isValuable(ItemStack stack) {
        return getItemPriority(stack) > 0;
    }

    private boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof ShulkerBoxBlock;
        }
        return false;
    }

    private boolean isNetherite(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains("netherite");
    }

    private boolean isArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        return eq != null && eq.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    private boolean isWeaponOrTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.MACE) || stack.is(Items.TRIDENT) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) {
            return true;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        String path = id.getPath();
        return path.endsWith("_sword") || path.endsWith("_pickaxe") || path.endsWith("_axe");
    }
}
