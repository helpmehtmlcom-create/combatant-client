/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ModuleInfo(
        id = "choruspredict",
        displayName = "ChorusPredict",
        category = ModuleCategory.VISUALS,
        aliases = {"chorus"}
)
public class ChorusPredict extends Module {

    private static final String SETTING_DURATION = "duration";
    private static final String SETTING_BOX_COLOR = "boxColor";
    private static final String SETTING_OUTLINE = "outline";
    private static final String SETTING_TRACERS = "tracers";

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> duration =
            num("chorusPredictDuration", SETTING_DURATION, 5, 1, 15);
    private final RGBAColorValue boxColor =
            color("chorusPredictBoxColor", SETTING_BOX_COLOR, "#88FF00FF");
    private final BooleanValue outline =
            bool("chorusPredictOutline", SETTING_OUTLINE, true);
    private final BooleanValue tracers =
            bool("chorusPredictTracers", SETTING_TRACERS, true);

    private final List<Prediction> predictions = new ArrayList<>();
    private final List<SoundRecord> recentChorusSounds = new ArrayList<>();
    private final Map<Integer, PendingOrigin> pendingOrigins = new HashMap<>();
    private final List<RecentTeleport> recentTeleports = new ArrayList<>();
    private final Object lock = new Object();

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onDisable() {
        synchronized (lock) {
            predictions.clear();
            recentChorusSounds.clear();
            pendingOrigins.clear();
            recentTeleports.clear();
        }
    }

    @EventHandler
    private void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        long now = System.currentTimeMillis();

        // Track players consuming chorus fruit to prime origin predictions
        for (Player player : mc.level.players()) {
            if (player.isUsingItem() && player.getUseItem().is(Items.CHORUS_FRUIT)) {
                synchronized (lock) {
                    pendingOrigins.put(player.getId(), new PendingOrigin(player.position(), player.position(), now));
                }
            }
        }

        // Clean up or promote lone chorus sound events
        synchronized (lock) {
            recentTeleports.removeIf(t -> now - t.time > 1500L);
            pendingOrigins.entrySet().removeIf(e -> now - e.getValue().time > 1500L);

            for (int i = recentChorusSounds.size() - 1; i >= 0; i--) {
                SoundRecord record = recentChorusSounds.get(i);
                if (now - record.time > 500L) {
                    // If no pair or teleport was linked within 500ms, register position as destination
                    addPredictionInternal(record.pos, record.pos, now);
                    recentChorusSounds.remove(i);
                }
            }

            long maxAgeMs = (long) duration.get() * 1000L;
            predictions.removeIf(p -> now - p.timestamp > maxAgeMs);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || mc.level == null) return;

