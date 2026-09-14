/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.rhi.resource.RenderResourceManager;
import combatant.client.render.engine.rhi.resource.TransientTargetDescriptor;
import combatant.client.render.engine.uniform.impl.DeferredLightingUniforms;
import combatant.client.util.logging.DebugLog;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Combatant-owned world geometry stage.
 *
 * <p>The producer bridge supplies already-batched geometry. This class owns the attachment
 * layout and lifetime; it has no Sodium or Iris dependency.</p>
 */
public final class DeferredWorldPipeline {
    public static final int SCENE_COLOR_SLOT = 0;
    public static final int SURFACE_SLOT = 1;
    public static final int GEOMETRY_SLOT = 2;
    public static final int AUXILIARY_SLOT = 3;
    public static final GpuFormat SURFACE_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final GpuFormat GEOMETRY_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final GpuFormat AUXILIARY_FORMAT = GpuFormat.RGBA8_UNORM;

    private static final String OWNER = "DeferredWorldPipeline";
    private static final Vector4fc EMPTY_GBUFFER = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("combatant.render.deferred", "true")
    );

    private long targetFrameId = Long.MIN_VALUE;
    private boolean clearedThisFrame;
    private boolean lightingResolvedThisFrame;
    private boolean preGeometryExecuted;
    private boolean postGeometryExecuted;
    private @Nullable RenderResourceManager targetOwner;
    private @Nullable GeometryTargets targets;
    private @Nullable GbufferViews sampleableTargets;
    private @Nullable Object worldOwner;
    private final DeferredResourceAllocator physicalResources = new DeferredResourceAllocator();
    private final DeferredResourceBindings resourceBindings = new DeferredResourceBindings(physicalResources);

    public boolean enabled() {
        return ENABLED;
    }

    /** Adds the fixed Combatant MRT contract before the producer pipeline is compiled. */
    public void configureGeometryPipeline(RenderPipeline.Builder builder) {
        if (!ENABLED) return;
        int availableAttachments = CombatantRenderSystem.rhi().capabilities().maxColorAttachments();
        if (availableAttachments <= AUXILIARY_SLOT) {
            throw new IllegalStateException("Deferred world pipeline requires 4 color attachments; backend exposes "
                    + availableAttachments);
        }
        builder.withColorTargetState(SURFACE_SLOT, new ColorTargetState(
                Optional.empty(), SURFACE_FORMAT, ColorTargetState.WRITE_ALL
        ));
        builder.withColorTargetState(GEOMETRY_SLOT, new ColorTargetState(
                Optional.empty(), GEOMETRY_FORMAT, ColorTargetState.WRITE_ALL
        ));
        builder.withColorTargetState(AUXILIARY_SLOT, new ColorTargetState(
                Optional.empty(), AUXILIARY_FORMAT, ColorTargetState.WRITE_ALL
        ));
        builder.withShaderDefine("COMBATANT_DEFERRED_GBUFFER");
    }

    /**
     * Opens one geometry phase pass around the producer's existing batched draws.
     * Slot zero remains the current scene color during migration; slots one and two are owned
     * by Combatant and later consumed by the deferred lighting stage.
     */
    public RenderPass openGeometryPass(CommandEncoder encoder,
                                       Supplier<String> label,
                                       GpuTextureView sceneColor,
                                       Optional<Vector4fc> clearSceneColor,
                                       @Nullable GpuTextureView depth,
                                       OptionalDouble clearDepth) {
        if (!ENABLED) {
            throw new IllegalStateException("Deferred world pipeline is disabled");
        }
        RenderSystem.assertOnRenderThread();

        int width = sceneColor.getWidth(0);
        int height = sceneColor.getHeight(0);
        int samples = samples(sceneColor);
        if (depth != null && samples(depth) != samples) {
            throw new IllegalStateException("Scene color/depth sample mismatch: color="
                    + samples + ", depth=" + samples(depth));
        }

        GeometryTargets geometry = acquireTargets(width, height, samples);
        resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
        resourceBindings.bindTexture(DeferredResource.MAIN_DEPTH, depth);
        resourceBindings.bindTexture(DeferredResource.GBUFFER_SURFACE, geometry.surface());
        resourceBindings.bindTexture(DeferredResource.GBUFFER_GEOMETRY, geometry.geometry());
        resourceBindings.bindTexture(DeferredResource.GBUFFER_AUXILIARY, geometry.auxiliary());
        if (!preGeometryExecuted) {
            executeStage(DeferredStage.FRAME_SETUP);
            executeStage(DeferredStage.PRE_GEOMETRY_COMPUTE);
            preGeometryExecuted = true;
        }
        Optional<Vector4fc> clearGbuffer = clearedThisFrame
                ? Optional.empty() : Optional.of(EMPTY_GBUFFER);

        RenderPassDescriptor descriptor = RenderPassDescriptor.create(label)
                .withColorAttachment(sceneColor, clearSceneColor)
                .withColorAttachment(geometry.surface(), clearGbuffer)
                .withColorAttachment(geometry.geometry(), clearGbuffer)
                .withColorAttachment(geometry.auxiliary(), clearGbuffer)
                .withRenderArea(new RenderPass.RenderArea(0, 0, width, height));
        if (depth != null) {
            descriptor.withDepthAttachment(depth, clearDepth);
        }

        RenderPass pass = encoder.createRenderPass(descriptor);
        clearedThisFrame = true;
        return pass;
    }

    /** Executes the neutral backend lighting stage once after opaque and cutout terrain. */
    public boolean resolveLighting(GpuTextureView sceneColor,
                                   GpuTextureView lightmap,
                                   GpuSampler gbufferSampler,
                                   GpuSampler lightmapSampler,
                                   LightingState state) {
        if (!ENABLED || lightingResolvedThisFrame || targets == null) return false;
        RenderSystem.assertOnRenderThread();

        GeometryTargets geometry = targets;
        if (sceneColor.getWidth(0) != geometry.width() || sceneColor.getHeight(0) != geometry.height()) {
            throw new IllegalStateException("Deferred lighting target dimensions differ from G-buffer: scene="
                    + sceneColor.getWidth(0) + "x" + sceneColor.getHeight(0)
                    + ", gbuffer=" + geometry.width() + "x" + geometry.height());
        }

        try {
            GbufferViews inputs = lightingInputs(geometry);
            sampleableTargets = inputs;
            resourceBindings.bindTexture(DeferredResource.GBUFFER_SURFACE, inputs.surface());
            resourceBindings.bindTexture(DeferredResource.GBUFFER_GEOMETRY, inputs.geometry());
            resourceBindings.bindTexture(DeferredResource.GBUFFER_AUXILIARY, inputs.auxiliary());
            if (!postGeometryExecuted) {
                executeStage(DeferredStage.POST_GEOMETRY_COMPUTE);
                executeStage(DeferredStage.DEPTH_RESOLVE);
                executeStage(DeferredStage.DEPTH_PYRAMID);
                executeStage(DeferredStage.AMBIENT_OCCLUSION);
                executeStage(DeferredStage.PRE_LIGHTING);
                postGeometryExecuted = true;
            }
            DeferredLightingUniforms.update(state);
            CombatantRenderSystem.rhi().drawFullscreen(
                    FullscreenDrawCommand.builder("Combatant Deferred Terrain Lighting")
                            .colorAttachment(sceneColor)
                            .pipeline(CombatantRenderPipelines.DEFERRED_TERRAIN_LIGHTING)
                            .uniform("DeferredLighting", DeferredLightingUniforms.get())
                            .sampler("u_GbufferSurface", inputs.surface(), gbufferSampler)
                            .sampler("u_GbufferGeometry", inputs.geometry(), gbufferSampler)
                            .sampler("u_GbufferAuxiliary", inputs.auxiliary(), gbufferSampler)
                            .sampler("u_LightTex", lightmap, lightmapSampler)
                            .build()
            );
            lightingResolvedThisFrame = true;
            resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
            executeStage(DeferredStage.POST_LIGHTING);
            executeStage(DeferredStage.REFLECTION_TRACE);
            executeStage(DeferredStage.REFLECTION_DENOISE);
            executeStage(DeferredStage.REFLECTION_COMPOSITE);
            return true;
        } catch (Throwable t) {
            DebugLog.warnOnChange(
                    "combatant.deferred.lighting.resolve.failed",
                    t.getClass().getSimpleName() + "|" + t.getMessage(),
                    "[Deferred] terrain lighting resolve failed; keeping forward scene color: %s: %s",
                    t.getClass().getSimpleName(),
                    t.getMessage()
            );
            return false;
        }
    }

    public @Nullable GeometryTargets currentTargets() {
        return targets;
    }

    /** Single-sample views safe for lighting, compute sampling and postprocess consumers. */
    public @Nullable GbufferViews currentSampleableTargets() {
        if (targets != null && targets.samples() <= 1) {
            return new GbufferViews(targets.surface(), targets.geometry(), targets.auxiliary());
        }
        return sampleableTargets;
    }

    private GeometryTargets acquireTargets(int width, int height, int samples) {
        RenderResourceManager resources = CombatantRenderSystem.resources();
        Object currentWorld = Minecraft.getInstance().level;
        if (worldOwner != currentWorld) {
            physicalResources.reset();
            worldOwner = currentWorld;
        }
        long frameId = CombatantRenderSystem.ensureFrameContext().frameId();
        if (targets != null && targetOwner == resources && targetFrameId == frameId
                && targets.width() == width && targets.height() == height && targets.samples() == samples) {
            return targets;
        }

        RenderTarget surface = acquire(resources, "world-gbuffer-surface", width, height, samples, SURFACE_FORMAT);
        RenderTarget geometry = acquire(resources, "world-gbuffer-geometry", width, height, samples, GEOMETRY_FORMAT);
        RenderTarget auxiliary = acquire(resources, "world-gbuffer-auxiliary", width, height, samples, AUXILIARY_FORMAT);
        targets = new GeometryTargets(
                surface, geometry, auxiliary, width, height, samples
        );
        targetOwner = resources;
        targetFrameId = frameId;
        resourceBindings.beginFrame(frameId);
        clearedThisFrame = false;
        lightingResolvedThisFrame = false;
        preGeometryExecuted = false;
        postGeometryExecuted = false;
        sampleableTargets = null;
        return targets;
    }

    public void beforeTranslucency() {
        if (!ENABLED || targets == null) return;
        executeStage(DeferredStage.FORWARD_OPAQUE);
        executeStage(DeferredStage.PRE_TRANSLUCENCY);
    }

    public void beforePostProcess(GpuTextureView sceneColor, @Nullable GpuTextureView resolvedDepth) {
        if (!ENABLED || targets == null) return;
        resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
        resourceBindings.bindTexture(DeferredResource.MAIN_DEPTH, resolvedDepth);
        executeStage(DeferredStage.POST_TRANSLUCENCY);
        executeStage(DeferredStage.TEMPORAL_RESOLVE);
        executeStage(DeferredStage.PRE_POST_PROCESS);
    }

    public void afterPostProcess(GpuTextureView sceneColor, @Nullable GpuTextureView resolvedDepth) {
        if (!ENABLED || targets == null) return;
        resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
        resourceBindings.bindTexture(DeferredResource.MAIN_DEPTH, resolvedDepth);
        executeStage(DeferredStage.POST_PROCESS);
    }

    public void finalComposite(GpuTextureView sceneColor, @Nullable GpuTextureView resolvedDepth) {
        if (!ENABLED || targets == null) return;
        resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
        resourceBindings.bindTexture(DeferredResource.MAIN_DEPTH, resolvedDepth);
        executeStage(DeferredStage.FINAL_COMPOSITE);
    }

    public DeferredResourceBindings resourceBindings() {
        return resourceBindings;
    }

    /** Releases optional persistent/history resources before a device switch or shutdown. */
    public void releasePhysicalResources() {
        RenderSystem.assertOnRenderThread();
        physicalResources.close();
        worldOwner = null;
        targets = null;
        sampleableTargets = null;
    }

    private void executeStage(DeferredStage stage) {
        CombatantRenderSystem.deferredGraph().execute(
                stage, CombatantRenderSystem.ensureFrameContext(), resourceBindings
        );
    }

    private static GbufferViews lightingInputs(GeometryTargets targets) {
        if (targets.samples() <= 1) {
            return new GbufferViews(targets.surface(), targets.geometry(), targets.auxiliary());
        }

        RenderResourceManager resources = CombatantRenderSystem.resources();
        RenderTarget surface = acquire(resources, "world-gbuffer-surface-resolved",
                targets.width(), targets.height(), 1, SURFACE_FORMAT);
        RenderTarget geometry = acquire(resources, "world-gbuffer-geometry-resolved",
                targets.width(), targets.height(), 1, GEOMETRY_FORMAT);
        RenderTarget auxiliary = acquire(resources, "world-gbuffer-auxiliary-resolved",
                targets.width(), targets.height(), 1, AUXILIARY_FORMAT);

        boolean resolved = CombatantRenderSystem.rhi().msaa().resolveTransient(
                targets.surfaceTarget(), surface, true, false
        );
        resolved &= CombatantRenderSystem.rhi().msaa().resolveTransient(
                targets.geometryTarget(), geometry, true, false
        );
        resolved &= CombatantRenderSystem.rhi().msaa().resolveTransient(
                targets.auxiliaryTarget(), auxiliary, true, false
        );
        if (!resolved) {
            throw new IllegalStateException("Backend could not resolve the multisampled world G-buffer");
        }
        return new GbufferViews(
                surface.getColorTextureView(), geometry.getColorTextureView(), auxiliary.getColorTextureView()
        );
    }

    private static RenderTarget acquire(RenderResourceManager resources,
                                        String name,
                                        int width,
                                        int height,
                                        int samples,
                                        GpuFormat format) {
        TransientTargetDescriptor descriptor = new TransientTargetDescriptor(
                name, width, height, false, format, samples,
                TransientTargetDescriptor.Lifetime.FRAME, OWNER
        );
        return samples > 1
                ? resources.frameTransientMsaa(descriptor)
                : resources.frameTransient(descriptor);
    }

    private static int samples(GpuTextureView view) {
        return view.texture() instanceof IMsaaTexture msaa
                ? Math.max(1, msaa.combatant$getSamples()) : 1;
    }

    public record GeometryTargets(
            RenderTarget surfaceTarget,
            RenderTarget geometryTarget,
            RenderTarget auxiliaryTarget,
            int width,
            int height,
            int samples
    ) {
        public GpuTextureView surface() {
            return surfaceTarget.getColorTextureView();
        }

        public GpuTextureView geometry() {
            return geometryTarget.getColorTextureView();
        }

        public GpuTextureView auxiliary() {
            return auxiliaryTarget.getColorTextureView();
        }
    }

    public record GbufferViews(
            GpuTextureView surface,
            GpuTextureView geometry,
            GpuTextureView auxiliary
    ) {
    }

    public record LightingState(
            float fogRed,
            float fogGreen,
            float fogBlue,
            float fogAlpha,
            float environmentalFogStart,
            float environmentalFogEnd,
            float renderFogStart,
            float renderFogEnd
    ) {
    }
}
