/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.DisableSettingI18n;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@ModuleInfo(
        id = "autoez",
        displayName = "AutoEZ",
        category = ModuleCategory.MISC,
        aliases = {"ez", "autotoxic"},
        description = "Automatically sends customizable defeat messages in chat after winning a fight."
)
public class AutoEZ extends Module {

    public enum EZMode implements EnumValue.IdProvider {
        PUBLIC("Public"),
        DIRECT("Direct"),
        CLIENT_ONLY("Client-Only");

        private final String displayName;

        EZMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final String SETTING_MODE = "mode";
    private static final String SETTING_SUFFIX = "suffix";

    @DisableSettingI18n(name = false, options = true)
    private final EnumValue<EZMode> mode = enumSetting(
            "autoez_mode",
            SETTING_MODE,
            EZMode.PUBLIC,
            EZMode.values()
    );

    private final StringValue suffix = text(
            "autoez_suffix",
            SETTING_SUFFIX,
            "Combatant owns you and all!"
    );

    private final Minecraft mc = Minecraft.getInstance();

    private static final long TARGET_TIMEOUT_MS = 15000L;
    private static final int DISPATCH_DELAY_TICKS = 8;

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

    public StringValue getSuffix() {
        return suffix;
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

    private boolean isIgnored(Player player) {
        if (player == null || mc.player == null || player.getUUID().equals(mc.player.getUUID())) return true;
        String name = player.getName().getString();
        CategoryType type = CategoryRules.determine(name);
        return type == CategoryType.FRIEND || type == CategoryType.BEDWARS_SELF;
    }

    private void trackTarget(Player player) {
        if (player == null || isIgnored(player)) return;
        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        targets.put(uuid, new TrackedTarget(uuid, name, System.currentTimeMillis()));
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!isEnabled() || mc.player == null || event == null) return;
        Entity target = event.getTarget();
        if (target instanceof Player targetPlayer) {
            trackTarget(targetPlayer);
        } else if (target instanceof EndCrystal crystal && mc.level != null) {
            Vec3 pos = crystal.position();
            for (Player player : mc.level.players()) {
                if (player != mc.player && player.distanceToSqr(pos) <= 144.0) {
                    trackTarget(player);
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        if (event.getPacket() instanceof ClientboundDamageEventPacket damagePacket) {
            if (damagePacket.sourceCauseId() == mc.player.getId() || damagePacket.sourceDirectId() == mc.player.getId()) {
                Entity victim = mc.level.getEntity(damagePacket.entityId());
                if (victim instanceof Player victimPlayer) {
                    trackTarget(victimPlayer);
                }
            }
        } else if (event.getPacket() instanceof ClientboundExplodePacket explodePacket) {
            Vec3 center = explodePacket.center();
            if (center != null && mc.player.distanceToSqr(center) <= 144.0) {
                for (Player player : mc.level.players()) {
                    if (player != mc.player && player.distanceToSqr(center) <= 144.0) {
                        trackTarget(player);
                    }
                }
            }
        } else if (event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() == 3) { // Death event
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

        long now = System.currentTimeMillis();
        targets.entrySet().removeIf(entry -> now - entry.getValue().lastAttackedTime > TARGET_TIMEOUT_MS);

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

    private String formatMessage(String targetName) {
        String customSuffix = suffix.get();
        if (customSuffix == null || customSuffix.isBlank()) {
            customSuffix = "Combatant owns you and all!";
        }
        if (customSuffix.contains("{player}")) {
            return customSuffix.replace("{player}", targetName);
        }
        return "GG " + targetName + ", " + customSuffix;
    }

    private void queueKillMessage(String targetName) {
        if (targetName == null || targetName.isBlank()) return;
        String formatted = formatMessage(targetName);
        messageQueue.add(new PendingMessage(targetName, formatted, currentTick + DISPATCH_DELAY_TICKS));
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
