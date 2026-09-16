/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.framegraph.CompiledFrameGraph;
import combatant.client.render.engine.framegraph.FrameGraphContractCompiler;
import combatant.client.render.engine.framegraph.FrameGraphAccess;
import combatant.client.render.engine.framegraph.FrameGraphPassContract;
import combatant.client.render.engine.framegraph.FrameGraphResourceKey;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.world.WorldRenderState;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Ordered world-pass registry. Geometry producers remain externally driven during migration;
 * compute/tessellation/post stages register here and execute only at explicit phase boundaries.
 */
public final class DeferredPassGraph {
    private static final Comparator<DeferredPassSpec> ORDER = Comparator
            .comparingInt((DeferredPassSpec pass) -> pass.stage().ordinal())
            .thenComparingInt(DeferredPassSpec::priority)
            .thenComparing(DeferredPassSpec::id);

    private final ArrayList<DeferredPassSpec> corePasses = new ArrayList<>();
    private final ArrayList<DeferredPassSpec> extensionPasses = new ArrayList<>();
    private final DeferredBackendPasses backendPasses = new DeferredBackendPasses();
    private List<DeferredPassSpec> ordered = List.of();
    private CompiledFrameGraph compiled = new CompiledFrameGraph(List.of(), List.of());
    private boolean dirty = true;

    public DeferredPassGraph() {
        corePasses.add(DeferredPassSpec.builder("world.geometry.opaque", DeferredStage.OPAQUE_GEOMETRY)
                .write(DeferredResource.SCENE_COLOR, DeferredResource.MAIN_DEPTH,
                        DeferredResource.GBUFFER_SURFACE, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_AUXILIARY, DeferredResource.GBUFFER_MATERIAL,
                        DeferredResource.GBUFFER_MATERIAL_ID)
                .external().build());
        corePasses.add(DeferredPassSpec.builder("world.geometry.cutout", DeferredStage.CUTOUT_GEOMETRY)
                .readWrite(DeferredResource.SCENE_COLOR, DeferredResource.MAIN_DEPTH,
                        DeferredResource.GBUFFER_SURFACE, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_AUXILIARY, DeferredResource.GBUFFER_MATERIAL,
                        DeferredResource.GBUFFER_MATERIAL_ID)
                .external().build());
        corePasses.add(DeferredPassSpec.builder("world.lighting.neutral", DeferredStage.LIGHTING)
                .read(DeferredResource.GBUFFER_SURFACE, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_AUXILIARY, DeferredResource.GBUFFER_MATERIAL,
                        DeferredResource.GBUFFER_DEPTH, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.SHADOW_COLOR, DeferredResource.CLOUD_SHADOW_VISIBILITY,
                        DeferredResource.AMBIENT_OCCLUSION, DeferredResource.ENVIRONMENT_IRRADIANCE)
                .write(DeferredResource.DIRECT_LIGHTING_COLOR, DeferredResource.SCENE_COLOR)
                .external().build());
        corePasses.add(DeferredPassSpec.builder("world.forward.opaque", DeferredStage.FORWARD_OPAQUE)
                .readWrite(DeferredResource.SCENE_COLOR, DeferredResource.MAIN_DEPTH)
                .external().build());
        corePasses.add(DeferredPassSpec.builder("world.translucency.forward", DeferredStage.TRANSLUCENCY)
                .readWrite(DeferredResource.SCENE_COLOR, DeferredResource.MAIN_DEPTH)
                .external().build());
        corePasses.add(DeferredPassSpec.builder("world.postprocess.external", DeferredStage.POST_PROCESS)
                .read(DeferredResource.MAIN_DEPTH)
                .readWrite(DeferredResource.SCENE_COLOR)
                .external().build());
        backendPasses.install(corePasses);
    }

    /** Releases native compute pipelines before the active RHI/device is destroyed or switched. */
    public synchronized void releaseBackendResources(CombatantRhi owner) {
        backendPasses.release(owner);
    }

    /** Compiles native deferred backend programs only after the deferred runtime asset scope activates. */
    public synchronized void prepareBackendResources() {
        backendPasses.prepare(CombatantRenderSystem.rhi());
    }

    public synchronized AutoCloseable register(DeferredPassSpec pass) {
        if (pass == null) return () -> { };
        if (containsId(pass.id())) throw new IllegalArgumentException("Duplicate deferred pass id: " + pass.id());
        extensionPasses.add(pass);
        dirty = true;
        return () -> unregister(pass);
    }

    public synchronized CompiledFrameGraph compile() {
        ensureCompiled();
        return compiled;
    }

    public void execute(DeferredStage stage, RenderFrameContext frame, DeferredResourceBindings resources) {
        execute(stage, frame, resources, new DeferredSecondaryViewRegistry(), new DeferredPrimaryViewSource(),
                WorldRenderState.unknown(0L), DeferredRuntimeConfig.current());
    }

