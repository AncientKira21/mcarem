package com.ancientkira.mca.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;

public class ArcherMoveControl extends MCAMoveControl {
    private boolean emergencyFleeing;
    private StrafeResult lastStrafeResult = StrafeResult.NONE;
    private boolean archerStrafeRequested;

    public ArcherMoveControl(Mob mob) {
        super(mob);
    }

    public void setEmergencyFleeing(boolean emergencyFleeing) {
        this.emergencyFleeing = emergencyFleeing;
    }

    public boolean isEmergencyFleeing() {
        return this.emergencyFleeing;
    }

    public boolean wasLastStrafeBlocked() {
        return this.lastStrafeResult == StrafeResult.BLOCKED;
    }

    public String getLastStrafeResult() {
        return this.lastStrafeResult.debugName;
    }

    @Override
    public void strafe(float forwards, float right) {
        this.archerStrafeRequested = false;
        super.strafe(forwards, right);
    }

    public void strafeForArcher(float forwards, float right) {
        super.strafe(forwards, right);
        this.archerStrafeRequested = true;
    }

    @Override
    public void tick() {
        if (isClimbNavigationActive()) {
            clearArcherStrafeState();
            super.tick();
            return;
        }

        if (this.operation == Operation.STRAFE && this.archerStrafeRequested) {
            tickArcherStrafe();
            this.archerStrafeRequested = false;
            return;
        }

        clearArcherStrafeState();
        super.tick();
    }

    private void clearArcherStrafeState() {
        this.archerStrafeRequested = false;
        if (this.lastStrafeResult != StrafeResult.NONE) {
            this.mob.setXxa(0.0F);
            this.mob.setZza(0.0F);
            this.lastStrafeResult = StrafeResult.NONE;
        }
    }

    private void tickArcherStrafe() {
        float speed = (float)this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float speedModified = (float)this.speedModifier * speed;
        float xa = this.strafeForwards;
        float za = this.strafeRight;
        float dist = Mth.sqrt(xa * xa + za * za);
        if (dist < 1.0F) {
            dist = 1.0F;
        }

        dist = speedModified / dist;
        xa *= dist;
        za *= dist;
        float sin = Mth.sin(this.mob.getYRot() * (float)(Math.PI / 180.0));
        float cos = Mth.cos(this.mob.getYRot() * (float)(Math.PI / 180.0));
        float dx = xa * cos - za * sin;
        float dz = za * cos + xa * sin;
        if (!this.isWalkable(dx, dz)) {
            if (this.strafeRight != 0.0F) {
                this.strafeRight = -this.strafeRight;
                // The opposite side can also be lava or a cliff; check it before moving.
                float reverseDx = xa * cos + za * sin;
                float reverseDz = xa * sin - za * cos;
                if (this.isWalkable(reverseDx, reverseDz)) {
                    this.lastStrafeResult = StrafeResult.REDIRECTED;
                } else {
                    this.strafeForwards = 0;
                    this.strafeRight = 0;
                    this.lastStrafeResult = StrafeResult.BLOCKED;
                }
            } else {
                this.strafeForwards = 0.0F;
                this.strafeRight = 0.0F;
                this.lastStrafeResult = StrafeResult.BLOCKED;
            }
        } else {
            this.lastStrafeResult = StrafeResult.ACCEPTED;
        }

        this.mob.setSpeed(speedModified);
        this.mob.setZza(this.strafeForwards);
        this.mob.setXxa(this.strafeRight);
        this.operation = Operation.WAIT;
    }

    private boolean isWalkable(float dx, float dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        double scale = length > 1.0E-5 ? Math.max(1.0, 0.9 / length) : 1.0;
        BlockPos ahead = BlockPos.containing(this.mob.getX() + dx * scale, this.mob.getY(), this.mob.getZ() + dz * scale);
        if (!com.ancientkira.mca.entity.ai.navigation.TerrainSafety.solidFloor(mob.level(), ahead)
                || com.ancientkira.mca.entity.ai.navigation.TerrainSafety.dangerousEdge(mob.level(), ahead)) return false;
        PathNavigation pathNavigation = this.mob.getNavigation();
        if (pathNavigation != null) {
            NodeEvaluator nodeEvaluator = pathNavigation.getNodeEvaluator();
            if (nodeEvaluator != null
                    && nodeEvaluator.getPathType(this.mob, BlockPos.containing(this.mob.getX() + dx, this.mob.getBlockY(), this.mob.getZ() + dz))
                    != PathType.WALKABLE) {
                return false;
            }
        }

        return true;
    }

    private enum StrafeResult {
        NONE("none"),
        ACCEPTED("accepted"),
        REDIRECTED("redirected"),
        BLOCKED("blocked");

        private final String debugName;

        StrafeResult(String debugName) {
            this.debugName = debugName;
        }
    }
}
