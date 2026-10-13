package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hostile mobs target ordinary villagers and leave raider professions alone. */
@Mixin(Mob.class)
public abstract class MixinMob {
    @Shadow private LivingEntity target;
    @Shadow protected GoalSelector targetSelector;
    @Unique private boolean mca$villagerTargetInstalled;

    @Inject(method = "playAmbientSound", at = @At("HEAD"), cancellable = true)
    private void mca$suppressQueuedRaiderVoice(CallbackInfo ci) {
        // Spawn eggs play this after insertion, even when MCA queued the original for replacement.
        if (com.ancientkira.mca.server.SpawnQueue.getInstance().isQueuedRaider((Mob) (Object) this)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean mca$ordinaryVillager(LivingEntity candidate) {
        return candidate instanceof VillagerEntityMCA person
                ? !ProfessionsMCA.isRaider(person.getProfession()) : candidate instanceof Villager;
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void mca$targetVillagers(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (!(mob instanceof Enemy) || mca$villagerTargetInstalled) return;
        mca$villagerTargetInstalled = true;
        // Install after subclass goals are registered; retain each mob's native attack behavior.
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, true,
                (candidate, world) -> mca$ordinaryVillager(candidate) && !mca$protectedRaider(candidate)));
    }

    @Inject(method = "setTarget", at = @At("TAIL"))
    private void mca$shareVillagerTargetWithBrain(LivingEntity candidate, CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (mob instanceof Enemy && candidate != null && mca$ordinaryVillager(candidate)
                && !mca$protectedRaider(candidate)) {
            mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, candidate);
        }
    }

    @Unique
    private boolean mca$protectedRaider(LivingEntity candidate) {
        if ((Object) this instanceof net.minecraft.world.entity.monster.Vex vex
                && vex.getOwner() instanceof VillagerEntityMCA owner && candidate != null
                && com.ancientkira.mca.registry.ProfessionsMCA.isRaider(owner.getProfession())
                && com.ancientkira.mca.server.RaiderSocial.isFamily(owner, candidate)) return true;
        return (Object) this instanceof Enemy && candidate instanceof VillagerEntityMCA person
                && ProfessionsMCA.isRaider(person.getProfession());
    }

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void mca$ignoreRaiders(LivingEntity candidate, CallbackInfoReturnable<Boolean> cir) {
        if (mca$protectedRaider(candidate)) cir.setReturnValue(false);
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void mca$rejectRaiderTarget(LivingEntity candidate, CallbackInfo ci) {
        if (mca$protectedRaider(candidate)) {
            target = null;
            ci.cancel();
        }
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void mca$forgetRaiderTargets(CallbackInfo ci) {
        if (mca$protectedRaider(target)) target = null;
        Mob mob = (Mob) (Object) this;
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && mca$protectedRaider(mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null))) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }
}
