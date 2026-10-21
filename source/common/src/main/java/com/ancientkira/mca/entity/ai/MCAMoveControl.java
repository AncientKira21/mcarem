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
