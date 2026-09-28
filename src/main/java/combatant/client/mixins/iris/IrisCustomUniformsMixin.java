/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris;

import combatant.client.render.iris.CombatantIrisUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-asserts Combatant AA ownership after Iris shaderpack custom uniforms have been pushed. */
@Pseudo
@Mixin(value = CustomUniforms.class, remap = false)
public abstract class IrisCustomUniformsMixin {
    @Inject(method = "push", at = @At("RETURN"), remap = false)
    private void combatant$reassertAaOwnership(Object target, CallbackInfo ci) {
        CombatantIrisUniforms.applyAaToCurrentProgram("CustomUniforms.push:return");
    }
}
