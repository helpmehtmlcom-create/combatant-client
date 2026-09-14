/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess.graph;

public enum PostProcessResource {
    MAIN_COLOR,
    MAIN_DEPTH,
    PRE_TRANSLUCENT_DEPTH,
    GBUFFER_SURFACE,
    GBUFFER_GEOMETRY,
    GBUFFER_AUXILIARY,
    VELOCITY,
    RESOLVED_DEPTH,
    DEPTH_PYRAMID,
    SHADOW_DEPTH,
    SHADOW_COLOR,
    AMBIENT_OCCLUSION,
    INDIRECT_LIGHT,
    LIGHTING_COLOR,
    REFLECTION_COLOR,
    REFLECTION_CONFIDENCE,
    TRANSLUCENT_COLOR,
    TRANSLUCENT_DEPTH,
    HISTORY_COLOR,
    HISTORY_DEPTH,
    HISTORY_REFLECTION,
    GRAPH_SOURCE_COLOR,
    GRAPH_DEST_COLOR,
    TEMP_COLOR,
    CUSTOM
}
