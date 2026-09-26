/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.spatial;

@FunctionalInterface
public interface SceneSpatialVisitor {
    /** @return false to stop traversal early. */
    boolean visit(SceneSpatialRecord record);
}
