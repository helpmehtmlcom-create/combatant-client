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
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.features.relations.CategoryService;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@ModuleInfo(
        id = "netheritefinder",
        displayName = "Netherite Finder",
        description = "Scans loaded chunks and entities for Ancient Debris, Netherite blocks, gear, and Shulker stashes.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"ancientdebrisfinder", "debrisfinder", "netheriteradar", "debrisradar"}
)
public class NetheriteFinder extends Module {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

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

    private final BooleanValue renderTracers =
            bool("netherite_tracers", "tracers", true);
    private final BooleanValue renderFill =
            bool("netherite_fill", "fill", true);
    private final BooleanValue renderOutline =
            bool("netherite_outline", "outline", true);
    private final BooleanValue renderHud =
            bool("netherite_hud", "hud_overlay", true);

    private final RGBAColorValue debrisColor =
            color("debris_color", "#FFFF9900"); // Golden Amber
    private final RGBAColorValue netheriteBlockColor =
            color("netherite_block_color", "#FF503D32"); // Dark Netherite Slate
    private final RGBAColorValue itemColor =
            color("netherite_item_color", "#FF8E44AD"); // Netherite Purple/Amethyst

    private final BooleanValue soundAlert =
            bool("netherite_sound_alert", "sound_alert", true);
    private final ModeValue soundType =
            modeSetting("netherite_sound_type", "sound_type", "Experience Orb", "Experience Orb", "Chime", "Bell", "Level Up");
    private final BooleanValue chatAlert =
            bool("netherite_chat_alert", "chat_alert", true);
    private final BooleanValue webhook =
            bool("netherite_webhook", "webhook", false);
    private final StringValue webhookUrl =
            text("netherite_webhook_url", "webhook_url", "");
    private final BooleanValue webhookPing =
            bool("netherite_webhook_ping", "webhook_ping", false);
    private final StringValue discordId =
            text("netherite_discord_id", "discord_id", "");

    private final NumberValue<Integer> minY =
            num("netherite_min_y", "min_y", -64, -64, 320);
    private final NumberValue<Integer> maxY =
            num("netherite_max_y", "max_y", 320, -64, 320);

