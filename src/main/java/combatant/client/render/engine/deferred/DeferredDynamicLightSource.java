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
import combatant.client.render.engine.world.DynamicLightProvider;
import combatant.client.render.engine.world.DynamicLightRegistry;
import combatant.client.render.engine.world.LightDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Explicit analytic-light GPU path: provider descriptors -> tiled lists -> local GGX radiance. */
final class DeferredDynamicLightSource implements AutoCloseable {
    static final int MAX_LIGHTS = 256;
    static final int TILE_SIZE = 16;
    static final int MAX_LIGHTS_PER_TILE = 64;
    private static final int LOCAL_SIZE = 8;

    private static final Identifier CULL_SHADER = id("deferred/local_light_cull");
    private static final Identifier SHADE_SHADER = id("deferred/local_light_shade");

    private static final Std430StructLayout LIGHT_LAYOUT = Std430StructLayout.builder()
            .member("positionRadius", Std430Type.VEC4)
            .member("radianceType", Std430Type.VEC4)
            .member("directionOuter", Std430Type.VEC4)
            .member("coneArea", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout CULL_DATA_LAYOUT = Std430StructLayout.builder()
            .member("projection", Std430Type.MAT4)
            .member("inverseProjection", Std430Type.MAT4)
            .member("viewportLightCount", Std430Type.VEC4)
            .member("tileGrid", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout UINT_LAYOUT = Std430StructLayout.builder()
            .member("value", Std430Type.UINT)
            .build();

    private static final ShaderResourceLayout CULL_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout SHADE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline cullPipeline;
    private RhiComputePipeline shadePipeline;
    private RhiStorageBuffer lightData;
    private RhiStorageBuffer cullData;
    private RhiStorageBuffer tileCounts;
    private RhiStorageBuffer tileIndices;
    private int tileCountX;
    private int tileCountY;
    private int uploadedLightCount;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.local-light.prepare", DeferredStage.POST_LIGHTING)
                .priority(0)
                .write(DeferredResource.LOCAL_LIGHT_DATA, DeferredResource.LOCAL_LIGHT_CULL_DATA)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null)
                .execute(this::prepareFrame)
                .build());
        passes.add(DeferredPassSpec.builder("world.local-light.cull", DeferredStage.POST_LIGHTING)
                .priority(10)
                .read(DeferredResource.LOCAL_LIGHT_DATA, DeferredResource.LOCAL_LIGHT_CULL_DATA)
                .write(DeferredResource.LOCAL_LIGHT_TILE_COUNTS, DeferredResource.LOCAL_LIGHT_TILE_INDICES)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.LOCAL_LIGHT_DATA)
                        && context.isValid(DeferredResource.LOCAL_LIGHT_CULL_DATA))
                .execute(this::cull)
                .build());
        passes.add(DeferredPassSpec.builder("world.local-light.shade", DeferredStage.POST_LIGHTING)
                .priority(20)
                .read(DeferredResource.GBUFFER_SURFACE, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_MATERIAL, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.GBUFFER_DEPTH, DeferredResource.LOCAL_LIGHT_DATA,
                        DeferredResource.LOCAL_LIGHT_CULL_DATA, DeferredResource.LOCAL_LIGHT_TILE_COUNTS,
                        DeferredResource.LOCAL_LIGHT_TILE_INDICES)
                .write(DeferredResource.LOCAL_LIGHTING_COLOR)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.resources().texture(DeferredResource.GBUFFER_SURFACE) != null
                        && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null
                        && context.resources().texture(DeferredResource.GBUFFER_MATERIAL) != null
                        && context.isValid(DeferredResource.LOCAL_LIGHT_TILE_COUNTS)
                        && context.isValid(DeferredResource.LOCAL_LIGHT_TILE_INDICES))
                .execute(this::shade)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        ensureStaticBuffers();
        cullPipeline();
        shadePipeline();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void prepareFrame(DeferredPassContext context) {
        ensureOwner(context.rhi());
        ensureStaticBuffers();
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;

        GpuTextureView reference = context.resources().texture(DeferredResource.SCENE_COLOR);
        if (reference == null) reference = context.resources().texture(DeferredResource.RESOLVED_DEPTH);
        if (reference == null) return;
        int width = reference.getWidth(0);
        int height = reference.getHeight(0);
        ensureTileBuffers(width, height);

        ArrayList<LightDescriptor> collected = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        DynamicLightRegistry.collect(new DynamicLightProvider.Context(
                level, context.worldState(), view.cameraPosition(), context.frame().frameId()
        ), collected::add);

        Vec3 camera = view.cameraPosition();
        collected.sort(Comparator
                .comparingDouble((LightDescriptor light) -> -priority(light, camera))
                .thenComparingLong(LightDescriptor::stableId));
        uploadedLightCount = Math.min(MAX_LIGHTS, collected.size());

        Std430Writer lightWriter = new Std430Writer(LIGHT_LAYOUT, MAX_LIGHTS);
        Matrix4f viewMatrix = view.view();
        for (int i = 0; i < uploadedLightCount; i++) {
            LightDescriptor light = collected.get(i);
            Vector3f position = new Vector3f(
                    (float) (light.x() - camera.x),
                    (float) (light.y() - camera.y),
                    (float) (light.z() - camera.z)
            );
            viewMatrix.transformPosition(position);
            Vector3f direction = new Vector3f(light.directionX(), light.directionY(), light.directionZ());
            if (direction.lengthSquared() <= 1e-8f) direction.set(0.0f, -1.0f, 0.0f);
            direction.normalize();
            viewMatrix.transformDirection(direction).normalize();

            lightWriter.putVec4(i, "positionRadius", position.x, position.y, position.z, light.radius())
                    .putVec4(i, "radianceType", light.red(), light.green(), light.blue(), light.type().ordinal())
                    .putVec4(i, "directionOuter", direction.x, direction.y, direction.z, light.outerConeCos())
                    .putVec4(i, "coneArea", light.innerConeCos(), light.areaRadius(), 0.0f, 0.0f);
        }
        lightData.upload(lightWriter.buffer(), 0L);

        boolean zeroToOne = isVulkan(context);
        Std430Writer cullWriter = new Std430Writer(CULL_DATA_LAYOUT, 1)
                .putMat4(0, "projection", view.projection())
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putVec4(0, "viewportLightCount", width, height, uploadedLightCount, TILE_SIZE)
                .putVec4(0, "tileGrid", tileCountX, tileCountY, MAX_LIGHTS_PER_TILE, zeroToOne ? 1.0f : 0.0f);
        cullData.upload(cullWriter.buffer(), 0L);

        bindBuffers(context);
    }

    private void cull(DeferredPassContext context) {
        ensureOwner(context.rhi());
        bindBuffers(context);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant tiled local-light culling",
                cullPipeline(), Math.max(1, tileCountX), Math.max(1, tileCountY), 1,
                List.of(
                        new StorageBinding(0, lightData, 0L, lightData.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(1, cullData, 0L, cullData.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(2, tileCounts, 0L, tileCounts.descriptor().byteSize(), StorageAccess.WRITE_ONLY),
                        new StorageBinding(3, tileIndices, 0L, tileIndices.descriptor().byteSize(), StorageAccess.WRITE_ONLY)
                ),
                List.of(), List.of()
        ));
    }

    private void shade(DeferredPassContext context) {
        ensureOwner(context.rhi());
        bindBuffers(context);
        GpuTextureView surface = requireTexture(context, DeferredResource.GBUFFER_SURFACE);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView material = requireTexture(context, DeferredResource.GBUFFER_MATERIAL);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        RhiStorageImage output = requireImage(context, DeferredResource.LOCAL_LIGHTING_COLOR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant deferred local-light shading",
                shadePipeline(), groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(
                        new StorageBinding(6, lightData, 0L, lightData.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(7, tileCounts, 0L, tileCounts.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(8, tileIndices, 0L, tileIndices.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(9, cullData, 0L, cullData.descriptor().byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(0, surface, nearest),
                        new SampledTextureBinding(1, geometry, nearest),
                        new SampledTextureBinding(2, material, nearest),
                        new SampledTextureBinding(3, depth, nearest),
                        new SampledTextureBinding(4, gbufferDepth, nearest)
                ),
                List.of(new StorageImageBinding(5, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void bindBuffers(DeferredPassContext context) {
        if (lightData != null) context.resources().bindBuffer(DeferredResource.LOCAL_LIGHT_DATA, lightData);
        if (cullData != null) context.resources().bindBuffer(DeferredResource.LOCAL_LIGHT_CULL_DATA, cullData);
        if (tileCounts != null) context.resources().bindBuffer(DeferredResource.LOCAL_LIGHT_TILE_COUNTS, tileCounts);
        if (tileIndices != null) context.resources().bindBuffer(DeferredResource.LOCAL_LIGHT_TILE_INDICES, tileIndices);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private void ensureStaticBuffers() {
        if (owner == null) throw new IllegalStateException("Dynamic-light source has no RHI owner");
        if (lightData == null) {
            lightData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-local-light-data", LIGHT_LAYOUT, MAX_LIGHTS, StorageAccess.READ_ONLY, false
            ));
        }
        if (cullData == null) {
            cullData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-local-light-cull-data", CULL_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
    }

    private void ensureTileBuffers(int width, int height) {
        int nextX = Math.max(1, (Math.max(width, 1) + TILE_SIZE - 1) / TILE_SIZE);
        int nextY = Math.max(1, (Math.max(height, 1) + TILE_SIZE - 1) / TILE_SIZE);
        if (tileCounts != null && tileIndices != null && nextX == tileCountX && nextY == tileCountY) return;
        close(tileCounts); tileCounts = null;
        close(tileIndices); tileIndices = null;
        tileCountX = nextX;
        tileCountY = nextY;
        int tiles = Math.multiplyExact(tileCountX, tileCountY);
        tileCounts = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-local-light-tile-counts", UINT_LAYOUT, tiles, StorageAccess.READ_WRITE, false
        ));
        tileIndices = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-local-light-tile-indices", UINT_LAYOUT,
                Math.multiplyExact(tiles, MAX_LIGHTS_PER_TILE), StorageAccess.READ_WRITE, false
        ));
    }

    private RhiComputePipeline cullPipeline() {
        if (owner == null) throw new IllegalStateException("Dynamic-light source has no RHI owner");
        if (cullPipeline == null) {
            cullPipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-local-light-cull", CULL_SHADER, CULL_LAYOUT
            ));
        }
        return cullPipeline;
    }

    private RhiComputePipeline shadePipeline() {
        if (owner == null) throw new IllegalStateException("Dynamic-light source has no RHI owner");
        if (shadePipeline == null) {
            shadePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-local-light-shade", SHADE_SHADER, SHADE_LAYOUT
            ));
        }
        return shadePipeline;
    }

    private void closeOwned() {
        close(cullPipeline); cullPipeline = null;
        close(shadePipeline); shadePipeline = null;
        close(lightData); lightData = null;
        close(cullData); cullData = null;
        close(tileCounts); tileCounts = null;
        close(tileIndices); tileIndices = null;
        tileCountX = 0;
        tileCountY = 0;
        uploadedLightCount = 0;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static double priority(LightDescriptor light, Vec3 camera) {
        double dx = light.x() - camera.x;
        double dy = light.y() - camera.y;
        double dz = light.z() - camera.z;
        double distanceSquared = Math.max(1.0, dx * dx + dy * dy + dz * dz);
        double luminance = 0.2126 * light.red() + 0.7152 * light.green() + 0.0722 * light.blue();
        return luminance * light.radius() * light.radius() / distanceSquared;
    }

    private static boolean isVulkan(DeferredPassContext context) {
        String name = context.rhi().capabilities().backendName();
        return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("vulkan");
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
