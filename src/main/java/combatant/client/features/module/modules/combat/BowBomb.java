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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.projectile.ProjectilePredictionUtil;
import combatant.client.util.target.TargetingUtil;

@ModuleInfo(
        id = "bowbomb",
        displayName = "BowBomb",
        category = ModuleCategory.COMBAT,
        description = "Exploits bow release packets to dramatically boost projectile velocity with automatic teammate protection."
)
public final class BowBomb extends Module {

    public enum Mode {
        BYPASS,
        DIRECT
    }

    private final EnumValue<Mode> mode = enumMode("mode", Mode.BYPASS, Mode.values());
    private final NumberValue<Integer> strength = num("strength", 50, 10, 200);

    private final Minecraft mc = Minecraft.getInstance();
    private boolean shooting = false;

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.getConnection() == null) return;

        if (event.getPacket() instanceof ServerboundPlayerActionPacket action) {
            if (action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                ItemStack using = mc.player.getUseItem();
                if (using.getItem() instanceof BowItem || using.getItem() instanceof CrossbowItem) {
                    if (isAimedAtTeammate(using)) {
                        return;
                    }

                    if (!shooting) {
                        shooting = true;
                        LocalPlayer p = mc.player;
                        double x = p.getX();
                        double y = p.getY();
                        double z = p.getZ();
                        int count = strength.get();
                        boolean isBypass = mode.get() == Mode.BYPASS;

                        for (int i = 0; i < count; i++) {
                            if (isBypass) {
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

    /**
     * Automatic safety check to prevent shooting teammates or friends.
     */
    private boolean isAimedAtTeammate(ItemStack stack) {
        if (mc.player == null) return false;

        // Check direct crosshair entity
        if (mc.crosshairPickEntity instanceof LivingEntity living && isTeammate(living)) {
            return true;
        }

        // Check hypothetical projectile hit
        Rotation rotation = new Rotation(mc.player.getYRot(), mc.player.getXRot());
        LivingEntity predictedHit = ProjectilePredictionUtil.getHypotheticalHit(
                mc.player,
                stack,
                rotation,
                entity -> true
        );
        return predictedHit != null && isTeammate(predictedHit);
    }

    private boolean isTeammate(LivingEntity entity) {
        if (entity == null || mc.player == null) {
            return false;
        }
        if (entity == mc.player) {
            return true;
        }
        if (mc.player.isAlliedTo(entity)) {
            return true;
        }
        return !TargetingUtil.isValidCombatTarget(entity);
    }
}
