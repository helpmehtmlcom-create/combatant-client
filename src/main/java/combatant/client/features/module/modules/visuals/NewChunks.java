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
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@ModuleInfo(
        id = "newchunks",
        displayName = "NewChunks",
        aliases = {"ChunkDetector", "FreshChunks"},
        category = ModuleCategory.VISUALS,
        description = "Highlights newly generated chunks to assist with base hunting and exploration."
)
public final class NewChunks extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> renderHeight =
            num("newChunksRenderHeight", "render_height", 0.0f, -64.0f, 320.0f);

    private final BooleanValue relativeToPlayer =
            bool("newChunksRelativeToPlayer", "relative_to_player", true);

    private final NumberValue<Integer> maxChunks =
            num("newChunksMaxChunks", "max_chunks", 1024, 256, 4096);

    private final BooleanValue fill =
            bool("newChunksFill", "fill", true);

    private final BooleanValue outline =
            bool("newChunksOutline", "outline", true);

    private final RGBAColorValue color =
            color("newChunksColor", "color", "#FF220044");

    private final RGBAColorValue lineColor =
            color("newChunksLineColor", "line_color", "#FF3300FF");

    private final Set<ChunkPos> knownChunks = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<ChunkPos> newChunks = Collections.synchronizedSet(new LinkedHashSet<>());

    @Override
    public void onDisable() {
        knownChunks.clear();
        newChunks.clear();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
            ChunkPos pos = new ChunkPos(chunkPacket.getX(), chunkPacket.getZ());
            if (!knownChunks.contains(pos)) {
                knownChunks.add(pos);
                if (newChunks.size() < maxChunks.get()) {
                    newChunks.add(pos);
                }
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || newChunks.isEmpty()) return;

        LocalPlayer player = mc.player;
        double y = (relativeToPlayer.get() && player != null) ? player.getY() : renderHeight.get();

        int fillArgb = color.getArgb();
        int lineArgb = lineColor.getArgb();
        boolean doFill = fill.get();
        boolean doOutline = outline.get();

        synchronized (newChunks) {
            for (ChunkPos pos : newChunks) {
                double minX = pos.getMinBlockX();
                double minZ = pos.getMinBlockZ();
                double maxX = pos.getMaxBlockX() + 1.0;
                double maxZ = pos.getMaxBlockZ() + 1.0;

                AABB box = new AABB(minX, y, minZ, maxX, y + 0.1, maxZ);

                if (doFill) {
                    addFilledBox(renderer, box, fillArgb);
                }
                if (doOutline) {
                    addOutlineBox(renderer, box, lineArgb);
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
}
