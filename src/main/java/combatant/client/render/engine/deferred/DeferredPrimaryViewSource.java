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
    private long historyEpoch;
    private DeferredHistoryResetReason pendingReset = DeferredHistoryResetReason.FIRST_FRAME;

    public void reset() {
        current = null;
        previous = null;
        worldOwner = null;
        queueHistoryReset(DeferredHistoryResetReason.RENDERER_RESET);
    }

    public void beginWorld(Object world) {
        if (worldOwner == world) return;
        boolean hadWorld = worldOwner != null;
        current = null;
        previous = null;
        worldOwner = world;
        queueHistoryReset(hadWorld
                ? DeferredHistoryResetReason.WORLD_CHANGE
                : DeferredHistoryResetReason.FIRST_FRAME);
    }

    public void capture(long frameId,
                        Matrix4fc view,
                        Matrix4fc projection,
                        @Nullable Vec3 cameraPosition,
                        float farPlane) {
        if (view == null || projection == null || cameraPosition == null) return;
        if (current != null && current.frameId == frameId) {
            current = new FrameView(
                    frameId,
                    new Matrix4f(view),
                    new Matrix4f(projection),
                    cameraPosition,
                    Float.isFinite(farPlane) && farPlane > 0.0f ? farPlane : 0.0f,
                    current.sunAngle,
                    current.historyEpoch,
                    current.historyResetReason
            );
            return;
        }

        FrameView prior = current;
        DeferredHistoryResetReason resetReason = pendingReset;
        pendingReset = DeferredHistoryResetReason.NONE;
        if (resetReason == DeferredHistoryResetReason.NONE) {
            if (prior == null) {
                resetReason = DeferredHistoryResetReason.FIRST_FRAME;
            } else if (prior.frameId + 1L != frameId) {
                resetReason = DeferredHistoryResetReason.FRAME_GAP;
            } else if (cameraPosition.distanceToSqr(prior.cameraPosition) > CAMERA_CUT_DISTANCE_SQUARED) {
                resetReason = DeferredHistoryResetReason.CAMERA_CUT;
            }
        }
        if (resetReason != DeferredHistoryResetReason.NONE) {
            historyEpoch++;
        }

        previous = prior;
        current = new FrameView(
                frameId,
                new Matrix4f(view),
                new Matrix4f(projection),
                cameraPosition,
                Float.isFinite(farPlane) && farPlane > 0.0f ? farPlane : 0.0f,
                Float.NaN,
                historyEpoch,
                resetReason
        );
    }

    public void updateSunAngle(long frameId, float sunAngle) {
        if (current == null || current.frameId != frameId || !Float.isFinite(sunAngle)) return;
        current = new FrameView(
                current.frameId, current.view, current.projection, current.cameraPosition,
                current.farPlane, sunAngle, current.historyEpoch, current.historyResetReason
        );
    }

    /** Queues an invalidation for the next primary-view capture. */
    public void queueHistoryReset(DeferredHistoryResetReason reason) {
        if (reason == null || reason == DeferredHistoryResetReason.NONE) return;
        if (pendingReset == DeferredHistoryResetReason.NONE) pendingReset = reason;
    }

    /** Invalidates the named frame if it has already been captured, otherwise queues the reset. */
    public void invalidateHistory(long frameId, DeferredHistoryResetReason reason) {
        if (reason == null || reason == DeferredHistoryResetReason.NONE) return;
        if (current == null || current.frameId != frameId) {
            queueHistoryReset(reason);
            return;
        }
        if (current.historyResetReason != DeferredHistoryResetReason.NONE) return;
        historyEpoch++;
        current = new FrameView(
                current.frameId, current.view, current.projection, current.cameraPosition,
                current.farPlane, current.sunAngle, historyEpoch, reason
        );
    }

    public @Nullable FrameView current() {
        return current;
    }

    public @Nullable FrameView previous() {
        return previous;
    }

    public DeferredHistoryDescriptor historyDescriptor() {
        if (current == null) {
            DeferredHistoryResetReason reason = pendingReset == DeferredHistoryResetReason.NONE
                    ? DeferredHistoryResetReason.FIRST_FRAME : pendingReset;
            return new DeferredHistoryDescriptor(historyEpoch, Long.MIN_VALUE, Long.MIN_VALUE, false, reason);
        }
        boolean valid = current.historyResetReason == DeferredHistoryResetReason.NONE
                && previous != null
                && previous.frameId + 1L == current.frameId
                && previous.historyEpoch == current.historyEpoch;
        return new DeferredHistoryDescriptor(
                current.historyEpoch,
                current.frameId,
                previous == null ? Long.MIN_VALUE : previous.frameId,
                valid,
                valid ? DeferredHistoryResetReason.NONE : current.historyResetReason
        );
    }

    public boolean hasTemporalHistory() {
        return historyDescriptor().valid();
    }

    public record FrameView(long frameId,
                            Matrix4f view,
                            Matrix4f projection,
                            Vec3 cameraPosition,
                            float farPlane,
                            float sunAngle,
                            long historyEpoch,
                            DeferredHistoryResetReason historyResetReason) {
        public FrameView {
            view = new Matrix4f(view);
            projection = new Matrix4f(projection);
            cameraPosition = cameraPosition == null ? Vec3.ZERO : cameraPosition;
            farPlane = Float.isFinite(farPlane) && farPlane > 0.0f ? farPlane : 0.0f;
            if (historyResetReason == null) historyResetReason = DeferredHistoryResetReason.RENDERER_RESET;
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
