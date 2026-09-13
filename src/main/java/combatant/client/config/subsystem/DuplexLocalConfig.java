/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.map.duplex.DuplexRole;

import java.util.List;

/**
 * Local-only DUPLEX IPC configuration.
 *
 * <p>The transport never exposes a configurable host: PRIMARY binds 127.0.0.1 and SECONDARY
 * connects to 127.0.0.1. The optional session token only authenticates another local process;
 * an empty token is valid because the socket is not reachable from external interfaces.</p>
 */
@ConfigSubsystem(value = "map/duplex", settingOwner = "duplex")
public final class DuplexLocalConfig extends SubsystemConfig {
    public static final DuplexLocalConfig INSTANCE = new DuplexLocalConfig();

    private final BooleanValue enabled = bool("enabled", false);
    private final EnumValue<DuplexRole> role = enumValue("role", DuplexRole.PRIMARY, DuplexRole.class);
    private final NumberValue<Integer> port = number("port", 28464, 1024, 65535);
    private final StringValue sessionToken = value(new StringValue("sessionToken", ""));
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
    public boolean enabled() { return enabled.get(); }
    public DuplexRole role() { return role.get(); }
    public int port() { return port.get().intValue(); }
    public String sessionToken() { return sessionToken.get(); }
    public int reconnectMs() { return reconnectMs.get().intValue(); }
    public int heartbeatMs() { return heartbeatMs.get().intValue(); }
    public int samplePairToleranceMs() { return samplePairToleranceMs.get().intValue(); }
    public double minCrossingAngleRadians() { return Math.toRadians(minCrossingAngleDegrees.get().doubleValue()); }
    public double bearingNoiseRadians() { return Math.toRadians(bearingNoiseDegrees.get().doubleValue()); }
    public int estimateStaleMs() { return estimateStaleMs.get().intValue(); }

    /** Values that require a new local IPC/session contract when changed. */
    public int runtimeFingerprint() {
        return java.util.Objects.hash(role.get(), port.get(), sessionToken.get(), reconnectMs.get(), heartbeatMs.get(),
                samplePairToleranceMs.get(), minCrossingAngleDegrees.get(), bearingNoiseDegrees.get(), estimateStaleMs.get());
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return List.of(
                SettingDef.bool("enabled", enabled),
                SettingDef.mode("role", role),
                SettingDef.number("port", port),
                SettingDef.text("sessionToken", sessionToken),
                SettingDef.number("reconnectMs", reconnectMs),
                SettingDef.number("heartbeatMs", heartbeatMs),
                SettingDef.number("samplePairToleranceMs", samplePairToleranceMs),
                SettingDef.number("minCrossingAngleDegrees", minCrossingAngleDegrees),
                SettingDef.number("bearingNoiseDegrees", bearingNoiseDegrees),
                SettingDef.number("estimateStaleMs", estimateStaleMs)
        );
    }
}
