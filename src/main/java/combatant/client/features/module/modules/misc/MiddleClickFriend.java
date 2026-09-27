/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(
        id = "middleclickfriend",
        displayName = "MiddleClickFriend",
        aliases = {"MCF", "MiddleClick"},
        category = ModuleCategory.MISC,
        description = "Middle-click a player to add or remove them from friends"
)
public class MiddleClickFriend extends Module {

    private final Minecraft mc = Minecraft.getInstance();
    private boolean wasPressed = false;

    public MiddleClickFriend() {
        super();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (ClientScreen.current(mc) != null) {
            wasPressed = false;
            return;
        }

        long windowHandle = mc.getWindow().handle();
        boolean isPressed = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

        if (isPressed && !wasPressed) {
            handleMiddleClick();
        }

        wasPressed = isPressed;
    }

    private void handleMiddleClick() {
        LocalPlayer self = mc.player;
        if (self == null || mc.crosshairPickEntity == null) return;

        if (mc.crosshairPickEntity instanceof Player player) {
            if (player == self) return;

            String name = player.getGameProfile().name();
            PlayerRelations relations = PlayerRelations.get();

            if (relations.isFriend(name)) {
                relations.removeFriend(name);
                CommandOutput.send("Removed " + name + " from friends.");
            } else {
                relations.addFriend(name);
                CommandOutput.success("Added " + name + " to friends.");
            }

            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f));
        }
    }
}
