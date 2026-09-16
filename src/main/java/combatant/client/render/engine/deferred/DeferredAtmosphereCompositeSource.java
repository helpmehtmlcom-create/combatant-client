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
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
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
import combatant.client.render.engine.rhi.shader.StorageVolumeBinding;
import combatant.client.render.engine.world.AerialPerspectiveLayout;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Composes opaque/sky/cloud radiance through the integrated participating-media froxel volume. */
final class DeferredAtmosphereCompositeSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = id("deferred/atmosphere_cloud_composite");

    private static final Std430StructLayout DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("froxel", Std430Type.VEC4)
            .member("policy", Std430Type.VEC4)
            .member("aerialLayout", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY)
    ));

    private final DeferredFroxelMediaSource froxelMedia;
    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiStorageBuffer data;

    DeferredAtmosphereCompositeSource(DeferredFroxelMediaSource froxelMedia) {
        if (froxelMedia == null) throw new IllegalArgumentException("froxelMedia");
        this.froxelMedia = froxelMedia;
    }

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.environment.media.composite", DeferredStage.VOLUMETRIC_MEDIA_COMPOSITE)
                .read(DeferredResource.SKY_COMPOSITED_RADIANCE, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.CLOUD_TEMPORAL_RADIANCE, DeferredResource.CLOUD_TEMPORAL_DEPTH,
                        DeferredResource.AERIAL_PERSPECTIVE, DeferredResource.AERIAL_TRANSMITTANCE,
                        DeferredResource.FROXEL_MEDIA_INTEGRATED_RADIANCE,
                        DeferredResource.FROXEL_MEDIA_INTEGRATED_TRANSMITTANCE)
                .write(DeferredResource.SCENE_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.SKY_COMPOSITED_RADIANCE)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.CLOUD_TEMPORAL_RADIANCE)
                        && context.isValid(DeferredResource.CLOUD_TEMPORAL_DEPTH)
                        && context.isValid(DeferredResource.AERIAL_PERSPECTIVE)
                        && context.isValid(DeferredResource.AERIAL_TRANSMITTANCE)
                        && context.isValid(DeferredResource.FROXEL_MEDIA_INTEGRATED_RADIANCE)
                        && context.isValid(DeferredResource.FROXEL_MEDIA_INTEGRATED_TRANSMITTANCE))
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

        GpuTextureView base = requireTexture(context, DeferredResource.SKY_COMPOSITED_RADIANCE);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView cloudRadiance = requireTexture(context, DeferredResource.CLOUD_TEMPORAL_RADIANCE);
        GpuTextureView cloudDepth = requireTexture(context, DeferredResource.CLOUD_TEMPORAL_DEPTH);
        GpuTextureView aerialRadiance = requireTexture(context, DeferredResource.AERIAL_PERSPECTIVE);
        GpuTextureView aerialTransmittance = requireTexture(context, DeferredResource.AERIAL_TRANSMITTANCE);
        RhiStorageVolume integratedRadiance = requireVolume(context, DeferredResource.FROXEL_MEDIA_INTEGRATED_RADIANCE);
        RhiStorageVolume integratedTransmittance = requireVolume(context, DeferredResource.FROXEL_MEDIA_INTEGRATED_TRANSMITTANCE);
        RhiStorageImage output = requireImage(context, DeferredResource.SCENE_RADIANCE);

        DeferredFroxelConfig.Grid grid = froxelMedia.currentGrid();
        Std430Writer writer = new Std430Writer(DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putMat4(0, "inverseView", view.inverseView())
                .putVec4(0, "froxel", grid.width(), grid.height(), grid.depth(), grid.maxDistanceBlocks())
                .putVec4(0, "policy", grid.depthExponent(), isVulkan(context) ? 1.0f : 0.0f, 0.0f, 0.0f)
                .putVec4(0, "aerialLayout", AerialPerspectiveLayout.ZENITH_SLICES,
                        AerialPerspectiveLayout.AZIMUTH_SLICES,
                        Math.max(0.064f, grid.maxDistanceBlocks() * 0.001f), 0.0f);
        RhiStorageBuffer buffer = data();
        buffer.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant participating-media and cloud composite", pipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(7, buffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, base, linear),
                        new SampledTextureBinding(1, depth, nearest),
                        new SampledTextureBinding(2, cloudRadiance, linear),
                        new SampledTextureBinding(3, cloudDepth, nearest),
                        new SampledTextureBinding(8, aerialRadiance, nearest),
                        new SampledTextureBinding(9, aerialTransmittance, nearest)
                ),
                List.of(new StorageImageBinding(6, output, StorageAccess.WRITE_ONLY)),
                List.of(
                        new StorageVolumeBinding(4, integratedRadiance, StorageAccess.READ_ONLY),
                        new StorageVolumeBinding(5, integratedTransmittance, StorageAccess.READ_ONLY)
                )
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline pipeline() {
        if (pipeline == null) pipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-atmosphere-cloud-composite", SHADER, LAYOUT)
        );
        return pipeline;
    }

    private RhiStorageBuffer data() {
        if (data == null) data = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-atmosphere-cloud-composite-data", DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
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

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
    }

    private static RhiStorageVolume requireVolume(DeferredPassContext context, DeferredResource resource) {
        RhiStorageVolume value = context.resources().storageVolume(resource);
        if (value == null) throw new IllegalStateException("Deferred storage volume is not bound: " + resource);
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

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Throwable ignored) { }
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
