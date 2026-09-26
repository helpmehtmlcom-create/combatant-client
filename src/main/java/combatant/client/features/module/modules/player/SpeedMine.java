/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

@ModuleInfo(
        id = "speedmine",
        displayName = "SpeedMine",
        aliases = {"PacketMine", "FastMine"},
        category = ModuleCategory.PLAYER,
        description = "Mines blocks using packet actions with visual progress box and instant rebreak."
)
public final class SpeedMine extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode =
            enumSetting("speedMineMode", "mode", Mode.PACKET, Mode.values());

    private final NumberValue<Float> speed =
            num("speedMineSpeed", "speed", 1.4f, 1.0f, 3.0f);

    private final NumberValue<Float> breakProgress =
            num("speedMineBreakProgress", "break_progress", 1.0f, 0.7f, 1.0f);

    private final BooleanValue instantRebreak =
            bool("speedMineInstantRebreak", "instant_rebreak", true);

    private final BooleanValue silentTool =
            bool("speedMineSilentTool", "silent_tool", true);

    private final BooleanValue render =
            bool("speedMineRender", "render", true);

    private final EnumValue<RenderMode> renderMode =
            enumSetting("speedMineRenderMode", "render_mode", RenderMode.EXPAND, RenderMode.values());

    private BlockPos miningPos = null;
    private Direction miningDirection = Direction.UP;
    private float progress = 0.0f;

    private BlockPos rebreakPos = null;
    private Direction rebreakDirection = Direction.UP;

    @Override
    public void onDisable() {
        miningPos = null;
        progress = 0.0f;
        rebreakPos = null;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.getConnection() == null) {
            miningPos = null;
            progress = 0.0f;
            return;
        }

        // Process active mining
        if (miningPos != null) {
            BlockState state = mc.level.getBlockState(miningPos);
            if (state.isAir()) {
                if (instantRebreak.get()) {
                    rebreakPos = miningPos;
                    rebreakDirection = miningDirection;
                }
                miningPos = null;
                progress = 0.0f;
            } else {
                float delta = state.getDestroyProgress(player, mc.level, miningPos);
                progress += delta * speed.get();

                if (progress >= breakProgress.get()) {
                    if (silentTool.get()) {
                        int toolSlot = findBestTool(state);
                        if (toolSlot != -1) {
                            InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
                        }
                    }

                    mc.getConnection().send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                            miningPos,
                            miningDirection
                    ));
                    player.swing(InteractionHand.MAIN_HAND);

                    if (instantRebreak.get()) {
                        rebreakPos = miningPos;
                        rebreakDirection = miningDirection;
                    }

                    miningPos = null;
                    progress = 0.0f;
                }
            }
        }

        // Process instant rebreak
        if (rebreakPos != null && instantRebreak.get()) {
            BlockState state = mc.level.getBlockState(rebreakPos);
            if (!state.isAir()) {
                if (silentTool.get()) {
                    int toolSlot = findBestTool(state);
                    if (toolSlot != -1) {
                        InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
                    }
                }

                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                        rebreakPos,
                        rebreakDirection
                ));
                player.swing(InteractionHand.MAIN_HAND);
                rebreakPos = null;
            }
        }
    }

    @EventHandler(priority = 100)
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundPlayerActionPacket actionPacket) {
            ServerboundPlayerActionPacket.Action action = actionPacket.getAction();

            if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                miningPos = actionPacket.getPos();
                miningDirection = actionPacket.getDirection();
                progress = 0.0f;
            } else if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                if (mode.get() == Mode.PACKET) {
                    event.cancel();
                }
            }
        }
    }

    private int findBestTool(BlockState state) {
        LocalPlayer player = mc.player;
        if (player == null) return -1;

        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                float speed = stack.getDestroySpeed(state);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || miningPos == null) return;

        float p = Math.min(1.0f, Math.max(0.0f, progress / breakProgress.get()));

        int r = (int) ((1.0f - p) * 255);
        int g = (int) (p * 255);
        int b = 40;

        int fillArgb = (0x55 << 24) | (r << 16) | (g << 8) | b;
        int lineArgb = (0xFF << 24) | (r << 16) | (g << 8) | b;

        AABB fullBox = new AABB(miningPos);
        AABB renderBox = fullBox;

        if (renderMode.get() == RenderMode.EXPAND) {
            double inset = (1.0 - p) * 0.45;
            renderBox = fullBox.deflate(inset);
        }

        addFilledBox(renderer, renderBox, fillArgb);
        addOutlineBox(renderer, renderBox, lineArgb);
    }

    private static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, a);
    }

    private static void addOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, r, g, b, a);

        renderer.line(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, a);

        renderer.line(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
    }

    public enum Mode {
        PACKET,
        DAMAGE
    }

    public enum RenderMode {
        EXPAND,
        STATIC
    }
}
