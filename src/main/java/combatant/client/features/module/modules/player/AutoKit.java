/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.Notifier;

@ModuleInfo(
        id = "autokit",
        displayName = "AutoKit",
        aliases = {"kitequip", "autokitequip"},
        description = "Automatically requests and equips saved or command-based PvP kits upon respawning or joining.",
        category = ModuleCategory.PLAYER
)
public class AutoKit extends Module {

    private static final String ACTION_EQUIP = "equip";

    private final StringValue kitName =
            text("autokit_kit_name", "kit_name", "pvp");
    private final StringValue commandFormat =
            text("autokit_command", "command_format", "/kit {kit}");
    private final EnumValue<Trigger> trigger =
            enumSetting("autokit_trigger", "trigger", Trigger.ON_RESPAWN, Trigger.values());
    private final NumberValue<Integer> delay =
            num("autokit_delay", "delay", 10, 0, 100);
    private final BooleanValue autoSort =
            bool("autokit_auto_sort", "auto_sort", true);
    private final BooleanValue autoArmor =
            bool("autokit_auto_armor", "auto_armor", true);
    private final BooleanValue chatNotice =
            bool("autokit_chat_notice", "chat_notice", true);

    private final Minecraft mc = Minecraft.getInstance();
    private boolean wasDead = false;
    private int pendingTicks = -1;
    private int postSortDelay = -1;

    public AutoKit() {
        action(ACTION_EQUIP, "K");
    }

    @Override
    public void onEnable() {
        wasDead = false;
        pendingTicks = -1;
        postSortDelay = -1;
        if (trigger.get() == Trigger.ON_ENABLE && mc.player != null) {
            scheduleEquip();
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null) {
            return;
        }

        if (isActionPressedOnce(ACTION_EQUIP)) {
            equipKit();
            return;
        }

        boolean isDead = !mc.player.isAlive() || mc.player.isDeadOrDying() || mc.player.getHealth() <= 0.0f;

        if (isDead) {
            wasDead = true;
        } else if (wasDead) {
            wasDead = false;
            if (trigger.get() == Trigger.ON_RESPAWN) {
                scheduleEquip();
            }
        }

        if (pendingTicks > 0) {
            pendingTicks--;
            if (pendingTicks == 0) {
                pendingTicks = -1;
                executeKitCommand();
            }
        }

        if (postSortDelay > 0) {
            postSortDelay--;
            if (postSortDelay == 0) {
                postSortDelay = -1;
                triggerPostActions();
            }
        }
    }

    public void scheduleEquip() {
        int delayTicks = delay.get();
        if (delayTicks <= 0) {
            executeKitCommand();
        } else {
            pendingTicks = delayTicks;
        }
    }

    public void equipKit() {
        executeKitCommand();
    }

    private void executeKitCommand() {
        if (mc.player == null || mc.player.connection == null) {
            return;
        }

        String kit = kitName.get().trim();
        if (kit.isEmpty()) {
            return;
        }

        String cmd = commandFormat.get().replace("{kit}", kit).trim();
        if (cmd.startsWith("/")) {
            mc.player.connection.sendCommand(cmd.substring(1));
        } else {
            mc.player.connection.sendChat(cmd);
        }

        if (chatNotice.get()) {
            String msg = "[AutoKit] Requested kit: " + kit;
            if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
            }
            Notifier.info(msg);
        }

        if (autoSort.get() || autoArmor.get()) {
            postSortDelay = 15; // 15 ticks after command to allow server items to arrive
        }
    }

    private void triggerPostActions() {
        if (autoArmor.get()) {
            AutoArmor armorModule = Modules.get(AutoArmor.class);
            if (armorModule != null && armorModule.isEnabled()) {
                armorModule.onTick();
            }
        }

        if (autoSort.get()) {
            InventorySorter sorter = Modules.get(InventorySorter.class);
            if (sorter != null && sorter.isEnabled()) {
                sorter.sort();
            }
        }
    }

    public String getActiveKitName() {
        return kitName.get();
    }

    public void setActiveKitName(String name) {
        kitName.set(name);
    }

    public enum Trigger {
        ON_RESPAWN,
        ON_ENABLE,
        MANUAL
    }
}
