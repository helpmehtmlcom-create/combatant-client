/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory.manager;

public enum InventoryPriority {
    /**
     * Critical survival tasks: Totem swaps, emergency gapple / shield offhand swaps.
     * Takes absolute precedence over all other inventory tasks.
     */
    CRITICAL(0),

    /**
     * High priority combat defense: AutoArmor equip/unequip, durability preservation.
     */
    HIGH(1),

    /**
     * Normal operational tasks: AutoReplenish hotbar refill.
     */
    NORMAL(2),

    /**
     * Background utility tasks: InventorySorter, InventoryCleaner, ChestStealer.
     */
    LOW(3);

    private final int level;

    InventoryPriority(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean isHigherThan(InventoryPriority other) {
        return other != null && this.level < other.level;
    }
}
