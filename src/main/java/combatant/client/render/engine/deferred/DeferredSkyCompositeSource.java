/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Fills only empty primary-view depth with the canonical HDR sky environment. */
final class DeferredSkyCompositeSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("combatant", "shaders/deferred/sky_composite.comp");

    private static final Std430StructLayout DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("flags", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout SKY_STATE_LAYOUT = Std430StructLayout.builder()
            .member("state0", Std430Type.VEC4)
            .member("state1", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiStorageBuffer data;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.environment.sky.composite", DeferredStage.SKY_COMPOSITE)
                .read(DeferredResource.OPAQUE_REFLECTED_RADIANCE, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.SKY_RADIANCE, DeferredResource.SKY_ENVIRONMENT_STATE)
                .write(DeferredResource.SCENE_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.OPAQUE_REFLECTED_RADIANCE)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.SKY_RADIANCE)
                        && context.isValid(DeferredResource.SKY_ENVIRONMENT_STATE))
                .execute(this::composite)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        pipeline();
        data();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void composite(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;

        GpuTextureView base = requireTexture(context, DeferredResource.OPAQUE_REFLECTED_RADIANCE);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView sky = requireTexture(context, DeferredResource.SKY_RADIANCE);
        RhiStorageBuffer skyState = requireBuffer(context, DeferredResource.SKY_ENVIRONMENT_STATE);
        RhiStorageImage output = requireImage(context, DeferredResource.SCENE_RADIANCE);

        Std430Writer writer = new Std430Writer(DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putMat4(0, "inverseView", view.inverseView())
                .putVec4(0, "flags", isVulkan(context) ? 1.0f : 0.0f, 0.0f, 0.0f, 0.0f);
        RhiStorageBuffer buffer = data();
        buffer.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler skySampler = RenderSystem.getSamplerCache().getSampler(
                AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, false
        );
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant HDR sky composite", pipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(
                        new StorageBinding(4, buffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(5, skyState, 0L, SKY_STATE_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(0, base, linear),
                        new SampledTextureBinding(1, depth, nearest),
                        new SampledTextureBinding(2, sky, skySampler)
                ),
                List.of(new StorageImageBinding(3, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline pipeline() {
        if (pipeline == null) pipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-sky-composite", SHADER, LAYOUT)
        );
        return pipeline;
    }

    private RhiStorageBuffer data() {
        if (data == null) data = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-sky-composite-data", DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
        ));
        return data;
    }

    private void closeOwned() {
        close(pipeline); pipeline = null;
        close(data); data = null;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Throwable ignored) { }
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
    }

    private static RhiStorageBuffer requireBuffer(DeferredPassContext context, DeferredResource resource) {
        RhiStorageBuffer value = context.resources().buffer(resource);
        if (value == null) throw new IllegalStateException("Deferred buffer is not bound: " + resource);
        return value;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage value = context.resources().storageImage(resource);
        if (value == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return value;
    }

    private static boolean isVulkan(DeferredPassContext context) {
        String backendName = context.rhi().capabilities().backendName();
        return backendName != null && backendName.toLowerCase(java.util.Locale.ROOT).contains("vulkan");
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }
}
