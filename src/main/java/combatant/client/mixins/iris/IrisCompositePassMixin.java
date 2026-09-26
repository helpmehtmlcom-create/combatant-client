/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris;

import com.google.common.collect.ImmutableSet;
import combatant.client.render.iris.IrisShaderpackTemporalIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.CompositeRenderer$Pass", remap = false)
public abstract class IrisCompositePassMixin {
    @Shadow String name;
    @Shadow ImmutableSet<Integer> stageReadsFromAlt;

    @Inject(method = "setupState", at = @At("HEAD"), remap = false)
    private void combatant$runShaderpackTemporalAtCompositeBoundary(CallbackInfo ci) {
        IrisShaderpackTemporalIntegration.beforePass(name, stageReadsFromAlt);
    }
}
