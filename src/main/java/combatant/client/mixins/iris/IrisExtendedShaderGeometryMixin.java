/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris;

import combatant.client.render.iris.geometry.IrisImportedGeometryRenderer;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the imported-material selector after Iris has bound and updated the actual program. */
@Pseudo
@Mixin(value = ExtendedShader.class, remap = false)
public abstract class IrisExtendedShaderGeometryMixin {
    @Inject(method = "iris$setupState", at = @At("RETURN"), remap = false)
    private void combatant$selectImportedMaterial(CallbackInfo ci) {
        int program = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        if (program <= 0) return;
        int location = GL20C.glGetUniformLocation(program, "combatantCustomGeometry");
        if (location >= 0) {
            GL20C.glUniform1i(location, IrisImportedGeometryRenderer.isDrawingImportedGeometry() ? 1 : 0);
        }
    }
}
