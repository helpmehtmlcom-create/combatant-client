/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.uniform.impl;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.deferred.DeferredWorldPipeline;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

/** Per-frame state required to reproduce the producer's neutral lightmap/fog lighting. */
public enum DeferredLightingUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .get();

    private static final String STREAM = "Combatant - Deferred Lighting UBO";
    private static final Data DATA = new Data();

    public static void update(DeferredWorldPipeline.LightingState state) {
        DATA.state = state;
        CombatantRenderSystem.uniforms().write(STREAM, SIZE, 1, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(STREAM);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private DeferredWorldPipeline.LightingState state;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(state.fogRed())
                    .putFloat(state.fogGreen())
                    .putFloat(state.fogBlue())
                    .putFloat(state.fogAlpha())
                    .putFloat(state.environmentalFogStart())
                    .putFloat(state.environmentalFogEnd())
                    .putFloat(state.renderFogStart())
                    .putFloat(state.renderFogEnd());
        }

        @Override
        public boolean equals(Object other) {
            return false;
        }
    }
}
