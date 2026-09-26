/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

@ModuleInfo(
        id = "norotate",
        displayName = "NoRotate",
        aliases = {"KeepRotations", "AntiForceLook"},
        category = ModuleCategory.PLAYER,
        description = "Prevents the server from forcefully rotating your camera angles."
)
public final class NoRotate extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private float savedYaw = 0.0f;
    private float savedPitch = 0.0f;
    private boolean hasSavedRotations = false;

    @Override
    public void onDisable() {
        hasSavedRotations = false;
    }

    @EventHandler(priority = 100)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundPlayerPositionPacket) {
            LocalPlayer player = mc.player;
            if (player != null) {
                savedYaw = player.getYRot();
                savedPitch = player.getXRot();
                hasSavedRotations = true;
            }
        }
    }

    @EventHandler(priority = -100)
    public void onPacketReceivePost(PacketEvent.ReceivePost event) {
        if (!isEnabled() || !hasSavedRotations) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundPlayerPositionPacket) {
            LocalPlayer player = mc.player;
            if (player != null) {
                player.setYRot(savedYaw);
                player.setXRot(savedPitch);
            }
            hasSavedRotations = false;
        }
    }
}
