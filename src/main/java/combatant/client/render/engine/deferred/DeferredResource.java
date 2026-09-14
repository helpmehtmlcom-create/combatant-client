/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import combatant.client.render.engine.framegraph.FrameGraphResourceKey;

import static combatant.client.render.engine.deferred.DeferredTextureSpec.SamplePolicy.MATCH_SCENE;

/** Logical world resources. Physical images/buffers are allocated separately by the RHI. */
public enum DeferredResource {
    SCENE_COLOR(FrameGraphResourceKey.external("world.scene_color"), null),
    MAIN_DEPTH(FrameGraphResourceKey.external("world.main_depth"), null),
    GBUFFER_SURFACE(FrameGraphResourceKey.transientTexture("world.gbuffer.surface"),
            DeferredTextureSpec.attachment(GpuFormat.RGBA8_UNORM, MATCH_SCENE)),
    GBUFFER_GEOMETRY(FrameGraphResourceKey.transientTexture("world.gbuffer.geometry"),
            DeferredTextureSpec.attachment(GpuFormat.RGBA8_UNORM, MATCH_SCENE)),
    GBUFFER_AUXILIARY(FrameGraphResourceKey.transientTexture("world.gbuffer.auxiliary"),
            DeferredTextureSpec.attachment(GpuFormat.RGBA8_UNORM, MATCH_SCENE)),
    VELOCITY(FrameGraphResourceKey.transientTexture("world.velocity"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RG16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    RESOLVED_DEPTH(FrameGraphResourceKey.transientTexture("world.depth.resolved"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    DEPTH_PYRAMID(FrameGraphResourceKey.transientTexture("world.depth.pyramid"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, true)),
    SHADOW_DEPTH(FrameGraphResourceKey.transientTexture("world.shadow.depth"), null),
    SHADOW_CASCADE_DATA(FrameGraphResourceKey.transientBuffer("world.shadow.cascades"), null),
    SHADOW_CASCADE_VISIBILITY(FrameGraphResourceKey.transientTexture("world.shadow.cascade_visibility"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.SHADOW_OUTPUT, false)),
    CONTACT_SHADOW(FrameGraphResourceKey.transientTexture("world.shadow.contact"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.CONTACT_SHADOW_TRACE, false)),
    SHADOW_COLOR(FrameGraphResourceKey.transientTexture("world.shadow.color"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.SHADOW_OUTPUT, false)),
    AMBIENT_OCCLUSION(FrameGraphResourceKey.transientTexture("world.ambient_occlusion"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    SCENE_RADIANCE(FrameGraphResourceKey.transientTexture("world.scene_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    INDIRECT_TRACE_DATA(FrameGraphResourceKey.transientBuffer("world.indirect_trace_data"), null),
    INDIRECT_TRACE_LIGHT(FrameGraphResourceKey.transientTexture("world.indirect.trace_light"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    INDIRECT_TRACE_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.indirect.trace_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    INDIRECT_LIGHT(FrameGraphResourceKey.transientTexture("world.indirect_light"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    INDIRECT_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.indirect_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    LIGHTING_COLOR(FrameGraphResourceKey.transientTexture("world.lighting_color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    REFLECTION_CASCADE_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.cascade_color"), null),
    REFLECTION_CASCADE_DEPTH(FrameGraphResourceKey.transientTexture("world.reflection.cascade_depth"), null),
    REFLECTION_CASCADE_DATA(FrameGraphResourceKey.transientBuffer("world.reflection.cascade_data"), null),
    REFLECTION_TRACE_DATA(FrameGraphResourceKey.transientBuffer("world.reflection.trace_data"), null),
    REFLECTION_TRACE_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.trace_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_TRACE, false)),
    REFLECTION_TRACE_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.trace_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_TRACE, false)),
    REFLECTION_RESOLVED_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.resolved_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_RESOLVED_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.resolved_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    TRANSLUCENT_COLOR(FrameGraphResourceKey.transientTexture("world.translucent.color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    TRANSLUCENT_DEPTH(FrameGraphResourceKey.transientTexture("world.translucent.depth"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    HISTORY_COLOR(FrameGraphResourceKey.persistentTexture("world.history.color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    HISTORY_DEPTH(FrameGraphResourceKey.persistentTexture("world.history.depth"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    HISTORY_REFLECTION(FrameGraphResourceKey.persistentTexture("world.history.reflection"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_HISTORY, false)),
    HISTORY_REFLECTION_CONFIDENCE(FrameGraphResourceKey.persistentTexture("world.history.reflection_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_HISTORY, false)),
    HISTORY_INDIRECT(FrameGraphResourceKey.persistentTexture("world.history.indirect"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    HISTORY_INDIRECT_CONFIDENCE(FrameGraphResourceKey.persistentTexture("world.history.indirect_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    LIGHT_LIST(FrameGraphResourceKey.transientBuffer("world.light_list"), null),
    INDIRECT_DRAWS(FrameGraphResourceKey.transientBuffer("world.indirect_draws"), null),
    EXPOSURE(FrameGraphResourceKey.persistentBuffer("world.exposure"), null);

    private final FrameGraphResourceKey key;
    private final DeferredTextureSpec textureSpec;

    DeferredResource(FrameGraphResourceKey key, DeferredTextureSpec textureSpec) {
        this.key = key;
        this.textureSpec = textureSpec;
    }

    public FrameGraphResourceKey key() {
        return key;
    }

    public DeferredTextureSpec textureSpec() {
        return textureSpec;
    }
}
