/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Authoritative primary-world camera history captured at the GameRenderer projection submission
 * boundary, before terrain rendering begins.
 */
public final class DeferredPrimaryViewSource {
    private static final double CAMERA_CUT_DISTANCE_SQUARED = 64.0 * 64.0;

    private @Nullable FrameView current;
    private @Nullable FrameView previous;
    private Object worldOwner;

    public void reset() {
        current = null;
        previous = null;
        worldOwner = null;
    }

    public void beginWorld(Object world) {
        if (worldOwner == world) return;
        current = null;
        previous = null;
        worldOwner = world;
    }

    public void capture(long frameId,
                        Matrix4fc view,
                        Matrix4fc projection,
                        @Nullable Vec3 cameraPosition,
                        float farPlane) {
        if (view == null || projection == null || cameraPosition == null) return;
        FrameView next = new FrameView(
                frameId,
                new Matrix4f(view),
                new Matrix4f(projection),
                cameraPosition,
                Float.isFinite(farPlane) && farPlane > 0.0f ? farPlane : 0.0f,
                current != null && current.frameId == frameId ? current.sunAngle : Float.NaN
        );
        if (current != null && current.frameId == frameId) {
            current = next;
            return;
        }
        previous = current;
        current = next;
    }

    public void updateSunAngle(long frameId, float sunAngle) {
        if (current == null || current.frameId != frameId || !Float.isFinite(sunAngle)) return;
        current = new FrameView(current.frameId, current.view, current.projection, current.cameraPosition, current.farPlane, sunAngle);
    }

    public @Nullable FrameView current() {
        return current;
    }

    public @Nullable FrameView previous() {
        return previous;
    }

    public boolean hasTemporalHistory() {
        if (current == null || previous == null) return false;
        if (previous.frameId + 1L != current.frameId) return false;
        return current.cameraPosition.distanceToSqr(previous.cameraPosition) <= CAMERA_CUT_DISTANCE_SQUARED;
    }

    public record FrameView(long frameId,
                            Matrix4f view,
                            Matrix4f projection,
                            Vec3 cameraPosition,
                            float farPlane,
                            float sunAngle) {
        public FrameView {
            view = new Matrix4f(view);
            projection = new Matrix4f(projection);
            cameraPosition = cameraPosition == null ? Vec3.ZERO : cameraPosition;
            farPlane = Float.isFinite(farPlane) && farPlane > 0.0f ? farPlane : 0.0f;
        }

        @Override
        public Matrix4f view() {
            return new Matrix4f(view);
        }

        @Override
        public Matrix4f projection() {
            return new Matrix4f(projection);
        }

        public Matrix4f inverseView() {
            return new Matrix4f(view).invert();
        }

        public Matrix4f inverseProjection() {
            return new Matrix4f(projection).invert();
        }

        public boolean hasSunAngle() {
            return Float.isFinite(sunAngle);
        }
    }
}