    // Discovered targets: position key -> TargetInfo
    private final Map<BlockPos, TargetInfo> discoveredTargets = new ConcurrentHashMap<>();
    private final Set<BlockPos> notifiedPositions = ConcurrentHashMap.newKeySet();
    private int tickCounter = 0;
    public int getTargetCount() {
        return discoveredTargets.size();
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

    public record TargetInfo(BlockPos pos, TargetType type, String label, AABB box) {
    }

    public enum TargetType {
        ANCIENT_DEBRIS,
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
        notifiedPositions.clear();
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

        // 1. Scan chunk sections for blocks
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
                                        currentScan.put(pos, new TargetInfo(pos, TargetType.ANCIENT_DEBRIS, "Ancient Debris", new AABB(pos)));
                                    } else if (scanNetheriteBlocks.get() && state.is(Blocks.NETHERITE_BLOCK)) {
                                        BlockPos pos = new BlockPos(worldX, worldY, worldZ);
                                        currentScan.put(pos, new TargetInfo(pos, TargetType.NETHERITE_BLOCK, "Netherite Block", new AABB(pos)));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Scan entities (ItemFrames, ArmorStands, Dropped Items, Players)
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
                                "Item Frame: " + stack.getHoverName().getString(), frame.getBoundingBox()));
                    } else if (scanShulkerContents.get() && hasContainedNetherite(stack)) {
                        currentScan.put(ePos, new TargetInfo(ePos, TargetType.NETHERITE_ITEM,
                                "Item Frame: Shulker Box (Contains Netherite)", frame.getBoundingBox()));
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
                            "Armor Stand: " + armorName, stand.getBoundingBox()));
                }
            } else if (scanDroppedItems.get() && entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                if (!stack.isEmpty()) {
                    if (isNetherite(stack.getItem())) {
                        currentScan.put(ePos, new TargetInfo(ePos, TargetType.NETHERITE_ITEM,
                                "Item: " + stack.getHoverName().getString() + " x" + stack.getCount(), itemEntity.getBoundingBox()));
                    } else if (scanShulkerContents.get() && hasContainedNetherite(stack)) {
                        currentScan.put(ePos, new TargetInfo(ePos, TargetType.NETHERITE_ITEM,
                                "Dropped Shulker Box (Contains Netherite)", itemEntity.getBoundingBox()));
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
                                "Player: " + player.getName().getString() + " (Netherite Gear)", player.getBoundingBox()));
                    }
                }
            }
        }

        discoveredTargets.clear();
        discoveredTargets.putAll(currentScan);

        // Check for new discoveries to alert
        for (Map.Entry<BlockPos, TargetInfo> entry : currentScan.entrySet()) {
            BlockPos pos = entry.getKey();
            if (notifiedPositions.add(pos)) {
                notifyFound(entry.getValue());
            }
        }
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

    private void notifyFound(TargetInfo info) {
        if (mc.player == null) return;
        BlockPos pos = info.pos();
        double dist = Math.sqrt(mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        String coordsStr = String.format("%d %d %d", pos.getX(), pos.getY(), pos.getZ());

        if (chatAlert.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            Component coordsComponent = Component.literal(String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ()))
                    .withStyle(style -> style
                            .withColor(0x55FFFF)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.CopyToClipboard(coordsStr))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy coordinates: §f" + coordsStr))));

            Component msg = Component.literal("§6[NetheriteFinder] §aFound §e" + info.label() + " §aat ")
                    .append(coordsComponent)
                    .append(Component.literal(String.format(" §7(§f%.1fm§7 away)", dist)));

            mc.gui.hud.getChat().addClientSystemMessage(msg);
        }

        if (soundAlert.get()) {
            SoundEvent snd = switch (soundType.get()) {
                case "Chime" -> SoundEvents.NOTE_BLOCK_CHIME.value();
                case "Bell" -> SoundEvents.BELL_BLOCK;
                case "Level Up" -> SoundEvents.PLAYER_LEVELUP;
                default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            };
            mc.player.playSound(snd, 1.0f, 1.0f);
        }

        if (webhook.get() && !webhookUrl.get().isBlank()) {
            sendWebhook(info, dist, coordsStr);
        }
    }

    private void sendWebhook(TargetInfo info, double distance, String coords) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) return;

        int embedColor = switch (info.type()) {
            case ANCIENT_DEBRIS -> 0xFFA500;
            case NETHERITE_BLOCK -> 0x503D32;
            case NETHERITE_ITEM -> 0x8E44AD;
        };

        String dimension = mc.level != null ? mc.level.dimension().identifier().toString() : "unknown";
        String playerName = mc.player != null ? mc.player.getName().getString() : "Unknown";
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer";

        String pingPrefix = "";
        if (webhookPing.get() && !discordId.get().isBlank()) {
            String id = discordId.get().trim();
            pingPrefix = id.equalsIgnoreCase("everyone") ? "@everyone " : "<@" + id + "> ";
        }

        String json = String.format(
                "{\"content\":%s,\"embeds\":[{" +
                        "\"title\":\"💎 Netherite / Debris Discovered!\"," +
                        "\"color\":%d," +
                        "\"description\":\"Found **%s** at **%s** (%.1fm away)\\n*Combatant Client • DonutSMP Base Hunting*\"," +
                        "\"fields\":[" +
                        "{\"name\":\"📍 Coordinates\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"📏 Distance\",\"value\":\"`%.1fm`\",\"inline\":true}," +
                        "{\"name\":\"🌍 Dimension\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"👤 Found By\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"🖥️ Server\",\"value\":\"`%s`\",\"inline\":true}" +
                        "]" +
                        "}]}",
                pingPrefix.isEmpty() ? "null" : "\"" + escapeJson(pingPrefix.trim()) + "\"",
                embedColor,
                escapeJson(info.label()),
                coords,
                distance,
                coords,
                distance,
                escapeJson(dimension),
                escapeJson(playerName),
                escapeJson(server)
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Throwable ignored) {
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null || discoveredTargets.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();

        for (TargetInfo info : discoveredTargets.values()) {
            AABB box = info.box();
            if (box == null) continue;

            int colorArgb = resolveColor(info.type());
            int r = (colorArgb >>> 16) & 0xFF;
            int g = (colorArgb >>> 8) & 0xFF;
            int b = colorArgb & 0xFF;

            if (renderFill.get()) {
                int fillA = 45;
                renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, fillA);
                renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, fillA);
            }

            if (renderOutline.get()) {
                int lineA = 240;
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
                renderer.line(camPos.x, camPos.y, camPos.z, center.x, center.y, center.z, r, g, b, 200);
            }
        }
    }

    @Override
    public void onRenderHudEngineForeground(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor graphics, float tickDelta) {
        if (!renderHud.get() || mc.player == null || discoveredTargets.isEmpty()) return;

        int count = discoveredTargets.size();
        TargetInfo nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        Vec3 pPos = mc.player.position();

        for (TargetInfo info : discoveredTargets.values()) {
            double dSq = info.pos().distToCenterSqr(pPos.x, pPos.y, pPos.z);
            if (dSq < nearestDistSq) {
                nearestDistSq = dSq;
                nearest = info;
            }
        }

        String summary = String.format("💎 Netherite Radar: %d targets", count);
        if (nearest != null) {
            summary += String.format(" | Nearest: %s (%.0fm)", nearest.label(), Math.sqrt(nearestDistSq));
        }

        int pad = 6;
        float textW = (float) textRenderer.getWidth(summary);
        int x = 10;
        int y = 45;

        // Dark rounded backdrop
        renderer.roundedRect(x - pad, y - pad, textW + (pad * 2), 12 + (pad * 2), 4, 0xAA101015);
        renderer.roundedRectStroke(x - pad, y - pad, textW + (pad * 2), 12 + (pad * 2), 4.0f, 1.0f, 0xFF8E44AD);

        textRenderer.render(summary, x, y, new RenderColor(0xFFFFD700), true);
    }

    private int resolveColor(TargetType type) {
        return switch (type) {
            case ANCIENT_DEBRIS -> debrisColor.getArgb();
            case NETHERITE_BLOCK -> netheriteBlockColor.getArgb();
            case NETHERITE_ITEM -> itemColor.getArgb();
        };
    }
}
