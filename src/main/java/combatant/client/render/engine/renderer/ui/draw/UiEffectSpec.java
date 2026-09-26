/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.blend.UiBackdropBlendSpec;

public record UiEffectSpec(UiEffectKind kind,
                           UiShape shape,
                           float intensity,
                           float thickness,
                           float extra0,
                           float extra1,
                           int argb,
                           UiBackdropRequest backdrop,
                           UiBackdropBlendSpec backdropBlend) {
    public UiEffectSpec {
        backdrop = backdrop != null ? backdrop : UiBackdropRequest.NONE;
        backdropBlend = backdropBlend != null ? backdropBlend : UiBackdropBlendSpec.NORMAL;
    }

    /** Source-compatible constructor for existing effect producers that do not specify a blend material. */
    public UiEffectSpec(UiEffectKind kind,
                        UiShape shape,
                        float intensity,
                        float thickness,
                        float extra0,
                        float extra1,
                        int argb,
                        UiBackdropRequest backdrop) {
        this(kind, shape, intensity, thickness, extra0, extra1, argb, backdrop, UiBackdropBlendSpec.NORMAL);
    }

    public static UiEffectSpec blur(UiBoxShape box, double radius, int argb) {
        return blur(UiShape.box(box), radius, argb);
    }

    public static UiEffectSpec blur(UiShape shape, double radius, int argb) {
        UiRect bounds = shape != null ? shape.bounds() : null;
        UiBackdropRequest backdrop = UiBackdropRequest.currentTargetBlur(
                bounds,
                UiBlurQuality.MEDIUM,
                1.0f
        );
        return blur(shape, radius, argb, backdrop);
    }

    public static UiEffectSpec blur(UiShape shape, double radius, int argb, UiBackdropRequest backdrop) {
        return new UiEffectSpec(UiEffectKind.BLUR, shape, (float) Math.max(0.0, radius),
                0f, 0f, 0f, argb, backdrop, UiBackdropBlendSpec.NORMAL);
    }

    public static UiEffectSpec liquidGlass(UiBoxShape box, double radius, double thickness, double distortion, int argb) {
        return liquidGlass(UiShape.box(box), radius, thickness, distortion, argb);
    }

    public static UiEffectSpec liquidGlass(UiShape shape, double radius, double thickness, double distortion, int argb) {
        UiRect bounds = shape != null ? shape.bounds() : null;
        UiBackdropRequest backdrop = UiBackdropRequest.capturedSceneGlass(
                bounds,
                UiBlurQuality.LIQUID_GLASS,
                Renderer2D.LIQUID_GLASS_KAWASE_OFFSET_PX
        );
        return liquidGlass(shape, radius, thickness, distortion, argb, backdrop);
    }

    public static UiEffectSpec liquidGlass(UiShape shape, double radius, double thickness,
                                           double distortion, int argb, UiBackdropRequest backdrop) {
        return liquidGlass(shape, radius, thickness, distortion, argb, backdrop, UiBackdropBlendSpec.NORMAL);
    }

    public static UiEffectSpec liquidGlass(UiShape shape, double radius, double thickness,
                                           double distortion, int argb, UiBackdropRequest backdrop,
                                           UiBackdropBlendSpec backdropBlend) {
        return new UiEffectSpec(UiEffectKind.LIQUID_GLASS, shape,
                (float) Math.max(0.0, radius), (float) Math.max(0.0, thickness),
                (float) distortion, 0f, argb, backdrop, backdropBlend);
    }

    public UiEffectSpec withBackdropBlend(UiBackdropBlendSpec blend) {
        return new UiEffectSpec(kind, shape, intensity, thickness, extra0, extra1, argb, backdrop, blend);
    }
}
