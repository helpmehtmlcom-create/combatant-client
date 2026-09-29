/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris.gui;

import combatant.client.compat.iris.gui.IrisStringOptionWidgetAccess;
import net.irisshaders.iris.gui.element.widget.StringElementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = StringElementWidget.class, remap = false)
public interface IrisStringElementWidgetAccessorMixin extends IrisStringOptionWidgetAccess {
    @Accessor("valueIndex")
    @Override int combatant$getValueIndex();

    @Accessor("valueCount")
    @Override int combatant$getValueCount();
}
