/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.water;

/**
 * Temporal state for water deformation. Previous time is an actual previous deformation state,
 * not a value reconstructed from the previous camera matrix.
 */
public final class WaterDeformationState {
    private long frameId = Long.MIN_VALUE;
    private float currentTime;
    private float previousTime;
    private boolean previousAvailable;

    public FrameTimes update(long newFrameId, float time, boolean historyValid) {
        float finiteTime = Float.isFinite(time) ? time : 0.0f;
        if (frameId != newFrameId) {
            boolean consecutive = frameId != Long.MIN_VALUE && frameId + 1L == newFrameId;
            previousTime = consecutive ? currentTime : finiteTime;
            currentTime = finiteTime;
            frameId = newFrameId;
            previousAvailable = historyValid && consecutive;
        } else {
            currentTime = finiteTime;
            previousAvailable &= historyValid;
            if (!previousAvailable) previousTime = currentTime;
        }
        return new FrameTimes(currentTime, previousAvailable ? previousTime : currentTime, previousAvailable);
    }

    public void reset() {
        frameId = Long.MIN_VALUE;
        currentTime = 0.0f;
        previousTime = 0.0f;
        previousAvailable = false;
    }

    public record FrameTimes(float currentTime, float previousTime, boolean historyValid) {
    }
}
