/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/** Typed reason why temporal consumers must reject previous-frame history. */
public enum DeferredHistoryResetReason {
    NONE,
    FIRST_FRAME,
    WORLD_CHANGE,
    FRAME_GAP,
    CAMERA_CUT,
    TELEPORT,
    RESIZE,
    POLICY_CHANGE,
    BACKEND_CHANGE,
    RENDERER_RESET
}
