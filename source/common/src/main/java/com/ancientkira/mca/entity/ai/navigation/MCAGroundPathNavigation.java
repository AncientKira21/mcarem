package com.ancientkira.mca.entity.ai.navigation;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.Collections;
import java.util.WeakHashMap;

public class MCAGroundPathNavigation extends GroundPathNavigation {
    private static final double CLIMB_VERTICAL_SPEED = 0.16D;
    private static final double CLIMB_HORIZONTAL_SPEED = 0.12D;
    private static final double CLIMB_HORIZONTAL_GAIN = 0.35D;
    private static final double LADDER_ENTRY_OFFSET = 0.1D;
    private static final double ASCENT_NODE_TOLERANCE = 0.20D;
    private static final double DESCENT_NODE_TOLERANCE = 0.08D;
    private static final double EXIT_HEIGHT_TOLERANCE = 0.25D;
    private static final double EXIT_VERTICAL_BIAS = 0.08D;
    private final FluidEscapeController fluidEscape;
    private Vec3 jumpStart;
    private BlockPos jumpLanding;
    private int jumpTick;
    private boolean wasEscapingFluid;
    private static final int JUMP_TICKS = 18;
    private final Set<Path> villageRoadPaths = Collections.newSetFromMap(new WeakHashMap<>());

