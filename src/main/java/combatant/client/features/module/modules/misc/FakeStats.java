/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/**
 * Fakes scoreboard stats (balance, shards, kills, deaths) on DonutSMP and intercepts /bal.
 * Ported and adapted from 67Client's FakeStatsModule.
 */
@ModuleInfo(
        id = "fakestats",
        displayName = "FakeStats",
        description = "Fakes scoreboard balance, shards, and stats on DonutSMP; intercepts /bal command.",
        category = ModuleCategory.MISC,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"fakebal", "fakescoreboard", "fakemoney", "fakeshard"}
)
public class FakeStats extends Module {

    private final StringValue money =
            text("fakestats_money", "money", "12.5M");
    private final StringValue shards =
            text("fakestats_shards", "shards", "2,500");
    private final StringValue kills =
            text("fakestats_kills", "kills", "1,337");
    private final StringValue deaths =
            text("fakestats_deaths", "deaths", "42");
    private final BooleanValue balanceCommand =
            bool("fakestats_balance_cmd", "balance_cmd", true);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!balanceCommand.get() || mc.player == null) return;

        if (event.getPacket() instanceof ServerboundChatCommandPacket packet) {
            String cmd = packet.command().trim().toLowerCase(Locale.ROOT);
            if (cmd.equals("bal") || cmd.equals("balance") || cmd.equals("money")) {
                event.cancel();
                if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                    mc.gui.hud.getChat().addClientSystemMessage(
                            Component.literal("§6[Balance] §7Your current balance: §a$" + money.get())
                    );
                }
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
    }

    public String getMoney() {
        return money.get();
    }

    public String getShards() {
        return shards.get();
    }

    public String getKills() {
        return kills.get();
    }

    public String getDeaths() {
        return deaths.get();
    }
}
