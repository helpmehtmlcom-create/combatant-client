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
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

@ModuleInfo(
        id = "logoutspots",
        displayName = "LogoutSpots",
        aliases = {"CombatLogESP", "LoggedPlayers"},
        category = ModuleCategory.VISUALS,
        description = "Renders 3D boxes and information at positions where players logged out."
)
public final class LogoutSpots extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue box =
            bool("logoutSpotsBox", "box", true);

    private final BooleanValue tracer =
            bool("logoutSpotsTracer", "tracer", true);

    private final NumberValue<Double> maxRange =
            num("logoutSpotsMaxRange", "max_range", 200.0, 20.0, 1000.0);

    private final RGBAColorValue fillColor =
            color("logoutSpotsFillColor", "fill_color", "#FF000044");

    private final RGBAColorValue lineColor =
            color("logoutSpotsLineColor", "line_color", "#FF0000FF");

    private final Map<UUID, PlayerSnapshot> trackedPlayers = new HashMap<>();
    private final Map<UUID, LogoutEntry> loggedOutSpots = Collections.synchronizedMap(new HashMap<>());

    @Override
    public void onDisable() {
        trackedPlayers.clear();
        loggedOutSpots.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null || mc.level == null) {
            trackedPlayers.clear();
            return;
        }

        Set<UUID> currentUuids = new HashSet<>();

        for (Player p : mc.level.players()) {
            if (p.equals(localPlayer)) continue;

            UUID uuid = p.getUUID();
            currentUuids.add(uuid);

            // If player was logged out and logged back in, clear their spot
            loggedOutSpots.remove(uuid);

            trackedPlayers.put(uuid, new PlayerSnapshot(
                    p.getName().getString(),
                    p.position(),
                    p.getHealth() + p.getAbsorptionAmount(),
                    p.isDeadOrDying()
            ));
        }

        // Check for players that disappeared
        Iterator<Map.Entry<UUID, PlayerSnapshot>> it = trackedPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PlayerSnapshot> entry = it.next();
            UUID uuid = entry.getKey();
            if (!currentUuids.contains(uuid)) {
                PlayerSnapshot snapshot = entry.getValue();
                if (!snapshot.dead && localPlayer.distanceToSqr(snapshot.pos) <= maxRange.get() * maxRange.get()) {
                    loggedOutSpots.put(uuid, new LogoutEntry(
                            snapshot.name,
                            snapshot.pos,
                            snapshot.health,
                            System.currentTimeMillis()
                    ));
                }
                it.remove();
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.player == null || loggedOutSpots.isEmpty()) return;

        LocalPlayer player = mc.player;
        Vec3 playerEyes = player.getEyePosition();

        int fillArgb = fillColor.getArgb();
        int lineArgb = lineColor.getArgb();

        int aLine = (lineArgb >>> 24) & 0xFF;
        int rLine = (lineArgb >>> 16) & 0xFF;
        int gLine = (lineArgb >>> 8) & 0xFF;
        int bLine = lineArgb & 0xFF;

        synchronized (loggedOutSpots) {
            for (LogoutEntry entry : loggedOutSpots.values()) {
                Vec3 p = entry.pos;

                if (box.get()) {
                    AABB bb = new AABB(
                            p.x - 0.3, p.y, p.z - 0.3,
                            p.x + 0.3, p.y + 1.8, p.z + 0.3
                    );
                    addFilledBox(renderer, bb, fillArgb);
                    addOutlineBox(renderer, bb, lineArgb);
                }

                if (tracer.get()) {
                    renderer.line(playerEyes.x, playerEyes.y, playerEyes.z,
                            p.x, p.y + 0.9, p.z,
                            rLine, gLine, bLine, aLine);
                }
            }
        }
    }

    private static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, a);
    }

    private static void addOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, r, g, b, a);

        renderer.line(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, a);

        renderer.line(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
    }

    private record PlayerSnapshot(String name, Vec3 pos, float health, boolean dead) {
    }

    private record LogoutEntry(String name, Vec3 pos, float health, long timestamp) {
    }
}