    public MCAGroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
        fluidEscape = new FluidEscapeController(mob, level);
    }

    public boolean isControllingFluid() { return fluidEscape != null && fluidEscape.active(); }

    public boolean isControllingJump() { return jumpLanding != null; }

    public boolean isControllingClimbable() {
        return getClimbContext() != null;
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new MCAWalkNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        this.nodeEvaluator.setCanFloat(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    public boolean canCutCorner(PathType type) {
        return false; // Follow every checked node rather than cutting across the edge buffer.
    }

    @Override
    protected Path createPath(Set<BlockPos> targets, int range, boolean offsetUpward, int accuracy, float followRange) {
        if (!(mob instanceof VillagerEntityMCA villager) || villager.isGuard() || villager.isHostile() || level.isClientSide()) {
            return super.createPath(targets, range, offsetUpward, accuracy, followRange);
        }
        Village village = villager.getResidency().getHomeVillage().orElse(null);
        if (village == null || targets.isEmpty()) {
            return super.createPath(targets, range, offsetUpward, accuracy, followRange);
        }
        // Leave the currently followed route intact; repeated movement requests reuse it.
        if (path != null && !path.isDone() && targets.contains(getTargetPos())) {
            return path;
        }
        MCAWalkNodeEvaluator evaluator = (MCAWalkNodeEvaluator) nodeEvaluator;
        evaluator.configureRoads(village, mob.blockPosition(), targets, true);
        try {
            Path roadPath = super.createPath(targets, range, offsetUpward, accuracy, followRange);
            if (roadPath != null && roadPath.canReach()) {
                villageRoadPaths.add(roadPath);
                return roadPath;
            }
            evaluator.configureRoads(village, mob.blockPosition(), targets, false);
            return super.createPath(targets, range, offsetUpward, accuracy, followRange);
        } finally {
            evaluator.configureRoads(null, mob.blockPosition(), Set.of(), false);
        }
    }

    @Override
    protected boolean canMoveDirectly(Vec3 start, Vec3 end) {
        return false; // A direct shortcut has not passed our edge and fluid checks.
    }

    @Override
    public int getSurfaceY() {
        if (this.mob.isInWater() && this.canFloat()) {
            int surfaceY = this.mob.getBlockY();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(this.mob.getX(), surfaceY, this.mob.getZ());
            int steps = 0;

            while (this.level.getFluidState(pos).is(FluidTags.WATER)) {
                pos.setY(++surfaceY);
                if (++steps > 16) {
                    return this.mob.getBlockY();
                }
            }

            return surfaceY;
        }

        return Mth.floor(this.mob.getY() + 0.5D);
    }

    @Override
    protected boolean canUpdatePath() {
        return super.canUpdatePath() || this.mob.onClimbable();
    }

    @Override
    public void tick() {
        if (fluidEscape.tick()) {
            if (!wasEscapingFluid) clearStaleMovementPath();
            wasEscapingFluid = true;
            jumpLanding = null;
            return;
        }
        if (wasEscapingFluid) {
            wasEscapingFluid = false;
            clearStaleMovementPath();
        }
        if (jumpLanding != null) {
            tickLavaJump();
            return;
        }
        if (tryLavaJump()) return;
        if (stepBackFromEdge()) return;
        if (path != null && !path.isDone() && !mob.onClimbable() && !mob.isInWater()) {
            BlockPos next = path.getNextNode().asBlockPos();
            if (TerrainSafety.dangerousEdge(level, next) && !TerrainSafety.dangerousEdge(level, mob.blockPosition())
                    || level.getFluidState(next).is(FluidTags.LAVA)
                    || level.getFluidState(next.below()).is(FluidTags.LAVA)
                    || level.getFluidState(next).is(FluidTags.WATER)
                    || level.getFluidState(next.below()).is(FluidTags.WATER)) {
                stop();
                mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
                mob.setXxa(0); mob.setZza(0);
                mob.setDeltaMovement(0, mob.getDeltaMovement().y(), 0);
                return;
            }
        }
        super.tick();
        ClimbContext context = getClimbContext();
        if (context != null) {
            applyClimbableMotion(context);
        }
    }

    private boolean stepBackFromEdge() {
        if (!mob.onGround() || mob.onClimbable() || mob.isInWater() || mob.isPassenger()
                || !TerrainSafety.dangerousEdge(level, mob.blockPosition())) return false;
        BlockPos feet = mob.blockPosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos safe = feet.relative(direction);
            if (!TerrainSafety.solidFloor(level, safe) || !TerrainSafety.clear(level, safe)
                    || TerrainSafety.dangerousEdge(level, safe)) continue;
            Vec3 center = Vec3.atBottomCenterOf(safe);
            if (!level.noCollision(mob, mob.getBoundingBox().move(center.subtract(mob.position())))) continue;
            mob.getMoveControl().setWantedPosition(center.x(), center.y(), center.z(), 0.6);
            return true;
        }
        // No adjacent buffered tile: let navigation take a dry route along the edge.
        return false;
    }

    private boolean tryLavaJump() {
        if (path == null || path.isDone() || !mob.onGround() || mob.isPassenger()
                || mob instanceof VillagerEntityMCA person
                    && person.getAgeState() == com.ancientkira.mca.entity.ai.relationship.AgeState.BABY) return false;
        int index = path.getNextNodeIndex();
        if (index <= 0) return false;
        BlockPos from = path.getNode(index - 1).asBlockPos();
        BlockPos to = path.getNextNode().asBlockPos();
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        int distance = Math.abs(dx) + Math.abs(dz);
        if (to.getY() != from.getY() || dx != 0 && dz != 0 || distance < 4 || distance > 5) return false;
        Direction direction = dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
        BlockPos safe = TerrainSafety.gapLanding(level, from, direction);
        if (!to.equals(safe)) { stop(); return true; }
        Vec3 launch = Vec3.atBottomCenterOf(from);
        if (mob.position().distanceToSqr(launch) > 0.12) {
            mob.getMoveControl().setWantedPosition(launch.x(), launch.y(), launch.z(), 0.5);
            return true;
        }
        // Check the villager's full width, not just the central block, before committing.
        for (int step = 1; step <= JUMP_TICKS; step++) {
            Vec3 point = jumpPoint(launch, to, step);
            if (!level.noCollision(mob, mob.getBoundingBox().move(point.subtract(mob.position())))) {
                stop(); return true;
            }
        }
        jumpStart = mob.position();
        jumpLanding = to;
        jumpTick = 0;
        tickLavaJump();
        return true;
    }

    private void clearStaleMovementPath() {
        stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
        mob.setXxa(0);
        mob.setZza(0);
        mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH);
        mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        // Preserve WALK_TARGET so follow, work and combat can plan a fresh route from this bank.
    }

    private static Vec3 jumpPoint(Vec3 start, BlockPos landing, int tick) {
        double t = tick / (double) JUMP_TICKS;
        return start.lerp(Vec3.atBottomCenterOf(landing), t).add(0, 7.2 * t * (1 - t), 0);
    }

    private void tickLavaJump() {
        if (!mob.isAlive() || mob.isPassenger()) { jumpLanding = null; stop(); return; }
        if (jumpTick >= JUMP_TICKS) {
            jumpLanding = null;
            mob.setDeltaMovement(0, mob.getDeltaMovement().y(), 0);
            clearStaleMovementPath();
            return;
        }
        Vec3 next = jumpPoint(jumpStart, jumpLanding, ++jumpTick);
        Vec3 delta = next.subtract(mob.position());
        if (!level.noCollision(mob, mob.getBoundingBox().move(delta))) {
            // A new obstruction appeared. Return toward the launch bank instead of pushing into it.
            next = jumpStart.add(0, Math.max(0, mob.getY() - jumpStart.y()), 0);
            delta = next.subtract(mob.position());
            if (delta.horizontalDistanceSqr() > 0.16) delta = new Vec3(delta.x(), 0, delta.z()).normalize().scale(0.4);
        }
        mob.setXxa(0); mob.setZza(0);
        mob.setDeltaMovement(delta);
    }

    @Override
    protected void followThePath() {
        if (this.path == null || this.path.isDone()) {
            return;
        }

        ClimbContext context = getClimbContext();
        if (context == null) {
            super.followThePath();
            return;
        }

        Vec3 position = this.getTempMobPos();
        if (context.pathTargetsClimbable() && this.mob.onClimbable()) {
            double tolerance = context.verticalDirection() < 0
                    ? DESCENT_NODE_TOLERANCE
                    : ASCENT_NODE_TOLERANCE;
            if (hasReachedHeight(context.targetNode().y, context.verticalDirection(), tolerance)) {
                this.path.advance();
            }
        }

        this.doStuckDetection(position);
    }

    @Override
    protected double getGroundY(Vec3 position) {
        BlockPos targetPos = BlockPos.containing(position);
        if (isClimbable(targetPos)) {
            return this.mob.getY();
        }
        return super.getGroundY(position);
    }

    private ClimbContext getClimbContext() {
        Path path = this.path;
        if (path == null || path.isDone()) {
            return null;
        }

        int nextNodeIndex = path.getNextNodeIndex();
        if (nextNodeIndex < 0 || nextNodeIndex >= path.getNodeCount()) {
            return null;
        }

        Node nextNode = path.getNode(nextNodeIndex);
        if (isClimbable(nextNode.asBlockPos())) {
            Node followingNode = nextNodeIndex + 1 < path.getNodeCount()
                    ? path.getNode(nextNodeIndex + 1)
                    : null;
            boolean exitsClimbable = followingNode != null
                    && !isClimbable(followingNode.asBlockPos());
            return new ClimbContext(
                    nextNode,
                    exitsClimbable ? followingNode : nextNode,
                    true,
                    exitsClimbable,
                    getVerticalDirection(path, nextNodeIndex)
            );
        }

        if (this.mob.onClimbable() && nextNodeIndex > 0) {
            Node previousNode = path.getNode(nextNodeIndex - 1);
            if (isClimbable(previousNode.asBlockPos())) {
                return new ClimbContext(
                        previousNode,
                        nextNode,
                        false,
                        true,
                        getVerticalDirection(path, nextNodeIndex - 1)
                );
            }
        }

        return null;
    }

    private static int getVerticalDirection(Path path, int climbableNodeIndex) {
        Node climbableNode = path.getNode(climbableNodeIndex);
        if (climbableNodeIndex + 1 < path.getNodeCount()) {
            Node followingNode = path.getNode(climbableNodeIndex + 1);
            int direction = Integer.compare(followingNode.y, climbableNode.y);
            if (direction != 0) {
                return direction;
            }
        }

        if (climbableNodeIndex > 0) {
            Node previousNode = path.getNode(climbableNodeIndex - 1);
            return Integer.compare(climbableNode.y, previousNode.y);
        }

        return 0;
    }

    private void applyClimbableMotion(ClimbContext context) {
        BlockPos climbablePos = findAttachedClimbable(context.climbableNode().asBlockPos());
        Vec3 anchor = getClimbableAnchor(climbablePos);

        if (!this.mob.onClimbable()) {
            this.mob.getMoveControl().setWantedPosition(
                    anchor.x(), this.mob.getY(), anchor.z(), this.speedModifier
            );
            Vec3 movement = this.mob.getDeltaMovement();
            this.mob.setDeltaMovement(
                    horizontalVelocity(anchor.x() - this.mob.getX()),
                    movement.y(),
                    horizontalVelocity(anchor.z() - this.mob.getZ())
            );
            return;
        }

        double targetY = context.targetNode().y;
        boolean atExitHeight = context.exitsClimbable() && isAtExitHeight(context, targetY);
        double targetX = anchor.x();
        double targetZ = anchor.z();
        if (context.exitsClimbable() && (!context.pathTargetsClimbable() || atExitHeight)) {
            targetX = context.targetNode().x + 0.5D;
            targetZ = context.targetNode().z + 0.5D;
        }

        this.mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, this.speedModifier);

        double verticalDelta = targetY - this.mob.getY();
        double controlledY;
        if (context.verticalDirection() > 0) {
            controlledY = Mth.clamp(verticalDelta, 0.0D, CLIMB_VERTICAL_SPEED);
        } else if (context.verticalDirection() < 0) {
            controlledY = Mth.clamp(verticalDelta, -CLIMB_VERTICAL_SPEED, 0.0D);
        } else {
            controlledY = Mth.clamp(verticalDelta, -CLIMB_VERTICAL_SPEED, CLIMB_VERTICAL_SPEED);
        }

        if (atExitHeight && context.verticalDirection() != 0) {
            controlledY = context.verticalDirection() > 0
                    ? Math.max(controlledY, EXIT_VERTICAL_BIAS)
                    : Math.min(controlledY, -EXIT_VERTICAL_BIAS);
        }

        this.mob.setXxa(0.0F);
        this.mob.setZza(0.0F);
        this.mob.setDeltaMovement(
                horizontalVelocity(targetX - this.mob.getX()),
                controlledY,
                horizontalVelocity(targetZ - this.mob.getZ())
        );
    }

    private boolean isAtExitHeight(ClimbContext context, double targetY) {
        return hasReachedHeight(targetY, context.verticalDirection(), EXIT_HEIGHT_TOLERANCE);
    }

    private boolean hasReachedHeight(double targetY, int verticalDirection, double tolerance) {
        if (verticalDirection > 0) {
            return this.mob.getY() >= targetY - tolerance;
        }
        if (verticalDirection < 0) {
            return this.mob.getY() <= targetY + tolerance;
        }
        return Math.abs(targetY - this.mob.getY()) <= tolerance;
    }

    private Vec3 getClimbableAnchor(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        Vec3 center = Vec3.atCenterOf(pos);
        if (state.getBlock() instanceof LadderBlock) {
            Direction facing = state.getValue(LadderBlock.FACING);
            return center.add(
                    facing.getStepX() * LADDER_ENTRY_OFFSET,
                    0.0D,
                    facing.getStepZ() * LADDER_ENTRY_OFFSET
            );
        }
        return center;
    }

    private static double horizontalVelocity(double delta) {
        return Mth.clamp(
                delta * CLIMB_HORIZONTAL_GAIN,
                -CLIMB_HORIZONTAL_SPEED,
                CLIMB_HORIZONTAL_SPEED
        );
    }

    private BlockPos findAttachedClimbable(BlockPos fallback) {
        BlockPos mobPos = this.mob.blockPosition();
        if (isClimbable(mobPos)) {
            return mobPos;
        }

        BlockPos above = mobPos.above();
        if (isClimbable(above)) {
            return above;
        }

        BlockPos below = mobPos.below();
        if (isClimbable(below)) {
            return below;
        }

        return fallback;
    }

    private boolean isClimbable(BlockPos pos) {
        return this.level.getBlockState(pos).is(BlockTags.CLIMBABLE);
    }

    private record ClimbContext(
            Node climbableNode,
            Node targetNode,
            boolean pathTargetsClimbable,
            boolean exitsClimbable,
            int verticalDirection
    ) {
    }
}
