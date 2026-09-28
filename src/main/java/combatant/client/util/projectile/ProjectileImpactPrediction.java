/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.projectile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public enum ProjectileImpactPrediction {
    ;

    public static Impact predict(Projectile projectile, int maxTicks) {
        if (projectile == null || maxTicks <= 0) return null;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return null;

        TrajectoryInfo.Typed typed = TrajectoryData.getTrajectoryInfoForEntity(projectile);
        if (typed == null) return null;

        TrajectoryInfo info = typed.info();
        Vec3 pos = projectile.position();
        Vec3 velocity = projectile.getDeltaMovement();

        for (int tick = 1; tick <= maxTicks; tick++) {
            Vec3 start = pos;
            double drag = projectile.isInWater() ? info.dragInWater() : info.drag();
            velocity = velocity.scale(drag).add(0.0, -info.gravity(), 0.0);
            Vec3 end = start.add(velocity);

            HitResult blockHit = level.clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    projectile
            ));

            EntityHitResult entityHit = findEntityHit(projectile, start, end, info.hitboxRadius());
            boolean hasBlockHit = blockHit.getType() != HitResult.Type.MISS;
            boolean useEntityHit = entityHit != null
                    && (!hasBlockHit || start.distanceToSqr(entityHit.getLocation()) <= start.distanceToSqr(blockHit.getLocation()));

            if (useEntityHit) {
                return new Impact(entityHit.getLocation(), tick, entityHit.getEntity(), false);
            }
            if (hasBlockHit) {
                return new Impact(blockHit.getLocation(), tick, null, true);
            }

            pos = end;
        }

        return new Impact(pos, maxTicks, null, false);
    }

    private static EntityHitResult findEntityHit(Projectile projectile, Vec3 start, Vec3 end, double radius) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() <= 1.0E-9) return null;

        AABB box = projectile.getBoundingBox()
                .move(start.subtract(projectile.position()))
                .expandTowards(direction)
                .inflate(Math.max(0.3, radius));

        Entity owner = projectile.getOwner();
        return net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                projectile,
                start,
                end,
                box,
                entity -> entity != projectile
                        && entity != owner
                        && entity.isAlive()
                        && entity.isPickable(),
                direction.lengthSqr()
        );
    }

    public record Impact(Vec3 position, int ticks, Entity hitEntity, boolean blockHit) {
    }
}
