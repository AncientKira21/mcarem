package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronGolem.class)
public abstract class MixinIronGolem extends net.minecraft.world.entity.Mob {
    protected MixinIronGolem(EntityType<? extends net.minecraft.world.entity.Mob> type, Level level) {
        super(type, level);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void mca$targetHostilePeople(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        targetSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                this, VillagerEntityMCA.class, true, (target, world) -> ((VillagerEntityMCA) target).isHostile()));
    }

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void mca$skipGuardTargets(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof VillagerEntityMCA villager && !villager.isHostile()) {
            cir.setReturnValue(false);
        }
    }
}
