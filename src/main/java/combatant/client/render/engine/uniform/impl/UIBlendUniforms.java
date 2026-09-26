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
import combatant.client.render.engine.renderer.ui.blend.UiBackdropBlendSpec;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

/** Std140 payload for the reusable backdrop-blend compositor. */
public enum UIBlendUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final String UNIFORM_NAME = "Combatant - UI Blend UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 64;
    private static final Data DATA = new Data();

    public static GpuBufferSlice write(UiBackdropBlendSpec requested) {
        UiBackdropBlendSpec spec = requested != null ? requested : UiBackdropBlendSpec.NORMAL;
        DATA.params[0] = spec.mode().shaderId();
        DATA.params[1] = spec.strength();
        DATA.params[2] = spec.pivot();
        DATA.params[3] = spec.softness();
        unpackArgb(spec.tone0Argb(), DATA.tone0);
        unpackArgb(spec.tone1Argb(), DATA.tone1);
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static void unpackArgb(int argb, float[] out) {
        out[0] = ((argb >>> 16) & 0xFF) / 255.0f;
        out[1] = ((argb >>> 8) & 0xFF) / 255.0f;
        out[2] = (argb & 0xFF) / 255.0f;
        out[3] = ((argb >>> 24) & 0xFF) / 255.0f;
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] params = new float[4];
        private final float[] tone0 = new float[4];
        private final float[] tone1 = new float[4];

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(params[0]).putFloat(params[1]).putFloat(params[2]).putFloat(params[3])
                    .putFloat(tone0[0]).putFloat(tone0[1]).putFloat(tone0[2]).putFloat(tone0[3])
                    .putFloat(tone1[0]).putFloat(tone1[1]).putFloat(tone1[2]).putFloat(tone1[3]);
        }

        @Override
        public boolean equals(Object other) {
            return false;
        }
    }
}
