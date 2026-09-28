/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris;

import combatant.client.render.iris.IrisShaderpackMsaaIntegration;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import org.lwjgl.opengl.GL30C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = GlFramebuffer.class, remap = false)
public abstract class IrisGlFramebufferMsaaMixin {
    @Inject(method = "drawBuffers", at = @At("TAIL"), remap = false)
    private void combatant$configureSingleDrawBuffers(int[] buffers, CallbackInfo ci) {
        IrisShaderpackMsaaIntegration.configureDrawBuffers((GlFramebuffer) (Object) this, buffers);
    }

    @Inject(method = "readBuffer", at = @At("TAIL"), remap = false)
    private void combatant$configureSingleReadBuffer(int buffer, CallbackInfo ci) {
        IrisShaderpackMsaaIntegration.configureReadBuffer((GlFramebuffer) (Object) this, buffer);
    }

    @Inject(method = "noDrawBuffers", at = @At("TAIL"), remap = false)
    private void combatant$configureSingleNoDrawBuffers(CallbackInfo ci) {
        IrisShaderpackMsaaIntegration.configureNoDrawBuffers((GlFramebuffer) (Object) this);
    }

    @Inject(method = "bind", at = @At("HEAD"), cancellable = true, remap = false)
    private void combatant$bindSelectedFramebuffer(CallbackInfo ci) {
        if (IrisShaderpackMsaaIntegration.bindFallback((GlFramebuffer) (Object) this, GL30C.GL_FRAMEBUFFER)) ci.cancel();
    }

    @Inject(method = "bindAsReadBuffer", at = @At("HEAD"), cancellable = true, remap = false)
    private void combatant$bindSelectedReadFramebuffer(CallbackInfo ci) {
        if (IrisShaderpackMsaaIntegration.bindFallback((GlFramebuffer) (Object) this, GL30C.GL_READ_FRAMEBUFFER)) ci.cancel();
    }

    @Inject(method = "bindAsDrawBuffer", at = @At("HEAD"), cancellable = true, remap = false)
    private void combatant$bindSelectedDrawFramebuffer(CallbackInfo ci) {
        if (IrisShaderpackMsaaIntegration.bindFallback((GlFramebuffer) (Object) this, GL30C.GL_DRAW_FRAMEBUFFER)) ci.cancel();
    }

    @Inject(method = "destroyInternal", at = @At("HEAD"), remap = false)
    private void combatant$destroySingleFramebufferTwin(CallbackInfo ci) {
        IrisShaderpackMsaaIntegration.framebufferDestroyed((GlFramebuffer) (Object) this);
    }
}
