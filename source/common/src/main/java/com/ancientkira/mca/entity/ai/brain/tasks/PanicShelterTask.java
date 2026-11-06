package com.ancientkira.mca.entity.ai.brain.tasks;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA;
import com.ancientkira.mca.entity.ai.navigation.TerrainSafety;
import com.ancientkira.mca.server.UnderwaterVillageSupport;
import com.ancientkira.mca.server.world.data.Building;
import com.ancientkira.mca.server.world.data.Village;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Gives nearby, reachable indoor shelter precedence over random panic wandering. */
public final class PanicShelterTask extends Behavior<VillagerEntityMCA> {
    private BlockPos shelter;
    private Building building;
    private long nextSearch;

    public PanicShelterTask() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), 1200);
    }
    private boolean needsShelter(VillagerEntityMCA person) {
        return person.isAlive() && !person.isSleeping() && !person.isKidnapped()
                && !VillagerTasksMCA.hasMovementOrder(person)
                && !com.ancientkira.mca.server.VillageWarfare.isFunFighting(person)
                && (person.isTooBusyToTalk() || person.getVillagerBrain().isPanicking());
    }
    @Override protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA person) {
        if (!needsShelter(person) || world.getGameTime() < nextSearch) return false;
        nextSearch = world.getGameTime() + 40;
        shelter = null; building = null;
        var village = person.getResidency().getHomeVillage().or(() -> Village.findNearest(person));
        if (village.isEmpty()) return false;
        List<Building> nearby = village.get().getBuildings().values().stream()
                .filter(b -> !b.getBuildingType().grouped() && b.getCenter().distSqr(person.blockPosition()) <= 32 * 32)
                .sorted(Comparator.comparingDouble(b -> b.getSourceBlock().distSqr(person.blockPosition())))
                .limit(4).toList();
        boolean flooded = UnderwaterVillageSupport.isAroundUnderwaterVillage(world, person.blockPosition());
        for (Building room : nearby) {
            List<BlockPos> candidates = new ArrayList<>();
            for (BlockPos anchor : List.of(room.getSourceBlock(), room.getCenter())) {
                for (BlockPos candidate : BlockPos.betweenClosed(anchor.offset(-1,-2,-1), anchor.offset(1,2,1))) {
                    if (room.containsPos(candidate) && safeIndoor(world, person, candidate, flooded)) candidates.add(candidate.immutable());
                }
            }
            candidates.sort(Comparator.comparingDouble(p -> p.distSqr(person.blockPosition())));
            int attempts = 0;
            for (BlockPos candidate : candidates) {
                if (attempts++ >= 3) break;
                var path = person.getNavigation().createPath(candidate, 1);
                if (person.blockPosition().distSqr(candidate) < 2 || path != null && path.canReach()) {
                    shelter = candidate; building = room; return true;
                }
            }
        }
        return false;
    }
    private static boolean safeIndoor(ServerLevel world, VillagerEntityMCA person, BlockPos pos, boolean flooded) {
        if (!world.hasChunkAt(pos) || !TerrainSafety.solidFloor(world, pos)
                || TerrainSafety.dangerousEdge(world, pos, !flooded)
                || !flooded && !world.getFluidState(pos).isEmpty()) return false;
        if (!world.noCollision(person, person.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(person.position())))) return false;
        for (int up = 1; up <= 12; up++) {
            BlockPos roof = pos.above(up);
            if (world.getBlockState(roof).isFaceSturdy(world, roof, Direction.DOWN)) return true;
        }
        return false;
    }
    @Override protected boolean canStillUse(ServerLevel world, VillagerEntityMCA person, long time) {
        return shelter != null && needsShelter(person)
                && safeIndoor(world, person, shelter, UnderwaterVillageSupport.isAroundUnderwaterVillage(world, shelter));
    }
    @Override protected void tick(ServerLevel world, VillagerEntityMCA person, long time) {
        // Once sheltered, don't let the generic panic stroll send them straight back outside.
        if (building.containsPos(person.blockPosition())
                && safeIndoor(world, person, person.blockPosition(), UnderwaterVillageSupport.isAroundUnderwaterVillage(world, person.blockPosition()))) {
            person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            person.getNavigation().stop();
        } else {
            person.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(shelter, 0.85F, 1));
        }
    }
    @Override protected void stop(ServerLevel world, VillagerEntityMCA person, long time) {
        var walk = person.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        if (walk != null && shelter != null && walk.getTarget().currentBlockPosition().equals(shelter)) {
            person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            person.getNavigation().stop();
        }
        shelter = null; building = null;
    }
}
