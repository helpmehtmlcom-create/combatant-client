/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.visibility;

/**
 * Explicit culling cost tiers. SECTION_ONLY intentionally does not touch FOV/projection math.
 */
public enum SceneVisibilityMode {
    NONE(false, false),
    SECTION_ONLY(true, false),
    FRUSTUM_ONLY(false, true),
    SECTION_AND_FRUSTUM(true, true);

    private final boolean sectionVisibility;
    private final boolean frustum;

    SceneVisibilityMode(boolean sectionVisibility, boolean frustum) {
        this.sectionVisibility = sectionVisibility;
        this.frustum = frustum;
    }

    public boolean sectionVisibility() { return sectionVisibility; }
    public boolean frustum() { return frustum; }
}
