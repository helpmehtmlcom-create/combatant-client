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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "autofish",
        displayName = "AutoFish",
        aliases = {"FishBot", "FastFish"},
        category = ModuleCategory.PLAYER,
        description = "Automatically reels in and recasts your fishing rod when a fish bites."
)
public final class AutoFish extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> recastDelay =
            num("autoFishRecastDelay", "recast_delay", 15, 5, 40);

    private final BooleanValue autoCast =
            bool("autoFishAutoCast", "auto_cast", true);

    private int recastCountdown = -1;
    private int initialCastCountdown = 20;

    @Override
    public void onDisable() {
        recastCountdown = -1;
        initialCastCountdown = 20;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;

        InteractionHand rodHand = getRodHand(player);
        if (rodHand == null) return;

        if (recastCountdown > 0) {
            recastCountdown--;
            if (recastCountdown == 0) {
                recastCountdown = -1;
                mc.gameMode.useItem(player, rodHand);
            }
            return;
        }

        // Auto cast if not cast yet
        if (player.fishing == null && autoCast.get()) {
            if (initialCastCountdown > 0) {
                initialCastCountdown--;
                if (initialCastCountdown == 0) {
                    mc.gameMode.useItem(player, rodHand);
                }
            }
        } else {
            initialCastCountdown = 20;
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;

        FishingHook hook = player.fishing;
        if (hook == null) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundSoundPacket soundPacket) {
            if (soundPacket.getSound().value() == SoundEvents.FISHING_BOBBER_SPLASH) {
                Vec3 hookPos = hook.position();
                double dx = soundPacket.getX() - hookPos.x;
                double dy = soundPacket.getY() - hookPos.y;
                double dz = soundPacket.getZ() - hookPos.z;

                if (dx * dx + dy * dy + dz * dz <= 4.0) {
                    InteractionHand rodHand = getRodHand(player);
                    if (rodHand != null) {
                        mc.gameMode.useItem(player, rodHand);
                        recastCountdown = recastDelay.get();
                    }
                }
            }
        }
    }

    private InteractionHand getRodHand(LocalPlayer player) {
        if (player.getMainHandItem().is(Items.FISHING_ROD)) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().is(Items.FISHING_ROD)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }
}
