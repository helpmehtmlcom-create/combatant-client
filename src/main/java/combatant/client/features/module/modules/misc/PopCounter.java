/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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

    private final Map<UUID, Integer> counts = new HashMap<>();
    private ClientLevel level;
    private int selfPops;

    @Override
    public void onEnable() {
        clearState();
    }

    @Override
    public void onDisable() {
        clearState();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.level == null) {
            clearState();
            return;
        }
        if (level != mc.level) {
            counts.clear();
            selfPops = 0;
            level = mc.level;
        }
    }

    @EventHandler
    private void onPacketReceivePost(PacketEvent.ReceivePost event) {
        if (!isEnabled() || mc.level == null) return;
        if (!(event.getPacket() instanceof ClientboundEntityEventPacket packet)) return;

        Entity entity = packet.getEntity(mc.level);
        if (!(entity instanceof Player player)) return;

        byte id = packet.getEventId();
        if (id == EntityEvent.PROTECTED_FROM_DEATH) {
            onPop(player);
        } else if (id == EntityEvent.DEATH) {
            onDeath(player);
        }
    }

    private void onPop(Player player) {
        boolean own = player == mc.player;
        if (own) {
            selfPops++;
            if (!self.get()) return;
            Notifier.update("popcounter:self", I18n.get("notification.popcounter.self_pop", selfPops), Notifier.Type.WARNING);
            return;
        }
        if (!allows(player)) return;

        int count = counts.merge(player.getUUID(), 1, Integer::sum);
        Notifier.update(
                "popcounter:" + player.getUUID(),
                I18n.get("notification.popcounter.pop", player.getName().getString(), count),
                Notifier.Type.INFO
        );
    }

    private void onDeath(Player player) {
        boolean own = player == mc.player;
        int count = own ? selfPops : counts.getOrDefault(player.getUUID(), 0);

        if (deathSummary.get() && count > 0 && (own ? self.get() : allows(player))) {
            String key = own ? "notification.popcounter.self_death" : "notification.popcounter.death";
            String text = own
                    ? I18n.get(key, count)
                    : I18n.get(key, player.getName().getString(), count);
            Notifier.info(text);
        }

        if (own) {
            selfPops = 0;
            Notifier.clear("popcounter:self");
        } else {
            counts.remove(player.getUUID());
            Notifier.clear("popcounter:" + player.getUUID());
        }
    }

    private void clearState() {
        counts.clear();
        selfPops = 0;
        level = null;
    }

    private boolean allows(Player player) {
        if (player == null) return false;
        CategoryType type = CategoryRules.determine(player.getGameProfile().name());
        return switch (type) {
            case FRIEND, BEDWARS_SELF -> targets.get("friends");
            case ENEMY, BEDWARS_ENEMY -> targets.get("enemies");
            case STAFF -> targets.get("staff");
            case DEFAULT -> targets.get("others");
        };
    }
}
