/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ModuleInfo(
        id = "stashfinder",
        displayName = "StashFinder",
        aliases = {"BaseFinder", "ChestCounter"},
        category = ModuleCategory.MISC,
        description = "Alerts you when chunks with high concentrations of storage containers are found."
)
public final class StashFinder extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> threshold =
            num("stashFinderThreshold", "threshold", 8, 2, 50);

    private final BooleanValue chat =
            bool("stashFinderChat", "chat", true);

    private final BooleanValue sound =
            bool("stashFinderSound", "sound", true);

    private final BooleanValue shulkersCount =
            bool("stashFinderShulkers", "shulkers", true);

    private final BooleanValue alertSingleShulker =
            bool("stashFinderAlertSingleShulker", "alert_single_shulker", true);

    private final BooleanValue chestsCount =
            bool("stashFinderChests", "chests", true);

    private final BooleanValue barrelsCount =
            bool("stashFinderBarrels", "barrels", true);

    private final BooleanValue hoppersCount =
            bool("stashFinderHoppers", "hoppers", true);

    private final Set<ChunkPos> reportedChunks = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onDisable() {
        reportedChunks.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        int playerChunkX = player.getBlockX() >> 4;
        int playerChunkZ = player.getBlockZ() >> 4;
        int chunkRadius = 8;

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                ChunkPos chunkPos = new ChunkPos(cx, cz);
                if (reportedChunks.contains(chunkPos)) continue;

                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                int chests = 0;
                int shulkers = 0;
                int barrels = 0;
                int hoppers = 0;
                int others = 0;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity) {
                        chests++;
                    } else if (be instanceof ShulkerBoxBlockEntity) {
                        shulkers++;
                    } else if (be instanceof BarrelBlockEntity) {
                        barrels++;
                    } else if (be instanceof HopperBlockEntity) {
                        hoppers++;
                    } else if (be instanceof DispenserBlockEntity || be instanceof DropperBlockEntity || be instanceof AbstractFurnaceBlockEntity) {
                        others++;
                    }
                }

                int totalCount = 0;
                if (chestsCount.get()) totalCount += chests;
                if (shulkersCount.get()) totalCount += shulkers;
                if (barrelsCount.get()) totalCount += barrels;
                if (hoppersCount.get()) totalCount += hoppers;
                totalCount += others;

                boolean isStash = totalCount >= threshold.get() || (alertSingleShulker.get() && shulkers > 0);

                if (isStash) {
                    reportedChunks.add(chunkPos);

                    if (chat.get()) {
                        int blockX = cx * 16 + 8;
                        int blockZ = cz * 16 + 8;
                        String msg = String.format(
                                "Stash found at [%d, ~, %d]! Total: %d (Chests: %d, Shulkers: %d, Barrels: %d, Hoppers: %d)",
                                blockX, blockZ, totalCount, chests, shulkers, barrels, hoppers
                        );
                        CommandOutput.send(Component.literal(msg), CommandOutput.Tone.WARNING);
                    }

                    if (sound.get() && mc.player != null) {
                        mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                }
            }
        }
    }
}
