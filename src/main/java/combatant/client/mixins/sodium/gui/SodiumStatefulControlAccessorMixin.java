/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium.gui;

import combatant.client.compat.sodium.gui.SodiumStatefulControlAccess;
import net.caffeinemc.mods.sodium.client.gui.options.control.StatefulControlElement;
import net.caffeinemc.mods.sodium.client.gui.widgets.ResetButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = StatefulControlElement.class, remap = false)
public interface SodiumStatefulControlAccessorMixin extends SodiumStatefulControlAccess {
    @Accessor("resetButton")
    @Override ResetButton combatant$getResetButton();
}
