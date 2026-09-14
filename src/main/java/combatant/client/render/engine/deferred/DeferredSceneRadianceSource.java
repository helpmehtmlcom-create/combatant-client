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
 * Freezes the opaque-lighting result into a backend-owned radiance source.
 *
 * <p>Reflection/indirect consumers must not sample the mutable scene target directly: later forward
 * and translucent stages are allowed to keep writing it. This pass gives those consumers one stable
 * full-resolution source with a portable storage-image format.</p>
 */
final class DeferredSceneRadianceSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = id("deferred/radiance_capture");
    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.radiance.capture", DeferredStage.RADIANCE_CAPTURE)
                .read(DeferredResource.SCENE_COLOR)
                .write(DeferredResource.SCENE_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> (context.settings().indirectLightEnabled() || context.settings().reflectionsEnabled())
                        && context.resources().texture(DeferredResource.SCENE_COLOR) != null)
                .execute(this::capture)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        pipeline();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void capture(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView source = requireTexture(context, DeferredResource.SCENE_COLOR);
        RhiStorageImage output = requireImage(context, DeferredResource.SCENE_RADIANCE);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant scene radiance capture",
                pipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(),
                List.of(new SampledTextureBinding(0, source, linear)),
                List.of(new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline pipeline() {
        if (owner == null) throw new IllegalStateException("Scene radiance source has no RHI owner");
        if (pipeline == null) {
            pipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-scene-radiance-capture", SHADER, LAYOUT
            ));
        }
        return pipeline;
    }

    private void closeOwned() {
        if (pipeline != null) {
            try { pipeline.close(); } catch (Throwable ignored) { }
            pipeline = null;
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
