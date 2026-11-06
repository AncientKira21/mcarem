package com.ancientkira.mca.entity.ai;

import com.ancientkira.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Owns movement rules shared by every MCA villager movement mode.
 *
 * <p>Specialised controls such as {@link ArcherMoveControl} delegate here so
 * navigation-owned movement is handled consistently.</p>
 */
class MCAMoveControl extends MoveControl {
    MCAMoveControl(Mob mob) {
        super(mob);
    }

    protected final boolean isClimbNavigationActive() {
        return this.mob.getNavigation() instanceof MCAGroundPathNavigation navigation
                && (navigation.isControllingClimbable() || navigation.isControllingJump() || navigation.isControllingFluid());
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speedModifier) {
        if (this.mob.onClimbable() && this.operation == Operation.JUMPING) {
            this.operation = Operation.WAIT;
        }
        super.setWantedPosition(x, y, z, speedModifier * youngGuardSprintMultiplier(this.mob, speedModifier));
    }

    static double youngGuardSprintMultiplier(Mob mob, double requestedSpeed) {
        if (!(mob instanceof com.ancientkira.mca.entity.VillagerEntityMCA person)
                || !person.isGuard() || person.isKidnapped() || requestedSpeed < 0.6) return 1.0;
        var age = person.getAgeState();
        return switch (age) {
            case TODDLER, CHILD, TEEN -> 1.3 / age.getSpeed();
            default -> 1.0;
        };
    }

    @Override
    public void tick() {
        if (this.mob instanceof com.ancientkira.mca.entity.VillagerEntityMCA dancer && dancer.isVillageDancing()) {
            this.operation = Operation.WAIT;
            this.mob.setSpeed(0.0F);
            this.mob.setXxa(0.0F);
            this.mob.setZza(0.0F);
            this.mob.setDeltaMovement(0, this.mob.getDeltaMovement().y, 0);
            return;
        }

        if (this.mob.isInWater() && com.ancientkira.mca.entity.ai.navigation.UnderwaterNavigation.isNonSwimmingBaby(this.mob)) {
            this.operation = Operation.WAIT;
            this.mob.setJumping(false);
            this.mob.setSpeed(0);
            this.mob.setXxa(0);
            this.mob.setZza(0);
            this.mob.setYya(0);
            return;
        }

        if (this.mob.isInWater() && this.mob.isSwimming()
                && com.ancientkira.mca.entity.ai.navigation.UnderwaterNavigation.canControlWater(this.mob)) {
            if (this.operation == Operation.MOVE_TO) {
                this.mob.setJumping(false);
                this.mob.setXxa(0); this.mob.setZza(0); this.mob.setYya(0);
                double dx = this.wantedX - this.mob.getX();
                double dy = this.wantedY - this.mob.getY();
                double dz = this.wantedZ - this.mob.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 1.0E-5D) {
                    float targetYaw = (float)(Math.atan2(dz, dx) * 57.2957763671875D) - 90.0F;
                    this.mob.setYRot(this.rotlerp(this.mob.getYRot(), targetYaw, 90.0F));
                    this.mob.yBodyRot = this.mob.getYRot();
                    float speed = (float)(this.speedModifier * this.mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
                    this.mob.setSpeed(speed);
                    double swimSpeed = speed * 0.7D;
                    this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(dx / dist * swimSpeed * 0.2D, dy / dist * swimSpeed * 0.2D + (speed > 0 ? 0.005D : 0), dz / dist * swimSpeed * 0.2D));
                }
                return;
            }
        }

        // Floor-walking villagers need an upward impulse to cross a raised, dry threshold.
        if (this.operation == Operation.MOVE_TO && this.mob.horizontalCollision
                && this.mob instanceof com.ancientkira.mca.entity.VillagerEntityMCA person
                && com.ancientkira.mca.entity.ai.navigation.UnderwaterNavigation.floorWalking(person)
                && com.ancientkira.mca.entity.ai.navigation.UnderwaterNavigation.canControlWater(person)
                && this.wantedY > this.mob.getY() + 0.15 && this.wantedY <= this.mob.getY() + 1.25
                && !this.mob.level().getBlockCollisions(this.mob, this.mob.getBoundingBox().move(0, 0.3, 0)).iterator().hasNext()) {
            var movement = this.mob.getDeltaMovement();
            this.mob.setDeltaMovement(movement.x, Math.max(movement.y, 0.3), movement.z);
        }

        if (!isClimbNavigationActive()) {
            super.tick();
            return;
        }

        this.operation = Operation.WAIT;
        this.mob.setSpeed(0.0F);
        this.mob.setXxa(0.0F);
        this.mob.setZza(0.0F);
    }
}
