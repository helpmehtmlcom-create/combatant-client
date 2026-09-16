/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

import java.util.List;
import java.util.Objects;

/** Immutable logical-to-physical lifetime/alias plan produced by the graph compiler. */
public record FrameGraphPhysicalPlan(List<LogicalResourcePlan> logicalResources,
                                     List<PhysicalAllocationPlan> physicalAllocations,
                                     MemoryEstimate memoryEstimate) {
    public static final FrameGraphPhysicalPlan EMPTY = new FrameGraphPhysicalPlan(List.of(), List.of(), new MemoryEstimate(0, 0, 0, 0));

    public FrameGraphPhysicalPlan {
        logicalResources = logicalResources == null ? List.of() : List.copyOf(logicalResources);
        physicalAllocations = physicalAllocations == null ? List.of() : List.copyOf(physicalAllocations);
        memoryEstimate = memoryEstimate == null ? new MemoryEstimate(0, 0, 0, 0) : memoryEstimate;
    }

    public LogicalResourcePlan logical(FrameGraphResourceKey key) {
        for (LogicalResourcePlan resource : logicalResources) if (resource.resource().equals(key)) return resource;
        return null;
    }

    public PhysicalAllocationPlan physical(int allocationId) {
        for (PhysicalAllocationPlan allocation : physicalAllocations) if (allocation.allocationId() == allocationId) return allocation;
        return null;
    }

    public String debugDump() {
        StringBuilder out = new StringBuilder(512);
        out.append("FrameGraphPhysicalPlan transientLogical=").append(memoryEstimate.transientLogicalBytes())
                .append(" transientPhysical=").append(memoryEstimate.transientPhysicalBytes())
                .append(" peakTransient=").append(memoryEstimate.peakTransientBytes())
                .append(" persistent=").append(memoryEstimate.persistentBytes()).append('\n');
        for (LogicalResourcePlan resource : logicalResources) {
            out.append("  ").append(resource.resource().name())
                    .append(" kind=").append(resource.resource().kind())
                    .append(" lifetime=").append(resource.resource().lifetime())
                    .append(" first=").append(resource.firstUse())
                    .append(" last=").append(resource.lastUse())
                    .append(" physical=").append(resource.physicalAllocationId() < 0 ? "external" : resource.physicalAllocationId())
                    .append(" aliasGroup=").append(resource.aliasGroup() < 0 ? "-" : resource.aliasGroup())
                    .append(" bytes=").append(resource.approximateBytes());
            if (resource.descriptor() != null) out.append(" [").append(resource.descriptor().debugDescription()).append(']');
            out.append('\n');
        }
        if (!physicalAllocations.isEmpty()) {
            out.append("Physical allocations:\n");
            for (PhysicalAllocationPlan allocation : physicalAllocations) {
                out.append("  #").append(allocation.allocationId())
                        .append(" aliasGroup=").append(allocation.aliasGroup())
                        .append(" lifetime=").append(allocation.lifetime())
                        .append(" bytes=").append(allocation.approximateBytes())
                        .append(" logical=").append(allocation.logicalResources())
                        .append('\n');
            }
        }
        return out.toString();
    }

    public record LogicalResourcePlan(FrameGraphResourceKey resource,
                                      FrameGraphPhysicalResourceDescriptor descriptor,
                                      int firstUse,
                                      int lastUse,
                                      int physicalAllocationId,
                                      int aliasGroup,
                                      long approximateBytes) {
        public LogicalResourcePlan {
            Objects.requireNonNull(resource, "resource");
            if (firstUse < 0 || lastUse < firstUse) throw new IllegalArgumentException("Invalid logical lifetime");
            if (resource.lifetime() == FrameGraphResourceLifetime.EXTERNAL) {
                if (descriptor != null || physicalAllocationId >= 0 || aliasGroup >= 0) {
                    throw new IllegalArgumentException("External resource cannot have graph-owned physical allocation");
                }
            } else if (descriptor == null || physicalAllocationId < 0 || aliasGroup < 0) {
                throw new IllegalArgumentException("Owned resource requires physical allocation: " + resource.name());
            }
        }
    }

    public record PhysicalAllocationPlan(int allocationId,
                                         int aliasGroup,
                                         FrameGraphResourceLifetime lifetime,
                                         FrameGraphPhysicalResourceDescriptor descriptor,
                                         List<FrameGraphResourceKey> logicalResources,
                                         long approximateBytes) {
        public PhysicalAllocationPlan {
            if (allocationId < 0 || aliasGroup < 0) throw new IllegalArgumentException("allocationId/aliasGroup");
            Objects.requireNonNull(lifetime, "lifetime");
            Objects.requireNonNull(descriptor, "descriptor");
            logicalResources = logicalResources == null ? List.of() : List.copyOf(logicalResources);
            if (logicalResources.isEmpty()) throw new IllegalArgumentException("Physical allocation must serve at least one logical resource");
            if (lifetime == FrameGraphResourceLifetime.EXTERNAL) throw new IllegalArgumentException("External allocation cannot be graph-owned");
        }
    }

    public record MemoryEstimate(long transientLogicalBytes,
                                 long transientPhysicalBytes,
                                 long peakTransientBytes,
                                 long persistentBytes) {
    }
}
