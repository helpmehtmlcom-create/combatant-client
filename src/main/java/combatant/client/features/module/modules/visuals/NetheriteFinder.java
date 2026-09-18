/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.HudPhase;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.features.relations.CategoryService;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScreenProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import combatant.client.util.world.WorldgenRng;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@ModuleInfo(
        id = "netheritefinder",
        displayName = "Netherite Finder",
        description = "Predicts Ancient Debris veins via world seed, scans loaded chunks/entities, and detects explosion craters.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"ancientdebrisfinder", "debrisfinder", "netheriteradar", "debrisradar"}
)
public class NetheriteFinder extends Module {

    private static final Predicate<BlockState> NETHERITE_OR_DEBRIS_PREDICATE =
            state -> state.is(Blocks.ANCIENT_DEBRIS) || state.is(Blocks.NETHERITE_BLOCK);

    private static final Set<Item> NETHERITE_ITEMS = Set.of(
            Items.ANCIENT_DEBRIS,
            Items.NETHERITE_BLOCK,
            Items.NETHERITE_INGOT,
            Items.NETHERITE_SCRAP,
            Items.NETHERITE_SWORD,
            Items.NETHERITE_AXE,
            Items.NETHERITE_PICKAXE,
            Items.NETHERITE_SHOVEL,
            Items.NETHERITE_HOE,
            Items.NETHERITE_HELMET,
            Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS,
            Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE
    );

    private final Minecraft mc = Minecraft.getInstance();

    // --- DonutSMP Seed & Vicinity Explosion Predictor ---
    private final BooleanValue seedPredictor =
            bool("netherite_seed_predictor", "seed_predictor", true);
    private final ModeValue seedPreset =
            modeSetting("netherite_seed_preset", "seed_preset", "DonutSMP #1", "DonutSMP #1", "DonutSMP #2", "Custom");
    private final StringValue netherSeed =
            text("netherite_nether_seed", "nether_seed", "-2803778941596086821");
    private final ModeValue rngAlgorithm =
            modeSetting("netherite_rng", "rng_type", "Xoroshiro (Modern)", "Xoroshiro (Modern)", "Legacy LCG");
    private final NumberValue<Integer> seedChunkRadius =
            num("netherite_seed_chunk_radius", "seed_radius", 6, 1, 16);
    private final BooleanValue netherOnly =
            bool("netherite_nether_only", "nether_only", false);

    private final BooleanValue hideBlownUp =
            bool("netherite_hide_blown_up", "hide_blown_up", true);
    private final BooleanValue checkVicinityExplosion =
            bool("netherite_check_explosion", "check_vicinity", true);
    private final NumberValue<Integer> explosionRadius =
            num("netherite_explosion_radius", "vicinity_radius", 3, 1, 6);
    private final NumberValue<Double> explosionAirThreshold =
            num("netherite_air_threshold", "air_threshold", 35.0, 10.0, 80.0);
    private final BooleanValue showVicinityBadges =
            bool("netherite_vicinity_badges", "world_badges", true);

    // --- Loaded Chunks & Live Entities Scanner ---
    private final NumberValue<Double> scanRange =
            num("netherite_range", "range", 96.0, 16.0, 256.0);
    private final BooleanValue scanAncientDebris =
            bool("detect_ancient_debris", "ancient_debris", true);
    private final BooleanValue scanNetheriteBlocks =
            bool("detect_netherite_blocks", "netherite_blocks", true);
    private final BooleanValue scanItemFrames =
            bool("detect_item_frames", "item_frames", true);
    private final BooleanValue scanArmorStands =
            bool("detect_armor_stands", "armor_stands", true);
    private final BooleanValue scanDroppedItems =
            bool("detect_dropped_items", "dropped_items", true);
    private final BooleanValue scanShulkerContents =
            bool("detect_shulker_contents", "shulker_contents", true);
    private final BooleanValue scanPlayers =
            bool("detect_player_gear", "players", true);

    // --- Visuals & Colors ---
    private final BooleanValue renderTracers =
            bool("netherite_tracers", "tracers", true);
    private final BooleanValue renderFill =
            bool("netherite_fill", "fill", true);
    private final BooleanValue renderOutline =
            bool("netherite_outline", "outline", true);
    private final BooleanValue renderHud =
            bool("netherite_hud", "hud_overlay", true);

