/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.screen;

import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import net.minecraft.client.gui.screens.Screen;

/**
 * Marker for full-window Combatant screens that paint their own backdrop. While one is open the client HUD
 * (module widgets, dynamic island, scoreboard, ...) is not drawn behind it, so it cannot bleed through the glass.
 */
public interface HudHidingScreen {

    /** True when {@code screen} is a {@link HudHidingScreen} and the HUD editor is not forcing widgets visible. */
    static boolean hidesHud(Screen screen) {
        return screen instanceof HudHidingScreen && !DraggableHudElementRegistry.isForceVisible();
    }
}
