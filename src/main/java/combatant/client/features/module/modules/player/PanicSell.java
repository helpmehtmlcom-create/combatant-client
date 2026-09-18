/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "panicsell",
        displayName = "Panic Sell",
        description = "Emergency liquidation module for DonutSMP. Instantly sells high-value items when a base is raided.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"emergencysell", "quickdumpsell", "dumpitems"}
)
public class PanicSell extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue sellElytras =
            bool("panic_sell_elytras", "sell_elytras", true);
    private final BooleanValue sellDragonHeads =
            bool("panic_sell_dragon_heads", "sell_dragon_heads", true);
    private final BooleanValue sellGildedBlackstone =
            bool("panic_sell_gilded_blackstone", "sell_gilded_blackstone", true);
    private final BooleanValue sellArmor =
            bool("panic_sell_armor", "sell_armor", true);
    private final BooleanValue sellTools =
            bool("panic_sell_tools", "sell_tools", true);
    private final BooleanValue sellShulkers =
            bool("panic_sell_shulkers", "sell_shulkers", true);
    private final StringValue otherItems =
            text("panic_sell_other_items", "other_items", "");
    private final BooleanValue notifications =
            bool("panic_sell_notifications", "notifications", true);

    @Override
    public void onEnable() {
        if (mc.player == null || mc.player.connection == null) {
            setEnabled(false);
            return;
        }

        Inventory inv = mc.player.getInventory();
        int itemsSold = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (shouldSell(stack)) {
                itemsSold += stack.getCount();
            }
        }

        // Execute DonutSMP sell command
        mc.player.connection.sendCommand("sell all");

        if (notifications.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("§c[PanicSell] §eLiquidated items to /sell! Total items evaluated: " + itemsSold)
            );
        }

        setEnabled(false);
    }

    private boolean shouldSell(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        if (sellElytras.get() && stack.is(Items.ELYTRA)) return true;
        if (sellDragonHeads.get() && stack.is(Items.DRAGON_HEAD)) return true;
        if (sellGildedBlackstone.get() && stack.is(Items.GILDED_BLACKSTONE)) return true;

        if (sellShulkers.get() && stack.getItem().toString().contains("shulker_box")) return true;

        if (sellArmor.get()) {
            String id = stack.getItem().toString().toLowerCase();
            if (id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") || id.contains("boots")) {
                return true;
            }
        }

        if (sellTools.get()) {
            String id = stack.getItem().toString().toLowerCase();
            if (id.contains("sword") || id.contains("pickaxe") || id.contains("axe") || id.contains("shovel") || id.contains("mace")) {
                return true;
            }
        }

        String extra = otherItems.get().trim().toLowerCase();
        if (!extra.isEmpty()) {
            String itemId = stack.getItem().toString().toLowerCase();
            for (String part : extra.split(",")) {
                if (itemId.contains(part.trim())) return true;
            }
        }

        return false;
    }
}
