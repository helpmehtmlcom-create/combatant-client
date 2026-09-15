/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess;

public interface PostProcessPass {
    boolean isActive();

    boolean render(PostProcessExecutionContext execution);

    /**
     * Whether this pass can profit from a storage-capable graph destination on the active backend.
     * This is a preference, not a requirement: graph allocation or compute failure must fall back to raster.
     */
    default boolean prefersStorageOutput(combatant.client.render.engine.rhi.CombatantRhi rhi) {
        return false;
    }

    default int getPriority() {
        return 0;
    }

    default Phase getPhase() {
        return Phase.PRE_HAND;
    }

    enum Phase {PRE_HAND, POST_HAND}
}
