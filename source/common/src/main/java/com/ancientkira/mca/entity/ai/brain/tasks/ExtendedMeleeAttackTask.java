package com.ancientkira.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.entity.ai.Messenger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class ExtendedMeleeAttackTask extends Behavior<Mob> {
    private final int interval;
    private final MemoryModuleType<? extends LivingEntity> target;

    public ExtendedMeleeAttackTask(int interval) {
        this(interval, MemoryModuleType.ATTACK_TARGET);
    }

    public ExtendedMeleeAttackTask(int interval, MemoryModuleType<? extends LivingEntity> target) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, target, MemoryStatus.VALUE_PRESENT, MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.VALUE_ABSENT));
        this.interval = interval;
        this.target = target;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Mob attacker) {
        LivingEntity target = getTarget(attacker);
        return com.ancientkira.mca.entity.ai.MeleeAttack.canHit(attacker, target);
    }

    @Override
    protected void start(ServerLevel world, Mob mobEntity, long l) {
        LivingEntity livingEntity = getTarget(mobEntity);
        if (!com.ancientkira.mca.entity.ai.MeleeAttack.canHit(mobEntity, livingEntity)) return;
        if (mobEntity instanceof com.ancientkira.mca.entity.VillagerEntityMCA person && person.isUsingCombatShield()) {
            person.stopUsingItem();
        }
        BehaviorUtils.lookAtEntity(mobEntity, livingEntity);
        if (mobEntity instanceof VillagerLike<?> villager) {
            mobEntity.swing(villager.getDominantHand());
        } else {
            mobEntity.swing(InteractionHand.MAIN_HAND);
        }
        mobEntity.doHurtTarget(world, livingEntity);
        mobEntity.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, interval);

        // kill phrase
        if (livingEntity.isDeadOrDying() && mobEntity instanceof Messenger messenger) {
            if (mobEntity.getRandom().nextFloat() < 0.3) {
                messenger.sendChatToAllAround("villager.kill");
            }
        }
    }

    private LivingEntity getTarget(Mob mobEntity) {
        return mobEntity.getBrain().getMemoryInternal(target).get();
    }
}
