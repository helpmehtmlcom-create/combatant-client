/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.framegraph.FrameGraphAccess;
import combatant.client.render.engine.framegraph.FrameGraphPassContract;
import combatant.client.render.engine.framegraph.FrameGraphResourceUse;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Immutable pass declaration used by the world graph before any GPU work is submitted. */
public record DeferredPassSpec(
        String id,
        DeferredStage stage,
        int priority,
        List<FrameGraphResourceUse> resources,
        Set<RhiShaderStage> requiredShaderStages,
        boolean externallyDriven,
        DeferredPassCondition condition,
        DeferredPassExecutor executor
) {
    public DeferredPassSpec {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Deferred pass id must not be blank");
        id = id.trim();
        if (stage == null) throw new IllegalArgumentException("stage");
        resources = resources == null ? List.of() : List.copyOf(resources);
        requiredShaderStages = requiredShaderStages == null || requiredShaderStages.isEmpty()
                ? Set.of()
                : Set.copyOf(requiredShaderStages);
        condition = condition == null ? DeferredPassCondition.ALWAYS : condition;
        if (!externallyDriven && executor == null) {
            throw new IllegalArgumentException("Executable deferred pass requires an executor: " + id);
        }
    }

    public static Builder builder(String id, DeferredStage stage) {
        return new Builder(id, stage);
    }

    public FrameGraphPassContract contract() {
        return new FrameGraphPassContract(stage.renderPhase(), id, resources, externallyDriven);
    }

    public static final class Builder {
        private final String id;
        private final DeferredStage stage;
        private final EnumMap<DeferredResource, FrameGraphAccess> resources = new EnumMap<>(DeferredResource.class);
        private final EnumSet<RhiShaderStage> requiredStages = EnumSet.noneOf(RhiShaderStage.class);
        private int priority;
        private boolean externallyDriven;
        private DeferredPassCondition condition = DeferredPassCondition.ALWAYS;
        private DeferredPassExecutor executor;

        private Builder(String id, DeferredStage stage) {
            this.id = id;
            this.stage = stage;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder read(DeferredResource... values) {
            return use(FrameGraphAccess.READ, values);
        }

        public Builder write(DeferredResource... values) {
            return use(FrameGraphAccess.WRITE, values);
        }

        public Builder readWrite(DeferredResource... values) {
            return use(FrameGraphAccess.READ_WRITE, values);
        }

        public Builder requires(RhiShaderStage... stages) {
            if (stages != null) {
                for (RhiShaderStage shaderStage : stages) if (shaderStage != null) requiredStages.add(shaderStage);
            }
            return this;
        }

        public Builder external() {
            externallyDriven = true;
            executor = null;
            return this;
        }

        /** Evaluated before implicit allocation so disabled optional branches stay physically lazy. */
        public Builder when(DeferredPassCondition condition) {
            this.condition = condition == null ? DeferredPassCondition.ALWAYS : condition;
            return this;
        }

        public Builder execute(DeferredPassExecutor executor) {
            this.executor = executor;
            this.externallyDriven = false;
            return this;
        }

        public DeferredPassSpec build() {
            ArrayList<FrameGraphResourceUse> uses = new ArrayList<>(resources.size());
            for (DeferredResource resource : DeferredResource.values()) {
                FrameGraphAccess access = resources.get(resource);
                if (access != null) uses.add(new FrameGraphResourceUse(resource.key(), access));
            }
            return new DeferredPassSpec(
                    id, stage, priority, uses, requiredStages, externallyDriven, condition, executor
            );
        }

        private Builder use(FrameGraphAccess access, DeferredResource... values) {
            if (values == null) return this;
            for (DeferredResource resource : values) {
                if (resource == null) continue;
                resources.merge(resource, access, Builder::mergeAccess);
            }
            return this;
        }

        private static FrameGraphAccess mergeAccess(FrameGraphAccess left, FrameGraphAccess right) {
            if (left == right) return left;
            return FrameGraphAccess.READ_WRITE;
        }
    }
}
