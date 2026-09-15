/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess.graph;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.ReimaginedVisual;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.deferred.DeferredResource;
import combatant.client.render.engine.depth.PreTranslucentDepth;
import combatant.client.render.engine.depth.WorldSceneDepth;
import combatant.client.render.engine.postprocess.PostProcessContext;
import combatant.client.render.engine.postprocess.PostProcessExecutionPolicy;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageImageDescriptor;
import combatant.client.render.iris.IrisSceneDepth;

import java.util.EnumMap;

public final class PostProcessGraphResources implements AutoCloseable {
    private final EnumMap<PostProcessResource, GpuTextureView> views = new EnumMap<>(PostProcessResource.class);

    private @Nullable RenderTarget mainFramebuffer;
    private @Nullable TextureTarget ping;
    private @Nullable TextureTarget pong;
    private @Nullable RhiStorageImage pingStorage;
    private @Nullable RhiStorageImage pongStorage;
    private @Nullable CombatantRhi storageOwner;
    private boolean storageTargetsFailed;
    private @Nullable GpuTextureView pingView;
    private @Nullable GpuTextureView pongView;
    private @Nullable PostProcessContext legacyContext;

    private boolean usePingAsSource = true;
    private boolean prepared;

    public boolean prepare(PostProcessPass.Phase phase, float tickDelta, CombatantRhi rhi, boolean preferStorageTargets) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null) return false;

        int w = Math.max(1, mc.getWindow().getWidth());
        int h = Math.max(1, mc.getWindow().getHeight());
        ensurePingPong(w, h, rhi, preferStorageTargets);
        if (pingView == null || pongView == null) return false;
        if (main.getColorTextureView() == null || pingView == null || pongView == null) return false;

        clear();
        this.mainFramebuffer = main;
        GpuTextureView mainDepth = IrisSceneDepth.isValid()
                ? IrisSceneDepth.mainDepthView()
                : WorldSceneDepth.hasMain() ? WorldSceneDepth.mainDepthView() : main.getDepthTextureView();
        GpuTextureView preTranslucentDepth = IrisSceneDepth.isValid()
                ? IrisSceneDepth.preTranslucentDepthView()
                : needsPreTranslucentDepth() ? PreTranslucentDepth.getDepthView() : null;
        GpuTextureView staticWorldDepth = IrisSceneDepth.isValid()
                ? null
                : WorldSceneDepth.hasItemEntity() ? WorldSceneDepth.itemEntityDepthView() : null;
        put(PostProcessResource.MAIN_COLOR, main.getColorTextureView());
        put(PostProcessResource.MAIN_DEPTH, mainDepth);
        put(PostProcessResource.PRE_TRANSLUCENT_DEPTH, preTranslucentDepth);
        var gbuffer = CombatantRenderSystem.deferredWorld().currentSampleableTargets();
        if (gbuffer != null) {
            put(PostProcessResource.GBUFFER_SURFACE, gbuffer.surface());
            put(PostProcessResource.GBUFFER_GEOMETRY, gbuffer.geometry());
            put(PostProcessResource.GBUFFER_AUXILIARY, gbuffer.auxiliary());
            put(PostProcessResource.GBUFFER_MATERIAL, gbuffer.material());
            put(PostProcessResource.GBUFFER_MATERIAL_ID, gbuffer.materialId());
        }
        bindDeferred(PostProcessResource.VELOCITY, DeferredResource.VELOCITY);
        bindDeferred(PostProcessResource.RESOLVED_DEPTH, DeferredResource.RESOLVED_DEPTH);
        bindDeferred(PostProcessResource.DEPTH_PYRAMID, DeferredResource.DEPTH_PYRAMID);
        bindDeferred(PostProcessResource.SHADOW_DEPTH, DeferredResource.SHADOW_DEPTH);
        bindDeferred(PostProcessResource.SHADOW_COLOR, DeferredResource.SHADOW_COLOR);
        bindDeferred(PostProcessResource.AMBIENT_OCCLUSION, DeferredResource.AMBIENT_OCCLUSION);
        bindDeferred(PostProcessResource.SCENE_RADIANCE, DeferredResource.SCENE_RADIANCE);
        bindDeferred(PostProcessResource.INDIRECT_LIGHT, DeferredResource.INDIRECT_LIGHT);
        bindDeferred(PostProcessResource.INDIRECT_CONFIDENCE, DeferredResource.INDIRECT_CONFIDENCE);
        bindDeferred(PostProcessResource.LIGHTING_COLOR, DeferredResource.LIGHTING_COLOR);
        bindDeferred(PostProcessResource.REFLECTION_COLOR, DeferredResource.REFLECTION_COLOR);
        bindDeferred(PostProcessResource.REFLECTION_CONFIDENCE, DeferredResource.REFLECTION_CONFIDENCE);
        bindDeferred(PostProcessResource.TRANSLUCENT_COLOR, DeferredResource.TRANSLUCENT_COLOR);
        bindDeferred(PostProcessResource.TRANSLUCENT_DEPTH, DeferredResource.TRANSLUCENT_DEPTH);
        bindDeferred(PostProcessResource.HISTORY_COLOR, DeferredResource.HISTORY_COLOR);
        bindDeferred(PostProcessResource.HISTORY_DEPTH, DeferredResource.HISTORY_DEPTH);
        bindDeferred(PostProcessResource.HISTORY_REFLECTION, DeferredResource.HISTORY_REFLECTION);
        put(PostProcessResource.GRAPH_SOURCE_COLOR, pingView);
        put(PostProcessResource.GRAPH_DEST_COLOR, pongView);
        this.legacyContext = new PostProcessContext(
                phase,
                tickDelta,
                main,
                main.getColorTextureView(),
                mainDepth,
                preTranslucentDepth,
                staticWorldDepth,
                main.width,
                main.height
        );
        this.usePingAsSource = true;
        this.prepared = true;
        return true;
    }

    public void resetPingPong() {
        usePingAsSource = true;
        put(PostProcessResource.GRAPH_SOURCE_COLOR, pingView);
        put(PostProcessResource.GRAPH_DEST_COLOR, pongView);
    }

    public void advancePingPong() {
        usePingAsSource = !usePingAsSource;
        put(PostProcessResource.GRAPH_SOURCE_COLOR, currentSource());
        put(PostProcessResource.GRAPH_DEST_COLOR, currentDestination());
    }

    public @Nullable GpuTextureView currentSource() {
        return usePingAsSource ? pingView : pongView;
    }

    public @Nullable GpuTextureView currentDestination() {
        return usePingAsSource ? pongView : pingView;
    }

    public @Nullable RhiStorageImage currentSourceStorage() {
        return usePingAsSource ? pingStorage : pongStorage;
    }

    public @Nullable RhiStorageImage currentDestinationStorage() {
        return usePingAsSource ? pongStorage : pingStorage;
    }

    public @Nullable GpuTextureView finalColor() {
        return currentSource();
    }

    public boolean isPrepared() {
        return prepared;
    }

    public @Nullable RenderTarget mainFramebuffer() {
        return mainFramebuffer;
    }

    public @Nullable GpuTextureView mainColor() {
        return get(PostProcessResource.MAIN_COLOR);
    }

    public @Nullable PostProcessContext legacyContext() {
        return legacyContext;
    }

    public void put(PostProcessResource resource, @Nullable GpuTextureView view) {
        if (view != null) views.put(resource, view);
    }

    public @Nullable GpuTextureView get(PostProcessResource resource) {
        return views.get(resource);
    }

    public void clear() {
        views.clear();
        mainFramebuffer = null;
        legacyContext = null;
        prepared = false;
    }

    private void bindDeferred(PostProcessResource target, DeferredResource source) {
        var bindings = CombatantRenderSystem.deferredWorld().resourceBindings();
        if (!bindings.isValid(source)) return;
        put(target, bindings.texture(source));
    }

    private void ensurePingPong(int w, int h, CombatantRhi rhi, boolean preferStorageTargets) {
        if (storageOwner != null && storageOwner != rhi) {
            closeStoragePingPong();
            storageTargetsFailed = false;
        }
        if (preferStorageTargets && PostProcessExecutionPolicy.useCompute(rhi) && !storageTargetsFailed) {
            try {
                ping = null;
                pong = null;
                ensureStoragePingPong(w, h, rhi);
                pingView = pingStorage != null ? pingStorage.view() : null;
                pongView = pongStorage != null ? pongStorage.view() : null;
                if (pingView != null && pongView != null) return;
            } catch (Throwable t) {
                PostProcessExecutionPolicy.warnRuntimeFallback(
                        "postprocess-storage-targets",
                        "Post-process storage targets",
                        t);
                closeStoragePingPong();
                storageTargetsFailed = true;
            }
        }

        closeStoragePingPong();
        ping = CombatantRenderSystem.resources().persistentFramebuffer(
                "combatant-postprocess-graph-ping", w, h, false, "PostProcessGraph");
        pong = CombatantRenderSystem.resources().persistentFramebuffer(
                "combatant-postprocess-graph-pong", w, h, false, "PostProcessGraph");
        pingView = ping != null ? ping.getColorTextureView() : null;
        pongView = pong != null ? pong.getColorTextureView() : null;
    }

    private void ensureStoragePingPong(int w, int h, CombatantRhi rhi) {
        boolean ownerChanged = storageOwner != rhi;
        boolean sizeChanged = pingStorage == null || pongStorage == null
                || pingStorage.descriptor().width() != w || pingStorage.descriptor().height() != h
                || pongStorage.descriptor().width() != w || pongStorage.descriptor().height() != h;
        if (!ownerChanged && !sizeChanged) return;

        closeStoragePingPong();
        storageOwner = rhi;
        pingStorage = rhi.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-postprocess-graph-ping-storage", w, h, GpuFormat.RGBA8_UNORM,
                StorageAccess.READ_WRITE, true, true));
        try {
            pongStorage = rhi.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-postprocess-graph-pong-storage", w, h, GpuFormat.RGBA8_UNORM,
                    StorageAccess.READ_WRITE, true, true));
        } catch (Throwable t) {
            closeStoragePingPong();
            throw t;
        }
    }

    private void closeStoragePingPong() {
        if (pingStorage != null) {
            try { pingStorage.close(); } catch (Throwable ignored) { }
            pingStorage = null;
        }
        if (pongStorage != null) {
            try { pongStorage.close(); } catch (Throwable ignored) { }
            pongStorage = null;
        }
        storageOwner = null;
    }

    private static boolean needsPreTranslucentDepth() {
        ReimaginedVisual module = Modules.get(ReimaginedVisual.class);
        return module != null && module.needsPreTranslucentDepthCapture();
    }

    public void releaseBackendResources(CombatantRhi owner) {
        if (storageOwner == null || owner == null || storageOwner == owner) {
            closeStoragePingPong();
            storageTargetsFailed = false;
            pingView = ping != null ? ping.getColorTextureView() : null;
            pongView = pong != null ? pong.getColorTextureView() : null;
        }
    }

    @Override
    public void close() {
        // Framebuffers are owned by RenderResourceManager. This object only drops references.
        closeStoragePingPong();
        ping = null;
        pong = null;
        pingView = null;
        pongView = null;
        clear();
    }
}
