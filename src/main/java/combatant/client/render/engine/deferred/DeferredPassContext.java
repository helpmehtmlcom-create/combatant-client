/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.AdvancedShaderBackend;

public record DeferredPassContext(
        DeferredStage stage,
        RenderFrameContext frame,
        CombatantRhi rhi,
        DeferredResourceBindings resources,
        DeferredSecondaryViewRegistry secondaryViews,
        DeferredPrimaryViewSource primaryView,
        DeferredRuntimeConfig.Snapshot settings
) {
    public DeferredPassContext {
        if (settings == null) settings = DeferredRuntimeConfig.current();
    }

    public AdvancedShaderBackend advancedShaders() {
        return rhi.advancedShaders();
    }

    public DeferredHistoryDescriptor history() {
        return primaryView.historyDescriptor();
    }

    /** Persistent history is false here until a successful producer has initialized it. */
    public boolean isValid(DeferredResource resource) {
        return resources.isValid(resource);
    }
}
