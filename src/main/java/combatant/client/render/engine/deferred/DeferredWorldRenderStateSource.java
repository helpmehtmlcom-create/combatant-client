/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import combatant.client.render.engine.world.WorldRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/** Builds one explicit renderer-world contract before graph consumers execute. */
final class DeferredWorldRenderStateSource {
    private static final float SUN_ANGULAR_RADIUS_RADIANS = 0.00465f;

    private static final Identifier PROFILE_OVERWORLD = id("overworld");
    private static final Identifier PROFILE_NETHER = id("nether");
    private static final Identifier PROFILE_END = id("end");
    private static final Identifier PROFILE_UNKNOWN = id("unknown");
    private static final Identifier CELESTIAL_VANILLA_SUN = id("vanilla_sun");

    private Object worldOwner;
    private long epoch;
    private WorldRenderState current = WorldRenderState.unknown(0L);

    WorldRenderState capture(ClientLevel level, DeferredPrimaryViewSource.FrameView view) {
        if (worldOwner != level) {
            worldOwner = level;
            epoch++;
        }
        if (level == null) {
            current = WorldRenderState.unknown(epoch);
            return current;
        }

        Identifier dimensionKey = level.dimension().identifier();
        Identifier profile = explicitProfile(level);
        boolean overworld = Level.OVERWORLD.equals(level.dimension());
        DirectionalLightDescriptor directional = overworld ? unitSun(view) : DirectionalLightDescriptor.NONE;

        current = new WorldRenderState(
                dimensionKey,
                profile,
                WorldRenderState.NONE,
                WorldRenderState.NONE,
                overworld ? CELESTIAL_VANILLA_SUN : WorldRenderState.NONE,
                WorldRenderState.NEUTRAL,
                WorldRenderState.NONE,
                WorldRenderState.NEUTRAL,
                WorldRenderState.NEUTRAL,
                WorldRenderState.NEUTRAL,
                WorldRenderState.NONE,
                directional,
                epoch
        );
        return current;
    }

    WorldRenderState current() {
        return current;
    }

    void reset() {
        worldOwner = null;
        epoch++;
        current = WorldRenderState.unknown(epoch);
    }

    private static DirectionalLightDescriptor unitSun(DeferredPrimaryViewSource.FrameView view) {
        if (view == null || !view.hasSunAngle()) return DirectionalLightDescriptor.NONE;
        float x = (float) -Math.sin(view.sunAngle());
        float y = (float) Math.cos(view.sunAngle());
        if (!(y > 0.0f)) return DirectionalLightDescriptor.NONE;
        return DirectionalLightDescriptor.of(
                x, y, 0.0f,
                1.0f, 1.0f, 1.0f,
                SUN_ANGULAR_RADIUS_RADIANS,
                true
        );
    }

    private static Identifier explicitProfile(ClientLevel level) {
        if (Level.OVERWORLD.equals(level.dimension())) return PROFILE_OVERWORLD;
        if (Level.NETHER.equals(level.dimension())) return PROFILE_NETHER;
        if (Level.END.equals(level.dimension())) return PROFILE_END;
        return PROFILE_UNKNOWN;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
