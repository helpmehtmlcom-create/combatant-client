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
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "stashfinder",
        displayName = "Stash Finder",
        description = "Detects hidden storage stashes, shulker vaults, and mob spawners with disconnect protection.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"basefinder", "chestfinder", "vaultfinder"}
)
public class StashFinder extends Module {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue detectChests =
            bool("stash_chests", "chests", true);
    private final BooleanValue detectBarrels =
            bool("stash_barrels", "barrels", true);
    private final BooleanValue detectShulkers =
            bool("stash_shulkers", "shulkers", true);
    private final BooleanValue detectEnderChests =
            bool("stash_echests", "ender_chests", true);
    private final BooleanValue detectFurnaces =
            bool("stash_furnaces", "furnaces", true);
    private final BooleanValue detectDispensers =
            bool("stash_dispensers", "dispensers_droppers", true);
    private final BooleanValue detectHoppers =
            bool("stash_hoppers", "hoppers", true);
    private final BooleanValue detectSpawners =
            bool("stash_spawners", "spawners", true);

    private final NumberValue<Integer> minStorageCount =
            num("stash_min_count", "min_storage_count", 4, 1, 100);
    private final NumberValue<Integer> minDistanceFromSpawn =
            num("stash_min_dist_spawn", "min_distance", 1000, 0, 10000);
    private final BooleanValue criticalSpawner =
            bool("stash_crit_spawner", "critical_spawner", true);
    private final BooleanValue disconnectOnFind =
            bool("stash_disconnect", "disconnect_on_find", false);

    private final BooleanValue sendNotifications =
            bool("stash_notifications", "send_notifications", true);
    private final BooleanValue enableWebhook =
            bool("stash_webhook", "enable_webhook", false);
    private final StringValue webhookUrl =
            text("stash_webhook_url", "webhook_url", "");
    private final BooleanValue selfPing =
            bool("stash_self_ping", "self_ping", false);
    private final StringValue discordId =
            text("stash_discord_id", "discord_id", "");

    private final BooleanValue renderTracers =
            bool("stash_tracers", "tracers", true);
    private final BooleanValue renderFill =
            bool("stash_fill", "fill", true);
    private final BooleanValue renderOutline =
            bool("stash_outline", "outline", true);
    private final RGBAColorValue stashColor =
            color("stash_color", "#FF00FFCC"); // Aqua / Teal

    public record DiscoveredStash(BlockPos centerPos, int containerCount, boolean hasSpawner,
                                  Map<String, Integer> counts, AABB box) {
    }

