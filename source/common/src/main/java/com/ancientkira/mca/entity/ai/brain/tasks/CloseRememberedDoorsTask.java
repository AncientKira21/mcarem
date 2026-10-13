package com.ancientkira.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.pathfinder.Path;

/** Keeps door reminders active after the walking path finishes or is cancelled. */
public class CloseRememberedDoorsTask extends Behavior<VillagerEntityMCA> {
    public CloseRememberedDoorsTask() {
        super(ImmutableMap.of(MemoryModuleType.DOORS_TO_CLOSE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.PATH, MemoryStatus.REGISTERED));
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        tickDoors(world, villager);
    }

    public static void tickDoors(ServerLevel world, VillagerEntityMCA villager) {
        // Combat can replace the brain path memory; navigation owns the actual current path.
        Path path = villager.getNavigation().getPath();
        boolean walking = path != null && !path.isDone();
        SmarterOpenDoorsTask.closeDoors(world, villager,
                walking ? path.getPreviousNode() : null,
                walking ? path.getNextNode() : null);
    }
}
