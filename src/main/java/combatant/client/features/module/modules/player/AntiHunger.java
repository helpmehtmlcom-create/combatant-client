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
import combatant.client.mixins.accessors.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

@ModuleInfo(
        id = "antihunger",
        displayName = "AntiHunger",
        category = ModuleCategory.PLAYER,
        description = "Reduces hunger exhaustion by canceling sprint packets and spoofing ground state.",
        aliases = {"nosaturate", "hungerless"}
)
public final class AntiHunger extends Module {

    private final BooleanValue cancelSprint = bool("cancelSprint", "cancel_sprint", true);
    private final BooleanValue spoofGround = bool("spoofGround", "spoof_ground", true);

    private final Minecraft mc = Minecraft.getInstance();

    public AntiHunger() {
    }

    public BooleanValue getCancelSprint() {
        return cancelSprint;
    }

    public BooleanValue getSpoofGround() {
        return spoofGround;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || event == null || mc.player == null) {
            return;
        }

        if (cancelSprint.get() && event.getPacket() instanceof ServerboundPlayerCommandPacket command) {
            if (command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                event.cancel();
            }
            return;
        }

        if (spoofGround.get() && event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            if (isSafeToSpoof(mc.player)) {
                ((ServerboundMovePlayerPacketAccessor) packet).combatant$setOnGround(false);
            }
        }
    }

    private boolean isSafeToSpoof(LocalPlayer player) {
        if (player.isSpectator() || player.isPassenger()) {
            return false;
        }
        if (player.getAbilities().flying || player.isFallFlying()) {
            return false;
        }
        if (player.onClimbable()) {
            return false;
        }
        if (isInWater(player)) {
            return false;
        }
        if (isJumping(player) || (player.getDeltaMovement().y > 0.0 && !player.onGround())) {
            return false;
        }
        if (player.fallDistance > 0.0f || (!player.onGround() && player.getDeltaMovement().y < 0.0)) {
            return false;
        }
        return true;
    }

    private boolean isInWater(LocalPlayer player) {
        return player.isInWater() || player.isUnderWater() || player.isInLava();
    }

    private boolean isJumping(LocalPlayer player) {
        if (mc.options != null && mc.options.keyJump.isDown()) {
            return true;
        }
        return player.input != null && player.input.keyPresses != null && player.input.keyPresses.jump();
    }
}
