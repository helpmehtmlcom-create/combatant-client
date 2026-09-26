/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

@ModuleInfo(
        id = "packetfly",
        displayName = "PacketFly",
        aliases = {"PhaseFly", "PacketFlight"},
        category = ModuleCategory.MOVEMENT,
        description = "Allows flight and phasing using position packet manipulation."
)
public final class PacketFly extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> speed =
            num("packetFlySpeed", "speed", 0.5f, 0.1f, 3.0f);

    private final NumberValue<Float> upSpeed =
            num("packetFlyUpSpeed", "up_speed", 0.5f, 0.1f, 2.0f);

    private final NumberValue<Float> downSpeed =
            num("packetFlyDownSpeed", "down_speed", 0.5f, 0.1f, 2.0f);

    private final NumberValue<Float> boundsOffset =
            num("packetFlyBoundsOffset", "bounds_offset", 1337.0f, 100.0f, 10000.0f);

    private final EnumValue<BoundsMode> boundsMode =
            enumSetting("packetFlyBoundsMode", "bounds_mode", BoundsMode.DOWN, BoundsMode.values());

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) return;

        player.setDeltaMovement(0, 0, 0);

        float yaw = player.getYRot();
        double rad = Math.toRadians(yaw);
        double sin = -Math.sin(rad);
        double cos = Math.cos(rad);

        double forward = 0.0;
        double strafe = 0.0;

        if (mc.options.keyUp.isDown()) forward += 1.0;
        if (mc.options.keyDown.isDown()) forward -= 1.0;
        if (mc.options.keyLeft.isDown()) strafe -= 1.0;
        if (mc.options.keyRight.isDown()) strafe += 1.0;

        double moveSpeed = speed.get();
        double mx = (forward * sin + strafe * cos) * moveSpeed;
        double mz = (forward * cos - strafe * sin) * moveSpeed;
        double my = 0.0;

        if (mc.options.keyJump.isDown()) {
            my = upSpeed.get();
        } else if (mc.options.keySprint.isDown()) {
            my = -downSpeed.get();
        }

        if (forward == 0.0 && strafe == 0.0 && my == 0.0) {
            return;
        }

        double targetX = player.getX() + mx;
        double targetY = player.getY() + my;
        double targetZ = player.getZ() + mz;

        // 1. Send target position packet
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                targetX, targetY, targetZ, player.onGround(), false
        ));

        // 2. Send out-of-bounds packet
        double boundY = switch (boundsMode.get()) {
            case DOWN -> targetY - boundsOffset.get();
            case UP -> targetY + boundsOffset.get();
            case PRESERVE -> targetY;
        };

        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                targetX, boundY, targetZ, player.onGround(), false
        ));

        // 3. Update client position
        player.setPos(targetX, targetY, targetZ);
    }

    public enum BoundsMode {
        DOWN,
        UP,
        PRESERVE
    }
}
