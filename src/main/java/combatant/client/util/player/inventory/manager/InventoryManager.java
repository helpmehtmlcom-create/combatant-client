/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.screen.ClientScreen;

import java.util.PriorityQueue;

public final class InventoryManager {

    public static final InventoryManager INSTANCE = new InventoryManager();

    private final Minecraft mc = Minecraft.getInstance();
    private final Object lock = new Object();
    private final PriorityQueue<InventoryTransaction> queue = new PriorityQueue<>();

    private int delayTicks = 1;
    private int cooldown = 0;
    private boolean pauseWhileUsingItem = true;
    private boolean pauseOnMove = false;

    private InventoryManager() {
    }

    public int getDelayTicks() {
        return delayTicks;
    }

    public void setDelayTicks(int delayTicks) {
        this.delayTicks = Math.max(0, delayTicks);
    }

    public boolean isPauseWhileUsingItem() {
        return pauseWhileUsingItem;
    }

    public void setPauseWhileUsingItem(boolean pauseWhileUsingItem) {
        this.pauseWhileUsingItem = pauseWhileUsingItem;
    }

    public boolean isPauseOnMove() {
        return pauseOnMove;
    }

    public void setPauseOnMove(boolean pauseOnMove) {
        this.pauseOnMove = pauseOnMove;
    }

    public void submit(InventoryTransaction transaction) {
        if (transaction == null) return;
        synchronized (lock) {
            queue.add(transaction);
        }
    }

    public void submitSwap(InventoryPriority priority, int slotA, int slotB, Object owner) {
        submit(InventoryTransaction.swapSlots(priority, slotA, slotB, owner));
    }

    public void submitQuickMove(InventoryPriority priority, int slot, Object owner) {
        submit(InventoryTransaction.quickMove(priority, slot, owner));
    }

    public void submitHotbarSwap(InventoryPriority priority, int containerSlot, int hotbarButton, Object owner) {
        submit(InventoryTransaction.hotbarSwap(priority, containerSlot, hotbarButton, owner));
    }

    public void submitThrow(InventoryPriority priority, int slot, boolean entireStack, Object owner) {
        submit(InventoryTransaction.throwStack(priority, slot, entireStack, owner));
    }

    public void submitCustom(InventoryPriority priority, Runnable action, Object owner) {
        submit(InventoryTransaction.custom(priority, action, owner));
    }

    public void clear(Object owner) {
        if (owner == null) return;
        synchronized (lock) {
            queue.removeIf(t -> t.owner() == owner);
        }
    }

    public void clearAll() {
        synchronized (lock) {
            queue.clear();
        }
    }

    public boolean hasPending() {
        synchronized (lock) {
            return !queue.isEmpty();
        }
    }

    public boolean hasPending(InventoryPriority minPriority) {
        if (minPriority == null) return hasPending();
        synchronized (lock) {
            for (InventoryTransaction t : queue) {
                if (t.priority().level() <= minPriority.level()) {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean isBusy() {
        return hasPending();
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        tick();
    }

    public void tick() {
        if (mc.player == null || mc.gameMode == null) {
            clearAll();
            return;
        }

        LocalPlayer player = mc.player;
        if (!player.isAlive() || player.isSpectator()) {
            clearAll();
            return;
        }

        // Safety check: pause if using items (eating, drinking, aiming bow)
        if (pauseWhileUsingItem && player.isUsingItem()) {
            return;
        }

        // Safety check: pause if moving on strict anti-cheats
        if (pauseOnMove && player.input != null && player.input.keyPresses != null
                && (player.input.keyPresses.forward() || player.input.keyPresses.backward() || player.input.keyPresses.left() || player.input.keyPresses.right())) {
            return;
        }

        // Check carried item desync recovery
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != null && !menu.getCarried().isEmpty()) {
            recoverCarriedDesync(player, menu);
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        InventoryTransaction next;
        synchronized (lock) {
            next = queue.poll();
        }

        if (next == null) return;

        executeTransaction(player, menu, next);
        cooldown = delayTicks;
    }

    private void executeTransaction(LocalPlayer player, AbstractContainerMenu menu, InventoryTransaction tx) {
        if (player == null || mc.gameMode == null) return;
        if (menu == null) menu = player.inventoryMenu;
        if (menu == null) return;

        int containerId = menu.containerId;

        try {
            switch (tx.kind()) {
                case SWAP_SCREEN_SLOTS -> {
                    int slotA = tx.slotA();
                    int slotB = tx.slotB();
                    if (isValidSlot(menu, slotA) && isValidSlot(menu, slotB)) {
                        mc.gameMode.handleContainerInput(containerId, slotA, 0, ContainerInput.PICKUP, player);
                        mc.gameMode.handleContainerInput(containerId, slotB, 0, ContainerInput.PICKUP, player);
                        mc.gameMode.handleContainerInput(containerId, slotA, 0, ContainerInput.PICKUP, player);
                    }
                }
                case QUICK_MOVE -> {
                    int slot = tx.slotA();
                    if (isValidSlot(menu, slot)) {
                        mc.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
                    }
                }
                case HOTBAR_SWAP -> {
                    int slot = tx.slotA();
                    int button = tx.button();
                    if (isValidSlot(menu, slot)) {
                        mc.gameMode.handleContainerInput(containerId, slot, button, ContainerInput.SWAP, player);
                    }
                }
                case THROW -> {
                    int slot = tx.slotA();
                    if (isValidSlot(menu, slot)) {
                        int button = tx.entireStack() ? 1 : 0;
                        mc.gameMode.handleContainerInput(containerId, slot, button, ContainerInput.THROW, player);
                    }
                }
                case CUSTOM -> {
                    if (tx.customAction() != null) {
                        tx.customAction().run();
                    }
                }
            }
        } catch (Throwable t) {
            DebugLog.error("Error executing inventory transaction: " + tx.kind(), t);
        }
    }

    /**
     * Recovers from carried item desync by clicking the first empty inventory slot.
     */
    private void recoverCarriedDesync(LocalPlayer player, AbstractContainerMenu menu) {
        if (player == null || menu == null || mc.gameMode == null) return;
        int emptySlot = -1;

        for (int i = 9; i < 45; i++) {
            if (isValidSlot(menu, i)) {
                Slot slot = menu.getSlot(i);
                if (slot != null && !slot.hasItem()) {
                    emptySlot = i;
                    break;
                }
            }
        }

        if (emptySlot >= 0) {
            mc.gameMode.handleContainerInput(menu.containerId, emptySlot, 0, ContainerInput.PICKUP, player);
        }
    }

    private boolean isValidSlot(AbstractContainerMenu menu, int slotIndex) {
        return menu != null && slotIndex >= 0 && slotIndex < menu.slots.size();
    }
}
