/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.target;

import combatant.client.config.values.EnumValue;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.features.relations.EntityFilters;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.player.PlayerHealthResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Centralized target selection and scoring helpers.
 */
public enum TargetingUtil {
    ;

    public enum HoleStatus {
        OPEN,
        OBSIDIAN,
        BEDROCK,
        BURROWED
    }

    public static LivingEntity findBestTarget(Minecraft mc, TargetingSettings settings) {
        List<LivingEntity> list = findTargets(mc, settings);
        if (list.isEmpty()) return null;
        return list.get(0);
    }

    /**
     * Resolves the shared TargetManager target for target-oriented UI/render features.
     * Source selection and the optional player-only filter live here so consumers do not
     * drift into slightly different target semantics.
     */
    public static LivingEntity resolveManagedTarget(boolean includeCrosshair, boolean playersOnly) {
        LivingEntity target = TargetManager.getTarget(includeCrosshair);
        if (target == null) return null;
        if (playersOnly && !(target instanceof Player)) return null;
        return target;
    }

    public static List<LivingEntity> findTargets(Minecraft mc, TargetingSettings settings) {
        if (mc == null || mc.level == null || mc.player == null || mc.getConnection() == null || settings == null) return List.of();
        if (!mc.player.isAlive() || mc.player.isRemoved() || mc.player.isSpectator()) return List.of();

        double range = settings.range();
        Vec3 center = mc.player.position();
        AABB search = new AABB(
                center.x - range, center.y - range, center.z - range,
                center.x + range, center.y + range, center.z + range
        );

        List<Entity> entities = mc.level.getEntities(mc.player, search, e -> e instanceof LivingEntity);
        List<LivingEntity> out = new ArrayList<>();

        for (Entity e : entities) {
            LivingEntity living = (LivingEntity) e;
            if (!isValidCombatTarget(living)) continue;
            if (settings.playersOnly() && !(living instanceof Player)) continue;
            if (settings.visibleOnly() && !mc.player.hasLineOfSight(living)) continue;

            if (settings.ignoreEntities()) {
                var id = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
                if (id != null && EntityFilters.get().isIgnoredEntity(id.toString())) {
                    continue;
                }
            }

            if (living instanceof Player p) {
                CategoryType type = CategoryRules.determine(p.getGameProfile().name());
                if (type == CategoryType.BEDWARS_SELF || type == CategoryType.FRIEND) continue;
                if (settings.ignoreStaff() && type == CategoryType.STAFF) continue;
                if (settings.ignoreEnemies() && (type == CategoryType.ENEMY || type == CategoryType.BEDWARS_ENEMY))
                    continue;
                if (settings.ignoreNaked() && isNaked(p)) continue;
            }

            double distSq = distanceToEntityBoxSq(mc.player.getEyePosition(), living);
            if (distSq > range * range) continue;

            float fov = settings.fov();
            if (fov < 180f) {
                float angle = RotationUtil.directionAngleTo(mc.player, living);
                if (angle > fov * 0.5f) continue;
            }

            out.add(living);
        }

        out.sort(buildComparator(settings.priority(), mc));
        return out;
    }

    /**
     * Finds the single highest-priority player target matching criteria.
     */
    public static Player findBestTarget(Player self, Level level, double range, TargetPriority priority) {
        List<Player> players = findPlayers(self, level, range, priority);
        return players.isEmpty() ? null : players.get(0);
    }

    /**
     * Finds and sorts candidate players within range using centralized filtering and scoring.
     */
    public static List<Player> findPlayers(Player self, Level level, double range, TargetPriority priority) {
        if (level == null) return List.of();
        List<Player> valid = new ArrayList<>();
        for (Player other : level.players()) {
            if (!isValidPlayerTarget(self, other, range)) continue;
            valid.add(other);
        }
        if (valid.isEmpty()) return valid;
        valid.sort(buildPlayerComparator(priority, self, level));
        return valid;
    }

    /**
     * Centralized player candidate filtering:
     * - Alive and not removed
     * - Not spectator, not self, not sleeping, not invulnerable
     * - Within specified distance
     * - Automatically ignores friends and teammates via CategoryRules
     */
    public static boolean isValidPlayerTarget(Player self, Player target, double range) {
        if (target == null || target == self) return false;
        if (!isValidCombatTarget(target)) return false;
        if (self != null && self.distanceTo(target) > range) return false;
        return true;
    }

    public static boolean isValidPlayerTarget(Player target) {
        return target != null && isValidCombatTarget(target);
    }

