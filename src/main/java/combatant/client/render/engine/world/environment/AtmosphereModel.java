/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import net.minecraft.resources.Identifier;

/** Registered producer for one explicit atmosphere model selected by a dimension profile. */
public interface AtmosphereModel {
    Identifier id();

    AtmosphereState capture(EnvironmentCaptureContext context);

    default DirectionalLightDescriptor attenuateDirectLight(
            AtmosphereState atmosphere,
            DirectionalLightDescriptor light,
            double observerAltitudeBlocks
    ) {
        return light;
    }

    default void reset() {
    }
}
