/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris.gui;

import combatant.client.compat.iris.gui.IrisSliderElementWidgetAccess;
import net.irisshaders.iris.gui.element.widget.SliderElementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = SliderElementWidget.class, remap = false)
public interface IrisSliderElementWidgetInvokerMixin extends IrisSliderElementWidgetAccess {
    @Invoker("whileDragging")
    @Override void combatant$whileDragging(int mouseX);

    @Invoker("onReleased")
    @Override void combatant$onReleased();
}
