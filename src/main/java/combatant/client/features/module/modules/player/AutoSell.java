/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import java.util.concurrent.ThreadLocalRandom;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.util.screen.ClientScreen;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "autosell",
        displayName = "AutoSell",
        description = "Automates DonutSMP economy grinding by selling mob drops and farmed items via server commands.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"sellall", "donutsell", "economysell"}
)
public class AutoSell extends Module {

    public enum SellMode implements EnumValue.IdProvider {
        SELL_ALL("sell_all", "sell all"),
        SELL_HAND("sell_hand", "sell hand"),
        SELL("sell", "sell"),
        SHOP("shop", "shop"),
        CUSTOM("custom", "custom");

        private final String id;
        private final String cmd;

        SellMode(String id, String cmd) {
            this.id = id;
            this.cmd = cmd;
        }

        @Override
        public String id() {
            return id;
        }

        public String command() {
            return cmd;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private final EnumValue<SellMode> mode =
            enumSetting("autosell_mode", "mode", SellMode.SELL_ALL, SellMode.values());
    private final StringValue customCommand =
            text("autosell_custom_command", "custom_command", "sell all");
    private final NumberValue<Integer> minItems =
            num("autosell_min_items", "min_items", 64, 1, 1024);
    private final NumberValue<Integer> intervalTicks =
            num("autosell_interval", "interval", 60, 10, 300);
    private final NumberValue<Integer> randomDelayTicks =
            num("autosell_random_delay", "random_delay", 20, 0, 100);
    private final BooleanValue humanize =
            bool("autosell_humanize", "humanize_delay", true);
    private final BooleanValue useInventoryThreshold =
            bool("autosell_use_inv_threshold", "inventory_threshold", false);
    private final NumberValue<Integer> inventoryThresholdPercent =
            num("autosell_inv_percent", "inv_full_percent", 80, 10, 100);
    private final BooleanValue onlyInGame =
            bool("autosell_only_in_game", "only_in_game", true);

    private final Minecraft mc = Minecraft.getInstance();
    private int tickCounter = 0;
    private int currentInterval = 60;

    @Override
    public void onEnable() {
        tickCounter = 0;
        resetInterval();
    }

    private void resetInterval() {
        int jitter = humanize.get() && randomDelayTicks.get() > 0
                ? ThreadLocalRandom.current().nextInt(0, randomDelayTicks.get() + 1)
                : 0;
        currentInterval = intervalTicks.get() + jitter;
    }
    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || player.connection == null) return;
        if (onlyInGame.get() && ClientScreen.current() != null) return;

        tickCounter++;
        if (tickCounter < currentInterval) return;
        tickCounter = 0;
        resetInterval();

        // Check inventory threshold if enabled
        if (useInventoryThreshold.get()) {
            int fullPercent = calculateInventoryFullness(player.getInventory());
            if (fullPercent < inventoryThresholdPercent.get()) {
                return;
            }
        }

        int totalSellable = countSellableItems(player.getInventory());
        if (totalSellable >= minItems.get()) {
            String cmd = mode.get() == SellMode.CUSTOM
                    ? customCommand.get().trim()
                    : mode.get().command();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (!cmd.isBlank()) {
                player.connection.sendCommand(cmd);
            }
        }
    }

    private int calculateInventoryFullness(Inventory inv) {
        int occupied = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                occupied++;
            }
        }
        return (occupied * 100) / 36;
    }

    private int countSellableItems(Inventory inv) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (isSellable(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean isSellable(ItemStack stack) {
        return stack.is(Items.BONE)
                || stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.GUNPOWDER)
                || stack.is(Items.STRING)
                || stack.is(Items.SPIDER_EYE)
                || stack.is(Items.BLAZE_ROD)
                || stack.is(Items.ENDER_PEARL)
                || stack.is(Items.SUGAR_CANE)
                || stack.is(Items.CACTUS)
                || stack.is(Items.BAMBOO)
                || stack.is(Items.IRON_INGOT)
                || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.COPPER_INGOT)
                || stack.is(Items.RAW_IRON)
                || stack.is(Items.RAW_GOLD)
                || stack.is(Items.EMERALD)
                || stack.is(Items.DIAMOND);
    }
}
