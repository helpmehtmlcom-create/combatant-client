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
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Notifier;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.mixins.accessors.ServerboundMovePlayerPacketAccessor;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.screen.ClientScreen;

@ModuleInfo(
        id = "antivoid",
        displayName = "AntiVoid",
        category = ModuleCategory.PLAYER,
        description = "Prevents falling into the void across dimensions with recovery modes and safe rescue."
)
public final class AntiVoid extends Module {

    public enum Mode {
        BOUNCE,
        GLIDE,
        PACKET
    }

    private final EnumValue<Mode> mode =
            enumSetting("antiVoidMode", "mode", Mode.BOUNCE, Mode.values());
    private final NumberValue<Double> voidHeight =
            num("antiVoidVoidHeight", "void_height", 0.0, -64.0, 10.0);
    private final BooleanValue netherCheck =
            bool("antiVoidNetherCheck", "nether_check", true);
    private final BooleanValue autoChorus =
            bool("antiVoidAutoChorus", "auto_chorus", true);
    private final NumberValue<Double> bounceStrength =
            num("antiVoidBounceStrength", "bounce_strength", 0.8, 0.2, 2.0);

    private final Minecraft mc = Minecraft.getInstance();

    private boolean inDanger = false;
    private boolean eatingChorus = false;
    private int chorusSlotRestoration = -1;
    private long lastNotificationTime = 0L;
    private long lastPearlThrowTime = 0L;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            stopEatingChorus(mc.player);
        }
        resetState();
    }

    private void resetState() {
        inDanger = false;
        eatingChorus = false;
        chorusSlotRestoration = -1;
        lastNotificationTime = 0L;
        lastPearlThrowTime = 0L;
        InventorySwap.INSTANCE.releaseHotbar(this);
        RotationManager.INSTANCE.release(this);
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;

        if (player.isSpectator() || player.isCreative()) {
            if (eatingChorus) stopEatingChorus(player);
            inDanger = false;
            return;
        }

        inDanger = checkVoidDanger(player);

        if (!inDanger) {
            if (eatingChorus) {
                stopEatingChorus(player);
            }
            return;
        }

        // Notify player (throttled to avoid spam)
        long now = System.currentTimeMillis();
        if (now - lastNotificationTime > 3000L) {
            lastNotificationTime = now;
            Notifier.warning("AntiVoid triggered! Mode: " + mode.get());
        }

        // Attempt chorus fruit or ender pearl escape if enabled
        if (autoChorus.get()) {
            handleAutoChorus(player);
        }

        // Handle selected recovery mode
        Vec3 delta = player.getDeltaMovement();
        switch (mode.get()) {
            case BOUNCE -> {
                player.setDeltaMovement(delta.x, bounceStrength.get(), delta.z);
                player.fallDistance = 0.0f;
            }
            case GLIDE -> {
                if (player.isFallFlying()) {
                    // Assist elytra gliding to prevent descent into the void
                    player.setDeltaMovement(delta.x, Math.max(delta.y, -0.05), delta.z);
                } else {
                    // Try deploying elytra if equipped
                    if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && mc.getConnection() != null) {
                        mc.getConnection().send(new ServerboundPlayerCommandPacket(
                                player,
                                ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
                        ));
                    }
                    // Apply slow falling motion
                    player.setDeltaMovement(delta.x, Math.max(delta.y, -0.08), delta.z);
                    player.fallDistance = 0.0f;
                }
            }
            case PACKET -> {
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            true,
                            player.horizontalCollision
                    ));
                }
                // Cancel downward velocity
                player.setDeltaMovement(delta.x, 0.0, delta.z);
                player.fallDistance = 0.0f;
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || !inDanger || mode.get() != Mode.PACKET) return;
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            ((ServerboundMovePlayerPacketAccessor) packet).combatant$setOnGround(true);
        }
    }

    /**
     * Checks whether the player is currently falling into void danger.
     */
    private boolean checkVoidDanger(LocalPlayer player) {
        if (player.onGround() || player.getAbilities().flying) {
            return false;
        }

        Level level = player.level();
        double y = player.getY();
        boolean inNether = level.dimension() == Level.NETHER;

        if (inNether && netherCheck.get()) {
            if (y <= 0.0) {
                return !hasSolidBlockBelow(player, level.getMinY());
            } else if (y >= 127.0) {
                // In Nether above ceiling: danger if falling with no solid platform above 127
                return !hasSolidBlockBelow(player, 128);
            }
            return false;
        }

        if (y <= voidHeight.get()) {
            return !hasSolidBlockBelow(player, level.getMinY());
        }

        return false;
    }

    /**
     * Scans downwards from the player's position to check for any solid collision blocks.
     */
    private boolean hasSolidBlockBelow(LocalPlayer player, int minY) {
        Level level = player.level();
        int playerY = player.getBlockY();
        int minScanY = Math.max(level.getMinY(), minY);

        AABB bb = player.getBoundingBox();
        int minX = Mth.floor(bb.minX);
        int maxX = Mth.floor(bb.maxX);
        int minZ = Mth.floor(bb.minZ);
        int maxZ = Mth.floor(bb.maxZ);

        for (int y = playerY; y >= minScanY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Attempts to consume Chorus Fruit or throw an Ender Pearl to reach safe land.
     */
    private void handleAutoChorus(LocalPlayer player) {
        if (mc.gameMode == null) return;

        // If currently eating chorus fruit, maintain the use key
        if (eatingChorus) {
            if (player.isUsingItem() || mc.options.keyUse.isDown()) {
                mc.options.keyUse.setDown(true);
                return;
            } else {
                stopEatingChorus(player);
            }
        }

        // 1. Try eating Chorus Fruit
        if (tryEatChorus(player)) {
            return;
        }

        // 2. Try throwing Ender Pearl to safe ground
        tryThrowPearl(player);
    }

    private boolean tryEatChorus(LocalPlayer player) {
        if (player.getOffhandItem().is(Items.CHORUS_FRUIT)) {
            mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
            mc.options.keyUse.setDown(true);
            eatingChorus = true;
            return true;
        }

        PlayerInventoryAccessor inv = (PlayerInventoryAccessor) player.getInventory();
        int hotbarSlot = findItemInHotbar(player, Items.CHORUS_FRUIT);
        if (hotbarSlot != -1) {
            chorusSlotRestoration = InventorySwap.INSTANCE.clientSelectedSlot();
            InventorySwap.INSTANCE.selectHotbar(hotbarSlot);
            mc.options.keyUse.setDown(true);
            eatingChorus = true;
            return true;
        }

        int invSlot = findItemInInventory(player, Items.CHORUS_FRUIT);
        if (invSlot != -1) {
            int currentSlot = InventorySwap.INSTANCE.clientSelectedSlot();
            int emptyHotbar = findFirstEmptyHotbarSlot(player);
            int targetHotbar = emptyHotbar != -1 ? emptyHotbar : currentSlot;

            chorusSlotRestoration = currentSlot;
            InventorySwap.INSTANCE.swapInventoryToHotbar(invSlot, targetHotbar);
            InventorySwap.INSTANCE.selectHotbar(targetHotbar);
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            mc.options.keyUse.setDown(true);
            eatingChorus = true;
            return true;
        }

        return false;
    }

    private void tryThrowPearl(LocalPlayer player) {
        long now = System.currentTimeMillis();
        if (now - lastPearlThrowTime < 1500L) return;
        if (player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) return;

        int pearlSlot = -1;
        boolean offhand = player.getOffhandItem().is(Items.ENDER_PEARL);
        if (!offhand) {
            pearlSlot = findItemInHotbar(player, Items.ENDER_PEARL);
            if (pearlSlot == -1) {
                pearlSlot = findItemInInventory(player, Items.ENDER_PEARL);
            }
            if (pearlSlot == -1) return;
        }

        // Calculate aim towards nearest safe ground or upward arc
        BlockPos safeGround = findNearestSafeGround(player, 32);
        float yaw;
        float pitch;

        if (safeGround != null) {
            double diffX = (safeGround.getX() + 0.5) - player.getX();
            double diffY = (safeGround.getY() + 1.0) - player.getEyeY();
            double diffZ = (safeGround.getZ() + 0.5) - player.getZ();
            double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
            yaw = (float) (Mth.atan2(diffZ, diffX) * (180.0 / Math.PI)) - 90.0f;
            pitch = (float) (-(Mth.atan2(diffY, diffXZ) * (180.0 / Math.PI))) - 25.0f;
            pitch = Mth.clamp(pitch, -85.0f, 85.0f);
        } else if (player.level().dimension() == Level.END) {
            // Throw towards center island in The End
            double diffX = 0.0 - player.getX();
            double diffZ = 0.0 - player.getZ();
            yaw = (float) (Mth.atan2(diffZ, diffX) * (180.0 / Math.PI)) - 90.0f;
            pitch = -50.0f;
        } else {
            // High upward trajectory
            yaw = player.getYRot() + 180.0f;
            pitch = -75.0f;
        }

        // Aim silently via RotationManager and sync server rotation
        RotationManager.INSTANCE.snapServerRotation(new Rotation(yaw, pitch, false), 50, this, 2);

        if (offhand) {
            mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
            player.swing(InteractionHand.OFF_HAND);
            lastPearlThrowTime = now;
        } else if (pearlSlot < 9) {
            InventorySwap.INSTANCE.leaseHotbar(this, pearlSlot, 1);
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            player.swing(InteractionHand.MAIN_HAND);
            InventorySwap.INSTANCE.releaseHotbar(this);
            lastPearlThrowTime = now;
        } else {
            int currentSlot = InventorySwap.INSTANCE.clientSelectedSlot();
            int emptyHotbar = findFirstEmptyHotbarSlot(player);
            int targetHotbar = emptyHotbar != -1 ? emptyHotbar : currentSlot;
            InventorySwap.INSTANCE.swapInventoryToHotbar(pearlSlot, targetHotbar);
            InventorySwap.INSTANCE.leaseHotbar(this, targetHotbar, 1);
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            player.swing(InteractionHand.MAIN_HAND);
            InventorySwap.INSTANCE.releaseHotbar(this);
            InventorySwap.INSTANCE.swapInventoryToHotbar(targetHotbar, pearlSlot);
            lastPearlThrowTime = now;
        }
    }

    private void stopEatingChorus(LocalPlayer player) {
        if (eatingChorus) {
            if (ClientScreen.current() == null) {
                mc.options.keyUse.setDown(false);
            }
            eatingChorus = false;
        }
        if (chorusSlotRestoration != -1) {
            InventorySwap.INSTANCE.selectHotbar(chorusSlotRestoration);
            chorusSlotRestoration = -1;
        }
    }

    private BlockPos findNearestSafeGround(LocalPlayer player, int radius) {
        Level level = player.level();
        BlockPos playerPos = player.blockPosition();
        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        int minSafeY = level.dimension() == Level.NETHER ? 1 : 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -4; dy <= radius; dy++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    if (pos.getY() < minSafeY) continue;

                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                        BlockPos above = pos.above();
                        BlockPos above2 = pos.above(2);
                        if (level.getBlockState(above).getCollisionShape(level, above).isEmpty()
                                && level.getBlockState(above2).getCollisionShape(level, above2).isEmpty()) {
                            double distSq = playerPos.distSqr(pos);
                            if (distSq < bestDistSq) {
                                bestDistSq = distSq;
                                bestPos = pos;
                            }
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    private int findItemInHotbar(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private int findItemInInventory(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private int findFirstEmptyHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }
}
