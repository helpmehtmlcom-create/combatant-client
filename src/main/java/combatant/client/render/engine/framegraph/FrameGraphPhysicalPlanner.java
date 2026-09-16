/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic interval planner for graph-owned physical allocations. */
public final class FrameGraphPhysicalPlanner {
    private FrameGraphPhysicalPlanner() {
    }

    public static FrameGraphPhysicalPlan plan(List<FrameGraphPassContract> passes,
                                              Map<FrameGraphResourceKey, FrameGraphResourceDeclaration> declarations) {
        if (passes == null || passes.isEmpty()) return FrameGraphPhysicalPlan.EMPTY;
        Map<FrameGraphResourceKey, FrameGraphResourceDeclaration> declared = declarations == null ? Map.of() : declarations;
        LinkedHashMap<FrameGraphResourceKey, MutableLogical> logical = new LinkedHashMap<>();

        for (int passIndex = 0; passIndex < passes.size(); passIndex++) {
            FrameGraphPassContract pass = passes.get(passIndex);
            for (FrameGraphResourceUse use : pass.resources()) {
                FrameGraphResourceKey key = use.resource();
                FrameGraphResourceDeclaration declaration = declared.get(key);
                if (key.lifetime() != FrameGraphResourceLifetime.EXTERNAL && declaration == null) {
                    throw new IllegalStateException("Frame-graph resource '" + key.name()
                            + "' has no physical allocation descriptor");
                }
                if (key.lifetime() == FrameGraphResourceLifetime.EXTERNAL) {
                    if (declaration != null && declaration.descriptor() != null) {
                        throw new IllegalStateException("External resource cannot have an owned descriptor: " + key.name());
                    }
                } else if (!declaration.descriptor().supports(use.access())) {
                    throw new IllegalStateException("Physical descriptor for '" + key.name() + "' does not support "
                            + use.access() + " in pass '" + pass.label() + "'");
                }
                MutableLogical state = logical.get(key);
                if (state == null) {
                    state = new MutableLogical(key, declaration == null ? null : declaration.descriptor(), passIndex);
                    logical.put(key, state);
                }
                state.lastUse = passIndex;
            }
        }

        ArrayList<MutableLogical> transientResources = new ArrayList<>();
        ArrayList<MutableLogical> persistentResources = new ArrayList<>();
        for (MutableLogical state : logical.values()) {
            if (state.key.lifetime() == FrameGraphResourceLifetime.TRANSIENT) transientResources.add(state);
            else if (state.key.lifetime() == FrameGraphResourceLifetime.PERSISTENT) persistentResources.add(state);
        }
        transientResources.sort(Comparator.comparingInt((MutableLogical r) -> r.firstUse).thenComparing(r -> r.key.name()));
        persistentResources.sort(Comparator.comparing(r -> r.key.name()));

        ArrayList<MutablePhysical> physical = new ArrayList<>();
        int nextId = 0;
        int nextAliasGroup = 0;
        for (MutableLogical state : transientResources) {
            MutablePhysical chosen = null;
            for (MutablePhysical candidate : physical) {
                if (candidate.lifetime != FrameGraphResourceLifetime.TRANSIENT) continue;
                if (candidate.lastUse >= state.firstUse) continue;
                if (!candidate.descriptor.aliasCompatible(state.descriptor)) continue;
                chosen = candidate;
                break;
            }
            if (chosen == null) {
                chosen = new MutablePhysical(nextId++, nextAliasGroup++, FrameGraphResourceLifetime.TRANSIENT,
                        state.descriptor, state.lastUse);
                physical.add(chosen);
            } else {
                chosen.lastUse = state.lastUse;
            }
            chosen.logicalResources.add(state.key);
            state.physicalId = chosen.id;
            state.aliasGroup = chosen.aliasGroup;
        }

        for (MutableLogical state : persistentResources) {
            MutablePhysical allocation = new MutablePhysical(nextId++, nextAliasGroup++,
                    FrameGraphResourceLifetime.PERSISTENT, state.descriptor, state.lastUse);
            allocation.logicalResources.add(state.key);
            physical.add(allocation);
            state.physicalId = allocation.id;
            state.aliasGroup = allocation.aliasGroup;
        }

        ArrayList<FrameGraphPhysicalPlan.LogicalResourcePlan> logicalPlans = new ArrayList<>();
        long transientLogicalBytes = 0L;
        long persistentBytes = 0L;
        for (MutableLogical state : logical.values()) {
            long bytes = state.descriptor == null ? 0L : state.descriptor.approximateByteSize();
            if (state.key.lifetime() == FrameGraphResourceLifetime.TRANSIENT) transientLogicalBytes = saturatedAdd(transientLogicalBytes, bytes);
            if (state.key.lifetime() == FrameGraphResourceLifetime.PERSISTENT) persistentBytes = saturatedAdd(persistentBytes, bytes);
            logicalPlans.add(new FrameGraphPhysicalPlan.LogicalResourcePlan(
                    state.key, state.descriptor, state.firstUse, state.lastUse,
                    state.key.lifetime() == FrameGraphResourceLifetime.EXTERNAL ? -1 : state.physicalId,
                    state.key.lifetime() == FrameGraphResourceLifetime.EXTERNAL ? -1 : state.aliasGroup,
                    bytes));
        }

        ArrayList<FrameGraphPhysicalPlan.PhysicalAllocationPlan> physicalPlans = new ArrayList<>();
        long transientPhysicalBytes = 0L;
        for (MutablePhysical allocation : physical) {
            long bytes = allocation.descriptor.approximateByteSize();
            if (allocation.lifetime == FrameGraphResourceLifetime.TRANSIENT) transientPhysicalBytes = saturatedAdd(transientPhysicalBytes, bytes);
            physicalPlans.add(new FrameGraphPhysicalPlan.PhysicalAllocationPlan(
                    allocation.id, allocation.aliasGroup, allocation.lifetime, allocation.descriptor,
                    allocation.logicalResources, bytes));
        }

        long peakTransientBytes = peakTransientBytes(logical, physical);
        return new FrameGraphPhysicalPlan(logicalPlans, physicalPlans,
                new FrameGraphPhysicalPlan.MemoryEstimate(transientLogicalBytes, transientPhysicalBytes,
                        peakTransientBytes, persistentBytes));
    }

