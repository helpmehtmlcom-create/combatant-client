/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.material.MaterialAtlasManager;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.pipeline.VertexLayoutSpec;
import combatant.client.render.engine.rhi.shader.*;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import combatant.client.render.sodium.fluid.HeightSurfacePatchRouting;
import combatant.client.render.sodium.fluid.SurfacePatchRouting;
import combatant.client.render.sodium.fluid.WaterSurfaceExtractor;
import combatant.client.render.sodium.fluid.WaterSurfacePatchRouting;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native patch consumer for explicit height-displacement terrain and extracted water surfaces. */
final class DeferredPatchSurfaceSource implements AutoCloseable {
    private static final Identifier PATCH_VERTEX = id("deferred/patch_surface");
    private static final Identifier PATCH_TESS_CONTROL = id("deferred/patch_surface");
    private static final Identifier HEIGHT_TESS_EVALUATION = id("deferred/height_surface");
    private static final Identifier WATER_TESS_EVALUATION = id("deferred/water_surface");
    private static final Identifier HEIGHT_FRAGMENT = id("deferred/height_surface");
    private static final Identifier WATER_FRAGMENT = id("deferred/water_surface");

    private static final VertexLayoutSpec LAYOUT = VertexLayoutSpec.of(
            "combatant:water_patch", CombatantVertexFormats.WATER_PATCH, PrimitiveTopology.QUADS);

    private static final Std430StructLayout CAMERA_LAYOUT = Std430StructLayout.builder()
            .member("viewRotation", Std430Type.MAT4)
            .member("projection", Std430Type.MAT4)
            .member("cameraTime", Std430Type.VEC4)
            .member("viewport", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout HEIGHT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout WATER_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiPatchPipeline heightPipeline;
    private RhiPatchPipeline waterPipeline;
    private PipelineKey heightKey;
    private PipelineKey waterKey;
    private RhiStorageBuffer cameraBuffer;
    private long heightActivationGeneration = Long.MIN_VALUE;
    private long waterActivationGeneration = Long.MIN_VALUE;
    private boolean heightNativeFailed;
    private boolean waterNativeFailed;
    private final MeshBuilder heightMesh = new MeshBuilder(CombatantVertexFormats.WATER_PATCH, PrimitiveTopology.QUADS);
    private final MeshBuilder waterMesh = new MeshBuilder(CombatantVertexFormats.WATER_PATCH, PrimitiveTopology.QUADS);

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.height-surface", DeferredStage.HEIGHT_SURFACE)
                .read(DeferredResource.MAIN_DEPTH)
                .write(DeferredResource.SCENE_COLOR,
                        DeferredResource.GBUFFER_SURFACE,
                        DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_AUXILIARY,
                        DeferredResource.GBUFFER_MATERIAL,
                        DeferredResource.GBUFFER_MATERIAL_ID,
                        DeferredResource.MAIN_DEPTH)
                .requires(RhiShaderStage.VERTEX, RhiShaderStage.TESS_CONTROL,
                        RhiShaderStage.TESS_EVALUATION, RhiShaderStage.FRAGMENT)
                .when(this::heightAvailable)
                .execute(this::drawHeight)
                .build());

        passes.add(DeferredPassSpec.builder("world.water-surface", DeferredStage.WATER_SURFACE)
                .read(DeferredResource.MAIN_DEPTH,
                        DeferredResource.REFLECTION_COLOR,
                        DeferredResource.SCENE_RADIANCE)
                .write(DeferredResource.SCENE_COLOR)
                .requires(RhiShaderStage.VERTEX, RhiShaderStage.TESS_CONTROL,
                        RhiShaderStage.TESS_EVALUATION, RhiShaderStage.FRAGMENT)
                .when(this::waterAvailable)
                .execute(this::drawWater)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        heightNativeFailed = false;
        waterNativeFailed = false;
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
        resetRoutingState();
    }

