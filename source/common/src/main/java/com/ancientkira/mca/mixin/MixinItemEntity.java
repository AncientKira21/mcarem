package com.ancientkira.mca.mixin;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemEntity.class)
abstract class MixinItemEntity {
    @Inject(method="tick()V",at=@At("HEAD"))
    private void mca$permanentCorpse(CallbackInfo ci){com.ancientkira.mca.server.Corpses.tickItem((ItemEntity)(Object)this);}
    @Inject(method="tick()V",at=@At("TAIL"))
    private void mca$recoverAfterRevival(CallbackInfo ci){com.ancientkira.mca.server.DeathDropRecovery.tick((ItemEntity)(Object)this);}
    @Inject(method="tryToMerge(Lnet/minecraft/world/entity/item/ItemEntity;)V",at=@At("HEAD"),cancellable=true)
    private void mca$keepDeathDropsSeparate(ItemEntity other,CallbackInfo ci){
        if(!com.ancientkira.mca.server.DeathDropRecovery.mayMerge((ItemEntity)(Object)this,other))ci.cancel();
    }
}
