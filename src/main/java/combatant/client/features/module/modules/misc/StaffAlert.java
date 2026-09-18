/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Advanced DonutSMP staff detector and alert system.
 * Directly ported and enhanced from 67Client's StaffDetector/StaffTracker architecture.
 */
@ModuleInfo(
        id = "staffalert",
        displayName = "StaffAlert",
        description = "Monitors DonutSMP staff, admins, unicode star ranks, and spectator players to prevent bans.",
        category = ModuleCategory.MISC,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"antistaff", "staffdetector", "adminradar", "stafftracker"}
)
public class StaffAlert extends Module {

    public static final String DEFAULT_SYMBOLS = "★☆✦✧✪✩✫✬✭✮✯⭐✰❂⚝✴✵✶✷✸✹⍟";

    private static final List<String> DEFAULT_RANK_KEYWORDS = List.of(
            "coowner", "owner", "manager", "administrator", "admin", "developer", "dev",
            "srmod", "seniormod", "moderator", "mod", "srhelper", "seniorhelper", "helper",
            "trialmod", "trial", "builder", "support", "staff", "curator"
    );

    private static final Map<String, String> RANK_LABELS = Map.ofEntries(
            Map.entry("coowner", "Co-Owner"),
            Map.entry("owner", "Owner"),
            Map.entry("manager", "Manager"),
            Map.entry("administrator", "Admin"),
            Map.entry("admin", "Admin"),
            Map.entry("developer", "Dev"),
            Map.entry("dev", "Dev"),
            Map.entry("srmod", "Sr.Mod"),
            Map.entry("seniormod", "Sr.Mod"),
            Map.entry("moderator", "Mod"),
            Map.entry("mod", "Mod"),
            Map.entry("srhelper", "Sr.Helper"),
            Map.entry("seniorhelper", "Sr.Helper"),
            Map.entry("helper", "Helper"),
            Map.entry("trialmod", "Trial"),
            Map.entry("trial", "Trial"),
            Map.entry("builder", "Builder"),
            Map.entry("support", "Support"),
            Map.entry("staff", "Staff"),
            Map.entry("curator", "Curator")
    );

    private final BooleanValue chatAlert =
            bool("staffalert_chat", "chat_alert", true);
    private final BooleanValue soundAlert =
            bool("staffalert_sound", "sound_alert", true);
    private final BooleanValue checkStars =
            bool("staffalert_stars", "star_markers", true);
    private final BooleanValue checkFontIcons =
            bool("staffalert_font_icons", "font_icons", true);
    private final BooleanValue checkSpectators =
            bool("staffalert_spectators", "spectators", true);
    private final BooleanValue autoLeave =
            bool("staffalert_auto_leave", "auto_leave", false);

    private final Minecraft mc = Minecraft.getInstance();
    private final Set<String> alertedStaff = new HashSet<>();

    @Override
    public void onEnable() {
        alertedStaff.clear();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (mc.getConnection() == null || mc.player == null) return;
        if (mc.player.tickCount % 20 != 0) return;

        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info == null || info.getProfile() == null) continue;
            String name = info.getProfile().name();
            if (name == null || name.equalsIgnoreCase(mc.player.getGameProfile().name())) continue;

            StaffResult result = classify(info);
            if (result != null && alertedStaff.add(name.toLowerCase(Locale.ROOT))) {
                triggerAlert(name, result);
                if (autoLeave.get()) {
                    disconnect(name, result.label());
                    setEnabled(false);
                    return;
                }
            }
        }
    }

    private void triggerAlert(String name, StaffResult result) {
        if (chatAlert.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            String badge = result.label().isEmpty() ? "Staff" : result.label();
            mc.gui.hud.getChat().addClientSystemMessage(
                    Component.literal(String.format("§c[StaffAlert] §eStaff Detected: §f%s §7[§c%s§7] §8(%s)",
                            name, badge, result.reason()))
            );
        }

        if (soundAlert.get() && mc.player != null) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
        }
    }

    private void disconnect(String name, String badge) {
        if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
            mc.getConnection().getConnection().disconnect(
                    Component.literal("[StaffAlert] Auto-leave: Staff member detected (" + name + " - " + badge + ")")
            );
        }
    }

    private StaffResult classify(PlayerInfo info) {
        String name = info.getProfile().name();
        boolean isSpectator = checkSpectators.get() && info.getGameMode() == GameType.SPECTATOR;
        if (isSpectator) {
            return new StaffResult(name, "Spectator", "Spectator Mode");
        }

        Component display = info.getTabListDisplayName();
        PlayerTeam team = info.getTeam();
        Component prefix = team != null ? team.getPlayerPrefix() : null;
        Component suffix = team != null ? team.getPlayerSuffix() : null;
        String teamName = team != null ? team.getName() : "";
        // Check unicode star symbols
        if (checkStars.get()) {
            if (hasSymbol(display) || hasSymbol(prefix) || hasSymbol(suffix)) {
                return new StaffResult(name, "Staff Star", "Unicode Star Tag");
            }
        }

        // Check custom font icons (\uE000-\uF8FF) used by DonutSMP rank icons
        if (checkFontIcons.get()) {
            if (hasFontIcon(display) || hasFontIcon(prefix) || hasFontIcon(suffix)) {
                return new StaffResult(name, "Custom Rank", "DonutSMP Font Icon");
            }
        }

        // Check keywords in display, team prefix, suffix, and team name
        String combined = plain(display) + " " + plain(prefix) + " " + plain(suffix) + " " + teamName;
        String cleaned = stripName(combined, name).toLowerCase(Locale.ROOT);

        for (String kw : DEFAULT_RANK_KEYWORDS) {
            if (cleaned.contains(kw)) {
                String label = RANK_LABELS.getOrDefault(kw, Character.toUpperCase(kw.charAt(0)) + kw.substring(1));
                return new StaffResult(name, label, "Keyword '" + kw + "'");
            }
        }

        // Also check raw name for prominent roles
        String lowerName = name.toLowerCase(Locale.ROOT);
        for (String kw : DEFAULT_RANK_KEYWORDS) {
            if (lowerName.contains(kw)) {
                String label = RANK_LABELS.getOrDefault(kw, Character.toUpperCase(kw.charAt(0)) + kw.substring(1));
                return new StaffResult(name, label, "Username Keyword");
            }
        }

        return null;
    }

    private boolean hasSymbol(Component c) {
        if (c == null) return false;
        String text = c.getString();
        for (int i = 0; i < text.length(); i++) {
            int cp = text.codePointAt(i);
            if (DEFAULT_SYMBOLS.indexOf(cp) >= 0) return true;
        }
        return false;
    }

    private boolean hasFontIcon(Component c) {
        if (c == null) return false;
        String text = c.getString();
        for (int i = 0; i < text.length(); i++) {
            int cp = text.codePointAt(i);
            if ((cp >= 0xE000 && cp <= 0xF8FF) || (cp >= 0xF0000 && cp <= 0xFFFFD) || (cp >= 0x100000 && cp <= 0x10FFFD)) {
                return true;
            }
        }
        return false;
    }

    private static String plain(Component c) {
        return c == null ? "" : c.getString();
    }

    private static String stripName(String text, String name) {
        return name != null && !name.isEmpty() ? text.replaceAll("(?i)" + Pattern.quote(name), " ") : text;
    }

    private record StaffResult(String name, String label, String reason) {}
}
