/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventSync;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.PlayerJumpEvent;
import combatant.client.events.impl.PlayerVelocityStrafe;
import combatant.client.events.impl.SprintControlEvent;
import combatant.client.mixins.accessors.EntityInvoker;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.Scaffold;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.util.combat.CombatStrikeController;
import combatant.client.util.combat.SprintController;
import combatant.client.util.player.MovementUtil;

//todo Description
@ModuleInfo(
        id = "sprint",
        displayName = "Sprint",
        category = ModuleCategory.MOVEMENT
)
public final class Sprint extends Module {

    private static final double VULCAN_SPRINT_A_SPEED_GATE = 0.25;
    private static final double VULCAN_SPRINT_A_BUFFER_DECAY = 0.4;
    private static final int VULCAN_SPRINT_A_DIRECTION_GATE_TICKS = 10;
    private static final int VULCAN_SPRINT_A_RESET_BEFORE_TICKS = 6;
    private static final int VULCAN_SPRINT_A_RESET_TICKS = 3;
    private static final int VULCAN_SPRINT_A_JUMP_GRACE_TICKS = 4;
    private static final int VULCAN_EXTERNAL_SPRINT_SUPPRESS_TICKS = 30;
    private final EnumValue<ResetMode> defaultResetMode =
            enumMode("default_reset_mode", ResetMode.LEGIT, ResetMode.values());
    private final BooleanValue omniDirectional =
            bool("omni_directional", false);
    private final BooleanValue strafe =
            bool("strafe", false);
    private final EnumValue<StrafeMode> strafeMode =
            visibleWhen(enumMode("strafe_mode", StrafeMode.NCP, StrafeMode.values()), strafe::get);
    private final BooleanValue vulcanBypass =
            visibleWhen(bool("omni_directional_vulcan_bypass", true), this::isOmniOrStrafe);
    private final BooleanValue avoidSwimStartForce =
            bool("avoid_swim_start_force", true);
    private final BooleanValue autoResume =
            bool("auto_resume", true);
    private final Minecraft mc = Minecraft.getInstance();
    private int vulcanSprintSuppressTicks;
    private int vulcanSprintDirectionTicks;
    private int vulcanSprintJumpGraceTicks;
    private int vulcanExternalSprintSuppressTicks;
    private double vulcanSprintABuffer;

    @Override
    public void onEnable() {
        resetVulcanSprintAGuard();
    }

    @Override
    public void onDisable() {
        resetVulcanSprintAGuard();
    }

