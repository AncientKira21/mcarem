package com.ancientkira.mca.entity.ai.brain.tasks;

import net.minecraft.sounds.SoundEvents;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Chore;
import com.ancientkira.mca.entity.ai.TaskUtils;
import com.ancientkira.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.ancientkira.mca.server.FarmRepairs;
import com.ancientkira.mca.server.VillagerGriefing;
import com.ancientkira.mca.entity.ai.MoveState;
import java.util.WeakHashMap;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.Comparator;

/** Find actual farm work beyond the vanilla farmer's one-block harvest search. */
public class FarmerWorkTask extends Behavior<VillagerEntityMCA> {
    private record RepairOrder(BlockPos area, BlockPos target, long expires) {}
    private static final Map<VillagerEntityMCA, RepairOrder> orders = new WeakHashMap<>();

    public static boolean canRequestRepair(ServerPlayer player, VillagerEntityMCA farmer) {
        return player.level() == farmer.level() && canRepair(farmer)
                && !orders.containsKey(farmer)
                && findRepair(player.level(), farmer, player.blockPosition()) != null;
    }

    public static void requestRepair(ServerPlayer player, VillagerEntityMCA farmer) {
        ServerLevel world = player.level();
        BlockPos area = player.blockPosition();
        BlockPos target = findRepair(world, farmer, area);
        if (target != null) orders.put(farmer, new RepairOrder(area, target, world.getGameTime() + 1200));
    }

    private static boolean canRepair(VillagerEntityMCA farmer) {
        return farmer.isAlive() && !farmer.isBaby() && !farmer.isSleeping() && !farmer.isKidnapped()
                && !farmer.isNoAi() && !farmer.isTrading()
                && farmer.getVillagerData().profession().is(VillagerProfession.FARMER)
                && farmer.getVillagerBrain().getMoveState() == MoveState.MOVE
                && farmer.getVillagerBrain().getCurrentJob() == Chore.NONE
                && !farmer.getVillagerBrain().isPanicking()
                && !farmer.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && VillagerGriefing.allowed(farmer);
    }

    private static BlockPos findRepair(ServerLevel world, VillagerEntityMCA farmer, BlockPos area) {
        return FarmRepairs.nearby(world, area).stream()
                .filter(pos -> pos.distSqr(farmer.blockPosition()) <= 576 && repairable(world, pos)
                        && world.isEmptyBlock(pos.above()))
                .map(BlockPos::above)
                .filter(pos -> {
                    var path = farmer.getNavigation().createPath(pos, 1);
                    return path != null && path.canReach();
                })
                .findFirst().orElse(null);
    }

    public static void tickRequestedRepair(ServerLevel world, VillagerEntityMCA farmer) {
        RepairOrder order = orders.get(farmer);
        if (order == null) return;
        if (!canRepair(farmer) || world.getGameTime() >= order.expires()) {
            orders.remove(farmer);
            return;
        }

        BlockPos target = order.target();
        if (world.hasChunkAt(target) && repairable(world, target.below()) && world.isEmptyBlock(target)) {
            if (farmer.distanceToSqr(Vec3.atBottomCenterOf(target)) > 5) {
                farmer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 1));
                return;
            }
            till(world, farmer, target);
        }

        BlockPos next = findRepair(world, farmer, order.area());
        if (next == null) orders.remove(farmer);
        else orders.put(farmer, new RepairOrder(order.area(), next, order.expires()));
    }

    private static void till(ServerLevel world, VillagerEntityMCA farmer, BlockPos target) {
        if (world.setBlock(target.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL)) {
            FarmRepairs.repaired(world, target.below());
            farmer.playSound(SoundEvents.HOE_TILL, 1.0F, 1.0F);
            farmer.swing(farmer.getDominantHand());
        }
    }

    private BlockPos target;
    private long nextScan;
    public FarmerWorkTask() { super(Map.of(), 300); }
    private boolean eligible(ServerLevel world, VillagerEntityMCA farmer) {
        return farmer.getVillagerData().profession().is(VillagerProfession.FARMER)
                && !orders.containsKey(farmer) && farmer.getBrain().isActive(Activity.WORK) && farmer.getVillagerBrain().getCurrentJob() == Chore.NONE
                && !farmer.isKidnapped() && !farmer.isNoAi() && !farmer.getVillagerBrain().isPanicking()
                && !farmer.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }
    @Override protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA farmer) {
        if (!eligible(world, farmer) || world.getGameTime() < nextScan) return false;
        nextScan = world.getGameTime() + 40;
        boolean seeds = InventoryUtils.stream(farmer.getInventory()).anyMatch(this::seed);
        target = TaskUtils.getNearbyBlocks(farmer.blockPosition(), world,
                state -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
                        || seeds && (state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT)), 24, 6).stream()
                .filter(pos -> mature(world, pos) || seeds && (world.getBlockState(pos).is(Blocks.FARMLAND) || repairable(world,pos)) && world.isEmptyBlock(pos.above()))
                .map(pos -> world.getBlockState(pos).is(Blocks.FARMLAND) || repairable(world,pos) ? pos.above() : pos)
                .sorted(Comparator.<BlockPos>comparingInt(pos -> mature(world, pos) ? 0 : 1)
                        .thenComparingDouble(pos -> pos.distSqr(farmer.blockPosition())))
                .limit(32)
                .filter(pos -> { var path = farmer.getNavigation().createPath(pos, 1); return path != null && path.canReach(); })
                .findFirst().orElse(null);
        return target != null;
    }
    private static boolean repairable(ServerLevel world,BlockPos pos) {
        return world.getBlockState(pos).is(Blocks.DIRT) && com.ancientkira.mca.server.FarmRepairs.needsRepair(world,pos);
    }
    private boolean seed(ItemStack stack) { return !stack.isEmpty() && stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock; }
    private static boolean mature(ServerLevel world, BlockPos pos) { var state = world.getBlockState(pos); return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state); }
    @Override protected boolean canStillUse(ServerLevel world, VillagerEntityMCA farmer, long time) { return target != null && eligible(world, farmer); }
    @Override protected void tick(ServerLevel world, VillagerEntityMCA farmer, long time) {
        if (farmer.distanceToSqr(Vec3.atBottomCenterOf(target)) > 5) {
            farmer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 1)); return;
        }
        var state = world.getBlockState(target);
        if (mature(world, target)) {
            var drops = Block.getDrops(state, world, target, null, farmer, ItemStack.EMPTY);
            if (!world.destroyBlock(target, false, farmer)) { target = null; return; }
            for (var drop : drops) { var left = farmer.getInventory().addItem(drop); if (!left.isEmpty()) farmer.spawnAtLocation(world, left); }
        }
        if (world.isEmptyBlock(target) && repairable(world, target.below())) {
            till(world, farmer, target);
        }
        if (world.isEmptyBlock(target) && world.getBlockState(target.below()).is(Blocks.FARMLAND)) {
            InventoryUtils.stream(farmer.getInventory()).filter(this::seed).findFirst().ifPresent(seed -> {
                var plant = ((BlockItem) seed.getItem()).getBlock().defaultBlockState();
                if (plant.canSurvive(world, target) && world.setBlock(target, plant, Block.UPDATE_ALL)) seed.shrink(1);
            });
        }
        farmer.swing(farmer.getDominantHand()); target = null;
    }
}
