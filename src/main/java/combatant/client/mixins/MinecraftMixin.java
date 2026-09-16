/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.systems.GpuSurface;
import combatant.client.features.module.modules.combat.*;
import net.minecraft.client.FramerateLimiter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.events.Events;
import combatant.client.events.impl.CrosshairTargetUpdateEvent;
import combatant.client.features.gui.chat.BetterChatStoreManager;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.Timer;
import combatant.client.features.module.modules.player.NoDelay;
import combatant.client.features.module.modules.player.MultiTask;
import combatant.client.features.module.modules.player.NoInteract;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.features.module.modules.visuals.ViewModel;
import combatant.client.features.relations.StaffTracker;
import combatant.client.render.helpers.TickDelta;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.util.player.PlayerSkinResolver;
import combatant.client.util.combat.AntiBotTracker;
import combatant.client.util.session.SessionChanger;
import combatant.client.util.session.MinecraftGameConfigHolder;
import combatant.client.util.logging.DebugLog;

@Mixin(Minecraft.class)
public class MinecraftMixin implements MinecraftGameConfigHolder {

    @Shadow
    @Final
    private GpuSurface windowSurface;

    @Unique
    private GameConfig combatant$gameConfig;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void combatant$captureGameConfig(GameConfig config, CallbackInfo ci) {
        this.combatant$gameConfig = config;
    }

    @Override
    public GameConfig combatant$getGameConfig() {
        return combatant$gameConfig;
    }

    @Unique
    private static BlockHitResult combatant$miss(Vec3 pos) {
        return new BlockHitResult(
                pos,
                Direction.UP,
                BlockPos.ZERO,
                false
        );
    }

