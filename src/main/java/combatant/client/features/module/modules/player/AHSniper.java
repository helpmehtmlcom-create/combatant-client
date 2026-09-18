/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(
        id = "ahsniper",
        displayName = "AH Sniper",
        description = "Automated Auction House sniper for DonutSMP. Instantly buys mispriced listings with time filter & auto-resell.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"auctionsniper", "donutsniper", "bazaarsniper"}
)
public class AHSniper extends Module {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static final Pattern PRICE_PATTERN = Pattern.compile("(?:price|cost|buy)\\s*[:$]?\\s*\\$?([0-9,.]+[kKmMbB]?)", Pattern.CASE_INSENSITIVE);

    private final Minecraft mc = Minecraft.getInstance();

    private final ModeValue snipeMode =
            modeSetting("ah_snipe_mode", "snipe_mode", "SINGLE", "SINGLE", "MULTI");

    // SINGLE mode settings
    private final StringValue targetItem =
            text("ah_target_item", "sniping_item", "shulker");
    private final StringValue minPriceStr =
            text("ah_min_price", "min_price", "0");
    private final StringValue maxPriceStr =
            text("ah_max_price", "max_price", "500k");

    // Filters & Speed
    private final BooleanValue filterLowTime =
            bool("ah_filter_low_time", "filter_low_time", true);
    private final BooleanValue topLeftOnly =
            bool("ah_top_left_only", "top_left_only", false);

    // Auto Resell
    private final BooleanValue autoSell =
            bool("ah_auto_sell", "auto_sell", false);
    private final StringValue sellPrice =
            text("ah_sell_price", "sell_price", "14m");

    // MULTI mode settings (Items 1 to 5)
    private final StringValue item1 = text("ah_item_1", "item_1", "");
    private final StringValue maxPrice1 = text("ah_max_price_1", "max_price_1", "1m");
    private final StringValue item2 = text("ah_item_2", "item_2", "");
    private final StringValue maxPrice2 = text("ah_max_price_2", "max_price_2", "1m");
    private final StringValue item3 = text("ah_item_3", "item_3", "");
    private final StringValue maxPrice3 = text("ah_max_price_3", "max_price_3", "1m");
    private final StringValue item4 = text("ah_item_4", "item_4", "");
    private final StringValue maxPrice4 = text("ah_max_price_4", "max_price_4", "1m");
    private final StringValue item5 = text("ah_item_5", "item_5", "");
    private final StringValue maxPrice5 = text("ah_max_price_5", "max_price_5", "1m");

    // Notifications & Webhook
    private final BooleanValue notifications =
            bool("ah_notifications", "notifications", true);
    private final BooleanValue webhook =
            bool("ah_webhook", "webhook", false);
    private final StringValue webhookUrl =
            text("ah_webhook_url", "webhook_url", "");
    private final BooleanValue selfPing =
            bool("ah_self_ping", "self_ping", false);
    private final StringValue discordId =
            text("ah_discord_id", "discord_id", "");

    private int delayCooldown = 0;
    private int sellTimer = 0;
    private boolean pendingSell = false;

    @Override
    public void onEnable() {
        delayCooldown = 0;
        sellTimer = 0;
        pendingSell = false;
    }

    public String getTargetFilter() { return targetItem.get(); }
    public String getMaxPrice() { return maxPriceStr.get(); }
    public boolean isPendingSell() { return pendingSell; }

    @Override
    public void onRenderHudEngineForeground(combatant.client.render.engine.renderer.Renderer2D renderer,
                                            combatant.client.render.engine.text.TextRenderer textRenderer,
                                            net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                            float tickDelta) {
        if (mc.player == null) return;
        String text = String.format("🎯 AH Sniper: %s (< %s) • ARMED", targetItem.get(), maxPriceStr.get());
        float textW = (float) textRenderer.getWidth(text);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = (int) ((screenW - textW) / 2);
        int y = 25;
        int pad = 4;

        renderer.roundedRect(x - pad, y - pad, textW + (pad * 2), 11 + (pad * 2), 4.0f, 0xCC111118);
        renderer.roundedRectStroke(x - pad, y - pad, textW + (pad * 2), 11 + (pad * 2), 4.0f, 1.0f, 0xFF00FF66);
        textRenderer.render(text, x, y, new combatant.client.render.engine.color.RenderColor(0xFF00FF88), true);
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.gameMode == null) return;

        if (pendingSell) {
            sellTimer++;
            if (sellTimer >= 20) {
                pendingSell = false;
                sellTimer = 0;
                if (mc.player.connection != null && !sellPrice.get().isBlank()) {
                    mc.player.connection.sendCommand("ah sell " + sellPrice.get().trim());
                }
            }
            return;
        }

        if (delayCooldown > 0) {
            delayCooldown--;
            return;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu == mc.player.inventoryMenu) {
            return;
        }

