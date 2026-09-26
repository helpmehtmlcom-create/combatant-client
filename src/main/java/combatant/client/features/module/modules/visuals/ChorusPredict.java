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
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ModuleInfo(
        id = "choruspredict",
        displayName = "ChorusPredict",
        aliases = {"ChorusESP", "ChorusTracker"},
        category = ModuleCategory.VISUALS,
        description = "Renders 3D boxes and tracer lines to Chorus Fruit teleport destinations."
)
public final class ChorusPredict extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> durationSeconds =
            num("chorusPredictDuration", "duration", 5, 1, 15);

    private final BooleanValue tracer =
            bool("chorusPredictTracer", "tracer", true);

    private final BooleanValue box =
            bool("chorusPredictBox", "box", true);

    private final RGBAColorValue fillColor =
            color("chorusPredictFillColor", "fill_color", "#AA00FF44");

    private final RGBAColorValue lineColor =
            color("chorusPredictLineColor", "line_color", "#EE33FFFF");

    private final List<ChorusDestination> destinations = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onDisable() {
        destinations.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        long now = System.currentTimeMillis();
        long maxAgeMs = durationSeconds.get() * 1000L;
        destinations.removeIf(dest -> now - dest.timestamp > maxAgeMs);
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundSoundPacket soundPacket) {
            if (soundPacket.getSound().value() == SoundEvents.CHORUS_FRUIT_TELEPORT) {
                Vec3 targetPos = new Vec3(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ());
                destinations.add(new ChorusDestination(targetPos, System.currentTimeMillis()));
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.player == null || destinations.isEmpty()) return;

        LocalPlayer player = mc.player;
        long now = System.currentTimeMillis();
        long maxAgeMs = durationSeconds.get() * 1000L;

        int fillArgb = fillColor.getArgb();
        int lineArgb = lineColor.getArgb();

        int aLine = (lineArgb >>> 24) & 0xFF;
        int rLine = (lineArgb >>> 16) & 0xFF;
        int gLine = (lineArgb >>> 8) & 0xFF;
        int bLine = lineArgb & 0xFF;

        Vec3 playerPos = player.position();

        synchronized (destinations) {
            for (ChorusDestination dest : destinations) {
                long age = now - dest.timestamp;
                if (age > maxAgeMs) continue;

                float factor = 1.0f - ((float) age / maxAgeMs);
                int alphaFill = (int) (((fillArgb >>> 24) & 0xFF) * factor);
                int alphaLine = (int) (aLine * factor);

                int fadedFill = (alphaFill << 24) | (fillArgb & 0x00FFFFFF);
                int fadedLine = (alphaLine << 24) | (lineArgb & 0x00FFFFFF);

                Vec3 target = dest.pos;

                if (box.get()) {
                    AABB bb = new AABB(
                            target.x - 0.3, target.y, target.z - 0.3,
                            target.x + 0.3, target.y + 1.8, target.z + 0.3
                    );
                    addFilledBox(renderer, bb, fadedFill);
                    addOutlineBox(renderer, bb, fadedLine);
                }

                if (tracer.get() && alphaLine > 0) {
                    renderer.line(playerPos.x, playerPos.y + player.getEyeHeight(), playerPos.z,
                            target.x, target.y + 0.9, target.z,
                            rLine, gLine, bLine, alphaLine);
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

    private static final class ChorusDestination {
        final Vec3 pos;
        final long timestamp;

        ChorusDestination(Vec3 pos, long timestamp) {
            this.pos = pos;
            this.timestamp = timestamp;
        }
    }
}
