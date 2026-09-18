/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;
import combatant.client.features.module.WorldPhase;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.render.engine.RenderState;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@ModuleInfo(
        id = "extraesp",
        displayName = "Extra ESP",
        description = "Detects underground player life, vines, kelp, mobs, and block anomalies indicating hidden bases.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"baseextraesp", "undergroundlife", "donutextraesp"}
)
public class ExtraESP extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue detectVines =
            bool("extra_vines", "detect_vines", true);
    private final BooleanValue detectKelp =
            bool("extra_kelp", "detect_kelp", true);
    private final BooleanValue detectVillagers =
            bool("extra_villagers", "detect_villagers", true);
    private final BooleanValue detectZombieVillagers =
            bool("extra_zombie_villagers", "detect_zombie_villagers", true);
    private final BooleanValue detectPillagers =
            bool("extra_pillagers", "detect_pillagers", true);
    private final BooleanValue detectWanderingTraders =
            bool("extra_traders", "detect_wandering_traders", true);
    private final BooleanValue detectDeepslate =
            bool("extra_deepslate", "detect_deepslate", false);
    private final BooleanValue detectRotatedDeepslate =
            bool("extra_rot_deepslate", "detect_rot_deepslate", true);
    private final BooleanValue detectLlamas =
            bool("extra_llamas", "detect_llamas", true);
    private final BooleanValue detectAmethyst =
            bool("extra_amethyst", "detect_amethyst", true);

    private final BooleanValue renderTracers =
            bool("extra_tracers", "tracers", false);
    private final BooleanValue renderFill =
            bool("extra_fill", "fill", true);
    private final BooleanValue renderOutline =
            bool("extra_outline", "outline", true);
    private final ModeValue scanSpeed =
            modeSetting("extra_scan_speed", "scan_speed", "Medium", "Slow", "Medium", "Fast");
    private final NumberValue<Double> scanRange =
            num("extra_range", "range", 96.0, 16.0, 192.0);

    private final RGBAColorValue vineColor =
            color("extra_vine_color", "#FF0EE80E");
    private final RGBAColorValue kelpColor =
            color("extra_kelp_color", "#FF2BB52B");
    private final RGBAColorValue deepslateColor =
            color("extra_deepslate_color", "#FF0FB1E5");
    private final RGBAColorValue rotDeepslateColor =
            color("extra_rot_deepslate_color", "#FFFFC0CB");
    private final RGBAColorValue amethystColor =
            color("extra_amethyst_color", "#FF630CE6");
    private final RGBAColorValue villagerColor =
            color("extra_villager_color", "#FF3CB371");
    private final RGBAColorValue zombieVillagerColor =
            color("extra_zombie_villager_color", "#FF228B22");
    private final RGBAColorValue pillagerColor =
            color("extra_pillager_color", "#FF696969");
    private final RGBAColorValue llamaColor =
            color("extra_llama_color", "#FFDEB887");
    private final RGBAColorValue traderColor =
            color("extra_trader_color", "#FF1E90FF");

    public record DiscoveredTarget(BlockPos pos, AABB box, int colorArgb, String type) {
    }

    private final Map<BlockPos, DiscoveredTarget> targets = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        targets.clear();
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        targets.clear();
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        int speedInterval = switch (scanSpeed.get()) {
            case "Fast" -> 2;
            case "Slow" -> 10;
            default -> 5;
        };

        tickCounter++;
        if (tickCounter % speedInterval != 0) return;

        double maxDist = scanRange.get();
        double maxDistSq = maxDist * maxDist;
        Vec3 playerPos = mc.player.position();
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = (int) Math.ceil(maxDist / 16.0);

        Map<BlockPos, DiscoveredTarget> nextTargets = new HashMap<>();

        // 1. Scan chunk sections for blocks
        boolean scanBlocks = detectVines.get() || detectKelp.get() || detectDeepslate.get()
                || detectRotatedDeepslate.get() || detectAmethyst.get();

        if (scanBlocks) {
            Predicate<BlockState> blockPredicate = state -> {
                if (detectVines.get() && isVine(state)) return true;
                if (detectKelp.get() && isKelp(state)) return true;
                if (detectDeepslate.get() && isDeepslate(state)) return true;
                if (detectRotatedDeepslate.get() && isRotatedDeepslate(state)) return true;
                if (detectAmethyst.get() && isAmethyst(state)) return true;
                return false;
            };

            for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
                for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                    LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                    if (chunk == null || chunk.isEmpty()) continue;

                    LevelChunkSection[] sections = chunk.getSections();
                    for (int sIdx = 0; sIdx < sections.length; sIdx++) {
                        LevelChunkSection section = sections[sIdx];
                        if (section == null || section.hasOnlyAir() || !section.maybeHas(blockPredicate)) continue;

                        int sectionY = chunk.getSectionYFromSectionIndex(sIdx);
                        int baseX = SectionPos.sectionToBlockCoord(cx);
                        int baseY = SectionPos.sectionToBlockCoord(sectionY);
                        int baseZ = SectionPos.sectionToBlockCoord(cz);

                        for (int y = 0; y < 16; y++) {
                            int worldY = baseY + y;
                            if (!mc.level.isInsideBuildHeight(worldY)) continue;

                            for (int z = 0; z < 16; z++) {
                                int worldZ = baseZ + z;
                                for (int x = 0; x < 16; x++) {
                                    int worldX = baseX + x;
                                    double dx = (worldX + 0.5) - playerPos.x;
                                    double dy = (worldY + 0.5) - playerPos.y;
                                    double dz = (worldZ + 0.5) - playerPos.z;
                                    if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;

                                    BlockState state = section.getBlockState(x, y, z);
                                    BlockPos pos = new BlockPos(worldX, worldY, worldZ);

                                    if (detectRotatedDeepslate.get() && isRotatedDeepslate(state)) {
                                        nextTargets.put(pos, new DiscoveredTarget(pos, new AABB(pos), rotDeepslateColor.getArgb(), "Rotated Deepslate"));
                                    } else if (detectDeepslate.get() && isDeepslate(state)) {
                                        nextTargets.put(pos, new DiscoveredTarget(pos, new AABB(pos), deepslateColor.getArgb(), "Deepslate"));
                                    } else if (detectVines.get() && isVine(state)) {
                                        nextTargets.put(pos, new DiscoveredTarget(pos, new AABB(pos), vineColor.getArgb(), "Vine"));
                                    } else if (detectKelp.get() && isKelp(state)) {
                                        nextTargets.put(pos, new DiscoveredTarget(pos, new AABB(pos), kelpColor.getArgb(), "Kelp"));
                                    } else if (detectAmethyst.get() && isAmethyst(state)) {
                                        nextTargets.put(pos, new DiscoveredTarget(pos, new AABB(pos), amethystColor.getArgb(), "Amethyst"));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Scan entities
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null) continue;
            double dSq = entity.distanceToSqr(playerPos);
            if (dSq > maxDistSq) continue;

            BlockPos ePos = entity.blockPosition();

            if (detectVillagers.get() && entity instanceof Villager) {
                nextTargets.put(ePos, new DiscoveredTarget(ePos, entity.getBoundingBox(), villagerColor.getArgb(), "Villager"));
            } else if (detectZombieVillagers.get() && entity instanceof ZombieVillager) {
                nextTargets.put(ePos, new DiscoveredTarget(ePos, entity.getBoundingBox(), zombieVillagerColor.getArgb(), "Zombie Villager"));
            } else if (detectPillagers.get() && entity instanceof Pillager) {
                nextTargets.put(ePos, new DiscoveredTarget(ePos, entity.getBoundingBox(), pillagerColor.getArgb(), "Pillager"));
            } else if (detectWanderingTraders.get() && entity instanceof WanderingTrader) {
                nextTargets.put(ePos, new DiscoveredTarget(ePos, entity.getBoundingBox(), traderColor.getArgb(), "Wandering Trader"));
            } else if (detectLlamas.get() && (entity instanceof Llama || entity instanceof TraderLlama)) {
                nextTargets.put(ePos, new DiscoveredTarget(ePos, entity.getBoundingBox(), llamaColor.getArgb(), "Llama"));
            }
        }

        targets.clear();
        targets.putAll(nextTargets);
    }

    private static boolean isVine(BlockState state) {
        return state.is(Blocks.VINE)
                || state.is(Blocks.CAVE_VINES)
                || state.is(Blocks.CAVE_VINES_PLANT)
                || state.is(Blocks.TWISTING_VINES)
                || state.is(Blocks.TWISTING_VINES_PLANT)
                || state.is(Blocks.WEEPING_VINES)
                || state.is(Blocks.WEEPING_VINES_PLANT);
    }

    private static boolean isKelp(BlockState state) {
        return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
    }

    private static boolean isDeepslate(BlockState state) {
        return state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(Blocks.POLISHED_DEEPSLATE);
    }

    private static boolean isRotatedDeepslate(BlockState state) {
        if (!state.is(Blocks.DEEPSLATE) && !state.is(Blocks.COBBLED_DEEPSLATE) && !state.is(Blocks.POLISHED_DEEPSLATE)) {
            return false;
        }
        if (state.hasProperty(RotatedPillarBlock.AXIS)) {
            Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
            return axis == Direction.Axis.X || axis == Direction.Axis.Z;
        }
        return false;
    }

    private static boolean isAmethyst(BlockState state) {
        return state.is(Blocks.AMETHYST_CLUSTER)
                || state.is(Blocks.LARGE_AMETHYST_BUD)
                || state.is(Blocks.MEDIUM_AMETHYST_BUD)
                || state.is(Blocks.SMALL_AMETHYST_BUD)
                || state.is(Blocks.BUDDING_AMETHYST)
                || state.is(Blocks.AMETHYST_BLOCK);
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null || targets.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();

        for (DiscoveredTarget target : targets.values()) {
            AABB box = target.box();
            if (box == null) continue;

            int argb = target.colorArgb();
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;

            if (renderFill.get()) {
                int fillA = 40;
                renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, fillA);
                renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, fillA);
            }

            if (renderOutline.get()) {
                int lineA = 220;
                renderer.line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, lineA);
                renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, lineA);
                renderer.line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, lineA);
                renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, r, g, b, lineA);

                renderer.line(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, lineA);
                renderer.line(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, lineA);
                renderer.line(box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, lineA);
                renderer.line(box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, lineA);

                renderer.line(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, r, g, b, lineA);
                renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, lineA);
                renderer.line(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, r, g, b, lineA);
                renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, lineA);
            }

            if (renderTracers.get()) {
                Vec3 center = box.getCenter();
                Vec3 start = RenderState.tracerOrigin();
                renderer.line(start.x, start.y, start.z, center.x, center.y, center.z, r, g, b, 180);
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }
}