    @EventHandler(priority = 2000)
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            resetVulcanSprintAGuard();
            return;
        }

        updateVulcanExternalSprintSuppress(player);
        updateVulcanSprintAGuard(player);

        if (autoResume.get() && canOperate(player) && hasMovementInput(player)) {
            if (!shouldHardSuppressSprint() && !shouldSuppressExternalVulcanSprint(player)) {
                if (!shouldAvoidForceSprintForSwimStart(player)) {
                    if (player.getFoodData().getFoodLevel() > 6.0f || player.getAbilities().mayfly) {
                        if (!player.isSprinting()) {
                            player.setSprinting(true);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        LocalPlayer player = mc.player;
        if (!isEnabled() || !isOmniOrStrafe() || !vulcanBypass.get() || player == null || mc.level == null) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundDamageEventPacket damage && damage.entityId() == player.getId()) {
            armVulcanExternalSprintSuppress();
        } else if (packet instanceof ClientboundSetEntityMotionPacket velocity && velocity.id() == player.getId()) {
            armVulcanExternalSprintSuppress();
        } else if (packet instanceof ClientboundExplodePacket explosion && explosion.playerKnockback().isPresent()) {
            armVulcanExternalSprintSuppress();
        }
    }

    @EventHandler
    private void onPlayerJump(PlayerJumpEvent event) {
        LocalPlayer player = mc.player;
        if (!shouldUseOmniDirectional(player)) {
            return;
        }

        markVulcanSprintAJumpWindow();
        event.setYaw(getMovementYaw(player, event.getYaw()));
    }

    @EventHandler
    private void onSprintControl(SprintControlEvent event) {
        LocalPlayer player = mc.player;
        if (shouldHardSuppressSprint()) {
            event.setSprint(false);
            return;
        }

        if (event.getSource() != SprintControlEvent.Source.MOVEMENT_TICK
                && event.getSource() != SprintControlEvent.Source.INPUT) {
            return;
        }

        if (!event.isMoving() || (!shouldUseOmniDirectional(player) && !autoResume.get())) {
            return;
        }

        if (shouldAvoidForceSprintForSwimStart(player)) {
            return;
        }

        event.setSprint(true);
    }
    @EventHandler(priority = 50)
    private void onVelocityStrafe(PlayerVelocityStrafe event) {
        if (!isEnabled() || !strafe.get()) {
            return;
        }

        LocalPlayer player = mc.player;
        if (!canOperate(player) || !isServerOrLocalSprinting(player) || !hasMovementInput(player)) {
            return;
        }

        if (shouldHardSuppressSprint() || shouldSuppressExternalVulcanSprint(player)) {
            return;
        }

        RotationTarget active = RotationManager.INSTANCE.getActiveRotationTarget();
        if (active != null && active.movementCorrection != MovementCorrection.OFF) {
            return;
        }

        StrafeMode mode = strafeMode.get();

        if (mode == StrafeMode.VANILLA) {
            // Vanilla: use the real movement input vector and the actual player yaw unchanged.
            // This makes A/D/W/S all produce correct strafing without rotating the yaw.
            Vec3 input = event.getMovementInput();
            double lenSq = input.lengthSqr();
            if (lenSq < 1.0E-7) return;
            Vec3 velocity = EntityInvoker.combatant$movementInputToVelocity(
                    input,
                    event.getSpeed(),
                    player.getYRot()
            );
            event.setVelocity(velocity);
        } else {
            // NCP: redirect all input onto the movement-direction yaw as pure forward,
            // so the server sees sprint-forward regardless of held keys.
            float moveYaw = getMovementYaw(player, player.getYRot());
            Vec3 forwardInput = new Vec3(0.0, 0.0, 1.0);
            Vec3 velocity = EntityInvoker.combatant$movementInputToVelocity(
                    forwardInput,
                    event.getSpeed(),
                    moveYaw
            );
            event.setVelocity(velocity);
        }
    }

    @EventHandler(priority = -50)
    private void onSync(EventSync event) {
        if (!isEnabled() || !strafe.get() || strafeMode.get() != StrafeMode.NCP) {
            return;
        }

        LocalPlayer player = mc.player;
        if (!canOperate(player) || !player.isSprinting() || !hasMovementInput(player)) {
            return;
        }

        if (shouldHardSuppressSprint() || shouldSuppressExternalVulcanSprint(player)) {
            return;
        }

        if ((player.isInWater() && !player.isSwimming()) || player.isInLava()) {
            return;
        }

        // Allow sync rotation even when a rotation target is active, as long as
        // it has movement correction OFF (e.g. silent aim that doesn't redirect movement).
        RotationTarget active = RotationManager.INSTANCE.getActiveRotationTarget();
        if (active != null && active.movementCorrection != MovementCorrection.OFF) {
            return;
        }

        if (isAttacking(player)) {
            return;
        }

        float moveYaw = getMovementYaw(player, player.getYRot());
        float diff = Math.abs(RotationUtil.angleDifference(player.getYRot(), moveYaw));
        if (diff > 15.0f) {
            event.setRotation(moveYaw, player.getXRot(), true);
        }
    }

    private boolean isAttacking(LocalPlayer player) {
        return player.attackAnim > 0 || player.swinging;
    }

    public boolean isOmniOrStrafe() {
        return omniDirectional.get() || strafe.get();
    }

    public CombatStrikeController.SprintResetMode resolveCombatResetMode() {
        return switch (defaultResetMode.get()) {
            case LEGIT -> CombatStrikeController.SprintResetMode.LEGIT;
            case PACKET -> CombatStrikeController.SprintResetMode.PACKET;
            case FORCE -> CombatStrikeController.SprintResetMode.NONE;
        };
    }

    public boolean isLegitResetMode() {
        return defaultResetMode.get() == ResetMode.LEGIT;
    }

    public boolean shouldAllowDirectionalSprint() {
        LocalPlayer player = mc.player;
        return shouldUseOmniDirectional(player);
    }

    public boolean shouldSuppressExternalVulcanSprintForControl() {
        return shouldSuppressExternalVulcanSprint(mc.player);
    }

    public boolean shouldAvoidForceSprintForSwimStart(LocalPlayer player) {
        return isEnabled()
                && avoidSwimStartForce.get()
                && isWaitingForManualSwimStart(player);
    }

    public float getMovementYaw(LocalPlayer player, float fallbackYaw) {
        return MovementUtil.getMovementDirectionYaw(player, fallbackYaw);
    }

    private boolean shouldUseOmniDirectional(LocalPlayer player) {
        return isEnabled()
                && isOmniOrStrafe()
                && canOperate(player)
                && !shouldSuppressOmniDirectionalSprint(player)
                && hasMovementInput(player);
    }

    private boolean shouldHardSuppressSprint() {
        return isEnabled()
                && isOmniOrStrafe()
                && vulcanBypass.get()
                && (shouldSuppressScaffoldDirectionalSprint()
                || vulcanSprintSuppressTicks > 0);
    }
    private boolean shouldSuppressOmniDirectionalSprint(LocalPlayer player) {
        return shouldHardSuppressSprint() || shouldSuppressExternalVulcanSprint(player);
    }

    private boolean shouldSuppressScaffoldDirectionalSprint() {
        Scaffold scaffold = Modules.get(Scaffold.class);
        return scaffold != null && scaffold.shouldSuppressOmniDirectionalSprint();
    }

    private void updateVulcanSprintAGuard(LocalPlayer player) {
        if (!isEnabled() || !isOmniOrStrafe() || !vulcanBypass.get() || !canOperate(player)) {
            resetVulcanSprintAGuard();
            return;
        }

        if (vulcanSprintJumpGraceTicks > 0) {
            vulcanSprintJumpGraceTicks--;
        }

        if (vulcanSprintSuppressTicks > 0) {
            vulcanSprintSuppressTicks--;
            vulcanSprintDirectionTicks = 0;
            decayVulcanSprintABuffer();
            return;
        }

        if (!isVulcanSprintARisky(player)) {
            vulcanSprintDirectionTicks = 0;
            decayVulcanSprintABuffer();
            return;
        }

        vulcanSprintDirectionTicks++;
        if (vulcanSprintDirectionTicks > VULCAN_SPRINT_A_DIRECTION_GATE_TICKS
                && getHorizontalSpeed(player) > VULCAN_SPRINT_A_SPEED_GATE) {
            vulcanSprintABuffer = Math.min(10000.0, vulcanSprintABuffer + 1.0);
        } else {
            decayVulcanSprintABuffer();
        }

        if (shouldResetBeforeVulcanSprintA()) {
            vulcanSprintSuppressTicks = VULCAN_SPRINT_A_RESET_TICKS;
            vulcanSprintDirectionTicks = 0;
            decayVulcanSprintABuffer();
            SprintController.INSTANCE.requestLegitStop(mc, player, VULCAN_SPRINT_A_RESET_TICKS);
        }
    }

    private void updateVulcanExternalSprintSuppress(LocalPlayer player) {
        if (!isEnabled() || !isOmniOrStrafe() || !vulcanBypass.get() || !canOperate(player)) {
            vulcanExternalSprintSuppressTicks = 0;
            return;
        }

        if (player.hurtTime > 0) {
            armVulcanExternalSprintSuppress();
        }

        if (vulcanExternalSprintSuppressTicks <= 0) {
            return;
        }

        if (player.onGround() && player.hurtTime <= 0) {
            vulcanExternalSprintSuppressTicks = 0;
            return;
        }

        vulcanExternalSprintSuppressTicks--;
    }

    private boolean shouldSuppressExternalVulcanSprint(LocalPlayer player) {
        return player != null
                && vulcanBypass.get()
                && (vulcanExternalSprintSuppressTicks > 0 || player.hurtTime > 0);
    }

    private void armVulcanExternalSprintSuppress() {
        vulcanExternalSprintSuppressTicks = Math.max(
                vulcanExternalSprintSuppressTicks,
                VULCAN_EXTERNAL_SPRINT_SUPPRESS_TICKS
        );
    }

    private boolean isVulcanSprintARisky(LocalPlayer player) {
        if (player == null
                || player.input == null
                || !hasMovementInput(player)
                || isVulcanSprintAExempt(player)
                || isLowRiskVulcanSprintAJumpWindow(player)
                || !isServerOrLocalSprinting(player)) {
            return false;
        }

        return hasRiskyOmniSprintDirection(player);
    }

    private boolean shouldResetBeforeVulcanSprintA() {
        return vulcanSprintDirectionTicks >= VULCAN_SPRINT_A_RESET_BEFORE_TICKS
                || vulcanSprintABuffer >= 5.0;
    }

    private boolean isServerOrLocalSprinting(LocalPlayer player) {
        return player.isSprinting() || SprintController.INSTANCE.isServerSprinting(player);
    }

    private boolean isLowRiskVulcanSprintAJumpWindow(LocalPlayer player) {
        return !player.onGround()
                || vulcanSprintJumpGraceTicks > 0
                || isJumpInputPressed(player);
    }

    private boolean isJumpInputPressed(LocalPlayer player) {
        return player.input != null
                && player.input.keyPresses != null
                && player.input.keyPresses.jump();
    }

    private void markVulcanSprintAJumpWindow() {
        if (!isEnabled() || !isOmniOrStrafe() || !vulcanBypass.get()) {
            return;
        }

        vulcanSprintJumpGraceTicks = VULCAN_SPRINT_A_JUMP_GRACE_TICKS;
    }

    private boolean hasRiskyOmniSprintDirection(LocalPlayer player) {
        Vec2 movementInput = player.input.getMoveVector();
        float sideways = Math.abs(movementInput.x);
        float forward = movementInput.y;

        return forward <= 0.0f || sideways > Math.abs(forward) + 0.001f;
    }

    private boolean isVulcanSprintAExempt(LocalPlayer player) {
        return player.isAutoSpinAttack()
                || player.isInWater()
                || player.isUnderWater()
                || player.isInLava()
                || player.isSwimming()
                || player.isFallFlying()
                || isOnIce(player);
    }

    private boolean isWaitingForManualSwimStart(LocalPlayer player) {
        return player != null
                && player.input != null
                && player.isUnderWater()
                && !player.isSwimming()
                && !player.isPassenger()
                && !player.getAbilities().flying
                && !player.isFallFlying()
                && !player.isSpectator()
                && mc.level != null
                && mc.level.getFluidState(player.blockPosition()).is(FluidTags.WATER);
    }

    private boolean isOnIce(LocalPlayer player) {
        if (mc.level == null) {
            return false;
        }

        BlockPos pos = player.blockPosition().below();
        Block block = mc.level.getBlockState(pos).getBlock();
        return block == Blocks.ICE
                || block == Blocks.PACKED_ICE
                || block == Blocks.BLUE_ICE
                || block == Blocks.FROSTED_ICE;
    }

    private double getHorizontalSpeed(LocalPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        return Math.hypot(velocity.x, velocity.z);
    }

    private void decayVulcanSprintABuffer() {
        vulcanSprintABuffer = Math.max(0.0, vulcanSprintABuffer - VULCAN_SPRINT_A_BUFFER_DECAY);
    }

    private void resetVulcanSprintAGuard() {
        vulcanSprintSuppressTicks = 0;
        vulcanSprintDirectionTicks = 0;
        vulcanSprintJumpGraceTicks = 0;
        vulcanExternalSprintSuppressTicks = 0;
        vulcanSprintABuffer = 0.0;
    }

    private boolean hasMovementInput(LocalPlayer player) {
        return player != null
                && player.input != null
                && (player.input.getMoveVector().x != 0.0f
                || player.input.getMoveVector().y != 0.0f);
    }

    private boolean canOperate(LocalPlayer player) {
        return player != null && !isFreecamActive();
    }

    private boolean isFreecamActive() {
        return Modules.get(Freecam.class) != null && Modules.get(Freecam.class).isEnabled();
    }

    public enum ResetMode {
        LEGIT,
        PACKET,
        FORCE
    }
    public enum StrafeMode implements EnumValue.IdProvider {
        NCP("ncp"),
        VANILLA("vanilla");

        private final String id;

        StrafeMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
