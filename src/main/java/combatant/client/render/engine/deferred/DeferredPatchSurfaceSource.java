/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import combatant.client.render.engine.pipeline.DepthTestFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.material.MaterialAtlasManager;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.pipeline.ExtendedRenderPipelineBuilder;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.rhi.pipeline.PipelineDomain;
import combatant.client.render.engine.rhi.pipeline.TransformPolicy;
import combatant.client.render.engine.rhi.pipeline.VertexLayoutSpec;
import combatant.client.render.engine.rhi.shader.*;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.WaterFrameUniforms;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import combatant.client.render.engine.water.CameraMediumState;
import combatant.client.render.engine.water.WaterDeformationState;
import combatant.client.render.engine.water.WaterForwardProfile;
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
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native patch consumer for explicit height-displacement terrain and extracted water surfaces. */
final class DeferredPatchSurfaceSource implements AutoCloseable {
    private static final Identifier PATCH_VERTEX = id("deferred/patch_surface");
    private static final Identifier PATCH_TESS_CONTROL = id("deferred/patch_surface");
    private static final Identifier WATER_PATCH_VERTEX = id("deferred/water_patch");
    private static final Identifier WATER_PATCH_TESS_CONTROL = id("deferred/water_patch");
    private static final Identifier HEIGHT_TESS_EVALUATION = id("deferred/height_surface");
    private static final Identifier WATER_TESS_EVALUATION = id("deferred/water_surface");
    private static final Identifier HEIGHT_FRAGMENT = id("deferred/height_surface");
    private static final Identifier WATER_FRAGMENT = id("deferred/water_surface");
    private static final Identifier WATER_BOUNDARY_FRAGMENT = id("deferred/water_medium_boundary");
    private static final Identifier WATER_FALLBACK_VERTEX = id("shaders/deferred/water_surface_fallback.vert");
    private static final Identifier WATER_FALLBACK_FRAGMENT = id("shaders/deferred/water_surface_fallback.frag");
    private static final Identifier WATER_BOUNDARY_FALLBACK_FRAGMENT = id("shaders/deferred/water_medium_boundary_fallback.frag");

    private static final VertexLayoutSpec LAYOUT = VertexLayoutSpec.of(
            "combatant:water_patch", CombatantVertexFormats.WATER_PATCH, PrimitiveTopology.QUADS);
    private static final VertexLayoutSpec WATER_LAYOUT_SPEC = VertexLayoutSpec.of(
            "combatant:water_forward_patch", CombatantVertexFormats.WATER_FORWARD_PATCH, PrimitiveTopology.QUADS);

    private static final Std430StructLayout CAMERA_LAYOUT = Std430StructLayout.builder()
            .member("viewRotation", Std430Type.MAT4)
            .member("projection", Std430Type.MAT4)
            .member("cameraTime", Std430Type.VEC4)
            .member("viewport", Std430Type.VEC4)
            .build();

