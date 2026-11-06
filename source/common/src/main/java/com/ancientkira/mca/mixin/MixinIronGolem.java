package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronGolem.class)
public abstract class MixinIronGolem extends net.minecraft.world.entity.Mob implements com.ancientkira.mca.entity.RaiderGolemAppearance {
    @org.spongepowered.asm.mixin.Unique
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> MCA_GREY_RAIDER =
            net.minecraft.network.syncher.SynchedEntityData.defineId(IronGolem.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void mca$defineRaiderTint(net.minecraft.network.syncher.SynchedEntityData.Builder builder,
                                    org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        builder.define(MCA_GREY_RAIDER, false);
    }

    @Override
    public boolean mca$isGreyRaider() { return getEntityData().get(MCA_GREY_RAIDER); }

    @Override
    public void mca$setGreyRaider(boolean grey) { getEntityData().set(MCA_GREY_RAIDER, grey); }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void mca$syncRaiderTint(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!level().isClientSide()) {
            mca$setGreyRaider(getType() == EntityType.IRON_GOLEM
                    && com.ancientkira.mca.server.GolemAllies.raider((IronGolem) (Object) this));
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mca$restoreRaiderTint(net.minecraft.world.level.storage.ValueInput input,
                                     org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!level().isClientSide()) {
            mca$setGreyRaider(getType() == EntityType.IRON_GOLEM
                    && com.ancientkira.mca.server.GolemAllies.raider((IronGolem) (Object) this));
        }
    }

    protected MixinIronGolem(EntityType<? extends net.minecraft.world.entity.Mob> type, Level level) {
        super(type, level);
    }

    @Inject(method = "mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void mca$conversation(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        var item=player.getItemInHand(hand);
        if (player.isShiftKeyDown() && (item.is(net.minecraft.tags.ItemTags.ANVIL) || item.is(Items.ANVIL)
                || item.is(Items.CHIPPED_ANVIL) || item.is(Items.DAMAGED_ANVIL))) {
            setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, item.copyWithCount(1));
            if (!player.getAbilities().instabuild) item.shrink(1);
            playSound(net.minecraft.sounds.SoundEvents.ANVIL_USE, 1.0F, 1.0F);
            cir.setReturnValue(net.minecraft.world.InteractionResult.SUCCESS);
            return;
        }
        if(player.isShiftKeyDown() || item.is(Items.NAME_TAG) || item.is(Items.LEAD)
                || item.is(Items.IRON_INGOT))return;
        if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && com.ancientkira.mca.server.NativeSocial.open(serverPlayer,this))cir.setReturnValue(net.minecraft.world.InteractionResult.SUCCESS);
        else if(level().isClientSide())cir.setReturnValue(net.minecraft.world.InteractionResult.SUCCESS);
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void mca$danceEvent(byte event, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.ancientkira.mca.server.GolemDance.event((IronGolem)(Object)this,event);
    }
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void mca$targetHostilePeople(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        goalSelector.addGoal(0, new com.ancientkira.mca.entity.ai.navigation.GolemSwimGoal((IronGolem) (Object) this));
        targetSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                this, net.minecraft.world.entity.npc.villager.Villager.class, true,
                (target, world) -> !(target instanceof VillagerEntityMCA) && com.ancientkira.mca.compat.HostileGolemCompat.hostile(this)));

        targetSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                this, VillagerEntityMCA.class, true, (target, world) -> com.ancientkira.mca.server.GolemAllies.enemy((IronGolem) (Object) this, target)));
    }

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void mca$arrestInsteadOfStrike(net.minecraft.server.level.ServerLevel world,
            net.minecraft.world.entity.Entity target, CallbackInfoReturnable<Boolean> cir) {
        IronGolem golem = (IronGolem) (Object) this;
        if (target instanceof LivingEntity living && com.ancientkira.mca.server.VillageWarfare.combatRelation(golem,living).filter(enemy->!enemy).isPresent()) {
            cir.setReturnValue(false); return;
        }
        if (!com.ancientkira.mca.compat.HostileGolemCompat.hostile(golem) && !com.ancientkira.mca.server.GolemAllies.raider(golem) && com.ancientkira.mca.server.GolemPolice.arrestable(target)) {
            if (com.ancientkira.mca.server.GolemPolice.arrest(golem, target)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void mca$skipGuardTargets(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        IronGolem golem = (IronGolem) (Object) this;
        if (com.ancientkira.mca.server.GolemPolice.prisoner(target)) { cir.setReturnValue(false); return; }
        if (target.isSleeping() && (target instanceof net.minecraft.world.entity.monster.zombie.Zombie || target instanceof com.ancientkira.mca.entity.ZombieVillagerEntityMCA)) {
            cir.setReturnValue(false);
            return;
        }
        if (target instanceof com.ancientkira.mca.entity.ZombieVillagerEntityMCA z && com.ancientkira.mca.server.InfirmaryCare.isZombiePatient(z)) {
            cir.setReturnValue(false);
            return;
        }
        if (target instanceof VillagerEntityMCA || target instanceof IronGolem) {
            cir.setReturnValue(com.ancientkira.mca.server.GolemAllies.enemy(golem, target));
        } else if (com.ancientkira.mca.server.GolemAllies.raider(golem)
                && target instanceof net.minecraft.world.entity.raid.Raider) {
            cir.setReturnValue(false);
        }
    }
}
