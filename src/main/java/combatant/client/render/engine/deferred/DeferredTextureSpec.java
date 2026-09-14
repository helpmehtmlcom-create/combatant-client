/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;

/** Default physical requirements for a lazily allocated world-graph texture. */
public record DeferredTextureSpec(
        GpuFormat format,
        Resolution resolution,
        SamplePolicy samples,
        boolean mipChain,
        boolean storageImage,
        boolean renderAttachment
) {
    public enum Resolution {
        FULL(1),
        HALF(2),
        QUARTER(4);

        private final int divisor;

        Resolution(int divisor) {
            this.divisor = divisor;
        }

        public int width(int fullWidth) {
            return Math.max(1, (Math.max(1, fullWidth) + divisor - 1) / divisor);
        }

        public int height(int fullHeight) {
            return Math.max(1, (Math.max(1, fullHeight) + divisor - 1) / divisor);
        }
    }

    public enum SamplePolicy {
        MATCH_SCENE,
        SINGLE_SAMPLE
    }

    public DeferredTextureSpec {
        if (format == null) throw new IllegalArgumentException("format");
        if (resolution == null) resolution = Resolution.FULL;
        if (samples == null) samples = SamplePolicy.SINGLE_SAMPLE;
        if (storageImage && !format.hasColorAspect()) {
            throw new IllegalArgumentException("Storage image requires a color format: " + format);
        }
    }

    public static DeferredTextureSpec attachment(GpuFormat format, SamplePolicy samples) {
        return new DeferredTextureSpec(format, Resolution.FULL, samples, false, false, true);
    }

    public static DeferredTextureSpec compute(GpuFormat format, Resolution resolution, boolean mipChain) {
        return new DeferredTextureSpec(
                format, resolution, SamplePolicy.SINGLE_SAMPLE, mipChain, true, false
        );
    }

    public static DeferredTextureSpec computeAttachment(GpuFormat format, Resolution resolution) {
        return new DeferredTextureSpec(
                format, resolution, SamplePolicy.SINGLE_SAMPLE, false, true, true
        );
    }
}
