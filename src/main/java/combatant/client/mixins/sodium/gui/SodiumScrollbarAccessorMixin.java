/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium.gui;

import combatant.client.compat.sodium.gui.SodiumScrollbarAccess;
import net.caffeinemc.mods.sodium.client.gui.widgets.ScrollbarWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ScrollbarWidget.class, remap = false)
public interface SodiumScrollbarAccessorMixin extends SodiumScrollbarAccess {
    @Accessor("horizontal")
    @Override boolean combatant$isHorizontal();

    @Accessor("visible")
    @Override int combatant$getVisibleAmount();

    @Accessor("total")
    @Override int combatant$getTotalAmount();

    @Accessor("dragging")
    @Override boolean combatant$isDragging();
}
