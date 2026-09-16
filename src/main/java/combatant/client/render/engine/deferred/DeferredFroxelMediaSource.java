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
import combatant.client.render.engine.world.DirectionalLightDescriptor;
import combatant.client.render.engine.world.environment.CloudProfile;
import combatant.client.render.engine.world.environment.LocalFogVolumeDescriptor;
import combatant.client.render.engine.world.environment.LocalFogVolumeProvider;
import combatant.client.render.engine.world.environment.LocalFogVolumeRegistry;
import combatant.client.render.engine.world.environment.LocalFogVolumeShape;
import combatant.client.render.engine.world.environment.ParticipatingMediaRange;
import combatant.client.render.engine.world.environment.ParticipatingMediumProfile;
import combatant.client.render.engine.world.environment.ParticipatingMediumProfileRegistry;
import combatant.client.render.engine.world.environment.WeatherFieldState;
import combatant.client.render.engine.world.environment.WeatherState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds, lights and integrates the view-aligned participating-media froxel volume. */
final class DeferredFroxelMediaSource implements AutoCloseable {
    private static final int INJECT_LOCAL_XY = 4;
    private static final int INJECT_LOCAL_Z = 4;
    private static final int LIGHT_LOCAL_XY = 4;
    private static final int LIGHT_LOCAL_Z = 4;
    private static final int INTEGRATE_LOCAL = 8;
    private static final Identifier INJECT_SHADER = id("deferred/froxel_media_inject");
    private static final Identifier LIGHT_SHADER = id("deferred/froxel_media_light");
    private static final Identifier LIGHT_SHADOWED_SHADER = id("deferred/froxel_media_light_shadowed");
    private static final Identifier LOCAL_LIGHT_SHADER = id("deferred/froxel_media_local_light");
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
            .member("mediumDistribution", Std430Type.VEC4)
            .member("mediumScattering", Std430Type.VEC4)
            .member("mediumAbsorption", Std430Type.VEC4)
            .member("mediumWeather", Std430Type.VEC4)
            .member("mediumWeatherState", Std430Type.VEC4)
            .member("mediumLighting", Std430Type.VEC4)
            .member("directionalDirection", Std430Type.VEC4)
            .member("directionalRadiance", Std430Type.VEC4)
            .build();

