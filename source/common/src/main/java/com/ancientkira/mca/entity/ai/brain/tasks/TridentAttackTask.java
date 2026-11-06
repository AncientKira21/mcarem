package com.ancientkira.mca.entity.ai.brain.tasks;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.RangedWeaponHelper;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.Items;

public final class TridentAttackTask extends Behavior<VillagerEntityMCA> {
    private long readyAt;
    public TridentAttackTask(){super(ImmutableMap.of(MemoryModuleType.ATTACK_TARGET,MemoryStatus.VALUE_PRESENT),1200);}
    @Override protected boolean checkExtraStartConditions(ServerLevel world,VillagerEntityMCA person) {
        return RangedWeaponHelper.getWeaponHoldingHand(person)!=null
                && person.getItemInHand(RangedWeaponHelper.getWeaponHoldingHand(person)).is(Items.TRIDENT);
    }
    @Override protected boolean canStillUse(ServerLevel world,VillagerEntityMCA person,long time) {
        return checkExtraStartConditions(world,person) && person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }
    @Override protected void tick(ServerLevel world,VillagerEntityMCA person,long time) {
        var target=person.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
        var hand=RangedWeaponHelper.getTridentHoldingHand(person);
        if(hand==null || !RangedWeaponHelper.isValidAttackTarget(person,target))return;
        if(person.isUsingCombatShield())return;
        if(time<readyAt || person.distanceToSqr(target)>225 || !person.hasLineOfSight(target)) {
            if(person.isUsingItem() && person.getUseItem().is(Items.TRIDENT))person.stopUsingItem();
            return;
        }
        person.getLookControl().setLookAt(target,30,30);
        if(!person.isUsingItem())person.startUsingItem(hand);
        else if(person.getUseItem().is(Items.TRIDENT) && person.getTicksUsingItem()>=20) {
            person.stopUsingItem();person.performRangedAttack(target,1);readyAt=time+40;
        }
    }
    @Override protected void stop(ServerLevel world,VillagerEntityMCA person,long time) {
        if(person.isUsingItem() && person.getUseItem().is(Items.TRIDENT))person.stopUsingItem();
    }
}
