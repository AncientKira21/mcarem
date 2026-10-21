package com.ancientkira.mca.entity.ai.brain.tasks;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.GunCompatibility;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;

public final class GunAttackTask extends Behavior<VillagerEntityMCA> {
    private long readyAt;
    private boolean reloading;
    private ItemStack weapon=ItemStack.EMPTY;
    public GunAttackTask() { super(ImmutableMap.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)); }
    @Override protected boolean checkExtraStartConditions(ServerLevel world,VillagerEntityMCA person) { return GunCompatibility.hand(person)!=null; }
    @Override protected boolean canStillUse(ServerLevel world,VillagerEntityMCA person,long time) {
        return GunCompatibility.hand(person)!=null && person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }
    @Override protected void start(ServerLevel world,VillagerEntityMCA person,long time) { tick(world,person,time); }
    @Override protected void tick(ServerLevel world,VillagerEntityMCA person,long time) {
        var hand=GunCompatibility.hand(person);
        LivingEntity target=person.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (hand==null || target==null || target==person || !target.isAlive() || !person.canAttack(target)) return;
        ItemStack stack=person.getItemInHand(hand);
        if (weapon!=stack) { weapon=stack;reloading=false;readyAt=time; }
        if (person.isUsingCombatShield() || time<readyAt) return;
        if (reloading) { if (!GunCompatibility.reload(stack)) { readyAt=time+100;return; } reloading=false; }
        if (GunCompatibility.ammo(stack)<=0) { reloading=true;readyAt=time+GunCompatibility.stat(stack,"totalReloadTime",40);return; }
        if (person.distanceToSqr(target)>1024 || !person.hasLineOfSight(target)) return;
        var start=person.getEyePosition();var end=target.getEyePosition();
        var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(person,start,end,
                person.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),e -> e instanceof LivingEntity && e!=person && e.isAlive(),1024);
        if (hit!=null && hit.getEntity()!=target) return;
        person.getLookControl().setLookAt(target,90,90);
        if (GunCompatibility.fire(person,stack,target)) readyAt=time+GunCompatibility.stat(stack,"fireDelay",10);
        else readyAt=time+100;
    }
}
