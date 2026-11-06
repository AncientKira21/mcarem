package com.ancientkira.mca.mixin.client;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(IronGolemModel.class)
abstract class MixinIronGolemModel {
    @Shadow @Final private ModelPart head;
    @Shadow @Final private ModelPart rightArm;
    @Shadow @Final private ModelPart leftArm;
    @Shadow @Final private ModelPart rightLeg;
    @Shadow @Final private ModelPart leftLeg;
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/IronGolemRenderState;)V",at=@At("TAIL"))
    private void mca$dance(IronGolemRenderState state,CallbackInfo ci){
        Float phase=com.ancientkira.mca.client.render.GolemDancePose.phase(state);if(phase==null)return;
        float beat=(float)Math.sin(state.ageInTicks*.3+phase),counter=(float)Math.cos(state.ageInTicks*.3+phase);
        head.zRot=beat*.15F;rightArm.xRot=counter*.7F;leftArm.xRot=-counter*.7F;
        rightArm.zRot=.45F+beat*.25F;leftArm.zRot=-.45F+beat*.25F;
        rightLeg.xRot=beat*.25F;leftLeg.xRot=-beat*.25F;
    }
}
