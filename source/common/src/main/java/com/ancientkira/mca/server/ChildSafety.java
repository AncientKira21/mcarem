package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.server.world.data.FamilyTree;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Comparator;
import java.util.Map;

/** Young villagers call their own living parents, even during their work schedule. */
public final class ChildSafety {
    private static final Map<VillagerEntityMCA, Long> NEXT_CALL = new com.google.common.collect.MapMaker().weakKeys().makeMap();

    private ChildSafety() { }

    public static void tick(VillagerEntityMCA child) {
        if (!(child.level() instanceof ServerLevel world) || child.tickCount % 20 != 0
                || !child.isAlive() || child.isNoAi() || child.isKidnapped()
                || child.getAgeState() == AgeState.TEEN || child.getAgeState() == AgeState.ADULT
                || child.getAgeState() == AgeState.UNASSIGNED) return;

        LivingEntity attacker = child.getLastHurtByMob();
        boolean attackedRecently = attacker != null && attacker.isAlive()
                && child.tickCount - child.getLastHurtByMobTimestamp() <= 200;
        boolean danger = attackedRecently || child.getVillagerBrain().isPanicking()
                || child.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)
                || world.getEntitiesOfClass(Mob.class, child.getBoundingBox().inflate(16),
                        mob -> mob.isAlive() && mob.getTarget() == child && child.hasLineOfSight(mob)).size() > 0;
        if (!danger) return;

        var family = child.getRelationships().getFamilyEntry();
        FamilyTree tree = FamilyTree.get(world);
        boolean motherAlive = tree.getOrEmpty(family.mother()).map(parent -> !parent.isDeceased()).orElse(false);
        boolean fatherAlive = tree.getOrEmpty(family.father()).map(parent -> !parent.isDeceased()).orElse(false);
        if (!motherAlive && !fatherAlive) return;

        long now = world.getGameTime();
        if (now >= NEXT_CALL.getOrDefault(child, 0L)) {
            NEXT_CALL.put(child, now + 200);
            String call = motherAlive && fatherAlive ? "parents" : motherAlive ? "mother" : "father";
            child.sendChatToAllAround(Component.translatable("mca.child.help." + call));
        }

        // A player parent receives the call; NPC parents are also a place to seek safety.
        child.getRelationships().getParents().filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                .filter(parent -> parent.isAlive() && parent.level() == world && child.distanceToSqr(parent) <= 128 * 128)
                .filter(parent -> !(parent instanceof VillagerEntityMCA person) || !person.isKidnapped())
                .filter(parent -> !attackedRecently || parent != attacker)
                .min(Comparator.comparingDouble(child::distanceToSqr)).ifPresent(parent -> {
                    if (child.getAgeState() == AgeState.BABY || child.getInteractions().getInteractingPlayer().isPresent()) return;
                    if (child.isSleeping()) child.stopSleeping();
                    child.getBrain().setActiveActivityIfPossible(Activity.IDLE);
                    child.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(parent, true));
                    child.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                            new WalkTarget(new EntityTracker(parent, false), 0.8F, 2));
                });
    }
}
