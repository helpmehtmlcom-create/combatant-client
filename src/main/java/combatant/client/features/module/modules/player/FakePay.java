/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

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

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Fakes /pay confirmation messages and sounds for recordings and clips on DonutSMP.
 * Intercepts outgoing /pay packets so real money is never sent.
 * Ported from 67Client's FakePayModule.
 */
@ModuleInfo(
        id = "fakepay",
        displayName = "FakePay",
        description = "Fakes /pay command confirmations for clips without actually spending in-game currency.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"fakemoney", "fakecoin", "payfake"}
)
public class FakePay extends Module {

    private static final DecimalFormat COMMA_FORMAT = new DecimalFormat("#,##0.##");

    private final StringValue currency =
            text("fakepay_currency", "currency", "$");
    private final BooleanValue sounds =
            bool("fakepay_sounds", "sounds", true);
    private final BooleanValue selfGuard =
            bool("fakepay_self_guard", "self_guard", true);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || !(event.getPacket() instanceof ServerboundChatCommandPacket packet)) return;

        String raw = packet.command().trim();
        String[] parts = raw.split("\\s+");
        if (parts.length == 0) return;

        String name = parts[0].toLowerCase(Locale.ROOT);
        if (!name.equals("pay")) return;

        // Hijack the command packet
        event.cancel();

        if (parts.length < 3) {
            msg("§cUsage: /pay <player> <amount>");
            return;
        }

        String target = parts[1];
        if (selfGuard.get() && target.equalsIgnoreCase(mc.player.getGameProfile().name())) {
            msg("§cYou cannot pay yourself!");
            return;
        }

        double amount = parseAmount(parts[2]);
        if (Double.isNaN(amount) || amount <= 0.0) {
            msg("§cInvalid amount: " + parts[2]);
            return;
        }

        String formattedAmount = COMMA_FORMAT.format(amount);
        String symbol = currency.get();

        // Print DonutSMP-accurate payment message
        msg("§a[Payment] §7You sent §e" + symbol + formattedAmount + " §7to §f" + target + "§7.");

        if (sounds.get()) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    private void msg(String text) {
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(text));
        }
    }

    public static double parseAmount(String s) {
        if (s == null || s.isBlank()) return Double.NaN;
        s = s.trim().toLowerCase(Locale.ROOT);
        double multiplier = 1.0;

        if (s.endsWith("k")) {
            multiplier = 1_000.0;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            multiplier = 1_000_000.0;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("t")) {
            multiplier = 1_000_000_000_000.0;
            s = s.substring(0, s.length() - 1);
        }

        try {
            return Double.parseDouble(s) * multiplier;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
