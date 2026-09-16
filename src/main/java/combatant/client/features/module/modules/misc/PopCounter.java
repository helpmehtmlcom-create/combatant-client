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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    public enum PopColor {
        RED(ChatFormatting.RED),
        GREEN(ChatFormatting.GREEN),
        GOLD(ChatFormatting.GOLD),
        AQUA(ChatFormatting.AQUA);

        private final ChatFormatting formatting;

        PopColor(ChatFormatting formatting) {
            this.formatting = formatting;
        }

        public ChatFormatting getFormatting() {
            return formatting;
        }
    }

    private final BooleanValue chat = bool("chat", true);
    private final BooleanValue hud = bool("hud", true);
    private final EnumValue<PopColor> color = enumSetting("color", "color", PopColor.GOLD, PopColor.values());

    private final Minecraft mc = Minecraft.getInstance();
    private final Map<UUID, Integer> popMap = new HashMap<>();
    private final Map<UUID, String> nameMap = new HashMap<>();

    public BooleanValue getChat() {
        return chat;
    }

    public BooleanValue getHud() {
        return hud;
    }

    public EnumValue<PopColor> getColor() {
        return color;
    }

    public Map<UUID, Integer> getPopMap() {
        return Collections.unmodifiableMap(popMap);
    }

    @Override
    public void onEnable() {
        popMap.clear();
        nameMap.clear();
    }

    @Override
    public void onDisable() {
        popMap.clear();
        nameMap.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || mc.level == null) return;

        if (event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() == 35) {
                Entity entity = packet.getEntity(mc.level);
                if (entity instanceof Player player && player != mc.player && (mc.player == null || !player.getUUID().equals(mc.player.getUUID()))) {
                    UUID uuid = player.getUUID();
                    int count = popMap.getOrDefault(uuid, 0) + 1;
                    popMap.put(uuid, count);
                    String playerName = player.getName().getString();
                    nameMap.put(uuid, playerName);

                    String message = String.format(Locale.ROOT, "[PopCounter] %s popped #%d totems!", playerName, count);
                    outputMessage(message);
                }
            } else if (packet.getEventId() == 3) {
                Entity entity = packet.getEntity(mc.level);
                if (entity instanceof Player player && player != mc.player) {
                    handleDeath(player.getUUID(), player.getName().getString());
                }
            }
        } else if (event.getPacket() instanceof ClientboundRemoveEntitiesPacket removePacket) {
            for (int id : removePacket.getEntityIds()) {
                Entity entity = mc.level.getEntity(id);
                if (entity instanceof Player player && player != mc.player) {
                    if (popMap.containsKey(player.getUUID())) {
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

    private void checkPlayerDeaths() {
        if (mc.level == null || popMap.isEmpty()) return;

        List<UUID> dead = null;
        for (UUID uuid : popMap.keySet()) {
            Player player = mc.level.getPlayerByUUID(uuid);
            if (player != null) {
                if (player.getHealth() <= 0.0f || player.isDeadOrDying() || player.isRemoved()) {
                    if (dead == null) dead = new ArrayList<>();
                    dead.add(uuid);
                }
            }
        }

        if (dead != null) {
            for (UUID uuid : dead) {
                handleDeath(uuid, nameMap.get(uuid));
            }
        }
    }

    private void handleDeath(UUID uuid, String fallbackName) {
        if (uuid == null) return;
        Integer count = popMap.remove(uuid);
        String name = nameMap.remove(uuid);
        if (count != null && count > 0) {
            String playerName = (name != null && !name.isBlank()) ? name : ((fallbackName != null && !fallbackName.isBlank()) ? fallbackName : "Player");
            String message = String.format(Locale.ROOT, "[PopCounter] %s died after popping %d totems!", playerName, count);
            outputMessage(message);
        }
    }

    private void outputMessage(String message) {
        if (chat.get()) {
            Component component = Component.literal(message).withStyle(color.get().getFormatting());
            if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                mc.gui.hud.getChat().addClientSystemMessage(component);
            } else {
                CommandOutput.send(component);
            }
        }
        if (hud.get()) {
            Notifier.info(message);
        }
    }
}
