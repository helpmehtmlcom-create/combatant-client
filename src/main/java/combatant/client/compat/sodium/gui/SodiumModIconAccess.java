/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.compat.sodium.gui;

import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.minecraft.resources.Identifier;

public interface SodiumModIconAccess {
    Identifier combatant$getIcon();
    boolean combatant$isIconMonochrome();
    ModOptions combatant$getModOptions();
}
