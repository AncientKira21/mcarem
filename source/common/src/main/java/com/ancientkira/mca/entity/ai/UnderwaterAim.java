package com.ancientkira.mca.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Aims at the actual swimming hitbox, with lead and the projectile's native water drag. */
public final class UnderwaterAim {
    private UnderwaterAim() {}
    public static Vec3 direction(Vec3 origin, LivingEntity target, double speed, double drag) {
        Vec3 delta = target.getBoundingBox().getCenter().subtract(origin);
        double horizontal = delta.horizontalDistance();
        double fraction = 1 - horizontal * (1 - drag) / speed;
        double ticks = fraction > 0 ? Math.log(fraction) / Math.log(drag) : 12;
        ticks = Math.clamp(ticks, 1, 12);
        Vec3 motion = target.getDeltaMovement();
        delta = delta.add(Math.clamp(motion.x, -.25, .25) * ticks,
                Math.clamp(motion.y, -.15, .15) * ticks, Math.clamp(motion.z, -.25, .25) * ticks);
        horizontal = delta.horizontalDistance();
        double angle = ArrowTrajectory.elevation(horizontal, delta.y, speed, drag);
        if (horizontal < .001) return new Vec3(0, Math.signum(delta.y), 0);
        return new Vec3(delta.x / horizontal * Math.cos(angle), Math.sin(angle), delta.z / horizontal * Math.cos(angle));
    }
}
