/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris.gui;

import combatant.client.compat.iris.gui.IrisGraphicsGuiRenderer;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerEventHandler.class)
public interface IrisShaderPackScreenScrollMixin {
    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void combatant$modernIrisMouseScrolled(double mouseX,
                                                    double mouseY,
                                                    double horizontal,
                                                    double vertical,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ShaderPackScreen screen)) return;
        if (IrisGraphicsGuiRenderer.mouseScrolled(screen, vertical)) {
            cir.setReturnValue(true);
        }
    }
}
