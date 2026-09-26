/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "anchorfly",
        displayName = "AnchorFly",
        aliases = {"AnchorFlight", "AnchorBoost"},
        category = ModuleCategory.MOVEMENT,
        description = "Propels you into the air and gives flight bursts using Respawn Anchor interactions."
)
public final class AnchorFly extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> boost =
            num("anchorFlyBoost", "boost", 1.2f, 0.5f, 5.0f);

    private final NumberValue<Float> hBoost =
            num("anchorFlyHBoost", "horizontal_boost", 1.1f, 1.0f, 3.0f);

    private final BooleanValue autoJump =
            bool("anchorFlyAutoJump", "auto_jump", true);

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundUseItemOnPacket usePacket) {
            BlockHitResult hit = usePacket.getHitResult();
            if (mc.level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
                Vec3 v = player.getDeltaMovement();
                double my = autoJump.get() ? Math.max(v.y, boost.get()) : v.y + boost.get() * 0.5;
                player.setDeltaMovement(v.x * hBoost.get(), my, v.z * hBoost.get());
            }
        }
    }
}
