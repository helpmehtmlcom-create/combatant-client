/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.world.level.block.Blocks;

@ModuleInfo(
        id = "portalgodmode",
        displayName = "PortalGodMode",
        aliases = {"PortalInvulnerability", "PortalGod"},
        category = ModuleCategory.PLAYER,
        description = "Gives invulnerability while in a Nether portal by canceling teleport confirmation packets."
)
public final class PortalGodMode extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue cancelTeleport =
            bool("portalGodModeCancelTeleport", "cancel_teleport", true);

    private final BooleanValue portalOnly =
            bool("portalGodModePortalOnly", "portal_only", true);

    @EventHandler(priority = 100)
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        Packet<?> packet = event.getPacket();
        if (cancelTeleport.get() && packet instanceof ServerboundAcceptTeleportationPacket) {
            if (!portalOnly.get() || isInsidePortal(player)) {
                event.cancel();
            }
        }
    }

    private boolean isInsidePortal(LocalPlayer player) {
        if (mc.level == null) return false;
        BlockPos feet = player.blockPosition();
        return mc.level.getBlockState(feet).is(Blocks.NETHER_PORTAL)
                || mc.level.getBlockState(feet.above()).is(Blocks.NETHER_PORTAL);
    }
}
