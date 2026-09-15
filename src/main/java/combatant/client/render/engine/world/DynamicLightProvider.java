/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** Producer API for exact analytic lights. The renderer never infers these lights from pixels. */
@FunctionalInterface
public interface DynamicLightProvider {
    void collect(Context context, Consumer<LightDescriptor> output);

    record Context(ClientLevel level,
                   WorldRenderState worldState,
                   Vec3 cameraPosition,
                   long frameId) {
        public Context {
            worldState = worldState == null ? WorldRenderState.unknown(0L) : worldState;
            cameraPosition = cameraPosition == null ? Vec3.ZERO : cameraPosition;
        }
    }
}
