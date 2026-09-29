/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.util.pvp.opponents.TotemPopCounter;
import combatant.client.util.pvp.opponents.TotemPopSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.UUID;

@ModuleInfo(
        id = "popcounter",
        displayName = "PopCounter",
        aliases = {"TotemPops"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.popcounter.description")
public final class PopCounter extends Module {
    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue self = bool("popCounterSelf", "self", true);
    private final BooleanValue deathSummary = bool("popCounterDeathSummary", "death_summary", true);
    private final BooleanMapValue targets = group("popCounterTargets", "targets", new LinkedHashMap<>() {{
        put("friends", true);
        put("enemies", true);
        put("staff", true);
        put("others", true);
    }});

    private final TotemPopCounter.Listener listener = new TotemPopCounter.Listener() {
        @Override
        public void onPop(TotemPopSnapshot snapshot) {
            if (!isEnabled() || snapshot == null || snapshot.playerId() == null) return;
            Minecraft client = Minecraft.getInstance();
            if (client == null) return;
            client.execute(() -> emitPop(snapshot));
        }

        @Override
        public void onReset(UUID playerId, TotemPopSnapshot snapshot, TotemPopCounter.ResetReason reason) {
            if (!isEnabled() || reason != TotemPopCounter.ResetReason.DEATH || snapshot == null || snapshot.count() <= 0) return;
            Minecraft client = Minecraft.getInstance();
            if (client == null) return;
            client.execute(() -> emitDeath(snapshot));
        }
    };

    @Override
    public void onEnable() {
        TotemPopCounter.addListener(listener);
    }

    @Override
    public void onDisable() {
        TotemPopCounter.removeListener(listener);
    }

    private void emitPop(TotemPopSnapshot snapshot) {
        boolean own = mc.player != null && snapshot.playerId().equals(mc.player.getUUID());
        if (own) {
            if (!self.get()) return;
            CommandOutput.warning(I18n.get("notification.popcounter.self_pop", snapshot.count()));
            return;
        }

        Player player = mc.level != null ? mc.level.getPlayerByUUID(snapshot.playerId()) : null;
        String name = snapshot.name();
        if (player != null) {
            if (!allows(player)) return;
            name = player.getName().getString();
        } else if (!allowsName(name)) {
            return;
        }
        if (name == null || name.isBlank()) name = snapshot.playerId().toString();
        CommandOutput.send(I18n.get("notification.popcounter.pop", name, snapshot.count()));
    }

    private void emitDeath(TotemPopSnapshot snapshot) {
        if (!deathSummary.get()) return;
        boolean own = mc.player != null && snapshot.playerId().equals(mc.player.getUUID());
        if (own) {
            if (self.get()) CommandOutput.send(I18n.get("notification.popcounter.self_death", snapshot.count()));
            return;
        }

        Player player = mc.level != null ? mc.level.getPlayerByUUID(snapshot.playerId()) : null;
        String name = snapshot.name();
        if (player != null) {
            if (!allows(player)) return;
            name = player.getName().getString();
        } else if (!allowsName(name)) {
            return;
        }
        if (name == null || name.isBlank()) name = snapshot.playerId().toString();
        CommandOutput.send(I18n.get("notification.popcounter.death", name, snapshot.count()));
    }

    private boolean allows(Player player) {
        return player != null && allowsType(CategoryRules.determine(player.getGameProfile().name()));
    }

    private boolean allowsName(String name) {
        return name != null && !name.isBlank() && allowsType(CategoryRules.determine(name));
    }

    private boolean allowsType(CategoryType type) {
        return switch (type) {
            case FRIEND, BEDWARS_SELF -> targets.get("friends");
            case ENEMY, BEDWARS_ENEMY -> targets.get("enemies");
            case STAFF -> targets.get("staff");
            case DEFAULT -> targets.get("others");
        };
    }
}
