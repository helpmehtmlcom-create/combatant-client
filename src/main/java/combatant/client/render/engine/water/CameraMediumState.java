/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.water;

import combatant.client.render.engine.RenderState;
import net.minecraft.world.level.material.FogType;

/** Explicit camera-medium contract captured by the camera/world producer. */
public record CameraMediumState(Medium medium) {
    public static CameraMediumState capture() {
        FogType type = RenderState.cameraSubmersion;
        if (type == FogType.WATER) return new CameraMediumState(Medium.WATER);
        if (type == FogType.LAVA) return new CameraMediumState(Medium.LAVA);
        if (type == FogType.POWDER_SNOW) return new CameraMediumState(Medium.POWDER_SNOW);
        return new CameraMediumState(Medium.AIR);
    }

    public boolean insideWater() {
        return medium == Medium.WATER;
    }

    /** Boundary direction for a camera ray crossing the extracted upper water surface. */
    public WaterBoundary waterBoundary() {
        return insideWater() ? WaterBoundary.EXITING_WATER : WaterBoundary.ENTERING_WATER;
    }

    public enum Medium {
        AIR(0), WATER(1), LAVA(2), POWDER_SNOW(3), UNKNOWN(4);

        private final int gpuCode;

        Medium(int gpuCode) {
            this.gpuCode = gpuCode;
        }

        public int gpuCode() {
            return gpuCode;
        }
    }

    public enum WaterBoundary {
        ENTERING_WATER,
        EXITING_WATER
    }
}
