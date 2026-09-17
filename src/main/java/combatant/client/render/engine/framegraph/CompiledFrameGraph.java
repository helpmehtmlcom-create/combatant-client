/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

import java.util.List;
import java.util.Objects;

/** Immutable resource-dependency and physical-lifetime result produced before pass execution. */
public record CompiledFrameGraph(List<FrameGraphPassContract> orderedPasses,
                                 List<Dependency> dependencies,
                                 FrameGraphPhysicalPlan physicalPlan) {
    public CompiledFrameGraph {
        orderedPasses = orderedPasses == null ? List.of() : List.copyOf(orderedPasses);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        physicalPlan = physicalPlan == null ? FrameGraphPhysicalPlan.EMPTY : physicalPlan;
    }

    public CompiledFrameGraph(List<FrameGraphPassContract> orderedPasses,
                              List<Dependency> dependencies) {
        this(orderedPasses, dependencies, FrameGraphPhysicalPlan.EMPTY);
    }

    public String debugDump() {
        StringBuilder out = new StringBuilder(physicalPlan.debugDump());
        out.append("Dependencies:\n");
        for (Dependency dependency : dependencies) {
            out.append("  ").append(dependency.producerIndex()).append(" -> ")
                    .append(dependency.consumerIndex()).append(' ')
                    .append(dependency.resource().name()).append(' ')
                    .append(dependency.hazard()).append('\n');
        }
        return out.toString();
    }

    public record Dependency(int producerIndex,
                             int consumerIndex,
                             FrameGraphResourceKey resource,
                             Hazard hazard) {
        public Dependency {
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(hazard, "hazard");
            if (producerIndex < 0 || consumerIndex < 0 || producerIndex >= consumerIndex) {
                throw new IllegalArgumentException("Frame-graph dependency must point from an earlier pass to a later pass");
            }
        }
    }

    public enum Hazard {
        READ_AFTER_WRITE,
        WRITE_AFTER_READ,
        WRITE_AFTER_WRITE
    }
}
