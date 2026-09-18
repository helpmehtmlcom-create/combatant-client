/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.features.relations.CategoryService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ModuleInfo(
        id = "playerdetect",
        displayName = "Player Detect",
        description = "Detects approaching players during base hunting and automatically disconnects or secures funds.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"autoleaveplayer", "hunterprotect", "antihunter"}
)
public class PlayerDetect extends Module {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue disconnect =
            bool("pdetect_disconnect", "disconnect", true);
    private final BooleanValue toggleOnDetect =
            bool("pdetect_toggle", "toggle_when_detected", true);
    private final BooleanValue ignoreFriends =
            bool("pdetect_ignore_friends", "ignore_friends", true);

    private final BooleanValue webhook =
            bool("pdetect_webhook", "webhook", false);
    private final StringValue webhookUrl =
            text("pdetect_webhook_url", "webhook_url", "");
    private final BooleanValue selfPing =
            bool("pdetect_self_ping", "self_ping", false);
    private final StringValue discordId =
            text("pdetect_discord_id", "discord_id", "");

    private final BooleanValue enablePanicPay =
            bool("pdetect_panic_pay", "enable_panic_pay", false);
    private final StringValue targetPlayer =
            text("pdetect_target_player", "target_player", "");
    private final StringValue amount =
            text("pdetect_pay_amount", "amount", "1m");

    private boolean detected = false;

    @Override
    public void onEnable() {
        detected = false;
    }

    @Override
    public void onDisable() {
    }

    public boolean isEnemyDetected() { return detected; }

    @Override
    public void onRenderHudEngineForeground(combatant.client.render.engine.renderer.Renderer2D renderer,
                                            combatant.client.render.engine.text.TextRenderer textRenderer,
                                            net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                            float tickDelta) {
        if (mc.player == null) return;
        String text = detected ? "🚨 PLAYER DETECTED • LEAVING" : "🛡️ Player Radar: SCANNING (Safe)";
        int col = detected ? 0xFFFF2222 : 0xFF00EE77;
        float textW = (float) textRenderer.getWidth(text);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = (int) ((screenW - textW) / 2);
        int y = 12;
        int pad = 4;

        renderer.roundedRect(x - pad, y - pad, textW + (pad * 2), 11 + (pad * 2), 4.0f, 0xDD101015);
        renderer.roundedRectStroke(x - pad, y - pad, textW + (pad * 2), 11 + (pad * 2), 4.0f, 1.0f, col);
        textRenderer.render(text, x, y, new combatant.client.render.engine.color.RenderColor(col), true);
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null || detected) return;

        for (Player other : mc.level.players()) {
            if (other == null || other == mc.player) continue;
            if (ignoreFriends.get() && CategoryService.isFriend(other)) continue;

            // Enemy/unknown player detected!
            detected = true;
            String name = other.getName().getString();
            BlockPos pPos = other.blockPosition();
            double dist = mc.player.distanceTo(other);

            // 1. Panic pay if enabled
            if (enablePanicPay.get() && !targetPlayer.get().isBlank() && mc.player.connection != null) {
                mc.player.connection.sendCommand("pay " + targetPlayer.get().trim() + " " + amount.get().trim());
            }

            // 2. Webhook alert
            if (webhook.get() && !webhookUrl.get().isBlank()) {
                sendWebhook(name, pPos, dist);
            }

            // 3. Disconnect if enabled
            if (disconnect.get() && mc.getConnection() != null && mc.getConnection().getConnection() != null) {
                mc.getConnection().getConnection().disconnect(
                        Component.literal("§c[PlayerDetect] Disconnected: " + name + " spotted at " + pPos.toShortString() + " (" + String.format("%.1fm", dist) + ")")
                );
            }

            if (toggleOnDetect.get()) {
                setEnabled(false);
            }
            break;
        }
    }

    private void sendWebhook(String enemyName, BlockPos pos, double distance) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) return;

        String dimension = mc.level != null ? mc.level.dimension().identifier().toString() : "unknown";
        String myName = mc.player != null ? mc.player.getName().getString() : "Unknown";
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer";

        String pingPrefix = "";
        if (selfPing.get() && !discordId.get().isBlank()) {
            String id = discordId.get().trim();
            pingPrefix = id.equalsIgnoreCase("everyone") ? "@everyone " : "<@" + id + "> ";
        }

        String json = String.format(
                "{\"content\":%s,\"embeds\":[{" +
                        "\"title\":\"⚠️ Player Detected!\"," +
                        "\"color\":16711680," +
                        "\"description\":\"**%s** was spotted **%.1fm away** at **[%d, %d, %d]**!\\n*Combatant Client • DonutSMP Base Hunting*\"," +
                        "\"fields\":[" +
                        "{\"name\":\"👤 Detected Player\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"📍 Coordinates\",\"value\":\"`%d %d %d`\",\"inline\":true}," +
                        "{\"name\":\"📏 Distance\",\"value\":\"`%.1fm`\",\"inline\":true}," +
                        "{\"name\":\"🌍 Dimension\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"🛡️ Hunter\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"🖥️ Server\",\"value\":\"`%s`\",\"inline\":true}" +
                        "]" +
                        "}]}",
                pingPrefix.isEmpty() ? "null" : "\"" + escapeJson(pingPrefix.trim()) + "\"",
                escapeJson(enemyName),
                distance,
                pos.getX(), pos.getY(), pos.getZ(),
                escapeJson(enemyName),
                pos.getX(), pos.getY(), pos.getZ(),
                distance,
                escapeJson(dimension),
                escapeJson(myName),
                escapeJson(server)
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(4))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Throwable ignored) {
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