    @Unique
    private static void combatant$guardSingleplayerSaveWithAltUsername() {
        SessionChanger.restoreSingleplayerJoinSessionForDisconnect();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void fastThrow(CallbackInfo ci) {
        try {
            NoDelay noDelay = Modules.get(NoDelay.class);
            if (noDelay != null && noDelay.handleFastUse()) {
                ci.cancel();
                return;
            }

            AutoBow autoBow = Modules.get(AutoBow.class);
            if (autoBow != null && autoBow.shouldCancelVanillaDoItemUse()) {
                ci.cancel();
            }
        } catch (Throwable t) {
            DebugLog.error("Error in fastThrow hook", t);
        }
    }

    @Inject(
            method = "handleKeybinds",
            at = @At("HEAD")
    )
    private void spearassist$blockAttackInput(CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null || mc.options == null || mc.options.keyAttack == null) return;

            SpearAssist assist = Modules.get(SpearAssist.class);
            if (assist == null || !assist.isEnabled() || !assist.respectCooldownEnabled()) {
                return;
            }

            ItemStack stack = mc.player.getMainHandItem();
            if (stack == null || !SpearAssist.isSpear(stack)) return;

            float cooldown = mc.player.getAttackStrengthScale(0.0F);
            if (cooldown < 0.99F) {
                // ВАЖНО: гасим сам ввод
                mc.options.keyAttack.setDown(false);
            }
        } catch (Throwable t) {
            DebugLog.error("Error in spearassist hook", t);
        }
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void combatant$autobow$finishInputCycle(CallbackInfo ci) {
        try {
            AutoBow autoBow = Modules.get(AutoBow.class);
            if (autoBow != null && autoBow.isEnabled()) {
                autoBow.onInputCycleHandled();
            }
        } catch (Throwable t) {
            DebugLog.error("Error in autobow hook", t);
        }
    }

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void combatant$disablePvpGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player)) return;
        try {
            PvpCooldowns mod = Modules.get(PvpCooldowns.class);
            if (mod == null || !mod.shouldHideTargetGlow()) return;
            cir.setReturnValue(false);
        } catch (Throwable t) {
            DebugLog.error("Error in disablePvpGlow hook", t);
        }
    }


    // === КУРСОР ДЛЯ CLICKGUI ===

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void onHandleBlockBreaking(boolean breaking, CallbackInfo ci) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null) return;
            LocalPlayer player = client.player;
            if (player == null || client.level == null) return;

            // Hitbox должен работать даже при ломании блоков: не блокируем vanilla логику
            Hitbox hitbox = Modules.get(Hitbox.class);
            if (hitbox != null && hitbox.isEnabled()) {
                return;
            }

            if (client.options == null || client.options.keyAttack == null) return;
            boolean attackKeyPressed = client.options.keyAttack.isDown();

            AutoAttack autoAttack = Modules.get(AutoAttack.class);
            boolean blockByAutoAttack = autoAttack != null
                    && autoAttack.isEnabled()
                    && !autoAttack.isAutoMode()
                    && autoAttack.shouldBlockBreaking();
            boolean shouldBlockBreaking = blockByAutoAttack;

            if (shouldBlockBreaking && attackKeyPressed) {
                // блокируем ломание
                ci.cancel();

                if (client.gameMode != null)
                    client.gameMode.stopDestroyBlock();

                // имитация удара
            }
        } catch (Throwable t) {
            DebugLog.error("Error in continueAttack block breaking hook", t);
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null || client.level == null) return;

            AttributeSwap attributeSwap = Modules.get(AttributeSwap.class);
            if (attributeSwap != null && attributeSwap.isEnabled() && attributeSwap.handleManualAttack()) {
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }

            AutoAttack autoAttack = Modules.get(AutoAttack.class);
            if (autoAttack != null && autoAttack.isEnabled()) {
                cir.setReturnValue(false);
                cir.cancel();
            }
        } catch (Throwable t) {
            DebugLog.error("Error in startAttack hook", t);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void combatant$betterChat$saveOnDisconnect(net.minecraft.client.gui.screens.Screen screen, boolean transferring, boolean bl, CallbackInfo ci) {
        try {
            combatant$guardSingleplayerSaveWithAltUsername();
            if (!transferring) {
                PlayerHeadRenderer.clearSessionCache();
                PlayerSkinResolver.clearAll();
            }
            try {
                BetterChatStoreManager.flushAll();
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            DebugLog.error("Error in disconnect HEAD hook", t);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("TAIL"))
    private void combatant$sessionRestoreAfterDisconnect(net.minecraft.client.gui.screens.Screen screen, boolean transferring, boolean bl, CallbackInfo ci) {
        try {
            SessionChanger.restoreDeferredSessionAfterDisconnect();
        } catch (Throwable t) {
            DebugLog.error("Error in disconnect TAIL hook", t);
        }
    }

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void onUpdateCrosshairTarget(float tickDelta, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) return;

            var fc = Modules.get(Freecam.class);

            // =========================================================
            // FREECAM
            // =========================================================
            if (fc != null && fc.isEnabled()) {

                // позиция и направление FREECAM
                Vec3 start = new Vec3(
                        Mth.lerp(tickDelta, fc.camPosPrev.x, fc.camPos.x),
                        Mth.lerp(tickDelta, fc.camPosPrev.y, fc.camPos.y),
                        Mth.lerp(tickDelta, fc.camPosPrev.z, fc.camPos.z)
                );

                float yawRad = -fc.camYaw * ((float) Math.PI / 180F);
                float pitchRad = -fc.camPitch * ((float) Math.PI / 180F);

                Vec3 dir = new Vec3(
                        Mth.sin(yawRad) * Mth.cos(pitchRad),
                        Mth.sin(pitchRad),
                        Mth.cos(yawRad) * Mth.cos(pitchRad)
                );

                // ===============================
                // FREECAM + NO INTERACT
                // ===============================
                if (!fc.allowInteract()) {
                    mc.hitResult = combatant$miss(start);
                    mc.crosshairPickEntity = null;
                    ci.cancel();
                    return;
                }

                // ===============================
                // FREECAM + INTERACT
                // ===============================

                // Reach (FREECAM)
                Reach reachModule = Modules.get(Reach.class);
                if (reachModule != null && reachModule.isEnabled()) {
                    EntityHitResult hit = reachModule.raycastEntities(
                            mc.player, start, dir
                    );

                    if (hit != null && hit.getEntity() instanceof LivingEntity target && target.isAlive()) {
                        mc.hitResult = hit;
                        mc.crosshairPickEntity = target;
                        ci.cancel();
                        return;
                    }
                }

                // Blocks (FREECAM)
                double reach = mc.player.blockInteractionRange();
                Vec3 end = start.add(dir.scale(reach));

                BlockHitResult bhr = mc.level.clip(new net.minecraft.world.level.ClipContext(
                        start, end,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        mc.player
                ));

                mc.hitResult = bhr;
                mc.crosshairPickEntity = null;
                ci.cancel();
                return;
            }

            Reach reachModule = Modules.get(Reach.class);
            if (reachModule != null && reachModule.isEnabled()) {
                EntityHitResult entityHit = reachModule.raycastEntities(mc.player);
                if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target && target.isAlive()) {
                    mc.hitResult = entityHit;
                    mc.crosshairPickEntity = target;
                    ci.cancel();
                }
            }
        } catch (Throwable t) {
            DebugLog.error("Error in onUpdateCrosshairTarget hook", t);
        }
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void combatant$dispatchCrosshairTargetEvent(float tickDelta, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) return;
            if (mc.hitResult == null) return;

            CrosshairTargetUpdateEvent event = new CrosshairTargetUpdateEvent(
                    tickDelta,
                    mc.hitResult,
                    mc.crosshairPickEntity
            );
            Events.BUS.post(event);
            mc.hitResult = event.getHitResult();
            mc.crosshairPickEntity = event.getTargetedEntity();
        } catch (Throwable t) {
            DebugLog.error("Error in dispatchCrosshairTargetEvent hook", t);
        }
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void noInteract$afterRaycast(float tickDelta, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) return;

            NoInteract noInteract = Modules.get(NoInteract.class);
            if (noInteract == null || !noInteract.shouldBlockBlockInteraction()) return;

            var hit = mc.hitResult;
            if (hit instanceof EntityHitResult) return;

            Vec3 camPos = mc.player.getEyePosition(tickDelta);
            mc.hitResult = new BlockHitResult(
                    camPos,
                    Direction.UP,
                    BlockPos.ZERO,
                    false
            );
            mc.crosshairPickEntity = null;
        } catch (Throwable t) {
            DebugLog.error("Error in noInteract afterRaycast hook", t);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void freecam$onDisconnect(net.minecraft.client.gui.screens.Screen screen, boolean transferring, boolean bl, CallbackInfo ci) {
        try {
            Freecam fc = Modules.get(Freecam.class);
            if (fc != null && fc.isEnabled()) {
                fc.toggle();
            }
        } catch (Throwable t) {
            DebugLog.error("Error in freecam disconnect hook", t);
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void freecam$onJoinWorld(ClientLevel world, CallbackInfo ci) {
        try {
            Freecam fc = Modules.get(Freecam.class);
            if (fc != null && fc.isEnabled()) {
                fc.toggle();
            }
        } catch (Throwable t) {
            DebugLog.error("Error in freecam setLevel hook", t);
        }
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void combatant$captureSingleplayerJoinSession(ClientLevel world, CallbackInfo ci) {
        try {
            if (world == null) {
                SessionChanger.clearSingleplayerJoinSession();
            } else {
                SessionChanger.captureSingleplayerJoinSession();
            }
        } catch (Throwable t) {
            DebugLog.error("Error in captureSingleplayerJoinSession hook", t);
        }
    }

    @Inject(method = "clearDownloadedResourcePacks", at = @At("HEAD"))
    private void combatant$onDisconnected(CallbackInfo ci) {
        try {
            StaffTracker.resetAll();
            AntiBotTracker.INSTANCE.reset();
        } catch (Throwable t) {
            DebugLog.error("Error in onDisconnected hook", t);
        }
    }

    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceRealTime(J)V",
                    shift = At.Shift.AFTER
            )
    )
    private void combatant$frameModulesAfterRenderTick(boolean tick, CallbackInfo ci) {
        try {
            ModuleManager.frameAll(TickDelta.frameDeltaTicks());
        } catch (Throwable t) {
            DebugLog.error("Error in frameModulesAfterRenderTick hook", t);
        }
    }

    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE_STRING",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
                    args = "ldc=frameLimiter",
                    shift = At.Shift.AFTER
            )
    )
    private void combatant$paceImmediateNoVsync(boolean tick, CallbackInfo ci) {
        try {
            Minecraft mc = (Minecraft) (Object) this;
            if (mc == null || mc.options == null || mc.getWindow() == null || windowSurface == null) {
                return;
            }
            if (Boolean.TRUE.equals(mc.options.enableVsync().get())) {
                return;
            }
            if (windowSurface.currentConfiguration().isEmpty()
                    || windowSurface.currentConfiguration().get().presentMode() != GpuSurface.PresentMode.IMMEDIATE) {
                return;
            }

            if (mc.gameRenderer == null || mc.gameRenderer.gameRenderState() == null) {
                return;
            }
            int configuredLimit = mc.gameRenderer.gameRenderState().framerateLimit;
            if (configuredLimit < 260) {
                return;
            }

            int refreshRate = mc.getWindow().getRefreshRate();
            int targetFps = combatant$immediatePacingTarget(refreshRate);
            FramerateLimiter.limitDisplayFPS(targetFps);
        } catch (Throwable t) {
            DebugLog.error("Error in paceImmediateNoVsync hook", t);
        }
    }

    @Unique
    private static int combatant$immediatePacingTarget(int refreshRate) {
        int base = refreshRate > 0 ? refreshRate : 120;
        return Mth.clamp(base * 2, 120, 360);
    }

    @Inject(method = "getTickTargetMillis", at = @At("RETURN"), cancellable = true)
    private void combatant$applyTimer(float millis, CallbackInfoReturnable<Float> cir) {
        try {
            float mult = Timer.getTickTimer();
            if (mult <= 0.0001f) return;
            if (Math.abs(mult - 1.0f) < 0.0001f) return;
            float base = cir.getReturnValue();
            cir.setReturnValue(base / mult);
        } catch (Throwable t) {
            DebugLog.error("Error in applyTimer hook", t);
        }
    }

    @Inject(method = "isLevelRunningNormally", at = @At("HEAD"), cancellable = true)
    private void combatant$skipPlayerDependentTicksWithoutPlayer(CallbackInfoReturnable<Boolean> cir) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null && mc.player == null) {
                cir.setReturnValue(false);
            }
        } catch (Throwable t) {
            DebugLog.error("Error in isLevelRunningNormally hook", t);
        }
    }

    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
    private void combatant$viewModel$resourceReload(CallbackInfoReturnable<java.util.concurrent.CompletableFuture<Void>> cir) {
        try {
            java.util.concurrent.CompletableFuture<Void> future = cir.getReturnValue();
            if (future == null) return;

            future.thenRun(() -> {
                try {
                    Minecraft client = Minecraft.getInstance();
                    if (client != null) {
                        client.execute(() -> {
                            try {
                                ViewModel viewModel = Modules.get(ViewModel.class);
                                if (viewModel != null) viewModel.onHmiResourceReload();
                            } catch (Throwable t) {
                                DebugLog.error("Error reloading ViewModel resources", t);
                            }
                        });
                    }
                } catch (Throwable t) {
                    DebugLog.error("Error scheduling ViewModel reload task", t);
                }
            });
        } catch (Throwable t) {
            DebugLog.error("Error in viewModel resourceReload hook", t);
        }
    }

    @Redirect(
            method = "startUseItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"
            )
    )
    private boolean combatant$multiTask$isDestroying(MultiPlayerGameMode gameMode) {
        if (gameMode == null) return false;
        try {
            MultiTask multiTask = Modules.get(MultiTask.class);
            if (multiTask != null && multiTask.canMineWhileUsing()) {
                return false;
            }
            return gameMode.isDestroying();
        } catch (Throwable t) {
            return gameMode.isDestroying();
        }
    }

    @Redirect(
            method = "continueAttack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"
            )
    )
    private boolean combatant$multiTask$isUsingItem(LocalPlayer player) {
        if (player == null) return false;
        try {
            MultiTask multiTask = Modules.get(MultiTask.class);
            if (multiTask != null && (multiTask.canMineWhileUsing() || multiTask.canAttackWhileUsing())) {
                return false;
            }
            return player.isUsingItem();
        } catch (Throwable t) {
            return player.isUsingItem();
        }
    }

}
