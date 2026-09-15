/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import combatant.client.render.engine.deferred.DeferredPrimaryViewSource;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import net.minecraft.resources.Identifier;

/**
 * Explicit producer of HDR lat-long sky radiance. Implementations own sky generation policy;
 * deferred BRDF/reflections only consume the resulting resources.
 */
public interface SkyEnvironmentProvider {
    Identifier id();

    SkyEnvironmentDescriptor describe(WorldRenderState worldState, long frameId);

    void render(RenderContext context, RhiStorageImage target);

    record RenderContext(CombatantRhi rhi,
                         WorldRenderState worldState,
                         DeferredPrimaryViewSource.FrameView view,
                         long frameId) {
    }
}
