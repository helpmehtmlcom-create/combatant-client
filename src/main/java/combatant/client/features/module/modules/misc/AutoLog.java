/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "autolog",
        displayName = "AutoLog",
        category = ModuleCategory.MISC,
        description = "Automatically disconnects from the server when health drops below a critical threshold."
)
public final class AutoLog extends Module {

    private final NumberValue<Double> health = num("health", 6.0, 1.0, 19.0);
    private final BooleanValue totemCheck = bool("totem_check", true);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.getConnection() == null) return;
        LocalPlayer player = mc.player;

        if (player.getHealth() <= health.get()) {
            if (totemCheck.get()) {
                boolean hasTotem = player.getMainHandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)
                        || player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
                if (hasTotem) return;
            }

            mc.getConnection().getConnection().disconnect(
                    Component.literal("[AutoLog] Disconnected due to low health (" + String.format("%.1f", player.getHealth()) + " HP)")
            );
            toggle();
        }
    }
}
