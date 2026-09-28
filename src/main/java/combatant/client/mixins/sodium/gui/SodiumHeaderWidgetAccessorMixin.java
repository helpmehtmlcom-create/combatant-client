/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium.gui;

import combatant.client.compat.sodium.gui.SodiumHeaderWidgetAccess;
import net.caffeinemc.mods.sodium.client.gui.widgets.ResetButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget$HeaderWidget", remap = false)
public interface SodiumHeaderWidgetAccessorMixin extends SodiumHeaderWidgetAccess {
    @Accessor("title")
    @Override String combatant$getTitle();

    @Accessor("resetButton")
    @Override ResetButton combatant$getResetButton();
}
