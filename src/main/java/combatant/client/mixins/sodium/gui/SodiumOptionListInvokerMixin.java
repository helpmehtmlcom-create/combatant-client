/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium.gui;

import combatant.client.compat.sodium.gui.SodiumOptionListAccess;
import net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = OptionListWidget.class, remap = false)
public interface SodiumOptionListInvokerMixin extends SodiumOptionListAccess {
    @Invoker("updateSectionFocus")
    @Override
    void combatant$updateSectionFocus(int scrollAmount);
}
