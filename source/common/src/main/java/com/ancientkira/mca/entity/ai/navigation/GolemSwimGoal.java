package com.ancientkira.mca.entity.ai.navigation;

import com.ancientkira.mca.server.UnderwaterVillageSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

/** Lets both vanilla and MCA golems pursue enemies through water in three dimensions. */
public final class GolemSwimGoal extends Goal {
    private final IronGolem golem;
    private Vec3 waypoint;
    private BlockPos destination;
    private int nextRoute, nextAttack;

    public GolemSwimGoal(IronGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override public boolean canUse() {
        return golem.isInWater() && !golem.isPassenger() && !golem.isNoAi();
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        destination = golem.getNavigation().getTargetPos();
        golem.getNavigation().stop();
        nextRoute = 0;
        golem.setSwimming(true);
    }
    @Override public void stop() {
        golem.setSwimming(false);
        waypoint = null;
        destination = null;
        golem.setJumping(false);
    }
    @Override public void tick() {
        golem.setJumping(false);
        var enemy = golem.getTarget();
        if (enemy != null && enemy.isAlive()) {
            destination = enemy.blockPosition();
            golem.getLookControl().setLookAt(enemy, 30, 30);
            double reach = golem.getBbWidth() + enemy.getBbWidth();
            if (golem.distanceToSqr(enemy) <= reach * reach && golem.hasLineOfSight(enemy)) {
                if (golem.tickCount >= nextAttack && golem.level() instanceof ServerLevel world) {
                    nextAttack = golem.tickCount + 20;
                    golem.doHurtTarget(world, enemy);
                }
            }
        } else if (!UnderwaterVillageSupport.isAroundUnderwaterVillage(golem.level(), golem.blockPosition())) {
            // Outside an underwater settlement, return toward the surface rather than sink indefinitely.
            BlockPos pos = golem.blockPosition();
            for (int up = 1; up <= 16; up++) {
                BlockPos above = pos.above(up);
                if (!golem.level().hasChunkAt(above)) break;
                if (!golem.level().getFluidState(above).is(FluidTags.WATER)) { destination = above; break; }
            }
        }
        if (destination == null) return;
        if (golem.tickCount >= nextRoute || waypoint != null && golem.position().distanceToSqr(waypoint) < 0.4) {
            nextRoute = golem.tickCount + 10;
            waypoint = UnderwaterNavigation.waypoint(golem, destination);
        }
        if (waypoint == null) return;
        Vec3 delta = waypoint.subtract(golem.position());
        if (delta.lengthSqr() < 0.02) return;
        Vec3 thrust = delta.normalize().scale(0.035);
        golem.setDeltaMovement(golem.getDeltaMovement().add(thrust.x, thrust.y + 0.005, thrust.z));
        golem.setYRot((float) (Math.atan2(delta.z, delta.x) * 180 / Math.PI) - 90);
        golem.yBodyRot = golem.getYRot();
    }
}
