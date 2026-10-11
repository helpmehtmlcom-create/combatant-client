/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

/** Transient, module-owned player-inventory slot reservations (0..40). */
public final class InventorySlotLocks {
    public static final int OFFHAND = 40;
    private static final Map<Object, Reservation> RESERVATIONS = new IdentityHashMap<>();
    private static Player activePlayer;

    private record Reservation(int slot, String reason) {}

    private InventorySlotLocks() {}

    private static void validateSession() {
        Player player = Minecraft.getInstance().player;
        if (player != activePlayer) {
            RESERVATIONS.clear();
            activePlayer = player;
        }
    }

    public static void claim(Object owner, int slot, String reason) {
        if (owner == null || slot < 0 || slot > OFFHAND) return;
        validateSession();
        if (activePlayer != null) RESERVATIONS.put(owner, new Reservation(slot, reason));
    }

    public static void release(Object owner) {
        if (owner == null) return;
        validateSession();
        RESERVATIONS.remove(owner);
    }

    public static boolean isLocked(int slot) {
        return reason(slot) != null;
    }

    public static String reason(int slot) {
        validateSession();
        if (activePlayer == null) return null;
        for (Reservation reservation : RESERVATIONS.values()) {
            if (reservation.slot == slot) return reservation.reason;
        }
        return null;
    }

    public static boolean blocks(int slot, Object actor) {
        validateSession();
        if (activePlayer == null) return false;
        for (Map.Entry<Object, Reservation> entry : RESERVATIONS.entrySet()) {
            if (entry.getValue().slot == slot && entry.getKey() != actor) return true;
        }
        return false;
    }

    public static boolean isLocked(Slot slot, Player player) {
        int index = playerSlotIndex(slot, player);
        return index >= 0 && isLocked(index);
    }

    public static int playerSlotIndex(Slot slot, Player player) {
        return InventoryComponentMap.playerInventoryIndex(slot, player);
    }

    public static int playerSlotIndex(AbstractContainerMenu menu, int menuSlot, Player player) {
        return InventoryComponentMap.playerInventoryIndex(menu, menuSlot, player);
    }

    public static boolean hasLockedPlayerSlot() {
        validateSession();
        if (activePlayer == null) return false;
        return !RESERVATIONS.isEmpty();
    }

    /** Check both the clicked slot and the other endpoint of a number-key/F swap. */
    public static boolean blocks(AbstractContainerMenu menu, int menuSlot, int button,
                                 ContainerInput input, Player player, Object actor) {
        if (menu == null || player == null || input == null) return false;
        int source = playerSlotIndex(menu, menuSlot, player);
        if (source >= 0 && blocks(source, actor)) return true;
        if (input == ContainerInput.SWAP) {
            if (button == OFFHAND) return blocks(OFFHAND, actor);
            if (button >= 0 && button < 9) return blocks(button, actor);
        }
        // QUICK_CRAFT should be rejected only if it actually touches a locked slot.
        // Blocking every drag operation whenever offhand is reserved effectively locks
        // unrelated hotbar slots, including the fifth one.
        if (input == ContainerInput.PICKUP_ALL) {
            // Collect-all scans other slots. Block it only if a reserved slot contains
            // a stack compatible with the carried stack and could be emptied by it.
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty() && carried.getCount() < carried.getMaxStackSize()) {
                for (Slot slot : menu.slots) {
                    int index = playerSlotIndex(slot, player);
                    if (index >= 0 && blocks(index, actor) && slot.hasItem()
                            && ItemStack.isSameItemSameComponents(carried, slot.getItem())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Render only for an identified slot, never by comparing ItemStack identities. */
    public static void renderLockOverlay(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, 0x714D2BB1);
        graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 1, 0xCABB8CFF);
    }
}