    private boolean heightAvailable(DeferredPassContext context) {
        return !heightNativeFailed
                && baseAvailable(context)
                && context.resources().texture(DeferredResource.GBUFFER_SURFACE) != null
                && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null
                && context.resources().texture(DeferredResource.GBUFFER_AUXILIARY) != null
                && context.resources().texture(DeferredResource.GBUFFER_MATERIAL) != null
                && context.resources().texture(DeferredResource.GBUFFER_MATERIAL_ID) != null
                && WaterSurfaceExtractor.hasHeightPatches();
    }

    private boolean waterAvailable(DeferredPassContext context) {
        return !waterNativeFailed
                && baseAvailable(context)
                && context.resources().texture(DeferredResource.SCENE_RADIANCE) != null
                && WaterSurfaceExtractor.hasWaterPatches();
    }

    private boolean baseAvailable(DeferredPassContext context) {
        return context.rhi().capabilities().nativeTessellationSubmission()
                && context.primaryView().current() != null
                && context.resources().texture(DeferredResource.SCENE_COLOR) != null
                && context.resources().texture(DeferredResource.MAIN_DEPTH) != null
                && MaterialAtlasManager.global().ready()
                && blockAtlasTexture() != null;
    }

    private void drawHeight(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView camera = context.primaryView().current();
        if (camera == null) return;

        if (!HeightSurfacePatchRouting.replacementActive()) {
            heightActivationGeneration = WaterSurfaceExtractor.generation();
            HeightSurfacePatchRouting.setReplacementActive(true);
            return;
        }

        List<WaterSurfaceExtractor.HeightPatch> patches = collectHeightPatches(heightActivationGeneration);
        if (patches.isEmpty()) return;

        GpuTextureView scene = requireTexture(context, DeferredResource.SCENE_COLOR);
        GpuTextureView depth = requireTexture(context, DeferredResource.MAIN_DEPTH);
        List<GpuTextureView> colors = List.of(
                scene,
                requireTexture(context, DeferredResource.GBUFFER_SURFACE),
                requireTexture(context, DeferredResource.GBUFFER_GEOMETRY),
                requireTexture(context, DeferredResource.GBUFFER_AUXILIARY),
                requireTexture(context, DeferredResource.GBUFFER_MATERIAL),
                requireTexture(context, DeferredResource.GBUFFER_MATERIAL_ID)
        );

        RhiPatchPipeline pipeline;
        try {
            pipeline = heightPipeline(colors, depth);
            uploadCamera(context, camera, scene);
            GpuMeshHandle mesh = buildHeightMesh(patches, camera.cameraPosition());
            context.advancedShaders().drawPatches(new PatchDrawCommand(
                    "Combatant height-displacement surfaces", pipeline, colors, depth, mesh,
                    List.of(new StorageBinding(5, cameraBuffer(), 0L, CAMERA_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                    heightTextures(), List.of()
            ));
            HeightSurfacePatchRouting.setReplacementActive(true);
        } catch (Throwable error) {
            heightNativeFailed = true;
            heightActivationGeneration = Long.MIN_VALUE;
            HeightSurfacePatchRouting.setReplacementActive(false);
            DebugLog.warnOnChange(
                    "deferred.height-surface.native.failed",
                    error.getClass().getSimpleName() + "|" + error.getMessage(),
                    "[Deferred] native height-surface replacement failed; Sodium fallback restored: %s: %s",
                    error.getClass().getSimpleName(), error.getMessage());
        }
    }

    private void drawWater(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView camera = context.primaryView().current();
        if (camera == null) return;

        if (!WaterSurfacePatchRouting.replacementActive()) {
            waterActivationGeneration = WaterSurfaceExtractor.generation();
            WaterSurfacePatchRouting.setReplacementActive(true);
            return;
        }

        List<WaterSurfaceExtractor.WaterPatch> patches = collectWaterPatches(waterActivationGeneration);
        if (patches.isEmpty()) return;

        GpuTextureView scene = requireTexture(context, DeferredResource.SCENE_COLOR);
        GpuTextureView depth = requireTexture(context, DeferredResource.MAIN_DEPTH);
        RhiPatchPipeline pipeline;
        try {
            pipeline = waterPipeline(scene, depth);
            uploadCamera(context, camera, scene);
            GpuMeshHandle mesh = buildWaterMesh(patches, camera.cameraPosition());
            List<SampledTextureBinding> textures = new ArrayList<>(waterMaterialTextures());
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuTextureView radiance = requireTexture(context, DeferredResource.SCENE_RADIANCE);
            GpuTextureView reflection = context.resources().texture(DeferredResource.REFLECTION_COLOR);
            textures.add(new SampledTextureBinding(5, reflection != null ? reflection : radiance, linear));
            textures.add(new SampledTextureBinding(6, radiance, linear));
            context.advancedShaders().drawPatches(new PatchDrawCommand(
                    "Combatant water surfaces", pipeline, scene, depth, mesh,
                    List.of(new StorageBinding(7, cameraBuffer(), 0L, CAMERA_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                    textures, List.of()
            ));
            WaterSurfacePatchRouting.setReplacementActive(true);
        } catch (Throwable error) {
            waterNativeFailed = true;
            waterActivationGeneration = Long.MIN_VALUE;
            WaterSurfacePatchRouting.setReplacementActive(false);
            DebugLog.warnOnChange(
                    "deferred.water-surface.native.failed",
                    error.getClass().getSimpleName() + "|" + error.getMessage(),
                    "[Deferred] native water-surface replacement failed; Sodium fallback restored: %s: %s",
                    error.getClass().getSimpleName(), error.getMessage());
        }
    }

    private RhiPatchPipeline heightPipeline(List<GpuTextureView> colors, GpuTextureView depth) {
        PipelineKey key = new PipelineKey(colors.stream().map(v -> v.texture().getFormat()).toList(),
                depth.texture().getFormat(), samples(colors.get(0)));
        if (heightPipeline != null && key.equals(heightKey)) return heightPipeline;
        closeHeightPipeline();
        heightKey = key;
        heightPipeline = owner.advancedShaders().createPatchPipeline(new PatchPipelineDescriptor(
                "combatant-height-surface", PATCH_VERTEX, PATCH_TESS_CONTROL, HEIGHT_TESS_EVALUATION,
                null, HEIGHT_FRAGMENT, LAYOUT, 4, HEIGHT_LAYOUT,
                AdvancedBlendMode.OPAQUE, AdvancedDepthMode.READ_WRITE_GREATER_EQUAL, AdvancedCullMode.NONE,
                key.colorFormats(), key.depthFormat(), key.samples()
        ));
        return heightPipeline;
    }

    private RhiPatchPipeline waterPipeline(GpuTextureView scene, GpuTextureView depth) {
        PipelineKey key = new PipelineKey(List.of(scene.texture().getFormat()), depth.texture().getFormat(), samples(scene));
        if (waterPipeline != null && key.equals(waterKey)) return waterPipeline;
        closeWaterPipeline();
        waterKey = key;
        waterPipeline = owner.advancedShaders().createPatchPipeline(new PatchPipelineDescriptor(
                "combatant-water-surface", PATCH_VERTEX, PATCH_TESS_CONTROL, WATER_TESS_EVALUATION,
                null, WATER_FRAGMENT, LAYOUT, 4, WATER_LAYOUT,
                AdvancedBlendMode.ALPHA, AdvancedDepthMode.READ_ONLY_GREATER_EQUAL, AdvancedCullMode.NONE,
                key.colorFormats(), key.depthFormat(), key.samples()
        ));
        return waterPipeline;
    }

    private void uploadCamera(DeferredPassContext context,
                              DeferredPrimaryViewSource.FrameView camera,
                              GpuTextureView target) {
        Matrix4f viewRotation = camera.view();
        viewRotation.m30(0.0f).m31(0.0f).m32(0.0f);
        float time = 0.0f;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            time = (minecraft.level.getGameTime() + context.frame().tickProgress()) / 20.0f;
        }
        Std430Writer writer = new Std430Writer(CAMERA_LAYOUT, 1)
                .putMat4(0, "viewRotation", viewRotation)
                .putMat4(0, "projection", camera.projection())
                .putVec4(0, "cameraTime", (float) camera.cameraPosition().x, (float) camera.cameraPosition().y,
                        (float) camera.cameraPosition().z, time)
                .putVec4(0, "viewport", target.getWidth(0), target.getHeight(0),
                        1.0f / Math.max(1, target.getWidth(0)), 1.0f / Math.max(1, target.getHeight(0)));
        cameraBuffer().upload(writer.buffer(), 0L);
    }

    private GpuMeshHandle buildWaterMesh(List<WaterSurfaceExtractor.WaterPatch> patches, Vec3 camera) {
        waterMesh.reserve(patches.size() * 4, patches.size() * 4);
        waterMesh.beginLocal();
        for (WaterSurfaceExtractor.WaterPatch patch : patches) {
            int base = waterMesh.getVertexCount();
            for (int i = 0; i < 4; i++) writePatchVertex(waterMesh, patch.positions(), patch.uvs(), patch.color(),
                    patch.ao(), patch.light(), i, camera, patch.flowX(), patch.flowZ(), patch.displacementScale(),
                    patch.minTessFactor(), patch.maxTessFactor(), patch.distanceFadeStart(), patch.distanceFadeEnd(),
                    patch.materialId(), patch.mapMask(), patch.packedSurface());
            waterMesh.patch4(base, base + 1, base + 2, base + 3);
        }
        waterMesh.end();
        return owner.dynamicMeshes().upload(waterMesh);
    }

    private GpuMeshHandle buildHeightMesh(List<WaterSurfaceExtractor.HeightPatch> patches, Vec3 camera) {
        heightMesh.reserve(patches.size() * 4, patches.size() * 4);
        heightMesh.beginLocal();
        for (WaterSurfaceExtractor.HeightPatch patch : patches) {
            int base = heightMesh.getVertexCount();
            for (int i = 0; i < 4; i++) writePatchVertex(heightMesh, patch.positions(), patch.uvs(), patch.color(),
                    patch.ao(), patch.light(), i, camera, 0.0f, 0.0f, patch.displacementScale(),
                    patch.minTessFactor(), patch.maxTessFactor(), patch.distanceFadeStart(), patch.distanceFadeEnd(),
                    patch.materialId(), patch.mapMask(), patch.packedSurface());
            heightMesh.patch4(base, base + 1, base + 2, base + 3);
        }
        heightMesh.end();
        return owner.dynamicMeshes().upload(heightMesh);
    }

    private static void writePatchVertex(MeshBuilder mesh,
                                         float[] positions,
                                         float[] uvs,
                                         int[] colors,
                                         float[] ao,
                                         int[] light,
                                         int i,
                                         Vec3 camera,
                                         float flowX,
                                         float flowZ,
                                         float displacement,
                                         float minFactor,
                                         float maxFactor,
                                         float fadeStart,
                                         float fadeEnd,
                                         int materialId,
                                         int mapMask,
                                         int packedSurface) {
        int packedColor = colors[i];
        int r = packedColor & 0xFF;
        int g = (packedColor >>> 8) & 0xFF;
        int b = (packedColor >>> 16) & 0xFF;
        int a = (packedColor >>> 24) & 0xFF;
        int packedLight = light[i];
        float blockLight = Math.max(0.0f, Math.min(1.0f, (packedLight & 0xFFFF) / 240.0f));
        float skyLight = Math.max(0.0f, Math.min(1.0f, ((packedLight >>> 16) & 0xFFFF) / 240.0f));

        mesh.vec3(positions[i * 3] - camera.x, positions[i * 3 + 1] - camera.y, positions[i * 3 + 2] - camera.z)
                .vec2(uvs[i * 2], uvs[i * 2 + 1])
                .color(r, g, b, a)
                .vec4(flowX, flowZ, ao[i], blockLight)
                .vec4(displacement, minFactor, maxFactor, fadeStart)
                .vec4(fadeEnd, skyLight, 0.0f, 0.0f)
                .uint(materialId)
                .uint(mapMask)
                .uint(packedSurface)
                .next();
    }

    private List<SampledTextureBinding> heightTextures() {
        return materialTextures();
    }

    private List<SampledTextureBinding> waterMaterialTextures() {
        return materialTextures();
    }

    private List<SampledTextureBinding> materialTextures() {
        AbstractTexture block = blockAtlasTexture();
        if (block == null) throw new IllegalStateException("Minecraft block atlas is unavailable");
        MaterialAtlasManager atlases = MaterialAtlasManager.global();
        GpuSampler atlasSampler = block.getSampler();
        return List.of(
                new SampledTextureBinding(0, block.getTextureView(), atlasSampler),
                new SampledTextureBinding(1, requireView(atlases.albedoView(), "material albedo atlas"), atlasSampler),
                new SampledTextureBinding(2, requireView(atlases.normalHeightView(), "material normal-height atlas"), atlasSampler),
                new SampledTextureBinding(3, requireView(atlases.surfaceView(), "material surface atlas"), atlasSampler),
                new SampledTextureBinding(4, requireView(atlases.specularView(), "material specular atlas"), atlasSampler)
        );
    }

    private static AbstractTexture blockAtlasTexture() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.getTextureManager() == null
                ? null : minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
    }

    private static GpuTextureView requireView(GpuTextureView view, String label) {
        if (view == null) throw new IllegalStateException(label + " is unavailable");
        return view;
    }

    private static List<WaterSurfaceExtractor.WaterPatch> collectWaterPatches(long minGenerationExclusive) {
        return WaterSurfaceExtractor.snapshot().values().stream()
                .filter(section -> section.generation() > minGenerationExclusive)
                .sorted(Comparator.comparingLong(WaterSurfaceExtractor.SectionPatchMesh::sectionKey))
                .flatMap(section -> section.waterPatches().stream())
                .toList();
    }

    private static List<WaterSurfaceExtractor.HeightPatch> collectHeightPatches(long minGenerationExclusive) {
        return WaterSurfaceExtractor.snapshot().values().stream()
                .filter(section -> section.generation() > minGenerationExclusive)
                .sorted(Comparator.comparingLong(WaterSurfaceExtractor.SectionPatchMesh::sectionKey))
                .flatMap(section -> section.heightPatches().stream())
                .toList();
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        if (owner != null) resetRoutingState();
        closeOwned();
        owner = rhi;
    }

    private RhiStorageBuffer cameraBuffer() {
        if (owner == null) throw new IllegalStateException("Patch surface source has no RHI owner");
        if (cameraBuffer == null) {
            cameraBuffer = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-patch-camera", CAMERA_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        return cameraBuffer;
    }

    private void resetRoutingState() {
        heightActivationGeneration = Long.MIN_VALUE;
        waterActivationGeneration = Long.MIN_VALUE;
        heightNativeFailed = false;
        waterNativeFailed = false;
        SurfacePatchRouting.reset();
    }

    private void closeHeightPipeline() {
        if (heightPipeline != null) {
            try { heightPipeline.close(); } catch (Throwable ignored) { }
            heightPipeline = null;
        }
        heightKey = null;
    }

    private void closeWaterPipeline() {
        if (waterPipeline != null) {
            try { waterPipeline.close(); } catch (Throwable ignored) { }
            waterPipeline = null;
        }
        waterKey = null;
    }

    private void closeOwned() {
        closeHeightPipeline();
        closeWaterPipeline();
        if (cameraBuffer != null) {
            try { cameraBuffer.close(); } catch (Throwable ignored) { }
            cameraBuffer = null;
        }
    }

    @Override
    public void close() {
        closeOwned();
        heightMesh.close();
        waterMesh.close();
        resetRoutingState();
        owner = null;
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView view = context.resources().texture(resource);
        if (view == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return view;
    }

    private static int samples(GpuTextureView view) {
        return view.texture() instanceof IMsaaTexture msaa ? Math.max(1, msaa.combatant$getSamples()) : 1;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }

    private record PipelineKey(List<GpuFormat> colorFormats, GpuFormat depthFormat, int samples) {
        PipelineKey {
            colorFormats = List.copyOf(colorFormats);
        }
    }
}
