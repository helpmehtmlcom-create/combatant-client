/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.network.BlinkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.world.level.block.Blocks;

@ModuleInfo(
        id = "portalgodmode",
        displayName = "PortalGodMode",
        aliases = {"PortalInvulnerability", "PortalGod"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.EXPLOIT,
        description = "module.portalgodmode.description")
public final class PortalGodMode extends Module {
    private static final long PORTAL_WINDOW_MS = 3500L;

    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue portalOnly = bool("portalGodModePortalOnly", "portal_only", true);
    private final NumberValue<Integer> maxHoldMs = num("portalGodModeMaxHoldMs", "max_hold_ms", 2500, 250, 10000);

    private ServerboundAcceptTeleportationPacket pendingAck;
    private long portalSeenAt;
    private long holdStartedAt;

    @Override
    public void onEnable() {
        clearState();
    }

    @Override
    public void onDisable() {
        releaseAck();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.getConnection() == null) {
            clearState();
            return;
        }

        long now = System.currentTimeMillis();
        if (isInsidePortal(player)) {
            portalSeenAt = now;
        }
        if (pendingAck != null && now - holdStartedAt >= maxHoldMs.get()) {
            releaseAck();
        }
    }

    @EventHandler(priority = 100)
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || !(event.getPacket() instanceof ServerboundAcceptTeleportationPacket ack)) return;

        long now = System.currentTimeMillis();
        if (portalOnly.get() && (portalSeenAt == 0L || now - portalSeenAt > PORTAL_WINDOW_MS)) return;

        if (pendingAck == null) {
            holdStartedAt = now;
        }
        pendingAck = ack;
        event.cancel();
    }

    private boolean isInsidePortal(LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        return mc.level.getBlockState(feet).is(Blocks.NETHER_PORTAL)
                || mc.level.getBlockState(feet.above()).is(Blocks.NETHER_PORTAL);
    }

    private void releaseAck() {
        ServerboundAcceptTeleportationPacket ack = pendingAck;
        clearState();
        if (ack != null && mc.getConnection() != null) {
            BlinkManager.INSTANCE.sendSilently(ack);
        }
    }

    private void clearState() {
        pendingAck = null;
        portalSeenAt = 0L;
        holdStartedAt = 0L;
    }
}
