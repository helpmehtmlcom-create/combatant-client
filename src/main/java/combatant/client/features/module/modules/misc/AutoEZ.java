/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ModuleInfo(
        id = "autoez",
        displayName = "AutoEZ",
        aliases = {"AutoGG", "KillSms"},
        category = ModuleCategory.MISC,
        description = "Automatically sends a customizable message in public chat when you kill an opponent."
)
public final class AutoEZ extends Module {

    private static final byte EVENT_DEATH = 3;
    private static final long KILL_TIMEOUT_MS = 10_000L;

    private final Minecraft mc = Minecraft.getInstance();

    private final StringValue message =
            text("autoEzMessage", "message", "GG, {player}! Combatant on top!");

    private final EnumValue<EzMode> mode =
            enumSetting("autoEzMode", "mode", EzMode.PUBLIC_CHAT, EzMode.values());

    private final NumberValue<Integer> delayTicks =
            num("autoEzDelayTicks", "delay_ticks", 5, 0, 40);

    private final Map<UUID, Long> recentAttacks = new HashMap<>();
    private final Deque<QueuedMessage> messageQueue = new ArrayDeque<>();

    @Override
    public void onDisable() {
        recentAttacks.clear();
        messageQueue.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        long now = System.currentTimeMillis();
        recentAttacks.entrySet().removeIf(entry -> now - entry.getValue() > KILL_TIMEOUT_MS);

        if (!messageQueue.isEmpty()) {
            QueuedMessage queued = messageQueue.peek();
            if (queued.delay > 0) {
                queued.delay--;
            } else {
                messageQueue.poll();
                sendMessage(queued.text);
            }
        }
    }

    @EventHandler
    public void onAttack(AttackEntityEvent event) {
        if (!isEnabled() || mc.player == null) return;
        if (event.getPlayer() != mc.player) return;

        if (event.getTarget() instanceof Player victim && !victim.equals(mc.player)) {
            recentAttacks.put(victim.getUUID(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || mc.level == null) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundEntityEventPacket entityPacket) {
            if (entityPacket.getEventId() == EVENT_DEATH) {
                Entity entity = entityPacket.getEntity(mc.level);
                if (entity instanceof Player victim && !victim.equals(mc.player)) {
                    UUID uuid = victim.getUUID();
                    Long lastAttack = recentAttacks.remove(uuid);

                    if (lastAttack != null && (System.currentTimeMillis() - lastAttack <= KILL_TIMEOUT_MS)) {
                        String raw = message.get();
                        String formatted = raw.replace("{player}", victim.getName().getString());
                        messageQueue.add(new QueuedMessage(formatted, delayTicks.get()));
                    }
                }
            }
        }
    }

    private void sendMessage(String text) {
        if (text == null || text.isBlank() || mc.getConnection() == null) return;

        if (mode.get() == EzMode.PUBLIC_CHAT) {
            mc.getConnection().sendChat(text);
        } else {
            CommandOutput.send(Component.literal(text), CommandOutput.Tone.SUCCESS);
        }
    }

    public enum EzMode {
        PUBLIC_CHAT,
        CLIENT_ONLY
    }

    private static final class QueuedMessage {
        final String text;
        int delay;

        QueuedMessage(String text, int delay) {
            this.text = text;
            this.delay = delay;
        }
    }
}
