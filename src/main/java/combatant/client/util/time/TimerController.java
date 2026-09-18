/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.time;

import net.minecraft.util.Mth;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TimerController {
    public static final TimerController INSTANCE = new TimerController();

    public static final int NOT_IMPORTANT = 0;
    public static final int NORMAL = 100;
    public static final int IMPORTANT_FOR_USAGE_1 = 1000;
    public static final int IMPORTANT_FOR_USAGE_2 = 2000;
    public static final int IMPORTANT_FOR_PLAYER_LIFE = 10000;

    private static double timerBalanceMs = 0.0;
    private static final double MAX_BALANCE_MS = 500.0;
    private static final double MIN_BALANCE_MS = -50.0;

    private final Map<Object, Request> requests = new LinkedHashMap<>();
    private long sequence;

    private TimerController() {
    }

    public static float getTimerSpeed() {
        return INSTANCE.activeTimerSpeed();
    }

    public static void requestTimerSpeed(float timerSpeed, int priority, Object provider) {
        requestTimerSpeed(timerSpeed, priority, provider, 1);
    }

    public static void requestTimerSpeed(float timerSpeed, int priority, Object provider, int resetAfterTicks) {
        INSTANCE.request(provider, timerSpeed, priority, resetAfterTicks);
    }

    public static void clear(Object provider) {
        INSTANCE.clearProvider(provider);
    }

    /**
     * Gets the current anti-cheat timer balance in milliseconds.
     *
     * @return current balance in ms
     */
    public static synchronized double getTimerBalanceMs() {
        return timerBalanceMs;
    }

    /**
     * Records a game tick for anti-cheat timer balance monitoring.
     * Normal 1.0x tick length is 50.0ms (50,000,000 ns).
     * When activeSpeed > 1.0f, drains balance by (activeSpeed - 1.0f) * 50.0.
     * When activeSpeed < 1.0f, charges balance by (1.0f - activeSpeed) * 50.0 up to MAX_BALANCE_MS.
     *
     * @param elapsedNanos elapsed tick duration in nanoseconds
     * @param activeSpeed the active timer speed multiplier
     */
    public static synchronized void recordTick(long elapsedNanos, float activeSpeed) {
        if (activeSpeed > 1.0f) {
            timerBalanceMs -= (activeSpeed - 1.0f) * 50.0;
        } else if (activeSpeed < 1.0f) {
            timerBalanceMs = Math.min(MAX_BALANCE_MS, timerBalanceMs + (1.0f - activeSpeed) * 50.0);
        }
    }

    /**
     * Checks if running at requestedSpeed for durationTicks would breach MIN_BALANCE_MS.
     *
     * @param requestedSpeed the proposed timer speed multiplier
     * @param durationTicks the planned duration in ticks
     * @return true if timer balance remains >= MIN_BALANCE_MS
     */
    public static synchronized boolean hasTimerBalance(float requestedSpeed, int durationTicks) {
        if (Float.isNaN(requestedSpeed) || Float.isInfinite(requestedSpeed) || requestedSpeed <= 1.0f || durationTicks <= 0) {
            return true;
        }
        double drain = (double) durationTicks * (requestedSpeed - 1.0f) * 50.0;
        return (timerBalanceMs - drain) >= MIN_BALANCE_MS;
    }

    /**
     * Returns the maximum number of ticks that can run at requestedSpeed before reaching MIN_BALANCE_MS.
     *
     * @param requestedSpeed the proposed timer speed multiplier
     * @return maximum safe burst ticks, or Integer.MAX_VALUE if requestedSpeed <= 1.0f
     */
    public static synchronized int getSafeBurstTicks(float requestedSpeed) {
        if (Float.isNaN(requestedSpeed) || Float.isInfinite(requestedSpeed) || requestedSpeed <= 1.0f) {
            return Integer.MAX_VALUE;
        }
        double drainPerTick = (requestedSpeed - 1.0f) * 50.0;
        double available = timerBalanceMs - MIN_BALANCE_MS;
        if (available <= 0.0) {
            return 0;
        }
        return (int) Math.floor(available / drainPerTick);
    }

    private static float sanitize(float timerSpeed) {
        if (Float.isNaN(timerSpeed) || Float.isInfinite(timerSpeed)) {
            return 1.0f;
        }
        return Mth.clamp(timerSpeed, 0.1f, 20.0f);
    }

    @EventHandler(priority = 10000)
    public void onTick(GameTickEvent event) {
        tick();
    }

    public synchronized void request(Object provider, float timerSpeed, int priority, int resetAfterTicks) {
        Object key = provider != null ? provider : TimerController.class;
        float speed = sanitize(timerSpeed);
        int ticks = Math.max(0, resetAfterTicks) + 1;
        requests.put(key, new Request(speed, priority, ticks, ++sequence));
    }

    public synchronized void clearProvider(Object provider) {
        if (provider != null) {
            requests.remove(provider);
        }
    }

    public synchronized void reset() {
        requests.clear();
        synchronized (TimerController.class) {
            timerBalanceMs = 0.0;
        }
    }

    public synchronized float activeTimerSpeed() {
        Request best = null;
        for (Request request : requests.values()) {
            if (best == null
                    || request.priority > best.priority
                    || (request.priority == best.priority && request.sequence > best.sequence)) {
                best = request;
            }
        }
        return best != null ? best.timerSpeed : 1.0f;
    }

    private synchronized void tick() {
        Iterator<Map.Entry<Object, Request>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Request> entry = iterator.next();
            Request request = entry.getValue();
            request.remainingTicks--;
            if (request.remainingTicks <= 0) {
                iterator.remove();
            }
        }
    }

    private static final class Request {
        final float timerSpeed;
        final int priority;
        final long sequence;
        int remainingTicks;

        Request(float timerSpeed, int priority, int remainingTicks, long sequence) {
            this.timerSpeed = timerSpeed;
            this.priority = priority;
            this.remainingTicks = remainingTicks;
            this.sequence = sequence;
        }
    }
}
