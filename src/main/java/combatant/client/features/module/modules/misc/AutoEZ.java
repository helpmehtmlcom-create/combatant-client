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
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@ModuleInfo(
        id = "autoez",
        displayName = "AutoEZ",
        category = ModuleCategory.MISC,
        aliases = {"ez", "autotoxic"}
)
public class AutoEZ extends Module {

    public enum EZMode {
        PUBLIC,
        DIRECT,
        CLIENT_ONLY
    }

    private final EnumValue<EZMode> mode = enumSetting("mode", "mode", EZMode.PUBLIC, EZMode.values());
    private final StringValue customMessage = text("customMessage", "custom_message", "GG {player}, Combatant owns you and all!");
    private final NumberValue<Integer> delay = num("delay", "delay", 10, 0, 40);

    private final Minecraft mc = Minecraft.getInstance();

    private static final long TARGET_TIMEOUT_MS = 15000L;

    private static class TrackedTarget {
        final UUID uuid;
        final String name;
        long lastAttackedTime;

        TrackedTarget(UUID uuid, String name, long lastAttackedTime) {
            this.uuid = uuid;
            this.name = name;
            this.lastAttackedTime = lastAttackedTime;
        }
    }

    private record PendingMessage(String targetName, String message, int dispatchTick) {}

    private final Map<UUID, TrackedTarget> targets = new HashMap<>();
    private final Deque<PendingMessage> messageQueue = new ArrayDeque<>();
    private int currentTick = 0;

    public EnumValue<EZMode> getMode() {
        return mode;
    }

    public StringValue getCustomMessage() {
        return customMessage;
    }

    public NumberValue<Integer> getDelay() {
        return delay;
    }

    @Override
    public void onEnable() {
        targets.clear();
        messageQueue.clear();
        currentTick = 0;
    }

    @Override
    public void onDisable() {
        targets.clear();
        messageQueue.clear();
        currentTick = 0;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!isEnabled() || mc.player == null) return;
        if (event.getTarget() instanceof Player targetPlayer && targetPlayer != mc.player) {
            UUID uuid = targetPlayer.getUUID();
            String name = targetPlayer.getName().getString();
            targets.put(uuid, new TrackedTarget(uuid, name, System.currentTimeMillis()));
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || mc.level == null || targets.isEmpty()) return;

        if (event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() == 3) { // Entity death status
                Entity entity = packet.getEntity(mc.level);
                if (entity instanceof Player player && player != mc.player) {
                    checkTargetDeath(player.getUUID(), player.getName().getString());
                }
            }
        } else if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket killPacket) {
            Entity entity = mc.level.getEntity(killPacket.playerId());
            if (entity instanceof Player player && player != mc.player) {
                checkTargetDeath(player.getUUID(), player.getName().getString());
            }
        } else if (event.getPacket() instanceof ClientboundRemoveEntitiesPacket removePacket) {
            for (int id : removePacket.getEntityIds()) {
                Entity entity = mc.level.getEntity(id);
                if (entity instanceof Player player && player != mc.player) {
                    if (player.getHealth() <= 0.0f || player.isDeadOrDying()) {
                        checkTargetDeath(player.getUUID(), player.getName().getString());
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(GameTickEvent event) {
        tickLogic();
    }

    @Override
    public void onTick() {
        tickLogic();
    }

    private void tickLogic() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        currentTick++;

        // Clean up expired tracked targets
        long now = System.currentTimeMillis();
        targets.entrySet().removeIf(entry -> now - entry.getValue().lastAttackedTime > TARGET_TIMEOUT_MS);

        // Check if any tracked player is dead in the world
        if (!targets.isEmpty()) {
            Iterator<Map.Entry<UUID, TrackedTarget>> it = targets.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, TrackedTarget> entry = it.next();
                Player player = mc.level.getPlayerByUUID(entry.getKey());
                if (player != null && (player.getHealth() <= 0.0f || player.isDeadOrDying() || player.isRemoved())) {
                    String targetName = entry.getValue().name;
                    it.remove();
                    queueKillMessage(targetName);
                }
            }
        }

        // Process message queue
        while (!messageQueue.isEmpty()) {
            PendingMessage pending = messageQueue.peek();
            if (pending != null && currentTick >= pending.dispatchTick()) {
                messageQueue.poll();
                sendMessage(pending.targetName(), pending.message());
            } else {
                break;
            }
        }
    }

    private void checkTargetDeath(UUID uuid, String fallbackName) {
        TrackedTarget tracked = targets.remove(uuid);
        if (tracked != null) {
            String targetName = (tracked.name != null && !tracked.name.isBlank()) ? tracked.name : fallbackName;
            queueKillMessage(targetName);
        }
    }

    private void queueKillMessage(String targetName) {
        if (targetName == null || targetName.isBlank()) return;

        String template = customMessage.get();
        if (template == null || template.isBlank()) {
            template = "GG {player}, Combatant owns you and all!";
        }
        String formatted = template.replace("{player}", targetName);

        int delayTicks = delay.get();
        if (delayTicks <= 0) {
            sendMessage(targetName, formatted);
        } else {
            messageQueue.add(new PendingMessage(targetName, formatted, currentTick + delayTicks));
        }
    }

    private void sendMessage(String targetName, String message) {
        if (mc.player == null) return;

        switch (mode.get()) {
            case PUBLIC -> {
                if (mc.player.connection != null) {
                    mc.player.connection.sendChat(message);
                }
            }
            case DIRECT -> {
                if (mc.player.connection != null) {
                    mc.player.connection.sendCommand("msg " + targetName + " " + message);
                }
            }
            case CLIENT_ONLY -> {
                Component component = Component.literal(message);
                if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                    mc.gui.hud.getChat().addClientSystemMessage(component);
                } else {
                    CommandOutput.send(component);
                }
            }
        }
    }
}
