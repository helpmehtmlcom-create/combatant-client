/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.draggable.impl.Itemizer;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.target.TargetingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.function.Predicate;

@ModuleInfo(
        id = "offhand",
        displayName = "Offhand",
        aliases = {"AutoTotem"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.offhand.description")
public final class Offhand extends Module {
    private static final String THREAT_BROKEN_ARMOR = "broken_armor";
    private static final String THREAT_MACE = "mace";
    private static final String THREAT_PROJECTILES = "projectiles";

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<SafeItem> safeItem =
            enumSetting("offhand_safe_item", "safe_item", SafeItem.PREVIOUS, SafeItem.values());

    private final NumberValue<Float> healthThreshold =
            numCommon(
                    "autototem_threshold",
                    "health_threshold",
                    CommonSettingSchemas.PLAYER_HEALTH_THRESHOLD,
                    10.0f,
                    1.0f,
                    40.0f
            );

    private final NumberValue<Float> elytraHealth =
            numCommon(
                    "autototem_elytra_health",
                    "elytra_health",
                    CommonSettingSchemas.PLAYER_ELYTRA_HEALTH,
                    8.5f,
                    1.0f,
                    40.0f
            );

    private final NumberValue<Float> crystalDistance =
            numCommon(
                    "autototem_crystal_distance",
                    "crystal_distance",
                    CommonSettingSchemas.COMBAT_CRYSTAL_DISTANCE,
                    4.0f,
                    1.0f,
                    6.0f
            );

    private final BooleanValue fallCheck =
            boolCommon(
                    "autototem_fall_check",
                    "fall_check",
                    CommonSettingSchemas.PLAYER_FALL_CHECK,
                    true
            );

    private final BooleanValue saveTaliks =
            boolCommon(
                    "autototem_save_taliks",
                    "save_taliks",
                    CommonSettingSchemas.ITEMS_SAVE_UNENCHANTED,
                    true
            );

    private final BooleanValue returnItem =
            boolCommon(
                    "autototem_return_item",
                    "return_item",
                    CommonSettingSchemas.INVENTORY_RESTORE_ITEM,
                    true
            );

    private final BooleanMapValue threats = group(
            "offhand_threats",
            "threats",
            new LinkedHashMap<>() {{
                put(THREAT_BROKEN_ARMOR, true);
                put(THREAT_MACE, true);
                put(THREAT_PROJECTILES, true);
            }}
    );

    private ItemStack previousOffhand = ItemStack.EMPTY;
    private int previousOffhandSlot = -1;
    private boolean usingTotem;
    private boolean offhandSwapPending;
    private int forcedTotemUntilTick = -1;

    @Override
    public String getConfigName() {
        return "autototem";
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.isPaused()) {
            offhandSwapPending = false;
            forcedTotemUntilTick = -1;
            return;
        }

        if (offhandSwapPending) return;

        if (shouldHoldTotem(player, player.getHealth() + player.getAbsorptionAmount())) {
            equipTotem(player);
            return;
        }

        if (usingTotem && returnItem.get() && safeItem.get() == SafeItem.PREVIOUS) {
            restorePrevious(player);
            return;
        }

        SafeItem preferred = safeItem.get();
        if (preferred != SafeItem.PREVIOUS && preferred != SafeItem.NONE) {
            equipPreferred(player, preferred);
        } else if (usingTotem && preferred == SafeItem.NONE) {
            clearRestoreState();
        }
    }

    @Override
    public void onDisable() {
        offhandSwapPending = false;
        forcedTotemUntilTick = -1;
        clearRestoreState();
    }

    public boolean shouldHoldTotemNow(LocalPlayer player) {
        if (!isEnabled() || player == null) return false;
        return shouldHoldTotem(player, player.getHealth() + player.getAbsorptionAmount());
    }

    public boolean isTotemPriorityActive(LocalPlayer player) {
        return shouldHoldTotemNow(player) || offhandSwapPending;
    }

    public boolean canProvideTotemNow(LocalPlayer player) {
        if (!isEnabled()) return false;
        if (player == null || mc.gameMode == null) return false;
        if (isTotemInOffhand(player)) return true;
        if (offhandSwapPending) return false;
        return findTotemSlot(player) != -1;
    }

    public boolean ensureTotemForDanger(LocalPlayer player) {
        if (!canProvideTotemNow(player)) return false;
        if (player != null) {
            forcedTotemUntilTick = Math.max(forcedTotemUntilTick, player.tickCount + 3);
        }
        if (isTotemInOffhand(player)) return true;

        int slot = findTotemSlot(player);
        if (slot == -1) return false;

        rememberPreviousOffhand(player, slot);
        return swapTotemToOffhand(player, slot);
    }

    public boolean isTotemSwapPending() {
        return offhandSwapPending;
    }

    private boolean shouldHoldTotem(LocalPlayer player, float health) {
        if (player.tickCount <= forcedTotemUntilTick) return true;
        if (player.isFallFlying() && health <= elytraHealth.get()) return true;
        if (health <= healthThreshold.get()) return true;
        if (fallCheck.get() && player.fallDistance > 10.0f) return true;
        if (getClosestCrystalDistance(player) <= crystalDistance.get()) return true;
        if (threats.get(THREAT_BROKEN_ARMOR) && hasBrokenArmor(player) && hasNearbyCombatPlayer(player, 10.0)) return true;
        if (threats.get(THREAT_MACE) && hasMaceThreat(player)) return true;
        return threats.get(THREAT_PROJECTILES) && hasProjectileThreat(player);
    }

    private boolean hasBrokenArmor(LocalPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                || player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private boolean hasNearbyCombatPlayer(LocalPlayer player, double range) {
        double rangeSq = range * range;
        for (Player other : mc.level.players()) {
            if (!isCombatThreatPlayer(player, other)) continue;
            if (other.distanceToSqr(player) <= rangeSq) return true;
        }
        return false;
    }

    private boolean hasMaceThreat(LocalPlayer player) {
        for (Player other : mc.level.players()) {
            if (!isCombatThreatPlayer(player, other)) continue;
            if (!other.getMainHandItem().is(Items.MACE) && !other.getOffhandItem().is(Items.MACE)) continue;
            if (other.getY() <= player.getY()) continue;
            if (other.distanceToSqr(player) > 49.0) continue;
            if (other.getDeltaMovement().y < 0.0 && other.fallDistance > 3.0f) return true;
        }
        return false;
    }

    private boolean isCombatThreatPlayer(LocalPlayer self, Player other) {
        if (other == null || other == self || !other.isAlive() || other.isSpectator()) return false;
        CategoryType type = CategoryRules.determine(other.getGameProfile().name());
        return type != CategoryType.FRIEND && type != CategoryType.STAFF && type != CategoryType.BEDWARS_SELF;
    }

    private boolean hasProjectileThreat(LocalPlayer player) {
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Projectile projectile)) continue;
            if (projectile.getOwner() == player) continue;

            Vec3 velocity = projectile.getDeltaMovement();
            double speedSq = velocity.lengthSqr();
            if (speedSq < 1.0E-7) continue;

            Vec3 from = projectile.position();
            Vec3 toPlayer = player.getBoundingBox().getCenter().subtract(from);
            if (velocity.dot(toPlayer) <= 0.0) continue;

            Vec3 direction = velocity.normalize();
            double along = toPlayer.dot(direction);
            if (along < 0.0 || along > 16.0) continue;

            Vec3 closest = from.add(direction.scale(along));
            if (TargetingUtil.distanceToBoxSq(closest, player.getBoundingBox().inflate(0.35)) <= 0.09) {
                return true;
            }
        }
        return false;
    }

    private void equipTotem(LocalPlayer player) {
        if (isTotemInOffhand(player)) {
            usingTotem = true;
            return;
        }

        int slot = findTotemSlot(player);
        if (slot == -1) return;

        rememberPreviousOffhand(player, slot);
        swapTotemToOffhand(player, slot);
    }

    private boolean swapTotemToOffhand(LocalPlayer player, int slot) {
        if (player == null || mc.gameMode == null || offhandSwapPending) return false;

        offhandSwapPending = true;
        ItemStack displayStack = player.getInventory().getItem(slot).copy();
        boolean accepted = InventorySwap.INSTANCE.swapInventoryToOffhand(slot, () -> {
            usingTotem = true;
            offhandSwapPending = false;
            Itemizer.showAutoTotem(displayStack);
        });
        if (!accepted) offhandSwapPending = false;
        return accepted;
    }

    private void equipPreferred(LocalPlayer player, SafeItem preferred) {
        if (preferred.matches(player.getOffhandItem())) {
            if (usingTotem) clearRestoreState();
            return;
        }

        int slot = find(player, preferred::matches);
        if (slot == -1) return;

        requestOffhandSwap(slot, this::clearRestoreState);
    }

    private void restorePrevious(LocalPlayer player) {
        if (previousOffhand.isEmpty()) {
            clearRestoreState();
            return;
        }

        if (!player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            clearRestoreState();
            return;
        }

        int restoreSlot = resolveRestoreSlot(player);
        if (restoreSlot == -1) {
            clearRestoreState();
            return;
        }

        requestOffhandSwap(restoreSlot, this::clearRestoreState);
    }

    private void rememberPreviousOffhand(LocalPlayer player, int totemSlot) {
        if (usingTotem) return;

        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            previousOffhand = ItemStack.EMPTY;
            previousOffhandSlot = -1;
            return;
        }

        previousOffhand = offhand.copy();
        previousOffhandSlot = totemSlot;
    }

    private boolean requestOffhandSwap(int slot, Runnable afterSwap) {
        if (offhandSwapPending) return false;

        offhandSwapPending = true;
        boolean accepted = InventorySwap.INSTANCE.swapInventoryToOffhand(slot, () -> {
            if (afterSwap != null) afterSwap.run();
            offhandSwapPending = false;
        });
        if (!accepted) offhandSwapPending = false;
        return accepted;
    }

    private int findTotemSlot(LocalPlayer player) {
        if (saveTaliks.get()) {
            int nonEnchanted = find(player, stack -> stack.is(Items.TOTEM_OF_UNDYING) && !stack.isEnchanted());
            if (nonEnchanted != -1) return nonEnchanted;
        }
        return find(player, Items.TOTEM_OF_UNDYING);
    }

    private int find(LocalPlayer player, Item item) {
        return find(player, stack -> stack.is(item));
    }

    private int find(LocalPlayer player, Predicate<ItemStack> predicate) {
        if (player == null || predicate == null) return -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && predicate.test(stack)) return i;
        }
        return -1;
    }

    private int findItem(LocalPlayer player, ItemStack target) {
        if (player == null || target == null || target.isEmpty()) return -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, target)) return i;
        }
        return -1;
    }

    private int resolveRestoreSlot(LocalPlayer player) {
        if (previousOffhandSlot >= 0 && previousOffhandSlot < 36) {
            ItemStack stack = player.getInventory().getItem(previousOffhandSlot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, previousOffhand)) {
                return previousOffhandSlot;
            }
        }
        return findItem(player, previousOffhand);
    }

    private double getClosestCrystalDistance(LocalPlayer player) {
        double minDist = Double.MAX_VALUE;
        double range = crystalDistance.get();
        for (EndCrystal crystal : mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(range),
                crystal -> crystal != null && !crystal.isRemoved()
        )) {
            double dist = player.position().distanceTo(crystal.position());
            if (dist < minDist) minDist = dist;
        }
        return minDist;
    }

    private boolean isTotemInOffhand(LocalPlayer player) {
        return player != null && player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private void clearRestoreState() {
        previousOffhand = ItemStack.EMPTY;
        previousOffhandSlot = -1;
        usingTotem = false;
    }

    public enum SafeItem implements EnumValue.IdProvider {
        PREVIOUS("previous"),
        TOTEM("totem"),
        GAPPLE("gapple"),
        CRYSTAL("crystal"),
        SHIELD("shield"),
        NONE("none");

        private final String id;

        SafeItem(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        private boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            return switch (this) {
                case PREVIOUS, NONE -> false;
                case TOTEM -> stack.is(Items.TOTEM_OF_UNDYING);
                case GAPPLE -> stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.GOLDEN_APPLE);
                case CRYSTAL -> stack.is(Items.END_CRYSTAL);
                case SHIELD -> stack.is(Items.SHIELD);
            };
        }
    }
}
