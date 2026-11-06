package com.ancientkira.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.ActivitiesMCA;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Optional;

public class GrieveTask extends Behavior<VillagerEntityMCA> {
    private long nextCheck;
    public GrieveTask() {
        super(ImmutableMap.of());
    }

    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        if (entity.getBrain().isActive(ActivitiesMCA.GRIEVE) || !com.ancientkira.mca.server.MourningAwareness.canSee(entity)
                || com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.hasMovementOrder(entity)
                || entity.getInteractions().getInteractingPlayer().isPresent() || entity.isTrading()
                || entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).filter(net.minecraft.world.entity.LivingEntity::isAlive).isPresent()
                || com.ancientkira.mca.server.InfirmaryCare.isPatient(entity)
                || com.ancientkira.mca.server.PortalHomecoming.isReturning(entity)) return false;
        if (world.getGameTime() < nextCheck) return false;
        nextCheck = world.getGameTime()+40+Math.floorMod(entity.getId(),20);
        if (!entity.getUnseenDeaths().isEmpty()) EnterGraveyardTask.hasPeriodicMourningCandidate(entity);
        if (!entity.getVillagerBrain().shouldGrieve()) return false;
        Optional<BlockPos> rememberedSite = entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE);
        if (rememberedSite.isPresent()) {
            if (!EnterGraveyardTask.hasMournableSite(entity)) {
                entity.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_SITE);
                entity.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
                entity.getVillagerBrain().justGrieved();
                return false;
            }
            return entity.getVillagerBrain().shouldGrieve();
        }

        return EnterGraveyardTask.hasPeriodicMourningCandidate(entity)
                && entity.getVillagerBrain().shouldGrieve();
    }

    @Override
    protected void start(ServerLevel serverWorld, VillagerEntityMCA villager, long l) {
        Brain<Villager> brain = villager.getBrain();
        if (!brain.isActive(ActivitiesMCA.GRIEVE)) {
            brain.eraseMemory(MemoryModuleType.PATH);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            brain.eraseMemory(MemoryModuleType.BREED_TARGET);
            brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        }
        villager.getMCABrain().setActiveActivityIfPossible(ActivitiesMCA.GRIEVE);
    }
}
