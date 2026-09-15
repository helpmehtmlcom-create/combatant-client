/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.EntityAccessor;
import combatant.client.mixins.accessors.LocalPlayerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Blocks;

@ModuleInfo(
        id = "portalgodmode",
        displayName = "PortalGodMode",
        category = ModuleCategory.PLAYER,
        aliases = {"portalchat", "portalgod"}
)
public final class PortalGodMode extends Module {

    private final BooleanValue godmode = bool("godmode", "godmode", true);
    private final BooleanValue chatInPortal = bool("chatInPortal", "chatInPortal", true);

    private final Minecraft mc = Minecraft.getInstance();

    public PortalGodMode() {
    }

    public BooleanValue getGodmode() {
        return godmode;
    }

    public BooleanValue getChatInPortal() {
        return chatInPortal;
    }

    @Override
    public void onTick() {
        handleTick();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        handleTick();
    }

    private void handleTick() {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            return;
        }

        if (chatInPortal.get() && isInPortal()) {
            if (mc.player instanceof LocalPlayerAccessor playerAccessor) {
                playerAccessor.combatant$setNauseaIntensity(0.0f);
                playerAccessor.combatant$setLastNauseaIntensity(0.0f);
            }

            if (mc.player instanceof EntityAccessor entityAccessor) {
                PortalProcessor portalProcessor = entityAccessor.combatant$getPortalManager();
                if (portalProcessor != null) {
                    portalProcessor.setAsInsidePortalThisTick(false);
                }
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || event == null || mc.player == null) {
            return;
        }

        if (godmode.get() && isInPortal()) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof ServerboundAcceptTeleportationPacket) {
                event.cancel();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || event == null || mc.player == null) {
            return;
        }

        if (godmode.get() && isInPortal()) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof ClientboundPlayerPositionPacket) {
                // Tracking position updates while in portal loading state
            }
        }
    }

    public boolean isInPortal() {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        if (isInPortalBlock()) {
            return true;
        }

        if (mc.player instanceof EntityAccessor entityAccessor) {
            PortalProcessor portalProcessor = entityAccessor.combatant$getPortalManager();
            return portalProcessor != null && portalProcessor.isInsidePortalThisTick();
        }

        return false;
    }

    public boolean isInPortalBlock() {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        BlockPos pos = mc.player.blockPosition();
        if (mc.level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
            return true;
        }
        return mc.level.getBlockState(pos.above()).is(Blocks.NETHER_PORTAL);
    }
}
