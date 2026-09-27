/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EntityCullingCompatTest {

    @Test
    void forceVisibleHandlesNullWithoutThrowing() {
        assertDoesNotThrow(() -> EntityCullingCompat.forceVisibleForShaderEsp(null));
    }
}
