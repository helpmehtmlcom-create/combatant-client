/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Object-motion producer contract. Missing previous transforms/deformation state remain explicit;
 * consumers must not reinterpret either case as a stationary object.
 */
public record DeferredMotionState(
        @Nullable Matrix4f currentTransform,
        @Nullable Matrix4f previousTransform,
        boolean previousTransformAvailable,
        DeformationHistory deformationHistory,
        Source source
) {
    public enum Source {
        STATIC_TERRAIN,
        ENTITY,
        BLOCK_ENTITY,
        WATER,
        CLOUD,
        VERTEX_ANIMATION,
        EXTENSION
    }

    public enum DeformationHistory {
        NOT_REQUIRED,
        AVAILABLE,
        UNAVAILABLE
    }

    public DeferredMotionState {
        currentTransform = currentTransform == null ? null : new Matrix4f(currentTransform);
        previousTransform = previousTransform == null ? null : new Matrix4f(previousTransform);
        previousTransformAvailable &= previousTransform != null;
        deformationHistory = deformationHistory == null ? DeformationHistory.NOT_REQUIRED : deformationHistory;
        source = source == null ? Source.EXTENSION : source;
    }

    public static DeferredMotionState rigid(Matrix4fc current, @Nullable Matrix4fc previous, Source source) {
        return of(current, previous, DeformationHistory.NOT_REQUIRED, source);
    }

    public static DeferredMotionState of(Matrix4fc current, @Nullable Matrix4fc previous,
                                         DeformationHistory deformationHistory, Source source) {
        return new DeferredMotionState(
                current == null ? null : new Matrix4f(current),
                previous == null ? null : new Matrix4f(previous),
                previous != null,
                deformationHistory,
                source
        );
    }

    public boolean motionAvailable() {
        return currentTransform != null && previousTransformAvailable && previousTransform != null
                && deformationHistory != DeformationHistory.UNAVAILABLE;
    }
}
