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
import net.minecraft.world.item.Items;
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

    @Inject(method="interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;",at=@At("HEAD"),cancellable=true)
    private void mca$nativeConversation(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.Vec3 location,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        Mob mob=(Mob)(Object)this;
        if(com.ancientkira.mca.server.SkeletonCuring.interact(mob,player,hand)) {
            cir.setReturnValue(net.minecraft.world.InteractionResult.SUCCESS); return;
        }
        if(com.ancientkira.mca.server.VillagerMounts.saddled(mob)
                && com.ancientkira.mca.server.VillagerMounts.hasVillager(mob) && mob.getPassengers().size()<2
                && !player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if(mob.level().isClientSide() || player.startRiding(mob,false,true))
                cir.setReturnValue(net.minecraft.world.InteractionResult.SUCCESS);
            return;
        }
        var item=player.getItemInHand(hand);
        if(!com.ancientkira.mca.server.NativeSocial.supported(mob) || player.isShiftKeyDown()
                || item.is(Items.NAME_TAG) || item.is(Items.LEAD)
                || item.is(Items.IRON_INGOT))return;
        if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && com.ancientkira.mca.server.NativeSocial.open(serverPlayer,mob))
            cir.setReturnValue(net.minecraft.world.InteractionResult.SUCCESS);
        else if(mob.level().isClientSide())cir.setReturnValue(net.minecraft.world.InteractionResult.SUCCESS);
    }
    @Inject(method="getControllingPassenger()Lnet/minecraft/world/entity/LivingEntity;",at=@At("HEAD"),cancellable=true)
    private void mca$saddleController(CallbackInfoReturnable<LivingEntity> result) {
        Mob mount=(Mob)(Object)this;
        if(com.ancientkira.mca.server.VillagerMounts.hasVillager(mount)) {
            if(!mount.isSaddled())result.setReturnValue(null);
            else if(mount.getFirstPassenger() instanceof LivingEntity first)result.setReturnValue(first);
        }
    }

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
        if (!(mob instanceof Enemy) || com.ancientkira.mca.server.NeutralMobRules.passiveUntilProvoked(mob)
                || mca$villagerTargetInstalled) return;
        mca$villagerTargetInstalled = true;
        // Install after subclass goals are registered; retain each mob's native attack behavior.
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, true,
                (candidate, world) -> mca$ordinaryVillager(candidate) && !mca$protectedRaider(candidate)
                        && com.ancientkira.mca.server.NeutralMobRules.mayHuntVillager(mob, candidate)));
    }

    @Inject(method = "serverAiStep", at = @At("TAIL"))
    private void mca$coordinateGolems(CallbackInfo ci) {
        com.ancientkira.mca.server.VillagerMounts.tick((Mob)(Object)this);
        if ((Object) this instanceof net.minecraft.world.entity.animal.golem.IronGolem golem)
            com.ancientkira.mca.server.GolemAllies.tick(golem);
        if ((Object) this instanceof net.minecraft.world.entity.animal.golem.IronGolem golem)
            com.ancientkira.mca.server.GolemDance.tick(golem);
        if ((Object)this instanceof net.minecraft.world.entity.animal.golem.IronGolem golem)
            com.ancientkira.mca.server.TacticalCommands.tickGolem(golem);
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void mca$checkPrisonEnclosure(CallbackInfo ci) {
        com.ancientkira.mca.compat.SickenedCompat.tick((Mob)(Object)this);
        com.ancientkira.mca.server.SkeletonCuring.tick((Mob)(Object)this);
        if(((Mob)(Object)this).tickCount%40==0)com.ancientkira.mca.server.NativeSocial.name((Mob)(Object)this);
        com.ancientkira.mca.server.GolemPolice.tickPrisoner((Mob) (Object) this);
        if ((Object)this instanceof net.minecraft.world.entity.animal.golem.IronGolem golem) com.ancientkira.mca.server.VillageWarfare.tickGolem(golem);
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void mca$keepWarAllegiance(LivingEntity target, CallbackInfo ci) {
        if (target!=null && (Object)this instanceof net.minecraft.world.entity.animal.golem.IronGolem golem
                && com.ancientkira.mca.server.VillageWarfare.combatRelation(golem,target).filter(enemy->!enemy).isPresent()) ci.cancel();
    }

    @Inject(method = "setTarget", at = @At("TAIL"))
    private void mca$shareVillagerTargetWithBrain(LivingEntity candidate, CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (mob instanceof Enemy && candidate != null && mca$ordinaryVillager(candidate)
                && !mca$protectedRaider(candidate)) {
            if (mob instanceof net.minecraft.world.entity.monster.piglin.Piglin
                    && com.ancientkira.mca.server.NeutralMobRules.mayHuntVillager(mob, candidate)) {
                mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, candidate.getUUID(), 600L);
            }
            mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, candidate);
        }
    }

    @Unique
    private boolean mca$protectedRaider(LivingEntity candidate) {
        if((Object)this instanceof net.minecraft.world.entity.monster.Ravager
                && candidate instanceof net.minecraft.world.entity.animal.golem.IronGolem golem
                && com.ancientkira.mca.server.GolemAllies.raider(golem)) return true;
        if ((Object) this instanceof net.minecraft.world.entity.monster.Vex vex
                && vex.getOwner() instanceof VillagerEntityMCA owner && candidate != null
                && com.ancientkira.mca.registry.ProfessionsMCA.isRaider(owner.getProfession())
                && com.ancientkira.mca.server.RaiderSocial.isFamily(owner, candidate)) return true;
        return (Object) this instanceof Enemy && candidate instanceof VillagerEntityMCA person
                && ProfessionsMCA.isRaider(person.getProfession());
    }

    @Unique
    private boolean mca$goldProtected(LivingEntity person) {
        return (Object) this instanceof net.minecraft.world.entity.monster.piglin.Piglin piglin
                && person != null && mca$ordinaryVillager(person)
                && com.ancientkira.mca.server.NeutralMobRules.wearingGold(person)
                && !com.ancientkira.mca.server.NeutralMobRules.provokedBy(piglin, person);
    }

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void mca$ignoreRaiders(LivingEntity candidate, CallbackInfoReturnable<Boolean> cir) {
        if (((Mob) (Object) this).isSleeping() || com.ancientkira.mca.server.GolemPolice.prisoner((Mob) (Object) this)) {
            cir.setReturnValue(false);
            return;
        }
        if (mca$protectedRaider(candidate) || mca$goldProtected(candidate)) cir.setReturnValue(false);
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void mca$rejectRaiderTarget(LivingEntity candidate, CallbackInfo ci) {
        if (((Mob) (Object) this).isSleeping() || com.ancientkira.mca.server.GolemPolice.prisoner((Mob) (Object) this)) {
            target = null;
            ci.cancel();
            return;
        }
        if (mca$protectedRaider(candidate) || mca$goldProtected(candidate)) {
            target = null;
            ci.cancel();
        }
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void mca$forgetRaiderTargets(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (mob.isSleeping() || com.ancientkira.mca.server.GolemPolice.prisoner(mob)) {
            target = null;
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            return;
        }
        if (mca$protectedRaider(target) || mca$goldProtected(target)) target = null;
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && (mca$protectedRaider(mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null))
                    || mca$goldProtected(mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null)))) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    @Inject(method = "doHurtTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void mca$noAttackWhileSleeping(net.minecraft.server.level.ServerLevel world, net.minecraft.world.entity.Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (((Mob) (Object) this).isSleeping() || com.ancientkira.mca.server.GolemPolice.prisoner((Mob) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
