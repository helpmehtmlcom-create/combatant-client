/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "autoconfirm",
        displayName = "Auto Confirm",
        description = "Automatically confirms DonutSMP GUI prompts (AH Buy, AH Sell, Orders, and TPA requests).",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"autoclickconfirm", "confirmbot"}
)
public class AutoConfirm extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue ahBuy =
            bool("autoconfirm_ah_buy", "ah_buy", true);
    private final BooleanValue ahSell =
            bool("autoconfirm_ah_sell", "ah_sell", true);
    private final BooleanValue orderFulfill =
            bool("autoconfirm_order", "order_fulfill", true);
    private final BooleanValue tpaAccept =
            bool("autoconfirm_tpa", "tpa_accept", true);
    private final NumberValue<Integer> delayTicks =
            num("autoconfirm_delay", "delay_ticks", 1, 0, 10);

    private int lastConfirmedContainer = -1;
    private int waitTicks = 0;

    @Override
    public void onEnable() {
        lastConfirmedContainer = -1;
        waitTicks = 0;
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.gameMode == null) return;

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu == mc.player.inventoryMenu) {
            lastConfirmedContainer = -1;
            waitTicks = 0;
            return;
        }

        if (menu.containerId == lastConfirmedContainer) {
            return;
        }

        if (ClientScreen.current() instanceof AbstractContainerScreen<?> containerScreen) {
            String title = containerScreen.getTitle().getString().toLowerCase();

            boolean isConfirmScreen = title.contains("confirm")
                    || title.contains("are you sure")
                    || title.contains("purchase")
                    || title.contains("fulfill")
                    || title.contains("listing");

            if (isConfirmScreen) {
                waitTicks++;
                if (waitTicks < delayTicks.get()) return;

                // Find confirm button in container slots (usually lime concrete, lime dye, emerald, or green wool)
                for (Slot slot : menu.slots) {
                    if (slot.container == mc.player.getInventory()) continue;

                    ItemStack stack = slot.getItem();
                    if (stack.isEmpty()) continue;

                    String name = stack.getHoverName().getString().toLowerCase();
                    String itemStr = stack.getItem().toString().toLowerCase();
                    boolean isGreen = itemStr.contains("lime")
                            || itemStr.contains("green")
                            || stack.is(Items.EMERALD_BLOCK)
                            || stack.is(Items.EMERALD);

                    if (name.contains("confirm") || name.contains("yes") || (isGreen && !name.contains("cancel") && !name.contains("no"))) {
                        mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                        lastConfirmedContainer = menu.containerId;
                        waitTicks = 0;
                        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§a[AutoConfirm] §7Auto-confirmed prompt: §f" + title));
                        }
                        return;
                    }
                }
            }
        }
    }
}
