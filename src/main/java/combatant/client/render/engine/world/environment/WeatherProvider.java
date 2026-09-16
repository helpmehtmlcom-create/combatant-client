/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Producer contract for dimension weather. */
public interface WeatherProvider {
    Identifier id();

    WeatherState capture(ClientLevel level,
                         Vec3 cameraPosition,
                         float partialTick,
                         BiomeClimateState climate,
                         long frameId);

    default void reset() {
    }
}
