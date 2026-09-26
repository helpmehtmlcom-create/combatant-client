/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

@ModuleInfo(
        id = "autokit",
        displayName = "AutoKit",
        aliases = {"KitAuto", "AutoLoadKit"},
        category = ModuleCategory.PLAYER,
        description = "Automatically sends kit commands upon respawn or joining a world."
)
public final class AutoKit extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final StringValue command =
            text("autoKitCommand", "command", "/kit pvp");

    private final NumberValue<Integer> delayTicks =
            num("autoKitDelayTicks", "delay_ticks", 10, 0, 60);

    private final BooleanValue onRespawn =
            bool("autoKitOnRespawn", "on_respawn", true);

    private final BooleanValue onJoin =
            bool("autoKitOnJoin", "on_join", true);

    private boolean wasDead = false;
    private int queuedTicks = -1;

    @Override
    public void onEnable() {
        wasDead = false;
        if (onJoin.get()) {
            queuedTicks = delayTicks.get();
        } else {
            queuedTicks = -1;
        }
    }

    @Override
    public void onDisable() {
        wasDead = false;
        queuedTicks = -1;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) {
            wasDead = false;
            queuedTicks = -1;
            return;
        }

        if (player.isDeadOrDying()) {
            wasDead = true;
            return;
        }

        if (wasDead) {
            wasDead = false;
            if (onRespawn.get()) {
                queuedTicks = delayTicks.get();
            }
        }

        if (queuedTicks > 0) {
            queuedTicks--;
            return;
        }

        if (queuedTicks == 0) {
            queuedTicks = -1;
            sendKitCommand();
        }
    }

    private void sendKitCommand() {
        if (mc.getConnection() == null) return;

        String cmd = command.get();
        if (cmd == null || cmd.isBlank()) return;

        cmd = cmd.trim();
        if (cmd.startsWith("/")) {
            mc.getConnection().sendCommand(cmd.substring(1));
        } else {
            mc.getConnection().sendChat(cmd);
        }
    }
}
