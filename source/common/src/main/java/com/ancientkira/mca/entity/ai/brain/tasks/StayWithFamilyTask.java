package com.ancientkira.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Chore;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import com.ancientkira.mca.entity.ai.MoveState;
import com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Ensures spouses and relatives stay together without causing deadlocks
 * or preventing villagers from reaching their destinations.
 *
 * Employs a strict Leader-Follower asymmetry so that the leader is free
 * to travel to buildings, meeting points, and workstations while the
 * follower accompanies them.
 */
public class StayWithFamilyTask extends Behavior<VillagerEntityMCA> {
    private static final double MAX_COMPANION_DISTANCE_SQR = 64.0 * 64.0;
    private static final double COMFORT_MIN_DISTANCE_SQR = 2.5 * 2.5;
    private static final double COMFORT_MAX_DISTANCE_SQR = 5.0 * 5.0;

    private final float speedModifier;
    private LivingEntity targetCompanion;

    public StayWithFamilyTask(float speedModifier) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), 100);
        this.speedModifier = speedModifier;
    }

    public static boolean canRun(VillagerEntityMCA villager) {
        if (!villager.isAlive() || villager.isRemoved() || villager.isSleeping()) {
            return false;
        }
        if (VillagerTasksMCA.isInDanger(villager) || villager.getVillagerBrain().isPanicking()) {
            return false;
        }
        if (villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.STAYING)
                || villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.PLAYER_FOLLOWING)
                || villager.getVillagerBrain().getMoveState() != MoveState.MOVE) {
            return false;
        }
        if (villager.getVillagerBrain().getCurrentJob() != Chore.NONE) {
            return false;
        }
        if (villager.getHangoutState().getBooleanOr("Active", false)) {
            return false;
        }
        // If during village meeting time, allow villagers to reach the meeting bell first
        if (villager.getBrain().isActive(Activity.MEET)) {
            Optional<GlobalPos> meetingPoint = villager.getBrain().getMemoryInternal(MemoryModuleType.MEETING_POINT);
            if (meetingPoint.isPresent() && meetingPoint.get().dimension().equals(villager.level().dimension())) {
                if (villager.blockPosition().distSqr(meetingPoint.get().pos()) > 8 * 8) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Determines whether a villager already has an independent destination
     * (e.g. Walking to a workstation, bed, building, or bell).
     */
    public static boolean hasIndependentDestination(VillagerEntityMCA villager) {
        if (!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return false;
        }
        WalkTarget walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        if (walkTarget == null) {
            return false;
        }
        if (walkTarget.getTarget() instanceof EntityTracker entityTracker) {
            Entity targetEntity = entityTracker.getEntity();
            if (targetEntity instanceof VillagerEntityMCA) {
                return false;
            }
        }
        return true;
    }

    /**
     * Strict leader determination. The leader is free to pick wander targets,
     * enter buildings, and travel, while the follower accompanies them.
     */
    public static boolean isLeader(VillagerEntityMCA villager, LivingEntity companion) {
        // 1. Players always lead NPCs
        if (companion instanceof ServerPlayer) {
            return false;
        }

        // 2. Adults always lead children
        boolean villagerIsAdult = villager.getAgeState() == AgeState.ADULT;
        if (companion instanceof VillagerEntityMCA companionVillager) {
            boolean companionIsAdult = companionVillager.getAgeState() == AgeState.ADULT;
            if (villagerIsAdult && !companionIsAdult) {
                return true;
            }
            if (!villagerIsAdult && companionIsAdult) {
                return false;
            }

            // Both are children: older child leads
            if (!villagerIsAdult && !companionIsAdult) {
                if (villager.getAgeState().ordinal() > companionVillager.getAgeState().ordinal()) {
                    return true;
                }
                if (villager.getAgeState().ordinal() < companionVillager.getAgeState().ordinal()) {
                    return false;
                }
            }

            // If one has an active independent destination (e.g. going to a building or workplace), they lead!
            boolean villagerHasDest = hasIndependentDestination(villager);
            boolean companionHasDest = hasIndependentDestination(companionVillager);
            if (villagerHasDest && !companionHasDest) {
                return true;
            }
            if (!villagerHasDest && companionHasDest) {
                return false;
            }
        }

        // Deterministic tie-breaker to prevent mutual following loops
        return villager.getUUID().compareTo(companion.getUUID()) < 0;
    }

    public static LivingEntity findFamilyCompanion(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return null;
        }

        // 1. Spouses stay together above all else!
        Optional<UUID> partnerUUID = villager.getRelationships().getPartnerUUID();
        if (partnerUUID.isPresent()) {
            UUID id = partnerUUID.get();
            Entity partner = level.getEntity(id);
            if (partner == null) {
                partner = level.getServer().getPlayerList().getPlayer(id);
            }
            if (partner instanceof LivingEntity living && living.isAlive() && !living.isRemoved()
                    && living.level().dimension().equals(level.dimension())
                    && villager.distanceToSqr(living) <= MAX_COMPANION_DISTANCE_SQR) {
                return living;
            }
        }

        // 2. Children, toddlers, babies, and teens stay near parents!
        if (villager.getAgeState() != AgeState.ADULT) {
            LivingEntity parent = villager.getRelationships().getParents()
                    .filter(p -> p instanceof LivingEntity living && living.isAlive() && !living.isRemoved()
                            && living.level().dimension().equals(level.dimension())
                            && villager.distanceToSqr(living) <= MAX_COMPANION_DISTANCE_SQR)
                    .map(LivingEntity.class::cast)
                    .min(Comparator.comparingDouble(villager::distanceToSqr))
                    .orElse(null);
            if (parent != null) {
                return parent;
            }

            // If no parent is nearby, stay with siblings!
            FamilyTreeNode familyEntry = villager.getRelationships().getFamilyEntry();
            LivingEntity sibling = familyEntry.siblings().stream()
                    .map(level::getEntity)
                    .filter(s -> s instanceof LivingEntity living && living.isAlive() && !living.isRemoved()
                            && living.level().dimension().equals(level.dimension())
                            && villager.distanceToSqr(living) <= MAX_COMPANION_DISTANCE_SQR)
                    .map(LivingEntity.class::cast)
                    .min(Comparator.comparingDouble(villager::distanceToSqr))
                    .orElse(null);
            if (sibling != null) {
                return sibling;
            }
        }

        // 3. Other close relatives (adult children, adult siblings)
        FamilyTreeNode familyEntry = villager.getRelationships().getFamilyEntry();
        LivingEntity relative = level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(32),
                other -> other != villager && other.isAlive() && !other.isRemoved()
                        && familyEntry.isFamilyMember(other.getUUID()))
                .stream()
                .min(Comparator.comparingDouble(villager::distanceToSqr))
                .orElse(null);

        return relative;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        if (!canRun(villager)) {
            return false;
        }

        // If this villager already has an independent destination (e.g. walking to a building),
        // let them travel freely and lead!
        if (hasIndependentDestination(villager)) {
            return false;
        }

        LivingEntity companion = findFamilyCompanion(villager);
        if (companion == null) {
            return false;
        }

        // If this villager is the leader, do not run the follower behavior to chase the companion!
        // The leader must be free to pick destinations, enter buildings, etc.
        if (isLeader(villager, companion)) {
            return false;
        }

        this.targetCompanion = companion;
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (!canRun(villager) || targetCompanion == null || !targetCompanion.isAlive()
                || targetCompanion.isRemoved() || !targetCompanion.level().dimension().equals(world.dimension())
                || villager.distanceToSqr(targetCompanion) > MAX_COMPANION_DISTANCE_SQR) {
            return false;
        }
        // If this villager has picked an independent destination, allow them to travel to it
        if (hasIndependentDestination(villager)) {
            return false;
        }
        // If roles changed and this villager is now the leader, stop following
        if (isLeader(villager, targetCompanion)) {
            return false;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        tickTogether(villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        tickTogether(villager);
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        this.targetCompanion = null;
    }

    private void tickTogether(VillagerEntityMCA villager) {
        if (targetCompanion == null) {
            return;
        }

        double distSqr = villager.distanceToSqr(targetCompanion);

        // Always keep companion in gaze
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(targetCompanion, true));

        // If further than comfortable proximity, move towards companion
        if (distSqr > COMFORT_MAX_DISTANCE_SQR) {
            float speed = distSqr > 16.0 * 16.0 ? Math.min(0.65F, speedModifier * 1.2F) : speedModifier;
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(targetCompanion, false), speed, 3));
        } else if (distSqr >= COMFORT_MIN_DISTANCE_SQR) {
            // Within comfortable companionship range (2.5 to 5 blocks)
            // If the leader is actively walking somewhere, accompany them towards where they are going!
            if (targetCompanion instanceof VillagerEntityMCA companionVillager
                    && companionVillager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                WalkTarget leaderTarget = companionVillager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
                if (leaderTarget != null && !(leaderTarget.getTarget() instanceof EntityTracker tracker && tracker.getEntity() == villager)) {
                    villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                            new WalkTarget(new EntityTracker(targetCompanion, false), speedModifier * 0.95F, 2));
                }
            } else if (targetCompanion instanceof ServerPlayer player && player.getDeltaMovement().lengthSqr() > 0.01) {
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new EntityTracker(player, false), speedModifier * 0.95F, 2));
            } else {
                // Both are lingering together at their destination
                if (villager.tickCount % 40 == 0) {
                    villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, targetCompanion);
                }
                // Occasionally make small leisurely steps near the leader so they don't look stiff
                if (villager.tickCount % 120 == 0 && villager.getRandom().nextFloat() < 0.25F) {
                    Vec3 strollOffset = new Vec3(
                            (villager.getRandom().nextDouble() - 0.5) * 3.0,
                            0,
                            (villager.getRandom().nextDouble() - 0.5) * 3.0
                    );
                    BlockPos strollTarget = BlockPos.containing(targetCompanion.position().add(strollOffset));
                    villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                            new WalkTarget(strollTarget, speedModifier * 0.5F, 1));
                }
            }
        } else {
            // Already close (< 2.5 blocks)
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            if (villager.tickCount % 60 == 0) {
                villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, targetCompanion);
            }
        }
    }
}
