package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.Status;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles relatives, particularly spouses, soothing screaming villagers.
 */
public final class FamilyCalm {
    private FamilyCalm() { }

    private record PendingCalm(UUID screamerId, UUID calmerId, long triggerTime, boolean isSpouse) { }

    private static final Map<UUID, Long> LAST_CALMED_TIME = new ConcurrentHashMap<>();
    private static final List<PendingCalm> PENDING_CALMS = new ArrayList<>();

    public static void onVillagerScream(VillagerEntityMCA screamer) {
        if (screamer.level().isClientSide() || !screamer.isAlive()) return;

        long now = screamer.level().getGameTime();
        Long lastCalmed = LAST_CALMED_TIME.get(screamer.getUUID());
        if (lastCalmed != null && now - lastCalmed < 180) { // Cooldown between calming events
            return;
        }

        List<VillagerEntityMCA> candidates = screamer.level().getEntitiesOfClass(
                VillagerEntityMCA.class,
                screamer.getBoundingBox().inflate(16.0),
                other -> other != screamer && other.isAlive() && !other.isSleeping() && !other.isNoAi()
                        && !other.isToYoungToSpeak() && !other.isKidnapped()
                        && RaiderSocial.isFamily(screamer, other)
        );

        if (candidates.isEmpty()) return;

        // Prioritize spouse first, then parents, then children, then other relatives, then by closest distance
        Comparator<VillagerEntityMCA> priority = Comparator
                .<VillagerEntityMCA>comparingInt(c -> {
                    if (c.getRelationships().isMarriedTo(screamer.getUUID())) return 0;
                    if (screamer.getRelationships().getFamilyEntry().isParent(c.getUUID())) return 1;
                    if (c.getRelationships().getFamilyEntry().isParent(screamer.getUUID())) return 2;
                    return 3;
                })
                .thenComparingDouble(c -> c.distanceToSqr(screamer));

        VillagerEntityMCA calmer = candidates.stream().min(priority).orElse(null);
        if (calmer == null) return;

        boolean isSpouse = calmer.getRelationships().isMarriedTo(screamer.getUUID());
        LAST_CALMED_TIME.put(screamer.getUUID(), now);

        // Schedule comforting dialogue 14 ticks after the scream for natural reaction pacing
        synchronized (PENDING_CALMS) {
            PENDING_CALMS.add(new PendingCalm(screamer.getUUID(), calmer.getUUID(), now + 14, isSpouse));
        }
    }

    public static void tick(VillagerEntityMCA entity) {
        if (entity.level().isClientSide() || PENDING_CALMS.isEmpty()) return;

        long now = entity.level().getGameTime();
        List<PendingCalm> due = new ArrayList<>();

        synchronized (PENDING_CALMS) {
            Iterator<PendingCalm> it = PENDING_CALMS.iterator();
            while (it.hasNext()) {
                PendingCalm pending = it.next();
                if (now >= pending.triggerTime()) {
                    due.add(pending);
                    it.remove();
                }
            }
        }

        for (PendingCalm pending : due) {
            if (entity.level() instanceof ServerLevel serverLevel) {
                if (serverLevel.getEntity(pending.screamerId()) instanceof VillagerEntityMCA screamer
                        && serverLevel.getEntity(pending.calmerId()) instanceof VillagerEntityMCA calmer) {
                    executeCalm(screamer, calmer, pending.isSpouse());
                }
            }
        }
    }

    private static void executeCalm(VillagerEntityMCA screamer, VillagerEntityMCA calmer, boolean isSpouse) {
        if (!screamer.isAlive() || !calmer.isAlive() || screamer.distanceToSqr(calmer) > 24 * 24) return;

        // Calming dialogue from comforting relative
        calmer.sendChatToAllAround(isSpouse ? "villager.calm.spouse" : "villager.calm.relative");

        // Face each other
        calmer.getLookControl().setLookAt(screamer, 30.0F, 30.0F);
        screamer.getLookControl().setLookAt(calmer, 30.0F, 30.0F);

        // Step towards the screamer if not already close and calmer is not panicking
        if (calmer.distanceToSqr(screamer) > 4.0 && !calmer.getVillagerBrain().isPanicking()) {
            calmer.getNavigation().moveTo(screamer, 0.65);
        }

        // Heart and comfort reassurance particles
        screamer.level().broadcastEntityEvent(screamer, Status.MCA_VILLAGER_POS_INTERACTION);
        calmer.level().broadcastEntityEvent(calmer, Status.MCA_VILLAGER_POS_INTERACTION);

        // Relieve panic talk block and soothe panic
        screamer.clearPanicTalkBlock();
        screamer.getVillagerBrain().modifyMoodValue(isSpouse ? 5 : 3);

        boolean immediateThreat = !screamer.level().getEntitiesOfClass(
                LivingEntity.class,
                screamer.getBoundingBox().inflate(6.0),
                e -> e instanceof Enemy && e.isAlive()
        ).isEmpty();

        if (!immediateThreat) {
            screamer.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
            screamer.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        }
    }
}
