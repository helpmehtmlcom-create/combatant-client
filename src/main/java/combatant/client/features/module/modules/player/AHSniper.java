/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;
import combatant.client.features.module.HudPhase;

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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
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

    private static final Pattern PREFIXED_PRICE_PATTERN = Pattern.compile(
            "(?:price|cost|buy(?:\\s*now)?|amount|bid)\\s*[:$]?\\s*\\$?\\s*([0-9][0-9,.]*\\s*[kKmMbB]?)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CURRENCY_PRICE_PATTERN = Pattern.compile(
            "\\$\\s*([0-9][0-9,.]*\\s*[kKmMbB]?)|([0-9][0-9,.]*\\s*[kKmMbB]?)\\s*(?:\\$|coins?|bucks?)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STANDALONE_PRICE_PATTERN = Pattern.compile(
            "^\\s*\\$?\\s*([0-9]+(?:[,.][0-9]+)*\\s*[kKmMbB])\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MARKET_VALUE_PATTERN = Pattern.compile(
            "(?:market\\s*value|estimated\\s*(?:value|price)|avg(?:erage)?\\s*price)\\s*[:$]?\\s*\\$?\\s*([0-9][0-9,.]*\\s*[kKmMbB]?)",
            Pattern.CASE_INSENSITIVE
    );

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

    // Spend Limit & Profit Filters
    private final NumberValue<Double> maxPrice =
            num("ah_max_spend_threshold", "max_price", 50000000.0, 0.0, 1000000000000.0);
    private final BooleanValue useProfitFilter =
            bool("ah_use_profit_filter", "use_profit_filter", false);
    private final NumberValue<Double> minProfit =
            num("ah_min_profit", "min_profit", 100000.0, 0.0, 1000000000.0);
    private final NumberValue<Double> minProfitPercent =
            num("ah_min_profit_percent", "min_profit_percent", 15.0, 0.0, 100.0);
    private final StringValue estimatedMarketPrice =
            text("ah_market_price", "estimated_market_price", "0");

    // Anti-Scam Protection
    private final BooleanValue antiScam =
            bool("ah_anti_scam", "anti_scam", true);

    // Filters & Speed
    private final BooleanValue filterLowTime =
            bool("ah_filter_low_time", "filter_low_time", true);
    private final BooleanValue topLeftOnly =
            bool("ah_top_left_only", "top_left_only", false);
    private final NumberValue<Integer> minClickDelay =
            num("ah_min_click_delay", "min_click_delay", 3, 0, 30);
    private final NumberValue<Integer> maxClickDelay =
            num("ah_max_click_delay", "max_click_delay", 8, 0, 60);
    private final BooleanValue randomizeClickDelay =
            bool("ah_randomize_delay", "randomize_click_delay", true);
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

                String hoverName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);

                String matchedTarget = null;
                long targetMaxPrice = -1;
                long targetMinPrice = 0;

                if (!isMulti) {
                    String targetFilter = targetItem.get().trim().toLowerCase(Locale.ROOT);
                    boolean matches = targetFilter.isEmpty()
                            || targetFilter.equals("all")
                            || hoverName.contains(targetFilter)
                            || itemId.contains(targetFilter);

                    if (matches) {
                        matchedTarget = targetFilter;
                        targetMaxPrice = parsePrice(maxPriceStr.get());
                        targetMinPrice = parsePrice(minPriceStr.get());
                    }
                } else {
                    MultiMatch match = checkMultiMatch(hoverName, itemId);
                    if (match != null) {
                        matchedTarget = match.targetName();
                        targetMaxPrice = match.maxPrice();
                    }
                }

                if (targetMaxPrice <= 0) continue;

                // Anti-Scam check
                if (isScam(stack, matchedTarget)) {
                    continue;
                }

                long itemPrice = extractPrice(stack);
                if (itemPrice < 0) continue;

                // Maximum spend price threshold
                if (maxPrice.get() > 0 && itemPrice > maxPrice.get().longValue()) {
                    continue;
                }

                // Profit margin filter
                if (!checkProfit(stack, itemPrice, targetMaxPrice)) {
                    continue;
                }

                if (itemPrice >= targetMinPrice && itemPrice <= targetMaxPrice) {
                    // Snipe the item!
                    mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                    int delay = randomizeClickDelay.get()
                            ? ThreadLocalRandom.current().nextInt(minClickDelay.get(), Math.max(minClickDelay.get(), maxClickDelay.get()) + 1)
                            : minClickDelay.get();
                    delayCooldown = Math.max(1, delay);

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

    private static record MultiMatch(String targetName, long maxPrice) {}

    private MultiMatch checkMultiMatch(String hoverName, String itemId) {
        String[][] slots = {
                {item1.get(), maxPrice1.get()},
                {item2.get(), maxPrice2.get()},
                {item3.get(), maxPrice3.get()},
                {item4.get(), maxPrice4.get()},
                {item5.get(), maxPrice5.get()}
        };

        for (String[] pair : slots) {
            String name = pair[0].trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) continue;
            if (hoverName.contains(name) || itemId.contains(name)) {
                return new MultiMatch(name, parsePrice(pair[1]));
            }
        }
        return null;
    }

    private boolean checkProfit(ItemStack stack, long itemPrice, long targetMaxPrice) {
        if (!useProfitFilter.get()) return true;

        long estimatedMarketValue = extractMarketValue(stack);
        if (estimatedMarketValue <= 0) {
            long configuredMarket = parsePrice(estimatedMarketPrice.get());
            if (configuredMarket > 0) {
                estimatedMarketValue = configuredMarket;
            } else if (autoSell.get() && !sellPrice.get().isBlank()) {
                estimatedMarketValue = parsePrice(sellPrice.get());
            } else {
                estimatedMarketValue = targetMaxPrice;
            }
        }

        // if estimated market value - listing price < minProfit, skip
        long netProfit = estimatedMarketValue - itemPrice;
        if (netProfit < minProfit.get().longValue()) {
            return false;
        }

        if (minProfitPercent.get() > 0 && itemPrice > 0) {
            double margin = ((double) netProfit / (double) itemPrice) * 100.0;
            if (margin < minProfitPercent.get()) {
                return false;
            }
        }

        return true;
    }

    private boolean isScam(ItemStack stack, String targetFilter) {
        if (!antiScam.get()) return false;
        if (stack.isEmpty()) return false;

        String itemRegistryName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        boolean hasCustomName = stack.has(DataComponents.CUSTOM_NAME);
        String customName = hasCustomName
                ? stack.getHoverName().getString().toLowerCase(Locale.ROOT)
                : "";

        boolean isRealSpawner = stack.is(Items.SPAWNER);
        boolean isRealBeacon = stack.is(Items.BEACON);
        boolean isRealNetherite = itemRegistryName.contains("netherite") || stack.is(Items.ANCIENT_DEBRIS);
        boolean isRealElytra = stack.is(Items.ELYTRA);
        boolean isRealTotem = stack.is(Items.TOTEM_OF_UNDYING);
        boolean isRealShulker = isShulkerBox(stack);
        boolean isRealMace = stack.is(Items.MACE);
        boolean isRealHeavyCore = stack.is(Items.HEAVY_CORE);
        boolean isRealTrident = stack.is(Items.TRIDENT);
        boolean isRealDragonEgg = stack.is(Items.DRAGON_EGG);

        // 1. Check custom item name: detect renamed items masquerading as high-value items
        if (hasCustomName) {
            if (customName.contains("spawner") && !isRealSpawner) return true;
            if (customName.contains("beacon") && !isRealBeacon) return true;
            if (customName.contains("netherite") && !isRealNetherite) return true;
            if (customName.contains("ancient debris") && !stack.is(Items.ANCIENT_DEBRIS)) return true;
            if (customName.contains("elytra") && !isRealElytra) return true;
            if (customName.contains("totem") && !isRealTotem) return true;
            if (customName.contains("shulker") && !isRealShulker) return true;
            if (customName.contains("mace") && !isRealMace) return true;
            if (customName.contains("heavy core") && !isRealHeavyCore) return true;
            if (customName.contains("trident") && !isRealTrident) return true;
            if (customName.contains("dragon egg") && !isRealDragonEgg) return true;
            if ((customName.contains("golden apple") || customName.contains("gapple") || customName.contains("notch apple"))
                    && !stack.is(Items.GOLDEN_APPLE) && !stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return true;

            // Check specific weapon/tool tags if custom name claims to be a netherite weapon/tool
            if (customName.contains("sword") && customName.contains("netherite") && (!isRealNetherite || !stack.is(ItemTags.SWORDS))) return true;
            if (customName.contains("pickaxe") && customName.contains("netherite") && (!isRealNetherite || !stack.is(ItemTags.PICKAXES))) return true;
            if (customName.contains("axe") && customName.contains("netherite") && (!isRealNetherite || !stack.is(ItemTags.AXES))) return true;
            if (customName.contains("shovel") && customName.contains("netherite") && (!isRealNetherite || !stack.is(ItemTags.SHOVELS))) return true;
            if (customName.contains("hoe") && customName.contains("netherite") && (!isRealNetherite || !stack.is(ItemTags.HOES))) return true;

            // 2. Junk base items disguised as high value items/vouchers
            if (isScamBaseItem(stack)) {
                if (customName.contains("heart") || customName.contains("voucher")
                        || customName.contains("tier") || customName.contains("spawner")
                        || customName.contains("netherite") || customName.contains("elytra")
                        || customName.contains("shulker") || customName.contains("beacon")
                        || customName.contains("totem") || customName.contains("coin")
                        || customName.contains("key") || customName.contains("money")
                        || customName.contains("sword") || customName.contains("armor")
                        || customName.contains("pickaxe") || customName.contains("god")
                        || customName.contains("star") || customName.contains("core")) {
                    return true;
                }
            }
        }

        // 3. Strict target check for valuable item classes
        if (targetFilter != null && !targetFilter.isBlank() && !targetFilter.equalsIgnoreCase("all")) {
            String filter = targetFilter.toLowerCase(Locale.ROOT);
            if (filter.contains("spawner") && !isRealSpawner) return true;
            if (filter.contains("beacon") && !isRealBeacon) return true;
            if (filter.contains("netherite") && !isRealNetherite) return true;
            if (filter.contains("debris") && !stack.is(Items.ANCIENT_DEBRIS)) return true;
            if (filter.contains("elytra") && !isRealElytra) return true;
            if (filter.contains("totem") && !isRealTotem) return true;
            if (filter.contains("shulker") && !isRealShulker) return true;
            if (filter.contains("mace") && !isRealMace) return true;
            if (filter.contains("heavy core") && !isRealHeavyCore) return true;
            if (filter.contains("trident") && !isRealTrident) return true;
            if (filter.contains("dragon egg") && !isRealDragonEgg) return true;

            // Item tag validation for targeted tools/weapons
            if (filter.contains("sword") && !stack.is(ItemTags.SWORDS)) return true;
            if (filter.contains("pickaxe") && !stack.is(ItemTags.PICKAXES)) return true;
            if (filter.contains("axe") && !stack.is(ItemTags.AXES)) return true;
        }

        // 4. Fake lore detection
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && isScamBaseItem(stack)) {
            for (Component line : lore.lines()) {
                String loreStr = line.getString().toLowerCase(Locale.ROOT);
                if (loreStr.contains("worth") || loreStr.contains("value") || loreStr.contains("redeem")
                        || loreStr.contains("voucher") || loreStr.contains("right click to claim")) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isShulkerBox(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
            return true;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        return id.contains("shulker_box");
    }

    private boolean isScamBaseItem(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        return id.contains("sign")
                || id.contains("button")
                || id.contains("lever")
                || id.contains("pressure_plate")
                || id.contains("tinted_glass")
                || id.contains("stained_glass")
                || id.contains("concrete")
                || id.contains("terracotta")
                || stack.is(Items.DIRT)
                || stack.is(Items.COBBLESTONE)
                || stack.is(Items.STONE)
                || stack.is(Items.GRANITE)
                || stack.is(Items.DIORITE)
                || stack.is(Items.ANDESITE)
                || stack.is(Items.DEEPSLATE)
                || stack.is(Items.NETHERRACK)
                || stack.is(Items.STICK)
                || stack.is(Items.PAPER)
                || stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.DEAD_BUSH)
                || stack.is(Items.WOODEN_SHOVEL)
                || stack.is(Items.WOODEN_HOE)
                || stack.is(Items.WOODEN_PICKAXE)
                || stack.is(Items.WOODEN_AXE)
                || stack.is(Items.WOODEN_SWORD)
                || stack.is(Items.STONE_SHOVEL)
                || stack.is(Items.STONE_HOE)
                || stack.is(Items.STONE_PICKAXE)
                || stack.is(Items.STONE_AXE)
                || stack.is(Items.STONE_SWORD)
                || stack.is(Items.FEATHER)
                || stack.is(Items.STRING)
                || stack.is(Items.GLASS_BOTTLE)
                || stack.is(Items.BOWL)
                || stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.BONE)
                || stack.is(Items.SPIDER_EYE)
                || stack.is(Items.COAL_BLOCK);
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
            // 1. Try explicit price prefix (Price: $1,500,000 / Buy: 1.5M / Cost: 500k)
            for (Component line : lore.lines()) {
                String text = line.getString();
                Matcher matcher = PREFIXED_PRICE_PATTERN.matcher(text);
                if (matcher.find()) {
                    return parsePrice(matcher.group(1));
                }
            }
            // 2. Try currency symbols ($1,500,000 / 1.5M coins / $500k)
            for (Component line : lore.lines()) {
                String text = line.getString();
                Matcher matcher = CURRENCY_PRICE_PATTERN.matcher(text);
                if (matcher.find()) {
                    String matched = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                    if (matched != null && !matched.isBlank()) {
                        return parsePrice(matched);
                    }
                }
            }
            // 3. Standalone price format on line (e.g. "1.5M" or "500k")
            for (Component line : lore.lines()) {
                String text = line.getString().trim();
                Matcher matcher = STANDALONE_PRICE_PATTERN.matcher(text);
                if (matcher.find()) {
                    return parsePrice(matcher.group(1));
                }
            }
        }
        return -1;
    }

    private long extractMarketValue(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                String text = line.getString();
                Matcher matcher = MARKET_VALUE_PATTERN.matcher(text);
                if (matcher.find()) {
                    return parsePrice(matcher.group(1));
                }
            }
        }
        return -1;
    }

    private long parsePrice(String text) {
        if (text == null || text.isBlank()) return 0;
        String clean = text.replaceAll("(?i)[,$_\\s]|coins?|bucks?", "").toLowerCase(Locale.ROOT);
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

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.LAST;
    }
}
