package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MoveState;
import com.ancientkira.mca.entity.ai.Chore;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import java.util.Comparator;

/** Short, voluntary visits between nearby relatives, including combat professions. */
public final class FamilyTogether {
    private FamilyTogether() { }

    private static boolean available(VillagerEntityMCA person) {
        return person.isAlive() && !person.isNoAi() && !person.isSleeping() && !person.isPassenger()
                && !person.isKidnapped() && person.getVillagerBrain().getMoveState() == MoveState.MOVE
                && person.getVillagerBrain().getCurrentJob() == Chore.NONE
                && person.getInteractions().getInteractingPlayer().isEmpty()
                && !person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !person.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
    }

    public static void tick(VillagerEntityMCA person) {
        if (person.tickCount % 100 != Math.floorMod(person.getId(), 100) || !available(person)
                || person.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                || PillagerRaids.getRaid(person).isPresent()) return;
        person.level().getEntitiesOfClass(VillagerEntityMCA.class, person.getBoundingBox().inflate(24),
                other -> other != person && available(other) && RaiderSocial.isFamily(person, other)
                        // One side approaches so pairs don't perpetually chase each other.
                        && person.getUUID().compareTo(other.getUUID()) > 0
                        && person.distanceToSqr(other) > 36)
                .stream().min(Comparator.comparingDouble(person::distanceToSqr))
                .ifPresent(relative -> person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(relative, 0.6F, 3)));
    }
}
