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
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;

@ModuleInfo(
        id = "meteorantiban",
        displayName = "Meteor Anti Ban",
        description = "Throttles outgoing packet bursts and prevents anticheat heuristic flags on DonutSMP.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"antiban", "packetlimiter", "heuristicprotect"}
)
public class MeteorAntiBan extends Module {

    private final NumberValue<Integer> maxActionsPerTick =
            num("antiban_max_actions", "max_actions_per_tick", 6, 1, 20);
    private final NumberValue<Integer> maxClicksPerTick =
            num("antiban_max_clicks", "max_clicks_per_tick", 4, 1, 15);
    private final BooleanValue limitSwings =
            bool("antiban_limit_swings", "limit_swings", true);

    private int actionsThisTick = 0;
    private int clicksThisTick = 0;
    private int swingsThisTick = 0;

    @Override
    public void onEnable() {
        actionsThisTick = 0;
        clicksThisTick = 0;
        swingsThisTick = 0;
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        actionsThisTick = 0;
        clicksThisTick = 0;
        swingsThisTick = 0;
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        Object packet = event.getPacket();

        if (packet instanceof ServerboundContainerClickPacket) {
            clicksThisTick++;
            if (clicksThisTick > maxClicksPerTick.get()) {
                event.setCancelled(true);
            }
        } else if (packet instanceof ServerboundPlayerActionPacket) {
            actionsThisTick++;
            if (actionsThisTick > maxActionsPerTick.get()) {
                event.setCancelled(true);
            }
        } else if (limitSwings.get() && packet instanceof ServerboundSwingPacket) {
            swingsThisTick++;
            if (swingsThisTick > 2) {
                event.setCancelled(true);
            }
        }
    }
}
