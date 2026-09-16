/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.network.TransferOrigin;

@ModuleInfo(
        id = "bowbomb",
        displayName = "BowBomb",
        category = ModuleCategory.COMBAT
)
public final class BowBomb extends Module {

    private final NumberValue<Integer> packets = num("packets", 50, 10, 200);
    private final BooleanValue bypass = bool("bypass", true);

    private final Minecraft mc = Minecraft.getInstance();
    private boolean shooting = false;

    @EventHandler
    public void onPacketSend(PacketEvent event) {
        if (!isEnabled() || event.getOrigin() != TransferOrigin.OUTGOING) return;
        if (mc.player == null || mc.getConnection() == null) return;

        if (event.getPacket() instanceof ServerboundPlayerActionPacket action) {
            if (action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                ItemStack using = mc.player.getUseItem();
                if (using.getItem() instanceof BowItem || using.getItem() instanceof CrossbowItem) {
                    if (!shooting) {
                        shooting = true;
                        LocalPlayer p = mc.player;
                        double x = p.getX();
                        double y = p.getY();
                        double z = p.getZ();

                        for (int i = 0; i < packets.get(); i++) {
                            if (bypass.get()) {
                                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                                        x, y - 1e-10, z, true, false
                                ));
                                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                                        x, y + 1e-10, z, false, false
                                ));
                            } else {
                                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                                        x, y - 1e-10, z, true, false
                                ));
                            }
                        }
                        shooting = false;
                    }
                }
            }
        }
    }
}
