/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris.gui;

import combatant.client.compat.iris.gui.IrisProfileOptionWidgetAccess;
import net.irisshaders.iris.gui.element.widget.ProfileElementWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ProfileElementWidget.class, remap = false)
public interface IrisProfileElementWidgetAccessorMixin extends IrisProfileOptionWidgetAccess {
    @Accessor("profileLabel")
    @Override Component combatant$getProfileLabel();
}
