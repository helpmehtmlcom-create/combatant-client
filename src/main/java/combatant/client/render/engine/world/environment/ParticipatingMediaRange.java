/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.WorldRenderState;

/** Shared distance contract for atmosphere LUTs and view-aligned participating-media consumers. */
public final class ParticipatingMediaRange {
    private static final float MINIMUM_BLOCKS = 64.0f;

    private ParticipatingMediaRange() {
    }

    public static float resolveBlocks(WorldRenderState worldState, float viewFarPlane) {
        float distance = Float.isFinite(viewFarPlane) && viewFarPlane > 0.0f
                ? Math.max(MINIMUM_BLOCKS, viewFarPlane)
                : MINIMUM_BLOCKS;
        if (worldState == null) return distance;

        CloudProfile cloud = CloudProfileRegistry.resolve(worldState.cloudProfile());
        if (cloud.valid()) distance = Math.max(distance, cloud.maxRayDistanceBlocks());

        ParticipatingMediumProfile medium = ParticipatingMediumProfileRegistry.resolve(worldState.mediumProfile());
        if (medium.valid()) distance = Math.max(distance, medium.maxDistanceBlocks());
        return distance;
    }
}
