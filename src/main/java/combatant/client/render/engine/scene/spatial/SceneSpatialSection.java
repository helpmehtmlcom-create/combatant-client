/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.spatial;

import net.minecraft.world.phys.AABB;

/** Read-only occupied section cell exposed by the generic spatial registry. */
public interface SceneSpatialSection {
    long key();
    int sectionX();
    int sectionY();
    int sectionZ();
    AABB bounds();
    int recordCount();
    boolean visitRecords(SceneSpatialVisitor visitor);
}
