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
        ResolutionClass resolution,
        SamplePolicy samples,
        boolean mipChain,
        boolean storageImage,
        boolean renderAttachment
) {
    /**
     * Semantic resolution classes. The class is stable graph ABI; the actual scale is runtime
     * policy and can change without replacing resource keys or pass contracts.
     */
    public enum ResolutionClass {
        FULL,
        SHADOW_OUTPUT,
        CONTACT_SHADOW_TRACE,
        AMBIENT_OCCLUSION,
        INDIRECT_LIGHT,
        REFLECTION_TRACE,
        REFLECTION_OUTPUT,
        REFLECTION_HISTORY;

        public float scale(DeferredRuntimeConfig.Snapshot settings) {
            if (settings == null) settings = DeferredRuntimeConfig.current();
            return switch (this) {
                case FULL -> 1.0f;
                case SHADOW_OUTPUT -> settings.shadowOutputScale();
                case CONTACT_SHADOW_TRACE -> settings.contactShadowScale();
                case AMBIENT_OCCLUSION -> settings.ambientOcclusionScale();
                case INDIRECT_LIGHT -> settings.indirectLightScale();
                case REFLECTION_TRACE -> settings.reflectionTraceScale();
                case REFLECTION_OUTPUT -> settings.reflectionOutputScale();
                case REFLECTION_HISTORY -> settings.reflectionHistoryScale();
            };
        }

        public int width(int fullWidth, DeferredRuntimeConfig.Snapshot settings) {
            return scaledExtent(fullWidth, scale(settings));
        }

        public int width(int fullWidth) {
            return width(fullWidth, DeferredRuntimeConfig.current());
        }

        public int height(int fullHeight, DeferredRuntimeConfig.Snapshot settings) {
            return scaledExtent(fullHeight, scale(settings));
        }

        public int height(int fullHeight) {
            return height(fullHeight, DeferredRuntimeConfig.current());
        }

        private static int scaledExtent(int fullExtent, float scale) {
            int extent = Math.max(1, fullExtent);
            float safeScale = Float.isFinite(scale) ? Math.max(0.01f, scale) : 1.0f;
            return Math.max(1, Math.round(extent * safeScale));
        }
    }

    public enum SamplePolicy {
        MATCH_SCENE,
        SINGLE_SAMPLE
    }

    public DeferredTextureSpec {
        if (format == null) throw new IllegalArgumentException("format");
        if (resolution == null) resolution = ResolutionClass.FULL;
        if (samples == null) samples = SamplePolicy.SINGLE_SAMPLE;
        if (storageImage && !format.hasColorAspect()) {
            throw new IllegalArgumentException("Storage image requires a color format: " + format);
        }
    }

    public static DeferredTextureSpec attachment(GpuFormat format, SamplePolicy samples) {
        return new DeferredTextureSpec(format, ResolutionClass.FULL, samples, false, false, true);
    }

    public static DeferredTextureSpec compute(GpuFormat format, ResolutionClass resolution, boolean mipChain) {
        return new DeferredTextureSpec(
                format, resolution, SamplePolicy.SINGLE_SAMPLE, mipChain, true, false
        );
    }

    public static DeferredTextureSpec computeAttachment(GpuFormat format, ResolutionClass resolution) {
        return new DeferredTextureSpec(
                format, resolution, SamplePolicy.SINGLE_SAMPLE, false, true, true
        );
    }
}
