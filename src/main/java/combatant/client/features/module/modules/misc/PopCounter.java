/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.pvp.opponents.TotemPopCounter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

@ModuleInfo(
        id = "popcounter",
        displayName = "PopCounter",
        aliases = {"TotemPopCounter", "TotemPops"},
        category = ModuleCategory.MISC,
        description = "Counts and notifies totem pops and deaths of nearby players in chat."
)
public final class PopCounter extends Module {

    private static final byte EVENT_DEATH = 3;
    private static final byte EVENT_TOTEM_POP = 35;

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue chatAnnouncement =
            bool("popCounterChat", "chat", true);

    private final BooleanValue selfAnnouncement =
            bool("popCounterSelf", "self", true);

    private final BooleanValue soundNotification =
            bool("popCounterSound", "sound", false);

    private final BooleanValue resetOnDeath =
            bool("popCounterResetOnDeath", "reset_on_death", true);

    private final BooleanValue resetOnLogout =
            bool("popCounterResetOnLogout", "reset_on_logout", true);

    private final NumberValue<Integer> resetAfterSeconds =
            num("popCounterResetAfterSeconds", "reset_after_seconds", 300, 0, 600);

    private int selfPops = 0;

    @Override
    public void onEnable() {
        TotemPopCounter.setEnabled(true);
        syncCounterConfig();
    }

    @Override
    public void onDisable() {
        selfPops = 0;
    }

    private void syncCounterConfig() {
        TotemPopCounter.configure(
                TotemPopCounter.options()
                        .withEnabled(true)
                        .withResetOnDeath(resetOnDeath.get())
                        .withResetOnLogout(resetOnLogout.get())
                        .withResetAfterMs(resetAfterSeconds.get() * 1000L)
        );
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || mc.level == null) return;

        Packet<?> packet = event.getPacket();
        if (!(packet instanceof ClientboundEntityEventPacket entityPacket)) {
            return;
        }

        byte eventId = entityPacket.getEventId();
        if (eventId == EVENT_TOTEM_POP) {
            handleTotemPop(entityPacket);
        } else if (eventId == EVENT_DEATH) {
            handleDeath(entityPacket);
        }
    }

    private void handleTotemPop(ClientboundEntityEventPacket packet) {
        if (mc.level == null) return;

        Entity entity = packet.getEntity(mc.level);
        if (!(entity instanceof Player player)) return;

        boolean isSelf = player.equals(mc.player);
        if (isSelf && !selfAnnouncement.get()) return;

        int count;
        if (isSelf) {
            selfPops++;
            count = selfPops;
        } else {
            TotemPopCounter.recordPop(player);
            count = TotemPopCounter.getCount(player.getUUID());
        }

        if (chatAnnouncement.get()) {
            String name = isSelf ? "You" : player.getName().getString();
            String verb = isSelf ? "popped" : "popped";
            String suffix = count == 1 ? "totem" : "totems";
            CommandOutput.send(
                    Component.literal(name + " " + verb + " " + count + " " + suffix + "!"),
                    isSelf ? CommandOutput.Tone.WARNING : CommandOutput.Tone.INFO
            );
        }

        if (soundNotification.get() && mc.player != null) {
            mc.player.playSound(SoundEvents.TOTEM_USE, 1.0f, 1.0f);
        }
    }

    private void handleDeath(ClientboundEntityEventPacket packet) {
        if (mc.level == null) return;

        Entity entity = packet.getEntity(mc.level);
        if (!(entity instanceof Player player)) return;

        boolean isSelf = player.equals(mc.player);
        int count = isSelf ? selfPops : TotemPopCounter.getCount(player.getUUID());

        if (count > 0 && chatAnnouncement.get()) {
            String name = isSelf ? "You" : player.getName().getString();
            String suffix = count == 1 ? "totem" : "totems";
            CommandOutput.send(
                    Component.literal(name + " died after popping " + count + " " + suffix + "!"),
                    isSelf ? CommandOutput.Tone.ERROR : CommandOutput.Tone.SUCCESS
            );
        }

        if (isSelf) {
            selfPops = 0;
        } else {
            TotemPopCounter.onPlayerDeath(player.getUUID());
        }
    }
}