    private static final Std430StructLayout LOCAL_FOG_VOLUME_LAYOUT = Std430StructLayout.builder()
            .member("centerShape", Std430Type.VEC4)
            .member("extentDensity", Std430Type.VEC4)
            .member("scatteringG", Std430Type.VEC4)
            .member("absorptionEdge", Std430Type.VEC4)
            .member("emission", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout INJECT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(12, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout LIGHT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_WRITE),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout LIGHT_SHADOWED_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_WRITE),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout LOCAL_LIGHT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_WRITE),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
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
    private RhiComputePipeline lightPipeline;
    private RhiComputePipeline lightShadowedPipeline;
    private RhiComputePipeline localLightPipeline;
    private RhiComputePipeline integratePipeline;
    private RhiStorageBuffer data;
    private RhiStorageBuffer localFogVolumeData;
    private long localFogFrameId = Long.MIN_VALUE;
    private int localFogVolumeCount;
    private RhiStorageVolume segmentRadiance;
    private RhiStorageVolume segmentTransmittance;
    private RhiStorageVolume mediaProperties;
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
                .priority(0)
                .read(DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.AERIAL_PERSPECTIVE,
                        DeferredResource.AERIAL_TRANSMITTANCE,
                        DeferredResource.CLOUD_SHADOW_MAP)
                .write(DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE,
                        DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE,
                        DeferredResource.FROXEL_MEDIA_PROPERTIES)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> config.enabled()
                        && context.primaryView().current() != null
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.AERIAL_PERSPECTIVE)
                        && context.isValid(DeferredResource.AERIAL_TRANSMITTANCE)
                        && context.isValid(DeferredResource.CLOUD_SHADOW_MAP))
                .execute(this::inject)
                .build());
        passes.add(DeferredPassSpec.builder("world.media.froxel.light", DeferredStage.VOLUMETRIC_MEDIA_INJECT)
                .priority(10)
                .read(DeferredResource.FROXEL_MEDIA_PROPERTIES,
                        DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE,
                        DeferredResource.CLOUD_SHADOW_MAP,
                        DeferredResource.SKY_DIFFUSE_SH,
                        DeferredResource.SHADOW_DEPTH,
                        DeferredResource.SHADOW_CASCADE_DATA)
                .readWrite(DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(this::mediumLightingAvailable)
                .execute(context -> injectLighting(context, directionalShadowAvailable(context)))
                .build());
        passes.add(DeferredPassSpec.builder("world.media.froxel.local-light", DeferredStage.VOLUMETRIC_MEDIA_INJECT)
                .priority(20)
                .read(DeferredResource.FROXEL_MEDIA_PROPERTIES,
                        DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE,
                        DeferredResource.LOCAL_LIGHT_DATA,
                        DeferredResource.LOCAL_LIGHT_CULL_DATA,
                        DeferredResource.LOCAL_LIGHT_TILE_COUNTS,
                        DeferredResource.LOCAL_LIGHT_TILE_INDICES,
                        DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.LOCAL_LIGHT_SHADOW_DEPTH,
                        DeferredResource.LOCAL_LIGHT_SHADOW_DATA)
                .readWrite(DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(this::localLightingAvailable)
                .execute(this::injectLocalLighting)
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
        lightPipeline();
        lightShadowedPipeline();
        localLightPipeline();
        integratePipeline();
        data();
        localFogVolumeData();
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
        float mediaRange = ParticipatingMediaRange.resolveBlocks(context.worldState(), view.farPlane());
        DeferredFroxelConfig.Grid grid = config.grid(depth.getWidth(0), depth.getHeight(0), mediaRange);
        frameGrid = grid;
        ensureTransportVolumes(grid);
        prepareLocalFogVolumes(context, view);

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
                                cloudField.layerData().descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(12, localFogVolumeData(), 0L,
                                localFogVolumeData().descriptor().byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(3, aerialRadiance, nearest),
                        new SampledTextureBinding(4, aerialTransmittance, nearest),
                        new SampledTextureBinding(7, cloudShadowMap, linear)
                ),
                List.of(),
                List.of(
                        new StorageVolumeBinding(1, segmentRadiance, StorageAccess.WRITE_ONLY),
                        new StorageVolumeBinding(2, segmentTransmittance, StorageAccess.WRITE_ONLY),
                        new StorageVolumeBinding(11, mediaProperties, StorageAccess.WRITE_ONLY)
                )
        ));
        context.resources().bindStorageVolume(DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE, segmentRadiance);
        context.resources().bindStorageVolume(DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE, segmentTransmittance);
        context.resources().bindStorageVolume(DeferredResource.FROXEL_MEDIA_PROPERTIES, mediaProperties);
    }

    private void injectLighting(DeferredPassContext context, boolean shadowed) {
        ensureOwner(context.rhi());
        DeferredFroxelConfig.Grid grid = currentGrid();
        RhiStorageVolume radiance = requireVolume(context, DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE);
        RhiStorageVolume properties = requireVolume(context, DeferredResource.FROXEL_MEDIA_PROPERTIES);
        RhiStorageVolume transmittance = requireVolume(context, DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE);
        GpuTextureView cloudShadowMap = requireTexture(context, DeferredResource.CLOUD_SHADOW_MAP);
        RhiStorageBuffer skyDiffuseSh = requireBuffer(context, DeferredResource.SKY_DIFFUSE_SH);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        ArrayList<StorageBinding> buffers = new ArrayList<>();
        buffers.add(new StorageBinding(0, data(), 0L, DATA_LAYOUT.arrayStride(), StorageAccess.READ_ONLY));
        buffers.add(new StorageBinding(4, skyDiffuseSh, 0L, skyDiffuseSh.descriptor().byteSize(), StorageAccess.READ_ONLY));
        ArrayList<SampledTextureBinding> textures = new ArrayList<>();
        textures.add(new SampledTextureBinding(3, cloudShadowMap, linear));
        if (shadowed) {
            RhiStorageBuffer cascadeData = requireBuffer(context, DeferredResource.SHADOW_CASCADE_DATA);
            GpuTextureView shadowDepth = requireTexture(context, DeferredResource.SHADOW_DEPTH);
            buffers.add(new StorageBinding(6, cascadeData, 0L, cascadeData.descriptor().byteSize(), StorageAccess.READ_ONLY));
            textures.add(new SampledTextureBinding(5, shadowDepth,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)));
        }

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                shadowed ? "Combatant shadowed froxel media lighting" : "Combatant froxel media lighting",
                shadowed ? lightShadowedPipeline() : lightPipeline(),
                groups(grid.width(), LIGHT_LOCAL_XY),
                groups(grid.height(), LIGHT_LOCAL_XY),
                groups(grid.depth(), LIGHT_LOCAL_Z),
                List.copyOf(buffers), List.copyOf(textures), List.of(),
                List.of(
                        new StorageVolumeBinding(1, radiance, StorageAccess.READ_WRITE),
                        new StorageVolumeBinding(2, properties, StorageAccess.READ_ONLY),
                        new StorageVolumeBinding(7, transmittance, StorageAccess.READ_ONLY)
                )
        ));
    }

    private void injectLocalLighting(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredFroxelConfig.Grid grid = currentGrid();
        RhiStorageVolume radiance = requireVolume(context, DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE);
        RhiStorageVolume properties = requireVolume(context, DeferredResource.FROXEL_MEDIA_PROPERTIES);
        RhiStorageVolume transmittance = requireVolume(context, DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE);
        RhiStorageBuffer lightData = requireBuffer(context, DeferredResource.LOCAL_LIGHT_DATA);
        RhiStorageBuffer tileCounts = requireBuffer(context, DeferredResource.LOCAL_LIGHT_TILE_COUNTS);
        RhiStorageBuffer tileIndices = requireBuffer(context, DeferredResource.LOCAL_LIGHT_TILE_INDICES);
        RhiStorageBuffer cullData = requireBuffer(context, DeferredResource.LOCAL_LIGHT_CULL_DATA);
        RhiStorageBuffer shadowData = requireBuffer(context, DeferredResource.LOCAL_LIGHT_SHADOW_DATA);
        GpuTextureView shadowDepth = context.resources().texture(DeferredResource.LOCAL_LIGHT_SHADOW_DEPTH);
        if (shadowDepth == null) shadowDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant froxel local-light scattering", localLightPipeline(),
                groups(grid.width(), LIGHT_LOCAL_XY),
                groups(grid.height(), LIGHT_LOCAL_XY),
                groups(grid.depth(), LIGHT_LOCAL_Z),
                List.of(
                        new StorageBinding(0, data(), 0L, DATA_LAYOUT.arrayStride(), StorageAccess.READ_ONLY),
                        new StorageBinding(4, lightData, 0L, lightData.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(5, tileCounts, 0L, tileCounts.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(6, tileIndices, 0L, tileIndices.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(7, cullData, 0L, cullData.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(9, shadowData, 0L, shadowData.descriptor().byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(new SampledTextureBinding(8, shadowDepth, nearest)),
                List.of(),
                List.of(
                        new StorageVolumeBinding(1, radiance, StorageAccess.READ_WRITE),
                        new StorageVolumeBinding(2, properties, StorageAccess.READ_ONLY),
                        new StorageVolumeBinding(3, transmittance, StorageAccess.READ_ONLY)
                )
        ));
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
        CloudProfile cloudProfile = cloud.profile();
        ParticipatingMediumProfile medium = ParticipatingMediumProfileRegistry.resolve(context.worldState().mediumProfile());
        DirectionalLightDescriptor directional = context.worldState().directionalLight();
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
                .putVec4(0, "counts", cloud.layerCount(), cloudProfile.maxRayDistanceBlocks(), localFogVolumeCount,
                        cloud.active() ? 1.0f : 0.0f)
                .putVec4(0, "noiseDomain",
                        wrapOrigin(field.valid() ? field.originBlockX() : 0),
                        wrapOrigin(field.valid() ? field.originBlockZ() : 0),
                        seedPhase(weather.modelSeed()), 0.0f)
                .putVec4(0, "froxel", froxelGrid.width(), froxelGrid.height(), froxelGrid.depth(),
                        froxelGrid.maxDistanceBlocks())
                .putVec4(0, "policy", froxelGrid.depthExponent(), isVulkan(context) ? 1.0f : 0.0f,
                        context.worldState().atmosphereState().valid() ? 1.0f : 0.0f,
                        context.settings().shadowCascadeBlendFraction())
                .putVec4(0, "shadowMapDomain", shadow.mapOriginRelativeX(), shadow.mapOriginRelativeZ(),
                        shadow.spanBlocks(), shadow.referenceY())
                .putVec4(0, "shadowSun", shadow.sunX(), shadow.sunY(), shadow.sunZ(), shadow.active() ? 1.0f : 0.0f)
                .putVec4(0, "cloudBounds", shadow.minCloudY(), shadow.maxCloudY(), 0.0f, shadow.altitudeSlices())
                .putVec4(0, "aerialLayout", AerialPerspectiveLayout.ZENITH_SLICES,
                        AerialPerspectiveLayout.AZIMUTH_SLICES, maxDistanceKm, 0.0f)
                .putVec4(0, "mediumDistribution", medium.referenceHeightBlocks(), medium.scaleHeightBlocks(),
                        medium.baseDensity(), medium.valid() ? 1.0f : 0.0f)
                .putVec4(0, "mediumScattering", medium.scatteringRed(), medium.scatteringGreen(),
                        medium.scatteringBlue(), medium.anisotropy())
                .putVec4(0, "mediumAbsorption", medium.absorptionRed(), medium.absorptionGreen(),
                        medium.absorptionBlue(), 0.0f)
                .putVec4(0, "mediumWeather", medium.humidityThreshold(), medium.humidityDensity(),
                        medium.precipitationDensity(), medium.stormDensity())
                .putVec4(0, "mediumWeatherState", weather.camera().humidity(), weather.camera().stormPotential(),
                        weather.camera().precipitationIntensity(), weather.camera().valid() ? 1.0f : 0.0f)
                .putVec4(0, "mediumLighting", medium.ambientRadianceScale(), medium.directionalRadianceScale(),
                        medium.maxDistanceBlocks(), 0.0f)
                .putVec4(0, "directionalDirection", directional.directionX(), directional.directionY(),
                        directional.directionZ(), directional.valid() ? 1.0f : 0.0f)
                .putVec4(0, "directionalRadiance", directional.radianceRed(), directional.radianceGreen(),
                        directional.radianceBlue(), directional.valid() ? 1.0f : 0.0f);
    }

    private boolean mediumVolumeAvailable(DeferredPassContext context) {
        if (!config.enabled() || context.primaryView().current() == null) return false;
        if (!context.isValid(DeferredResource.FROXEL_MEDIA_SEGMENT_RADIANCE)
                || !context.isValid(DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE)
                || !context.isValid(DeferredResource.FROXEL_MEDIA_PROPERTIES)) {
            return false;
        }
        return localFogVolumeCount > 0
                || ParticipatingMediumProfileRegistry.resolve(context.worldState().mediumProfile()).valid();
    }

    private boolean mediumLightingAvailable(DeferredPassContext context) {
        return mediumVolumeAvailable(context)
                && context.isValid(DeferredResource.CLOUD_SHADOW_MAP)
                && context.isValid(DeferredResource.SKY_DIFFUSE_SH);
    }

    private static boolean directionalShadowAvailable(DeferredPassContext context) {
        return context.worldState().directionalLight().shadowValid()
                && context.isValid(DeferredResource.SHADOW_DEPTH)
                && context.isValid(DeferredResource.SHADOW_CASCADE_DATA);
    }

    private boolean localLightingAvailable(DeferredPassContext context) {
        if (!mediumVolumeAvailable(context)
                || !context.isValid(DeferredResource.LOCAL_LIGHT_DATA)
                || !context.isValid(DeferredResource.LOCAL_LIGHT_CULL_DATA)
                || !context.isValid(DeferredResource.LOCAL_LIGHT_TILE_COUNTS)
                || !context.isValid(DeferredResource.LOCAL_LIGHT_TILE_INDICES)) {
            return false;
        }
        return context.resources().buffer(DeferredResource.LOCAL_LIGHT_SHADOW_DATA) != null;
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
                && segmentRadiance != null && segmentTransmittance != null && mediaProperties != null
                && integratedRadiance != null && integratedTransmittance != null) {
            return;
        }
        close(segmentRadiance); segmentRadiance = null;
        close(segmentTransmittance); segmentTransmittance = null;
        close(mediaProperties); mediaProperties = null;
        close(integratedRadiance); integratedRadiance = null;
        close(integratedTransmittance); integratedTransmittance = null;

        segmentRadiance = createVolume("combatant-froxel-segment-radiance", grid);
        segmentTransmittance = createVolume("combatant-froxel-segment-transmittance", grid);
        mediaProperties = createVolume("combatant-froxel-media-properties", grid);
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

    private RhiComputePipeline lightPipeline() {
        if (lightPipeline == null) lightPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-froxel-media-light", LIGHT_SHADER, LIGHT_LAYOUT)
        );
        return lightPipeline;
    }

    private RhiComputePipeline lightShadowedPipeline() {
        if (lightShadowedPipeline == null) lightShadowedPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-froxel-media-light-shadowed", LIGHT_SHADOWED_SHADER,
                        LIGHT_SHADOWED_LAYOUT)
        );
        return lightShadowedPipeline;
    }

    private RhiComputePipeline localLightPipeline() {
        if (localLightPipeline == null) localLightPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-froxel-media-local-light", LOCAL_LIGHT_SHADER,
                        LOCAL_LIGHT_LAYOUT)
        );
        return localLightPipeline;
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

    private RhiStorageBuffer localFogVolumeData() {
        if (localFogVolumeData == null) localFogVolumeData = owner.advancedShaders().createStorageBuffer(
                new StorageBufferDescriptor(
                        "combatant-local-fog-volumes", LOCAL_FOG_VOLUME_LAYOUT,
                        config.maxLocalFogVolumes(), StorageAccess.READ_ONLY, false
                )
        );
        return localFogVolumeData;
    }

    private void prepareLocalFogVolumes(DeferredPassContext context,
                                        DeferredPrimaryViewSource.FrameView view) {
        long frameId = context.frame().frameId();
        if (localFogFrameId == frameId) return;

        ArrayList<LocalFogVolumeDescriptor> volumes = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        Vec3 camera = view.cameraPosition();
        LocalFogVolumeRegistry.collect(new LocalFogVolumeProvider.Context(
                level, context.worldState(), camera, frameId
        ), descriptor -> {
            if (descriptor != null && descriptor.valid()) volumes.add(descriptor);
        });

        volumes.sort(Comparator
                .comparingDouble((LocalFogVolumeDescriptor volume) -> -volume.priority())
                .thenComparingDouble(volume -> volume.distanceToBoundsSquared(camera.x, camera.y, camera.z))
                .thenComparingLong(LocalFogVolumeDescriptor::stableId));
        if (volumes.size() > config.maxLocalFogVolumes()) {
            volumes.subList(config.maxLocalFogVolumes(), volumes.size()).clear();
        }

        localFogVolumeCount = volumes.size();
        if (localFogVolumeCount > 0) {
            Std430Writer writer = new Std430Writer(LOCAL_FOG_VOLUME_LAYOUT, localFogVolumeCount);
            for (int i = 0; i < localFogVolumeCount; i++) {
                LocalFogVolumeDescriptor volume = volumes.get(i);
                writer.putVec4(i, "centerShape",
                                (float) (volume.centerX() - camera.x),
                                (float) (volume.centerY() - camera.y),
                                (float) (volume.centerZ() - camera.z),
                                volume.shape() == LocalFogVolumeShape.BOX ? 1.0f : 0.0f)
                        .putVec4(i, "extentDensity",
                                volume.extentX(), volume.extentY(), volume.extentZ(), volume.density())
                        .putVec4(i, "scatteringG",
                                volume.scatteringRed(), volume.scatteringGreen(),
                                volume.scatteringBlue(), volume.anisotropy())
                        .putVec4(i, "absorptionEdge",
                                volume.absorptionRed(), volume.absorptionGreen(),
                                volume.absorptionBlue(), volume.edgeFadeBlocks())
                        .putVec4(i, "emission",
                                volume.emissionRed(), volume.emissionGreen(), volume.emissionBlue(), 1.0f);
            }
            localFogVolumeData().upload(writer.buffer(), 0L);
        } else {
            localFogVolumeData();
        }
        localFogFrameId = frameId;
    }

    private void closeOwned() {
        close(injectPipeline); injectPipeline = null;
        close(lightPipeline); lightPipeline = null;
        close(lightShadowedPipeline); lightShadowedPipeline = null;
        close(localLightPipeline); localLightPipeline = null;
        close(integratePipeline); integratePipeline = null;
        close(data); data = null;
        close(localFogVolumeData); localFogVolumeData = null;
        localFogFrameId = Long.MIN_VALUE;
        localFogVolumeCount = 0;
        close(segmentRadiance); segmentRadiance = null;
        close(segmentTransmittance); segmentTransmittance = null;
        close(mediaProperties); mediaProperties = null;
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

    private static RhiStorageBuffer requireBuffer(DeferredPassContext context, DeferredResource resource) {
        RhiStorageBuffer value = context.resources().buffer(resource);
        if (value == null) throw new IllegalStateException("Deferred storage buffer is not bound: " + resource);
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
