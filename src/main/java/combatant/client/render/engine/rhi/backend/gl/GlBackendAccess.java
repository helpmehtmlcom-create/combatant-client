/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.gl;

import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import combatant.client.mixininterface.IGlBackendInfo;

public enum GlBackendAccess {
    ;

    public static @Nullable IGlBackendInfo current() {
        GpuDevice device = RenderSystem.tryGetDevice();
        return device instanceof IGlBackendInfo glBackend ? glBackend : null;
    }
}
