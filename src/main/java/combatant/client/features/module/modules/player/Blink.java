/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.BlinkPacketEvent;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.util.network.BlinkManager;
import combatant.client.util.network.TransferOrigin;

import java.util.ArrayList;
import java.util.List;
@ModuleInfo(
        id = "blink",
        displayName = "Blink",
        category = ModuleCategory.PLAYER,
        description = "Suspends outgoing movement packets to simulate severe lag and teleport across distances upon release."
)
public final class Blink extends Module {

    public enum Mode {
        FLUSH,
        CANCEL
    }
    private final EnumValue<Mode> mode = enumMode("mode", Mode.FLUSH);
    private final BooleanValue pulse = bool("pulse", false);
    private final NumberValue<Integer> pulseTicks = visibleWhen(num("pulse_ticks", 20, 2, 100), pulse::get);
    private final NumberValue<Float> autoDisableHealth = num("auto_disable_health", 6.0f, 0.0f, 20.0f);
    private final NumberValue<Integer> maxPackets = num("max_packets", 200, 10, 200);

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Vec3> trailPoints = new ArrayList<>();
    private int packetCount = 0;
    private int ticks = 0;
    private Vec3 startPos = null;
    private float startYaw = 0.0f;
    @Override
    public void onEnable() {
        packetCount = 0;
        ticks = 0;
        trailPoints.clear();
        BlinkManager.INSTANCE.setBlinking(true);
        if (mc.player != null) {
            startPos = mc.player.position();
            startYaw = mc.player.getYRot();
            trailPoints.add(startPos);
        }
    }

    @Override
    public void onDisable() {
        BlinkManager.INSTANCE.setBlinking(false);
        if (mode.get() == Mode.CANCEL) {
            BlinkManager.INSTANCE.cancelOutgoingMovement();
        } else {
            BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
        }
        packetCount = 0;
        ticks = 0;
        startPos = null;
        trailPoints.clear();
    }

    @EventHandler
    public void onBlinkPacket(BlinkPacketEvent event) {
        if (!isEnabled()) return;
        if (event.getOrigin() != TransferOrigin.OUTGOING) return;

        Packet<?> packet = event.getPacket();
        if (packet == null) {
            event.setAction(BlinkManager.Action.QUEUE);
            return;
        }

        if (packet instanceof ServerboundMovePlayerPacket) {
            event.setAction(BlinkManager.Action.QUEUE);
            packetCount++;

            if (packetCount >= maxPackets.get()) {
                toggle();
            }
        }
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled()) return;

        if (mc.player == null || mc.player.isDeadOrDying()) {
            toggle();
            return;
        }

        if (autoDisableHealth.get() > 0.0f && mc.player.getHealth() <= autoDisableHealth.get()) {
            toggle();
            return;
        }

        Vec3 playerPos = mc.player.position();
        if (trailPoints.isEmpty() || trailPoints.get(trailPoints.size() - 1).distanceToSqr(playerPos) > 0.005) {
            trailPoints.add(playerPos);
        }

        if (pulse.get()) {
            ticks++;
            if (ticks >= pulseTicks.get()) {
                BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
                packetCount = 0;
                ticks = 0;
                startPos = mc.player.position();
                startYaw = mc.player.getYRot();
                trailPoints.clear();
                trailPoints.add(startPos);
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || mc.player == null || startPos == null) {
            return;
        }

        MeshBuilder tris = renderer.batch(CombatantRenderPipelines.WORLD_COLORED, Renderer3D.DepthMode.NONE);
        float previousLineWidth = RenderState.lineWidth;
        MeshBuilder lines;
        try {
            RenderState.lineWidth = 1.8f;
            lines = renderer.batch(CombatantRenderPipelines.WORLD_COLORED_LINES, Renderer3D.DepthMode.NONE);
        } finally {
            RenderState.lineWidth = previousLineWidth;
        }

        // Render ghost model at start position
        int ghostFill = 0x3300D9FF;
        int ghostLine = 0xCC00E5FF;
        addBodyModel(tris, lines, mc.player, startPos, startYaw, ghostFill, ghostLine);

        // Render visual trail connecting start position to current position
        int trailColor = 0xEE00FFFF;
        if (trailPoints.size() > 1) {
            for (int i = 0; i < trailPoints.size() - 1; i++) {
                Vec3 p1 = trailPoints.get(i);
                Vec3 p2 = trailPoints.get(i + 1);
                line(lines, p1.x, p1.y + 0.1, p1.z, p2.x, p2.y + 0.1, p2.z, trailColor);
            }
        }
        if (!trailPoints.isEmpty()) {
            Vec3 last = trailPoints.get(trailPoints.size() - 1);
            Vec3 current = mc.player.position();
            line(lines, last.x, last.y + 0.1, last.z, current.x, current.y + 0.1, current.z, trailColor);
        }
    }

