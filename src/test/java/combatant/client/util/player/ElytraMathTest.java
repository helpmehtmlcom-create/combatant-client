package combatant.client.util.player;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraMathTest {

    @Test
    void stepMatchesVanillaForLevelGlide() {
        // Vanilla: level glide (pitch 0) at 1.0 b/t horizontal, no vertical speed.
        // y = 0 + 0.08 * (-1 + 1 * 0.75) = -0.02; then convert = -0.02 * -0.1 * 1 = 0.002 -> y = -0.018
        // x: moveHor stays 1.0 along look, drag 0.99.
        Vec3 next = ElytraMath.step(new Vec3(0.0, 0.0, 1.0), 0.0f, 0.0f);
        assertEquals(0.0, next.x, 1.0E-9);
        assertEquals(-0.018 * 0.98, next.y, 1.0E-9);
        assertEquals(1.0018 * 0.99, next.z, 1.0E-6);
    }

    @Test
    void divingGainsSpeedAndClimbingLosesIt() {
        Vec3 start = new Vec3(0.0, 0.0, 1.0);
        Vec3 dive = start;
        Vec3 climb = start;
        for (int i = 0; i < 10; i++) {
            dive = ElytraMath.step(dive, 0.0f, 45.0f);
            climb = ElytraMath.step(climb, 0.0f, -45.0f);
        }
        assertTrue(dive.horizontalDistance() > 1.0 || dive.y < -0.5, "dive converts height into speed");
        assertTrue(dive.y < climb.y);
        assertTrue(climb.horizontalDistance() < start.horizontalDistance());
    }

    @Test
    void lookVectorFollowsVanillaConvention() {
        Vec3 south = ElytraMath.lookVector(0.0f, 0.0f);
        assertEquals(1.0, south.z, 1.0E-9);
        Vec3 west = ElytraMath.lookVector(90.0f, 0.0f);
        assertEquals(-1.0, west.x, 1.0E-9);
        Vec3 up = ElytraMath.lookVector(0.0f, -90.0f);
        assertEquals(1.0, up.y, 1.0E-9);
    }

    @Test
    void interceptOfStationaryTargetIsDistanceOverSpeed() {
        double ticks = ElytraMath.interceptTicks(new Vec3(0.0, 0.0, 30.0), Vec3.ZERO, 1.5);
        assertEquals(20.0, ticks, 1.0E-9);
    }

    @Test
    void interceptLeadsCrossingTarget() {
        Vec3 rel = new Vec3(0.0, 0.0, 30.0);
        Vec3 targetVelocity = new Vec3(0.5, 0.0, 0.0);
        double speed = 1.5;
        double t = ElytraMath.interceptTicks(rel, targetVelocity, speed);
        Vec3 meet = rel.add(targetVelocity.scale(t));
        assertEquals(speed * t, meet.length(), 1.0E-6, "interceptor covers exactly the distance to the meeting point");
        assertTrue(t > 20.0, "a crossing target needs more than the stationary time");
    }

    @Test
    void interceptOfFasterFleeingTargetIsImpossible() {
        double t = ElytraMath.interceptTicks(new Vec3(0.0, 0.0, 10.0), new Vec3(0.0, 0.0, 2.0), 1.0);
        assertTrue(Double.isInfinite(t));
    }

    @Test
    void leadTicksAreCappedAndFallBack() {
        Vec3 rel = new Vec3(0.0, 0.0, 60.0);
        assertEquals(6.0, ElytraMath.leadTicks(rel, Vec3.ZERO, 1.5, 6.0), 1.0E-9);
        assertEquals(6.0, ElytraMath.leadTicks(rel, Vec3.ZERO, 0.0, 6.0), 1.0E-9, "stationary self uses the cap");
        assertEquals(6.0, ElytraMath.leadTicks(rel, new Vec3(0.0, 0.0, 3.0), 1.0, 6.0), 1.0E-9, "uncatchable uses the cap");
        assertEquals(2.0, ElytraMath.leadTicks(new Vec3(0.0, 0.0, 3.0), Vec3.ZERO, 1.5, 6.0), 1.0E-9);
        assertEquals(0.0, ElytraMath.leadTicks(rel, Vec3.ZERO, 1.5, 0.0), 1.0E-9);
    }

    @Test
    void interceptPointAimsAheadOfMovingTarget() {
        Vec3 self = Vec3.ZERO;
        Vec3 target = new Vec3(0.0, 0.0, 6.0);
        Vec3 point = ElytraMath.interceptPoint(self, target, new Vec3(0.5, 0.0, 0.0), 1.5, 10.0);
        assertTrue(point.x > 0.0);
        assertEquals(6.0, point.z, 1.0E-9);
    }

    private static ElytraMath.ControlParams hold(double y) {
        return new ElytraMath.ControlParams(y, 0.6, 12, 8.0f, -50.0f, 50.0f);
    }

    @Test
    void controlPitchClimbsWhenBelowTargetAndDivesWhenAbove() {
        Vec3 vel = new Vec3(0.0, 0.0, 1.2);
        float below = ElytraMath.controlPitch(new Vec3(0.0, 60.0, 0.0), vel, 0.0f, 0.0f, hold(70.0), ElytraMath.CollisionProbe.NONE);
        float above = ElytraMath.controlPitch(new Vec3(0.0, 80.0, 0.0), vel, 0.0f, 0.0f, hold(70.0), ElytraMath.CollisionProbe.NONE);
        assertTrue(below < 0.0f, "negative pitch looks up");
        assertTrue(above > 0.0f, "positive pitch looks down");
    }

    @Test
    void controlPitchRespectsStepLimit() {
        float pitch = ElytraMath.controlPitch(new Vec3(0.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.5), 0.0f, 10.0f,
                hold(100.0), ElytraMath.CollisionProbe.NONE);
        assertTrue(pitch >= 2.0f - 1.0E-3f && pitch <= 18.0f + 1.0E-3f, "pitch " + pitch);
    }

    @Test
    void controlPitchHoldsAltitudeOverLongRun() {
        Vec3 pos = new Vec3(0.0, 100.0, 0.0);
        Vec3 vel = new Vec3(0.0, 0.0, 1.0);
        float pitch = 0.0f;
        double minY = pos.y;
        double maxY = pos.y;
        double minSpeed = Double.MAX_VALUE;
        for (int i = 0; i < 400; i++) {
            pitch = ElytraMath.controlPitch(pos, vel, 0.0f, pitch, hold(100.0), ElytraMath.CollisionProbe.NONE);
            vel = ElytraMath.step(vel, 0.0f, pitch);
            pos = pos.add(vel);
            minY = Math.min(minY, pos.y);
            maxY = Math.max(maxY, pos.y);
            if (i > 40) minSpeed = Math.min(minSpeed, vel.horizontalDistance());
        }
        // No thrust: energy bleeds off, so it cannot hold forever, but it must stay sane and never stall to zero speed
        // in the first seconds. It trades altitude for speed within a few blocks.
        assertTrue(maxY - 100.0 < 6.0, "overshoot " + (maxY - 100.0));
        assertTrue(minSpeed > 0.2, "speed " + minSpeed);
    }

    @Test
    void controlPitchAvoidsWallAhead() {
        // A wall of solid blocks at z >= 20 for heights below 120: the controller must pitch up to clear it.
        ElytraMath.CollisionProbe wall = pos -> pos.z >= 20.0 && pos.y < 120.0;
        Vec3 pos = new Vec3(0.0, 100.0, 0.0);
        Vec3 vel = new Vec3(0.0, 0.0, 1.6);
        float pitch = ElytraMath.controlPitch(pos, vel, 0.0f, 0.0f,
                new ElytraMath.ControlParams(100.0, 0.6, 14, 15.0f, -50.0f, 50.0f), wall);
        assertTrue(pitch < -5.0f, "pitch " + pitch);
        assertFalse(wall.collides(pos));
    }
}
