/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.core.RenderPhase;

/** Stable world-frame insertion points. Ordering is part of the renderer contract. */
public enum DeferredStage {
    FRAME_SETUP(RenderPhase.WORLD_BEFORE_ENTITIES),
    SHADOW_MAP(RenderPhase.WORLD_BEFORE_ENTITIES),
    PRE_GEOMETRY_COMPUTE(RenderPhase.WORLD_BEFORE_ENTITIES),
    OPAQUE_GEOMETRY(RenderPhase.WORLD_BEFORE_ENTITIES),
    CUTOUT_GEOMETRY(RenderPhase.WORLD_BEFORE_ENTITIES),
    POST_GEOMETRY_COMPUTE(RenderPhase.WORLD_BEFORE_ENTITIES),
    DEPTH_RESOLVE(RenderPhase.WORLD_BEFORE_ENTITIES),
    DEPTH_PYRAMID(RenderPhase.WORLD_BEFORE_ENTITIES),
    AMBIENT_OCCLUSION(RenderPhase.WORLD_BEFORE_ENTITIES),
    PRE_LIGHTING(RenderPhase.WORLD_BEFORE_ENTITIES),
    LIGHTING(RenderPhase.WORLD_BEFORE_ENTITIES),
    POST_LIGHTING(RenderPhase.WORLD_BEFORE_ENTITIES),
    REFLECTION_TRACE(RenderPhase.WORLD_BEFORE_TRANSLUCENT),
    REFLECTION_DENOISE(RenderPhase.WORLD_BEFORE_TRANSLUCENT),
    REFLECTION_COMPOSITE(RenderPhase.WORLD_BEFORE_TRANSLUCENT),
    FORWARD_OPAQUE(RenderPhase.WORLD_BEFORE_TRANSLUCENT),
    PRE_TRANSLUCENCY(RenderPhase.WORLD_BEFORE_TRANSLUCENT),
    TRANSLUCENCY(RenderPhase.WORLD_BEFORE_TRANSLUCENT),
    POST_TRANSLUCENCY(RenderPhase.WORLD_AFTER_TRANSLUCENT),
    TEMPORAL_RESOLVE(RenderPhase.WORLD_AFTER_TRANSLUCENT),
    PRE_POST_PROCESS(RenderPhase.WORLD_POST_PRE_HAND),
    POST_PROCESS(RenderPhase.WORLD_POST_PRE_HAND),
    FINAL_COMPOSITE(RenderPhase.WORLD_POST_HAND);

    private final RenderPhase renderPhase;

    DeferredStage(RenderPhase renderPhase) {
        this.renderPhase = renderPhase;
    }

    public RenderPhase renderPhase() {
        return renderPhase;
    }
}
