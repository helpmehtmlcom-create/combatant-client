/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "bowspam",
        displayName = "BowSpam",
        aliases = {"FastBow", "RapidBow"},
        category = ModuleCategory.COMBAT,
        description = "Automatically and rapidly releases bows at a configurable draw charge."
)
public final class BowSpam extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> chargeTicks =
            num("bowSpamChargeTicks", "charge_ticks", 4, 2, 20);

    private final BooleanValue autoRelease =
            bool("bowSpamAutoRelease", "auto_release", true);

    private boolean wasHoldingUse = false;

    @Override
    public void onDisable() {
        wasHoldingUse = false;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;

        if (player.isUsingItem() && player.getUseItem().is(Items.BOW)) {
            int ticksUsing = player.getTicksUsingItem();

            if (ticksUsing >= chargeTicks.get()) {
                InteractionHand hand = player.getUsedItemHand();
                mc.gameMode.releaseUsingItem(player);
                player.releaseUsingItem();

                if (autoRelease.get() && mc.options.keyUse.isDown()) {
                    mc.gameMode.useItem(player, hand);
                }
            }
        }
    }
}
