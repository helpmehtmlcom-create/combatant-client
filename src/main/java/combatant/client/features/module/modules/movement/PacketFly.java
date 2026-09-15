/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventCollision;
import combatant.client.events.impl.EventPushOutOfBlocks;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "packetfly",
        displayName = "PacketFly",
        category = ModuleCategory.MOVEMENT,
        aliases = {"pfly"}
)
public class PacketFly extends Module {

    public enum PacketFlyMode {
        FACTOR,
        SETBACK,
        FAST
    }

    public enum BoundsMode {
        UP,
        DOWN,
        PRESERVE
    }

    private final EnumValue<PacketFlyMode> mode =
            enumMode("mode", PacketFlyMode.FACTOR, PacketFlyMode.values());

    private final NumberValue<Double> speed =
            num("speed", 1.0, 0.1, 3.0);

    private final BooleanValue phase =
            bool("phase", true);

    private final BooleanValue antiKick =
            bool("anti_kick", true);

    private final EnumValue<BoundsMode> bounds =
            enumMode("bounds", BoundsMode.UP, BoundsMode.values());

    private final Set<Packet<?>> internalPackets = ConcurrentHashMap.newKeySet();
    private int teleportId = 0;
    private int antiKickTicks = 0;
    private Vec3 currentMovement = Vec3.ZERO;

    @Override
    public void onEnable() {
        teleportId = 0;
        antiKickTicks = 0;
        currentMovement = Vec3.ZERO;
        internalPackets.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public void onDisable() {
        internalPackets.clear();
        currentMovement = Vec3.ZERO;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.noPhysics = false;
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.getConnection() == null) {
            return;
        }

        LocalPlayer player = mc.player;
        if (phase.get()) {
            player.noPhysics = true;
        }

        boolean movingHorizontally = MovementUtil.isMoving();
        boolean up = mc.options.keyJump.isDown();
        boolean down = mc.options.keyShift.isDown();
        boolean movingVertically = up || down;
        boolean moving = movingHorizontally || movingVertically;

        antiKickTicks++;

        double currentSpeed = speed.get();
        double motionX = 0.0;
        double motionZ = 0.0;
        double motionY = 0.0;

        if (movingHorizontally) {
            double[] forward = MovementUtil.forward(currentSpeed * 0.2873);
            motionX = forward[0];
            motionZ = forward[1];
        }

        if (up && !down) {
            motionY = currentSpeed * 0.0624;
        } else if (down && !up) {
            motionY = -currentSpeed * 0.0624;
        } else if (antiKick.get() && antiKickTicks >= 20 && !player.onGround()) {
            antiKickTicks = 0;
            motionY = -0.03130;
        }

        currentMovement = new Vec3(motionX, motionY, motionZ);

        if (!moving) {
            player.setDeltaMovement(Vec3.ZERO);
            if (antiKick.get() && antiKickTicks >= 20 && !player.onGround()) {
                antiKickTicks = 0;
                dispatchFlyPackets(player.position().add(0, -0.03130, 0), player.onGround());
            }
            return;
        }

        Vec3 targetPos = player.position().add(motionX, motionY, motionZ);
        switch (mode.get()) {
            case FACTOR -> {
                int factor = Math.max(1, (int) Math.round(currentSpeed));
                Vec3 step = new Vec3(motionX / factor, motionY / factor, motionZ / factor);
                Vec3 current = player.position();
                for (int i = 0; i < factor; i++) {
                    current = current.add(step);
                    dispatchFlyPackets(current, player.onGround());
                }
                player.setPos(targetPos.x, targetPos.y, targetPos.z);
            }
            case SETBACK -> {
                dispatchFlyPackets(targetPos, player.onGround());
                // In setback mode, wait for server position correction to advance
            }
            case FAST -> {
                dispatchFlyPackets(targetPos, player.onGround());
                player.setPos(targetPos.x, targetPos.y, targetPos.z);
            }
        }

        player.setDeltaMovement(Vec3.ZERO);
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (currentMovement.lengthSqr() > 1.0E-7) {
            event.setMovement(currentMovement);
        } else {
            event.setMovement(Vec3.ZERO);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled()) return;

        Packet<?> packet = event.getPacket();
        if (internalPackets.remove(packet)) {
            return;
        }

        if (packet instanceof ServerboundMovePlayerPacket move) {
            if (move.hasPosition()) {
                event.cancel();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.getConnection() == null) return;

        if (event.getPacket() instanceof ClientboundPlayerPositionPacket packet) {
            teleportId = packet.id();

            // Confirm teleport to keep client and server synchronized
            sendInternalPacket(new ServerboundAcceptTeleportationPacket(teleportId));

            Vec3 target = packet.change().position();
            if (mode.get() == PacketFlyMode.SETBACK) {
                mc.player.setPos(target.x, target.y, target.z);
            }

            // Acknowledge position with move packet
            sendInternalPacket(new ServerboundMovePlayerPacket.Pos(
                    target.x,
                    target.y,
                    target.z,
                    mc.player.onGround(),
                    mc.player.horizontalCollision
            ));

            // Prevent client snapping back
            event.cancel();
        }
    }

    @EventHandler
    private void onCollision(EventCollision event) {
        if (!isEnabled() || !phase.get()) return;
        event.setState(Blocks.AIR.defaultBlockState());
    }

    @EventHandler
    private void onPushOutOfBlocks(EventPushOutOfBlocks event) {
        if (!isEnabled() || !phase.get()) return;
        event.cancel();
    }

    private void dispatchFlyPackets(Vec3 targetPos, boolean onGround) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null || mc.player == null) return;

        // 1. Send target position packet
        ServerboundMovePlayerPacket.Pos targetPacket = new ServerboundMovePlayerPacket.Pos(
                targetPos.x,
                targetPos.y,
                targetPos.z,
                onGround,
                mc.player.horizontalCollision
        );
        sendInternalPacket(targetPacket);

        // 2. Send out-of-bounds position packet
        Vec3 boundsPos = getBoundsPos(targetPos);
        ServerboundMovePlayerPacket.Pos boundsPacket = new ServerboundMovePlayerPacket.Pos(
                boundsPos.x,
                boundsPos.y,
                boundsPos.z,
                onGround,
                mc.player.horizontalCollision
        );
        sendInternalPacket(boundsPacket);

        // 3. Confirm teleportation if we have an ID
        if (teleportId != 0) {
            sendInternalPacket(new ServerboundAcceptTeleportationPacket(teleportId));
        }
    }

    private Vec3 getBoundsPos(Vec3 pos) {
        return switch (bounds.get()) {
            case UP -> new Vec3(pos.x, pos.y + 1337.0, pos.z);
            case DOWN -> new Vec3(pos.x, pos.y - 1337.0, pos.z);
            case PRESERVE -> new Vec3(pos.x, pos.y, pos.z);
        };
    }

    private void sendInternalPacket(Packet<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return;

        internalPackets.add(packet);
        mc.getConnection().send(packet);
    }
}
