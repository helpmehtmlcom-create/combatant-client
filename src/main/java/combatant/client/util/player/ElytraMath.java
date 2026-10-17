/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.world.phys.Vec3;

/**
 * Pure elytra math shared by ElytraFly and ElytraTarget: the vanilla gliding step (the one GrimAC simulates), target
 * interception and the pitch controller. No world or player access, so everything here is unit-testable.
 */
public final class ElytraMath {

    /** Default entity gravity in blocks per tick squared. */
    public static final double GRAVITY = 0.08;
    /** Blocks per tick to km/h. */
    public static final double KMH_PER_BLOCK_TICK = 72.0;
    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double EPS = 1.0E-7;

    private ElytraMath() {
    }

    /** Tests whether a player standing at {@code pos} would intersect the world. */
    @FunctionalInterface
    public interface CollisionProbe {
        CollisionProbe NONE = pos -> false;

        boolean collides(Vec3 pos);
    }

    /** Unit look vector for vanilla yaw/pitch degrees. */
    public static Vec3 lookVector(float yaw, float pitch) {
        double f = pitch * DEG_TO_RAD;
        double g = -yaw * DEG_TO_RAD;
        double cosG = Math.cos(g);
        double sinG = Math.sin(g);
        double cosF = Math.cos(f);
        double sinF = Math.sin(f);
        return new Vec3(sinG * cosF, -sinF, cosG * cosF);
    }

    public static double speedKmh(Vec3 velocity) {
        return velocity.horizontalDistance() * KMH_PER_BLOCK_TICK;
    }

    /**
     * One tick of vanilla {@code LivingEntity.updateFallFlyingMovement} including the final 0.99/0.98/0.99 drag, for
     * a glider looking along {@code yaw}/{@code pitch}. Returns the velocity after the tick (no firework boost).
     */
    public static Vec3 step(Vec3 velocity, float yaw, float pitch) {
        Vec3 look = lookVector(yaw, pitch);
        double pitchRad = pitch * DEG_TO_RAD;
        double lookHor = Math.sqrt(look.x * look.x + look.z * look.z);
        double moveHor = velocity.horizontalDistance();
        double cosPitch = Math.cos(pitchRad);
        double lift = cosPitch * cosPitch;

        double x = velocity.x;
        double y = velocity.y + GRAVITY * (-1.0 + lift * 0.75);
        double z = velocity.z;

        if (y < 0.0 && lookHor > 0.0) {
            double convert = y * -0.1 * lift;
            x += look.x * convert / lookHor;
            y += convert;
            z += look.z * convert / lookHor;
        }
        if (pitchRad < 0.0 && lookHor > 0.0) {
            double convert = moveHor * -Math.sin(pitchRad) * 0.04;
            x += -look.x * convert / lookHor;
            y += convert * 3.2;
            z += -look.z * convert / lookHor;
        }
        if (lookHor > 0.0) {
            x += (look.x / lookHor * moveHor - x) * 0.1;
            z += (look.z / lookHor * moveHor - z) * 0.1;
        }
        return new Vec3(x * 0.99, y * 0.98, z * 0.99);
    }

    /**
     * Ticks until a body moving at {@code speed} (blocks per tick, straight line) meets a target that is at
     * {@code relative} from it and moves with {@code targetVelocity}. Positive infinity if it cannot catch it.
     */
    public static double interceptTicks(Vec3 relative, Vec3 targetVelocity, double speed) {
        double c = relative.lengthSqr();
        if (c < EPS) return 0.0;
        double a = targetVelocity.lengthSqr() - speed * speed;
        double b = 2.0 * relative.dot(targetVelocity);

        if (Math.abs(a) < EPS) {
            return b < 0.0 ? -c / b : Double.POSITIVE_INFINITY;
        }
        double disc = b * b - 4.0 * a * c;
        if (disc < 0.0) return Double.POSITIVE_INFINITY;
        double root = Math.sqrt(disc);
        double t1 = (-b - root) / (2.0 * a);
        double t2 = (-b + root) / (2.0 * a);
        double lo = Math.min(t1, t2);
        double hi = Math.max(t1, t2);
        if (lo > 0.0) return lo;
        return hi > 0.0 ? hi : Double.POSITIVE_INFINITY;
    }

