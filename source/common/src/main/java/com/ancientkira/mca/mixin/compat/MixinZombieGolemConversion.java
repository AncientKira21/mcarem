package com.ancientkira.mca.mixin.compat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** False resumes MultiGolem's ordinary damage path rather than its instant villager conversion path. */
@Pseudo
@Mixin(targets="dev.charles.multigolem.ability.ZombieGolemConversion",remap=false)
public abstract class MixinZombieGolemConversion {
    @Inject(method="handle",at=@At("HEAD"),cancellable=true,remap=false)
    private static void mca$damageWithoutInfection(CallbackInfoReturnable<Boolean> cir) { if(com.ancientkira.mca.compat.SickenedCompat.enabled())cir.setReturnValue(false); }
}
