/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Notifier;
import combatant.client.util.pvp.opponents.OpponentCooldownManager;
import combatant.client.util.pvp.opponents.TotemPopCounter;
import combatant.client.util.pvp.opponents.TotemPopSnapshot;
import combatant.client.util.sound.SoundSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@ModuleInfo(
        id = "popcounter",
        displayName = "PopCounter",
        category = ModuleCategory.MISC,
        aliases = {"totemcounter", "totempops"},
        description = "Tracks and announces totem pops of nearby players in chat or notifications."
)
public class PopCounter extends Module {

    public enum PopColor implements EnumValue.IdProvider {
        RED(ChatFormatting.RED, "Red"),
        GREEN(ChatFormatting.GREEN, "Green"),
        GOLD(ChatFormatting.GOLD, "Gold"),
        AQUA(ChatFormatting.AQUA, "Aqua");

        private final ChatFormatting formatting;
        private final String displayName;

        PopColor(ChatFormatting formatting, String displayName) {
            this.formatting = formatting;
            this.displayName = displayName;
        }

        public ChatFormatting getFormatting() {
            return formatting;
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

    private final BooleanValue chat = bool("popcounter_chat", "chat", true);
    private final BooleanValue soundAlert = bool("popcounter_sound_alert", "sound_alert", true);
    private final EnumValue<PopColor> color = enumSetting("popcounter_color", "color", PopColor.GOLD, PopColor.values());

    private final Minecraft mc = Minecraft.getInstance();
    private final Map<UUID, String> nameCache = new HashMap<>();

    public BooleanValue getChat() {
        return chat;
    }

    public BooleanValue getSoundAlert() {
        return soundAlert;
    }

    public EnumValue<PopColor> getColor() {
        return color;
    }

    public Map<UUID, Integer> getPopMap() {
        Map<UUID, Integer> map = new HashMap<>();
        for (Map.Entry<UUID, TotemPopSnapshot> entry : TotemPopCounter.snapshots().entrySet()) {
            map.put(entry.getKey(), entry.getValue().count());
        }
        return map;
    }

    @Override
    public void onEnable() {
        nameCache.clear();
    }

    @Override
    public void onDisable() {
        nameCache.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || mc.level == null) return;

        if (event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() == 35) { // Totem pop event
                Entity entity = packet.getEntity(mc.level);
                if (entity instanceof Player player && player != mc.player && (mc.player == null || !player.getUUID().equals(mc.player.getUUID()))) {
                    handlePop(player);
                }
            } else if (packet.getEventId() == 3) { // Death event
                Entity entity = packet.getEntity(mc.level);
                if (entity instanceof Player player && player != mc.player) {
                    handleDeath(player.getUUID(), player.getName().getString());
                }
            }
        } else if (event.getPacket() instanceof ClientboundRemoveEntitiesPacket removePacket) {
            for (int id : removePacket.getEntityIds()) {
                Entity entity = mc.level.getEntity(id);
                if (entity instanceof Player player && player != mc.player) {
                    if (TotemPopCounter.getCount(player.getUUID()) > 0) {
                        handleDeath(player.getUUID(), player.getName().getString());
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(GameTickEvent event) {
        checkPlayerDeaths();
    }

    @Override
    public void onTick() {
        checkPlayerDeaths();
    }

    private void handlePop(Player player) {
        UUID uuid = player.getUUID();
        String playerName = player.getName().getString();
        nameCache.put(uuid, playerName);

        TotemPopCounter.recordPop(player);
        try {
            OpponentCooldownManager.recordUse(uuid, Items.TOTEM_OF_UNDYING);
        } catch (Throwable ignored) {
        }

        int count = TotemPopCounter.getCount(uuid);

        if (soundAlert.get()) {
            SoundSystem.playCombatEffect("pop", 1.0f, 1.0f);
        }

        if (chat.get()) {
            String message = String.format(Locale.ROOT, "[PopCounter] %s popped #%d totems!", playerName, count);
            outputMessage(message);
        }
    }

    private void checkPlayerDeaths() {
        if (mc.level == null) return;
        for (Map.Entry<UUID, TotemPopSnapshot> entry : TotemPopCounter.snapshots().entrySet()) {
            UUID uuid = entry.getKey();
            if (entry.getValue().count() <= 0) continue;
            Player player = mc.level.getPlayerByUUID(uuid);
            if (player != null && (player.getHealth() <= 0.0f || player.isDeadOrDying() || player.isRemoved())) {
                handleDeath(uuid, nameCache.get(uuid));
            }
        }
    }

    private void handleDeath(UUID uuid, String fallbackName) {
        if (uuid == null) return;
        int count = TotemPopCounter.getCount(uuid);
        String name = nameCache.remove(uuid);
        if (count > 0) {
            TotemPopCounter.reset(uuid);
            String playerName = (name != null && !name.isBlank()) ? name : ((fallbackName != null && !fallbackName.isBlank()) ? fallbackName : uuid.toString().substring(0, 8));
            if (chat.get()) {
                String message = String.format(Locale.ROOT, "[PopCounter] %s died after popping %d totems!", playerName, count);
                outputMessage(message);
            }
        }
    }

    private void outputMessage(String message) {
        Component component = Component.literal(message).withStyle(color.get().getFormatting());
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(component);
        } else {
            CommandOutput.send(component);
        }
    }
}