    public void execute(DeferredStage stage,
                        RenderFrameContext frame,
                        DeferredResourceBindings resources,
                        DeferredSecondaryViewRegistry secondaryViews,
                        DeferredPrimaryViewSource primaryView) {
        execute(stage, frame, resources, secondaryViews, primaryView, WorldRenderState.unknown(0L),
                DeferredRuntimeConfig.current());
    }

    public void execute(DeferredStage stage,
                        RenderFrameContext frame,
                        DeferredResourceBindings resources,
                        DeferredSecondaryViewRegistry secondaryViews,
                        DeferredPrimaryViewSource primaryView,
                        WorldRenderState worldState,
                        DeferredRuntimeConfig.Snapshot settings) {
        if (stage == null || frame == null || resources == null) return;
        if (secondaryViews == null) throw new IllegalArgumentException("secondaryViews");
        if (primaryView == null) throw new IllegalArgumentException("primaryView");
        List<DeferredPassSpec> snapshot;
        CompiledFrameGraph compiledSnapshot;
        synchronized (this) {
            ensureCompiled();
            snapshot = ordered;
            compiledSnapshot = compiled;
        }

        CombatantRhi rhi = CombatantRenderSystem.rhi();
        DeferredPassContext context = new DeferredPassContext(
                stage, frame, rhi, resources, secondaryViews, primaryView, worldState, settings
        );
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(stage.renderPhase(), "deferred:" + stage.name().toLowerCase())) {
            for (int passIndex = 0; passIndex < snapshot.size(); passIndex++) {
                DeferredPassSpec pass = snapshot.get(passIndex);
                if (pass.stage() != stage || pass.externallyDriven()) continue;
                if (!supports(pass, rhi)) {
                    DebugLog.warnOnChange(
                            "deferred.pass.unsupported." + pass.id(),
                            pass.requiredShaderStages().toString(),
                            "[Deferred] skipping pass %s; required shader stages are unavailable: %s",
                            pass.id(), pass.requiredShaderStages()
                    );
                    continue;
                }
                try {
                    if (!pass.condition().test(context)) continue;
                    prepareResources(pass, context);
                    lowerAdvancedBarriers(passIndex, snapshot, compiledSnapshot, context);
                    pass.executor().execute(context);
                    markWrites(pass, context.resources());
                } catch (Throwable t) {
                    DebugLog.warnOnChange(
                            "deferred.pass.failed." + pass.id(),
                            t.getClass().getSimpleName() + "|" + t.getMessage(),
                            "[Deferred] pass %s failed: %s: %s",
                            pass.id(), t.getClass().getSimpleName(), t.getMessage()
                    );
                }
            }
        }
    }

    public synchronized List<DeferredPassSpec> passes() {
        ensureCompiled();
        return ordered;
    }

    private synchronized void unregister(DeferredPassSpec pass) {
        if (extensionPasses.remove(pass)) dirty = true;
    }

    private boolean containsId(String id) {
        for (DeferredPassSpec pass : corePasses) if (pass.id().equals(id)) return true;
        for (DeferredPassSpec pass : extensionPasses) if (pass.id().equals(id)) return true;
        return false;
    }

    private void ensureCompiled() {
        if (!dirty) return;
        ArrayList<DeferredPassSpec> next = new ArrayList<>(corePasses.size() + extensionPasses.size());
        next.addAll(corePasses);
        next.addAll(extensionPasses);
        next.sort(ORDER);

        Set<String> ids = new HashSet<>();
        ArrayList<FrameGraphPassContract> contracts = new ArrayList<>(next.size());
        for (DeferredPassSpec pass : next) {
            if (!ids.add(pass.id())) throw new IllegalStateException("Duplicate deferred pass id: " + pass.id());
            contracts.add(pass.contract());
        }
        compiled = FrameGraphContractCompiler.compile(contracts);
        ordered = List.copyOf(next);
        dirty = false;
    }

    private static boolean supports(DeferredPassSpec pass, CombatantRhi rhi) {
        for (RhiShaderStage stage : pass.requiredShaderStages()) {
            if (!rhi.advancedShaders().supports(stage)) return false;
        }
        return true;
    }

    private static void prepareResources(DeferredPassSpec pass, DeferredPassContext context) {
        for (var use : pass.resources()) {
            DeferredResource resource = resource(use.resource());
            if (resource == null) continue;

            // Persistent history survives physically across frames while logical bindings are
            // frame-local. Rebind an existing allocation for reads, but never allocate a missing
            // optional input just because a consumer declared it.
            if (use.access().reads() && !context.resources().isBound(resource)) {
                context.resources().bindExisting(resource, context.settings());
            }

            if (!use.access().writes() || resource.textureSpec() == null
                    || context.resources().isBound(resource)) {
                continue;
            }
            context.resources().ensureTexture(resource, context.rhi(), context.settings());
        }
    }

    private static void markWrites(DeferredPassSpec pass, DeferredResourceBindings resources) {
        for (var use : pass.resources()) {
            if (!use.access().writes()) continue;
            DeferredResource resource = resource(use.resource());
            if (resource != null) resources.markWritten(resource);
        }
    }

    private static void lowerAdvancedBarriers(int consumerIndex,
                                              List<DeferredPassSpec> passes,
                                              CompiledFrameGraph graph,
                                              DeferredPassContext context) {
        Map<BarrierKey, BarrierResources> grouped = new LinkedHashMap<>();
        Set<BarrierKey> genericTextureBarriers = new HashSet<>();
        for (CompiledFrameGraph.Dependency dependency : graph.dependencies()) {
            if (dependency.consumerIndex() != consumerIndex) continue;
            DeferredResource resource = resource(dependency.resource());
            if (resource == null) continue;

            RhiStorageBuffer buffer = context.resources().buffer(resource);
            RhiStorageImage image = context.resources().storageImage(resource);
            RhiStorageVolume volume = context.resources().storageVolume(resource);
            boolean genericTexture = image == null && volume == null && context.resources().texture(resource) != null;
            if (buffer == null && image == null && volume == null && !genericTexture) continue;

            DeferredPassSpec producer = passes.get(dependency.producerIndex());
            DeferredPassSpec consumer = passes.get(dependency.consumerIndex());
            RhiResourceBarrier.Access sourceAccess = barrierAccess(access(producer, dependency.resource()));
            RhiResourceBarrier.Access destinationAccess = barrierAccess(access(consumer, dependency.resource()));
            if (genericTexture) {
                // Mojang-owned render targets/atlas views do not expose a Combatant RhiStorageImage.
                // Use a conservative global memory dependency so framebuffer writes are visible to
                // later compute/sampled consumers on both GL and Vulkan.
                genericTextureBarriers.add(new BarrierKey(
                        RhiResourceBarrier.Stage.ALL, sourceAccess,
                        RhiResourceBarrier.Stage.ALL, destinationAccess
                ));
            }

            if (buffer == null && image == null && volume == null) continue;
            BarrierKey key = new BarrierKey(
                    barrierStage(producer), sourceAccess,
                    barrierStage(consumer), destinationAccess
            );
            BarrierResources values = grouped.computeIfAbsent(key, ignored -> new BarrierResources());
            if (buffer != null && !values.buffers.contains(buffer)) values.buffers.add(buffer);
            if (image != null && !values.images.contains(image)) values.images.add(image);
            if (volume != null && !values.volumes.contains(volume)) values.volumes.add(volume);
        }

        for (Map.Entry<BarrierKey, BarrierResources> entry : grouped.entrySet()) {
            BarrierKey key = entry.getKey();
            BarrierResources resources = entry.getValue();
            context.advancedShaders().barrier(new RhiResourceBarrier(
                    key.sourceStage, key.sourceAccess,
                    key.destinationStage, key.destinationAccess,
                    resources.buffers, resources.images, resources.volumes
            ));
        }
        for (BarrierKey key : genericTextureBarriers) {
            context.advancedShaders().barrier(new RhiResourceBarrier(
                    key.sourceStage, key.sourceAccess,
                    key.destinationStage, key.destinationAccess,
                    List.of(), List.of()
            ));
        }
    }

    private static DeferredResource resource(FrameGraphResourceKey key) {
        for (DeferredResource resource : DeferredResource.values()) {
            if (resource.key().equals(key)) return resource;
        }
        return null;
    }

    private static FrameGraphAccess access(DeferredPassSpec pass, FrameGraphResourceKey resource) {
        for (var use : pass.resources()) if (use.resource().equals(resource)) return use.access();
        throw new IllegalStateException("Pass " + pass.id() + " does not declare " + resource.name());
    }

    private static RhiResourceBarrier.Stage barrierStage(DeferredPassSpec pass) {
        if (pass.requiredShaderStages().contains(RhiShaderStage.COMPUTE)) {
            return RhiResourceBarrier.Stage.COMPUTE;
        }
        return RhiResourceBarrier.Stage.GRAPHICS;
    }

    private static RhiResourceBarrier.Access barrierAccess(FrameGraphAccess access) {
        return switch (access) {
            case READ -> RhiResourceBarrier.Access.READ;
            case WRITE -> RhiResourceBarrier.Access.WRITE;
            case READ_WRITE -> RhiResourceBarrier.Access.READ_WRITE;
        };
    }

    private record BarrierKey(
            RhiResourceBarrier.Stage sourceStage,
            RhiResourceBarrier.Access sourceAccess,
            RhiResourceBarrier.Stage destinationStage,
            RhiResourceBarrier.Access destinationAccess
    ) {
    }

    private static final class BarrierResources {
        private final ArrayList<RhiStorageBuffer> buffers = new ArrayList<>();
        private final ArrayList<RhiStorageImage> images = new ArrayList<>();
        private final ArrayList<RhiStorageVolume> volumes = new ArrayList<>();
    }
}
