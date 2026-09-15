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
import combatant.client.util.text.ChatNameUtil;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ConfigSubsystem(value = "map/triangulation", settingOwner = "map_triangulation")
public final class MapTriangulationConfig extends SubsystemConfig {
    public static final MapTriangulationConfig INSTANCE = new MapTriangulationConfig();

    private final EnumValue<MapTriangulationMode> mode =
            enumValue("mode", MapTriangulationMode.DATA_MINING, MapTriangulationMode.class);
    private final SetValue targetedPlayers = stringSet("targetedPlayers");
    private final SetValue targetedPlayerNames = stringSet("targetedPlayerNames");
    private final NumberValue<Integer> dataMiningMinSolveIntervalMs =
            number("dataMiningMinSolveIntervalMs", 700, 0, 10000);
    private final NumberValue<Integer> targetedMinSolveIntervalMs =
            number("targetedMinSolveIntervalMs", 100, 0, 5000);
    private final NumberValue<Integer> dataMiningMaxActiveTargets =
            number("dataMiningMaxActiveTargets", 128, 1, 1024);
    private final ConcurrentHashMap<UUID, String> resolvedTargetNames = new ConcurrentHashMap<>();

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

    public boolean accepts(UUID uuid, String name) {
        if (uuid == null) return false;
        return switch (mode()) {
            case OFF -> false;
            case DATA_MINING -> true;
            case TARGETED -> isTargeted(uuid, name);
        };
    }

    public boolean isTargeted(UUID uuid) {
        if (uuid == null) return false;
        if (containsUuid(uuid)) return true;
        String remembered = resolvedTargetNames.get(uuid);
        return remembered != null && isTargetedName(remembered);
    }

    public boolean isTargeted(UUID uuid, String name) {
        if (uuid != null && containsUuid(uuid)) return true;
        String clean = cleanDisplayName(name);
        if (!clean.isEmpty() && isTargetedName(clean)) {
            if (uuid != null) resolvedTargetNames.put(uuid, clean);
            return true;
        }
        return uuid != null && isTargeted(uuid);
    }

    public boolean isTargetedName(String name) {
        String normalized = normalizeTargetName(name);
        if (normalized.isEmpty()) return false;
        Set<String> values = targetedPlayerNames.get();
        if (values == null || values.isEmpty()) return false;
        for (String raw : values) {
            if (normalized.equals(normalizeTargetName(raw))) return true;
        }
        return false;
    }

    public String nameForTarget(UUID uuid) {
        if (uuid == null) return "";
        String remembered = resolvedTargetNames.get(uuid);
        if (remembered != null && !remembered.isBlank()) return remembered;
        Set<String> names = targetedPlayerNames.get();
        if (names != null) {
            for (String raw : names) {
                String clean = cleanDisplayName(raw);
                if (!clean.isEmpty() && offlineTargetUuid(clean).equals(uuid)) return clean;
            }
        }
        return "";
    }

    public SetValue targetedPlayersValue() { return targetedPlayers; }
    public SetValue targetedPlayerNamesValue() { return targetedPlayerNames; }

