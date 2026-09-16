/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.*;
import combatant.client.render.engine.world.AerialPerspectiveLayout;
import combatant.client.render.engine.world.environment.CloudProfile;
import combatant.client.render.engine.world.environment.WeatherFieldState;
import combatant.client.render.engine.world.environment.WeatherState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Builds and integrates the view-aligned participating-media froxel volume. */
final class DeferredFroxelMediaSource implements AutoCloseable {
    private static final int INJECT_LOCAL_XY = 4;
    private static final int INJECT_LOCAL_Z = 4;
    private static final int INTEGRATE_LOCAL = 8;
    private static final Identifier INJECT_SHADER = id("deferred/froxel_media_inject");
    private static final Identifier INTEGRATE_SHADER = id("deferred/froxel_media_integrate");

    private static final Std430StructLayout DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("cameraTime", Std430Type.VEC4)
            .member("grid", Std430Type.VEC4)
            .member("counts", Std430Type.VEC4)
            .member("noiseDomain", Std430Type.VEC4)
            .member("froxel", Std430Type.VEC4)
            .member("policy", Std430Type.VEC4)
            .member("shadowMapDomain", Std430Type.VEC4)
            .member("shadowSun", Std430Type.VEC4)
            .member("cloudBounds", Std430Type.VEC4)
            .member("aerialLayout", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout INJECT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout INTEGRATE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.WRITE_ONLY)
    ));

    private final DeferredFroxelConfig config = DeferredFroxelConfig.current();
    private final DeferredCloudFieldSource cloudField;
    private final DeferredCloudShadowSource cloudShadows;

    private CombatantRhi owner;
    private RhiComputePipeline injectPipeline;
    private RhiComputePipeline integratePipeline;
    private RhiStorageBuffer data;
    private RhiStorageVolume segmentRadiance;
    private RhiStorageVolume segmentTransmittance;
    private RhiStorageVolume integratedRadiance;
    private RhiStorageVolume integratedTransmittance;
    private DeferredFroxelConfig.Grid allocatedGrid;
    private DeferredFroxelConfig.Grid frameGrid;

    DeferredFroxelMediaSource(DeferredCloudFieldSource cloudField, DeferredCloudShadowSource cloudShadows) {
        if (cloudField == null) throw new IllegalArgumentException("cloudField");
        if (cloudShadows == null) throw new IllegalArgumentException("cloudShadows");
        this.cloudField = cloudField;
        this.cloudShadows = cloudShadows;
    }

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.media.froxel.inject", DeferredStage.VOLUMETRIC_MEDIA_INJECT)
                .read(DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.AERIAL_PERSPECTIVE,
                        DeferredResource.AERIAL_TRANSMITTANCE,
                        DeferredResource.CLOUD_SHADOW_MAP)
                .write(DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE,
                        DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> config.enabled()
                        && context.primaryView().current() != null
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.AERIAL_PERSPECTIVE)
                        && context.isValid(DeferredResource.AERIAL_TRANSMITTANCE)
                        && context.isValid(DeferredResource.CLOUD_SHADOW_MAP))
                .execute(this::inject)
                .build());
        passes.add(DeferredPassSpec.builder("world.media.froxel.integrate", DeferredStage.VOLUMETRIC_MEDIA_INTEGRATE)
                .read(DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE,
                        DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE)
                .write(DeferredResource.FROXEL_MEDIA_INTEGRATED_RADIANCE,
                        DeferredResource.FROXEL_MEDIA_INTEGRATED_TRANSMITTANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> config.enabled()
                        && context.primaryView().current() != null
                        && context.isValid(DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE)
                        && context.isValid(DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE))
                .execute(this::integrate)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        injectPipeline();
        integratePipeline();
        data();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    DeferredFroxelConfig.Grid currentGrid() {
        if (frameGrid == null) throw new IllegalStateException("Froxel grid is not prepared for the current frame");
        return frameGrid;
    }

    private void inject(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;

        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView aerialRadiance = requireTexture(context, DeferredResource.AERIAL_PERSPECTIVE);
        GpuTextureView aerialTransmittance = requireTexture(context, DeferredResource.AERIAL_TRANSMITTANCE);
        GpuTextureView cloudShadowMap = requireTexture(context, DeferredResource.CLOUD_SHADOW_MAP);
        DeferredCloudFieldSource.FrameData cloud = cloudField.prepareFrame(context);
        DeferredCloudShadowSource.FrameState shadow = cloudShadows.frameState(context, view, cloud);
        float mediaRange = view.farPlane();
        if (cloud.active()) mediaRange = Math.max(mediaRange, cloud.profile().maxRayDistanceBlocks());
        DeferredFroxelConfig.Grid grid = config.grid(depth.getWidth(0), depth.getHeight(0), mediaRange);
        frameGrid = grid;
        ensureTransportVolumes(grid);

        Std430Writer writer = writer(context, view, grid, cloud, shadow);
        RhiStorageBuffer dataBuffer = data();
        dataBuffer.upload(writer.buffer(), 0L);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant froxel media injection", injectPipeline(),
                groups(grid.width(), INJECT_LOCAL_XY),
                groups(grid.height(), INJECT_LOCAL_XY),
                groups(grid.depth(), INJECT_LOCAL_Z),
                List.of(
                        new StorageBinding(0, dataBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(5, cloudField.weatherData(), 0L,
                                cloudField.weatherData().descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(6, cloudField.layerData(), 0L,
                                cloudField.layerData().descriptor().byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(3, aerialRadiance, nearest),
                        new SampledTextureBinding(4, aerialTransmittance, nearest),
                        new SampledTextureBinding(7, cloudShadowMap, linear)
                ),
                List.of(),
                List.of(
                        new StorageVolumeBinding(1, segmentRadiance, StorageAccess.WRITE_ONLY),
                        new StorageVolumeBinding(2, segmentTransmittance, StorageAccess.WRITE_ONLY)
                )
        ));
        context.resources().bindStorageVolume(DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE, segmentRadiance);
        context.resources().bindStorageVolume(DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE, segmentTransmittance);
    }

    private void integrate(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredFroxelConfig.Grid grid = currentGrid();
        ensureTransportVolumes(grid);

        RhiStorageVolume segmentRadianceVolume = requireVolume(context, DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE);
        RhiStorageVolume segmentTransmittanceVolume = requireVolume(context, DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant froxel media integration", integratePipeline(),
                groups(grid.width(), INTEGRATE_LOCAL), groups(grid.height(), INTEGRATE_LOCAL), 1,
                List.of(new StorageBinding(0, data(), 0L, DATA_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                List.of(), List.of(),
                List.of(
                        new StorageVolumeBinding(1, segmentRadianceVolume, StorageAccess.READ_ONLY),
                        new StorageVolumeBinding(2, segmentTransmittanceVolume, StorageAccess.READ_ONLY),
                        new StorageVolumeBinding(3, integratedRadiance, StorageAccess.WRITE_ONLY),
                        new StorageVolumeBinding(4, integratedTransmittance, StorageAccess.WRITE_ONLY)
                )
        ));
        context.resources().bindStorageVolume(DeferredResource.FROXEL_MEDIA_INTEGRATED_RADIANCE, integratedRadiance);
        context.resources().bindStorageVolume(DeferredResource.FROXEL_MEDIA_INTEGRATED_TRANSMITTANCE, integratedTransmittance);
    }

    private Std430Writer writer(DeferredPassContext context,
                                DeferredPrimaryViewSource.FrameView view,
                                DeferredFroxelConfig.Grid froxelGrid,
                                DeferredCloudFieldSource.FrameData cloud,
                                DeferredCloudShadowSource.FrameState shadow) {
        WeatherState weather = cloud.weather();
        WeatherFieldState field = cloud.field();
        CloudProfile profile = cloud.profile();
        Vec3 camera = view.cameraPosition();
        float originX = field.valid() ? field.originBlockX() : (float) camera.x;
        float originZ = field.valid() ? field.originBlockZ() : (float) camera.z;
        float spacing = field.valid() ? Math.max(1, field.spacingBlocks()) : 1.0f;
        int width = field.valid() ? field.gridWidth() : 0;
        int depth = field.valid() ? field.gridDepth() : 0;
        float timeSeconds = weather.valid() ? weather.modelTimeTicks() / 20.0f : 0.0f;
        float maxDistanceKm = Math.max(0.064f, froxelGrid.maxDistanceBlocks() * 0.001f);

        return new Std430Writer(DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putMat4(0, "inverseView", view.inverseView())
                .putVec4(0, "cameraTime", (float) (camera.x - originX), (float) camera.y,
                        (float) (camera.z - originZ), timeSeconds)
                .putVec4(0, "grid", spacing, width, depth, cloud.weatherCount())
                .putVec4(0, "counts", cloud.layerCount(), profile.maxRayDistanceBlocks(), 0.0f,
                        cloud.active() ? 1.0f : 0.0f)
                .putVec4(0, "noiseDomain",
                        wrapOrigin(field.valid() ? field.originBlockX() : 0),
                        wrapOrigin(field.valid() ? field.originBlockZ() : 0),
                        seedPhase(weather.modelSeed()), 0.0f)
                .putVec4(0, "froxel", froxelGrid.width(), froxelGrid.height(), froxelGrid.depth(),
                        froxelGrid.maxDistanceBlocks())
                .putVec4(0, "policy", froxelGrid.depthExponent(), isVulkan(context) ? 1.0f : 0.0f,
                        context.worldState().atmosphereState().valid() ? 1.0f : 0.0f,
                        config.enabled() ? 1.0f : 0.0f)
                .putVec4(0, "shadowMapDomain", shadow.mapOriginRelativeX(), shadow.mapOriginRelativeZ(),
                        shadow.spanBlocks(), shadow.referenceY())
                .putVec4(0, "shadowSun", shadow.sunX(), shadow.sunY(), shadow.sunZ(), shadow.active() ? 1.0f : 0.0f)
                .putVec4(0, "cloudBounds", shadow.minCloudY(), shadow.maxCloudY(), 0.0f, shadow.altitudeSlices())
                .putVec4(0, "aerialLayout", AerialPerspectiveLayout.ZENITH_SLICES,
                        AerialPerspectiveLayout.AZIMUTH_SLICES, maxDistanceKm, 0.0f);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private void ensureTransportVolumes(DeferredFroxelConfig.Grid grid) {
        if (owner == null) throw new IllegalStateException("Froxel source has no RHI owner");
        if (allocatedGrid != null
                && allocatedGrid.width() == grid.width()
                && allocatedGrid.height() == grid.height()
                && allocatedGrid.depth() == grid.depth()
                && segmentRadiance != null && segmentTransmittance != null
                && integratedRadiance != null && integratedTransmittance != null) {
            return;
        }
        close(segmentRadiance); segmentRadiance = null;
        close(segmentTransmittance); segmentTransmittance = null;
        close(integratedRadiance); integratedRadiance = null;
        close(integratedTransmittance); integratedTransmittance = null;

        segmentRadiance = createVolume("combatant-froxel-segment-radiance", grid);
        segmentTransmittance = createVolume("combatant-froxel-segment-transmittance", grid);
        integratedRadiance = createVolume("combatant-froxel-integrated-radiance", grid);
        integratedTransmittance = createVolume("combatant-froxel-integrated-transmittance", grid);
        allocatedGrid = grid;
    }

    private RhiStorageVolume createVolume(String label, DeferredFroxelConfig.Grid grid) {
        return owner.advancedShaders().createStorageVolume(new StorageVolumeDescriptor(
                label, grid.width(), grid.height(), grid.depth(), GpuFormat.RGBA16_FLOAT, StorageAccess.READ_WRITE
        ));
    }

    private RhiComputePipeline injectPipeline() {
        if (injectPipeline == null) injectPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-froxel-media-inject", INJECT_SHADER, INJECT_LAYOUT)
        );
        return injectPipeline;
    }

    private RhiComputePipeline integratePipeline() {
        if (integratePipeline == null) integratePipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-froxel-media-integrate", INTEGRATE_SHADER, INTEGRATE_LAYOUT)
        );
        return integratePipeline;
    }

    private RhiStorageBuffer data() {
        if (data == null) data = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-froxel-media-data", DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
        ));
        return data;
    }

    private void closeOwned() {
        close(injectPipeline); injectPipeline = null;
        close(integratePipeline); integratePipeline = null;
        close(data); data = null;
        close(segmentRadiance); segmentRadiance = null;
        close(segmentTransmittance); segmentTransmittance = null;
        close(integratedRadiance); integratedRadiance = null;
        close(integratedTransmittance); integratedTransmittance = null;
        allocatedGrid = null;
        frameGrid = null;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static RhiStorageVolume requireVolume(DeferredPassContext context, DeferredResource resource) {
        RhiStorageVolume value = context.resources().storageVolume(resource);
        if (value == null) throw new IllegalStateException("Deferred storage volume is not bound: " + resource);
        return value;
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
    }

    private static float seedPhase(long seed) {
        long mixed = seed ^ (seed >>> 33) ^ (seed << 11);
        return (float) (mixed & 0x00FF_FFFFL) / 16777216.0f;
    }

    private static float wrapOrigin(int value) {
        return Math.floorMod(value, 65536);
    }

    private static boolean isVulkan(DeferredPassContext context) {
        String backendName = context.rhi().capabilities().backendName();
        return backendName != null && backendName.toLowerCase(java.util.Locale.ROOT).contains("vulkan");
    }

    private static int groups(int extent, int localSize) {
        return Math.max(1, (Math.max(1, extent) + localSize - 1) / localSize);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Throwable ignored) { }
    }
}