    /**
     * Centralized combat target validation.
     * Integrates CategoryRules.determine automatically to ignore friends and teammates.
     */
    public static boolean isValidCombatTarget(LivingEntity living) {
        if (living == null) return false;
        if (living instanceof AbstractClientPlayer) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getConnection() == null) return false;
        }
        if (!living.isAlive() || living.isRemoved()) return false;
        if (living.isSpectator()) return false;
        if (!living.isPickable()) return false;
        if (!living.isAttackable() || living.isInvulnerable()) return false;

        if (living instanceof Player player) {
            if (player.isSleeping()) return false;
            CategoryType type = CategoryRules.determine(player.getGameProfile().name());
            if (type == CategoryType.FRIEND || type == CategoryType.BEDWARS_SELF) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a player is burrowed (feet block inside a solid or blast-resistant block).
     */
    public static boolean isBurrowed(Player player, Level level) {
        if (player == null || level == null) return false;
        BlockPos feet = player.blockPosition();
        BlockState state = level.getBlockState(feet);
        if (state.isAir() || state.canBeReplaced()) return false;
        return isBlastResistant(state) || state.blocksMotion();
    }

    /**
     * Checks if all 4 horizontal blocks surrounding the player's feet are blast resistant.
     */
    public static boolean isSurrounded(Player player, Level level) {
        if (player == null || level == null) return false;
        BlockPos feet = player.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos surround = feet.relative(dir);
            BlockState state = level.getBlockState(surround);
            if (!isBlastResistant(state)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines whether a block state is blast-resistant (unbreakable or obsidian-grade).
     */
    public static boolean isBlastResistant(BlockState state) {
        if (state == null || state.isAir()) return false;
        return state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.BEDROCK)
                || state.is(Blocks.RESPAWN_ANCHOR)
                || state.is(Blocks.NETHERITE_BLOCK)
                || state.is(Blocks.ANVIL)
                || state.is(Blocks.CHIPPED_ANVIL)
                || state.is(Blocks.DAMAGED_ANVIL)
                || state.getBlock().getExplosionResistance() >= 600.0f;
    }

    /**
     * Checks if the player's bounding box intersects any cobweb block or if the player is standing in soul sand.
     *
     * @param player the player to check
     * @param level the level context
     * @return true if webbed or standing in soul sand
     */
    public static boolean isWebbed(Player player, Level level) {
        if (player == null || level == null) return false;
        AABB box = player.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.COBWEB) && box.intersects(new AABB(pos))) {
                        return true;
                    }
                    if (state.is(Blocks.SOUL_SAND) && box.intersects(new AABB(pos))) {
                        return true;
                    }
                }
            }
        }
        BlockPos feet = player.blockPosition();
        return level.getBlockState(feet).is(Blocks.SOUL_SAND)
                || level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ())).is(Blocks.SOUL_SAND);
    }

    /**
     * Checks if the player's bounding box intersects any solid block collision shapes.
     *
     * @param player the player to check
     * @param level the level context
     * @return true if intersecting a solid block's collision shape
     */
    public static boolean isPhasing(Player player, Level level) {
        if (player == null || level == null) return false;
        AABB box = player.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && state.blocksMotion()) {
                        VoxelShape collisionShape = state.getCollisionShape(level, pos);
                        if (!collisionShape.isEmpty()) {
                            for (AABB blockBox : collisionShape.toAabbs()) {
                                if (box.intersects(blockBox.move(x, y, z))) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Resolves the player's defensive hole status (burrowed, bedrock hole, obsidian hole, open).
     * Evaluates the 4 horizontal neighbor blocks at the player's feet (north, south, east, west)
     * and the floor block (below).
     *
     * @param player the player to evaluate
     * @param level the level context
     * @return the resolved {@link HoleStatus}
     */
    public static HoleStatus getHoleStatus(Player player, Level level) {
        if (player == null || level == null) return HoleStatus.OPEN;
        if (isBurrowed(player, level)) return HoleStatus.BURROWED;

        BlockPos feet = player.blockPosition();
        BlockPos[] surrounding = new BlockPos[]{
                feet.north(),
                feet.south(),
                feet.east(),
                feet.west(),
                feet.below()
        };

        boolean allBedrock = true;
        for (BlockPos pos : surrounding) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.BEDROCK)) {
                allBedrock = false;
            }
            if (!isBlastResistant(state)) {
                return HoleStatus.OPEN;
            }
        }

        if (allBedrock) {
            return HoleStatus.BEDROCK;
        }
        return HoleStatus.OBSIDIAN;
    }

    /**
     * Returns an integer vulnerability score for hole status (0 = open/vulnerable, 3 = burrowed).
     */
    public static int getHoleStatusScore(LivingEntity entity, Level level) {
        if (!(entity instanceof Player player) || level == null) return 0;
        return switch (getHoleStatus(player, level)) {
            case OPEN -> 0;
            case OBSIDIAN -> 1;
            case BEDROCK -> 2;
            case BURROWED -> 3;
        };
    }

    /**
     * Computes the remaining total durability of all equipped armor pieces.
     * Lower durability indicates lower protection (higher priority target).
     */
    public static double getArmorDurabilityScore(LivingEntity entity) {
        if (entity == null) return 0.0;
        double totalDurability = 0.0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (stack.isDamageableItem()) {
                totalDurability += Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            } else {
                totalDurability += 500.0;
            }
        }
        return totalDurability;
    }

    /**
     * Calculates an intelligent threat score for a target:
     * - Proximity and facing local player
     * - Movement towards local player
     * - Held weapons (crystals, anchors, swords, axes, maces, bows)
     * - Armor tier and quantity
     * - Active combat state (hurt time, swinging)
     */
    public static double calculateThreat(Player self, LivingEntity target) {
        if (target == null) return 0.0;
        double threat = 0.0;

        // Proximity (closer is more immediate danger)
        if (self != null) {
            double dist = self.distanceTo(target);
            threat += Math.max(0.0, 30.0 - dist * 3.0);

            // Facing self
            Vec3 lookVec = target.getViewVector(1.0f).normalize();
            Vec3 toSelf = self.getEyePosition().subtract(target.getEyePosition()).normalize();
            double dot = lookVec.dot(toSelf);
            if (dot > 0.7) {
                threat += dot * 20.0;
            }

            // Movement towards self
            Vec3 delta = target.getDeltaMovement();
            if (delta.lengthSqr() > 0.01) {
                double moveDot = delta.normalize().dot(toSelf);
                if (moveDot > 0.5) {
                    threat += moveDot * 10.0;
                }
            }
        }

        // Entity type
        if (target instanceof Player player) {
            threat += 25.0;

            // Held weapons
            ItemStack main = player.getMainHandItem();
            if (!main.isEmpty()) {
                Item item = main.getItem();
                if (main.is(ItemTags.SWORDS) || main.is(ItemTags.AXES) || item instanceof MaceItem) {
                    threat += 25.0;
                } else if (item == Items.END_CRYSTAL || item == Items.RESPAWN_ANCHOR) {
                    threat += 30.0;
                } else if (item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT) {
                    threat += 15.0;
                }
            }

            ItemStack off = player.getOffhandItem();
            if (!off.isEmpty()) {
                Item item = off.getItem();
                if (item == Items.TOTEM_OF_UNDYING) {
                    threat += 5.0;
                } else if (item == Items.END_CRYSTAL || item == Items.RESPAWN_ANCHOR) {
                    threat += 20.0;
                }
            }

            // Armor pieces
            int armorPieces = 0;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (!player.getItemBySlot(slot).isEmpty()) armorPieces++;
            }
            threat += armorPieces * 3.0;
        } else if (target instanceof Monster) {
            threat += 10.0;
        }

        // Combat activity
        if (target.hurtTime > 0) {
            threat += 5.0;
        }
        if (target.swinging) {
            threat += 10.0;
        }

        return threat;
    }

    /**
     * Calculates a threat rating from 0 to 100 based on:
     * - Weapon in main hand (Mace = +35, End Crystal = +30, Netherite/Diamond Sword/Axe = +25, Bow = +15, Wind Charge = +20)
     * - Armor tier (Netherite = +25, Diamond = +20, Iron = +10, Naked = +0)
     * - Active effects (Strength = +15, Speed = +10, Resistance = +10)
     * - Aim orientation toward self (target looking within 30 degrees of self = +15)
     *
     * @param target the target player to evaluate
     * @param self the local player reference
     * @return threat rating from 0 to 100
     */
    public static int calculateThreatLevel(Player target, Player self) {
        if (target == null) return 0;
        int threat = 0;

        // Weapon in main hand
        ItemStack mainHand = target.getMainHandItem();
        if (!mainHand.isEmpty()) {
            Item item = mainHand.getItem();
            if (item instanceof MaceItem || item == Items.MACE) {
                threat += 35;
            } else if (item == Items.END_CRYSTAL) {
                threat += 30;
            } else if (item == Items.NETHERITE_SWORD || item == Items.NETHERITE_AXE
                    || item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE) {
                threat += 25;
            } else if (item == Items.WIND_CHARGE) {
                threat += 20;
            } else if (item == Items.BOW || item == Items.CROSSBOW) {
                threat += 15;
            }
        }

        // Armor tier
        boolean hasNetherite = false;
        boolean hasDiamond = false;
        boolean hasIron = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = target.getItemBySlot(slot);
            if (armor.isEmpty()) continue;
            Item item = armor.getItem();
            if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE
                    || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS) {
                hasNetherite = true;
            } else if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE
                    || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS) {
                hasDiamond = true;
            } else if (item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE
                    || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS) {
                hasIron = true;
            }
        }
        if (hasNetherite) {
            threat += 25;
        } else if (hasDiamond) {
            threat += 20;
        } else if (hasIron) {
            threat += 10;
        }

        // Active effects
        if (target.hasEffect(MobEffects.STRENGTH)) {
            threat += 15;
        }
        if (target.hasEffect(MobEffects.SPEED)) {
            threat += 10;
        }
        if (target.hasEffect(MobEffects.RESISTANCE)) {
            threat += 10;
        }

        // Aim orientation toward self
        if (self != null) {
            Vec3 lookVec = target.getViewVector(1.0f).normalize();
            Vec3 toSelf = self.getEyePosition().subtract(target.getEyePosition());
            if (toSelf.lengthSqr() > 1.0E-6) {
                double dot = lookVec.dot(toSelf.normalize());
                if (dot >= Math.cos(Math.toRadians(30.0))) {
                    threat += 15;
                }
            }
        }

        return Math.min(100, Math.max(0, threat));
    }

    public static boolean isNaked(Player player) {
        if (player == null) return false;
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private static Comparator<LivingEntity> buildComparator(TargetPriority priority, Minecraft mc) {
        if (priority == null) priority = TargetPriority.DISTANCE;
        switch (priority) {
            case HEALTH:
                return Comparator.comparingDouble(PlayerHealthResolver::totalHealth);
            case ARMOR:
                return Comparator.comparingDouble(TargetingUtil::getArmorDurabilityScore);
            case THREAT:
                return (a, b) -> Double.compare(calculateThreat(mc.player, b), calculateThreat(mc.player, a));
            case HOLE:
                return Comparator.comparingInt(e -> getHoleStatusScore(e, mc.level));
            case HURT_TIME:
                return Comparator.comparingInt(e -> e.hurtTime);
            case ANGLE:
                return Comparator.comparingDouble(e -> RotationUtil.directionAngleTo(mc.player, e));
            case AGE:
                return Comparator.comparingInt(e -> -e.tickCount);
            case DISTANCE:
            default:
                return Comparator.comparingDouble(e -> distanceToEntityBoxSq(mc.player.getEyePosition(), e));
        }
    }

    private static Comparator<Player> buildPlayerComparator(TargetPriority priority, Player self, Level level) {
        if (priority == null) priority = TargetPriority.DISTANCE;
        switch (priority) {
            case HEALTH:
                return Comparator.comparingDouble(PlayerHealthResolver::totalHealth);
            case ARMOR:
                return Comparator.comparingDouble(TargetingUtil::getArmorDurabilityScore);
            case THREAT:
                return (a, b) -> Double.compare(calculateThreat(self, b), calculateThreat(self, a));
            case HOLE:
                return Comparator.comparingInt(p -> getHoleStatusScore(p, level));
            case HURT_TIME:
                return Comparator.comparingInt(p -> p.hurtTime);
            case ANGLE:
                return Comparator.comparingDouble(p -> self != null ? RotationUtil.directionAngleTo(self, p) : 0.0);
            case AGE:
                return Comparator.comparingInt(p -> -p.tickCount);
            case DISTANCE:
            default:
                return Comparator.comparingDouble(p -> self != null ? self.distanceToSqr(p) : 0.0);
        }
    }

    public static double distanceToEntityBoxSq(Vec3 from, Entity e) {
        AABB box = e.getBoundingBox();
        return boxDistanceSq(box, from);
    }

    public static double distanceToBoxSq(Vec3 from, AABB box) {
        return boxDistanceSq(box, from);
    }

    private static double boxDistanceSq(AABB box, Vec3 p) {
        double dx = 0.0;
        if (p.x < box.minX) dx = box.minX - p.x;
        else if (p.x > box.maxX) dx = p.x - box.maxX;

        double dy = 0.0;
        if (p.y < box.minY) dy = box.minY - p.y;
        else if (p.y > box.maxY) dy = p.y - box.maxY;

        double dz = 0.0;
        if (p.z < box.minZ) dz = box.minZ - p.z;
        else if (p.z > box.maxZ) dz = p.z - box.maxZ;

        return dx * dx + dy * dy + dz * dz;
    }

    public enum TargetPriority implements EnumValue.IdProvider {
        DISTANCE("distance"),
        HEALTH("health"),
        ARMOR("armor"),
        THREAT("threat"),
        HOLE("hole"),
        HURT_TIME("hurt_time"),
        ANGLE("angle"),
        AGE("age");

        private final String id;

        TargetPriority(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public record TargetingSettings(
            double range,
            float fov,
            boolean playersOnly,
            boolean ignoreFriends,
            boolean ignoreStaff,
            boolean ignoreEnemies,
            boolean ignoreNaked,
            boolean ignoreEntities,
            boolean visibleOnly,
            TargetPriority priority
    ) {
    }
}
