/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ModuleInfo(
        id = "silenthome",
        displayName = "Silent Home",
        description = "Instantly teleports to your designated DonutSMP home slot with optional Discord coordinate logging.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"fasthome", "safehome", "stealthhome"}
)
public class SilentHome extends Module {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> homeSlot =
            num("silent_home_slot", "home_slot", 1, 1, 90);
    private final BooleanValue deletePrevious =
            bool("silent_home_delete_prev", "delete_previous", false);
    private final BooleanValue webhook =
            bool("silent_home_webhook", "webhook", false);
    private final StringValue webhookUrl =
            text("silent_home_webhook_url", "webhook_url", "");

    @Override
    public void onEnable() {
        if (mc.player != null && mc.player.connection != null) {
            int slot = homeSlot.get();
            BlockPos pos = mc.player.blockPosition();

            if (deletePrevious.get()) {
                mc.player.connection.sendCommand("delhome " + slot);
            }

            mc.player.connection.sendCommand("home " + slot);

            if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§a[SilentHome] §7Teleporting to home §e#" + slot));
            }

            if (webhook.get() && !webhookUrl.get().isBlank()) {
                sendWebhook(slot, pos);
            }
        }
        setEnabled(false);
    }

    private void sendWebhook(int slot, BlockPos pos) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) return;

        String json = String.format(
                "{\"content\":null,\"embeds\":[{\"title\":\"Silent Home Triggered\",\"color\":3447003,\"fields\":[{\"name\":\"Home Slot\",\"value\":\"#%d\",\"inline\":true},{\"name\":\"Departed Coordinates\",\"value\":\"[%d, %d, %d]\",\"inline\":true}]}]}",
                slot, pos.getX(), pos.getY(), pos.getZ()
        );

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Throwable ignored) {
        }
    }
}
