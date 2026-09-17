/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.profiler.TracyGpuProfiler;
import combatant.client.render.engine.profiler.TracyProfiler;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.resource.FrameGraphPhysicalResource;
import combatant.client.render.engine.rhi.resource.FrameGraphPhysicalResourcePool;
import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Combatant frame graph front-end and physical lifetime owner.
 * Logical resource identity remains stable while the compiler selects reusable physical allocations.
 */
public final class CombatantFrameGraph implements AutoCloseable {
    private final PassScheduler scheduler = new PassScheduler();
    private final FrameGraphResourceRegistry resources = new FrameGraphResourceRegistry();
    private CombatantRhi physicalOwner;

    public void add(RenderPhase phase, Consumer<RenderFrameContext> handler) {
        add(phase, phase == null ? "unnamed" : phase.name().toLowerCase(), handler);
    }

    public void add(RenderPhase phase, String label, Consumer<RenderFrameContext> handler) {
        if (handler == null) return;
        scheduler.add(new RenderPassNode(phase, label, handler));
    }

    public void add(FrameGraphPassContract contract, Consumer<RenderFrameContext> handler) {
        if (contract == null || handler == null) return;
        scheduler.add(new RenderPassNode(contract, handler));
    }

    /** A disabled node is removed before dependency/lifetime planning, not skipped after allocation. */
    public void add(FrameGraphPassContract contract,
                    BooleanSupplier enabled,
                    Consumer<RenderFrameContext> handler) {
        if (contract == null || handler == null) return;
        scheduler.add(new RenderPassNode(contract, enabled, handler));
    }

    public void declare(FrameGraphResourceKey resource, FrameGraphPhysicalResourceDescriptor descriptor) {
        resources.declare(resource, descriptor);
    }

    public void declareExternal(FrameGraphResourceKey resource) {
        resources.declareExternal(resource);
    }

    public CompiledFrameGraph compile(RenderPhase phase) {
        List<RenderPassNode> nodes = scheduler.enabledNodes(phase);
        return scheduler.compile(nodes, resources.isEmpty() ? null : resources.snapshot());
    }

    /** Compiles the complete ordered frame so lifetimes and aliasing can span phase boundaries. */
    public CompiledFrameGraph compileFrame() {
        List<RenderPassNode> nodes = scheduler.enabledFrameNodes();
        return scheduler.compile(nodes, resources.isEmpty() ? null : resources.snapshot());
    }

    public FrameGraphPhysicalResource physicalResource(FrameGraphResourceKey resource) {
        CombatantRhi owner = physicalOwner;
        if (owner == null) return null;
        return owner.resources().frameGraphResources().resolve(this, resource);
    }

    public boolean isValid(FrameGraphResourceKey resource) {
        CombatantRhi owner = physicalOwner;
        return owner != null && owner.resources().frameGraphResources().valid(this, resource);
    }

    public String debugDump(RenderPhase phase) {
        return compile(phase).debugDump();
    }

    public String debugDumpFrame() {
        return compileFrame().debugDump();
    }

