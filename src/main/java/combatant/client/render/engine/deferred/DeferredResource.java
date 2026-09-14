/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import combatant.client.render.engine.framegraph.FrameGraphResourceKey;

import static combatant.client.render.engine.deferred.DeferredTextureSpec.Resolution.FULL;
import static combatant.client.render.engine.deferred.DeferredTextureSpec.Resolution.HALF;
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
            DeferredTextureSpec.attachment(GpuFormat.RG16_FLOAT, MATCH_SCENE)),
    RESOLVED_DEPTH(FrameGraphResourceKey.transientTexture("world.depth.resolved"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R32_FLOAT, FULL)),
    DEPTH_PYRAMID(FrameGraphResourceKey.transientTexture("world.depth.pyramid"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, FULL, true)),
    SHADOW_DEPTH(FrameGraphResourceKey.transientTexture("world.shadow.depth"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R32_FLOAT, FULL)),
    SHADOW_COLOR(FrameGraphResourceKey.transientTexture("world.shadow.color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA8_UNORM, FULL)),
    AMBIENT_OCCLUSION(FrameGraphResourceKey.transientTexture("world.ambient_occlusion"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, HALF, false)),
    INDIRECT_LIGHT(FrameGraphResourceKey.transientTexture("world.indirect_light"),
            DeferredTextureSpec.compute(GpuFormat.RG11B10_FLOAT, HALF, false)),
    LIGHTING_COLOR(FrameGraphResourceKey.transientTexture("world.lighting_color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RG11B10_FLOAT, FULL)),
    REFLECTION_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, HALF, false)),
    REFLECTION_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, HALF, false)),
    TRANSLUCENT_COLOR(FrameGraphResourceKey.transientTexture("world.translucent.color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, FULL)),
    TRANSLUCENT_DEPTH(FrameGraphResourceKey.transientTexture("world.translucent.depth"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R32_FLOAT, FULL)),
    HISTORY_COLOR(FrameGraphResourceKey.persistentTexture("world.history.color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, FULL)),
    HISTORY_DEPTH(FrameGraphResourceKey.persistentTexture("world.history.depth"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R32_FLOAT, FULL)),
    HISTORY_REFLECTION(FrameGraphResourceKey.persistentTexture("world.history.reflection"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, HALF, false)),
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
