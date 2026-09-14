/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.runtime;

import combatant.client.config.subsystem.DuplexLocalConfig;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.map.duplex.DuplexBearingSample;
import combatant.client.features.map.duplex.DuplexEstimate;
import combatant.client.features.map.duplex.DuplexRuntime;
import combatant.client.features.map.heuristic.HeuristicEstimate;
import combatant.client.features.map.heuristic.HeuristicRuntime;
import combatant.client.features.map.location.PlayerLocationService;
import combatant.client.features.map.location.PlayerLocationSnapshot;
import combatant.client.features.map.location.PlayerLocationSource;
import combatant.client.features.map.location.ServerIdentity;
import combatant.client.features.map.location.WorldIdentity;
import combatant.client.features.map.locator.LocatorObservation;
import combatant.client.features.map.locator.LocatorObservationType;
import combatant.client.features.map.locator.LocatorRuntime;
import combatant.client.features.map.storage.MapBearingFrame;
import combatant.client.features.map.storage.MapEstimateFrame;
import combatant.client.features.map.storage.MapExactPointFrame;
import combatant.client.features.map.storage.MapHistorySource;
import combatant.client.features.map.storage.MapHistoryStore;
import combatant.client.features.maplink.model.MapLinkObservation;
import combatant.client.features.maplink.model.MapLinkProfileState;
import combatant.client.features.maplink.model.MapLinkProfileStatus;
import combatant.client.features.maplink.model.MapLinkSnapshot;
import combatant.client.features.maplink.runtime.MapLinkRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main-thread coordinator for the location backend.
 *
 * <p>Mutable Minecraft state is captured here and converted into immutable location/history
 * records. MapLink exact points suppress single-client triangulation workload without being fed
 * into the solver. Storage/network/solver workers only receive immutable data.</p>
 */
public final class MapLocationRuntime {
    private static final MapLocationRuntime INSTANCE = new MapLocationRuntime();

    private final DuplexLocalConfig duplexConfig = DuplexLocalConfig.get();
    private final DuplexRuntime duplex = new DuplexRuntime();
    private final Map<UUID, Long> lastDuplexRevision = new HashMap<>();

    private String activeServerFingerprint = "";
    private String activeWorldFingerprint = "";
    private int activeDuplexConfigFingerprint;

    private MapLocationRuntime() {
    }

    public static MapLocationRuntime get() {
        return INSTANCE;
    }

    public DuplexRuntime duplex() {
        return duplex;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!usable(mc)) {
            reset();
            return;
        }

        String server = serverFingerprint(mc);
        String world = mc.level.dimension().identifier().toString();
        reconcileDuplex(server, world);

        MapLinkSnapshot mapLink = MapLinkRuntime.get().snapshot();
        Set<UUID> exactTargets = new HashSet<>(exactMapLinkTargets(mapLink, world));
        exactTargets.addAll(localExactTargets(mc));
        HeuristicRuntime.get().setSuppressedTargets(Set.copyOf(exactTargets));

        LocatorRuntime.get().capture(mc);
        List<LocatorObservation> locator = LocatorRuntime.get().snapshot();
        for (LocatorObservation observation : locator) {
            if (observation.targetUuid() != null && observation.type() == LocatorObservationType.EXACT_POSITION) {
                exactTargets.add(observation.targetUuid());
            }
        }
        Set<UUID> immutableExactTargets = Set.copyOf(exactTargets);
        HeuristicRuntime.get().setSuppressedTargets(immutableExactTargets);
        forwardDuplexBearings(locator, immutableExactTargets);
        duplex.tick();

        Map<UUID, HeuristicEstimate> heuristic = HeuristicRuntime.get().snapshot();
        Map<UUID, DuplexEstimate> duplexEstimates = duplex.estimates();

