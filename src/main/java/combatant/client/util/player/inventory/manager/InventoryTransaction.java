/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory.manager;

public final class InventoryTransaction implements Comparable<InventoryTransaction> {

    public enum Kind {
        SWAP_SCREEN_SLOTS,
        QUICK_MOVE,
        HOTBAR_SWAP,
        THROW,
        CUSTOM
    }

    private final InventoryPriority priority;
    private final Kind kind;
    private final int slotA;
    private final int slotB;
    private final int button;
    private final boolean entireStack;
    private final Runnable customAction;
    private final Object owner;
    private final long timestamp;

    private InventoryTransaction(InventoryPriority priority, Kind kind, int slotA, int slotB,
                                 int button, boolean entireStack, Runnable customAction, Object owner) {
        this.priority = priority != null ? priority : InventoryPriority.NORMAL;
        this.kind = kind;
        this.slotA = slotA;
        this.slotB = slotB;
        this.button = button;
        this.entireStack = entireStack;
        this.customAction = customAction;
        this.owner = owner;
        this.timestamp = System.nanoTime();
    }

    public static InventoryTransaction swapSlots(InventoryPriority priority, int slotA, int slotB, Object owner) {
        return new InventoryTransaction(priority, Kind.SWAP_SCREEN_SLOTS, slotA, slotB, 0, false, null, owner);
    }

    public static InventoryTransaction quickMove(InventoryPriority priority, int slot, Object owner) {
        return new InventoryTransaction(priority, Kind.QUICK_MOVE, slot, -1, 0, false, null, owner);
    }

    public static InventoryTransaction hotbarSwap(InventoryPriority priority, int containerSlot, int hotbarButton, Object owner) {
        return new InventoryTransaction(priority, Kind.HOTBAR_SWAP, containerSlot, -1, hotbarButton, false, null, owner);
    }

    public static InventoryTransaction throwStack(InventoryPriority priority, int slot, boolean entireStack, Object owner) {
        return new InventoryTransaction(priority, Kind.THROW, slot, -1, 0, entireStack, null, owner);
    }

    public static InventoryTransaction custom(InventoryPriority priority, Runnable action, Object owner) {
        return new InventoryTransaction(priority, Kind.CUSTOM, -1, -1, 0, false, action, owner);
    }

    public InventoryPriority priority() {
        return priority;
    }

    public Kind kind() {
        return kind;
    }

    public int slotA() {
        return slotA;
    }

    public int slotB() {
        return slotB;
    }

    public int button() {
        return button;
    }

    public boolean entireStack() {
        return entireStack;
    }

    public Runnable customAction() {
        return customAction;
    }

    public Object owner() {
        return owner;
    }

    public long timestamp() {
        return timestamp;
    }

    @Override
    public int compareTo(InventoryTransaction o) {
        if (this.priority.level() != o.priority.level()) {
            return Integer.compare(this.priority.level(), o.priority.level());
        }
        return Long.compare(this.timestamp, o.timestamp);
    }
}
