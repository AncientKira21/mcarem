package com.ancientkira.mca.entity.ai.navigation;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MoveState;
import com.ancientkira.mca.server.UnderwaterVillageSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Three-dimensional, bounded water routes rather than surface-projected walking paths. */
public final class UnderwaterNavigation {
    private static final Map<VillagerEntityMCA, Route> ROUTES = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final class Route { BlockPos goal; Vec3 waypoint; int nextSearch; }
    private UnderwaterNavigation() {}

    public static boolean isNonSwimmingBaby(Mob mob) {
        return mob instanceof VillagerEntityMCA person
                && (person.getAgeState() == com.ancientkira.mca.entity.ai.relationship.AgeState.BABY
                    || person.getAgeState() == com.ancientkira.mca.entity.ai.relationship.AgeState.UNASSIGNED && person.isBaby());
    }

    /** Bubble-column forces must not be overwritten by powered swimming or surfacing. */
    public static boolean canControlWater(Mob mob) {
        if (isNonSwimmingBaby(mob)) return false;
        var box = mob.getBoundingBox().deflate(0.001);
        for (BlockPos pos : BlockPos.betweenClosed(net.minecraft.util.Mth.floor(box.minX), net.minecraft.util.Mth.floor(box.minY),
                net.minecraft.util.Mth.floor(box.minZ), net.minecraft.util.Mth.floor(box.maxX),
                net.minecraft.util.Mth.floor(box.maxY), net.minecraft.util.Mth.floor(box.maxZ))) {
            if (mob.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN)) return false;
        }
        return true;
    }

    public static boolean wantsToSwim(VillagerEntityMCA person) {
        if (!canControlWater(person) || person.getInteractions().getInteractingPlayer().isPresent() || person.isSleeping()
                || person.getVillagerBrain().getMoveState() == MoveState.STAY) return false;
        var walk = person.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        return walk != null && person.position().distanceToSqr(walk.getTarget().currentPosition())
                > Math.max(0.36, walk.getCloseEnoughDist() * walk.getCloseEnoughDist())
                && (person.isSprinting() || walk.getSpeedModifier() > 0.6F)
                || person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    /** Dry rooms in an underwater village must still have routes back into the ocean. */
    public static boolean canEnterVillageWater(Mob mob) {
        return mob instanceof VillagerEntityMCA && !isNonSwimmingBaby(mob)
                && UnderwaterVillageSupport.isAroundUnderwaterVillage(mob.level(), mob.blockPosition());
    }

    public static boolean floorWalking(VillagerEntityMCA person) {
        return person.isInWater() && !wantsToSwim(person)
                && UnderwaterVillageSupport.isAroundUnderwaterVillage(person.level(), person.blockPosition());
    }

    public static boolean active(VillagerEntityMCA person) {
        if (!wantsToSwim(person) || !person.isInWater() || person.isInLava() || person.isPassenger() || person.isSleeping()) return false;
        var target = person.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
        return UnderwaterVillageSupport.isAroundUnderwaterVillage(person.level(), person.blockPosition())
                || target != null && target.isAlive() && target.isInWater() && person.getAirSupply() > 100;
    }

    public static boolean tick(VillagerEntityMCA person) {
        if (!active(person)) { ROUTES.remove(person); return false; }
        person.setJumping(false);
        var brain = person.getBrain();
        var walk = brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        Vec3 goal = walk == null ? null : walk.getTarget().currentPosition();
        double speed = walk == null ? 0.6 : walk.getSpeedModifier();
        if (person.getVillagerBrain().getMoveState() == MoveState.STAY) goal = null;
        var enemy = brain.getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (enemy != null && enemy.isAlive() && person.getVillagerBrain().getMoveState() == MoveState.MOVE) {
            double range = com.ancientkira.mca.entity.ai.RangedWeaponHelper.getAttackRangeSquared(person, 225);
            boolean ranged = range > 0;
            double stopDistance = ranged ? range * 0.65 : 3;
            double distance = person.distanceToSqr(enemy);
            // Combat routes can rise off the floor; patrols never use this route.
            double height = ranged && (Math.floorMod(person.getUUID().hashCode(), 3) == 0)
                    ? 1.0 + Math.sin((person.tickCount + person.getId()) * 0.02) * 0.3 : 0;
            Vec3 aimPosition = enemy.position().add(0, height, 0);
            if (distance > stopDistance || height > 0 && Math.abs(person.getY() - aimPosition.y) > 0.5) goal = aimPosition;
            else goal = null;
        }
        if (goal == null || person.position().distanceToSqr(goal) < 0.36) {
            person.getMoveControl().setWantedPosition(person.getX(), person.getY(), person.getZ(), 0);
            person.setXxa(0); person.setZza(0); person.setYya(0);
            return true;
        }
        Route route = ROUTES.computeIfAbsent(person, p -> new Route());
        BlockPos target = BlockPos.containing(goal);
        if (person.tickCount >= route.nextSearch
                || route.waypoint != null && person.position().distanceToSqr(route.waypoint) < 0.3) {
            route.nextSearch = person.tickCount + 10; route.goal = target;
            route.waypoint = waypoint(person, target);
        }
        Vec3 next = route.waypoint;
        if (next == null) {
            person.getMoveControl().setWantedPosition(person.getX(), person.getY(), person.getZ(), 0);
            return false; // Let ground navigation try a different route or leave the water.
        }
        if (next != null) {
            com.ancientkira.mca.entity.ai.brain.tasks.SmarterOpenDoorsTask.openForSwimming(
                    (net.minecraft.server.level.ServerLevel) person.level(), person, next);
            person.getMoveControl().setWantedPosition(next.x, next.y, next.z, speed);
        }
        return true;
    }

    public static Vec3 waypoint(net.minecraft.world.entity.Mob person, BlockPos goal) {
        BlockPos start = person.blockPosition();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        queue.add(start); parents.put(start, start); BlockPos best = start;
        for (int visited = 0; !queue.isEmpty() && visited < 256; visited++) {
            BlockPos pos = queue.removeFirst();
            if (pos.distSqr(goal) < best.distSqr(goal)) best = pos;
            if (pos.equals(goal)) break;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (parents.containsKey(next) || next.distSqr(start) > 100 || !person.level().hasChunkAt(next)) continue;
                if (!person.level().getFluidState(next).is(FluidTags.WATER)
                        && !canOpenSwimmingDoor(person, person.level().getBlockState(next))
                        && !(person.level().getBlockState(next).isAir() && person.level().getBlockState(next.below()).isFaceSturdy(person.level(), next.below(), Direction.UP))) continue;
                Vec3 point = Vec3.atBottomCenterOf(next);
                if (!clearSwimmingStep(person, person.getBoundingBox().move(point.subtract(person.position())))) continue;
                parents.put(next, pos); queue.addLast(next);
            }
        }
        if (best.equals(start)) return null;
        while (!parents.get(best).equals(start)) best = parents.get(best);
        return Vec3.atBottomCenterOf(best);
    }
    private static boolean canOpenSwimmingDoor(net.minecraft.world.entity.Mob person,
                                               net.minecraft.world.level.block.state.BlockState state) {
        return person instanceof VillagerEntityMCA && state.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)
                && state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
    }

    /** Plan through openable door panels without ignoring any neighboring wall or floor. */
    private static boolean clearSwimmingStep(net.minecraft.world.entity.Mob person, net.minecraft.world.phys.AABB box) {
        var world = person.level();
        // Moving neighbors must not permanently seal every exit in a crowded room.
        // Physical entity collisions still apply during movement.
        if (!world.getWorldBorder().isWithinBounds(box)) return false;
        var body = net.minecraft.world.phys.shapes.Shapes.create(box);
        var context = net.minecraft.world.phys.shapes.CollisionContext.of(person);
        for (BlockPos pos : BlockPos.betweenClosed(net.minecraft.util.Mth.floor(box.minX), net.minecraft.util.Mth.floor(box.minY),
                net.minecraft.util.Mth.floor(box.minZ), net.minecraft.util.Mth.floor(box.maxX - 1.0E-7),
                net.minecraft.util.Mth.floor(box.maxY - 1.0E-7), net.minecraft.util.Mth.floor(box.maxZ - 1.0E-7))) {
            if (!world.hasChunkAt(pos)) return false;
            var state = world.getBlockState(pos);
            if (canOpenSwimmingDoor(person, state)) continue;
            var shape = state.getCollisionShape(world, pos, context).move(pos.getX(), pos.getY(), pos.getZ());
            if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(body, shape, net.minecraft.world.phys.shapes.BooleanOp.AND)) return false;
        }
        return true;
    }

}
