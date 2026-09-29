/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium.gui;

import combatant.client.compat.sodium.gui.SodiumSearchWidgetAccess;
import net.caffeinemc.mods.sodium.client.gui.widgets.SearchWidget;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SearchWidget.class, remap = false)
public interface SodiumSearchWidgetAccessorMixin extends SodiumSearchWidgetAccess {
    @Accessor("query")
    @Override String combatant$getQuery();

    @Accessor("searchBox")
    @Override EditBox combatant$getSearchBox();
}
