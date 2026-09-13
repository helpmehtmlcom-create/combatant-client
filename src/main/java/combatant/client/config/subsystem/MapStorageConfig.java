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
import combatant.client.features.map.storage.MapHistoryRetentionMode;

import java.util.List;

@ConfigSubsystem(value = "map/storage", settingOwner = "map_storage")
public final class MapStorageConfig extends SubsystemConfig {
    public static final MapStorageConfig INSTANCE = new MapStorageConfig();

    private final BooleanValue enabled = bool("enabled", true);
    private final EnumValue<MapHistoryRetentionMode> retentionMode =
            enumValue("retentionMode", MapHistoryRetentionMode.ESTIMATES_ONLY, MapHistoryRetentionMode.class);

    private final NumberValue<Integer> chunkRecords = number("chunkRecords", 64, 8, 512);
    private final NumberValue<Integer> flushIntervalMs = number("flushIntervalMs", 2500, 250, 30000);
    private final NumberValue<Integer> maxFileSizeMb = number("maxFileSizeMb", 128, 8, 2048);
    private final NumberValue<Integer> maxHistoryDays = number("maxHistoryDays", 30, 1, 3650);
    private final NumberValue<Integer> maxTotalStorageMb = number("maxTotalStorageMb", 2048, 64, 32768);

    private final NumberValue<Double> estimateCenterChange = number("estimateCenterChange", 4.0, 0.0, 256.0);
    private final NumberValue<Double> estimateUncertaintyImprovePct =
            number("estimateUncertaintyImprovePct", 0.08, 0.0, 1.0);
    private final NumberValue<Double> estimateConfidenceChange =
            number("estimateConfidenceChange", 0.08, 0.0, 1.0);
    private final NumberValue<Integer> estimateMaxIntervalMs =
            number("estimateMaxIntervalMs", 60000, 1000, 900000);

    private final NumberValue<Double> exactPositionChange =
            number("exactPositionChange", 1.0, 0.0, 64.0);
    private final NumberValue<Integer> exactMaxIntervalMs =
            number("exactMaxIntervalMs", 30000, 1000, 900000);

    private final BooleanValue storeHeuristic = bool("storeHeuristic", true);
    private final BooleanValue storeDuplex = bool("storeDuplex", true);
    private final BooleanValue storeMapLinkExact = bool("storeMapLinkExact", true);
    private final BooleanValue storeLocatorExact = bool("storeLocatorExact", true);
    private final BooleanValue storeLocalExact = bool("storeLocalExact", false);

    private MapStorageConfig() {
        loadConfig();
    }

    public static MapStorageConfig get() { return INSTANCE; }
    public boolean enabled() { return enabled.get(); }
    public MapHistoryRetentionMode retentionMode() { return retentionMode.get(); }
    public int chunkRecords() { return chunkRecords.get().intValue(); }
    public int flushIntervalMs() { return flushIntervalMs.get().intValue(); }
    public long maxFileSizeBytes() { return maxFileSizeMb.get().longValue() * 1024L * 1024L; }
    public int maxHistoryDays() { return maxHistoryDays.get().intValue(); }
    public long maxTotalStorageBytes() { return maxTotalStorageMb.get().longValue() * 1024L * 1024L; }
    public double estimateCenterChange() { return estimateCenterChange.get().doubleValue(); }
    public double estimateUncertaintyImprovePct() { return estimateUncertaintyImprovePct.get().doubleValue(); }
    public double estimateConfidenceChange() { return estimateConfidenceChange.get().doubleValue(); }
    public int estimateMaxIntervalMs() { return estimateMaxIntervalMs.get().intValue(); }
    public double exactPositionChange() { return exactPositionChange.get().doubleValue(); }
    public int exactMaxIntervalMs() { return exactMaxIntervalMs.get().intValue(); }
    public boolean storeHeuristic() { return storeHeuristic.get(); }
    public boolean storeDuplex() { return storeDuplex.get(); }
    public boolean storeMapLinkExact() { return storeMapLinkExact.get(); }
    public boolean storeLocatorExact() { return storeLocatorExact.get(); }
    public boolean storeLocalExact() { return storeLocalExact.get(); }

    @Override
    public List<SettingDef> getSettingDefs() {
        return settings(
                SettingDef.bool("enabled", enabled),
                SettingDef.mode("retentionMode", retentionMode),
                SettingDef.number("chunkRecords", chunkRecords),
                SettingDef.number("flushIntervalMs", flushIntervalMs),
                SettingDef.number("maxFileSizeMb", maxFileSizeMb),
                SettingDef.number("maxHistoryDays", maxHistoryDays),
                SettingDef.number("maxTotalStorageMb", maxTotalStorageMb),
                SettingDef.number("estimateCenterChange", estimateCenterChange),
                SettingDef.number("estimateUncertaintyImprovePct", estimateUncertaintyImprovePct),
                SettingDef.number("estimateConfidenceChange", estimateConfidenceChange),
                SettingDef.number("estimateMaxIntervalMs", estimateMaxIntervalMs),
                SettingDef.number("exactPositionChange", exactPositionChange),
                SettingDef.number("exactMaxIntervalMs", exactMaxIntervalMs),
                SettingDef.bool("storeHeuristic", storeHeuristic),
                SettingDef.bool("storeDuplex", storeDuplex),
                SettingDef.bool("storeMapLinkExact", storeMapLinkExact),
                SettingDef.bool("storeLocatorExact", storeLocatorExact),
                SettingDef.bool("storeLocalExact", storeLocalExact)
        );
    }
}