    private static long peakTransientBytes(Map<FrameGraphResourceKey, MutableLogical> logical,
                                           List<MutablePhysical> physical) {
        int maxPass = -1;
        for (MutableLogical state : logical.values()) maxPass = Math.max(maxPass, state.lastUse);
        long peak = 0L;
        for (int pass = 0; pass <= maxPass; pass++) {
            long active = 0L;
            for (MutablePhysical allocation : physical) {
                if (allocation.lifetime != FrameGraphResourceLifetime.TRANSIENT) continue;
                boolean used = false;
                for (FrameGraphResourceKey key : allocation.logicalResources) {
                    MutableLogical resource = logical.get(key);
                    if (resource != null && resource.firstUse <= pass && pass <= resource.lastUse) {
                        used = true;
                        break;
                    }
                }
                if (used) active = saturatedAdd(active, allocation.descriptor.approximateByteSize());
            }
            peak = Math.max(peak, active);
        }
        return peak;
    }

    private static long saturatedAdd(long a, long b) {
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }

    private static final class MutableLogical {
        final FrameGraphResourceKey key;
        final FrameGraphPhysicalResourceDescriptor descriptor;
        final int firstUse;
        int lastUse;
        int physicalId = -1;
        int aliasGroup = -1;

        MutableLogical(FrameGraphResourceKey key, FrameGraphPhysicalResourceDescriptor descriptor, int firstUse) {
            this.key = key;
            this.descriptor = descriptor;
            this.firstUse = firstUse;
            this.lastUse = firstUse;
        }
    }

    private static final class MutablePhysical {
        final int id;
        final int aliasGroup;
        final FrameGraphResourceLifetime lifetime;
        final FrameGraphPhysicalResourceDescriptor descriptor;
        final ArrayList<FrameGraphResourceKey> logicalResources = new ArrayList<>();
        int lastUse;

        MutablePhysical(int id, int aliasGroup, FrameGraphResourceLifetime lifetime,
                        FrameGraphPhysicalResourceDescriptor descriptor, int lastUse) {
            this.id = id;
            this.aliasGroup = aliasGroup;
            this.lifetime = lifetime;
            this.descriptor = descriptor;
            this.lastUse = lastUse;
        }
    }
}
