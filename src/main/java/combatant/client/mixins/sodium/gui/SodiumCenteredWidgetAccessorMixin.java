/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium.gui;

import combatant.client.compat.sodium.gui.SodiumCenteredWidgetAccess;
import net.caffeinemc.mods.sodium.client.gui.widgets.CenteredFlatWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CenteredFlatWidget.class, remap = false)
public interface SodiumCenteredWidgetAccessorMixin extends SodiumCenteredWidgetAccess {
    @Accessor("label")
    @Override Component combatant$getLabel();

    @Accessor("subtitle")
    @Override Component combatant$getSubtitle();

    @Accessor("selected")
    @Override boolean combatant$isSelected();

    @Accessor("enabled")
    @Override boolean combatant$isEnabled();

    @Accessor("visible")
    @Override boolean combatant$isVisible();
}
