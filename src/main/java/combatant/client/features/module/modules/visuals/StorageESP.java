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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "storageesp",
        displayName = "StorageESP",
        aliases = {"ChestESP", "ContainerESP"},
        category = ModuleCategory.VISUALS,
        description = "Renders colored 3D outlines and boxes around containers and storage blocks."
)
public final class StorageESP extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> range =
            num("storageEspRange", "range", 64, 8, 128);

    private final BooleanValue chests =
            bool("storageEspChests", "chests", true);

    private final BooleanValue enderChests =
            bool("storageEspEnderChests", "ender_chests", true);

    private final BooleanValue shulkers =
            bool("storageEspShulkers", "shulkers", true);

    private final BooleanValue barrels =
            bool("storageEspBarrels", "barrels", true);

    private final BooleanValue other =
            bool("storageEspOther", "other", true);

    private final BooleanValue fill =
            bool("storageEspFill", "fill", true);

    private final BooleanValue outline =
            bool("storageEspOutline", "outline", true);

    private final RGBAColorValue chestColor =
            color("storageEspChestColor", "chest_color", "#FF990044");

    private final RGBAColorValue enderChestColor =
            color("storageEspEnderChestColor", "ender_chest_color", "#AA00FF44");

    private final RGBAColorValue shulkerColor =
            color("storageEspShulkerColor", "shulker_color", "#FF00AA44");

    private final RGBAColorValue barrelColor =
            color("storageEspBarrelColor", "barrel_color", "#8B5A2B44");

    private final RGBAColorValue otherColor =
            color("storageEspOtherColor", "other_color", "#AAAAAA44");

    private final List<StorageEntry> targets = new ArrayList<>();

    @Override
    public void onDisable() {
        targets.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            targets.clear();
            return;
        }

        int playerChunkX = player.getBlockX() >> 4;
        int playerChunkZ = player.getBlockZ() >> 4;
        int r = range.get();
        int chunkRadius = Math.min(16, (r + 15) >> 4);
        double maxDistSq = (double) r * r;

        List<StorageEntry> nextTargets = new ArrayList<>();

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    double distSq = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distSq > maxDistSq) continue;

                    int argb = resolveColor(be);
                    if (argb != 0) {
                        nextTargets.add(new StorageEntry(pos, argb));
                    }
                }
            }
        }

        targets.clear();
        targets.addAll(nextTargets);
    }

    private int resolveColor(BlockEntity be) {
        if (chests.get() && be instanceof ChestBlockEntity) {
            return chestColor.getArgb();
        }
        if (enderChests.get() && be instanceof EnderChestBlockEntity) {
            return enderChestColor.getArgb();
        }
        if (shulkers.get() && be instanceof ShulkerBoxBlockEntity) {
            return shulkerColor.getArgb();
        }
        if (barrels.get() && be instanceof BarrelBlockEntity) {
            return barrelColor.getArgb();
        }
        if (other.get()) {
            if (be instanceof HopperBlockEntity
                    || be instanceof DispenserBlockEntity
                    || be instanceof AbstractFurnaceBlockEntity) {
                return otherColor.getArgb();
            }
        }
        return 0;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || targets.isEmpty()) return;

        boolean doFill = fill.get();
        boolean doOutline = outline.get();

        for (StorageEntry entry : targets) {
            AABB box = new AABB(entry.pos());
            if (doFill) {
                addFilledBox(renderer, box, entry.argb());
            }
            if (doOutline) {
                addOutlineBox(renderer, box, entry.argb() | 0xFF000000);
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

    private record StorageEntry(BlockPos pos, int argb) {
    }
}
