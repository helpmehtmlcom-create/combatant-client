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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
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

    private final NumberValue<Integer> maxPacketsPerTick =
            num("antiban_max_packets_tick", "max_packets_per_tick", 15, 5, 50);
    private final NumberValue<Integer> maxPacketsPerSecond =
            num("antiban_max_packets_sec", "max_packets_per_sec", 250, 50, 600);
    private final NumberValue<Integer> maxActionsPerTick =
            num("antiban_max_actions", "max_actions_per_tick", 4, 1, 20);
    private final NumberValue<Integer> maxClicksPerTick =
            num("antiban_max_clicks", "max_clicks_per_tick", 3, 1, 15);
    private final NumberValue<Integer> maxInteractsPerTick =
            num("antiban_max_interacts", "max_interacts_per_tick", 2, 1, 10);
    private final BooleanValue limitSwings =
            bool("antiban_limit_swings", "limit_swings", true);
    private final BooleanValue commandSpamProtect =
            bool("antiban_command_protect", "command_spam_protect", true);
    private final NumberValue<Integer> commandCooldownTicks =
            num("antiban_command_cooldown", "command_cooldown_ticks", 8, 2, 40);
    private final BooleanValue notifyThrottle =
            bool("antiban_notify", "notify_throttle", false);

    private int packetsThisTick = 0;
    private int actionsThisTick = 0;
    private int clicksThisTick = 0;
    private int interactsThisTick = 0;
    private int swingsThisTick = 0;
    private int commandCooldown = 0;
    private final int[] rollingPacketHistory = new int[20];
    private int historyIndex = 0;

    @Override
    public void onEnable() {
        packetsThisTick = 0;
        actionsThisTick = 0;
        clicksThisTick = 0;
        interactsThisTick = 0;
        swingsThisTick = 0;
        commandCooldown = 0;
        historyIndex = 0;
        java.util.Arrays.fill(rollingPacketHistory, 0);
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        rollingPacketHistory[historyIndex] = packetsThisTick;
        historyIndex = (historyIndex + 1) % 20;

        packetsThisTick = 0;
        actionsThisTick = 0;
        clicksThisTick = 0;
        interactsThisTick = 0;
        swingsThisTick = 0;
        if (commandCooldown > 0) {
            commandCooldown--;
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        Object packet = event.getPacket();
        if (isCriticalPacket(packet)) {
            return;
        }

        // Command and chat spam prevention
        if (packet instanceof ServerboundChatCommandPacket || packet instanceof ServerboundChatPacket) {
            if (commandSpamProtect.get()) {
                if (commandCooldown > 0) {
                    event.setCancelled(true);
                    notifyThrottled("Command / Chat spam throttled (" + commandCooldown + " ticks cooldown)");
                    return;
                }
                commandCooldown = commandCooldownTicks.get();
            }
        }

        // Container clicks rate limiter
        if (packet instanceof ServerboundContainerClickPacket) {
            clicksThisTick++;
            if (clicksThisTick > maxClicksPerTick.get()) {
                event.setCancelled(true);
                notifyThrottled("Container click rate limit exceeded");
                return;
            }
        }

        // Player actions (mining, block breaking, swap hands, sneak)
        if (packet instanceof ServerboundPlayerActionPacket) {
            actionsThisTick++;
            if (actionsThisTick > maxActionsPerTick.get()) {
                event.setCancelled(true);
                notifyThrottled("Player action rate limit exceeded");
                return;
            }
        }

        // Entity interacts (attacks, right clicks)
        if (packet instanceof ServerboundInteractPacket) {
            interactsThisTick++;
            if (interactsThisTick > maxInteractsPerTick.get()) {
                event.setCancelled(true);
                notifyThrottled("Entity interact rate limit exceeded");
                return;
            }
        }

        // Swing animation packets
        if (limitSwings.get() && packet instanceof ServerboundSwingPacket) {
            swingsThisTick++;
            if (swingsThisTick > 1) {
                event.setCancelled(true);
                return;
            }
        }

        // Overall packet burst and flood limiter
        packetsThisTick++;
        if (packetsThisTick > maxPacketsPerTick.get() || (getRollingPacketCount() + packetsThisTick) > maxPacketsPerSecond.get()) {
            event.setCancelled(true);
            notifyThrottled("Packet burst limit reached (DonutSMP safety)");
        }
    }

    private int getRollingPacketCount() {
        int sum = 0;
        for (int count : rollingPacketHistory) {
            sum += count;
        }
        return sum;
    }

    private boolean isCriticalPacket(Object packet) {
        return packet instanceof ServerboundKeepAlivePacket
                || packet instanceof ServerboundPongPacket
                || packet instanceof ServerboundPingRequestPacket
                || packet instanceof ServerboundAcceptTeleportationPacket;
    }

    private void notifyThrottled(String reason) {
        if (!notifyThrottle.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§6[AntiBan] §c" + reason));
        }
    }
}
