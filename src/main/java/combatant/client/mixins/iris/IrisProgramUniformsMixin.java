/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris;

import combatant.client.render.iris.CombatantIrisUniforms;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Photon render-scale and jitter ownership synchronized with Combatant for every Iris program. */
@Pseudo
@Mixin(value = ProgramUniforms.class, remap = false)
public abstract class IrisProgramUniformsMixin {
    @Inject(method = "update", at = @At("RETURN"), remap = false)
    private void combatant$applyAaOwnership(CallbackInfo ci) {
        CombatantIrisUniforms.applyAaToCurrentProgram("ProgramUniforms.update");
    }
}
