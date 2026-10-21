package com.ancientkira.mca.mixin;

import com.ancientkira.mca.server.PillagerRaids;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Raid.class)
public abstract class MixinRaid {
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isVillage(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean mca$recognizeRegisteredVillage(boolean vanilla,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) ServerLevel world) {
        if (vanilla) return true;
        Raid raid = (Raid) (Object) this;
        return com.ancientkira.mca.server.world.data.VillageManager.get(world)
                .findNearestVillage(raid.getCenter(), 0).isPresent();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mca$updatePeople(ServerLevel world, CallbackInfo ci) {
        PillagerRaids.update((Raid) (Object) this, world);
    }

    @Inject(method = "getTotalRaidersAlive", at = @At("RETURN"), cancellable = true)
    private void mca$countPeople(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + PillagerRaids.count((Raid) (Object) this));
    }

    @Inject(method = "getHealthOfLivingRaiders", at = @At("RETURN"), cancellable = true)
    private void mca$peopleHealth(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() + PillagerRaids.health((Raid) (Object) this));
    }
}
