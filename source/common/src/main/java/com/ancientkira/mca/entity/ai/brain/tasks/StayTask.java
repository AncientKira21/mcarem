package com.ancientkira.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StayTask extends Behavior<VillagerEntityMCA> {
    private net.minecraft.core.BlockPos waitingPosition;
    public StayTask() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return villager.getMCABrain().getMemoryInternal(MemoryModuleTypeMCA.STAYING).isPresent() && (villager.isGuard() || !villager.getVillagerBrain().isPanicking());
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return this.checkExtraStartConditions(world, villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (waitingPosition == null) waitingPosition = villager.blockPosition();
        villager.getNavigation().stop();
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (villager.isGuard() && waitingPosition != null
                && villager.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(waitingPosition)) > 2.25) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(waitingPosition, 0.6F, 0));
            return;
        }
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}
