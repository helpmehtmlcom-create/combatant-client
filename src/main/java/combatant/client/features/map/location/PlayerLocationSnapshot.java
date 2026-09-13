/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.location;

import java.util.Map;
import java.util.UUID;

/** GUI-neutral immutable location domain record. NaN coordinates mean the source is bearing-only. */
public record PlayerLocationSnapshot(
        UUID playerUuid,
        String playerName,
        ServerIdentity serverIdentity,
        WorldIdentity worldIdentity,
        PlayerLocationSource source,
        long observedAtMs,
        long effectiveAtMs,
        double x,
        double y,
        double z,
        double bearingRadians,
        double uncertaintyMajor,
        double uncertaintyMinor,
        double uncertaintyAngleRadians,
        double confidence,
        String sourceKey,
        long sourceRevision,
        Map<String, String> metadata
) {
    public PlayerLocationSnapshot {
        if (playerUuid == null) throw new IllegalArgumentException("playerUuid");
        playerName = clean(playerName);
        if (serverIdentity == null) throw new IllegalArgumentException("serverIdentity");
        if (worldIdentity == null) throw new IllegalArgumentException("worldIdentity");
        if (source == null) throw new IllegalArgumentException("source");
        sourceKey = clean(sourceKey);
        uncertaintyMajor = finiteNonNegative(uncertaintyMajor);
        uncertaintyMinor = finiteNonNegative(uncertaintyMinor);
        uncertaintyAngleRadians = finite(uncertaintyAngleRadians);
        confidence = clamp01(confidence);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String serverKey() {
        return serverIdentity.stableKey();
    }

    public String worldKey() {
        return worldIdentity.stableKey();
    }

    public boolean hasPosition() {
        return Double.isFinite(x) && Double.isFinite(z);
    }

    public boolean hasBearing() {
        return Double.isFinite(bearingRadians);
    }

    public boolean exact() {
        return source.exact() && hasPosition();
    }

    public long ageMs(long now) {
        return Math.max(0L, now - observedAtMs);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static double finite(double value) { return Double.isFinite(value) ? value : 0.0; }
    private static double finiteNonNegative(double value) { return Math.max(0.0, finite(value)); }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, finite(value))); }
}
