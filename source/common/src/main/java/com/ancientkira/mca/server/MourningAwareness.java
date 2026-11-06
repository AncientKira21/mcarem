package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public final class MourningAwareness {
    private MourningAwareness() {}
    public static boolean canSee(VillagerEntityMCA person) {
        return person.isAlive() && !person.isSleeping() && !person.hasEffect(MobEffects.BLINDNESS);
    }
    public static void miss(VillagerEntityMCA person,UUID victim) {
        if(!person.getUUID().equals(victim)) person.getUnseenDeaths().putBoolean(victim.toString(),true);
    }
    public static boolean visibleGrave(VillagerEntityMCA person,BlockPos grave) {
        if(!canSee(person) || person.distanceToSqr(Vec3.atCenterOf(grave))>32*32)return false;
        Vec3 target=Vec3.atCenterOf(grave).add(0,.3,0);
        var hit=person.level().clip(new ClipContext(person.getEyePosition(),target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,person));
        return hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(grave);
    }
    public static boolean knows(VillagerEntityMCA person,BlockPos grave,UUID victim) {
        if(!canSee(person))return false;
        if(!person.getUnseenDeaths().contains(victim.toString())) {
            boolean witnessed = person.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_PERSON)
                    .filter(victim::equals).isPresent();
            return witnessed || visibleGrave(person,grave);
        }
        if(!visibleGrave(person,grave))return false;
        person.getUnseenDeaths().remove(victim.toString());
        person.getVillagerBrain().modifyMoodValue(-5);
        person.getVillagerBrain().setGrieving();
        return true;
    }
    public static void tick(VillagerEntityMCA person) {
        if(person.level().isClientSide() || canSee(person))return;
        person.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_PERSON).ifPresent(victim->miss(person,victim));
        if(person.getBrain().isActive(com.ancientkira.mca.entity.ai.ActivitiesMCA.GRIEVE)) {
            person.getNavigation().stop();
            person.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            person.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
            person.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.IDLE);
        }
        person.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_SITE);
        person.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
        person.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_PERSON);
    }
}
