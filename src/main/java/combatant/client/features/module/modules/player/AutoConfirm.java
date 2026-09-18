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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
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

    private final BooleanValue safeMode =
            bool("autoconfirm_safe_mode", "safe_mode", true);
    private final BooleanValue ahBuy =
            bool("autoconfirm_ah_buy", "ah_buy", true);
    private final BooleanValue ahSell =
            bool("autoconfirm_ah_sell", "ah_sell", true);
    private final BooleanValue orderFulfill =
            bool("autoconfirm_order", "order_fulfill", true);
    private final BooleanValue tpaAccept =
            bool("autoconfirm_tpa", "tpa_accept", true);
    private final NumberValue<Integer> delayTicks =
            num("autoconfirm_delay", "delay_ticks", 3, 0, 30);
    private final NumberValue<Integer> maxDelayTicks =
            num("autoconfirm_max_delay", "max_delay_ticks", 7, 0, 50);
    private final BooleanValue randomizeDelay =
            bool("autoconfirm_random_delay", "randomize_delay", true);

    private int lastConfirmedContainer = -1;
    private int waitTicks = 0;
    private int currentTargetDelay = 3;
    @Override
    public void onEnable() {
        lastConfirmedContainer = -1;
        waitTicks = 0;
        updateTargetDelay();
    }

    private void updateTargetDelay() {
        currentTargetDelay = randomizeDelay.get()
                ? ThreadLocalRandom.current().nextInt(delayTicks.get(), Math.max(delayTicks.get(), maxDelayTicks.get()) + 1)
                : delayTicks.get();
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
            String title = containerScreen.getTitle().getString().toLowerCase(Locale.ROOT);

            // Safe Mode check: block dangerous loss/destruction prompts
            if (isDangerousPrompt(menu, title)) {
                if (lastConfirmedContainer != menu.containerId) {
                    lastConfirmedContainer = menu.containerId;
                    if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                        mc.gui.hud.getChat().addClientSystemMessage(Component.literal(
                                "§c[AutoConfirm] §eBLOCKED dangerous prompt: §f" + containerScreen.getTitle().getString() + " §7(Safe Mode)"
                        ));
                    }
                }
                return;
            }

            boolean isConfirmScreen = title.contains("confirm")
                    || title.contains("are you sure")
                    || title.contains("purchase")
                    || title.contains("fulfill")
                    || title.contains("listing");

            if (isConfirmScreen) {
                waitTicks++;
                if (waitTicks < currentTargetDelay) return;

                // Find confirm button in container slots (lime concrete, lime dye, emerald, green wool/glass)
                for (Slot slot : menu.slots) {
                    if (slot.container == mc.player.getInventory()) continue;

                    ItemStack stack = slot.getItem();
                    if (stack.isEmpty()) continue;

                    String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                    String itemStr = stack.getItem().toString().toLowerCase(Locale.ROOT);
                    boolean isGreen = itemStr.contains("lime")
                            || itemStr.contains("green")
                            || stack.is(Items.EMERALD_BLOCK)
                            || stack.is(Items.EMERALD);

                    if (name.contains("confirm") || name.contains("yes") || (isGreen && !name.contains("cancel") && !name.contains("no"))) {
                        mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                        lastConfirmedContainer = menu.containerId;
                        waitTicks = 0;
                        updateTargetDelay();
                        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§a[AutoConfirm] §7Auto-confirmed: " + containerScreen.getTitle().getString()));
                        }
                        return;
                    }
                }
            }
        }
    }

    private boolean isDangerousPrompt(AbstractContainerMenu menu, String title) {
        if (!safeMode.get()) return false;

        boolean isLossPrompt = title.contains("drop")
                || title.contains("discard")
                || title.contains("delete")
                || title.contains("destroy")
                || title.contains("void")
                || title.contains("salvage")
                || title.contains("disenchant")
                || title.contains("gamble")
                || title.contains("coinflip")
                || title.contains("trash");

        if (isLossPrompt) {
            return true;
        }

        // Check if valuable gear is present in container with loss warnings
        for (Slot slot : menu.slots) {
            if (slot.container == mc.player.getInventory()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (isValuableGear(stack)) {
                ItemLore lore = stack.get(DataComponents.LORE);
                if (lore != null) {
                    for (Component line : lore.lines()) {
                        String lineStr = line.getString().toLowerCase(Locale.ROOT);
                        if (lineStr.contains("permanently") || lineStr.contains("lost")
                                || lineStr.contains("cannot be undone") || lineStr.contains("drop")
                                || lineStr.contains("destroy")) {
                            return true;
                        }
                    }
                }
                if (title.contains("confirm") && (title.contains("item") || title.contains("drop") || title.contains("action"))) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isValuableGear(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        return id.contains("netherite")
                || stack.is(Items.ELYTRA)
                || stack.is(Items.TOTEM_OF_UNDYING)
                || stack.is(Items.MACE)
                || stack.is(Items.HEAVY_CORE)
                || stack.is(Items.DRAGON_EGG)
                || stack.is(Items.BEACON)
                || stack.is(Items.SPAWNER)
                || stack.is(Items.TRIDENT)
                || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                || id.contains("shulker_box");
    }
}
