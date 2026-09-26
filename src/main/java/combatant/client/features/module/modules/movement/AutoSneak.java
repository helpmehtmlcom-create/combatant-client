/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;

@ModuleInfo(
        id = "autosneak",
        displayName = "AutoSneak",
        aliases = {"Sneak", "SilentSneak"},
        category = ModuleCategory.MOVEMENT,
        description = "Automatically sneaks physically or sends silent packet sneaking."
)
public final class AutoSneak extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode =
            enumSetting("autoSneakMode", "mode", Mode.INPUT, Mode.values());

    @EventHandler
    public void onMovementInput(MovementInputEvent event) {
        if (!isEnabled()) return;

        if (mode.get() == Mode.INPUT) {
            event.setSneak(true);
        }
    }

    @EventHandler(priority = 100)
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || mode.get() != Mode.PACKET) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundPlayerInputPacket inputPacket) {
            Input in = inputPacket.input();
            if (!in.shift()) {
                Input spoofed = new Input(in.forward(), in.backward(), in.left(), in.right(), in.jump(), true, in.sprint());
                event.cancel();
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundPlayerInputPacket(spoofed));
                }
            }
        }
    }

    public enum Mode {
        INPUT,
        PACKET
    }
}
