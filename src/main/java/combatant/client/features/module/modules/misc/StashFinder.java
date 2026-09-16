/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Notifier;

import java.util.HashSet;
import java.util.Set;

@ModuleInfo(
        id = "stashfinder",
        displayName = "StashFinder",
        category = ModuleCategory.MISC,
        description = "Scans loaded chunks for high concentrations of storage blocks to locate bases and stashes."
)
public final class StashFinder extends Module {

    private final NumberValue<Integer> minChests = num("min_chests", 5, 1, 50);
    private final NumberValue<Integer> minShulkers = num("min_shulkers", 2, 1, 20);
    private final BooleanValue notifyChat = bool("notify_chat", true);

    private final Minecraft mc = Minecraft.getInstance();
    private final Set<Long> alertedChunks = new HashSet<>();

    @Override
    public void onDisable() {
        alertedChunks.clear();
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;

        for (int cx = playerChunkX - 4; cx <= playerChunkX + 4; cx++) {
            for (int cz = playerChunkZ - 4; cz <= playerChunkZ + 4; cz++) {
                long chunkKey = (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
                if (alertedChunks.contains(chunkKey)) continue;

                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                int chestCount = 0;
                int shulkerCount = 0;
                BlockPos firstPos = null;

                for (BlockEntity be : new java.util.ArrayList<>(chunk.getBlockEntities().values())) {
                    if (be instanceof ChestBlockEntity) {
                        chestCount++;
                        if (firstPos == null) firstPos = be.getBlockPos();
                    } else if (be instanceof ShulkerBoxBlockEntity) {
                        shulkerCount++;
                        if (firstPos == null) firstPos = be.getBlockPos();
                    }
                }

                if (chestCount >= minChests.get() || shulkerCount >= minShulkers.get()) {
                    alertedChunks.add(chunkKey);
                    if (notifyChat.get() && firstPos != null) {
                        Notifier.info("Stash found at " + firstPos.getX() + ", " + firstPos.getY() + ", " + firstPos.getZ()
                                + " (" + chestCount + " chests, " + shulkerCount + " shulkers)");
                    }
                }
            }
        }
    }
}
