/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.compat.sodium.gui;

public interface SodiumScrollbarAccess {
    boolean combatant$isHorizontal();
    int combatant$getVisibleAmount();
    int combatant$getTotalAmount();
    boolean combatant$isDragging();
}
