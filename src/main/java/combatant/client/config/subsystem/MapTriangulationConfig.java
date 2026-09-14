/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.SetValue;
import combatant.client.features.map.heuristic.MapTriangulationMode;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@ConfigSubsystem(value = "map/triangulation", settingOwner = "map_triangulation")
public final class MapTriangulationConfig extends SubsystemConfig {
    public static final MapTriangulationConfig INSTANCE = new MapTriangulationConfig();

    private final EnumValue<MapTriangulationMode> mode =
            enumValue("mode", MapTriangulationMode.DATA_MINING, MapTriangulationMode.class);
    private final SetValue targetedPlayers = stringSet("targetedPlayers");
    private final NumberValue<Integer> dataMiningMinSolveIntervalMs =
            number("dataMiningMinSolveIntervalMs", 700, 0, 10000);
    private final NumberValue<Integer> targetedMinSolveIntervalMs =
            number("targetedMinSolveIntervalMs", 100, 0, 5000);
    private final NumberValue<Integer> dataMiningMaxActiveTargets =
            number("dataMiningMaxActiveTargets", 128, 1, 1024);

    private MapTriangulationConfig() {
        loadConfig();
    }

    public static MapTriangulationConfig get() { return INSTANCE; }
    public MapTriangulationMode mode() { return mode.get(); }
    public void setMode(MapTriangulationMode next) {
        mode.set(next == null ? MapTriangulationMode.OFF : next);
        saveConfig();
    }
    public int minSolveIntervalMs() {
        return mode() == MapTriangulationMode.TARGETED
                ? targetedMinSolveIntervalMs.get().intValue()
                : dataMiningMinSolveIntervalMs.get().intValue();
    }
    public int dataMiningMaxActiveTargets() { return dataMiningMaxActiveTargets.get().intValue(); }

    public boolean accepts(UUID uuid) {
        if (uuid == null) return false;
        return switch (mode()) {
            case OFF -> false;
            case DATA_MINING -> true;
            case TARGETED -> isTargeted(uuid);
        };
    }

    public boolean isTargeted(UUID uuid) {
        if (uuid == null) return false;
        String canonical = uuid.toString().toLowerCase(Locale.ROOT);
        Set<String> values = targetedPlayers.get();
        if (values == null || values.isEmpty()) return false;
        for (String raw : values) {
            if (raw != null && canonical.equals(raw.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public SetValue targetedPlayersValue() { return targetedPlayers; }

    public void setTargetedPlayers(Collection<UUID> players) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (players != null) {
            for (UUID player : players) {
                if (player != null) values.add(player.toString().toLowerCase(Locale.ROOT));
            }
        }
        targetedPlayers.set(values);
        saveConfig();
    }

    public void addTargetedPlayer(UUID player) {
        if (player == null) return;
        LinkedHashSet<String> values = new LinkedHashSet<>(targetedPlayers.get());
        if (values.add(player.toString().toLowerCase(Locale.ROOT))) {
            targetedPlayers.set(values);
            saveConfig();
        }
    }

    public void removeTargetedPlayer(UUID player) {
        if (player == null) return;
        String canonical = player.toString().toLowerCase(Locale.ROOT);
        LinkedHashSet<String> values = new LinkedHashSet<>(targetedPlayers.get());
        if (values.removeIf(value -> value != null && canonical.equals(value.trim().toLowerCase(Locale.ROOT)))) {
            targetedPlayers.set(values);
            saveConfig();
        }
    }


    @Override
    public List<SettingDef> getSettingDefs() {
        return settings(
                SettingDef.mode("mode", mode),
                SettingDef.textList("targetedPlayers", targetedPlayers)
                        .visibleWhen(() -> mode() == MapTriangulationMode.TARGETED),
                SettingDef.number("dataMiningMinSolveIntervalMs", dataMiningMinSolveIntervalMs)
                        .visibleWhen(() -> mode() == MapTriangulationMode.DATA_MINING),
                SettingDef.number("targetedMinSolveIntervalMs", targetedMinSolveIntervalMs)
                        .visibleWhen(() -> mode() == MapTriangulationMode.TARGETED),
                SettingDef.number("dataMiningMaxActiveTargets", dataMiningMaxActiveTargets)
                        .visibleWhen(() -> mode() == MapTriangulationMode.DATA_MINING)
        );
    }
}
