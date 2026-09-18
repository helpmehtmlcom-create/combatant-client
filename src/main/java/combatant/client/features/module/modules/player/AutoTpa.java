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
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.features.relations.PlayerRelations;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatically sends or accepts TPA requests with humanized delay on DonutSMP.
 * Ported and enhanced from 67Client's AutoTpaModule.
 */
@ModuleInfo(
        id = "autotpa",
        displayName = "AutoTpa",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        description = "Automatically accepts or sends TPA requests with humanized delays on DonutSMP."
)
public class AutoTpa extends Module {

    public enum Mode {
        ACCEPT,
        SEND
    }

    public enum SendType {
        TPA,
        TPAHERE
    }

    private final EnumValue<Mode> mode =
            enumSetting("autotpa_mode", "mode", Mode.ACCEPT, Mode.values());
    private final EnumValue<SendType> sendType =
            enumSetting("autotpa_send_type", "send_type", SendType.TPA, SendType.values());
    private final StringValue target =
            text("autotpa_target", "target", "");
    private final NumberValue<Integer> delay =
            num("autotpa_delay", "delay_ms", 2000, 250, 10000);
    private final NumberValue<Integer> humanize =
            num("autotpa_humanize", "humanize_percent", 15, 0, 50);
    private final BooleanValue friendsOnly =
            bool("autotpa_friends_only", "friends_only", false);
    private final BooleanValue notify =
            bool("autotpa_notify", "notify", true);

    private static final Pattern TPA_REQUEST_PATTERN = Pattern.compile("(?i)([a-zA-Z0-9_]{3,16})\\s+has\\s+requested\\s+to\\s+teleport");
    private static final Pattern TPAHERE_REQUEST_PATTERN = Pattern.compile("(?i)([a-zA-Z0-9_]{3,16})\\s+has\\s+requested\\s+that\\s+you\\s+teleport");

    private long nextActionAtMs = -1L;
    private String pendingCommand = null;

    @Override
    public void onEnable() {
        nextActionAtMs = -1L;
        pendingCommand = null;
        if (mode.get() == Mode.SEND) {
            scheduleNext(0);
        }
    }

    @Override
    public void onDisable() {
        nextActionAtMs = -1L;
        pendingCommand = null;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mode.get() != Mode.ACCEPT) return;
        if (event.getPacket() instanceof ClientboundSystemChatPacket chatPacket) {
            String text = chatPacket.content().getString();
            Matcher tpaMatcher = TPA_REQUEST_PATTERN.matcher(text);
            Matcher tpaHereMatcher = TPAHERE_REQUEST_PATTERN.matcher(text);

            String sender = null;
            if (tpaMatcher.find()) {
                sender = tpaMatcher.group(1);
            } else if (tpaHereMatcher.find()) {
                sender = tpaHereMatcher.group(1);
            }

            if (sender != null) {
                if (friendsOnly.get() && !PlayerRelations.get().isFriend(sender)) {
                    return;
                }
                pendingCommand = "tpaccept";
                scheduleNext(delay.get().longValue());
                if (notify.get()) {
                    sendNotice("§d[AutoTPA] §7Accepting TPA from §f" + sender + "§7...");
                }
            }
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }

        if (nextActionAtMs >= 0L && System.currentTimeMillis() >= nextActionAtMs) {
            if (mode.get() == Mode.ACCEPT && pendingCommand != null) {
                mc.player.connection.sendCommand(pendingCommand);
                pendingCommand = null;
                nextActionAtMs = -1L;
            } else if (mode.get() == Mode.SEND) {
                String targetName = target.get().trim();
                if (!targetName.isEmpty()) {
                    String cmd = (sendType.get() == SendType.TPAHERE ? "tpahere " : "tpa ") + targetName;
                    mc.player.connection.sendCommand(cmd);
                    if (notify.get()) {
                        sendNotice("§d[AutoTPA] §7Sent §f/" + cmd);
                    }
                }
                scheduleNext(delay.get().longValue());
            }
        }
    }

    private void scheduleNext(long baseDelayMs) {
        double base = baseDelayMs > 0 ? baseDelayMs : delay.get().doubleValue();
        double factor = humanize.get().doubleValue() / 100.0;
        double swing = factor <= 0.0 ? 1.0 : 1.0 + (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * factor;
        long wait = Math.max(0L, Math.round(base * swing));
        nextActionAtMs = System.currentTimeMillis() + wait;
    }

    private void sendNotice(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
        }
    }
}
