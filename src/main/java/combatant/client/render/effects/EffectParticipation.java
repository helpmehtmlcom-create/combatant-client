/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects;

/**
 * Semantic ownership of a transient effect relative to the world/shaderpack scene.
 *
 * <p>This is deliberately independent from geometry shape ({@link EffectDomain}) and from any
 * concrete Iris/Photon framebuffer or shader program. Backends may degrade an unsupported mode
 * to a safe fallback, but the descriptor still preserves the intended scene participation.</p>
 */
public enum EffectParticipation {
    /** Readability/debug primitive rendered as a Combatant world overlay. */
    OVERLAY,
    /** Opaque/cutout geometry expected to receive scene lighting. */
    LIT_OPAQUE,
    /** Translucent geometry expected to participate in the scene forward path. */
    LIT_TRANSLUCENT,
    /** Additive/emissive world VFX intended to feed the shaderpack scene where supported. */
    EMISSIVE,
    /** World-space request that distorts/composites scene color rather than drawing ordinary color. */
    SCENE_DISTORTION,
    /** Camera/screen-space effect; never consumed by the ordinary world-effect renderer. */
    SCREEN_POST
}
