package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownTrident.class)
public class MixinThrownTrident {
    @Inject(method="tick()V",at=@At("TAIL"))
    private void mca$finishLoyaltyReturn(CallbackInfo callback) {
        ThrownTrident trident=(ThrownTrident)(Object)this;
        if(!trident.level().isClientSide() && trident.entityTags().contains("mca_archer_trident")
                && trident.isNoPhysics() && trident.getOwner() instanceof VillagerEntityMCA owner
                && trident.position().distanceToSqr(owner.getEyePosition())<2.25)trident.discard();
    }
}
