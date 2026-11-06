package com.ancientkira.mca.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/** A lit creeper is a panic stimulus only where its blast can threaten the observer. */
public final class CreeperThreat {
    private CreeperThreat() {}
    public static boolean explodingNear(Creeper creeper, LivingEntity observer) {
        if (!creeper.isAlive() || !(creeper.isIgnited() || creeper.getSwelling(0) > 0)) return false;
        double radius = creeper.isPowered() ? 12 : 6;
        var box = observer.getBoundingBox();
        Vec3 blast = creeper.position();
        if (distanceSquared(blast, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ) <= radius * radius) return true;
        // A falling lit creeper can enter blast range shortly; a suspended one cannot.
        if (creeper.getDeltaMovement().y >= -0.08) return false;
        Vec3 falling = blast.add(creeper.getDeltaMovement().scale(10)).add(0, -2.2, 0);
        return distanceSquared(falling, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ) <= radius * radius;
    }
    private static double distanceSquared(Vec3 point, double x0, double y0, double z0, double x1, double y1, double z1) {
        double x = point.x - Math.clamp(point.x, x0, x1);
        double y = point.y - Math.clamp(point.y, y0, y1);
        double z = point.z - Math.clamp(point.z, z0, z1);
        return x*x + y*y + z*z;
    }
}
