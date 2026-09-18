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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

@ModuleInfo(
        id = "rtp",
        displayName = "RTP",
        description = "Automates DonutSMP random teleportation cycles for wilderness base hunting.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"randomteleport", "rtphunt", "wildhunt"}
)
public class RTP extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final ModeValue region =
            modeSetting("rtp_region", "region", "west", "west", "east", "north", "south", "asia", "oceania", "eu central", "eu west");
    private final NumberValue<Integer> delaySeconds =
            num("rtp_delay", "delay_seconds", 15, 5, 60);
    private final NumberValue<Double> targetDistance =
            num("rtp_target_dist", "target_distance", 50000.0, 1000.0, 500000.0);
    private final BooleanValue disconnectOnReach =
            bool("rtp_disconnect_on_reach", "disconnect_on_reach", true);

    private int ticksRemaining = 0;

    @Override
    public void onEnable() {
        ticksRemaining = 0;
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.player.connection == null) return;

        // Check if player has reached target exploration distance from spawn (0, 0)
        double currentDist = Math.hypot(mc.player.getX(), mc.player.getZ());
        if (currentDist >= targetDistance.get()) {
            if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§a[RTP] §eReached target distance §f(%.0fm)§e! Stopping RTP."));
            }
            if (disconnectOnReach.get() && mc.getConnection() != null) {
                mc.getConnection().getConnection().disconnect(Component.literal("§a[RTP] Target exploration distance reached!"));
            }
            setEnabled(false);
            return;
        }

        if (ticksRemaining > 0) {
            ticksRemaining--;
            return;
        }

        // Send RTP command for specified region
        String reg = region.get().trim().toLowerCase();
        String cmd = reg.isEmpty() ? "rtp" : "rtp " + reg;
        mc.player.connection.sendCommand(cmd);

        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§6[RTP] §7Sent §e/" + cmd + "§7 (Distance: §f" + (int) currentDist + "m§7)"));
        }

        ticksRemaining = delaySeconds.get() * 20;
    }
}
