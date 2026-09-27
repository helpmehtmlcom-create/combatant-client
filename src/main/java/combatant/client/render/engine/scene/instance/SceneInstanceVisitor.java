/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.instance;

@FunctionalInterface
public interface SceneInstanceVisitor {
    /** @return false to stop traversal. */
    boolean visit(SceneAssetInstance<?> instance, SceneInstanceViewState state);
}
