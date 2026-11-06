package com.ancientkira.mca.client.model;

import com.google.common.collect.ImmutableList;
import com.ancientkira.mca.Config;
import com.ancientkira.mca.client.render.VillagerRenderState;
import com.ancientkira.mca.client.render.VillagerStateHolder;
import com.ancientkira.mca.client.render.VillagerVisuals;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;

public class VillagerEntityBaseModelMCA extends HumanoidModel<VillagerRenderState> implements CommonVillagerModel<VillagerRenderState> {
    protected static final String BREASTS = "breasts";

    public final ModelPart breasts;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;
    private boolean receivesDeferredAnimationPose;

    public VillagerEntityBaseModelMCA(ModelPart root) {
        super(root);
        this.breasts = getChildOrEmpty(root, BREASTS);
    }

    /** Returns a model child or a detached empty part for externally animated vanilla roots. */
    static ModelPart getChildOrEmpty(ModelPart root, String name) {
        return root.hasChild(name) ? root.getChild(name) : new ModelPart(List.of(), Map.of());
    }

    public static MeshDefinition getModelData(CubeDeformation dilation) {
        MeshDefinition modelData = HumanoidModel.createMesh(dilation, 0.0f);
        PartDefinition data = modelData.getRoot();

        data.addOrReplaceChild(BREASTS, newBreasts(dilation, 0), PartPose.ZERO);

        return modelData;
    }

    protected static CubeListBuilder newBreasts(CubeDeformation dilation, int oy) {
        CubeListBuilder builder = CubeListBuilder.create();
        if (Config.getInstance().enableBoobs) {
            builder.texOffs(18, 21 + oy).addBox(-3.25F, -1.25F, -1.5F, 6, 3, 3, dilation);
        }
        return builder;
    }

    @Override
    public void setupAnim(VillagerRenderState state) {
        CrossbowPoseGuard.prepare(state);
        float originalWalkAnimationPos = state.walkAnimationPos;
        float originalWalkAnimationSpeed = state.walkAnimationSpeed;
        float originalYRot = state.yRot;
        boolean originalIsPassenger = state.isPassenger;
        VillagerVisuals visuals = VillagerVisuals.require(state);
        boolean baby = visuals.baby();

        if (baby) {
            state.isPassenger = true;
        }

        if (baby && !originalIsPassenger) {
            state.walkAnimationSpeed = (float) Math.sin(visuals.tickCount() / 12.0F);
            state.walkAnimationPos = (float) Math.cos(visuals.tickCount() / 9.0F) * 3.0F;
            state.yRot = originalYRot + (float) Math.sin(visuals.tickCount() / 2.0F);
        }

        super.setupAnim(state);

        state.walkAnimationPos = originalWalkAnimationPos;
        state.walkAnimationSpeed = originalWalkAnimationSpeed;
        state.yRot = originalYRot;
        state.isPassenger = originalIsPassenger;

        if (state.isCastingSpell) {
            // Match the vanilla evoker's raised, gently waving casting arms.
            rightArm.xRot = Mth.cos(state.ageInTicks * 0.6662F) * 0.25F;
            leftArm.xRot = rightArm.xRot;
            rightArm.yRot = leftArm.yRot = 0.0F;
            rightArm.zRot = 2.3561945F;
            leftArm.zRot = -2.3561945F;
        }

        CpaVillagerAnimations.apply(this, state);
        CrossbowPoseGuard.repair(this, state);
        applyVillagerDimensions(visuals);
        if (receivesDeferredAnimationPose && state instanceof VillagerStateHolder holder && holder.mca$getHumanoidModelPose() != null) {
            holder.mca$getHumanoidModelPose().applyTo(this);
        }
        applyPanicPose(state);
        applyBabySitting(state);
        if(visuals.hugging() && !visuals.sleeping()){
            rightArm.xRot=leftArm.xRot=-.95F;
            rightArm.yRot=-.65F;leftArm.yRot=.65F;
            rightArm.zRot=.20F;leftArm.zRot=-.20F;
        }
    }

    /** Apply after pack/deferred poses so fear cannot be replaced by an idle animation. */
    public void applyPanicPose(VillagerRenderState state) {
        if (state.isVisuallySwimming || state.hasPose(net.minecraft.world.entity.Pose.SLEEPING)
                || state.isCastingSpell && !state.isKidnapped) return;
        float progress = state.isKidnapped ? 1.0F : Mth.clamp(state.panicAnimationProgress, 0.0F, 1.0F);
        if (progress <= 0) return;
        float radians = (float) Math.PI / 180;
        float raise = (((float) Math.sin(state.ageInTicks / 5) * 30 - 180)
                + (float) Math.sin(state.ageInTicks / 3) * 3) * radians * progress;
        float wave = ((float) Math.sin(state.ageInTicks / 2) * 12 - 17) * radians * progress;
        leftArm.xRot = raise;
        rightArm.xRot = -raise;
        leftArm.yRot = rightArm.yRot = 0;
        leftArm.zRot = -wave;
        rightArm.zRot = wave;
    }

    /** Reapply the baby leg pose after external and deferred animations, including armor passes. */
    public void applyBabySitting(VillagerRenderState state) {
        if (!VillagerVisuals.require(state).baby() || state.hasPose(net.minecraft.world.entity.Pose.SLEEPING)
                || state.isVisuallySwimming) return;
        rightLeg.xRot = leftLeg.xRot = -1.4137167F;
        rightLeg.yRot = 0.31415927F;
        leftLeg.yRot = -0.31415927F;
        rightLeg.zRot = 0.07853982F;
        leftLeg.zRot = -0.07853982F;
    }

    protected boolean hasDeferredPose(VillagerRenderState state) {
        return receivesDeferredAnimationPose && state instanceof VillagerStateHolder holder
                && holder.mca$getHumanoidModelPose() != null;
    }

    public VillagerEntityBaseModelMCA receiveDeferredAnimationPose() {
        receivesDeferredAnimationPose = true;
        return this;
    }

    public void copyPropertiesTo(HumanoidModel<?> target) {
        if (target instanceof VillagerEntityBaseModelMCA m) {
            CommonVillagerModel.copyPartState(m.head, head);
            CommonVillagerModel.copyPartState(m.hat, hat);
            CommonVillagerModel.copyPartState(m.body, body);
            CommonVillagerModel.copyPartState(m.leftArm, leftArm);
            CommonVillagerModel.copyPartState(m.rightArm, rightArm);
            CommonVillagerModel.copyPartState(m.leftLeg, leftLeg);
            CommonVillagerModel.copyPartState(m.rightLeg, rightLeg);
            copyCommonAttributes(m);

            m.breasts.visible = breasts.visible;
            CommonVillagerModel.copyPartState(m.breasts, breasts);
        }
    }

    @Override
    public ModelPart getBreastPart() {
        return breasts;
    }

    @Override
    public ModelPart getBodyPart() {
        return body;
    }

    @Override
    public Iterable<ModelPart> getCommonHeadParts() {
        return ImmutableList.of(head);
    }

    @Override
    public Iterable<ModelPart> getCommonBodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg);
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts);
    }

    @Override
    public VillagerDimensions.Mutable getDimensions() {
        return dimensions;
    }

    @Override
    public float getBreastSize() {
        return breastSize;
    }

    @Override
    public void setBreastSize(float breastSize) {
        this.breastSize = breastSize;
    }

}