    private final RGBAColorValue untouchedColor =
            color("netherite_untouched_color", "#FF00FF66"); // Vibrant Neon Emerald
    private final RGBAColorValue exposedColor =
            color("netherite_exposed_color", "#FF00E5FF"); // Cyan / Diamond
    private final RGBAColorValue blownUpColor =
            color("netherite_blown_color", "#FF666666"); // Muted Gray
    private final RGBAColorValue debrisColor =
            color("debris_color", "#FFFF9900"); // Golden Amber
    private final RGBAColorValue netheriteBlockColor =
            color("netherite_block_color", "#FF503D32"); // Dark Netherite Slate
    private final RGBAColorValue itemColor =
            color("netherite_item_color", "#FF8E44AD"); // Netherite Purple/Amethyst

    private final NumberValue<Integer> minY =
            num("netherite_min_y", "min_y", -64, -64, 320);
    private final NumberValue<Integer> maxY =
            num("netherite_max_y", "max_y", 320, -64, 320);

    // Discovered targets: position key -> TargetInfo
    private final Map<BlockPos, TargetInfo> discoveredTargets = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public int getTargetCount() {
        return discoveredTargets.size();
    }

    public int getUntouchedCount() {
        int count = 0;
        for (TargetInfo info : discoveredTargets.values()) {
            if (info.status() == VicinityStatus.UNTOUCHED || info.status() == VicinityStatus.EXPOSED) {
                count++;
            }
        }
        return count;
    }

    public int getBlownUpCount() {
        int count = 0;
        for (TargetInfo info : discoveredTargets.values()) {
            if (info.status() == VicinityStatus.BLOWN_UP) {
                count++;
            }
        }
        return count;
    }

