package com.ancientkira.mca.mixin.compat;
import com.ancientkira.mca.entity.VillagerLike;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Optional target: MCA people use their own staged infection and identity-preserving conversion. */
@Pseudo
@Mixin(targets="com.ancientkira.sickenedmobs.server.SickenedConversion",remap=false)
public abstract class MixinSickenedConversion {
    @Inject(method="onEntityDeath",at=@At("HEAD"),cancellable=true,remap=false)
    private static void mca$ownSicknessConversion(LivingEntity victim,DamageSource source,CallbackInfoReturnable<Boolean> cir) {
        if(com.ancientkira.mca.compat.SickenedCompat.enabled() && victim instanceof VillagerLike<?>)cir.setReturnValue(com.ancientkira.mca.compat.SickenedCompat.onDeath(victim, source));
    }
}