        if (ClientScreen.current() instanceof AbstractContainerScreen<?> containerScreen) {
            String title = containerScreen.getTitle().getString().toLowerCase();

            // Only run on Auction House screens
            if (!title.contains("auction") && !title.contains("/ah") && !title.contains("listings")) {
                return;
            }

            boolean isMulti = "MULTI".equalsIgnoreCase(snipeMode.get());
            boolean isTopLeft = topLeftOnly.get();

            for (Slot slot : menu.slots) {
                if (slot.container == mc.player.getInventory()) continue;
                if (isTopLeft && slot.getContainerSlot() != 0) continue;

                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;

                if (filterLowTime.get() && !isFreshListing(stack)) {
                    continue;
                }

                String hoverName = stack.getHoverName().getString().toLowerCase();
                String itemId = stack.getItem().toString().toLowerCase();

                long targetMaxPrice = -1;
                long targetMinPrice = 0;

                if (!isMulti) {
                    String targetFilter = targetItem.get().trim().toLowerCase();
                    boolean matches = targetFilter.isEmpty()
                            || targetFilter.equals("all")
                            || hoverName.contains(targetFilter)
                            || itemId.contains(targetFilter);

                    if (matches) {
                        targetMaxPrice = parsePrice(maxPriceStr.get());
                        targetMinPrice = parsePrice(minPriceStr.get());
                    }
                } else {
                    targetMaxPrice = checkMultiMatch(hoverName, itemId);
                }

                if (targetMaxPrice <= 0) continue;

                long itemPrice = extractPrice(stack);
                if (itemPrice >= targetMinPrice && itemPrice <= targetMaxPrice) {
                    // Snipe the item!
                    mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                    delayCooldown = 15;

                    String displayName = stack.getHoverName().getString();
                    if (notifications.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                        String msg = String.format(
                                "§6[AHSniper] §aSNIPED §e%s §afor §2$%s §7(Max: $%s)!",
                                displayName, formatNumber(itemPrice), formatNumber(targetMaxPrice)
                        );
                        mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
                    }

                    mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.5f);

                    if (webhook.get() && !webhookUrl.get().isBlank()) {
                        sendWebhook(displayName, itemPrice, targetMaxPrice);
                    }

                    if (autoSell.get()) {
                        pendingSell = true;
                        sellTimer = 0;
                    }
                    return;
                }
            }
        }
    }

    private long checkMultiMatch(String hoverName, String itemId) {
        String[][] slots = {
                {item1.get(), maxPrice1.get()},
                {item2.get(), maxPrice2.get()},
                {item3.get(), maxPrice3.get()},
                {item4.get(), maxPrice4.get()},
                {item5.get(), maxPrice5.get()}
        };

        for (String[] pair : slots) {
            String name = pair[0].trim().toLowerCase();
            if (name.isEmpty()) continue;
            if (hoverName.contains(name) || itemId.contains(name)) {
                return parsePrice(pair[1]);
            }
        }
        return -1;
    }

    private boolean isFreshListing(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return true;

        for (Component line : lore.lines()) {
            String text = line.getString().toLowerCase();
            if (text.contains("time") || text.contains("expires") || text.contains("remaining")) {
                // DonutSMP auctions start at 24h. Fresh listings are 23h or 24h.
                return text.contains("23h") || text.contains("24h") || text.contains("1d");
            }
        }
        return true;
    }

    private long extractPrice(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                String text = line.getString();
                Matcher matcher = PRICE_PATTERN.matcher(text);
                if (matcher.find()) {
                    return parsePrice(matcher.group(1));
                }
            }
        }
        return -1;
    }

    private long parsePrice(String text) {
        if (text == null || text.isBlank()) return 0;
        String clean = text.replaceAll("[,$\\s]", "").toLowerCase();
        try {
            if (clean.endsWith("k")) {
                double val = Double.parseDouble(clean.substring(0, clean.length() - 1));
                return (long) (val * 1_000L);
            } else if (clean.endsWith("m")) {
                double val = Double.parseDouble(clean.substring(0, clean.length() - 1));
                return (long) (val * 1_000_000L);
            } else if (clean.endsWith("b")) {
                double val = Double.parseDouble(clean.substring(0, clean.length() - 1));
                return (long) (val * 1_000_000_000L);
            } else {
                return (long) Double.parseDouble(clean);
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatNumber(long n) {
        if (n >= 1_000_000_000L) {
            return String.format("%.2fB", n / 1_000_000_000.0);
        } else if (n >= 1_000_000L) {
            return String.format("%.2fM", n / 1_000_000.0);
        } else if (n >= 1_000L) {
            return String.format("%.2fK", n / 1_000.0);
        }
        return String.valueOf(n);
    }

    private void sendWebhook(String itemName, long price, long maxPrice) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) return;

        String playerName = mc.player != null ? mc.player.getName().getString() : "Unknown";
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer";

        String pingPrefix = "";
        if (selfPing.get() && !discordId.get().isBlank()) {
            String id = discordId.get().trim();
            pingPrefix = id.equalsIgnoreCase("everyone") ? "@everyone " : "<@" + id + "> ";
        }

        String json = String.format(
                "{\"content\":%s,\"embeds\":[{" +
                        "\"title\":\"🎯 AH Snipe Successful!\"," +
                        "\"color\":65280," +
                        "\"description\":\"Sniped **%s** for **$%s**!\\n*Combatant Client • DonutSMP AH Sniper*\"," +
                        "\"fields\":[" +
                        "{\"name\":\"📦 Item\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"💵 Sniped Price\",\"value\":\"`$%s`\",\"inline\":true}," +
                        "{\"name\":\"💰 Target Max\",\"value\":\"`$%s`\",\"inline\":true}," +
                        "{\"name\":\"👤 Buyer\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"🖥️ Server\",\"value\":\"`%s`\",\"inline\":true}" +
                        "]" +
                        "}]}",
                pingPrefix.isEmpty() ? "null" : "\"" + escapeJson(pingPrefix.trim()) + "\"",
                escapeJson(itemName),
                formatNumber(price),
                escapeJson(itemName),
                formatNumber(price),
                formatNumber(maxPrice),
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
}
