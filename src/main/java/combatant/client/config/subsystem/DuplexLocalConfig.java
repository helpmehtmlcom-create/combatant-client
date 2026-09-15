/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.map.duplex.DuplexRole;

import java.util.List;

/**
 * Local-only DUPLEX IPC configuration.
 *
 * <p>The Combatant side always owns the PRIMARY endpoint on a fixed loopback port. The standalone
 * courier is always SECONDARY, so neither artifact needs a second role/port/token configuration
 * that can drift out of sync.</p>
 */
@ConfigSubsystem(value = "map/duplex", settingOwner = "duplex")
public final class DuplexLocalConfig extends SubsystemConfig {
    public static final DuplexLocalConfig INSTANCE = new DuplexLocalConfig();
    public static final int COURIER_PORT = 28464;

    private final BooleanValue enabled = bool("enabled", false);
    private final NumberValue<Integer> reconnectMs = number("reconnectMs", 1000, 100, 10000);
    private final NumberValue<Integer> heartbeatMs = number("heartbeatMs", 2000, 250, 60000);
    private final NumberValue<Integer> samplePairToleranceMs = number("samplePairToleranceMs", 750, 25, 10000);
    private final NumberValue<Double> minCrossingAngleDegrees = number("minCrossingAngleDegrees", 5.0, 0.5, 45.0);
    private final NumberValue<Double> bearingNoiseDegrees = number("bearingNoiseDegrees", 0.25, 0.01, 5.0);
    private final NumberValue<Integer> estimateStaleMs = number("estimateStaleMs", 15000, 1000, 120000);

    private DuplexLocalConfig() {
        loadConfig();
    }

    public static DuplexLocalConfig get() { return INSTANCE; }
    public BooleanValue enabledValue() { return enabled; }
    public boolean enabled() { return enabled.get(); }
    public DuplexRole role() { return DuplexRole.PRIMARY; }
    public int port() { return COURIER_PORT; }
    public String sessionToken() { return ""; }
    public int reconnectMs() { return reconnectMs.get().intValue(); }
    public int heartbeatMs() { return heartbeatMs.get().intValue(); }
    public int samplePairToleranceMs() { return samplePairToleranceMs.get().intValue(); }
    public double minCrossingAngleRadians() { return Math.toRadians(minCrossingAngleDegrees.get().doubleValue()); }
    public double bearingNoiseRadians() { return Math.toRadians(bearingNoiseDegrees.get().doubleValue()); }
    public int estimateStaleMs() { return estimateStaleMs.get().intValue(); }

    /** Values that require a new local IPC/session contract when changed. */
    public int runtimeFingerprint() {
        return java.util.Objects.hash(reconnectMs.get(), heartbeatMs.get(),
                samplePairToleranceMs.get(), minCrossingAngleDegrees.get(), bearingNoiseDegrees.get(), estimateStaleMs.get());
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return List.of(
                SettingDef.bool("enabled", enabled),
                SettingDef.number("reconnectMs", reconnectMs),
                SettingDef.number("heartbeatMs", heartbeatMs),
                SettingDef.number("samplePairToleranceMs", samplePairToleranceMs),
                SettingDef.number("minCrossingAngleDegrees", minCrossingAngleDegrees),
                SettingDef.number("bearingNoiseDegrees", bearingNoiseDegrees),
                SettingDef.number("estimateStaleMs", estimateStaleMs)
        );
    }
}
