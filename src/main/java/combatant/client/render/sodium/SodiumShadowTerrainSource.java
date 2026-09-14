/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import combatant.client.render.engine.deferred.DeferredSecondaryView;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;

/** @deprecated use {@link SodiumSecondaryTerrainSource}; kept as a source-compatible facade. */
@Deprecated(forRemoval = false)
public final class SodiumShadowTerrainSource {
    private SodiumShadowTerrainSource() {
    }

    public static SortedRenderLists buildRenderLists(RenderSectionManager manager, DeferredSecondaryView view) {
        return SodiumSecondaryTerrainSource.buildRenderLists(manager, view);
    }

    public static void clearShadowBatches(SortedRenderLists lists) {
        SodiumSecondaryTerrainSource.clearCachedBatches(lists);
    }
}
