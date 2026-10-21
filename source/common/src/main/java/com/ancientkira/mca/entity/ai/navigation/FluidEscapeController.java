package com.ancientkira.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Three-dimensional escape routes, independent of land-only path restrictions. */
final class FluidEscapeController {
    private static final int RANGE = 12;
    private static final int MAX_NODES = 2048;
    private final Mob mob;
    private final Level world;
    private final ArrayDeque<BlockPos> route = new ArrayDeque<>();
    private int nextSearch;
    private boolean escaping;
    private boolean escapingLava;

    FluidEscapeController(Mob mob, Level world) {
        this.mob = mob;
        this.world = world;
    }

    boolean active() {
        return !mob.isPassenger() && (mob.isInWater() || mob.isInLava()
                || escaping && (!TerrainSafety.solidFloor(world, mob.blockPosition())
                    || !route.isEmpty() || TerrainSafety.lavaNear(world, mob.getBoundingBox(), 0)));
    }

    boolean tick() {
        if (!active()) {
            route.clear();
            escaping = false;
            escapingLava = false;
            nextSearch = 0;
            return false;
        }
        if (!escaping) escapingLava = mob.isInLava();
        escaping = true;
        if (mob.isInLava()) escapingLava = true;
        mob.setJumping(false);
        boolean lava = escapingLava;
        if ((!route.isEmpty() && !passable(route.peekFirst(), lava)) || mob.tickCount >= nextSearch) {
            findRoute(lava);
            nextSearch = mob.tickCount + 20;
        }
        while (!route.isEmpty() && Vec3.atBottomCenterOf(route.peekFirst()).distanceToSqr(mob.position()) < 0.0225) {
            route.removeFirst();
        }
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
        mob.setXxa(0);
        mob.setZza(0);
        if (route.isEmpty()) {
            boolean headUnderFluid = !world.getFluidState(BlockPos.containing(mob.getEyePosition())).isEmpty();
            boolean canRise = !world.getBlockCollisions(mob, mob.getBoundingBox().move(0, 0.25, 0)).iterator().hasNext();
            mob.setDeltaMovement(0, headUnderFluid && canRise ? 0.06 : 0, 0);
            return true;
        }
        Vec3 target = Vec3.atBottomCenterOf(route.peekFirst());
        Vec3 delta = target.subtract(mob.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double speed = lava ? 0.11 : 0.15;
        double scale = horizontal > speed ? speed / horizontal : 1;
        double vertical = Math.clamp(delta.y * 0.35, -0.12, 0.12);
        // Rise before moving over a bank; lava drag otherwise pins the body against its lip.
        if (delta.y > 0.15 && (lava || TerrainSafety.solidFloor(world, route.peekFirst()))) vertical = 0.28;
        if (mob.horizontalCollision && delta.y >= -0.1
                && !world.getBlockCollisions(mob, mob.getBoundingBox().move(0, 0.3, 0)).iterator().hasNext())
            vertical = Math.max(vertical, 0.3);
        mob.setDeltaMovement(delta.x * scale, vertical, delta.z * scale);
        if (!lava && horizontal > 0.02) {
            float yaw = (float)(Math.atan2(delta.z, delta.x) * 180.0 / Math.PI) - 90.0F;
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
        }
        mob.getLookControl().setLookAt(target.x, target.y + mob.getEyeHeight(), target.z);
        return true;
    }

    private boolean passable(BlockPos pos, boolean lava) {
        if (!world.hasChunkAt(pos) || pos.getY() < world.getMinY() || pos.getY() >= world.getMaxY()) return false;
        if (!lava && (world.getFluidState(pos).is(FluidTags.LAVA)
                || world.getFluidState(pos.above()).is(FluidTags.LAVA))) return false;
        // Moving entities are temporary crowding, not permanent terrain walls.
        return !world.getBlockCollisions(mob, mob.getBoundingBox().move(
                Vec3.atBottomCenterOf(pos).subtract(mob.position()))).iterator().hasNext();
    }

    private void findRoute(boolean lava) {
        route.clear();
        BlockPos start = mob.blockPosition();
        var queue = new ArrayDeque<BlockPos>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        previous.put(start, start);
        queue.add(start);
        BlockPos air = null;
        BlockPos exit = null;
        // Downward branches remain available when a roof prevents surfacing.
        Direction[] directions = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN};
        while (!queue.isEmpty() && previous.size() <= MAX_NODES) {
            BlockPos pos = queue.removeFirst();
            if (TerrainSafety.solidFloor(world, pos) && TerrainSafety.clear(world, pos)
                    && !TerrainSafety.lavaNear(world, mob.getBoundingBox().move(
                            Vec3.atBottomCenterOf(pos).subtract(mob.position())), 0.15)) {
                exit = pos;
                break;
            }
            // A horizontal swimmer's eyes are lower than a standing villager's.
            // Reach actual breathing air rather than stopping one block below it.
            BlockPos eyes = BlockPos.containing(pos.getX() + .5, pos.getY() + mob.getEyeHeight(), pos.getZ() + .5);
            if (air == null && world.getFluidState(eyes).isEmpty() && world.getFluidState(pos.above()).isEmpty()
                    && world.getBlockState(pos.above()).getCollisionShape(world, pos.above()).isEmpty()) air = pos;
            for (Direction direction : directions) {
                BlockPos next = pos.relative(direction);
                if (Math.abs(next.getX() - start.getX()) > RANGE || Math.abs(next.getZ() - start.getZ()) > RANGE
                        || Math.abs(next.getY() - start.getY()) > RANGE || previous.containsKey(next)
                        || !passable(next, lava)) continue;
                // Surface air above liquid is needed for an up-and-over bank exit.
                // Unsupported air elsewhere still cannot form a flying route.
                if (world.getFluidState(next).isEmpty() && world.getFluidState(next.below()).isEmpty()
                        && !TerrainSafety.solidFloor(world, next)) continue;
                previous.put(next, pos);
                queue.addLast(next);
            }
        }
        BlockPos goal = exit == null ? air : exit;
        // A partly submerged body may already occupy the shore block: still center it on dry ground.
        if (exit != null && exit.equals(start)) route.add(start);
        while (goal != null && !goal.equals(start)) {
            route.addFirst(goal);
            goal = previous.get(goal);
        }
    }
}
