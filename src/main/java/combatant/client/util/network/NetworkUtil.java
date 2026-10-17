/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.network;

import combatant.client.util.player.NetworkStatsUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe network latency, RTT, jitter, and TPS tracking utility,
 * along with safe client execution and packet transmission helpers.
 */
public final class NetworkUtil {

    private static final double EMA_ALPHA = 0.15; // Exponential Moving Average smoothing factor
    private static final double JITTER_ALPHA = 0.10;

    private static final AtomicReference<Double> EMA_RTT = new AtomicReference<>(0.0);
    private static final AtomicReference<Double> EMA_JITTER = new AtomicReference<>(0.0);
    private static final AtomicLong LAST_RTT_SAMPLE_TIME = new AtomicLong(0L);
    private static final AtomicLong LAST_PACKET_TIME = new AtomicLong(0L);

    private NetworkUtil() {}

    /**
     * Records an RTT (Round Trip Time) sample in milliseconds and updates EMA and jitter.
     *
     * @param rttMs measured round-trip time in milliseconds
     */
    public static void recordRtt(double rttMs) {
        if (rttMs < 0.0 || Double.isNaN(rttMs) || Double.isInfinite(rttMs)) return;

        double prevRtt = EMA_RTT.getAndUpdate(current -> {
            if (current <= 0.0) return rttMs;
            return current * (1.0 - EMA_ALPHA) + rttMs * EMA_ALPHA;
        });

        if (prevRtt > 0.0) {
            double diff = Math.abs(rttMs - prevRtt);
            EMA_JITTER.updateAndGet(currJitter -> {
                if (currJitter <= 0.0) return diff;
                return currJitter * (1.0 - JITTER_ALPHA) + diff * JITTER_ALPHA;
            });
        }

        LAST_RTT_SAMPLE_TIME.set(System.currentTimeMillis());
    }

    /**
     * Returns the Exponential Moving Average (EMA) of RTT in milliseconds.
     *
     * @return current smoothed RTT in ms, or fallback to player ping
     */
    public static double getEmaRtt() {
        double rtt = EMA_RTT.get();
        if (rtt > 0.0) {
            return rtt;
        }
        Minecraft mc = Minecraft.getInstance();
        int ping = NetworkStatsUtil.getPing(mc);
        return ping >= 0 ? (double) ping : 0.0;
    }

    /**
     * Returns the Exponential Moving Average of latency jitter in milliseconds.
     *
     * @return estimated jitter in ms
     */
    public static double getJitterMs() {
        return EMA_JITTER.get();
    }

    /**
     * Returns the estimated server TPS (Ticks Per Second).
     *
     * @return estimated server TPS in range [0.0, 20.0]
     */
    public static float getEstimatedTps() {
        Minecraft mc = Minecraft.getInstance();
        return NetworkStatsUtil.getTps(mc);
    }

    /**
     * Returns the estimated server tick time in milliseconds (1000.0 / TPS).
     *
     * @return estimated millisecond duration of one server tick
     */
    public static double getTickTimeMs() {
        float tps = getEstimatedTps();
        if (tps <= 0.001f || Float.isNaN(tps)) {
            return 50.0; // Standard 20 TPS baseline (50ms per tick)
        }
        return Math.max(50.0, 1000.0 / tps);
    }

    /**
     * Sends a packet to the server safely with null-checks and connection state validation.
     *
     * @param packet the packet to send
     * @return true if the packet was successfully queued to the connection, false otherwise
     */
    public static boolean sendPacket(Packet<?> packet) {
        if (packet == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;

        ClientPacketListener listener = mc.getConnection();
        if (listener == null) return false;

        Connection connection = listener.getConnection();
        if (connection == null || !connection.isConnected()) return false;

        try {
            connection.send(packet);
            LAST_PACKET_TIME.set(System.currentTimeMillis());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Safely executes an action or packet handling task on the main client render/tick thread.
     *
     * @param action runnable to execute
     */
    public static void executeOnClient(Runnable action) {
        if (action == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        if (mc.isSameThread()) {
            action.run();
        } else {
            mc.execute(action);
        }
    }

    /**
     * Returns the timestamp in epoch milliseconds when the last packet was sent through {@link #sendPacket}.
     */
    public static long getLastPacketTime() {
        return LAST_PACKET_TIME.get();
    }
}
