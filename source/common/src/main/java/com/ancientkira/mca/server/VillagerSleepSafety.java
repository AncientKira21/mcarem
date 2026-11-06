package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Traits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

public final class VillagerSleepSafety {
    private VillagerSleepSafety() {}
    public static void tick(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world) || !person.isAlive()) return;
        if (person.isRestingInInfirmary() && !world.getBlockState(person.blockPosition()).is(BlockTags.BEDS))
            person.setRestingInInfirmary(false);
        if (!person.isSleeping()) return;
        boolean inBed = person.getSleepingPos().filter(pos -> world.getBlockState(pos).is(BlockTags.BEDS)
                && person.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5) <= 2.25).isPresent();
        boolean night = world.isDarkOutside();
        if (!inBed && (!night || !person.getTraits().hasTrait(Traits.EXTREME_SLEEPER))) {
            person.stopSleeping();
            person.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        }
    }
}