        Object packet = event.getPacket();
        if (packet instanceof ClientboundSoundPacket soundPacket) {
            if (isChorusSound(soundPacket.getSound())) {
                Vec3 soundPos = new Vec3(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ());
                handleChorusSound(soundPos);
            }
        } else if (packet instanceof ClientboundSoundEntityPacket soundEntityPacket) {
            if (isChorusSound(soundEntityPacket.getSound())) {
                Entity entity = mc.level.getEntity(soundEntityPacket.getId());
                if (entity != null) {
                    handleChorusSound(entity.position());
                }
            }
        } else if (packet instanceof ClientboundTeleportEntityPacket teleportPacket) {
            handleEntityTeleport(teleportPacket.id(), teleportPacket);
        } else if (packet instanceof ClientboundEntityPositionSyncPacket syncPacket) {
            if (syncPacket.values() != null) {
                handleEntityMove(syncPacket.id(), syncPacket.values().position());
            }
        } else if (packet instanceof ClientboundPlayerPositionPacket playerPositionPacket) {
            if (mc.player != null) {
                PositionMoveRotation current = new PositionMoveRotation(
                        mc.player.position(),
                        mc.player.getDeltaMovement(),
                        mc.player.getYRot(),
                        mc.player.getXRot()
                );
                Vec3 newPos = PositionMoveRotation.calculateAbsolute(
                        current,
                        playerPositionPacket.change(),
                        playerPositionPacket.relatives()
                ).position();
                if (newPos != null && Double.isFinite(newPos.x)) {
                    handleEntityMove(mc.player.getId(), newPos);
                }
            }
        }
    }

    private boolean isChorusSound(Holder<SoundEvent> soundHolder) {
        if (soundHolder == null) return false;
        try {
            if (soundHolder.value() == SoundEvents.CHORUS_FRUIT_TELEPORT) {
                return true;
            }
        } catch (Throwable ignored) {}
        try {
            SoundEvent event = soundHolder.value();
            if (event != null && event.location() != null) {
                String path = event.location().getPath();
                if (path.contains("chorus_fruit") && path.contains("teleport")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void handleChorusSound(Vec3 soundPos) {
        long now = System.currentTimeMillis();

        synchronized (lock) {
            // Check if any recent teleport correlates with this sound
            for (int i = recentTeleports.size() - 1; i >= 0; i--) {
                RecentTeleport tp = recentTeleports.get(i);
                if (now - tp.time <= 1000L) {
                    if (tp.oldPos.distanceToSqr(soundPos) < 9.0 || tp.newPos.distanceToSqr(soundPos) < 9.0) {
                        addPredictionInternal(tp.oldPos, tp.newPos, now);
                        recentTeleports.remove(i);
                        return;
                    }
                }
            }

            // Check if a previous chorus sound within 1000ms forms a pair (origin -> destination)
            SoundRecord pair = null;
            for (int i = recentChorusSounds.size() - 1; i >= 0; i--) {
                SoundRecord record = recentChorusSounds.get(i);
                if (now - record.time > 1000L) {
                    recentChorusSounds.remove(i);
                    continue;
                }
                double dist = record.pos.distanceTo(soundPos);
                if (dist > 0.5 && dist <= 24.0) {
                    pair = record;
                    recentChorusSounds.remove(i);
                    break;
                }
            }

            if (pair != null) {
                addPredictionInternal(pair.pos, soundPos, now);
                return;
            }

            // Find nearby entity to store pending origin
            if (mc.level != null) {
                Entity closest = null;
                double closestDistSq = 16.0;
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (e instanceof Player || e instanceof net.minecraft.world.entity.LivingEntity) {
                        double distSq = e.position().distanceToSqr(soundPos);
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closest = e;
                        }
                    }
                }
                if (closest != null) {
                    pendingOrigins.put(closest.getId(), new PendingOrigin(closest.position(), soundPos, now));
                }
            }

            recentChorusSounds.add(new SoundRecord(soundPos, now));
        }
    }

    private void handleEntityTeleport(int entityId, ClientboundTeleportEntityPacket packet) {
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        Vec3 newPos = null;

        if (entity != null) {
            PositionMoveRotation current = new PositionMoveRotation(
                    entity.position(),
                    entity.getDeltaMovement(),
                    entity.getYRot(),
                    entity.getXRot()
            );
            newPos = PositionMoveRotation.calculateAbsolute(current, packet.change(), packet.relatives()).position();
        } else if (packet.change() != null) {
            newPos = packet.change().position();
        }

        if (newPos != null && Double.isFinite(newPos.x) && Double.isFinite(newPos.y) && Double.isFinite(newPos.z)) {
            handleEntityMove(entityId, newPos);
        }
    }

    private void handleEntityMove(int entityId, Vec3 newPos) {
        if (mc.level == null || newPos == null) return;
        Entity entity = mc.level.getEntity(entityId);
        Vec3 oldPos = entity != null ? entity.position() : null;
        long now = System.currentTimeMillis();

        synchronized (lock) {
            PendingOrigin pending = pendingOrigins.remove(entityId);
            if (pending != null && now - pending.time <= 1200L) {
                addPredictionInternal(pending.sourcePos, newPos, now);
                return;
            }

            if (oldPos != null) {
                double distSq = oldPos.distanceToSqr(newPos);
                if (distSq >= 1.0 && distSq <= 600.0) {
                    // Check if near any recent chorus sound
                    for (int i = recentChorusSounds.size() - 1; i >= 0; i--) {
                        SoundRecord sr = recentChorusSounds.get(i);
                        if (now - sr.time <= 1000L && (sr.pos.distanceToSqr(oldPos) < 9.0 || sr.pos.distanceToSqr(newPos) < 9.0)) {
                            addPredictionInternal(oldPos, newPos, now);
                            recentChorusSounds.remove(i);
                            return;
                        }
                    }
                    recentTeleports.add(new RecentTeleport(entityId, oldPos, newPos, now));
                }
            }
        }
    }

    private void addPredictionInternal(Vec3 origin, Vec3 destination, long timestamp) {
        if (destination == null) return;
        for (Prediction p : predictions) {
            if (p.destination.distanceToSqr(destination) < 1.0 && timestamp - p.timestamp < 1000L) {
                return;
            }
        }
        predictions.add(new Prediction(origin != null ? origin : destination, destination, timestamp));
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null || renderer == null) return;

        long now = System.currentTimeMillis();
        long maxAgeMs = (long) duration.get() * 1000L;

        List<Prediction> active = new ArrayList<>();
        synchronized (lock) {
            predictions.removeIf(p -> now - p.timestamp > maxAgeMs);
            active.addAll(predictions);
        }

        if (active.isEmpty()) return;

        int argb = boxColor.getArgb();
        boolean renderOutline = outline.get();
        boolean renderTracers = tracers.get();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        int outlineArgb = (argb & 0x00FFFFFF) | 0xFF000000;

        for (Prediction p : active) {
            Vec3 dest = p.destination;
            AABB box = new AABB(
                    dest.x - 0.3, dest.y, dest.z - 0.3,
                    dest.x + 0.3, dest.y + 1.8, dest.z + 0.3
            );

            // 3D box fill
            drawFilledBox(renderer, box, r, g, b, a);

            // 3D box outline
            if (renderOutline) {
                drawOutlineBox(renderer, box, outlineArgb);
            }

            // Tracer line from origin to landing spot
            if (renderTracers && p.origin != null) {
                Vec3 origin = p.origin;
                if (origin.distanceToSqr(dest) > 0.04) {
                    renderer.line(
                            origin.x, origin.y + 0.9, origin.z,
                            dest.x, dest.y + 0.9, dest.z,
                            r, g, b, 255
                    );
                }
            }
        }
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

    private record Prediction(Vec3 origin, Vec3 destination, long timestamp) {}

    private record SoundRecord(Vec3 pos, long time) {}

    private record PendingOrigin(Vec3 entityPos, Vec3 sourcePos, long time) {}

    private record RecentTeleport(int entityId, Vec3 oldPos, Vec3 newPos, long time) {}
}
