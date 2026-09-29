/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ModuleInfo(
        id = "logoutspots",
        displayName = "LogoutSpots",
        aliases = {"CombatLogESP", "LoggedPlayers"},
        category = ModuleCategory.VISUALS,
        subcategory = ModuleSubcategory.ESP,
        description = "module.logoutspots.description")
public final class LogoutSpots extends Module {
    private static final int MAX_ENTRIES = 128;

    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue box = bool("logoutSpotsBox", "box", true);
    private final BooleanValue tracer = bool("logoutSpotsTracer", "tracer", true);
    private final NumberValue<Double> maxRange = num("logoutSpotsMaxRange", "max_range", 200.0, 20.0, 1000.0);
    private final NumberValue<Integer> ttlSeconds = num("logoutSpotsTtlSeconds", "ttl_seconds", 300, 10, 3600);
    private final RGBAColorValue fillColor = color("logoutSpotsFillColor", "fill_color", "#FF000044");
    private final RGBAColorValue lineColor = color("logoutSpotsLineColor", "line_color", "#FF0000FF");

    private final Map<UUID, PlayerSnapshot> tracked = new HashMap<>();
    private final Map<UUID, LogoutEntry> spots = new HashMap<>();
    private String identity = "";

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer local = mc.player;
        if (local == null || mc.level == null) {
            reset();
            return;
        }

        String currentIdentity = identity();
        if (!currentIdentity.equals(identity)) {
            tracked.clear();
            spots.clear();
            identity = currentIdentity;
        }

        long now = System.currentTimeMillis();
        long ttlMs = ttlSeconds.get() * 1000L;
        spots.values().removeIf(entry -> now - entry.timestamp() > ttlMs);

        Set<UUID> present = new HashSet<>();
        for (Player player : mc.level.players()) {
            if (player == local) continue;

            UUID id = player.getUUID();
            present.add(id);
            spots.remove(id);
            tracked.put(id, new PlayerSnapshot(
                    player.getName().getString(),
                    player.position(),
                    player.getHealth() + player.getAbsorptionAmount(),
                    player.isDeadOrDying()
            ));
        }

        double maxRangeSq = maxRange.get() * maxRange.get();
        Iterator<Map.Entry<UUID, PlayerSnapshot>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerSnapshot> entry = iterator.next();
            if (present.contains(entry.getKey())) continue;

            PlayerSnapshot snapshot = entry.getValue();
            if (!snapshot.dead() && local.position().distanceToSqr(snapshot.position()) <= maxRangeSq) {
                if (spots.size() >= MAX_ENTRIES) {
                    UUID oldest = null;
                    long oldestTimestamp = Long.MAX_VALUE;
                    for (Map.Entry<UUID, LogoutEntry> spot : spots.entrySet()) {
                        if (spot.getValue().timestamp() < oldestTimestamp) {
                            oldestTimestamp = spot.getValue().timestamp();
                            oldest = spot.getKey();
                        }
                    }
                    if (oldest != null) spots.remove(oldest);
                }
                spots.put(entry.getKey(), new LogoutEntry(
                        snapshot.name(), snapshot.position(), snapshot.health(), now
                ));
            }
            iterator.remove();
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.player == null || spots.isEmpty()) return;

        Vec3 eyes = mc.player.getEyePosition();
        int lineArgb = lineColor.getArgb();
        int a = lineArgb >>> 24 & 0xFF;
        int r = lineArgb >>> 16 & 0xFF;
        int g = lineArgb >>> 8 & 0xFF;
        int b = lineArgb & 0xFF;

        for (LogoutEntry entry : spots.values()) {
            Vec3 position = entry.position();
            if (box.get()) {
                AABB bounds = new AABB(
                        position.x - 0.3, position.y, position.z - 0.3,
                        position.x + 0.3, position.y + 1.8, position.z + 0.3
                );
                renderer.filledBox(bounds, fillColor.getArgb(), Renderer3D.DepthMode.NONE);
                renderer.outlineBox(bounds, lineArgb, 2.0f, Renderer3D.DepthMode.NONE);
            }
            if (tracer.get()) {
                renderer.line(eyes.x, eyes.y, eyes.z,
                        position.x, position.y + 0.9, position.z,
                        r, g, b, a);
            }
        }
    }

    private String identity() {
        String dimension = mc.level.dimension().identifier().toString();
        ServerData server = mc.getCurrentServer();
        String endpoint = server != null && server.ip != null ? server.ip : "singleplayer";
        return endpoint + '|' + dimension;
    }

    private void reset() {
        tracked.clear();
        spots.clear();
        identity = "";
    }

    private record PlayerSnapshot(String name, Vec3 position, float health, boolean dead) {
    }

    private record LogoutEntry(String name, Vec3 position, float health, long timestamp) {
    }
}
