/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "bowbomb",
        displayName = "BowBomb",
        aliases = {"OneShotBow", "ArrowExploit"},
        category = ModuleCategory.COMBAT,
        description = "Amplifies arrow damage and velocity upon bow release via packet manipulation."
)
public final class BowBomb extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> packets =
            num("bowBombPackets", "packets", 30, 5, 100);

    private final NumberValue<Float> offset =
            num("bowBombOffset", "offset", 0.1f, 0.01f, 1.0f);

    private final BooleanValue spoofGround =
            bool("bowBombSpoofGround", "spoof_ground", true);

    private boolean sending = false;

    @EventHandler(priority = 100)
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || sending || mc.player == null || mc.getConnection() == null) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundPlayerActionPacket actionPacket) {
            if (actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                if (mc.player.getUseItem().is(Items.BOW)) {
                    sending = true;
                    try {
                        double x = mc.player.getX();
                        double y = mc.player.getY();
                        double z = mc.player.getZ();
                        float off = offset.get();
                        int count = packets.get();
                        boolean ground = spoofGround.get();

                        for (int i = 0; i < count; i++) {
                            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y - off, z, ground, false));
                            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + off, z, false, false));
                        }
                    } finally {
                        sending = false;
                    }
                }
            }
        }
    }
}
