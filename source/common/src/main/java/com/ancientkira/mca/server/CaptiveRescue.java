package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MoveState;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Comparator;
import java.util.Map;

/** Villagers temporarily leave their routine to rescue a nearby captive. */
public final class CaptiveRescue {
    private static final Map<VillagerEntityMCA, Mission> MISSIONS = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private CaptiveRescue() { }
    public static boolean isHelping(VillagerEntityMCA person) { return MISSIONS.containsKey(person); }

    private static boolean available(VillagerEntityMCA person) {
        return person.isAlive() && !person.isNoAi() && !person.isKidnapped()
                && !ProfessionsMCA.isRaider(person.getProfession()) && person.getAgeState() == AgeState.ADULT
                && person.getHealth() > person.getMaxHealth() * 0.5F
                && person.getVillagerBrain().getMoveState() == MoveState.MOVE
                && person.getVillagerBrain().getCurrentJob() == com.ancientkira.mca.entity.ai.Chore.NONE
                && person.getInteractions().getInteractingPlayer().isEmpty()
                && !person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !ClericAid.isHelping(person) && !VillageWarfare.isFighting(person);
    }

    public static void tick(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world)) return;
        Mission mission = MISSIONS.get(person);
        if (mission == null && person.tickCount % 20 == 0 && available(person)) {
            var captive = world.getEntitiesOfClass(VillagerEntityMCA.class, person.getBoundingBox().inflate(128), victim ->
                    victim.isAlive() && victim.isKidnapped() && !victim.isInvisible()
                            && (RaiderSocial.isFamily(person, victim) || person.distanceToSqr(victim) <= 32 * 32)
                            && MISSIONS.values().stream().noneMatch(existing -> existing.victim == victim))
                    .stream().min(Comparator.<VillagerEntityMCA>comparingInt(victim -> RaiderSocial.isFamily(person, victim) ? 0 : 1)
                            .thenComparingDouble(person::distanceToSqr)).orElse(null);
            if (captive != null) {
                mission = new Mission(captive, world.getGameTime() + 1800);
                MISSIONS.put(person, mission);
            }
        }
        if (mission == null) return;
        VillagerEntityMCA victim = mission.victim;
        if (!available(person) || !victim.isAlive() || !victim.isKidnapped() || victim.level() != world
                || world.getGameTime() >= mission.expires) {
            finish(person);
            return;
        }
        if (person.isSleeping()) person.stopSleeping();
        person.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        person.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(victim, true));
        if (person.distanceToSqr(victim) <= 2.5 * 2.5 && person.hasLineOfSight(victim)) {
            victim.rescueFromKidnapping();
            finish(person);
        } else {
            person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(victim, false), 0.7F, 2));
        }
    }

    private static void finish(VillagerEntityMCA person) {
        MISSIONS.remove(person);
        person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        person.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        person.getNavigation().stop();
        person.getBrain().updateActivityFromSchedule(person.level().environmentAttributes(), person.level().getGameTime(), person.position());
    }

    private record Mission(VillagerEntityMCA victim, long expires) { }
}