        Captured captured = captureLocations(mc, server, world, mapLink, locator, heuristic, duplexEstimates);
        PlayerLocationService.get().publish(captured.locations);
        persist(server, world, captured, heuristic, duplexEstimates);
    }

    public void shutdown() {
        reset();
        MapHistoryStore.get().shutdown();
    }

    private Captured captureLocations(Minecraft mc,
                                      String server,
                                      String currentWorld,
                                      MapLinkSnapshot mapLink,
                                      List<LocatorObservation> locator,
                                      Map<UUID, HeuristicEstimate> heuristic,
                                      Map<UUID, DuplexEstimate> duplexEstimates) {
        long now = System.currentTimeMillis();
        ServerIdentity serverIdentity = ServerIdentity.endpoint(server);
        WorldIdentity currentWorldIdentity = WorldIdentity.dimension(currentWorld);
        List<PlayerLocationSnapshot> locations = new ArrayList<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<String, UUID> idsByName = new HashMap<>();

        UUID self = mc.player.getUUID();
        for (Player player : mc.level.players()) {
            if (player == null || player.getUUID() == null || player.getUUID().equals(self)) continue;
            UUID id = player.getUUID();
            String name = player.getName() == null ? "" : player.getName().getString();
            rememberIdentity(names, idsByName, id, name);
            locations.add(new PlayerLocationSnapshot(id, name, serverIdentity, currentWorldIdentity,
                    PlayerLocationSource.LOCAL_ENTITY_EXACT, now, now,
                    player.getX(), player.getY(), player.getZ(), Double.NaN,
                    0.0, 0.0, 0.0, 1.0, "local_entity", now, Map.of()));
        }

        if (mapLink != null) {
            for (MapLinkObservation observation : mapLink.observations()) {
                UUID id = observation.resolvedUuid();
                if (id == null || !observation.worldMapped()) continue;
                MapLinkProfileState state = mapLink.profileStates().get(observation.profileId());
                if (state == null || state.status() != MapLinkProfileStatus.LIVE) continue;
                String name = observation.rawPlayerName();
                rememberIdentity(names, idsByName, id, name);
                long effective = observation.providerTimestamp() > 0L
                        ? observation.providerTimestamp() : observation.fetchTimestamp();
                locations.add(new PlayerLocationSnapshot(id, name, serverIdentity, WorldIdentity.dimension(observation.mappedWorld()),
                        PlayerLocationSource.MAPLINK_EXACT, observation.fetchTimestamp(), effective,
                        observation.x(), observation.y(), observation.z(), Double.NaN,
                        0.0, 0.0, 0.0, 1.0, observation.profileId(),
                        revisionHash(observation.profileId(), observation.revision(), effective),
                        Map.of("provider", observation.providerType().name(),
                                "providerWorld", clean(observation.providerWorld()))));
            }
        }

        if (locator != null) {
            for (LocatorObservation observation : locator) {
                UUID id = resolve(observation.targetUuid(), observation.targetName(), idsByName);
                if (id == null) continue;
                String name = !clean(observation.targetName()).isBlank()
                        ? clean(observation.targetName()) : names.getOrDefault(id, "");
                rememberIdentity(names, idsByName, id, name);

                if (observation.type() == LocatorObservationType.EXACT_POSITION
                        || observation.type() == LocatorObservationType.CHUNK_POSITION) {
                    boolean exact = observation.type() == LocatorObservationType.EXACT_POSITION;
                    locations.add(new PlayerLocationSnapshot(id, name, serverIdentity, currentWorldIdentity,
                            exact ? PlayerLocationSource.LOCATOR_EXACT : PlayerLocationSource.LOCATOR_APPROXIMATE,
                            observation.observedAtMs(), observation.observedAtMs(),
                            observation.x(), observation.y(), observation.z(), Double.NaN,
                            Math.max(0.0, observation.uncertaintyRadius()),
                            Math.max(0.0, observation.uncertaintyRadius()), 0.0,
                            exact ? 1.0 : 0.75, "locator", observation.sourceRevision(),
                            Map.of("type", observation.type().name())));
                } else if (observation.type() == LocatorObservationType.BEARING_ONLY) {
                    locations.add(new PlayerLocationSnapshot(id, name, serverIdentity, currentWorldIdentity,
                            PlayerLocationSource.LOCATOR_BEARING,
                            observation.observedAtMs(), observation.observedAtMs(),
                            Double.NaN, Double.NaN, Double.NaN, observation.bearingRadians(),
                            0.0, 0.0, 0.0, 0.25, "locator", observation.sourceRevision(),
                            Map.of("type", observation.type().name(),
                                    "observerX", Double.toString(observation.observerX()),
                                    "observerZ", Double.toString(observation.observerZ()))));
                }
            }
        }

        if (heuristic != null) {
            for (HeuristicEstimate estimate : heuristic.values()) {
                if (estimate == null || estimate.targetUuid() == null) continue;
                String name = names.getOrDefault(estimate.targetUuid(), "");
                locations.add(new PlayerLocationSnapshot(estimate.targetUuid(), name, serverIdentity, currentWorldIdentity,
                        PlayerLocationSource.TRIANGULATED, estimate.updatedAtMs(), estimate.updatedAtMs(),
                        estimate.x(), Double.NaN, estimate.z(), Double.NaN,
                        estimate.uncertaintyMajor(), estimate.uncertaintyMinor(),
                        estimate.uncertaintyAngleRadians(), estimate.confidence(),
                        "single_client", estimate.updatedAtMs(),
                        Map.of("samples", Integer.toString(estimate.sampleCount()),
                                "inliers", Integer.toString(estimate.inlierCount()),
                                "segment", Long.toString(estimate.segmentId()))));
            }
        }

        if (duplexEstimates != null) {
            for (DuplexEstimate estimate : duplexEstimates.values()) {
                if (estimate == null || estimate.targetUuid() == null) continue;
                String name = names.getOrDefault(estimate.targetUuid(), "");
                locations.add(new PlayerLocationSnapshot(estimate.targetUuid(), name, serverIdentity, currentWorldIdentity,
                        PlayerLocationSource.DUPLEX_TRIANGULATED,
                        estimate.observedAtMs(), estimate.observedAtMs(),
                        estimate.x(), Double.NaN, estimate.z(), Double.NaN,
                        estimate.uncertaintyRadius(), estimate.uncertaintyRadius(), 0.0,
                        estimate.confidence(), "duplex", estimate.sourceRevision(),
                        Map.of("crossingAngle", Double.toString(estimate.crossingAngleRadians()))));
            }
        }

        return new Captured(List.copyOf(locations), Map.copyOf(names), Map.copyOf(idsByName), mapLink, locator);
    }

    private void persist(String server,
                         String currentWorld,
                         Captured captured,
                         Map<UUID, HeuristicEstimate> heuristic,
                         Map<UUID, DuplexEstimate> duplexEstimates) {
        MapHistoryStore history = MapHistoryStore.get();

        for (PlayerLocationSnapshot snapshot : captured.locations) {
            if (snapshot.source() != PlayerLocationSource.LOCAL_ENTITY_EXACT) continue;
            if (!finite3(snapshot.x(), snapshot.y(), snapshot.z())) continue;
            history.offer(server, new MapExactPointFrame(snapshot.playerUuid(), snapshot.playerName(),
                    snapshot.worldKey(), MapHistorySource.LOCAL_ENTITY_EXACT, "local_entity",
                    snapshot.observedAtMs(), snapshot.sourceRevision(),
                    snapshot.x(), snapshot.y(), snapshot.z(), 0.0));
        }

        if (captured.mapLink != null) {
            for (MapLinkObservation observation : captured.mapLink.observations()) {
                UUID id = observation.resolvedUuid();
                if (id == null || !observation.worldMapped()) continue;
                MapLinkProfileState state = captured.mapLink.profileStates().get(observation.profileId());
                if (state == null || state.status() != MapLinkProfileStatus.LIVE) continue;
                if (!finite3(observation.x(), observation.y(), observation.z())) continue;
                long effective = observation.providerTimestamp() > 0L
                        ? observation.providerTimestamp() : observation.fetchTimestamp();
                history.offer(server, new MapExactPointFrame(id, observation.rawPlayerName(),
                        observation.mappedWorld(), MapHistorySource.MAPLINK_EXACT, observation.profileId(),
                        effective,
                        revisionHash(observation.profileId(), observation.revision(), effective),
                        observation.x(), observation.y(), observation.z(), 0.0));
            }
        }

        if (captured.locator != null) {
            for (LocatorObservation observation : captured.locator) {
                UUID id = resolve(observation.targetUuid(), observation.targetName(), captured.idsByName);
                if (id == null) continue;
                String name = !clean(observation.targetName()).isBlank()
                        ? clean(observation.targetName()) : captured.names.getOrDefault(id, "");
                switch (observation.type()) {
                    case EXACT_POSITION, CHUNK_POSITION -> {
                        if (!finite3(observation.x(), observation.y(), observation.z())) continue;
                        history.offer(server, new MapExactPointFrame(id, name, currentWorld,
                                    observation.type() == LocatorObservationType.EXACT_POSITION
                                            ? MapHistorySource.LOCATOR_EXACT
                                            : MapHistorySource.LOCATOR_APPROXIMATE,
                                    "locator", observation.observedAtMs(), observation.sourceRevision(),
                                    observation.x(), observation.y(), observation.z(),
                                    Math.max(0.0, observation.uncertaintyRadius())));
                    }
                    case BEARING_ONLY -> {
                        if (!finite3(observation.observerX(), observation.bearingRadians(), observation.observerZ())) continue;
                        history.offer(server, new MapBearingFrame(id, name, currentWorld, MapHistorySource.LOCATOR_BEARING,
                                    "locator", observation.observedAtMs(), observation.sourceRevision(),
                                    observation.observerX(), observation.observerZ(),
                                    observation.bearingRadians(), 1.0));
                    }
                    default -> {
                    }
                }
            }
        }

        if (heuristic != null) {
            for (HeuristicEstimate estimate : heuristic.values()) {
                if (estimate == null || estimate.targetUuid() == null) continue;
                if (!Double.isFinite(estimate.x()) || !Double.isFinite(estimate.z())) continue;
                history.offer(server, new MapEstimateFrame(estimate.targetUuid(),
                        captured.names.getOrDefault(estimate.targetUuid(), ""), currentWorld,
                        MapHistorySource.HEURISTIC_TRIANGULATED, "single_client",
                        estimate.updatedAtMs(), estimate.updatedAtMs(),
                        estimate.x(), estimate.z(), estimate.uncertaintyMajor(), estimate.uncertaintyMinor(),
                        estimate.uncertaintyAngleRadians(), estimate.residualRms(), estimate.confidence(),
                        estimate.sampleCount(), estimate.inlierCount(), estimate.segmentId()));
            }
        }

        if (duplexEstimates != null) {
            for (DuplexEstimate estimate : duplexEstimates.values()) {
                if (estimate == null || estimate.targetUuid() == null) continue;
                if (!Double.isFinite(estimate.x()) || !Double.isFinite(estimate.z())) continue;
                history.offer(server, new MapEstimateFrame(estimate.targetUuid(),
                        captured.names.getOrDefault(estimate.targetUuid(), ""), currentWorld,
                        MapHistorySource.DUPLEX_TRIANGULATED, "duplex",
                        estimate.observedAtMs(), estimate.sourceRevision(),
                        estimate.x(), estimate.z(), estimate.uncertaintyRadius(), estimate.uncertaintyRadius(),
                        0.0, 0.0, estimate.confidence(), 2, 2, estimate.sourceRevision()));
            }
        }
    }

    private static boolean finite3(double a, double b, double c) {
        return Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c);
    }

    private void forwardDuplexBearings(List<LocatorObservation> observations, Set<UUID> exactTargets) {
        Set<UUID> seen = new HashSet<>();
        for (LocatorObservation observation : observations) {
            UUID target = observation.targetUuid();
            if (target == null || observation.type() != LocatorObservationType.BEARING_ONLY
                    || exactTargets.contains(target)) {
                continue;
            }
            seen.add(target);
            long revision = observation.sourceRevision();
            Long previous = lastDuplexRevision.get(target);
            if (previous != null && previous == revision) continue;

            if (duplex.publishBearing(new DuplexBearingSample(
                    target,
                    observation.observerX(),
                    observation.observerZ(),
                    observation.bearingRadians(),
                    observation.observedAtMs(),
                    revision))) {
                lastDuplexRevision.put(target, revision);
            }
        }
        lastDuplexRevision.keySet().removeIf(id -> !seen.contains(id));
    }


    private static Set<UUID> localExactTargets(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null) return Set.of();
        UUID self = mc.player.getUUID();
        Set<UUID> result = new HashSet<>();
        for (Player player : mc.level.players()) {
            if (player == null || player.getUUID() == null || player.getUUID().equals(self)) continue;
            result.add(player.getUUID());
        }
        return result;
    }

    private Set<UUID> exactMapLinkTargets(MapLinkSnapshot snapshot, String world) {
        if (snapshot == null || snapshot.observations().isEmpty()) return Set.of();
        Set<UUID> result = new HashSet<>();
        for (MapLinkObservation observation : snapshot.observations()) {
            UUID target = observation.resolvedUuid();
            if (target == null || !observation.worldMapped() || !world.equals(observation.mappedWorld())) continue;
            MapLinkProfileState state = snapshot.profileStates().get(observation.profileId());
            if (state == null || state.status() != MapLinkProfileStatus.LIVE) continue;
            result.add(target);
        }
        return Set.copyOf(result);
    }

    private void reconcileDuplex(String server, String world) {
        if (!duplexConfig.enabled()) {
            if (!activeServerFingerprint.isEmpty() || !activeWorldFingerprint.isEmpty()) duplex.close();
            activeServerFingerprint = "";
            activeWorldFingerprint = "";
            activeDuplexConfigFingerprint = 0;
            lastDuplexRevision.clear();
            return;
        }
        int configFingerprint = duplexConfig.runtimeFingerprint();
        if (server.equals(activeServerFingerprint) && world.equals(activeWorldFingerprint)
                && configFingerprint == activeDuplexConfigFingerprint) return;
        activeServerFingerprint = server;
        activeWorldFingerprint = world;
        activeDuplexConfigFingerprint = configFingerprint;
        lastDuplexRevision.clear();
        duplex.start(server, world);
    }

    private void reset() {
        LocatorRuntime.get().clear();
        HeuristicRuntime.get().setSuppressedTargets(Set.of());
        PlayerLocationService.get().clear();
        duplex.close();
        activeServerFingerprint = "";
        activeWorldFingerprint = "";
        activeDuplexConfigFingerprint = 0;
        lastDuplexRevision.clear();
    }

    private static boolean usable(Minecraft mc) {
        return mc != null && mc.player != null && mc.level != null && mc.getConnection() != null
                && !mc.hasSingleplayerServer() && mc.getCurrentServer() != null;
    }

    private static String serverFingerprint(Minecraft mc) {
        ServerData server = mc.getCurrentServer();
        String address = server == null || server.ip == null ? "" : server.ip.trim();
        return address.toLowerCase(Locale.ROOT);
    }

    private static void rememberIdentity(Map<UUID, String> names, Map<String, UUID> idsByName,
                                         UUID id, String name) {
        if (id == null) return;
        String cleaned = clean(name);
        if (!cleaned.isBlank()) {
            names.put(id, cleaned);
            idsByName.put(cleaned.toLowerCase(Locale.ROOT), id);
        } else {
            names.putIfAbsent(id, "");
        }
    }

    private static UUID resolve(UUID direct, String name, Map<String, UUID> idsByName) {
        if (direct != null) return direct;
        String cleaned = clean(name);
        return cleaned.isBlank() ? null : idsByName.get(cleaned.toLowerCase(Locale.ROOT));
    }

    private static long revisionHash(String profile, String revision, long effectiveTimestamp) {
        long h = 0xcbf29ce484222325L;
        h = mix(h, clean(profile).hashCode());
        h = mix(h, clean(revision).hashCode());
        h = mix(h, effectiveTimestamp);
        return h;
    }

    private static long mix(long h, long value) {
        return (h ^ value) * 0x100000001b3L;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record Captured(
            List<PlayerLocationSnapshot> locations,
            Map<UUID, String> names,
            Map<String, UUID> idsByName,
            MapLinkSnapshot mapLink,
            List<LocatorObservation> locator
    ) {
    }
}
