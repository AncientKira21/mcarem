package com.ancientkira.mca.mixin.compat;
import com.ancientkira.mca.entity.VillagerLike;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Preserve MCA's person type when the external mod migrates legacy tagged zombies to its custom vanilla types. */
@Pseudo
@Mixin(targets="com.ancientkira.sickenedmobs.SickenedEntities",remap=false)
public abstract class MixinSickenedEntities {
    @Inject(method="convertZombie",at=@At("HEAD"),cancellable=true,remap=false)
    private static void mca$keepPersonType(Zombie source,boolean sickened,boolean restoreHealth,CallbackInfoReturnable<Zombie> cir) {
        if(com.ancientkira.mca.compat.SickenedCompat.enabled() && source instanceof VillagerLike<?>)cir.setReturnValue(null);
    }
}
