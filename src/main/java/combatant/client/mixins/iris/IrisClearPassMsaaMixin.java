/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris;

import combatant.client.render.iris.IrisShaderpackMsaaIntegration;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.targets.ClearPass;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps detached MSAA color attachments on the exact same clear lifecycle as Iris targets. */
@Pseudo
@Mixin(value = ClearPass.class, remap = false)
public abstract class IrisClearPassMsaaMixin {
    @Shadow @Final private Vector4f color;
    @Shadow @Final private GlFramebuffer framebuffer;
    @Shadow @Final private int clearFlags;

    @Inject(method = "execute", at = @At("TAIL"), remap = false)
    private void combatant$mirrorShaderpackClear(Vector4f defaultColor, CallbackInfo ci) {
        Vector4f effective = color != null ? color : defaultColor;
        if (effective == null) return;
        IrisShaderpackMsaaIntegration.mirrorShaderpackClear(
                framebuffer, clearFlags, effective.x, effective.y, effective.z, effective.w);
    }
}
