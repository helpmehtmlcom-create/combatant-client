/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import net.minecraft.world.phys.AABB;

/**
 * Version-isolated primary-camera visibility view exposed by the Sodium compat mixin.
 * Consumers never receive Sodium implementation types.
 */
public interface SodiumWorldVisibilityView {
    boolean combatant$isBoxVisible(AABB box);
}
