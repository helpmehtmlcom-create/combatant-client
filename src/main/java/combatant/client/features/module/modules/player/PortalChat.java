/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.mixins.accessors.EntityAccessor;
import combatant.client.mixins.accessors.LocalPlayerAccessor;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Blocks;

/**
 * Prevents nether portals from closing open chat or inventory screens and allows typing/interacting while inside.
 */
@ModuleInfo(
        id = "portalchat",
        displayName = "PortalChat",
        category = ModuleCategory.PLAYER,
        description = "Allows opening and interacting with chat and inventory screens while inside nether portals."
)
public final class PortalChat extends Module {

    private final BooleanValue allowChat = bool("allowChat", "allow_chat", true);
    private final BooleanValue allowInventory = bool("allowInventory", "allow_inventory", true);

    private final Minecraft mc = Minecraft.getInstance();
    private Screen cachedScreen = null;

    public PortalChat() {
    }

    public BooleanValue getAllowChat() {
        return allowChat;
    }

    public BooleanValue getAllowInventory() {
        return allowInventory;
    }

    public boolean isChatAllowed() {
        return isEnabled() && allowChat.get();
    }

    public boolean isInventoryAllowed() {
        return isEnabled() && allowInventory.get();
    }

    /**
     * Checks if the given screen is allowed to remain open inside a portal.
     *
     * @param screen the screen to evaluate
     * @return true if the screen should be retained
     */
    public boolean shouldRetainScreen(Screen screen) {
        if (!isEnabled() || screen == null) {
            return false;
        }

        if (screen instanceof ChatScreen) {
            return allowChat.get();
        }

        if (screen instanceof InventoryScreen || screen instanceof AbstractContainerScreen<?>) {
            return allowInventory.get();
        }

        return false;
    }

    /**
     * Static hook for mixins and client systems to query whether a screen is permitted in portals.
     *
     * @param screen the screen to query
     * @return true if PortalChat permits this screen inside a portal
     */
    public static boolean shouldAllowInPortal(Screen screen) {
        PortalChat module = Modules.get(PortalChat.class);
        return module != null && module.shouldRetainScreen(screen);
    }

    /**
     * Checks if the local player is currently inside a portal or has an active portal processor.
     *
     * @return true if the player is in or processing a portal
     */
    public boolean isInPortal() {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        if (isInPortalBlock()) {
            return true;
        }

        if (mc.player instanceof EntityAccessor entityAccessor) {
            PortalProcessor portalProcessor = entityAccessor.combatant$getPortalManager();
            if (portalProcessor != null && portalProcessor.isInsidePortalThisTick()) {
                return true;
            }
        }

        return mc.player.portalProcess != null;
    }

    /**
     * Checks if the player's bounding position intersects nether portal blocks.
     */
    public boolean isInPortalBlock() {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        BlockPos pos = mc.player.blockPosition();
        if (mc.level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
            return true;
        }
        return mc.level.getBlockState(pos.above()).is(Blocks.NETHER_PORTAL);
    }

    @Override
    public void onTick() {
        handleTick();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        handleTick();
    }

    private void handleTick() {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            cachedScreen = null;
            return;
        }

        Screen currentScreen = ClientScreen.current(mc);
        if (currentScreen != null && shouldRetainScreen(currentScreen)) {
            cachedScreen = currentScreen;
        }

        if (isInPortal()) {
            // Nullify nausea intensity to keep the GUI legible and responsive
            if (mc.player instanceof LocalPlayerAccessor playerAccessor) {
                playerAccessor.combatant$setNauseaIntensity(0.0f);
                playerAccessor.combatant$setLastNauseaIntensity(0.0f);
            }

            // Restore retained screen if vanilla portal handling closed it unexpectedly
            if (currentScreen == null && cachedScreen != null && shouldRetainScreen(cachedScreen)) {
                ClientScreen.show(mc, cachedScreen);
            }
        } else {
            cachedScreen = null;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || event == null || mc.player == null) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundContainerClosePacket && isInventoryAllowed() && isInPortal()) {
            Screen current = ClientScreen.current(mc);
            if (current instanceof InventoryScreen || current instanceof AbstractContainerScreen<?>) {
                event.cancel();
            }
        }
    }

    @Override
    public void onDisable() {
        cachedScreen = null;
    }
}
