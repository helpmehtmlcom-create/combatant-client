/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import combatant.client.render.iris.IrisOptionOwnershipPolicy;
import net.irisshaders.iris.gui.element.widget.AbstractElementWidget;
import net.irisshaders.iris.gui.element.widget.OptionMenuConstructor;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuOptionElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = OptionMenuConstructor.class, remap = false)
public abstract class IrisOptionMenuMixin {
    @ModifyReturnValue(method = "createWidget", at = @At("RETURN"), remap = false)
    private static AbstractElementWidget<? extends OptionMenuElement> combatant$hideOwnedShaderpackOptions(
            AbstractElementWidget<? extends OptionMenuElement> original,
            OptionMenuElement element) {
        if (element instanceof OptionMenuOptionElement option
                && IrisOptionOwnershipPolicy.hide(option.optionId)) {
            return AbstractElementWidget.EMPTY;
        }
        return original;
    }
}
