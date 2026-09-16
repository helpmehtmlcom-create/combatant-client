/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

/**
 * Combatant-only view over Sodium's section storage.
 *
 * <p>The renderer depends on this interface rather than on the concrete private storage layout;
 * version-specific field access stays isolated in the Sodium mixin adapter.</p>
 */
public interface SodiumSectionStorageView {
    Iterable<RenderSection> combatant$sections();

    /**
     * Monotonic topology revision. It changes only when the set of section objects changes, not
     * for ordinary mesh rebuilds, so immutable secondary-cull snapshots can be retained cheaply.
     */
    long combatant$structuralRevision();
}