    /**
     * How many ticks ahead to aim at a moving target: the interception time at the current closing speed, never more
     * than {@code maxTicks}. When we are too slow to ever catch it (or standing still) the full {@code maxTicks} is used.
     */
    public static double leadTicks(Vec3 relative, Vec3 targetVelocity, double speed, double maxTicks) {
        if (maxTicks <= 0.0) return 0.0;
        if (speed < 0.05) return maxTicks;
        double t = interceptTicks(relative, targetVelocity, speed);
        if (Double.isInfinite(t) || Double.isNaN(t)) return maxTicks;
        return Math.max(0.0, Math.min(maxTicks, t));
    }

    /** Where the target will be after {@link #leadTicks}; linear extrapolation of its velocity. */
    public static Vec3 interceptPoint(Vec3 selfPos, Vec3 targetPos, Vec3 targetVelocity, double speed, double maxTicks) {
        double ticks = leadTicks(targetPos.subtract(selfPos), targetVelocity, speed, maxTicks);
        return targetPos.add(targetVelocity.scale(ticks));
    }

    /** Parameters of {@link #controlPitch}. */
    public record ControlParams(double targetY, double minSpeed, int horizonTicks, float maxStep, float minPitch, float maxPitch) {
    }

    /**
     * Pitch controller for vanilla gliding: looks {@code horizonTicks} ahead with {@link #step} for every candidate
     * pitch within {@code maxStep} of {@code previousPitch}, and picks the one that ends closest to
     * {@code targetY} with the least vertical speed, keeps horizontal speed above {@code minSpeed} and does not hit
     * anything (per {@code probe}). Everything it does is a plain pitch change, so the server simulates exactly it.
     */
    public static float controlPitch(Vec3 position, Vec3 velocity, float yaw, float previousPitch,
                                     ControlParams params, CollisionProbe probe) {
        float lo = Math.max(params.minPitch(), previousPitch - params.maxStep());
        float hi = Math.min(params.maxPitch(), previousPitch + params.maxStep());
        if (lo > hi) {
            float clamped = Math.max(params.minPitch(), Math.min(params.maxPitch(), previousPitch));
            lo = hi = clamped;
        }

        float best = Math.max(lo, Math.min(hi, previousPitch));
        double bestCost = Double.POSITIVE_INFINITY;
        int horizon = Math.max(1, params.horizonTicks());
        float stepDeg = Math.max(0.5f, (hi - lo) / 12.0f);

        for (float pitch = lo; pitch <= hi + 1.0E-4f; pitch += stepDeg) {
            double cost = rollout(position, velocity, yaw, pitch, horizon, params, probe);
            cost += Math.abs(pitch - previousPitch) * 0.002;
            if (cost < bestCost) {
                bestCost = cost;
                best = pitch;
            }
        }
        return best;
    }

    private static double rollout(Vec3 position, Vec3 velocity, float yaw, float pitch, int horizon,
                                  ControlParams params, CollisionProbe probe) {
        Vec3 pos = position;
        Vec3 vel = velocity;
        double cost = 0.0;
        for (int tick = 1; tick <= horizon; tick++) {
            vel = step(vel, yaw, pitch);
            pos = pos.add(vel);
            if (probe.collides(pos)) {
                cost += 10_000.0 * (horizon - tick + 1);
                break;
            }
            double speed = vel.horizontalDistance();
            if (speed < params.minSpeed()) {
                double deficit = params.minSpeed() - speed;
                cost += deficit * deficit * 400.0;
            }
        }
        double dy = pos.y - params.targetY();
        cost += dy * dy + vel.y * vel.y * 60.0;
        return cost;
    }
}
