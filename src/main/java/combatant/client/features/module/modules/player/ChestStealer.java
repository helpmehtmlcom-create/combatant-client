/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        id = "cheststealer",
        displayName = "ChestStealer",
        aliases = {"Stealer", "Looter"},
        category = ModuleCategory.PLAYER,
        description = "Automatically loots items from opened chests and containers."
)
public final class ChestStealer extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> delayTicks =
            num("chestStealerDelayTicks", "delay_ticks", 1, 0, 10);

    private final BooleanValue randomDelay =
            bool("chestStealerRandomDelay", "random_delay", true);

    private final BooleanValue autoClose =
            bool("chestStealerAutoClose", "auto_close", true);

    private final BooleanValue ignoreEnderChest =
            bool("chestStealerIgnoreEnderChest", "ignore_ender_chest", true);

    private int cooldown = 0;

    @Override
    public void onDisable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;

        Screen screen = ClientScreen.current();
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            cooldown = 0;
            return;
        }

        if (screen instanceof InventoryScreen) {
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.slots.size() <= 36) {
            return;
        }

        if (ignoreEnderChest.get() && screen.getTitle() != null) {
            String title = screen.getTitle().getString().toLowerCase();
            if (title.contains("ender")) {
                return;
            }
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int containerSlotCount = menu.slots.size() - 36;
        boolean hasItemsLeft = false;

        for (int i = 0; i < containerSlotCount; i++) {
            Slot slot = menu.slots.get(i);
            if (slot != null && slot.hasItem() && !slot.getItem().isEmpty()) {
                hasItemsLeft = true;
                mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, player);

                int delay = delayTicks.get();
                if (randomDelay.get()) {
                    delay += ThreadLocalRandom.current().nextInt(2);
                }
                cooldown = delay;
                return;
            }
        }

        if (!hasItemsLeft && autoClose.get()) {
            player.closeContainer();
            ClientScreen.show(null);
        }
    }
}
