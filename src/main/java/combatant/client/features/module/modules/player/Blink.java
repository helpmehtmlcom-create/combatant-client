/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.BlinkPacketEvent;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.network.BlinkManager;
import combatant.client.util.network.TransferOrigin;

@ModuleInfo(
        id = "blink",
        displayName = "Blink",
        category = ModuleCategory.PLAYER
)
public final class Blink extends Module {

    public enum Mode {
        FLUSH,
        CANCEL
    }

    private final EnumValue<Mode> mode = enumMode("mode", Mode.FLUSH);
    private final NumberValue<Integer> maxPackets = num("max_packets", 200, 10, 1000);
    private final BooleanValue pulse = bool("pulse", false);
    private final NumberValue<Integer> pulseTicks = num("pulse_ticks", 20, 2, 100);

    private final Minecraft mc = Minecraft.getInstance();
    private int packetCount = 0;
    private int ticks = 0;
    private Vec3 startPos = null;

    @Override
    public void onEnable() {
        packetCount = 0;
        ticks = 0;
        if (mc.player != null) {
            startPos = mc.player.position();
        }
    }

    @Override
    public void onDisable() {
        if (mode.get() == Mode.CANCEL) {
            BlinkManager.INSTANCE.cancelOutgoingMovement();
        } else {
            BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
        }
        packetCount = 0;
        ticks = 0;
        startPos = null;
    }

    @EventHandler
    public void onBlinkPacket(BlinkPacketEvent event) {
        if (!isEnabled()) return;
        if (event.getOrigin() != TransferOrigin.OUTGOING) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundMovePlayerPacket) {
            event.setAction(BlinkManager.Action.QUEUE);
            packetCount++;

            if (packetCount >= maxPackets.get()) {
                toggle();
            }
        }
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled()) return;

        if (pulse.get()) {
            ticks++;
            if (ticks >= pulseTicks.get()) {
                BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
                packetCount = 0;
                ticks = 0;
            }
        }
    }

    public int getPacketCount() {
        return packetCount;
    }

    public Vec3 getStartPos() {
        return startPos;
    }
}
