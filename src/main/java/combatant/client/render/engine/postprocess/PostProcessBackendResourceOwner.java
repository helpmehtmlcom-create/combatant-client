/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.postprocess;

import combatant.client.render.engine.rhi.CombatantRhi;

/** Optional hook for legacy/module passes that own native backend resources. */
public interface PostProcessBackendResourceOwner {
    void releaseBackendResources(CombatantRhi owner);
}
