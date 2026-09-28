/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris;

import combatant.client.render.iris.CombatantIrisUniforms;
import combatant.client.render.iris.geometry.IrisImportedGeometryRenderer;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Finalizes Combatant state after ExtendedShader has bound the real GL program, updated Iris
 * uniforms/custom uniforms and selected the shaderpack framebuffer.
 */
@Pseudo
@Mixin(value = ExtendedShader.class, remap = false)
public abstract class IrisExtendedShaderGeometryMixin {
    @Inject(method = "iris$setupState", at = @At("RETURN"), remap = false)
    private void combatant$finalizeExtendedShaderState(CallbackInfo ci) {
        CombatantIrisUniforms.applyAaToCurrentProgram("ExtendedShader.iris$setupState:return");
        IrisImportedGeometryRenderer.applyCurrentDrawUniforms();
    }
}
