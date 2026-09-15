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
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.WorldTextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "logoutspots",
        displayName = "LogoutSpots",
        category = ModuleCategory.VISUALS,
        aliases = {"loggedplayers", "disconnectspots"}
)
public class LogoutSpots extends Module {

    private static final String SETTING_RENDER_DURATION = "renderDuration";
    private static final String SETTING_BOX_COLOR = "boxColor";
    private static final String SETTING_NAMETAG = "nametag";
    private static final String SETTING_TRACERS = "tracers";

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> renderDuration =
            num("logoutSpotsRenderDuration", SETTING_RENDER_DURATION, 15, 1, 60);
    private final RGBAColorValue boxColor =
            color("logoutSpotsBoxColor", SETTING_BOX_COLOR, "#88FF5555");
    private final BooleanValue nametag =
            bool("logoutSpotsNametag", SETTING_NAMETAG, true);
    private final BooleanValue tracers =
            bool("logoutSpotsTracers", SETTING_TRACERS, true);

    private final Map<UUID, LogoutSpot> logoutSpots = new ConcurrentHashMap<>();
    private final Map<UUID, TrackedPlayer> trackedPlayers = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> entityIdToUuid = new ConcurrentHashMap<>();
    private final Set<UUID> deadPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Object lock = new Object();
    private ClientLevel lastLevel = null;

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onDisable() {
        synchronized (lock) {
            logoutSpots.clear();
            trackedPlayers.clear();
            entityIdToUuid.clear();
            deadPlayers.clear();
            lastLevel = null;
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) {
            synchronized (lock) {
                trackedPlayers.clear();
                entityIdToUuid.clear();
            }
            return;
        }

        // Handle dimension switch / level change
        if (lastLevel != mc.level) {
            lastLevel = mc.level;
            synchronized (lock) {
                trackedPlayers.clear();
                entityIdToUuid.clear();
                deadPlayers.clear();
            }
            return;
        }

        long now = System.currentTimeMillis();
        long maxAgeMs = (long) renderDuration.get() * 60_000L;
        logoutSpots.entrySet().removeIf(entry -> (now - entry.getValue().timestamp()) > maxAgeMs);

        Set<UUID> currentLevelUuids = new HashSet<>();

        for (Player player : mc.level.players()) {
            if (player == null || player == mc.player) continue;

            UUID uuid = player.getUUID();
            currentLevelUuids.add(uuid);

            // If player died, mark dead and remove from tracking and logout spots
            if (player.isDeadOrDying() || player.getHealth() <= 0.0f) {
                deadPlayers.add(uuid);
                trackedPlayers.remove(uuid);
                entityIdToUuid.remove(player.getId());
                logoutSpots.remove(uuid);
                continue;
            }

            // Player is alive; clear dead status if previously set
            deadPlayers.remove(uuid);

            // Check if player rejoined
            LogoutSpot spot = logoutSpots.remove(uuid);
            if (spot != null) {
                String posStr = String.format(Locale.ROOT, "%.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ());
                sendNotice("[LogoutSpots] " + player.getName().getString() + " has logged back in at " + posStr + "!");
            }

            // Snapshot player state
            List<ItemStack> armor = List.of(
                    player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                    player.getItemBySlot(EquipmentSlot.CHEST).copy(),
                    player.getItemBySlot(EquipmentSlot.LEGS).copy(),
                    player.getItemBySlot(EquipmentSlot.FEET).copy()
            );
            trackedPlayers.put(uuid, new TrackedPlayer(
                    player.getName().getString(),
                    uuid,
                    player.position(),
                    player.getHealth(),
                    armor
            ));
            entityIdToUuid.put(player.getId(), uuid);
        }

        // Check for tracked players that left the level
        List<UUID> departed = new ArrayList<>();
        for (UUID uuid : trackedPlayers.keySet()) {
            if (!currentLevelUuids.contains(uuid)) {
                departed.add(uuid);
            }
        }

        for (UUID uuid : departed) {
            TrackedPlayer tp = trackedPlayers.remove(uuid);
            if (tp != null && !deadPlayers.contains(uuid)) {
                if (!logoutSpots.containsKey(uuid)) {
                    logoutSpots.put(uuid, new LogoutSpot(
                            tp.name(),
                            uuid,
                            tp.position(),
                            tp.health(),
                            tp.armor(),
                            now
                    ));
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        if (event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            // Event 3 corresponds to entity death in Minecraft
            if (packet.getEventId() == 3) {
                Entity entity = packet.getEntity(mc.level);
                if (entity instanceof Player player && player != mc.player) {
                    UUID uuid = player.getUUID();
                    deadPlayers.add(uuid);
                    trackedPlayers.remove(uuid);
                    entityIdToUuid.remove(player.getId());
                    logoutSpots.remove(uuid);
                }
            }
        } else if (event.getPacket() instanceof ClientboundRemoveEntitiesPacket packet) {
            for (int id : packet.getEntityIds()) {
                Entity entity = mc.level.getEntity(id);
                UUID uuid = entityIdToUuid.remove(id);
                if (entity instanceof Player player && player != mc.player) {
                    uuid = player.getUUID();
                    if (player.isDeadOrDying() || player.getHealth() <= 0.0f) {
                        deadPlayers.add(uuid);
                        trackedPlayers.remove(uuid);
                        logoutSpots.remove(uuid);
                        continue;
                    }
                }
                if (uuid != null && !deadPlayers.contains(uuid)) {
                    TrackedPlayer tp = trackedPlayers.remove(uuid);
                    if (tp != null && !logoutSpots.containsKey(uuid)) {
                        logoutSpots.put(uuid, new LogoutSpot(
                                tp.name(),
                                uuid,
                                tp.position(),
                                tp.health(),
                                tp.armor(),
                                System.currentTimeMillis()
                        ));
                    }
                }
            }
        } else if (event.getPacket() instanceof ClientboundPlayerInfoRemovePacket packet) {
            for (UUID uuid : packet.profileIds()) {
                if (uuid != null && !deadPlayers.contains(uuid)) {
                    TrackedPlayer tp = trackedPlayers.remove(uuid);
                    if (tp != null && !logoutSpots.containsKey(uuid)) {
                        logoutSpots.put(uuid, new LogoutSpot(
                                tp.name(),
                                uuid,
                                tp.position(),
                                tp.health(),
                                tp.armor(),
                                System.currentTimeMillis()
                        ));
                    }
                }
            }
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || mc.level == null || mc.player == null || logoutSpots.isEmpty()) {
            return;
        }

        int argb = boxColor.getArgb();
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        Vec3 camPos = RenderState.cameraPos != null
                ? RenderState.cameraPos
                : mc.player.getEyePosition(tickDelta);
        Vec3 look = RenderState.cameraLook != null
                ? RenderState.cameraLook
                : mc.player.getViewVector(tickDelta);
        Vec3 tracerStart = (camPos != null && look != null)
                ? camPos.add(look.scale(0.5))
                : camPos;

        boolean renderTracers = tracers.get() && tracerStart != null;
        boolean renderNametag = nametag.get();

        for (LogoutSpot spot : logoutSpots.values()) {
            Vec3 pos = spot.position();
            if (pos == null) continue;

            // Player-sized 3D bounding box (0.6 width, 1.8 height)
            AABB box = new AABB(
                    pos.x - 0.3, pos.y, pos.z - 0.3,
                    pos.x + 0.3, pos.y + 1.8, pos.z + 0.3
            );

            // Render filled box
            drawFilledBox(renderer, box, r, g, b, a);

            // Render outline box (full alpha for distinct border)
            drawOutlineBox(renderer, box, (255 << 24) | (r << 16) | (g << 8) | b);

            // Render tracer line from camera to logout spot
            if (renderTracers) {
                renderer.line(
                        tracerStart.x, tracerStart.y, tracerStart.z,
                        pos.x, pos.y + 0.9, pos.z,
                        r, g, b, 255
                );
            }

            // Render nametag above bounding box
            if (renderNametag) {
                Vec3 tagPos = new Vec3(pos.x, pos.y + 2.05, pos.z);
                double dist = camPos != null ? camPos.distanceTo(pos) : 0.0;
                long elapsedSec = Math.max(0L, (System.currentTimeMillis() - spot.timestamp()) / 1000L);
                long mins = elapsedSec / 60L;
                long secs = elapsedSec % 60L;
                String timeStr = mins > 0 ? (mins + "m " + secs + "s") : (secs + "s");
                String text = String.format(Locale.ROOT, "%s  %.1f HP  (%.0fm, %s)", spot.name(), spot.health(), dist, timeStr);

                double worldScale = Math.max(0.025, dist * 0.0025);
                WorldTextRenderer.Options options = WorldTextRenderer.Options.defaults()
                        .withScale(1.0)
                        .withWorldScale(worldScale)
                        .withDepthMode(Renderer3D.DepthMode.NONE)
                        .withShadow(true)
                        .withCentered(true)
                        .withColor(new RenderColor(255, 255, 255, 255));
                WorldTextRenderer.drawBillboard(renderer, TextRenderer.get(), text, tagPos, options);
            }
        }
    }

    private void sendNotice(String message) {
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
        } else {
            CommandOutput.send(Component.literal(message));
        }
        Notifier.info(message);
    }

    private static void drawFilledBox(Renderer3D renderer, AABB box, int r, int g, int b, int a) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        // Bottom & Top
        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);

        // Sides
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
    }

    private static void drawOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        // Bottom
        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Top
        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Verticals
        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    public Map<UUID, LogoutSpot> getLogoutSpots() {
        return Collections.unmodifiableMap(logoutSpots);
    }

    public record LogoutSpot(
            String name,
            UUID uuid,
            Vec3 position,
            float health,
            List<ItemStack> armor,
            long timestamp
    ) {
        public String getName() { return name; }
        public UUID getUuid() { return uuid; }
        public Vec3 getPosition() { return position; }
        public float getHealth() { return health; }
        public List<ItemStack> getArmor() { return armor; }
        public long getTimestamp() { return timestamp; }
    }

    private record TrackedPlayer(
            String name,
            UUID uuid,
            Vec3 position,
            float health,
            List<ItemStack> armor
    ) {}
}
