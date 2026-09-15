/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import combatant.client.render.sodium.fluid.WaterSurfaceExtractor;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps extracted patch geometry lifetime identical to Sodium RenderSection lifetime. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSection")
public abstract class SodiumRenderSectionSurfaceMixin {
    @Inject(method = "delete()V", at = @At("HEAD"), remap = false)
    private void combatant$discardExtractedSurfaces(CallbackInfo ci) {
        RenderSection section = (RenderSection)(Object)this;
        WaterSurfaceExtractor.discardSection(section.getPosition().asLong());
    }
}
