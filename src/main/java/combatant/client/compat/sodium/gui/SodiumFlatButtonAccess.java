/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.compat.sodium.gui;

import net.minecraft.network.chat.Component;

public interface SodiumFlatButtonAccess {
    Component combatant$getLabel();
    boolean combatant$isSelected();
    boolean combatant$isEnabled();
    boolean combatant$isVisible();
    boolean combatant$isLeftAligned();
}
