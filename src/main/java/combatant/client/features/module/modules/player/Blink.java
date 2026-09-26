/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.BlinkPacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.network.BlinkManager;
import combatant.client.util.network.TransferOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

@ModuleInfo(
        id = "blink",
        displayName = "Blink",
        aliases = {"PacketChoke", "FakeLagBurst", "Desync"},
        category = ModuleCategory.PLAYER,
        description = "Chokes outgoing movement packets until disabled or limit reached."
)
public final class Blink extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<PacketMode> packetMode =
            enumSetting("blinkPacketMode", "packet_mode", PacketMode.MOVEMENT_ONLY, PacketMode.values());

    private final NumberValue<Integer> limit =
            num("blinkLimit", "limit", 100, 10, 1000);

    private final EnumValue<LimitAction> limitAction =
            enumSetting("blinkLimitAction", "limit_action", LimitAction.DISABLE, LimitAction.values());

    private final BooleanValue pulse =
            bool("blinkPulse", "pulse", false);

    private final NumberValue<Integer> pulseDelayMs =
            visibleWhen(num("blinkPulseDelayMs", "pulse_delay_ms", 500, 50, 5000), pulse::get);

    private final BooleanValue cancelOnDisable =
            bool("blinkCancelOnDisable", "cancel_on_disable", false);

    private final BooleanValue renderGhost =
            bool("blinkRenderGhost", "render_ghost", true);

    private final BooleanValue renderTrail =
            bool("blinkRenderTrail", "render_trail", true);

    private final RGBAColorValue fillColor =
            color("blinkFillColor", "fill_color", "#285A9C33");

    private final RGBAColorValue lineColor =
            color("blinkLineColor", "line_color", "#55AAFFFF");

    private final NumberValue<Float> lineWidth =
            numCommon("blinkLineWidth", "line_width", CommonSettingSchemas.LINE_WIDTH, 2.0f, 1.0f, 6.0f);

    private Vec3 startPos = null;
    private long lastPulseTime = 0L;

    @Override
    public void onEnable() {
        LocalPlayer player = mc.player;
        if (player == null) {
            setEnabled(false);
            return;
        }

        startPos = player.position();
        lastPulseTime = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        if (cancelOnDisable.get()) {
            BlinkManager.INSTANCE.cancelOutgoingMovement();
        } else {
            BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
        }
        startPos = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) {
            setEnabled(false);
            return;
        }

        int queuedCount = BlinkManager.INSTANCE.getPacketQueue().size();
        if (queuedCount >= limit.get()) {
            if (limitAction.get() == LimitAction.DISABLE) {
                setEnabled(false);
                return;
            } else {
                BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
                startPos = player.position();
            }
        }

        if (pulse.get()) {
            long now = System.currentTimeMillis();
            if (now - lastPulseTime >= pulseDelayMs.get()) {
                lastPulseTime = now;
                BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
                startPos = player.position();
            }
        }
    }

    @EventHandler(priority = 100)
    public void onBlinkPacket(BlinkPacketEvent event) {
        if (!isEnabled() || event.getOrigin() != TransferOrigin.OUTGOING) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packetMode.get() == PacketMode.MOVEMENT_ONLY) {
            if (!(packet instanceof ServerboundMovePlayerPacket)) {
                return;
            }
        }

        event.setAction(BlinkManager.Action.QUEUE);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;

        LocalPlayer player = mc.player;
        List<Vec3> positions = BlinkManager.INSTANCE.getQueuedMovePositions();
        Vec3 serverPos = positions.isEmpty() ? startPos : positions.get(0);
        if (serverPos == null) return;

        int fillArgb = fillColor.getArgb();
        int lineArgb = lineColor.getArgb();

        if (renderGhost.get()) {
            AABB ghostBox = player.getDimensions(player.getPose()).makeBoundingBox(serverPos);
            addFilledBox(renderer, ghostBox, fillArgb);
            addOutlineBox(renderer, ghostBox, lineArgb);
        }

        if (renderTrail.get()) {
            int a = (lineArgb >>> 24) & 0xFF;
            int r = (lineArgb >>> 16) & 0xFF;
            int g = (lineArgb >>> 8) & 0xFF;
            int b = lineArgb & 0xFF;

            Vec3 prev = serverPos;
            for (Vec3 pos : positions) {
                renderer.line(prev.x, prev.y + 0.1, prev.z, pos.x, pos.y + 0.1, pos.z, r, g, b, a);
                prev = pos;
            }
            renderer.line(prev.x, prev.y + 0.1, prev.z, player.getX(), player.getY() + 0.1, player.getZ(), r, g, b, a);
        }
    }

    private static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
    }

    private static void addOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    public enum PacketMode {
        MOVEMENT_ONLY,
        ALL_OUTGOING
    }

    public enum LimitAction {
        DISABLE,
        FLUSH
    }
}