    public TargetInfo getNearestTarget() {
        if (mc.player == null || discoveredTargets.isEmpty()) return null;
        Vec3 pPos = mc.player.position();
        TargetInfo best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (TargetInfo info : discoveredTargets.values()) {
            double dSq = info.pos().distToCenterSqr(pPos.x, pPos.y, pPos.z);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = info;
            }
        }
        return best;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.LAST;
    }

    public record TargetInfo(
            BlockPos pos,
            TargetType type,
            String label,
            AABB box,
            VicinityStatus status,
            int airCount,
            int totalChecked
    ) {
    }

    public enum VicinityStatus {
        UNTOUCHED("Untouched", 0xFF00FF66),
        EXPOSED("Exposed", 0xFF00E5FF),
        BLOWN_UP("Blown Up / Mined", 0xFFFF3333),
        UNLOADED("Predicted", 0xFFFFAA00);

        private final String label;
        private final int colorArgb;

        VicinityStatus(String label, int colorArgb) {
            this.label = label;
            this.colorArgb = colorArgb;
        }

        public String getLabel() {
            return label;
        }

        public int getColorArgb() {
            return colorArgb;
        }
    }

    public enum TargetType {
        ANCIENT_DEBRIS,
        SEED_DEBRIS,
        NETHERITE_BLOCK,
        NETHERITE_ITEM
    }

    @Override
    public void onEnable() {
        clear();
    }

    @Override
    public void onDisable() {
        clear();
    }

    private void clear() {
        discoveredTargets.clear();
        tickCounter = 0;
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter % 5 != 0) return;

        double maxDist = scanRange.get();
        double maxDistSq = maxDist * maxDist;
        Vec3 playerPos = mc.player.position();
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = (int) Math.ceil(maxDist / 16.0);

        int minHeight = minY.get();
        int maxHeight = maxY.get();

        Map<BlockPos, TargetInfo> currentScan = new HashMap<>();

        // 1. Automatic Seed-based Ancient Debris Mapper (DonutSMP Seeds)
        if (seedPredictor.get()) {
            boolean isNether = mc.level.dimension() == Level.NETHER;
            if (!netherOnly.get() || isNether) {
                long seed = resolveSeed();
                int sRad = seedChunkRadius.get();
                for (int cx = playerChunkX - sRad; cx <= playerChunkX + sRad; cx++) {
                    for (int cz = playerChunkZ - sRad; cz <= playerChunkZ + sRad; cz++) {
                        mapChunkAncientDebris(cx, cz, seed, currentScan);
                    }
                }
            }
        }

        // 2. Scan chunk sections for live physically placed blocks
        if (scanAncientDebris.get() || scanNetheriteBlocks.get()) {
            for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
                for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                    LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                    if (chunk == null || chunk.isEmpty()) continue;

                    LevelChunkSection[] sections = chunk.getSections();
                    for (int sIdx = 0; sIdx < sections.length; sIdx++) {
                        LevelChunkSection section = sections[sIdx];
                        if (section == null || section.hasOnlyAir() || !section.maybeHas(NETHERITE_OR_DEBRIS_PREDICATE)) {
                            continue;
                        }

                        int sectionY = chunk.getSectionYFromSectionIndex(sIdx);
                        int baseX = SectionPos.sectionToBlockCoord(cx);
                        int baseY = SectionPos.sectionToBlockCoord(sectionY);
                        int baseZ = SectionPos.sectionToBlockCoord(cz);

                        for (int y = 0; y < 16; y++) {
                            int worldY = baseY + y;
                            if (worldY < minHeight || worldY > maxHeight || !mc.level.isInsideBuildHeight(worldY)) {
                                continue;
                            }

                            for (int z = 0; z < 16; z++) {
                                int worldZ = baseZ + z;
                                for (int x = 0; x < 16; x++) {
                                    int worldX = baseX + x;
                                    double dx = (worldX + 0.5) - playerPos.x;
                                    double dy = (worldY + 0.5) - playerPos.y;
                                    double dz = (worldZ + 0.5) - playerPos.z;
                                    if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;

                                    BlockState state = section.getBlockState(x, y, z);
                                    if (scanAncientDebris.get() && state.is(Blocks.ANCIENT_DEBRIS)) {
                                        BlockPos pos = new BlockPos(worldX, worldY, worldZ);
                                        currentScan.put(pos, new TargetInfo(pos, TargetType.ANCIENT_DEBRIS, "Ancient Debris (Live)", new AABB(pos), VicinityStatus.UNTOUCHED, 0, 0));
                                    } else if (scanNetheriteBlocks.get() && state.is(Blocks.NETHERITE_BLOCK)) {
                                        BlockPos pos = new BlockPos(worldX, worldY, worldZ);
                                        currentScan.put(pos, new TargetInfo(pos, TargetType.NETHERITE_BLOCK, "Netherite Block", new AABB(pos), VicinityStatus.UNTOUCHED, 0, 0));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Scan entities (ItemFrames, ArmorStands, Dropped Items, Players)
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null) continue;
            double distSq = entity.distanceToSqr(playerPos);
            if (distSq > maxDistSq) continue;

            BlockPos ePos = entity.blockPosition();
            if (ePos.getY() < minHeight || ePos.getY() > maxHeight) continue;

            if (scanItemFrames.get() && entity instanceof ItemFrame frame) {
                ItemStack stack = frame.getItem();
                if (!stack.isEmpty()) {
                    if (isNetherite(stack.getItem())) {
                        currentScan.put(ePos, new TargetInfo(ePos, TargetType.NETHERITE_ITEM,
                                "Item Frame: " + stack.getHoverName().getString(), frame.getBoundingBox(), VicinityStatus.EXPOSED, 0, 0));
                    } else if (scanShulkerContents.get() && hasContainedNetherite(stack)) {
                        currentScan.put(ePos, new TargetInfo(ePos, TargetType.NETHERITE_ITEM,
                                "Item Frame: Shulker Box (Contains Netherite)", frame.getBoundingBox(), VicinityStatus.EXPOSED, 0, 0));
                    }
                }
            } else if (scanArmorStands.get() && entity instanceof ArmorStand stand) {
                boolean hasNetherite = false;
                String armorName = "";
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack piece = stand.getItemBySlot(slot);
                    if (!piece.isEmpty()) {
                        if (isNetherite(piece.getItem())) {
                            hasNetherite = true;
                            armorName = piece.getHoverName().getString();
                            break;
                        } else if (scanShulkerContents.get() && hasContainedNetherite(piece)) {
                            hasNetherite = true;
                            armorName = "Shulker Box (Contains Netherite)";
                            break;
                        }
                    }
                }
                if (hasNetherite) {
                    currentScan.put(ePos, new TargetInfo(ePos, TargetType.NETHERITE_ITEM,
                            "Armor Stand: " + armorName, stand.getBoundingBox(), VicinityStatus.EXPOSED, 0, 0));
                }
            } else if (scanDroppedItems.get() && entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                if (!stack.isEmpty()) {
                    if (isNetherite(stack.getItem())) {
                        currentScan.put(ePos, new TargetInfo(ePos, TargetType.NETHERITE_ITEM,
                                "Item: " + stack.getHoverName().getString() + " x" + stack.getCount(), itemEntity.getBoundingBox(), VicinityStatus.EXPOSED, 0, 0));
                    } else if (scanShulkerContents.get() && hasContainedNetherite(stack)) {
                        currentScan.put(ePos, new TargetInfo(ePos, TargetType.NETHERITE_ITEM,
                                "Dropped Shulker Box (Contains Netherite)", itemEntity.getBoundingBox(), VicinityStatus.EXPOSED, 0, 0));
                    }
                }
            } else if (scanPlayers.get() && entity instanceof Player player && player != mc.player) {
                if (!CategoryService.isFriend(player)) {
                    boolean hasNetherite = false;
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        ItemStack piece = player.getItemBySlot(slot);
                        if (!piece.isEmpty() && isNetherite(piece.getItem())) {
                            hasNetherite = true;
                            break;
                        }
                    }
                    if (hasNetherite) {
                        currentScan.put(ePos, new TargetInfo(ePos, TargetType.NETHERITE_ITEM,
                                "Player: " + player.getName().getString() + " (Netherite Gear)", player.getBoundingBox(), VicinityStatus.EXPOSED, 0, 0));
                    }
                }
            }
        }

        discoveredTargets.clear();
        discoveredTargets.putAll(currentScan);
    }

    private boolean isNetherite(Item item) {
        return item != null && NETHERITE_ITEMS.contains(item);
    }

    private boolean hasContainedNetherite(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            return container.nonEmptyItemCopyStream().anyMatch(sub -> isNetherite(sub.getItem()));
        }
        return false;
    }

    private long resolveSeed() {
        String preset = seedPreset.get();
        if ("DonutSMP #1".equalsIgnoreCase(preset)) {
            return -2803778941596086821L;
        } else if ("DonutSMP #2".equalsIgnoreCase(preset)) {
            return 6608149111735331168L;
        }
        try {
            return Long.parseLong(netherSeed.get().trim());
        } catch (NumberFormatException e) {
            return (long) netherSeed.get().trim().hashCode();
        }
    }

    private WorldgenRng createWorldgenRandom(long seed) {
        if ("Legacy LCG".equalsIgnoreCase(rngAlgorithm.get())) {
            return WorldgenRng.legacy(seed);
        }
        return WorldgenRng.create(seed);
    }

    private void mapChunkAncientDebris(int chunkX, int chunkZ, long worldSeed, Map<BlockPos, TargetInfo> targetMap) {
        WorldgenRng rng = createWorldgenRandom(worldSeed);
        long decorationSeed = rng.setDecorationSeed(worldSeed, chunkX << 4, chunkZ << 4);

        // 1. Large Vein (Feature 4, Step 6, size 3, triangular Y: 8..24)
        rng.setFeatureSeed(decorationSeed, 4, 6);
        int lx = (chunkX << 4) + rng.nextInt(16);
        int lz = (chunkZ << 4) + rng.nextInt(16);
        int ly = 8 + rng.nextInt(9) + rng.nextInt(9);
        int lCount = rng.nextInt(4); // 0 to 3 blocks
        for (int i = 0; i < lCount; i++) {
            int radius = Math.min(i, 7);
            int dx = Math.round((rng.nextFloat() - rng.nextFloat()) * (float) radius);
            int dy = Math.round((rng.nextFloat() - rng.nextFloat()) * (float) radius);
            int dz = Math.round((rng.nextFloat() - rng.nextFloat()) * (float) radius);
            BlockPos pos = new BlockPos(lx + dx, ly + dy, lz + dz);
            processPredictedDebris(pos, "Ancient Debris (Vein #1)", targetMap);
        }

        // 2. Small Vein (Feature 5, Step 6, size 2, uniform Y: 8..119)
        rng.setFeatureSeed(decorationSeed, 5, 6);
        int sx = (chunkX << 4) + rng.nextInt(16);
        int sz = (chunkZ << 4) + rng.nextInt(16);
        int sy = 8 + rng.nextInt(112);
        int sCount = rng.nextInt(3); // 0 to 2 blocks
        for (int i = 0; i < sCount; i++) {
            int radius = Math.min(i, 7);
            int dx = Math.round((rng.nextFloat() - rng.nextFloat()) * (float) radius);
            int dy = Math.round((rng.nextFloat() - rng.nextFloat()) * (float) radius);
            int dz = Math.round((rng.nextFloat() - rng.nextFloat()) * (float) radius);
            BlockPos pos = new BlockPos(sx + dx, sy + dy, sz + dz);
            processPredictedDebris(pos, "Ancient Debris (Vein #2)", targetMap);
        }
    }

    private void processPredictedDebris(BlockPos pos, String label, Map<BlockPos, TargetInfo> targetMap) {
        if (targetMap.containsKey(pos)) return;
        if (pos.getY() < minY.get() || pos.getY() > maxY.get()) return;

        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        LevelChunk chunk = mc.level != null ? mc.level.getChunkSource().getChunk(cx, cz, false) : null;

        VicinityStatus status;
        int airCount = 0;
        int totalChecked = 0;

        if (chunk == null || chunk.isEmpty()) {
            status = VicinityStatus.UNLOADED;
        } else {
            // Chunk is loaded! Check block and its vicinity
            BlockState centerState = mc.level.getBlockState(pos);
            boolean centerDebris = centerState.is(Blocks.ANCIENT_DEBRIS);

            int r = explosionRadius.get();
            boolean nearbyDebris = centerDebris;

            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx * dx + dy * dy + dz * dz > r * r) continue;
                        totalChecked++;
                        BlockPos checkPos = pos.offset(dx, dy, dz);
                        BlockState s = mc.level.getBlockState(checkPos);
                        if (s.isAir() || s.is(Blocks.CAVE_AIR)) {
                            airCount++;
                        } else if (s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.LAVA)) {
                            airCount++;
                        } else if (s.is(Blocks.ANCIENT_DEBRIS)) {
                            nearbyDebris = true;
                        }
                    }
                }
            }

            double airRatio = totalChecked > 0 ? (airCount * 100.0 / totalChecked) : 0.0;
            double threshold = explosionAirThreshold.get();

            if (nearbyDebris) {
                if (airRatio >= threshold) {
                    status = VicinityStatus.EXPOSED; // Exposed in blast crater!
                } else {
                    status = VicinityStatus.UNTOUCHED; // Intact in solid rock!
                }
            } else {
                if (centerState.isAir() || centerState.is(Blocks.CAVE_AIR) || centerState.is(Blocks.FIRE) || centerState.is(Blocks.LAVA) || airRatio >= threshold) {
                    status = VicinityStatus.BLOWN_UP; // Blown up / cratered / mined out!
                } else {
                    status = VicinityStatus.UNTOUCHED; // Solid virgin netherrack
                }
            }
        }

        if (hideBlownUp.get() && status == VicinityStatus.BLOWN_UP) {
            return;
        }

        AABB box = new AABB(pos);
        targetMap.put(pos, new TargetInfo(pos, TargetType.SEED_DEBRIS, label, box, status, airCount, totalChecked));
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null || discoveredTargets.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();

        for (TargetInfo info : discoveredTargets.values()) {
            AABB box = info.box();
            if (box == null) continue;

            int colorArgb = resolveColor(info);
            int r = (colorArgb >>> 16) & 0xFF;
            int g = (colorArgb >>> 8) & 0xFF;
            int b = colorArgb & 0xFF;

            if (renderFill.get()) {
                int fillA = (info.status() == VicinityStatus.BLOWN_UP) ? 25 : 55;
                renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, fillA);
                renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, fillA);
            }

            if (renderOutline.get()) {
                int lineA = (info.status() == VicinityStatus.BLOWN_UP) ? 120 : 240;
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

            if (renderTracers.get() && (info.status() == VicinityStatus.UNTOUCHED || info.status() == VicinityStatus.EXPOSED)) {
                Vec3 center = box.getCenter();
                renderer.line(camPos.x, camPos.y, camPos.z, center.x, center.y, center.z, r, g, b, 200);
            }
        }
    }

    @Override
    public void onRenderHudEngineForeground(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor graphics, float tickDelta) {
        if (mc.player == null || discoveredTargets.isEmpty()) return;

        // 1. Floating World-Space Nametags / Billboard Status Badges
        if (showVicinityBadges.get()) {
            for (TargetInfo info : discoveredTargets.values()) {
                BlockPos p = info.pos();
                double distSq = mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                if (distSq > 128.0 * 128.0) continue;
                double dist = Math.sqrt(distSq);

                Vec3 anchor = new Vec3(p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5);
                Vec3 screen = ScreenProjection.worldToScreen(anchor, tickDelta);
                if (screen == null) continue;

                String statusTag = switch (info.status()) {
                    case UNTOUCHED -> "✓ UNTOUCHED";
                    case EXPOSED -> "⚡ EXPOSED";
                    case BLOWN_UP -> "✗ BLOWN UP";
                    case UNLOADED -> "? PREDICTED";
                };
                int statusCol = switch (info.status()) {
                    case UNTOUCHED -> 0xFF00FF66;
                    case EXPOSED -> 0xFF00E5FF;
                    case BLOWN_UP -> 0xFFFF3333;
                    case UNLOADED -> 0xFFFFAA00;
                };

                String title = String.format("Ancient Debris (%.0fm)", dist);
                float w1 = (float) textRenderer.getWidth(title);
                float w2 = (float) textRenderer.getWidth(statusTag);
                float maxW = Math.max(w1, w2);
                float badgeX = (float) screen.x - (maxW / 2.0f);
                float badgeY = (float) screen.y - 18.0f;
                float pad = 4.0f;

                renderer.roundedRect(badgeX - pad, badgeY - pad, maxW + pad * 2, 20 + pad * 2, 3, 0xCC101218);
                renderer.roundedRectStroke(badgeX - pad, badgeY - pad, maxW + pad * 2, 20 + pad * 2, 3.0f, 1.0f, statusCol);

                textRenderer.render(title, (int) badgeX, (int) badgeY, new RenderColor(0xFFFFFFFF), true);
                textRenderer.render(statusTag, (int) badgeX, (int) (badgeY + 10), new RenderColor(statusCol), true);
            }
        }

        // 2. On-screen summary HUD
        if (renderHud.get()) {
            int untouched = getUntouchedCount();
            int blown = getBlownUpCount();
            TargetInfo nearest = getNearestTarget();

            String summary = String.format("💎 Netherite: %d untouched", untouched);
            if (blown > 0) {
                summary += String.format(" | %d blown up", blown);
            }
            if (nearest != null && mc.player != null) {
                double d = Math.sqrt(mc.player.distanceToSqr(nearest.pos().getX() + 0.5, nearest.pos().getY() + 0.5, nearest.pos().getZ() + 0.5));
                summary += String.format(" • Nearest: %.0fm", d);
            }

            int pad = 6;
            float textW = (float) textRenderer.getWidth(summary);
            int x = 10;
            int y = 45;

            renderer.roundedRect(x - pad, y - pad, textW + (pad * 2), 12 + (pad * 2), 4, 0xAA101015);
            renderer.roundedRectStroke(x - pad, y - pad, textW + (pad * 2), 12 + (pad * 2), 4.0f, 1.0f, 0xFF00FF66);

            textRenderer.render(summary, x, y, new RenderColor(0xFF00FF66), true);
        }
    }

    private int resolveColor(TargetInfo info) {
        if (info.type() == TargetType.SEED_DEBRIS || info.type() == TargetType.ANCIENT_DEBRIS) {
            return switch (info.status()) {
                case UNTOUCHED -> untouchedColor.getArgb();
                case EXPOSED -> exposedColor.getArgb();
                case BLOWN_UP -> blownUpColor.getArgb();
                case UNLOADED -> debrisColor.getArgb();
            };
        }
        return switch (info.type()) {
            case ANCIENT_DEBRIS, SEED_DEBRIS -> debrisColor.getArgb();
            case NETHERITE_BLOCK -> netheriteBlockColor.getArgb();
            case NETHERITE_ITEM -> itemColor.getArgb();
        };
    }
}
