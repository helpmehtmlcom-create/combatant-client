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
import combatant.client.render.engine.material.MaterialAtlasManager;
import combatant.client.render.sodium.fluid.SurfacePatchRouting;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.rhi.resource.RenderResourceManager;
import combatant.client.render.engine.rhi.resource.TransientTargetDescriptor;
import combatant.client.render.engine.uniform.impl.DeferredLightingUniforms;
import combatant.client.render.engine.world.WorldRenderState;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.asset.AssetAutoLoader;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;

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
    public static final int MATERIAL_SLOT = 4;
    public static final int MATERIAL_ID_SLOT = 5;
    public static final GpuFormat SURFACE_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final GpuFormat GEOMETRY_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final GpuFormat AUXILIARY_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final GpuFormat MATERIAL_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final GpuFormat MATERIAL_ID_FORMAT = GpuFormat.R32_UINT;

    private static final String OWNER = "DeferredWorldPipeline";
    private static final Vector4fc EMPTY_GBUFFER = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
    private volatile boolean requestedEnabled = Boolean.parseBoolean(
            System.getProperty("combatant.render.deferred", "false")
    );
    private volatile LifecycleState lifecycleState = LifecycleState.DISABLED;
    private long geometryPipelineGeneration;

    private long frameStateId = Long.MIN_VALUE;
    private long targetFrameId = Long.MIN_VALUE;
    private DeferredRuntimeConfig.Snapshot frameSettings = DeferredRuntimeConfig.current();
    private long frameSettingsGeneration = DeferredRuntimeConfig.generation();
    private long temporalPolicyGeneration = Long.MIN_VALUE;
    private int temporalWidth = -1;
    private int temporalHeight = -1;
    private int temporalSamples = -1;
    private final AtomicReference<DeferredHistoryResetReason> pendingExternalHistoryReset =
            new AtomicReference<>(DeferredHistoryResetReason.NONE);
    private boolean clearedThisFrame;
    private boolean lightingResolvedThisFrame;
    private boolean frameSetupExecuted;
    private boolean shadowExecuted;
    private boolean preGeometryExecuted;
    private boolean postGeometryExecuted;
    private @Nullable RenderResourceManager targetOwner;
    private @Nullable GeometryTargets targets;
    private @Nullable GbufferViews sampleableTargets;
    private @Nullable Object worldOwner;
    private final DeferredResourceAllocator physicalResources = new DeferredResourceAllocator();
    private final DeferredResourceBindings resourceBindings = new DeferredResourceBindings(physicalResources);
    private final DeferredSecondaryViewRegistry secondaryViews = new DeferredSecondaryViewRegistry();
    private final DeferredPrimaryViewSource primaryView = new DeferredPrimaryViewSource();
    private final DeferredWorldRenderStateSource worldStateSource = new DeferredWorldRenderStateSource();
    private WorldRenderState worldRenderState = WorldRenderState.unknown(0L);

    public boolean enabled() {
        return lifecycleState == LifecycleState.ACTIVE;
    }

    public boolean requestedEnabled() {
        return requestedEnabled;
    }

    /** Runtime toggle. The requested state is committed at the next safe world-frame boundary. */
    public void requestEnabled(boolean enabled) {
        requestedEnabled = enabled;
    }

    /** Thread-safe typed lifecycle event from exact Minecraft producers such as player teleport. */
    public void requestHistoryReset(DeferredHistoryResetReason reason) {
        if (reason == null || reason == DeferredHistoryResetReason.NONE) return;
        if (!requestedEnabled && !enabled()) return;
        pendingExternalHistoryReset.getAndUpdate(current ->
                current == DeferredHistoryResetReason.NONE ? reason : current);
    }

    public LifecycleState lifecycleState() {
        return lifecycleState;
    }

    /** Changes whenever Sodium's immutable terrain RenderPipeline attachment contract must rebuild. */
    public long geometryPipelineGeneration() {
        return geometryPipelineGeneration;
    }

    /**
     * Called before world submission for a new Combatant frame. Deferred activation is
     * world-gated so boot/menu resource reloads never compile or upload its assets.
     */
    public void serviceRuntimeLifecycle() {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft != null && minecraft.level != null;
        if (lifecycleState == LifecycleState.FAILED) {
            recoverFailedRuntime(minecraft);
            if (lifecycleState == LifecycleState.FAILED) return;
        }
        if (requestedEnabled && inWorld && lifecycleState == LifecycleState.DISABLED) {
            activateRuntime(minecraft);
        } else if ((!requestedEnabled || !inWorld) && lifecycleState == LifecycleState.ACTIVE) {
            deactivateRuntime(minecraft);
        }
    }

    /**
     * A failed activation/backend switch/deactivation may have partially-created native resources.
     * Never disguise that state as DISABLED. Retry authoritative cleanup at the next clean frame
     * boundary and only publish DISABLED after every Combatant-owned resource family is released.
     */
    private void recoverFailedRuntime(Minecraft minecraft) {
        try {
            releasePhysicalResources();
            CombatantRenderSystem.deferredGraph().releaseBackendResources(CombatantRenderSystem.rhi());
            if (AssetAutoLoader.isScopeActive(DeferredRuntimeAssets.SCOPE)) {
                if (minecraft == null) {
                    throw new IllegalStateException("Minecraft instance is unavailable during deferred cleanup");
                }
                AssetAutoLoader.deactivate(DeferredRuntimeAssets.SCOPE, minecraft.getResourceManager());
            }
            lifecycleState = LifecycleState.DISABLED;
            DebugLog.renderThreadOnChange(
                    "combatant.deferred.lifecycle.recovered",
                    "disabled|" + geometryPipelineGeneration,
                    "[Deferred] recovered failed runtime state; deferred assets are fully disabled"
            );
        } catch (Throwable cleanupError) {
            lifecycleState = LifecycleState.FAILED;
            DebugLog.warnOnChange(
                    "combatant.deferred.lifecycle.recovery.failed",
                    cleanupError.getClass().getSimpleName() + "|" + cleanupError.getMessage(),
                    "[Deferred] failed-state cleanup will retry at the next frame boundary: %s: %s",
                    cleanupError.getClass().getSimpleName(), cleanupError.getMessage()
            );
        }
    }

    private void activateRuntime(Minecraft minecraft) {
        lifecycleState = LifecycleState.ACTIVATING;
        try {
            AssetAutoLoader.activate(DeferredRuntimeAssets.SCOPE, minecraft.getResourceManager());
            primaryView.reset();
            worldStateSource.reset();
            worldRenderState = worldStateSource.current();
            physicalResources.reset();
            geometryPipelineGeneration++;
            lifecycleState = LifecycleState.ACTIVE;
            DebugLog.renderThreadOnChange(
                    "combatant.deferred.lifecycle",
                    "active|" + geometryPipelineGeneration,
                    "[Deferred] activated at world-frame boundary; pipeline generation=%d",
                    geometryPipelineGeneration
            );
        } catch (Throwable error) {
            requestedEnabled = false;
            lifecycleState = LifecycleState.FAILED;
            try {
                if (AssetAutoLoader.isScopeActive(DeferredRuntimeAssets.SCOPE)) {
                    AssetAutoLoader.deactivate(DeferredRuntimeAssets.SCOPE, minecraft.getResourceManager());
                }
            } catch (Throwable teardownError) {
                error.addSuppressed(teardownError);
            }
            DebugLog.error("[Deferred] runtime activation failed", error);
        }
    }

    /** Rebuilds active deferred-native programs after Combatant switches GPU backends. */
    public void onBackendChanged() {
        if (!enabled()) return;
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        try {
            DeferredRuntimeAssets.reprepareAfterBackendSwitch(minecraft.getResourceManager());
            physicalResources.reset();
            primaryView.queueHistoryReset(DeferredHistoryResetReason.BACKEND_CHANGE);
        } catch (Throwable error) {
            requestedEnabled = false;
            lifecycleState = LifecycleState.FAILED;
            releasePhysicalResources();
            try {
                if (AssetAutoLoader.isScopeActive(DeferredRuntimeAssets.SCOPE)) {
                    AssetAutoLoader.deactivate(DeferredRuntimeAssets.SCOPE, minecraft.getResourceManager());
                }
            } catch (Throwable teardownError) {
                error.addSuppressed(teardownError);
            }
            geometryPipelineGeneration++;
            DebugLog.error("[Deferred] failed to reprepare assets after backend switch", error);
        }
    }

    /** Full renderer shutdown path; unlike a runtime toggle this also clears the requested state. */
    public void shutdownRuntime() {
        RenderSystem.assertOnRenderThread();
        requestedEnabled = false;
        geometryPipelineGeneration++;
        releasePhysicalResources();
        primaryView.reset();
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (AssetAutoLoader.isScopeActive(DeferredRuntimeAssets.SCOPE)) {
                if (minecraft == null) {
                    throw new IllegalStateException("Minecraft instance is unavailable during deferred shutdown");
                }
                AssetAutoLoader.deactivate(DeferredRuntimeAssets.SCOPE, minecraft.getResourceManager());
            }
            lifecycleState = LifecycleState.DISABLED;
        } catch (Throwable error) {
            lifecycleState = LifecycleState.FAILED;
            DebugLog.error("[Deferred] runtime shutdown cleanup failed", error);
        }
    }

    private void deactivateRuntime(Minecraft minecraft) {
        lifecycleState = LifecycleState.DEACTIVATING;
        // Publish the forward layout before any next terrain pipeline lookup.
        geometryPipelineGeneration++;
        releasePhysicalResources();
        primaryView.reset();
        worldStateSource.reset();
        worldRenderState = worldStateSource.current();
        try {
            if (AssetAutoLoader.isScopeActive(DeferredRuntimeAssets.SCOPE)) {
                AssetAutoLoader.deactivate(DeferredRuntimeAssets.SCOPE, minecraft.getResourceManager());
            }
            lifecycleState = LifecycleState.DISABLED;
            DebugLog.renderThreadOnChange(
                    "combatant.deferred.lifecycle",
                    "disabled|" + geometryPipelineGeneration,
                    "[Deferred] disabled at world-frame boundary; pipeline generation=%d",
                    geometryPipelineGeneration
            );
        } catch (Throwable error) {
            requestedEnabled = false;
            lifecycleState = LifecycleState.FAILED;
            DebugLog.error("[Deferred] runtime deactivation failed", error);
        }
    }

    /** Adds the fixed Combatant MRT contract before the producer pipeline is compiled. */
    public void configureGeometryPipeline(RenderPipeline.Builder builder) {
        if (!enabled()) return;
        int availableAttachments = CombatantRenderSystem.rhi().capabilities().maxColorAttachments();
        if (availableAttachments <= MATERIAL_ID_SLOT) {
            throw new IllegalStateException("Deferred world pipeline requires 6 color attachments; backend exposes "
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
        builder.withColorTargetState(MATERIAL_SLOT, new ColorTargetState(
                Optional.empty(), MATERIAL_FORMAT, ColorTargetState.WRITE_ALL
        ));
        builder.withColorTargetState(MATERIAL_ID_SLOT, new ColorTargetState(
                Optional.empty(), MATERIAL_ID_FORMAT, ColorTargetState.WRITE_ALL
        ));
        builder.withBindGroupLayout(MaterialAtlasManager.BIND_GROUP);
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
        if (!enabled()) {
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

        beginDeferredFrameState();
        GeometryTargets geometry = acquireTargets(width, height, samples);
        resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
        resourceBindings.bindTexture(DeferredResource.MAIN_DEPTH, depth);
        resourceBindings.bindTexture(DeferredResource.GBUFFER_SURFACE, geometry.surface());
        resourceBindings.bindTexture(DeferredResource.GBUFFER_GEOMETRY, geometry.geometry());
        resourceBindings.bindTexture(DeferredResource.GBUFFER_AUXILIARY, geometry.auxiliary());
        resourceBindings.bindTexture(DeferredResource.GBUFFER_MATERIAL, geometry.material());
        resourceBindings.bindTexture(DeferredResource.GBUFFER_MATERIAL_ID, geometry.materialId());
        if (!frameSetupExecuted) {
            executeStage(DeferredStage.FRAME_SETUP);
            frameSetupExecuted = true;
        }
        if (!shadowExecuted) {
            executeStage(DeferredStage.SHADOW_PREPARE);
            executeStage(DeferredStage.SHADOW_MAP);
            executeStage(DeferredStage.SHADOW_FILTER);
            shadowExecuted = true;
        }
        if (!preGeometryExecuted) {
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
                .withColorAttachment(geometry.material(), clearGbuffer)
                // Integer material IDs have no meaningful float clear. Every geometry fragment writes an exact ID;
                // consumers must mask empty pixels using GBUFFER_SURFACE/depth.
                .withColorAttachment(geometry.materialId(), Optional.empty())
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
                                   GpuSampler gbufferSampler,
                                   LightingState state) {
        if (!enabled() || lightingResolvedThisFrame || targets == null) return false;
        RenderSystem.assertOnRenderThread();

        GeometryTargets geometry = targets;
        if (sceneColor.getWidth(0) != geometry.width() || sceneColor.getHeight(0) != geometry.height()) {
            throw new IllegalStateException("Deferred lighting target dimensions differ from G-buffer: scene="
                    + sceneColor.getWidth(0) + "x" + sceneColor.getHeight(0)
                    + ", gbuffer=" + geometry.width() + "x" + geometry.height());
        }

        try {
            if (!postGeometryExecuted) {
                // Native height-displacement patches are geometry producers. They must write the
                // original (possibly multisampled) MRT before any G-buffer resolve takes place.
                resourceBindings.bindTexture(DeferredResource.GBUFFER_SURFACE, geometry.surface());
                resourceBindings.bindTexture(DeferredResource.GBUFFER_GEOMETRY, geometry.geometry());
                resourceBindings.bindTexture(DeferredResource.GBUFFER_AUXILIARY, geometry.auxiliary());
                resourceBindings.bindTexture(DeferredResource.GBUFFER_MATERIAL, geometry.material());
                resourceBindings.bindTexture(DeferredResource.GBUFFER_MATERIAL_ID, geometry.materialId());
                executeStage(DeferredStage.HEIGHT_SURFACE);
            }

            GbufferViews inputs = lightingInputs(geometry);
            sampleableTargets = inputs;
            resourceBindings.bindTexture(DeferredResource.GBUFFER_SURFACE, inputs.surface());
            resourceBindings.bindTexture(DeferredResource.GBUFFER_GEOMETRY, inputs.geometry());
            resourceBindings.bindTexture(DeferredResource.GBUFFER_AUXILIARY, inputs.auxiliary());
            resourceBindings.bindTexture(DeferredResource.GBUFFER_MATERIAL, inputs.material());
            resourceBindings.bindTexture(DeferredResource.GBUFFER_MATERIAL_ID, inputs.materialId());
            if (!postGeometryExecuted) {
                executeStage(DeferredStage.POST_GEOMETRY_COMPUTE);
                executeStage(DeferredStage.DEPTH_RESOLVE);
                executeStage(DeferredStage.VELOCITY_RESOLVE);
                executeStage(DeferredStage.DEPTH_PYRAMID);
                executeStage(DeferredStage.SHADOW_CASCADE_RESOLVE);
                executeStage(DeferredStage.CONTACT_SHADOW);
                executeStage(DeferredStage.SHADOW_RESOLVE);
                executeStage(DeferredStage.AMBIENT_OCCLUSION);
                executeStage(DeferredStage.PRE_LIGHTING);
                postGeometryExecuted = true;
            }
            GpuTextureView resolvedDepth = resourceBindings.texture(DeferredResource.RESOLVED_DEPTH);
            GpuTextureView gbufferDepth = resourceBindings.texture(DeferredResource.GBUFFER_DEPTH);
            if (resolvedDepth == null || gbufferDepth == null) {
                throw new IllegalStateException("Deferred lighting requires resolved and G-buffer depth");
            }
            boolean shadowValid = resourceBindings.isValid(DeferredResource.SHADOW_COLOR);
            boolean ambientOcclusionValid = resourceBindings.isValid(DeferredResource.AMBIENT_OCCLUSION);
            boolean cloudShadowValid = resourceBindings.isValid(DeferredResource.CLOUD_SHADOW_VISIBILITY);
            if (!resourceBindings.isValid(DeferredResource.ENVIRONMENT_IRRADIANCE)) {
                throw new IllegalStateException("Deferred lighting requires environment irradiance contract");
            }
            GpuTextureView environmentIrradiance = resourceBindings.texture(DeferredResource.ENVIRONMENT_IRRADIANCE);
            GpuTextureView shadowVisibility = shadowValid
                    ? resourceBindings.texture(DeferredResource.SHADOW_COLOR) : inputs.surface();
            GpuTextureView ambientVisibility = ambientOcclusionValid
                    ? resourceBindings.texture(DeferredResource.AMBIENT_OCCLUSION) : inputs.surface();
            GpuTextureView cloudShadowVisibility = cloudShadowValid
                    ? resourceBindings.texture(DeferredResource.CLOUD_SHADOW_VISIBILITY) : inputs.surface();
            boolean zeroToOneDepth = CombatantRenderSystem.rhi().capabilities().backendName() != null
                    && CombatantRenderSystem.rhi().capabilities().backendName().toLowerCase(Locale.ROOT).contains("vulkan");
            DeferredLightingUniforms.update(
                    state, worldRenderState, primaryView.current(), zeroToOneDepth, shadowValid, ambientOcclusionValid,
                    cloudShadowValid
            );

            // Keep direct terrain lighting in a Combatant-owned HDR target. Publishing it into the
            // mutable Minecraft scene target is only a compatibility step for forward opaque draws.
            resourceBindings.ensureTexture(
                    DeferredResource.DIRECT_LIGHTING_COLOR, CombatantRenderSystem.rhi(), frameSettings
            );
            GpuTextureView directLighting = resourceBindings.texture(DeferredResource.DIRECT_LIGHTING_COLOR);
            if (directLighting == null) {
                throw new IllegalStateException("Deferred direct-lighting target is unavailable");
            }
            CombatantRenderSystem.rhi().drawFullscreen(
                    FullscreenDrawCommand.builder("Combatant Deferred Terrain Lighting")
                            .colorAttachment(directLighting)
                            .pipeline(DeferredRuntimeAssets.terrainLighting())
                            .uniform("DeferredLighting", DeferredLightingUniforms.get())
                            .sampler("u_GbufferSurface", inputs.surface(), gbufferSampler)
                            .sampler("u_GbufferGeometry", inputs.geometry(), gbufferSampler)
                            .sampler("u_GbufferMaterial", inputs.material(), gbufferSampler)
                            .sampler("u_GbufferDepth", gbufferDepth, gbufferSampler)
                            .sampler("u_EnvironmentIrradiance", environmentIrradiance, gbufferSampler)
                            .sampler("u_ResolvedDepth", resolvedDepth, gbufferSampler)
                            .sampler("u_ShadowVisibility", shadowVisibility, gbufferSampler)
                            .sampler("u_CloudShadowVisibility", cloudShadowVisibility, gbufferSampler)
                            .sampler("u_AmbientVisibility", ambientVisibility, gbufferSampler)
                            .build()
            );
            resourceBindings.markWritten(DeferredResource.DIRECT_LIGHTING_COLOR);

            // Forward compatibility rendering still targets Minecraft's scene image. Only terrain
            // pixels are published here; sky and other non-G-buffer producers remain untouched.
            CombatantRenderSystem.rhi().drawFullscreen(
                    FullscreenDrawCommand.builder("Combatant Deferred Terrain Publish")
                            .colorAttachment(sceneColor)
                            .pipeline(DeferredRuntimeAssets.terrainPublish())
                            .uniform("DeferredLighting", DeferredLightingUniforms.get())
                            .sampler("u_Source", directLighting, gbufferSampler)
                            .sampler("u_GbufferAuxiliary", inputs.auxiliary(), gbufferSampler)
                            .sampler("u_GbufferDepth", gbufferDepth, gbufferSampler)
                            .build()
            );
            lightingResolvedThisFrame = true;
            resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
            executeStage(DeferredStage.POST_LIGHTING);
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
            return new GbufferViews(targets.surface(), targets.geometry(), targets.auxiliary(), targets.material(), targets.materialId());
        }
        return sampleableTargets;
    }

    /**
     * Opens the logical deferred frame before Sodium enters its primary chunk renderer. Shadow
     * producers need this boundary because recursively opening them from DefaultChunkRenderer
     * would corrupt Sodium's active terrain program and would be too late to replace visibility.
     */
    public void beforeTerrainSubmission() {
        RenderSystem.assertOnRenderThread();
        if (SurfacePatchRouting.consumeReloadRequested()) {
            CombatantRenderSystem.sodium().reloadWorldRenderer();
        }
        if (!enabled()) return;
        beginDeferredFrameState();
        if (!frameSetupExecuted) {
            executeStage(DeferredStage.FRAME_SETUP);
            frameSetupExecuted = true;
        }
        if (!shadowExecuted) {
            executeStage(DeferredStage.SHADOW_PREPARE);
            executeStage(DeferredStage.SHADOW_MAP);
            executeStage(DeferredStage.SHADOW_FILTER);
            shadowExecuted = true;
        }
    }

    private void beginDeferredFrameState() {
        Object currentWorld = Minecraft.getInstance().level;
        if (worldOwner != currentWorld) {
            physicalResources.reset();
            primaryView.beginWorld(currentWorld);
            worldStateSource.reset();
            worldRenderState = worldStateSource.current();
            worldOwner = currentWorld;
            frameStateId = Long.MIN_VALUE;
            targetFrameId = Long.MIN_VALUE;
            targets = null;
            sampleableTargets = null;
            targetOwner = null;
            temporalWidth = -1;
            temporalHeight = -1;
            temporalSamples = -1;
        }

        long frameId = CombatantRenderSystem.ensureFrameContext().frameId();
        if (frameStateId == frameId) return;
        frameStateId = frameId;
        DeferredHistoryResetReason externalReset = pendingExternalHistoryReset.getAndSet(DeferredHistoryResetReason.NONE);
        if (externalReset != DeferredHistoryResetReason.NONE) {
            primaryView.invalidateHistory(frameId, externalReset);
        }
        frameSettings = DeferredRuntimeConfig.current();
        frameSettingsGeneration = DeferredRuntimeConfig.generation();
        worldRenderState = worldStateSource.capture(Minecraft.getInstance().level, primaryView.current());
        if (temporalPolicyGeneration != Long.MIN_VALUE && temporalPolicyGeneration != frameSettingsGeneration) {
            primaryView.invalidateHistory(frameId, DeferredHistoryResetReason.POLICY_CHANGE);
        }
        temporalPolicyGeneration = frameSettingsGeneration;
        resourceBindings.beginFrame(frameId, primaryView.historyDescriptor().epoch());
        secondaryViews.beginFrame(frameId);
        clearedThisFrame = false;
        lightingResolvedThisFrame = false;
        frameSetupExecuted = false;
        shadowExecuted = false;
        preGeometryExecuted = false;
        postGeometryExecuted = false;
        sampleableTargets = null;
        targets = null;
        targetFrameId = Long.MIN_VALUE;
    }

    private GeometryTargets acquireTargets(int width, int height, int samples) {
        beginDeferredFrameState();
        RenderResourceManager resources = CombatantRenderSystem.resources();
        long frameId = frameStateId;
        if (targets != null && targetOwner == resources && targetFrameId == frameId
                && targets.width() == width && targets.height() == height && targets.samples() == samples) {
            return targets;
        }
        if (temporalWidth >= 0 && (temporalWidth != width || temporalHeight != height || temporalSamples != samples)) {
            primaryView.invalidateHistory(frameId, DeferredHistoryResetReason.RESIZE);
        }
        temporalWidth = width;
        temporalHeight = height;
        temporalSamples = samples;

        RenderTarget surface = acquire(resources, "world-gbuffer-surface", width, height, samples, SURFACE_FORMAT);
        RenderTarget geometry = acquire(resources, "world-gbuffer-geometry", width, height, samples, GEOMETRY_FORMAT);
        RenderTarget auxiliary = acquire(resources, "world-gbuffer-auxiliary", width, height, samples, AUXILIARY_FORMAT);
        RenderTarget material = acquire(resources, "world-gbuffer-material", width, height, samples, MATERIAL_FORMAT);
        RenderTarget materialId = acquire(resources, "world-gbuffer-material-id", width, height, samples, MATERIAL_ID_FORMAT);
        targets = new GeometryTargets(
                surface, geometry, auxiliary, material, materialId, width, height, samples
        );
        targetOwner = resources;
        targetFrameId = frameId;
        return targets;
    }

    public void beforeTranslucency(GpuTextureView sceneColor, @Nullable GpuTextureView depth) {
        if (!enabled() || targets == null) return;
        resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
        resourceBindings.bindTexture(DeferredResource.MAIN_DEPTH, depth);
        executeStage(DeferredStage.FORWARD_OPAQUE);

        // Forward opaque/entity rendering may have changed the primary depth after the terrain
        // deferred resolve. Refresh the screen-space tracing inputs at the real opaque/translucent
        // boundary so reflection/indirect producers never consume a terrain-only depth snapshot.
        executeStage(DeferredStage.PRE_TRANSLUCENCY_DEPTH_RESOLVE);
        executeStage(DeferredStage.PRE_TRANSLUCENCY_VELOCITY_RESOLVE);
        executeStage(DeferredStage.PRE_TRANSLUCENCY_DEPTH_PYRAMID);
        executeStage(DeferredStage.PRE_TRANSLUCENCY_TEMPORAL_VALIDATION);
        executeStage(DeferredStage.RADIANCE_CAPTURE);
        executeStage(DeferredStage.INDIRECT_PREPARE);
        executeStage(DeferredStage.INDIRECT_TRACE);
        executeStage(DeferredStage.INDIRECT_TEMPORAL);
        executeStage(DeferredStage.INDIRECT_HISTORY);
        executeStage(DeferredStage.INDIRECT_COMPOSITE);
        executeStage(DeferredStage.REFLECTION_CAPTURE_PREPARE);
        executeStage(DeferredStage.REFLECTION_CAPTURE);
        executeStage(DeferredStage.REFLECTION_PREPARE);
        executeStage(DeferredStage.REFLECTION_TRACE);
        executeStage(DeferredStage.REFLECTION_RESOLVE);
        executeStage(DeferredStage.REFLECTION_TEMPORAL);
        executeStage(DeferredStage.REFLECTION_DENOISE);
        executeStage(DeferredStage.REFLECTION_HISTORY);
        executeStage(DeferredStage.REFLECTION_COMPOSITE);
        executeStage(DeferredStage.SKY_COMPOSITE);
        executeStage(DeferredStage.VOLUMETRIC_MEDIA_INJECT);
        executeStage(DeferredStage.VOLUMETRIC_MEDIA_INTEGRATE);
        executeStage(DeferredStage.VOLUMETRIC_MEDIA_COMPOSITE);
        executeStage(DeferredStage.WATER_SURFACE);
        executeStage(DeferredStage.PRE_TRANSLUCENCY);
    }

    public void beforePostProcess(GpuTextureView sceneColor, @Nullable GpuTextureView resolvedDepth) {
        if (!enabled() || targets == null) return;
        resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
        resourceBindings.bindTexture(DeferredResource.MAIN_DEPTH, resolvedDepth);
        executeStage(DeferredStage.POST_TRANSLUCENCY);
        executeStage(DeferredStage.TEMPORAL_RESOLVE);
        executeStage(DeferredStage.PRE_POST_PROCESS);
    }

    public void afterPostProcess(GpuTextureView sceneColor, @Nullable GpuTextureView resolvedDepth) {
        if (!enabled() || targets == null) return;
        resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
        resourceBindings.bindTexture(DeferredResource.MAIN_DEPTH, resolvedDepth);
        executeStage(DeferredStage.POST_PROCESS);
    }

    public void finalComposite(GpuTextureView sceneColor, @Nullable GpuTextureView resolvedDepth) {
        if (!enabled() || targets == null) return;
        resourceBindings.bindTexture(DeferredResource.SCENE_COLOR, sceneColor);
        resourceBindings.bindTexture(DeferredResource.MAIN_DEPTH, resolvedDepth);
        executeStage(DeferredStage.FINAL_COMPOSITE);
    }

    /** Immutable tuning snapshot used by every deferred pass in the current frame. */
    public DeferredRuntimeConfig.Snapshot frameSettings() {
        return frameSettings;
    }

    public long frameSettingsGeneration() {
        return frameSettingsGeneration;
    }

    public DeferredResourceBindings resourceBindings() {
        return resourceBindings;
    }

    /** Frame-local cascade/probe registry exposed to deferred extension producers. */
    public DeferredSecondaryViewRegistry secondaryViews() {
        return secondaryViews;
    }

    /** Releases optional persistent/history resources before a device switch or shutdown. */
    public void releasePhysicalResources() {
        RenderSystem.assertOnRenderThread();
        physicalResources.close();
        resourceBindings.reset();
        secondaryViews.reset();
        worldOwner = null;
        frameStateId = Long.MIN_VALUE;
        targetFrameId = Long.MIN_VALUE;
        targetOwner = null;
        targets = null;
        sampleableTargets = null;
        temporalPolicyGeneration = Long.MIN_VALUE;
        temporalWidth = -1;
        temporalHeight = -1;
        temporalSamples = -1;
        pendingExternalHistoryReset.set(DeferredHistoryResetReason.NONE);
        frameSetupExecuted = false;
        shadowExecuted = false;
        preGeometryExecuted = false;
        postGeometryExecuted = false;
        primaryView.reset();
        worldStateSource.reset();
        worldRenderState = worldStateSource.current();
    }

    public DeferredPrimaryViewSource primaryView() {
        return primaryView;
    }

    /** Explicit semantic world contract frozen for the current deferred frame. */
    public WorldRenderState worldRenderState() {
        return worldRenderState;
    }

    /** Exact matrices are injected before LevelRenderer/Sodium world submission. */
    public void capturePrimaryView(long frameId,
                                   org.joml.Matrix4fc view,
                                   org.joml.Matrix4fc projection,
                                   @Nullable net.minecraft.world.phys.Vec3 cameraPosition,
                                   float farPlane) {
        if (!enabled()) return;
        primaryView.beginWorld(Minecraft.getInstance().level);
        primaryView.capture(frameId, view, projection, cameraPosition, farPlane);
    }

    public void captureSunAngle(long frameId, float sunAngle) {
        if (!enabled()) return;
        primaryView.updateSunAngle(frameId, sunAngle);
    }

    private void executeStage(DeferredStage stage) {
        resourceBindings.setHistoryEpoch(primaryView.historyDescriptor().epoch());
        CombatantRenderSystem.deferredGraph().execute(
                stage, CombatantRenderSystem.ensureFrameContext(), resourceBindings, secondaryViews, primaryView,
                worldRenderState, frameSettings
        );
    }

    private static GbufferViews lightingInputs(GeometryTargets targets) {
        if (targets.samples() <= 1) {
            return new GbufferViews(targets.surface(), targets.geometry(), targets.auxiliary(), targets.material(), targets.materialId());
        }

        RenderResourceManager resources = CombatantRenderSystem.resources();
        RenderTarget surface = acquire(resources, "world-gbuffer-surface-resolved",
                targets.width(), targets.height(), 1, SURFACE_FORMAT);
        RenderTarget geometry = acquire(resources, "world-gbuffer-geometry-resolved",
                targets.width(), targets.height(), 1, GEOMETRY_FORMAT);
        RenderTarget auxiliary = acquire(resources, "world-gbuffer-auxiliary-resolved",
                targets.width(), targets.height(), 1, AUXILIARY_FORMAT);
        RenderTarget material = acquire(resources, "world-gbuffer-material-resolved",
                targets.width(), targets.height(), 1, MATERIAL_FORMAT);
        RenderTarget materialId = acquire(resources, "world-gbuffer-material-id-resolved",
                targets.width(), targets.height(), 1, MATERIAL_ID_FORMAT);

        boolean resolved = CombatantRenderSystem.rhi().msaa().resolveTransient(
                targets.surfaceTarget(), surface, true, false
        );
        resolved &= CombatantRenderSystem.rhi().msaa().resolveTransient(
                targets.geometryTarget(), geometry, true, false
        );
        resolved &= CombatantRenderSystem.rhi().msaa().resolveTransient(
                targets.auxiliaryTarget(), auxiliary, true, false
        );
        resolved &= CombatantRenderSystem.rhi().msaa().resolveTransient(
                targets.materialTarget(), material, true, false
        );
        resolved &= CombatantRenderSystem.rhi().msaa().resolveTransient(
                targets.materialIdTarget(), materialId, true, false
        );
        if (!resolved) {
            throw new IllegalStateException("Backend could not resolve the multisampled world G-buffer");
        }
        return new GbufferViews(
                surface.getColorTextureView(), geometry.getColorTextureView(), auxiliary.getColorTextureView(),
                material.getColorTextureView(), materialId.getColorTextureView()
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

    public enum LifecycleState {
        DISABLED,
        ACTIVATING,
        ACTIVE,
        DEACTIVATING,
        FAILED
    }

    public record GeometryTargets(
            RenderTarget surfaceTarget,
            RenderTarget geometryTarget,
            RenderTarget auxiliaryTarget,
            RenderTarget materialTarget,
            RenderTarget materialIdTarget,
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

        public GpuTextureView material() {
            return materialTarget.getColorTextureView();
        }

        public GpuTextureView materialId() {
            return materialIdTarget.getColorTextureView();
        }
    }

    public record GbufferViews(
            GpuTextureView surface,
            GpuTextureView geometry,
            GpuTextureView auxiliary,
            GpuTextureView material,
            GpuTextureView materialId
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
