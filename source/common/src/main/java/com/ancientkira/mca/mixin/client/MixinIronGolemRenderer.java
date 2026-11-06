package com.ancientkira.mca.mixin.client;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.world.entity.animal.golem.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(IronGolemRenderer.class)
abstract class MixinIronGolemRenderer {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/animal/golem/IronGolem;Lnet/minecraft/client/renderer/entity/state/IronGolemRenderState;F)V",at=@At("TAIL"))
    private void mca$dance(IronGolem golem,IronGolemRenderState state,float partialTick,CallbackInfo ci){
        ((com.ancientkira.mca.entity.RaiderGolemAppearance) state).mca$setGreyRaider(
                ((com.ancientkira.mca.entity.RaiderGolemAppearance) golem).mca$isGreyRaider());
        com.ancientkira.mca.client.render.GolemDancePose.set(state,com.ancientkira.mca.server.GolemDance.dancing(golem)?(float)(golem.getId()%13):null);
    }
}
