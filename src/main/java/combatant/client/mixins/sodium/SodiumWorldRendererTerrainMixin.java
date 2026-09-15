/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.FilterMode;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.deferred.DeferredWorldPipeline;
import combatant.client.render.sodium.SodiumSecondaryTerrainContext;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures Sodium's exact terrain pass boundary without cancelling or replacing the production draw.
 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class SodiumWorldRendererTerrainMixin {
    @Inject(method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDDLnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/blaze3d/textures/GpuSampler;)V", at = @At("HEAD"), remap = false)
    private void combatant$beforeTerrainLayer(ChunkRenderMatrices matrices,
                                              TerrainRenderPass pass,
                                              double cameraX,
                                              double cameraY,
                                              double cameraZ,
                                              FogParameters fog,
                                              GpuSampler sampler,
                                              CallbackInfo ci) {
        if (SodiumSecondaryTerrainContext.active()) return;
        CombatantRenderSystem.sodium().terrainInterop().beforeTerrainDraw(
                matrices, pass, cameraX, cameraY, cameraZ, fog, sampler);
        if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) {
            CombatantRenderSystem.deferredWorld().beforeTranslucency(
                    pass.getTarget().getColorTextureView(),
                    pass.getTarget().getDepthTextureView()
            );
        } else {
            CombatantRenderSystem.deferredWorld().beforeTerrainSubmission();
        }
    }

    @Inject(method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDDLnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/blaze3d/textures/GpuSampler;)V", at = @At("RETURN"), remap = false)
    private void combatant$afterTerrainLayer(ChunkRenderMatrices matrices,
                                             TerrainRenderPass pass,
                                             double cameraX,
                                             double cameraY,
                                             double cameraZ,
                                             FogParameters fog,
                                             GpuSampler sampler,
                                             CallbackInfo ci) {
        if (SodiumSecondaryTerrainContext.active()) return;
        if (pass == DefaultTerrainRenderPasses.CUTOUT && CombatantRenderSystem.deferredWorld().enabled()) {
            DeferredWorldPipeline.LightingState lighting = new DeferredWorldPipeline.LightingState(
                    fog.red(), fog.green(), fog.blue(), fog.alpha(),
                    fog.environmentalStart(), fog.environmentalEnd(),
                    fog.renderStart(), fog.renderEnd()
            );
            CombatantRenderSystem.deferredWorld().resolveLighting(
                    pass.getTarget().getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                    lighting
            );
        }
        CombatantRenderSystem.sodium().terrainInterop().afterTerrainDraw(
                matrices, pass, cameraX, cameraY, cameraZ, fog, sampler);
    }
    @WrapOperation(
            method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDDLnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;getRenderLists()Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/SortedRenderLists;"
            ),
            remap = false
    )
    private SortedRenderLists combatant$secondaryRenderLists(RenderSectionManager manager,
                                                              Operation<SortedRenderLists> original) {
        SodiumSecondaryTerrainContext.State secondary = SodiumSecondaryTerrainContext.current();
        return secondary != null ? secondary.renderLists(manager) : original.call(manager);
    }

}
