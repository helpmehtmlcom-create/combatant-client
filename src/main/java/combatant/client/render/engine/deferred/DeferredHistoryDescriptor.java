/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/**
 * Frame-local temporal history contract. Epoch changes whenever previous-frame data becomes
 * semantically incompatible with the current primary view.
 */
public record DeferredHistoryDescriptor(
        long epoch,
        long currentFrameId,
        long previousFrameId,
        boolean valid,
        DeferredHistoryResetReason resetReason
) {
    public DeferredHistoryDescriptor {
        if (resetReason == null) resetReason = DeferredHistoryResetReason.RENDERER_RESET;
        if (valid && resetReason != DeferredHistoryResetReason.NONE) {
            throw new IllegalArgumentException("Valid history cannot carry reset reason " + resetReason);
        }
    }
}
