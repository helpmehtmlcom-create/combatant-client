/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

/** Stable rendering/temporal groups for spatial cloud domains. */
public enum CloudDomainGroup {
    LOW_MID(0),
    HIGH(1),
    CONVECTIVE(2);

    private final int gpuCode;

    CloudDomainGroup(int gpuCode) {
        this.gpuCode = gpuCode;
    }

    public int gpuCode() {
        return gpuCode;
    }
}
