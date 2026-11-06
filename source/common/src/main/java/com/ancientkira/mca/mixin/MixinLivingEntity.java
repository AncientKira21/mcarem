package com.ancientkira.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.ancientkira.mca.entity.PlayerDimensions;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;

@Mixin(LivingEntity.class)
abstract class MixinLivingEntity implements com.ancientkira.mca.entity.RevivalState {
    @org.spongepowered.asm.mixin.injection.Inject(method="startSleeping", at=@At("HEAD"), cancellable=true)
    private void mca$respectPlayerHomeBed(net.minecraft.core.BlockPos pos,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (entity instanceof Mob && entity.level() instanceof net.minecraft.server.level.ServerLevel world
                && com.ancientkira.mca.server.world.data.PlayerBedClaims.reserved(world, pos)) callback.cancel();
    }

    @org.spongepowered.asm.mixin.injection.Inject(method="dropCustomDeathLoot",at=@At("TAIL"))
    private void mca$sickenedFlesh(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean playerKilled,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.ancientkira.mca.compat.SickenedCompat.dropFlesh((LivingEntity)(Object)this,level);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method="hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",at=@At("RETURN"))
    private void mca$sickenedExposure(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) com.ancientkira.mca.compat.SickenedCompat.onHurt((LivingEntity)(Object)this,source);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method="die",at=@At("TAIL"))
    private void mca$releaseOwnedHorse(DamageSource source, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if ((Object)this instanceof net.minecraft.world.entity.animal.equine.Horse horse && !horse.isAlive())
            com.ancientkira.mca.server.HorseHelp.horseDied(horse);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method="tick()V",at=@At("HEAD"))
    private void mca$expireVillageMovement(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.ancientkira.mca.server.UnderwaterVillageSupport.tickMovement((LivingEntity)(Object)this);
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="travelInWater",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double mca$keepLandMovementWhileSwimming(LivingEntity entity,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Double> original) {
        double value=original.call(entity,attribute);
        if(attribute==net.minecraft.world.entity.ai.attributes.Attributes.WATER_MOVEMENT_EFFICIENCY) {
            var instance=entity.getAttribute(attribute);
            if(instance!=null && instance.hasModifier(com.ancientkira.mca.server.UnderwaterVillageSupport.WATER_MOVEMENT))
                return entity.onGround()?1.0:2.0; // Native swimming halves this value away from the floor.
        }
        return value;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method={"canBeSeenAsEnemy", "isPushable"},at=@At("HEAD"),cancellable=true)
    private void mca$hideFirstJoinFromEnemies(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> info) {
        if ((Object)this instanceof net.minecraft.server.level.ServerPlayer player
                && com.ancientkira.mca.server.FirstJoinSetup.active(player)) info.setReturnValue(false);
    }

    @Override public void mca$resetDeathState() {
        dead = false;
        LivingEntity living=(LivingEntity)(Object)this;
        living.deathTime = 0;
        living.hurtTime = 0;
        living.hurtDuration = 0;
        if(living.getPose()==Pose.DYING)living.setPose(Pose.STANDING);
    }
    @org.spongepowered.asm.mixin.Shadow protected boolean dead;
    @org.spongepowered.asm.mixin.injection.Inject(method="handleEntityEvent(B)V",at=@At("HEAD"),cancellable=true)
    private void mca$clearClientDeathAfterRevival(byte event,org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        if(event!=com.ancientkira.mca.entity.Status.MCA_VILLAGER_REVIVED)return;
        LivingEntity living=(LivingEntity)(Object)this;
        mca$resetDeathState();
        // Explicit revival precedes the next normal health-metadata update.
        if(living.level().isClientSide() && living.getHealth()<=0)living.setHealth(living.getMaxHealth());
        callback.cancel();
    }

    @org.spongepowered.asm.mixin.injection.Inject(method="hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",at=@At("HEAD"),cancellable=true)
    private void mca$ravagerFriendlyFire(net.minecraft.server.level.ServerLevel world,DamageSource source,float amount,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> result) {
        // Includes reflected enchantment damage: passive babies cannot retaliate indirectly either.
        if (source.getEntity() instanceof com.ancientkira.mca.entity.ZombieVillagerEntityMCA zombie
                && zombie.isPassiveBaby()) {
            result.setReturnValue(false);
            return;
        }
        if (com.ancientkira.mca.server.UnderwaterVillageSupport.shouldCancelDrowning((LivingEntity)(Object)this, source)) {
            result.setReturnValue(false);
            return;
        }
        if((Object)this instanceof net.minecraft.world.entity.animal.golem.IronGolem golem
                && com.ancientkira.mca.server.GolemAllies.raider(golem)
                && source.getEntity() instanceof net.minecraft.world.entity.monster.Ravager)result.setReturnValue(false);
    }


    private boolean mca$usesDeathStateGuard() {
        return (Object)this instanceof com.ancientkira.mca.entity.VillagerLike<?>
                || (Object)this instanceof net.minecraft.world.entity.animal.golem.IronGolem;
    }

    // Health can be restored by healing/return hooks before the death animation finishes.
    // A committed death must still exclude the entity from targeting and further damage.
    @ModifyReturnValue(method="isAlive()Z", at=@At("RETURN"))
    private boolean mca$excludeCommittedDeaths(boolean alive) {
        return alive && !(mca$usesDeathStateGuard() && dead);
    }

    @ModifyReturnValue(method="isDeadOrDying()Z", at=@At("RETURN"))
    private boolean mca$rejectDamageAfterDeath(boolean dying) {
        return dying || mca$usesDeathStateGuard() && dead;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method="die",at=@org.spongepowered.asm.mixin.injection.At("HEAD"),cancellable=true)
    private void mca$funGolemDeath(net.minecraft.world.damagesource.DamageSource source,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.ancientkira.mca.server.AnvilSquashAdvancement.checkKill((LivingEntity) (Object) this, source);
        if((Object)this instanceof net.minecraft.world.entity.animal.golem.IronGolem golem) {
            if(com.ancientkira.mca.server.VillageWarfare.simulateGolemDeath(golem))ci.cancel();
            else com.ancientkira.mca.server.VillageWarfare.recordGolemDeath(golem);
        }
    }

    @ModifyArg(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"), index = 0)
    private float mca$captureInsteadOfKilling(float remainingHealth, @Local(argsOnly = true) DamageSource source) {
        if ((Object) this instanceof VillagerEntityMCA victim
                && remainingHealth < victim.getHealth() && remainingHealth <= victim.getMaxHealth() * 0.25F
                && com.ancientkira.mca.server.RaiderSocial.tryKidnapFromAttack(source.getEntity(), victim)) {
            // Armor, shields and absorption have already reduced the damage at this point.
            return Math.max(1.0F, remainingHealth);
        }
        return remainingHealth;
    }

    @ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))
    private EntityDimensions mca$scalePlayerDimensions(EntityDimensions original, Pose pose) {
        if (pose == Pose.SLEEPING || !((Object) this instanceof Player player)) {
            return original;
        }

        return PlayerDimensions.getScale(player)
                .map(scale -> {
                    EntityDimensions scaled = original.scale(scale.width(), scale.height());
                    return scaled;
                })
                .orElse(original);
    }

    @ModifyReturnValue(method = "isImmobile()Z", at = @At("RETURN"))
    private boolean mca$allowMcaControlledMovement(boolean original) {
        if ((Object) this instanceof Mob mob && mob.getControllingPassenger() instanceof VillagerEntityMCA) {
            return false;
        }
        return original;
    }
}