    private final Map<BlockPos, DiscoveredStash> discoveredStashes = new ConcurrentHashMap<>();
    private final Set<BlockPos> notifiedStashes = ConcurrentHashMap.newKeySet();
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        discoveredStashes.clear();
        notifiedStashes.clear();
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        discoveredStashes.clear();
        notifiedStashes.clear();
    }

    public int getDiscoveredCount() {
        return discoveredStashes.size();
    }

    public DiscoveredStash getNearestStash() {
        if (mc.player == null || discoveredStashes.isEmpty()) return null;
        Vec3 pPos = mc.player.position();
        DiscoveredStash best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (DiscoveredStash s : discoveredStashes.values()) {
            double dSq = s.centerPos().distToCenterSqr(pPos.x, pPos.y, pPos.z);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = s;
            }
        }
        return best;
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter % 10 != 0) return;

        // Check distance from spawn
        double spawnDistSq = mc.player.getX() * mc.player.getX() + mc.player.getZ() * mc.player.getZ();
        int minDist = minDistanceFromSpawn.get();
        if (spawnDistSq < (long) minDist * minDist) {
            return;
        }

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int radius = 6;

        List<BlockEntity> foundContainers = new ArrayList<>();

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null || chunk.isEmpty()) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (isTargetContainer(be)) {
                        foundContainers.add(be);
                    }
                }
            }
        }

        // Cluster containers together within 16 blocks
        Map<BlockPos, DiscoveredStash> currentScan = new HashMap<>();
        List<List<BlockEntity>> clusters = new ArrayList<>();

        for (BlockEntity be : foundContainers) {
            boolean added = false;
            BlockPos pos = be.getBlockPos();
            for (List<BlockEntity> cluster : clusters) {
                for (BlockEntity existing : cluster) {
                    if (existing.getBlockPos().distSqr(pos) <= 256.0) { // 16 blocks
                        cluster.add(be);
                        added = true;
                        break;
                    }
                }
                if (added) break;
            }
            if (!added) {
                List<BlockEntity> newCluster = new ArrayList<>();
                newCluster.add(be);
                clusters.add(newCluster);
            }
        }

        int requiredCount = minStorageCount.get();
        boolean allowCritSpawner = criticalSpawner.get();

        for (List<BlockEntity> cluster : clusters) {
            boolean hasSpwn = false;
            Map<String, Integer> counts = new HashMap<>();
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

            for (BlockEntity be : cluster) {
                BlockPos p = be.getBlockPos();
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX() + 1);
                maxY = Math.max(maxY, p.getY() + 1);
                maxZ = Math.max(maxZ, p.getZ() + 1);

                String name = getContainerTypeName(be);
                counts.put(name, counts.getOrDefault(name, 0) + 1);
                if (be instanceof SpawnerBlockEntity) {
                    hasSpwn = true;
                }
            }

            int totalCount = cluster.size();
            if (totalCount >= requiredCount || (allowCritSpawner && hasSpwn)) {
                BlockPos center = new BlockPos((int) ((minX + maxX) / 2), (int) ((minY + maxY) / 2), (int) ((minZ + maxZ) / 2));
                AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
                currentScan.put(center, new DiscoveredStash(center, totalCount, hasSpwn, counts, box));
            }
        }

        discoveredStashes.clear();
        discoveredStashes.putAll(currentScan);

        for (Map.Entry<BlockPos, DiscoveredStash> entry : currentScan.entrySet()) {
            BlockPos pos = entry.getKey();
            if (notifiedStashes.add(pos)) {
                notifyStash(entry.getValue());
                if (disconnectOnFind.get() && mc.getConnection() != null && mc.getConnection().getConnection() != null) {
                    mc.getConnection().getConnection().disconnect(
                            Component.literal("§a[StashFinder] Disconnected safely! Stash found at " + pos.toShortString())
                    );
                    return;
                }
            }
        }
    }

    private boolean isTargetContainer(BlockEntity be) {
        if (be == null) return false;
        if (detectChests.get() && (be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity)) return true;
        if (detectBarrels.get() && be instanceof BarrelBlockEntity) return true;
        if (detectShulkers.get() && be instanceof ShulkerBoxBlockEntity) return true;
        if (detectEnderChests.get() && be instanceof EnderChestBlockEntity) return true;
        if (detectFurnaces.get() && (be instanceof AbstractFurnaceBlockEntity)) return true;
        if (detectDispensers.get() && (be instanceof DispenserBlockEntity || be instanceof DropperBlockEntity)) return true;
        if (detectHoppers.get() && be instanceof HopperBlockEntity) return true;
        if (detectSpawners.get() && be instanceof SpawnerBlockEntity) return true;
        return false;
    }

    private String getContainerTypeName(BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity) return "Shulker";
        if (be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity) return "Chest";
        if (be instanceof BarrelBlockEntity) return "Barrel";
        if (be instanceof EnderChestBlockEntity) return "Ender Chest";
        if (be instanceof SpawnerBlockEntity) return "Spawner";
        if (be instanceof HopperBlockEntity) return "Hopper";
        if (be instanceof DispenserBlockEntity || be instanceof DropperBlockEntity) return "Dispenser";
        if (be instanceof AbstractFurnaceBlockEntity) return "Furnace";
        return "Container";
    }

    private void notifyStash(DiscoveredStash stash) {
        if (mc.player == null) return;
        BlockPos pos = stash.centerPos();
        double dist = Math.sqrt(mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        String coordsStr = String.format("%d %d %d", pos.getX(), pos.getY(), pos.getZ());

        StringBuilder breakdown = new StringBuilder();
        stash.counts().forEach((k, v) -> {
            if (!breakdown.isEmpty()) breakdown.append(", ");
            breakdown.append(v).append("x ").append(k);
        });

        if (sendNotifications.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            Component coordsComponent = Component.literal(String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ()))
                    .withStyle(style -> style
                            .withColor(0x55FFFF)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.CopyToClipboard(coordsStr))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy coordinates: §f" + coordsStr))));

            Component msg = Component.literal("§d[StashFinder] §aFound Base Stash (" + stash.containerCount() + " containers: §e" + breakdown + "§a) at ")
                    .append(coordsComponent)
                    .append(Component.literal(String.format(" §7(§f%.1fm§7 away)", dist)));

            mc.gui.hud.getChat().addClientSystemMessage(msg);
        }

        mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);

        if (enableWebhook.get() && !webhookUrl.get().isBlank()) {
            sendWebhook(stash, dist, coordsStr, breakdown.toString());
        }
    }

    private void sendWebhook(DiscoveredStash stash, double distance, String coords, String breakdown) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) return;

        String dimension = mc.level != null ? mc.level.dimension().identifier().toString() : "unknown";
        String playerName = mc.player != null ? mc.player.getName().getString() : "Unknown";
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer";

        String pingPrefix = "";
        if (selfPing.get() && !discordId.get().isBlank()) {
            String id = discordId.get().trim();
            pingPrefix = id.equalsIgnoreCase("everyone") ? "@everyone " : "<@" + id + "> ";
        }

        String json = String.format(
                "{\"content\":%s,\"embeds\":[{" +
                        "\"title\":\"📦 Base Stash Discovered!\"," +
                        "\"color\":%d," +
                        "\"description\":\"Found stash with **%d containers** at **%s** (%.1fm away)\\n*Combatant Client • DonutSMP Base Hunting*\"," +
                        "\"fields\":[" +
                        "{\"name\":\"📍 Coordinates\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"📏 Distance\",\"value\":\"`%.1fm`\",\"inline\":true}," +
                        "{\"name\":\"🗃️ Breakdown\",\"value\":\"`%s`\",\"inline\":false}," +
                        "{\"name\":\"🌍 Dimension\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"👤 Found By\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"🖥️ Server\",\"value\":\"`%s`\",\"inline\":true}" +
                        "]" +
                        "}]}",
                pingPrefix.isEmpty() ? "null" : "\"" + escapeJson(pingPrefix.trim()) + "\"",
                stash.hasSpawner() ? 0xFF3333 : 0x00FFCC,
                stash.containerCount(),
                coords,
                distance,
                coords,
                distance,
                escapeJson(breakdown),
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
        if (mc.player == null || discoveredStashes.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        int argb = stashColor.getArgb();
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        for (DiscoveredStash stash : discoveredStashes.values()) {
            AABB box = stash.box();
            if (box == null) continue;

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
                renderer.line(camPos.x, camPos.y, camPos.z, center.x, center.y, center.z, r, g, b, 190);
            }
        }
    }
}
