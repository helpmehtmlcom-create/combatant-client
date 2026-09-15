/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable environment-light contract for deferred shading.
 *
 * <p>The current sky fallback deliberately emits zero irradiance instead of reconstructing ambient
 * light from Minecraft's lightmap. A future atmosphere/sky producer can write
 * {@link DeferredResource#SKY_DIFFUSE_IRRADIANCE}; the compose pass remains the stable hand-off to
 * deferred lighting and is where additional environment sources (for example a colored block-light
 * volume) are combined later.</p>
 */
final class DeferredEnvironmentIrradianceSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SKY_FALLBACK_SHADER = id("deferred/environment_sky_fallback");
    private static final Identifier ENVIRONMENT_COMPOSE_SHADER = id("deferred/environment_irradiance_compose");

    private static final ShaderResourceLayout SKY_FALLBACK_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout ENVIRONMENT_COMPOSE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline skyFallbackPipeline;
    private RhiComputePipeline environmentComposePipeline;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.environment.sky-diffuse.fallback", DeferredStage.PRE_LIGHTING)
                .priority(900)
                .write(DeferredResource.SKY_DIFFUSE_IRRADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> !context.isValid(DeferredResource.SKY_DIFFUSE_IRRADIANCE))
                .execute(this::writeNeutralSkyFallback)
                .build());
        passes.add(DeferredPassSpec.builder("world.environment.irradiance.compose", DeferredStage.PRE_LIGHTING)
                .priority(1000)
                .read(DeferredResource.SKY_DIFFUSE_IRRADIANCE)
                .write(DeferredResource.ENVIRONMENT_IRRADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.SKY_DIFFUSE_IRRADIANCE))
                .execute(this::composeEnvironment)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        skyFallbackPipeline();
        environmentComposePipeline();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void writeNeutralSkyFallback(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageImage output = requireImage(context, DeferredResource.SKY_DIFFUSE_IRRADIANCE);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant neutral sky irradiance fallback",
                skyFallbackPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(), List.of(),
                List.of(new StorageImageBinding(0, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void composeEnvironment(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView sky = requireTexture(context, DeferredResource.SKY_DIFFUSE_IRRADIANCE);
        RhiStorageImage output = requireImage(context, DeferredResource.ENVIRONMENT_IRRADIANCE);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant environment irradiance compose",
                environmentComposePipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(),
                List.of(new SampledTextureBinding(0, sky, linear)),
                List.of(new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline skyFallbackPipeline() {
        if (owner == null) throw new IllegalStateException("Environment irradiance source has no RHI owner");
        if (skyFallbackPipeline == null) {
            skyFallbackPipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-environment-sky-fallback", SKY_FALLBACK_SHADER, SKY_FALLBACK_LAYOUT
            ));
        }
        return skyFallbackPipeline;
    }

    private RhiComputePipeline environmentComposePipeline() {
        if (owner == null) throw new IllegalStateException("Environment irradiance source has no RHI owner");
        if (environmentComposePipeline == null) {
            environmentComposePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-environment-irradiance-compose", ENVIRONMENT_COMPOSE_SHADER, ENVIRONMENT_COMPOSE_LAYOUT
            ));
        }
        return environmentComposePipeline;
    }

    private void closeOwned() {
        if (skyFallbackPipeline != null) {
            try { skyFallbackPipeline.close(); } catch (Throwable ignored) { }
            skyFallbackPipeline = null;
        }
        if (environmentComposePipeline != null) {
            try { environmentComposePipeline.close(); } catch (Throwable ignored) { }
            environmentComposePipeline = null;
        }
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage value = context.resources().storageImage(resource);
        if (value == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return value;
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
