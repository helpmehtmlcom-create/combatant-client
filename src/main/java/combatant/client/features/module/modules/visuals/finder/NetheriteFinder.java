/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.finder.FinderRender;
import combatant.client.util.finder.LoadedChunkScanner;
import combatant.client.util.finder.OreSimulation;
import combatant.client.util.logging.DebugLog;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Predicts ancient debris from the world seed (Meteor's OreSim technique, ported from
 * krypton). Anti-xray hides the ore, but with the seed the exact generation can be replayed,
 * then filtered against the visible world so mined or exposed spots drop out.
 */
@ModuleInfo(
        id = "netheritefinder",
        displayName = "NetheriteFinder",
        aliases = {"DebrisFinder", "OreSim"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.netheritefinder.description")
public final class NetheriteFinder extends Module {

    // DonutSMP's world seed, the reason this module exists; any other server needs its own.
    private static final String DONUT_SMP_SEED = "6608149111735331168";
    private static final int MAX_BOXES = 2048;

    // Registry building is slow and the result never changes within a game session.
    private static volatile CompletableFuture<OreSimulation> simulation;

    private final StringValue seed = text("seed", DONUT_SMP_SEED);
    private final NumberValue<Integer> rangeChunks = num("range", 6, 1, 12);
    private final RGBAColorValue fillColor = color("fill_color", "#50BF40BF");
    private final RGBAColorValue lineColor = color("line_color", "#FFD11BF5");

    private final Minecraft mc = Minecraft.getInstance();
    private final LoadedChunkScanner<List<BlockPos>> scanner =
            new LoadedChunkScanner<>(this::predict, rangeChunks::get, 6, 3000L);
    private String scannedSeed = "";

    @Override
    public void onEnable() {
        scanner.clear();
        scannedSeed = "";
        simulation();
    }

    @Override
    public void onDisable() {
        scanner.clear();
    }

    private static CompletableFuture<OreSimulation> simulation() {
        CompletableFuture<OreSimulation> current = simulation;
        if (current == null || current.isCompletedExceptionally()) {
            synchronized (NetheriteFinder.class) {
                current = simulation;
                if (current == null || current.isCompletedExceptionally()) {
                    current = CompletableFuture.supplyAsync(() -> OreSimulation.forNether(
                            OrePlacements.ORE_ANCIENT_DEBRIS_LARGE,
                            OrePlacements.ORE_ANCIENT_DEBRIS_SMALL));
                    simulation = current;
                }
            }
        }
        return current;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        scanner.onPacket(event.getPacket());
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (mc.level.dimension() != Level.NETHER) return;

        CompletableFuture<OreSimulation> future = simulation();
        if (future.isCompletedExceptionally()) {
            Throwable cause = future.handle((ok, error) -> error).join();
            DebugLog.error("NetheriteFinder could not build the ore simulation", cause);
            Notifier.error("NetheriteFinder: could not load world-gen data, see log");
            setEnabledInternal(false);
            return;
        }
        if (!future.isDone()) return;

        if (!seed.get().equals(scannedSeed)) {
            scannedSeed = seed.get();
            scanner.rescanAll();
        }
        scanner.tick(mc);
    }

    private List<BlockPos> predict(ClientLevel level, LevelChunk chunk) {
        OreSimulation sim = simulation().getNow(null);
        if (sim == null || level.dimension() != Level.NETHER) return null;
        ChunkPos pos = chunk.getPos();
        List<BlockPos> found = sim.simulate(level, parseSeed(scannedSeed), pos.x(), pos.z());
        return found.isEmpty() ? null : found;
    }

    // Numeric seeds parse as-is; anything else goes through String.hashCode, the same rule
    // vanilla applies to a seed typed into the world creation screen.
    private static long parseSeed(String text) {
        String trimmed = text == null ? "" : text.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            return trimmed.hashCode();
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        LocalPlayer player = mc.player;
        if (!isEnabled() || renderer == null || player == null || mc.level == null
                || mc.level.dimension() != Level.NETHER) return;

        FinderRender draw = FinderRender.begin(renderer, 1.0f);
        int fill = fillColor.getArgb();
        int line = lineColor.getArgb();
        int range = rangeChunks.get();
        ChunkPos center = player.chunkPosition();
        Map<Long, List<BlockPos>> results = scanner.results();
        // Walk square rings outward from the player: when MAX_BOXES cuts the list, the ore that
        // gets dropped is the farthest, not whatever the hash map happened to put last.
        int drawn = 0;
        for (int ring = 0; ring <= range; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                boolean edgeColumn = dx == -ring || dx == ring;
                for (int dz = -ring; dz <= ring; dz += edgeColumn ? 1 : 2 * ring) {
                    List<BlockPos> ore = results.get(ChunkPos.pack(center.x() + dx, center.z() + dz));
                    if (ore == null) continue;
                    for (BlockPos pos : ore) {
                        draw.blockBox(pos.getX(), pos.getY(), pos.getZ(), fill, line);
                        if (++drawn >= MAX_BOXES) return;
                    }
                }
            }
        }
    }
}
