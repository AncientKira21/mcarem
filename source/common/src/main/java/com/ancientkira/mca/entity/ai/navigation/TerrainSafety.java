package com.ancientkira.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;

/** Shared checks for both route planning and movement, including freshly changed terrain. */
public final class TerrainSafety {
    private TerrainSafety() { }

    /** Detect lava around the actual body footprint, including below a bank lip. */
    public static boolean lavaNear(net.minecraft.world.level.BlockGetter world, net.minecraft.world.phys.AABB box, double margin) {
        var area = box.inflate(margin, 0, margin);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(area.minX, area.minY - 0.6, area.minZ),
                BlockPos.containing(area.maxX, area.minY + 0.1, area.maxZ))) {
            if (world.getFluidState(pos).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    public static boolean solidFloor(BlockGetter world, BlockPos feet) {
        BlockPos floor = feet.below();
        return world.getFluidState(floor).isEmpty()
                && world.getBlockState(floor).isFaceSturdy(world, floor, Direction.UP);
    }

    public static boolean clear(BlockGetter world, BlockPos feet) {
        return world.getFluidState(feet).isEmpty() && world.getFluidState(feet.above()).isEmpty()
                && world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                && world.getBlockState(feet.above()).getCollisionShape(world, feet.above()).isEmpty();
    }

    public static boolean dangerousEdge(BlockGetter world, BlockPos feet) {
        return dangerousEdge(world, feet, true);
    }

    public static boolean dangerousEdge(BlockGetter world, BlockPos feet, boolean avoidWater) {
        if (!solidFloor(world, feet)) return false; // Swimming and ladders use their own rules.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos beside = feet.relative(direction);
            // A solid barrier keeps a villager away from the drop without blocking indoor routes.
            if (!world.getBlockState(beside).getCollisionShape(world, beside).isEmpty()) continue;
            if (world.getFluidState(beside).is(FluidTags.LAVA)
                    || world.getFluidState(beside.below()).is(FluidTags.LAVA)
                    || avoidWater && (world.getFluidState(beside).is(FluidTags.WATER)
                        || world.getFluidState(beside.below()).is(FluidTags.WATER))) return true;
            boolean supported = false;
            for (int drop = 0; drop <= 2; drop++) {
                if (solidFloor(world, beside.below(drop))
                        || world.getFluidState(beside.below(drop)).is(FluidTags.WATER)) {
                    supported = true;
                    break;
                }
            }
            if (!supported) return true;
        }
        return false;
    }

    /** Cross a 1–2 block lava/air gap, landing inside a clear 5x5 platform. */
    public static BlockPos gapLanding(BlockGetter world, BlockPos start, Direction direction) {
        if (!solidFloor(world, start) || !clear(world, start) || dangerousEdge(world, start)) return null;
        BlockPos lip = start.relative(direction);
        if (!solidFloor(world, lip) || !clear(world, lip)) return null;
        int gap = 0;
        while (gap < 3) {
            BlockPos crossing = start.relative(direction, 2 + gap);
            boolean lava = world.getFluidState(crossing.below()).is(FluidTags.LAVA);
            boolean air = world.getBlockState(crossing.below()).isAir();
            if ((!lava && !air) || !clear(world, crossing)) break;
            gap++;
        }
        if (gap < 1 || gap > 2) return null;
        BlockPos shore = start.relative(direction, gap + 2);
        Direction across = direction.getClockWise();
        for (int depth = 0; depth < 5; depth++) {
            for (int width = -2; width <= 2; width++) {
                BlockPos floor = shore.relative(direction, depth).relative(across, width);
                if (!solidFloor(world, floor) || !clear(world, floor)) return null;
            }
        }
        BlockPos landing = shore.relative(direction);
        if (dangerousEdge(world, landing)) return null;
        // Include the top of the arc, so low ceilings cannot turn a jump into a lava fall.
        for (int step = 0; step <= (gap + 3) * 4; step++) {
            double progress = step / ((gap + 3) * 4.0);
            BlockPos body = BlockPos.containing(start.getX() + 0.5 + direction.getStepX() * (gap + 3) * progress,
                    start.getY() + 7.2 * progress * (1 - progress),
                    start.getZ() + 0.5 + direction.getStepZ() * (gap + 3) * progress);
            if (!clear(world, body)) return null;
        }
        return landing;
    }
}
