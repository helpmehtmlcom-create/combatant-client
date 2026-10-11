/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/**
 * Optical model data for the shared glass surface pipeline.
 */
public record UiGlassOpticsSpec(Mode mode,
                               float refraction,
                               float bevelWidth,
                               float bevelDepth,
                               float interactionStrength,
                               float interactionRadius,
                               float interactionViscosity,
                               float cleanReveal,
                               float chromaticAberration, float magnification,
                               boolean shapeDeformation,
                               float tiltXDegrees, float tiltYDegrees, boolean specular,
                               ChromaticMotion chromaticMotion) {
    /** liquidGL-derived defaults for Combatant's captured-scene coordinates. */
    public static final UiGlassOpticsSpec LIQUID = new UiGlassOpticsSpec(
            Mode.LIQUID_REFRACTION,
            0.010f,
            0.15f,
            0.055f,
            0.50f,
            0.35f,
            0.65f,
            0.55f, 0.0f, 1.0f, false, 0.0f, 0.0f, true, ChromaticMotion.STATIC
    );

    /** Pre-existing eight-field constructor remains source compatible. */
    public UiGlassOpticsSpec(Mode mode, float refraction, float bevelWidth, float bevelDepth,
                            float interactionStrength, float interactionRadius,
                            float interactionViscosity, float cleanReveal) {
        this(mode, refraction, bevelWidth, bevelDepth, interactionStrength, interactionRadius,
                interactionViscosity, cleanReveal, 0.0f, 1.0f, false, 0.0f, 0.0f, true,
                ChromaticMotion.STATIC);
    }

    public UiGlassOpticsSpec(Mode mode, float refraction, float bevelWidth, float bevelDepth,
                            float interactionStrength, float interactionRadius,
                            float interactionViscosity, float cleanReveal,
                            float chromaticAberration, float magnification,
                            boolean shapeDeformation) {
        this(mode, refraction, bevelWidth, bevelDepth, interactionStrength, interactionRadius,
                interactionViscosity, cleanReveal, chromaticAberration, magnification,
                shapeDeformation, 0.0f, 0.0f, true, ChromaticMotion.STATIC);
    }

    public UiGlassOpticsSpec(Mode mode, float refraction, float bevelWidth, float bevelDepth,
                            float interactionStrength, float interactionRadius,
                            float interactionViscosity, float cleanReveal,
                            float chromaticAberration, float magnification,
                            boolean shapeDeformation,
                            float tiltXDegrees, float tiltYDegrees, boolean specular) {
        this(mode, refraction, bevelWidth, bevelDepth, interactionStrength, interactionRadius,
                interactionViscosity, cleanReveal, chromaticAberration, magnification,
                shapeDeformation, tiltXDegrees, tiltYDegrees, specular, ChromaticMotion.STATIC);
    }

    public UiGlassOpticsSpec {
        mode = mode != null ? mode : Mode.LIQUID_REFRACTION;
        refraction = finiteClamp(refraction, -0.08f, 0.08f);
        bevelWidth = finiteClamp(bevelWidth, 0.001f, 1.0f);
        bevelDepth = finiteClamp(bevelDepth, -0.15f, 0.15f);
        interactionStrength = finiteClamp(interactionStrength, 0.0f, 5.0f);
        interactionRadius = finiteClamp(interactionRadius, 0.0f, 2.0f);
        interactionViscosity = finiteClamp(interactionViscosity, 0.0f, 1.0f);
        cleanReveal = finiteClamp(cleanReveal, 0.0f, 1.0f);
        chromaticAberration = finiteClamp(chromaticAberration, 0.0f, 0.95f);
        magnification = finiteClamp(magnification, 0.65f, 1.8f);
        tiltXDegrees = finiteClamp(tiltXDegrees, -16.0f, 15.75f);
        tiltYDegrees = finiteClamp(tiltYDegrees, -16.0f, 15.75f);
        chromaticMotion = chromaticMotion != null ? chromaticMotion : ChromaticMotion.STATIC;
    }

    public UiGlassOpticsSpec withMode(Mode value) {
        return new UiGlassOpticsSpec(value, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion);
    }

    /** Independent optical curvature; does not alter the bevel width or blur. */
    public UiGlassOpticsSpec withRefraction(float value) {
        return new UiGlassOpticsSpec(mode, value, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion);
    }

    public UiGlassOpticsSpec withBevel(float width, float depth) {
        return new UiGlassOpticsSpec(mode, refraction, width, depth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion);
    }

    public UiGlassOpticsSpec withFluid(float radius, float viscosity) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, radius, viscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion);
    }

    public UiGlassOpticsSpec withCleanReveal(float value) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, value,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion);
    }

    public UiGlassOpticsSpec withInteractionStrength(float strength) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                strength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion);
    }

    public UiGlassOpticsSpec withChromaticAberration(float aberration) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                aberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion);
    }

    public UiGlassOpticsSpec withMagnification(float zoom) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, zoom, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion);
    }

    public UiGlassOpticsSpec withShapeDeformation(boolean enabled) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, enabled,
                tiltXDegrees, tiltYDegrees, specular, chromaticMotion);
    }

    /** liquidGL-compatible optical tilt, independent of pointer-driven deformation. */
    public UiGlassOpticsSpec withTilt(float xDegrees, float yDegrees) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                xDegrees, yDegrees, specular, chromaticMotion);
    }

    public UiGlassOpticsSpec withSpecular(boolean enabled) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, enabled, chromaticMotion);
    }

    public UiGlassOpticsSpec withChromaticMotion(ChromaticMotion motion) {
        return new UiGlassOpticsSpec(mode, refraction, bevelWidth, bevelDepth,
                interactionStrength, interactionRadius, interactionViscosity, cleanReveal,
                chromaticAberration, magnification, shapeDeformation,
                tiltXDegrees, tiltYDegrees, specular, motion);
    }

    public enum Mode {
        LIQUID_REFRACTION(0),
        REACTIVE_SMOKED(2),
        ETCHED_GLASS(3),
        SOFT_LENS(4);

        private final int shaderId;

        Mode(int shaderId) {
            this.shaderId = shaderId;
        }

        public int shaderId() {
            return shaderId;
        }
    }

    public enum ChromaticMotion {
        STATIC(0),
        FLOW(1),
        IMPACT_EDGE(2);

        private final int shaderId;

        ChromaticMotion(int shaderId) {
            this.shaderId = shaderId;
        }

        public int shaderId() {
            return shaderId;
        }
    }

    private static float finiteClamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