    /** mat4/vec4 only so this std430 layout is byte-identical to WaterFrameUniforms std140. */
    private static final Std430StructLayout WATER_FRAME_LAYOUT = Std430StructLayout.builder()
            .member("currentView", Std430Type.MAT4)
            .member("currentProjection", Std430Type.MAT4)
            .member("currentInverseProjection", Std430Type.MAT4)
            .member("currentInverseView", Std430Type.MAT4)
            .member("previousView", Std430Type.MAT4)
            .member("previousProjection", Std430Type.MAT4)
            .member("currentCameraTime", Std430Type.VEC4)
            .member("previousCameraTime", Std430Type.VEC4)
            .member("viewport", Std430Type.VEC4)
            .member("depthTransform", Std430Type.VEC4)
            .member("deformation0", Std430Type.VEC4)
            .member("deformation1", Std430Type.VEC4)
            .member("mediumReflection", Std430Type.VEC4)
            .member("mediumBoundary", Std430Type.VEC4)
            .member("opticalAbsorption", Std430Type.VEC4)
            .member("opticalScattering", Std430Type.VEC4)
            .member("reflectionMeta", Std430Type.VEC4)
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
            new ShaderResourceSlot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(10, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout WATER_BOUNDARY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiPatchPipeline heightPipeline;
    private RhiPatchPipeline waterPipeline;
    private RhiPatchPipeline waterBoundaryPipeline;
    private PipelineKey heightKey;
    private PipelineKey waterKey;
    private BoundaryPipelineKey waterBoundaryKey;
    private RhiStorageBuffer cameraBuffer;
    private RhiStorageBuffer waterFrameBuffer;
    private RenderPipeline waterFallbackPipeline;
    private FallbackPipelineKey waterFallbackKey;
    private RenderPipeline waterBoundaryFallbackPipeline;
    private GpuFormat waterBoundaryFallbackFormat;
    private long heightActivationGeneration = Long.MIN_VALUE;
    private long waterActivationGeneration = Long.MIN_VALUE;
    private boolean heightNativeFailed;
    private boolean waterNativeFailed;
    private boolean waterBoundaryNativeFailed;
    private final MeshBuilder heightMesh = new MeshBuilder(CombatantVertexFormats.WATER_PATCH, PrimitiveTopology.QUADS);
    private final MeshBuilder waterMesh = new MeshBuilder(CombatantVertexFormats.WATER_FORWARD_PATCH, PrimitiveTopology.QUADS);
    private final MeshBuilder waterFallbackMesh = new MeshBuilder(CombatantVertexFormats.WATER_FORWARD_PATCH, PrimitiveTopology.TRIANGLES);
    private final WaterDeformationState waterDeformation = new WaterDeformationState();

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

        passes.add(DeferredPassSpec.builder("world.water.medium-boundary", DeferredStage.WATER_MEDIUM_BOUNDARY)
                .read(DeferredResource.RESOLVED_DEPTH)
                .write(DeferredResource.WATER_MEDIUM_BOUNDARY)
                .requires(RhiShaderStage.VERTEX, RhiShaderStage.FRAGMENT)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.RESOLVED_DEPTH))
                .execute(this::drawWaterMediumBoundary)
                .build());

        passes.add(DeferredPassSpec.builder("world.water-surface", DeferredStage.WATER_SURFACE)
                .read(DeferredResource.MAIN_DEPTH,
                        DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.REFLECTION_COLOR,
                        DeferredResource.REFLECTION_CONFIDENCE,
                        DeferredResource.SKY_SPECULAR_RADIANCE,
                        DeferredResource.SCENE_RADIANCE)
                .write(DeferredResource.SCENE_COLOR,
                        DeferredResource.RASTER_MOTION_VELOCITY,
                        DeferredResource.RASTER_MOTION_VALIDITY,
                        DeferredResource.RASTER_TEMPORAL_COVERAGE,
                        DeferredResource.RASTER_REACTIVE_MASK)
                .requires(RhiShaderStage.VERTEX, RhiShaderStage.FRAGMENT)
                .when(this::waterAvailable)
                .execute(this::drawWater)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        heightNativeFailed = false;
        waterNativeFailed = false;
        waterBoundaryNativeFailed = false;
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
        return context.primaryView().current() != null
                && context.resources().texture(DeferredResource.SCENE_COLOR) != null
                && context.resources().texture(DeferredResource.MAIN_DEPTH) != null
                && context.resources().texture(DeferredResource.RESOLVED_DEPTH) != null
                && context.resources().texture(DeferredResource.SCENE_RADIANCE) != null
                && MaterialAtlasManager.global().ready()
                && blockAtlasTexture() != null
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

    private void drawWaterMediumBoundary(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView camera = context.primaryView().current();
        if (camera == null) return;

        GpuTextureView boundary = requireTexture(context, DeferredResource.WATER_MEDIUM_BOUNDARY);
        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
                boundary.texture(), new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));

        List<WaterSurfaceExtractor.WaterPatch> patches = collectAllWaterPatches();
        if (patches.isEmpty()) return;

        WaterFrameUniforms.Frame waterFrame = buildWaterFrame(context, camera, boundary, false);
        uploadWaterFrame(waterFrame);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        if (context.rhi().capabilities().nativeTessellationSubmission() && !waterBoundaryNativeFailed) {
            try {
                RhiPatchPipeline pipeline = waterBoundaryPipeline(boundary);
                GpuMeshHandle mesh = buildWaterMesh(patches, camera.cameraPosition());
                context.advancedShaders().drawPatches(new PatchDrawCommand(
                        "Combatant water medium boundary", pipeline, boundary, null, mesh,
                        List.of(new StorageBinding(11, waterFrameBuffer(), 0L,
                                WATER_FRAME_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                        List.of(new SampledTextureBinding(0, resolvedDepth, nearest)), List.of()
                ));
                return;
            } catch (Throwable error) {
                waterBoundaryNativeFailed = true;
                closeWaterBoundaryPipeline();
                DebugLog.warnOnChange(
                        "deferred.water-medium-boundary.tessellation.failed",
                        error.getClass().getSimpleName() + "|" + error.getMessage(),
                        "[Deferred] tessellated water-medium boundary failed; using graphics fallback: %s: %s",
                        error.getClass().getSimpleName(), error.getMessage());
            }
        }

        GpuMeshHandle mesh = buildWaterFallbackMesh(patches, camera.cameraPosition());
        GpuBufferSlice frameUniform = WaterFrameUniforms.write(waterFrame);
        context.rhi().drawMesh(RhiDrawCommand.builder("Combatant water medium boundary (triangle fallback)")
                .pipeline(waterBoundaryFallbackPipeline(boundary))
                .colorAttachment(boundary)
                .mesh(mesh)
                .uniform("WaterFrame", frameUniform)
                .sampler("u_OpaqueDepth", resolvedDepth, nearest)
                .build());
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
        GpuTextureView velocity = context.resources().texture(DeferredResource.RASTER_MOTION_VELOCITY);
        GpuTextureView motionValidity = context.resources().texture(DeferredResource.RASTER_MOTION_VALIDITY);
        GpuTextureView temporalCoverage = context.resources().texture(DeferredResource.RASTER_TEMPORAL_COVERAGE);
        GpuTextureView reactiveMask = context.resources().texture(DeferredResource.RASTER_REACTIVE_MASK);
        boolean motionMrt = velocity != null
                && motionValidity != null
                && temporalCoverage != null
                && reactiveMask != null
                && velocity.getWidth(0) == scene.getWidth(0)
                && velocity.getHeight(0) == scene.getHeight(0)
                && motionValidity.getWidth(0) == scene.getWidth(0)
                && motionValidity.getHeight(0) == scene.getHeight(0)
                && temporalCoverage.getWidth(0) == scene.getWidth(0)
                && temporalCoverage.getHeight(0) == scene.getHeight(0)
                && reactiveMask.getWidth(0) == scene.getWidth(0)
                && reactiveMask.getHeight(0) == scene.getHeight(0)
                && samples(velocity) == samples(scene)
                && samples(motionValidity) == samples(scene)
                && samples(temporalCoverage) == samples(scene)
                && samples(reactiveMask) == samples(scene);
        List<GpuTextureView> colors = motionMrt
                ? List.of(scene, velocity, motionValidity, temporalCoverage, reactiveMask) : List.of(scene);
        WaterFrameUniforms.Frame waterFrame = buildWaterFrame(context, camera, scene, motionMrt);
        uploadWaterFrame(waterFrame);

        Throwable nativeFailure = null;
        if (context.rhi().capabilities().nativeTessellationSubmission() && !waterNativeFailed) {
            try {
                RhiPatchPipeline pipeline = waterPipeline(colors, depth);
                GpuMeshHandle mesh = buildWaterMesh(patches, camera.cameraPosition());
                context.advancedShaders().drawPatches(new PatchDrawCommand(
                        "Combatant water surfaces (tessellated)", pipeline, colors, depth, mesh,
                        List.of(new StorageBinding(11, waterFrameBuffer(), 0L,
                                WATER_FRAME_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                        waterTextures(context), List.of()
                ));
                WaterSurfacePatchRouting.setReplacementActive(true);
                return;
            } catch (Throwable error) {
                nativeFailure = error;
                waterNativeFailed = true;
                closeWaterPipeline();
                DebugLog.warnOnChange(
                        "deferred.water-surface.tessellation.failed",
                        error.getClass().getSimpleName() + "|" + error.getMessage(),
                        "[Deferred] tessellated water path failed; using declared graphics fallback: %s: %s",
                        error.getClass().getSimpleName(), error.getMessage());
            }
        }

        try {
            GpuMeshHandle mesh = buildWaterFallbackMesh(patches, camera.cameraPosition());
            RenderPipeline pipeline = waterFallbackPipeline(scene, motionMrt ? velocity : null,
                    motionMrt ? motionValidity : null, motionMrt ? temporalCoverage : null,
                    motionMrt ? reactiveMask : null, depth);
            GpuBufferSlice frameUniform = WaterFrameUniforms.write(waterFrame);
            RhiDrawCommand.Builder draw = RhiDrawCommand.builder("Combatant water surfaces (triangle fallback)")
                    .pipeline(pipeline)
                    .colorAttachment(0, scene)
                    .depthAttachment(depth)
                    .mesh(mesh)
                    .uniform("WaterFrame", frameUniform);
            if (motionMrt) {
                draw.colorAttachment(1, velocity);
                draw.colorAttachment(2, motionValidity);
                draw.colorAttachment(3, temporalCoverage);
                draw.colorAttachment(4, reactiveMask);
            }
            bindWaterFallbackTextures(draw, context);
            context.rhi().drawMesh(draw.build());
            WaterSurfacePatchRouting.setReplacementActive(true);
        } catch (Throwable error) {
            waterActivationGeneration = Long.MIN_VALUE;
            WaterSurfacePatchRouting.setReplacementActive(false);
            String nativeSuffix = nativeFailure == null ? "" : " (tessellation had already failed)";
            DebugLog.warnOnChange(
                    "deferred.water-surface.fallback.failed",
                    error.getClass().getSimpleName() + "|" + error.getMessage(),
                    "[Deferred] water graphics fallback failed%s; Sodium fallback restored: %s: %s",
                    nativeSuffix, error.getClass().getSimpleName(), error.getMessage());
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

    private RhiPatchPipeline waterPipeline(List<GpuTextureView> colors, GpuTextureView depth) {
        PipelineKey key = new PipelineKey(colors.stream().map(v -> v.texture().getFormat()).toList(),
                depth.texture().getFormat(), samples(colors.getFirst()));
        if (waterPipeline != null && key.equals(waterKey)) return waterPipeline;
        closeWaterPipeline();
        waterKey = key;
        waterPipeline = owner.advancedShaders().createPatchPipeline(new PatchPipelineDescriptor(
                "combatant-water-surface", WATER_PATCH_VERTEX, WATER_PATCH_TESS_CONTROL, WATER_TESS_EVALUATION,
                null, WATER_FRAGMENT, WATER_LAYOUT_SPEC, 4, WATER_LAYOUT,
                AdvancedBlendMode.ALPHA, AdvancedDepthMode.READ_ONLY_GREATER_EQUAL, AdvancedCullMode.NONE,
                key.colorFormats(), key.depthFormat(), key.samples()
        ));
        return waterPipeline;
    }

    private RhiPatchPipeline waterBoundaryPipeline(GpuTextureView boundary) {
        BoundaryPipelineKey key = new BoundaryPipelineKey(boundary.texture().getFormat());
        if (waterBoundaryPipeline != null && key.equals(waterBoundaryKey)) return waterBoundaryPipeline;
        closeWaterBoundaryPipeline();
        waterBoundaryKey = key;
        waterBoundaryPipeline = owner.advancedShaders().createPatchPipeline(new PatchPipelineDescriptor(
                "combatant-water-medium-boundary", WATER_PATCH_VERTEX, WATER_PATCH_TESS_CONTROL, WATER_TESS_EVALUATION,
                null, WATER_BOUNDARY_FRAGMENT, WATER_LAYOUT_SPEC, 4, WATER_BOUNDARY_LAYOUT,
                AdvancedBlendMode.OPAQUE, AdvancedDepthMode.DISABLED, AdvancedCullMode.NONE,
                key.colorFormat(), null, 1
        ));
        return waterBoundaryPipeline;
    }

    private RenderPipeline waterBoundaryFallbackPipeline(GpuTextureView boundary) {
        GpuFormat format = boundary.texture().getFormat();
        if (waterBoundaryFallbackPipeline != null && format == waterBoundaryFallbackFormat) {
            return waterBoundaryFallbackPipeline;
        }
        waterBoundaryFallbackFormat = format;
        ExtendedRenderPipelineBuilder builder = new ExtendedRenderPipelineBuilder(CombatantRenderPipelines.meshUniforms())
                .withLocation(id("pipeline/world_water_medium_boundary_" + format.name().toLowerCase(Locale.ROOT)))
                .withVertexFormat(CombatantVertexFormats.WATER_FORWARD_PATCH, PrimitiveTopology.TRIANGLES)
                .withVertexShader(WATER_FALLBACK_VERTEX)
                .withFragmentShader(WATER_BOUNDARY_FALLBACK_FRAGMENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withColorTarget(0, format)
                .withSampler("u_OpaqueDepth")
                .withUniform("WaterFrame", UniformType.UNIFORM_BUFFER)
                .withDomain(PipelineDomain.WORLD)
                .withTransformPolicy(TransformPolicy.NONE);
        waterBoundaryFallbackPipeline = CombatantRenderPipelines.registerAddonPipeline(builder.build());
        return waterBoundaryFallbackPipeline;
    }

    private RenderPipeline waterFallbackPipeline(GpuTextureView scene,
                                                 GpuTextureView velocity,
                                                 GpuTextureView motionValidity,
                                                 GpuTextureView temporalCoverage,
                                                 GpuTextureView reactiveMask,
                                                 GpuTextureView depth) {
        FallbackPipelineKey key = new FallbackPipelineKey(
                scene.texture().getFormat(),
                velocity != null ? velocity.texture().getFormat() : null,
                motionValidity != null ? motionValidity.texture().getFormat() : null,
                temporalCoverage != null ? temporalCoverage.texture().getFormat() : null,
                reactiveMask != null ? reactiveMask.texture().getFormat() : null,
                depth.texture().getFormat());
        if (waterFallbackPipeline != null && key.equals(waterFallbackKey)) return waterFallbackPipeline;
        waterFallbackKey = key;

        ExtendedRenderPipelineBuilder builder = new ExtendedRenderPipelineBuilder(CombatantRenderPipelines.meshUniforms())
                .withLocation(id("pipeline/world_water_surface_fallback_" + Integer.toHexString(key.hashCode())))
                .withVertexFormat(CombatantVertexFormats.WATER_FORWARD_PATCH, PrimitiveTopology.TRIANGLES)
                .withVertexShader(WATER_FALLBACK_VERTEX)
                .withFragmentShader(WATER_FALLBACK_FRAGMENT)
                .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withColorTarget(0, key.sceneFormat(), BlendFunction.TRANSLUCENT, ColorTargetState.WRITE_ALL)
                .withSampler("u_BlockAtlas")
                .withSampler("u_AlbedoAtlas")
                .withSampler("u_NormalHeightAtlas")
                .withSampler("u_SurfaceAtlas")
                .withSampler("u_SpecularAtlas")
                .withSampler("u_ReflectionColor")
                .withSampler("u_ReflectionConfidence")
                .withSampler("u_SceneRadiance")
                .withSampler("u_ResolvedDepth")
                .withSampler("u_SkySpecular")
                .withSampler("u_ReflectionProbe")
                .withUniform("WaterFrame", UniformType.UNIFORM_BUFFER)
                .withDomain(PipelineDomain.WORLD)
                .withTransformPolicy(TransformPolicy.NONE);
        if (key.velocityFormat() != null) {
            // Motion outputs use alpha=1, so the standard alpha state becomes an exact overwrite.
            builder.withColorTarget(1, key.velocityFormat(), BlendFunction.TRANSLUCENT, ColorTargetState.WRITE_ALL);
        }
        if (key.motionValidityFormat() != null) {
            builder.withColorTarget(2, key.motionValidityFormat(), BlendFunction.TRANSLUCENT, ColorTargetState.WRITE_ALL);
        }
        if (key.temporalCoverageFormat() != null) {
            builder.withColorTarget(3, key.temporalCoverageFormat(), BlendFunction.TRANSLUCENT, ColorTargetState.WRITE_ALL);
        }
        if (key.reactiveMaskFormat() != null) {
            builder.withColorTarget(4, key.reactiveMaskFormat(), BlendFunction.TRANSLUCENT, ColorTargetState.WRITE_ALL);
        }
        waterFallbackPipeline = CombatantRenderPipelines.registerAddonPipeline(builder.build());
        return waterFallbackPipeline;
    }

    private WaterFrameUniforms.Frame buildWaterFrame(DeferredPassContext context,
                                                      DeferredPrimaryViewSource.FrameView current,
                                                      GpuTextureView target,
                                                      boolean velocityMrt) {
        DeferredHistoryDescriptor history = context.history();
        DeferredPrimaryViewSource.FrameView previous = history.valid() ? context.primaryView().previous() : null;
        if (previous == null) previous = current;

        Matrix4f currentView = current.view();
        currentView.m30(0.0f).m31(0.0f).m32(0.0f);
        Matrix4f previousView = previous.view();
        previousView.m30(0.0f).m31(0.0f).m32(0.0f);
        Matrix4f currentProjection = current.projection();
        Matrix4f previousProjection = previous.projection();
        Matrix4f currentInverseProjection = new Matrix4f(currentProjection).invert();
        Matrix4f currentInverseView = new Matrix4f(currentView).invert();

        float time = 0.0f;
        float rain = 0.0f;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null) {
            time = (minecraft.level.getGameTime() + context.frame().tickProgress()) / 20.0f;
            rain = Math.max(0.0f, Math.min(1.0f, minecraft.level.getRainLevel(context.frame().tickProgress())));
        }
        WaterDeformationState.FrameTimes deformationTimes =
                waterDeformation.update(current.frameId(), time, history.valid());
        WaterForwardProfile profile = WaterForwardProfile.FOUNDATION;
        CameraMediumState medium = CameraMediumState.capture();

        boolean hasSsr = context.settings().reflectionsEnabled()
                && context.isValid(DeferredResource.REFLECTION_COLOR)
                && context.isValid(DeferredResource.REFLECTION_CONFIDENCE)
                && context.resources().texture(DeferredResource.REFLECTION_COLOR) != null
                && context.resources().texture(DeferredResource.REFLECTION_CONFIDENCE) != null;
        boolean hasSky = context.isValid(DeferredResource.SKY_SPECULAR_RADIANCE)
                && context.resources().texture(DeferredResource.SKY_SPECULAR_RADIANCE) != null;
        int skyMipCount = 1;
        RhiStorageImage skyImage = context.resources().storageImage(DeferredResource.SKY_SPECULAR_RADIANCE);
        if (hasSky && skyImage != null) skyMipCount = Math.max(1, skyImage.descriptor().mipLevels());
        boolean zeroToOne = isVulkan(context);

        return new WaterFrameUniforms.Frame(
                currentView, currentProjection, currentInverseProjection, currentInverseView,
                previousView, previousProjection,
                vec4(current.cameraPosition(), deformationTimes.currentTime()),
                vec4(previous.cameraPosition(), deformationTimes.previousTime()),
                new float[]{target.getWidth(0), target.getHeight(0),
                        1.0f / Math.max(1, target.getWidth(0)), 1.0f / Math.max(1, target.getHeight(0))},
                new float[]{zeroToOne ? 1.0f : 2.0f, zeroToOne ? 0.0f : -1.0f,
                        zeroToOne ? 1.0f : 0.5f, zeroToOne ? 0.0f : 0.5f},
                new float[]{profile.displacementAmplitudeFactor(), profile.displacementSpatialFrequency(),
                        profile.displacementTemporalFrequency(), profile.flowCoupling()},
                new float[]{profile.windX(), profile.windZ(), profile.windCoupling(), profile.rainRippleContribution()},
                new float[]{medium.medium().gpuCode(), medium.insideWater() ? 1.0f : 0.0f,
                        hasSsr ? 1.0f : 0.0f, hasSky ? 1.0f : 0.0f},
                new float[]{medium.boundarySurfaceY(), medium.boundarySource().gpuCode(),
                        medium.fluidTypeId(), medium.boundarySurfaceValid() ? 1.0f : 0.0f},
                new float[]{profile.absorptionR(), profile.absorptionG(), profile.absorptionB(),
                        profile.refractionProbeDistance()},
                new float[]{profile.scatteringR(), profile.scatteringG(), profile.scatteringB(), rain},
                new float[]{skyMipCount, 0.0f, velocityMrt ? 1.0f : 0.0f,
                        deformationTimes.historyValid() ? 1.0f : 0.0f}
        );
    }

    private void uploadWaterFrame(WaterFrameUniforms.Frame frame) {
        float[][] vectors = {
                frame.currentCameraTime(), frame.previousCameraTime(), frame.viewport(), frame.depthTransform(),
                frame.deformation0(), frame.deformation1(), frame.mediumReflection(), frame.mediumBoundary(),
                frame.opticalAbsorption(), frame.opticalScattering(), frame.reflectionMeta()
        };
        Std430Writer writer = new Std430Writer(WATER_FRAME_LAYOUT, 1)
                .putMat4(0, "currentView", frame.currentView())
                .putMat4(0, "currentProjection", frame.currentProjection())
                .putMat4(0, "currentInverseProjection", frame.currentInverseProjection())
                .putMat4(0, "currentInverseView", frame.currentInverseView())
                .putMat4(0, "previousView", frame.previousView())
                .putMat4(0, "previousProjection", frame.previousProjection());
        String[] names = {"currentCameraTime", "previousCameraTime", "viewport", "depthTransform", "deformation0",
                "deformation1", "mediumReflection", "mediumBoundary", "opticalAbsorption", "opticalScattering",
                "reflectionMeta"};
        for (int i = 0; i < names.length; i++) {
            float[] value = vectors[i];
            writer.putVec4(0, names[i], value[0], value[1], value[2], value[3]);
        }
        waterFrameBuffer().upload(writer.buffer(), 0L);
    }

    private static float[] vec4(Vec3 position, float w) {
        Vec3 value = position == null ? Vec3.ZERO : position;
        return new float[]{(float) value.x, (float) value.y, (float) value.z, w};
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
            for (int i = 0; i < 4; i++) writeWaterVertex(waterMesh, patch, i, camera);
            waterMesh.patch4(base, base + 1, base + 2, base + 3);
        }
        waterMesh.end();
        return owner.dynamicMeshes().upload(waterMesh);
    }

    private GpuMeshHandle buildWaterFallbackMesh(List<WaterSurfaceExtractor.WaterPatch> patches, Vec3 camera) {
        waterFallbackMesh.reserve(patches.size() * 4, patches.size() * 6);
        waterFallbackMesh.beginLocal();
        for (WaterSurfaceExtractor.WaterPatch patch : patches) {
            int base = waterFallbackMesh.getVertexCount();
            for (int i = 0; i < 4; i++) writeWaterVertex(waterFallbackMesh, patch, i, camera);
            waterFallbackMesh.quad(base, base + 1, base + 2, base + 3);
        }
        waterFallbackMesh.end();
        return owner.dynamicMeshes().upload(waterFallbackMesh);
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

    private static void writeWaterVertex(MeshBuilder mesh,
                                         WaterSurfaceExtractor.WaterPatch patch,
                                         int i,
                                         Vec3 camera) {
        int packedColor = patch.color()[i];
        int r = packedColor & 0xFF;
        int g = (packedColor >>> 8) & 0xFF;
        int b = (packedColor >>> 16) & 0xFF;
        int a = (packedColor >>> 24) & 0xFF;
        int packedLight = patch.light()[i];
        float blockLight = Math.max(0.0f, Math.min(1.0f, (packedLight & 0xFFFF) / 240.0f));
        float skyLight = Math.max(0.0f, Math.min(1.0f, ((packedLight >>> 16) & 0xFFFF) / 240.0f));

        mesh.vec3(patch.positions()[i * 3] - camera.x,
                        patch.positions()[i * 3 + 1] - camera.y,
                        patch.positions()[i * 3 + 2] - camera.z)
                .vec2(patch.uvs()[i * 2], patch.uvs()[i * 2 + 1])
                .vec2(patch.localSurfaceCoordinates()[i * 2], patch.localSurfaceCoordinates()[i * 2 + 1])
                .color(r, g, b, a)
                .vec4(patch.flowX(), patch.flowZ(), patch.ao()[i], blockLight)
                .vec4(patch.displacementScale(), patch.minTessFactor(), patch.maxTessFactor(), patch.distanceFadeStart())
                .vec4(patch.distanceFadeEnd(), skyLight, patch.flowStrength(), patch.surfaceNormalX())
                .vec4(patch.transmission(), patch.fallbackThickness(), patch.surfaceNormalY(), patch.surfaceNormalZ())
                .uint(patch.materialId())
                .uint(patch.fluidTypeId())
                .uint(patch.mapMask())
                .uint(patch.featureMask())
                .uint(patch.surfaceFlags())
                .uint(patch.packedSurface())
                .next();
    }

    private List<SampledTextureBinding> heightTextures() {
        return materialTextures();
    }

    private List<SampledTextureBinding> waterMaterialTextures() {
        return materialTextures();
    }

    private List<SampledTextureBinding> waterTextures(DeferredPassContext context) {
        List<SampledTextureBinding> textures = new ArrayList<>(waterMaterialTextures());
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler skySampler = RenderSystem.getSamplerCache().getSampler(
                com.mojang.blaze3d.textures.AddressMode.REPEAT,
                com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, true);
        GpuTextureView radiance = requireTexture(context, DeferredResource.SCENE_RADIANCE);
        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView reflection = context.resources().texture(DeferredResource.REFLECTION_COLOR);
        GpuTextureView confidence = context.resources().texture(DeferredResource.REFLECTION_CONFIDENCE);
        GpuTextureView sky = context.resources().texture(DeferredResource.SKY_SPECULAR_RADIANCE);
        textures.add(new SampledTextureBinding(5, reflection != null ? reflection : radiance, linear));
        textures.add(new SampledTextureBinding(6, confidence != null ? confidence : resolvedDepth, nearest));
        textures.add(new SampledTextureBinding(7, radiance, linear));
        textures.add(new SampledTextureBinding(8, resolvedDepth, nearest));
        textures.add(new SampledTextureBinding(9, sky != null ? sky : radiance, sky != null ? skySampler : linear));
        // Probe tier is explicit but currently unavailable; bind a safe texture while the validity flag is false.
        textures.add(new SampledTextureBinding(10, sky != null ? sky : radiance, sky != null ? skySampler : linear));
        return textures;
    }

    private void bindWaterFallbackTextures(RhiDrawCommand.Builder draw, DeferredPassContext context) {
        AbstractTexture block = blockAtlasTexture();
        if (block == null) throw new IllegalStateException("Minecraft block atlas is unavailable");
        MaterialAtlasManager atlases = MaterialAtlasManager.global();
        GpuSampler atlasSampler = block.getSampler();
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler skySampler = RenderSystem.getSamplerCache().getSampler(
                com.mojang.blaze3d.textures.AddressMode.REPEAT,
                com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, true);
        GpuTextureView radiance = requireTexture(context, DeferredResource.SCENE_RADIANCE);
        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView reflection = context.resources().texture(DeferredResource.REFLECTION_COLOR);
        GpuTextureView confidence = context.resources().texture(DeferredResource.REFLECTION_CONFIDENCE);
        GpuTextureView sky = context.resources().texture(DeferredResource.SKY_SPECULAR_RADIANCE);
        draw.sampler("u_BlockAtlas", block.getTextureView(), atlasSampler)
                .sampler("u_AlbedoAtlas", requireView(atlases.albedoView(), "material albedo atlas"), atlasSampler)
                .sampler("u_NormalHeightAtlas", requireView(atlases.normalHeightView(), "material normal-height atlas"), atlasSampler)
                .sampler("u_SurfaceAtlas", requireView(atlases.surfaceView(), "material surface atlas"), atlasSampler)
                .sampler("u_SpecularAtlas", requireView(atlases.specularView(), "material specular atlas"), atlasSampler)
                .sampler("u_ReflectionColor", reflection != null ? reflection : radiance, linear)
                .sampler("u_ReflectionConfidence", confidence != null ? confidence : resolvedDepth, nearest)
                .sampler("u_SceneRadiance", radiance, linear)
                .sampler("u_ResolvedDepth", resolvedDepth, nearest)
                .sampler("u_SkySpecular", sky != null ? sky : radiance, sky != null ? skySampler : linear)
                .sampler("u_ReflectionProbe", sky != null ? sky : radiance, sky != null ? skySampler : linear);
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

    private static List<WaterSurfaceExtractor.WaterPatch> collectAllWaterPatches() {
        return WaterSurfaceExtractor.snapshot().values().stream()
                .sorted(Comparator.comparingLong(WaterSurfaceExtractor.SectionPatchMesh::sectionKey))
                .flatMap(section -> section.waterPatches().stream())
                .toList();
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

    private RhiStorageBuffer waterFrameBuffer() {
        if (owner == null) throw new IllegalStateException("Patch surface source has no RHI owner");
        if (waterFrameBuffer == null) {
            waterFrameBuffer = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-water-frame", WATER_FRAME_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        return waterFrameBuffer;
    }

    private void resetRoutingState() {
        heightActivationGeneration = Long.MIN_VALUE;
        waterActivationGeneration = Long.MIN_VALUE;
        heightNativeFailed = false;
        waterNativeFailed = false;
        waterBoundaryNativeFailed = false;
        waterDeformation.reset();
        SurfacePatchRouting.reset();
    }

    private void closeHeightPipeline() {
        if (heightPipeline != null) {
            try { heightPipeline.close(); } catch (Throwable ignored) { }
            heightPipeline = null;
        }
        heightKey = null;
    }

    private void closeWaterBoundaryPipeline() {
        if (waterBoundaryPipeline != null) {
            try { waterBoundaryPipeline.close(); } catch (Throwable ignored) { }
            waterBoundaryPipeline = null;
        }
        waterBoundaryKey = null;
        waterBoundaryFallbackPipeline = null;
        waterBoundaryFallbackFormat = null;
    }

    private void closeWaterPipeline() {
        if (waterPipeline != null) {
            try { waterPipeline.close(); } catch (Throwable ignored) { }
            waterPipeline = null;
        }
        waterKey = null;
        waterFallbackPipeline = null;
        waterFallbackKey = null;
    }

    private void closeOwned() {
        closeHeightPipeline();
        closeWaterBoundaryPipeline();
        closeWaterPipeline();
        if (cameraBuffer != null) {
            try { cameraBuffer.close(); } catch (Throwable ignored) { }
            cameraBuffer = null;
        }
        if (waterFrameBuffer != null) {
            try { waterFrameBuffer.close(); } catch (Throwable ignored) { }
            waterFrameBuffer = null;
        }
    }

    @Override
    public void close() {
        closeOwned();
        heightMesh.close();
        waterMesh.close();
        waterFallbackMesh.close();
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

    private static boolean isVulkan(DeferredPassContext context) {
        return context.rhi().getClass().getName().toLowerCase(Locale.ROOT).contains("vulkan");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }

    private record PipelineKey(List<GpuFormat> colorFormats, GpuFormat depthFormat, int samples) {
        PipelineKey {
            colorFormats = List.copyOf(colorFormats);
        }
    }

    private record BoundaryPipelineKey(GpuFormat colorFormat) {
    }

    private record FallbackPipelineKey(GpuFormat sceneFormat, GpuFormat velocityFormat,
                                       GpuFormat motionValidityFormat, GpuFormat temporalCoverageFormat,
                                       GpuFormat reactiveMaskFormat, GpuFormat depthFormat) {
    }
}
