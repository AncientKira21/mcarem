package com.ancientkira.mca.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Physical reach checked at impact, independent of cached AI visibility. */
public final class MeleeAttack {
    private MeleeAttack() {}

    public static boolean canHit(Mob attacker, LivingEntity target) {
        if (!RangedWeaponHelper.isValidAttackTarget(attacker, target)
                || !attacker.isWithinMeleeAttackRange(target)) return false;
        if (attacker instanceof com.ancientkira.mca.entity.VillagerEntityMCA person
                && person.getProfession() == com.ancientkira.mca.registry.ProfessionsMCA.VINDICATOR) {
            double dx=attacker.getX()-target.getX(), dz=attacker.getZ()-target.getZ();
            double reach=1.0 + target.getBbWidth()/2.0;
            if(dx*dx+dz*dz>reach*reach)return false;
        }
        Vec3 eye = attacker.getEyePosition();
        var box = target.getBoundingBox();
        Vec3 closest = new Vec3(
                net.minecraft.util.Mth.clamp(eye.x, box.minX, box.maxX),
                net.minecraft.util.Mth.clamp(eye.y, box.minY, box.maxY),
                net.minecraft.util.Mth.clamp(eye.z, box.minZ, box.maxZ));
        // Check collision shapes, so opaque and transparent solid blocks both stop a hit.
        // Several points allow attacks on an exposed head/body beside partial cover.
        for (Vec3 point : new Vec3[]{closest, target.getEyePosition(), box.getCenter()}) {
            if (eye.distanceToSqr(point) <= 9.0 && attacker.level().clip(new ClipContext(
                    eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker))
                    .getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }
}
