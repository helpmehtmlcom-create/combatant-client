/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.WorldRenderState;
import net.minecraft.resources.Identifier;

/** Declarative profile selected before graph construction from a stable dimension key. */
public record DimensionRenderProfile(
        Identifier id,
        Identifier environmentModel,
        Identifier skyProvider,
        Identifier celestialModel,
        Identifier mediumProfile,
        Identifier weatherProvider,
        Identifier ambientPalette,
        Identifier exposureProfile,
        Identifier postProfile
) {
    public DimensionRenderProfile {
        if (id == null) id = WorldRenderState.UNKNOWN_PROFILE;
        if (environmentModel == null) environmentModel = WorldRenderState.NONE;
        if (skyProvider == null) skyProvider = WorldRenderState.NONE;
        if (celestialModel == null) celestialModel = WorldRenderState.NONE;
        if (mediumProfile == null) mediumProfile = WorldRenderState.NEUTRAL;
        if (weatherProvider == null) weatherProvider = WorldRenderState.NONE;
        if (ambientPalette == null) ambientPalette = WorldRenderState.NEUTRAL;
        if (exposureProfile == null) exposureProfile = WorldRenderState.NEUTRAL;
        if (postProfile == null) postProfile = WorldRenderState.NEUTRAL;
    }
}