    public void execute(RenderPhase phase, RenderFrameContext baseContext) {
        RenderPhase resolvedPhase = phase == null ? RenderPhase.NONE : phase;
        boolean framePhysicalPlanning = !resources.isEmpty();
        List<RenderPassNode> nodes = framePhysicalPlanning
                ? scheduler.enabledFrameNodes()
                : scheduler.enabledNodes(resolvedPhase);
        if (nodes.isEmpty()) return;
        boolean hasPhaseNode = false;
        for (RenderPassNode node : nodes) {
            if (node.phase() == resolvedPhase) {
                hasPhaseNode = true;
                break;
            }
        }
        if (!hasPhaseNode) return;

        CompiledFrameGraph compiled;
        try (TracyProfiler.Scope ignored = TracyProfiler.beginZone(
                "framegraph/compile/" + resolvedPhase.name().toLowerCase(Locale.ROOT))) {
            compiled = scheduler.compile(nodes, framePhysicalPlanning ? resources.snapshot() : null);
        }
        CombatantRhi rhi = CombatantRenderSystem.rhi();
        if (physicalOwner != null && physicalOwner != rhi) {
            try {
                physicalOwner.resources().frameGraphResources().releaseScope(this);
            } catch (RuntimeException ignored) {
                // The previous backend may already have completed teardown.
            }
            physicalOwner = null;
        }
        FrameGraphPhysicalResourcePool physicalPool = rhi.resources().frameGraphResources();
        boolean physicalPlanning = !compiled.physicalPlan().physicalAllocations().isEmpty();
        if (physicalPlanning) {
            try (TracyProfiler.Scope ignored = TracyProfiler.beginZone(
                    "framegraph/materialize/" + resolvedPhase.name().toLowerCase(Locale.ROOT))) {
                physicalPool.materialize(this, compiled.physicalPlan(), rhi);
            }
            physicalOwner = rhi;
        }

        String label = "framegraph:" + (phase == null ? "none" : phase.name().toLowerCase());
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(phase, label)) {
            RenderFrameContext ctx = CombatantRenderSystem.currentContext();
            if (ctx == null && baseContext != null) ctx = baseContext.withPhase(phase);
            if (ctx == null) return;
            for (int passIndex = 0; passIndex < nodes.size(); passIndex++) {
                RenderPassNode node = nodes.get(passIndex);
                if (node.phase() != resolvedPhase) continue;
                if (physicalPlanning) {
                    invalidateRetiredAliases(passIndex, compiled, physicalPool);
                    validateReads(node.contract(), physicalPool);
                    lowerBarriers(passIndex, compiled, physicalPool, rhi);
                }
                try (RenderPhaseScope nodeScope = CombatantRenderSystem.phase(phase, "pass:" + node.label())) {
                    try (TracyGpuProfiler.Scope ignoredGpu = TracyGpuProfiler.beginZone(
                            "framegraph/" + resolvedPhase.name().toLowerCase(Locale.ROOT) + "/" + node.label())) {
                        node.execute(CombatantRenderSystem.currentContext());
                    }
                }
                if (physicalPlanning) markWrites(node.contract(), physicalPool);
            }
        }
    }

    public void clear() {
        scheduler.clear();
        resources.clear();
        releasePhysicalResources();
    }

    @Override
    public void close() {
        clear();
    }


    private void invalidateRetiredAliases(int consumerIndex,
                                          CompiledFrameGraph graph,
                                          FrameGraphPhysicalResourcePool pool) {
        for (FrameGraphPhysicalPlan.PhysicalAllocationPlan allocation : graph.physicalPlan().physicalAllocations()) {
            if (allocation.logicalResources().size() < 2) continue;
            ArrayList<FrameGraphPhysicalPlan.LogicalResourcePlan> aliases = new ArrayList<>();
            for (FrameGraphResourceKey key : allocation.logicalResources()) {
                FrameGraphPhysicalPlan.LogicalResourcePlan logical = graph.physicalPlan().logical(key);
                if (logical != null) aliases.add(logical);
            }
            aliases.sort(java.util.Comparator.comparingInt(FrameGraphPhysicalPlan.LogicalResourcePlan::firstUse));
            for (int i = 1; i < aliases.size(); i++) {
                FrameGraphPhysicalPlan.LogicalResourcePlan current = aliases.get(i);
                if (current.firstUse() == consumerIndex) {
                    pool.invalidateLogical(this, aliases.get(i - 1).resource());
                }
            }
        }
    }

    private void validateReads(FrameGraphPassContract pass, FrameGraphPhysicalResourcePool pool) {
        for (FrameGraphResourceUse use : pass.resources()) {
            if (!use.access().reads() || use.resource().lifetime() != FrameGraphResourceLifetime.TRANSIENT) continue;
            if (!pool.valid(this, use.resource())) {
                throw new IllegalStateException("Enabled pass '" + pass.label() + "' would read transient resource '"
                        + use.resource().name() + "' before its enabled producer completed");
            }
        }
    }

    private void markWrites(FrameGraphPassContract pass, FrameGraphPhysicalResourcePool pool) {
        for (FrameGraphResourceUse use : pass.resources()) {
            if (use.access().writes() && use.resource().lifetime() != FrameGraphResourceLifetime.EXTERNAL) {
                pool.markProduced(this, use.resource());
            }
        }
    }

    private void lowerBarriers(int consumerIndex,
                               CompiledFrameGraph graph,
                               FrameGraphPhysicalResourcePool pool,
                               CombatantRhi rhi) {
        LinkedHashMap<BarrierKey, BarrierResources> grouped = new LinkedHashMap<>();
        Set<BarrierKey> generic = new HashSet<>();
        for (CompiledFrameGraph.Dependency dependency : graph.dependencies()) {
            if (dependency.consumerIndex() != consumerIndex) continue;
            FrameGraphPassContract producer = graph.orderedPasses().get(dependency.producerIndex());
            FrameGraphPassContract consumer = graph.orderedPasses().get(dependency.consumerIndex());
            addBarrier(grouped, generic, dependency.resource(),
                    stage(producer), access(access(producer, dependency.resource())),
                    stage(consumer), access(access(consumer, dependency.resource())), pool);
        }
        addAliasReuseBarriers(consumerIndex, graph, grouped, generic, pool);

        for (Map.Entry<BarrierKey, BarrierResources> entry : grouped.entrySet()) {
            BarrierKey key = entry.getKey();
            BarrierResources values = entry.getValue();
            rhi.advancedShaders().barrier(new RhiResourceBarrier(
                    key.sourceStage, key.sourceAccess, key.destinationStage, key.destinationAccess,
                    values.buffers, values.images, values.volumes));
        }
        for (BarrierKey key : generic) {
            rhi.advancedShaders().barrier(new RhiResourceBarrier(
                    key.sourceStage, key.sourceAccess, key.destinationStage, key.destinationAccess,
                    List.of(), List.of(), List.of()));
        }
    }

    private void addAliasReuseBarriers(int consumerIndex,
                                       CompiledFrameGraph graph,
                                       Map<BarrierKey, BarrierResources> grouped,
                                       Set<BarrierKey> generic,
                                       FrameGraphPhysicalResourcePool pool) {
        for (FrameGraphPhysicalPlan.PhysicalAllocationPlan allocation : graph.physicalPlan().physicalAllocations()) {
            if (allocation.logicalResources().size() < 2) continue;
            ArrayList<FrameGraphPhysicalPlan.LogicalResourcePlan> aliases = new ArrayList<>();
            for (FrameGraphResourceKey key : allocation.logicalResources()) {
                FrameGraphPhysicalPlan.LogicalResourcePlan logical = graph.physicalPlan().logical(key);
                if (logical != null) aliases.add(logical);
            }
            aliases.sort(java.util.Comparator.comparingInt(FrameGraphPhysicalPlan.LogicalResourcePlan::firstUse));
            for (int i = 1; i < aliases.size(); i++) {
                FrameGraphPhysicalPlan.LogicalResourcePlan previous = aliases.get(i - 1);
                FrameGraphPhysicalPlan.LogicalResourcePlan current = aliases.get(i);
                if (current.firstUse() != consumerIndex) continue;
                FrameGraphPassContract producer = graph.orderedPasses().get(previous.lastUse());
                FrameGraphPassContract consumer = graph.orderedPasses().get(current.firstUse());
                addBarrier(grouped, generic, current.resource(),
                        stage(producer), access(access(producer, previous.resource())),
                        stage(consumer), access(access(consumer, current.resource())), pool);
            }
        }
    }

    private void addBarrier(Map<BarrierKey, BarrierResources> grouped,
                            Set<BarrierKey> generic,
                            FrameGraphResourceKey resource,
                            RhiResourceBarrier.Stage sourceStage,
                            RhiResourceBarrier.Access sourceAccess,
                            RhiResourceBarrier.Stage destinationStage,
                            RhiResourceBarrier.Access destinationAccess,
                            FrameGraphPhysicalResourcePool pool) {
        FrameGraphPhysicalResource physical = pool.resolve(this, resource);
        if (physical == null) return;
        BarrierKey key = new BarrierKey(sourceStage, sourceAccess, destinationStage, destinationAccess);
        RhiStorageBuffer buffer = physical.storageBuffer();
        RhiStorageImage image = physical.storageImage();
        RhiStorageVolume volume = physical.storageVolume();
        if (buffer == null && image == null && volume == null) {
            generic.add(key);
            return;
        }
        BarrierResources values = grouped.computeIfAbsent(key, ignored -> new BarrierResources());
        if (buffer != null && !values.buffers.contains(buffer)) values.buffers.add(buffer);
        if (image != null && !values.images.contains(image)) values.images.add(image);
        if (volume != null && !values.volumes.contains(volume)) values.volumes.add(volume);
    }

    private static FrameGraphAccess access(FrameGraphPassContract pass, FrameGraphResourceKey resource) {
        for (FrameGraphResourceUse use : pass.resources()) if (use.resource().equals(resource)) return use.access();
        throw new IllegalStateException("Pass '" + pass.label() + "' does not declare " + resource.name());
    }

    private static RhiResourceBarrier.Stage stage(FrameGraphPassContract pass) {
        return switch (pass.executionDomain()) {
            case GRAPHICS -> RhiResourceBarrier.Stage.GRAPHICS;
            case COMPUTE -> RhiResourceBarrier.Stage.COMPUTE;
            case TRANSFER -> RhiResourceBarrier.Stage.TRANSFER;
        };
    }

    private static RhiResourceBarrier.Access access(FrameGraphAccess access) {
        return switch (access) {
            case READ -> RhiResourceBarrier.Access.READ;
            case WRITE -> RhiResourceBarrier.Access.WRITE;
            case READ_WRITE -> RhiResourceBarrier.Access.READ_WRITE;
        };
    }

    private void releasePhysicalResources() {
        CombatantRhi owner = physicalOwner;
        physicalOwner = null;
        if (owner == null) return;
        try {
            owner.resources().frameGraphResources().releaseScope(this);
        } catch (RuntimeException ignored) {
            // Device teardown may already have drained this scope; the RHI remains the lifetime owner.
        }
    }

    private record BarrierKey(RhiResourceBarrier.Stage sourceStage,
                              RhiResourceBarrier.Access sourceAccess,
                              RhiResourceBarrier.Stage destinationStage,
                              RhiResourceBarrier.Access destinationAccess) {
    }

    private static final class BarrierResources {
        final ArrayList<RhiStorageBuffer> buffers = new ArrayList<>();
        final ArrayList<RhiStorageImage> images = new ArrayList<>();
        final ArrayList<RhiStorageVolume> volumes = new ArrayList<>();
    }
}
