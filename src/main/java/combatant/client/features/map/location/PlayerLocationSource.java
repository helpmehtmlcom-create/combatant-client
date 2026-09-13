/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.location;

public enum PlayerLocationSource {
    LOCAL_ENTITY_EXACT(100, true),
    MAPLINK_EXACT(95, true),
    LOCATOR_EXACT(90, true),
    DUPLEX_TRIANGULATED(80, false),
    TRIANGULATED(65, false),
    LOCATOR_BEARING(25, false),
    LOCATOR_APPROXIMATE(85, false),
    HISTORICAL(5, false);

    private final int priority;
    private final boolean exact;

    PlayerLocationSource(int priority, boolean exact) {
        this.priority = priority;
        this.exact = exact;
    }

    public int priority() { return priority; }
    public boolean exact() { return exact; }
}