    public int targetedCount() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        Set<String> names = targetedPlayerNames.get();
        if (names != null) {
            for (String raw : names) {
                String normalized = normalizeTargetName(raw);
                if (!normalized.isEmpty()) unique.add("name:" + normalized);
            }
        }
        Set<String> ids = targetedPlayers.get();
        if (ids != null) {
            for (String raw : ids) {
                if (raw == null || raw.isBlank()) continue;
                unique.add("uuid:" + raw.trim().toLowerCase(Locale.ROOT));
            }
        }
        return unique.size();
    }

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
        addTargetedPlayer(player, "");
    }

    public void addTargetedPlayer(UUID player, String name) {
        boolean changed = false;
        if (player != null) {
            LinkedHashSet<String> values = new LinkedHashSet<>(safeSet(targetedPlayers.get()));
            if (values.add(player.toString().toLowerCase(Locale.ROOT))) {
                targetedPlayers.set(values);
                changed = true;
            }
        }
        String cleanName = cleanDisplayName(name);
        if (!cleanName.isEmpty()) {
            LinkedHashSet<String> names = new LinkedHashSet<>(safeSet(targetedPlayerNames.get()));
            if (addIgnoreCase(names, cleanName)) {
                targetedPlayerNames.set(names);
                changed = true;
            }
            if (player != null) resolvedTargetNames.put(player, cleanName);
        }
        if (changed) saveConfig();
    }

    public boolean addTargetedName(String name) {
        String clean = cleanDisplayName(name);
        if (clean.isEmpty() || !ChatNameUtil.isNickLike(clean)) return false;
        LinkedHashSet<String> values = new LinkedHashSet<>(safeSet(targetedPlayerNames.get()));
        if (!addIgnoreCase(values, clean)) return false;
        targetedPlayerNames.set(values);
        saveConfig();
        return true;
    }

    public void removeTargetedPlayer(UUID player) {
        removeTargetedPlayer(player, "");
    }

    public void removeTargetedPlayer(UUID player, String name) {
        boolean changed = false;
        if (player != null) {
            String canonical = player.toString().toLowerCase(Locale.ROOT);
            LinkedHashSet<String> values = new LinkedHashSet<>(safeSet(targetedPlayers.get()));
            if (values.removeIf(value -> value != null && canonical.equals(value.trim().toLowerCase(Locale.ROOT)))) {
                targetedPlayers.set(values);
                changed = true;
            }
            resolvedTargetNames.remove(player);
        }
        String normalized = normalizeTargetName(name);
        if (!normalized.isEmpty()) {
            LinkedHashSet<String> names = new LinkedHashSet<>(safeSet(targetedPlayerNames.get()));
            if (names.removeIf(value -> normalized.equals(normalizeTargetName(value)))) {
                targetedPlayerNames.set(names);
                changed = true;
            }
            resolvedTargetNames.entrySet().removeIf(entry -> normalized.equals(normalizeTargetName(entry.getValue())));
        }
        if (changed) saveConfig();
    }

    public static UUID offlineTargetUuid(String name) {
        String normalized = normalizeTargetName(name);
        return UUID.nameUUIDFromBytes(("CombatantTarget:" + normalized).getBytes(StandardCharsets.UTF_8));
    }

    /** Runtime-only identity used before a name can be resolved to an authoritative UUID. */
    public static boolean isOfflineTargetUuid(UUID uuid, String name) {
        if (uuid == null) return false;
        String clean = cleanDisplayName(name);
        return !clean.isEmpty() && offlineTargetUuid(clean).equals(uuid);
    }

    public boolean isOfflineTargetUuid(UUID uuid) {
        if (uuid == null) return false;
        String known = nameForTarget(uuid);
        return !known.isBlank() && isOfflineTargetUuid(uuid, known);
    }

    private boolean containsUuid(UUID uuid) {
        String canonical = uuid.toString().toLowerCase(Locale.ROOT);
        Set<String> values = targetedPlayers.get();
        if (values == null || values.isEmpty()) return false;
        for (String raw : values) {
            if (raw != null && canonical.equals(raw.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String cleanDisplayName(String name) {
        String clean = ChatNameUtil.normalizeNickCandidate(name);
        return ChatNameUtil.isNickLike(clean) ? clean : "";
    }

    private static String normalizeTargetName(String name) {
        String clean = ChatNameUtil.normalizeNickCandidate(name);
        return clean.isEmpty() ? "" : clean.toLowerCase(Locale.ROOT);
    }

    private static Set<String> safeSet(Set<String> values) {
        return values == null ? Set.of() : values;
    }

    private static boolean addIgnoreCase(Set<String> values, String name) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(name)) return false;
        }
        return values.add(name);
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return settings(
                SettingDef.mode("mode", mode),
                SettingDef.textList("targetedPlayers", targetedPlayers)
                        .visibleWhen(() -> mode() == MapTriangulationMode.TARGETED),
                SettingDef.textList("targetedPlayerNames", targetedPlayerNames)
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
