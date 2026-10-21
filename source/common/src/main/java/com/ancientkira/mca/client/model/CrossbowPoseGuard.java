package com.ancientkira.mca.client.model;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Keeps crossbow transitions from sending invalid rotations into deferred skin/armor renders. */
public final class CrossbowPoseGuard {
    private CrossbowPoseGuard() {}

    public static void prepare(HumanoidRenderState state) {
        if (!Float.isFinite(state.maxCrossbowChargeDuration) || state.maxCrossbowChargeDuration < 1) {
            state.maxCrossbowChargeDuration = 1;
        }
        if (!Float.isFinite(state.ticksUsingItem)) state.ticksUsingItem = 0;
    }

    public static void repair(HumanoidModel<?> model, HumanoidRenderState state) {
        if (!crossbow(state.leftArmPose) && !crossbow(state.rightArmPose)) return;
        if (finite(model.leftArm) && finite(model.rightArm)) return;
        // A bad supporting-arm transform must not persist into the next entity or armor pass.
        model.leftArm.resetPose();
        model.rightArm.resetPose();
        AnimationUtils.animateCrossbowHold(model.rightArm, model.leftArm, model.head,
                crossbow(state.rightArmPose));
    }

    private static boolean crossbow(HumanoidModel.ArmPose pose) {
        return pose == HumanoidModel.ArmPose.CROSSBOW_CHARGE || pose == HumanoidModel.ArmPose.CROSSBOW_HOLD;
    }

    private static boolean finite(ModelPart part) {
        return Float.isFinite(part.x) && Float.isFinite(part.y) && Float.isFinite(part.z)
                && Float.isFinite(part.xRot) && Float.isFinite(part.yRot) && Float.isFinite(part.zRot)
                && Float.isFinite(part.xScale) && Float.isFinite(part.yScale) && Float.isFinite(part.zScale);
    }
}
