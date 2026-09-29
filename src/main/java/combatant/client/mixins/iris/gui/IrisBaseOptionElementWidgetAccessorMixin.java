/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris.gui;

import combatant.client.compat.iris.gui.IrisBaseOptionWidgetAccess;
import net.irisshaders.iris.gui.element.widget.BaseOptionElementWidget;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BaseOptionElementWidget.class, remap = false)
public interface IrisBaseOptionElementWidgetAccessorMixin extends IrisBaseOptionWidgetAccess {
    @Accessor(value = "unmodifiedLabel", remap = false)
    @Override MutableComponent combatant$getUnmodifiedLabel();

    @Accessor(value = "valueLabel", remap = false)
    @Override net.minecraft.network.chat.Component combatant$getValueLabel();
}