    private static void addBodyModel(MeshBuilder tris, MeshBuilder lines, Entity entity, Vec3 feet, float yaw, int fillArgb, int lineArgb) {
        AABB currentBox = entity.getBoundingBox();
        double width = Math.max(0.35, Math.min(1.35, Math.max(currentBox.getXsize(), currentBox.getZsize())));
        double height = Math.max(0.75, Math.min(3.2, currentBox.getYsize()));

        double depth = width * 0.32;
        double legHeight = height * 0.43;
        double torsoHeight = height * 0.34;
        double headSize = Math.min(width * 0.58, height * 0.24);

        double legWidth = width * 0.20;
        double legDepth = depth * 0.85;
        double legOffset = width * 0.14;
        double torsoWidth = width * 0.58;
        double armWidth = width * 0.17;
        double armOffset = torsoWidth * 0.5 + armWidth * 0.6;

        addBodyPart(tris, lines, feet, -legOffset, legHeight * 0.5, 0.0, legWidth, legHeight, legDepth, yaw, fillArgb, lineArgb);
        addBodyPart(tris, lines, feet, legOffset, legHeight * 0.5, 0.0, legWidth, legHeight, legDepth, yaw, fillArgb, lineArgb);
        addBodyPart(tris, lines, feet, 0.0, legHeight + torsoHeight * 0.5, 0.0, torsoWidth, torsoHeight, depth, yaw, fillArgb, lineArgb);
        addBodyPart(tris, lines, feet, -armOffset, legHeight + torsoHeight * 0.5, 0.0, armWidth, torsoHeight * 0.95, depth, yaw, fillArgb, lineArgb);
        addBodyPart(tris, lines, feet, armOffset, legHeight + torsoHeight * 0.5, 0.0, armWidth, torsoHeight * 0.95, depth, yaw, fillArgb, lineArgb);
        addBodyPart(tris, lines, feet, 0.0, height - headSize * 0.5, 0.0, headSize, headSize, headSize, yaw, fillArgb, lineArgb);
    }

    private static void addBodyPart(MeshBuilder tris, MeshBuilder lines, Vec3 origin, double centerX, double centerY, double centerZ,
                                    double sizeX, double sizeY, double sizeZ, float yaw, int fillArgb, int lineArgb) {
        double hx = sizeX * 0.5;
        double hy = sizeY * 0.5;
        double hz = sizeZ * 0.5;
        Vec3[] c = new Vec3[]{
                rot(origin, centerX - hx, centerY - hy, centerZ - hz, yaw),
                rot(origin, centerX + hx, centerY - hy, centerZ - hz, yaw),
                rot(origin, centerX + hx, centerY - hy, centerZ + hz, yaw),
                rot(origin, centerX - hx, centerY - hy, centerZ + hz, yaw),
                rot(origin, centerX - hx, centerY + hy, centerZ - hz, yaw),
                rot(origin, centerX + hx, centerY + hy, centerZ - hz, yaw),
                rot(origin, centerX + hx, centerY + hy, centerZ + hz, yaw),
                rot(origin, centerX - hx, centerY + hy, centerZ + hz, yaw)
        };
        quad(tris, c[0], c[1], c[2], c[3], fillArgb);
        quad(tris, c[4], c[7], c[6], c[5], fillArgb);
        quad(tris, c[0], c[4], c[5], c[1], fillArgb);
        quad(tris, c[3], c[2], c[6], c[7], fillArgb);
        quad(tris, c[1], c[5], c[6], c[2], fillArgb);
        quad(tris, c[0], c[3], c[7], c[4], fillArgb);

        line(lines, c[0].x, c[0].y, c[0].z, c[1].x, c[1].y, c[1].z, lineArgb);
        line(lines, c[1].x, c[1].y, c[1].z, c[2].x, c[2].y, c[2].z, lineArgb);
        line(lines, c[2].x, c[2].y, c[2].z, c[3].x, c[3].y, c[3].z, lineArgb);
        line(lines, c[3].x, c[3].y, c[3].z, c[0].x, c[0].y, c[0].z, lineArgb);
        line(lines, c[4].x, c[4].y, c[4].z, c[5].x, c[5].y, c[5].z, lineArgb);
        line(lines, c[5].x, c[5].y, c[5].z, c[6].x, c[6].y, c[6].z, lineArgb);
        line(lines, c[6].x, c[6].y, c[6].z, c[7].x, c[7].y, c[7].z, lineArgb);
        line(lines, c[7].x, c[7].y, c[7].z, c[4].x, c[4].y, c[4].z, lineArgb);
        line(lines, c[0].x, c[0].y, c[0].z, c[4].x, c[4].y, c[4].z, lineArgb);
        line(lines, c[1].x, c[1].y, c[1].z, c[5].x, c[5].y, c[5].z, lineArgb);
        line(lines, c[2].x, c[2].y, c[2].z, c[6].x, c[6].y, c[6].z, lineArgb);
        line(lines, c[3].x, c[3].y, c[3].z, c[7].x, c[7].y, c[7].z, lineArgb);
    }

    private static Vec3 rot(Vec3 origin, double x, double y, double z, float yaw) {
        double rad = Math.toRadians(-yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        return new Vec3(origin.x + (x * cos - z * sin), origin.y + y, origin.z + (x * sin + z * cos));
    }

    private static void quad(MeshBuilder mesh, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, int argb) {
        mesh.ensureQuadCapacity();
        v(mesh, p1.x, p1.y, p1.z, argb);
        int i1 = mesh.next();
        v(mesh, p2.x, p2.y, p2.z, argb);
        int i2 = mesh.next();
        v(mesh, p3.x, p3.y, p3.z, argb);
        int i3 = mesh.next();
        v(mesh, p4.x, p4.y, p4.z, argb);
        int i4 = mesh.next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void line(MeshBuilder mesh, double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        mesh.ensureLineCapacity();
        v(mesh, x1, y1, z1, argb);
        int i1 = mesh.next();
        v(mesh, x2, y2, z2, argb);
        int i2 = mesh.next();
        mesh.line(i1, i2);
    }

    private static void v(MeshBuilder mesh, double x, double y, double z, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        mesh.vec3(x, y, z).color(r, g, b, a);
    }

    public int getPacketCount() {
        return packetCount;
    }

    public Vec3 getStartPos() {
        return startPos;
    }
}
