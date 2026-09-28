/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium.gui;

import combatant.client.compat.sodium.gui.SodiumModIconAccess;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget$ModHeaderWidget", remap = false)
public interface SodiumOptionModHeaderIconAccessorMixin extends SodiumModIconAccess {
    @Accessor("icon")
    @Override Identifier combatant$getIcon();

    @Accessor("iconMonochrome")
    @Override boolean combatant$isIconMonochrome();
}
