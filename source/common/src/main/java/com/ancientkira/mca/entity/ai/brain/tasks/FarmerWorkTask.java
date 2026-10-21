package com.ancientkira.mca.entity.ai.brain.tasks;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Chore;
import com.ancientkira.mca.entity.ai.TaskUtils;
import com.ancientkira.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.Comparator;

/** Find actual farm work beyond the vanilla farmer's one-block harvest search. */
public class FarmerWorkTask extends Behavior<VillagerEntityMCA> {
    private BlockPos target;
    private long nextScan;
    public FarmerWorkTask() { super(Map.of(), 300); }
    private boolean eligible(ServerLevel world, VillagerEntityMCA farmer) {
        return farmer.getVillagerData().profession().is(VillagerProfession.FARMER)
                && farmer.getBrain().isActive(Activity.WORK) && farmer.getVillagerBrain().getCurrentJob() == Chore.NONE
                && !farmer.isKidnapped() && !farmer.isNoAi() && !farmer.getVillagerBrain().isPanicking()
                && !farmer.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && world.getGameRules().get(GameRules.MOB_GRIEFING);
    }
    @Override protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA farmer) {
        if (!eligible(world, farmer) || world.getGameTime() < nextScan) return false;
        nextScan = world.getGameTime() + 40;
        boolean seeds = InventoryUtils.stream(farmer.getInventory()).anyMatch(this::seed);
        target = TaskUtils.getNearbyBlocks(farmer.blockPosition(), world,
                state -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
                        || seeds && state.is(Blocks.FARMLAND), 24, 6).stream()
                .filter(pos -> mature(world, pos) || seeds && world.getBlockState(pos).is(Blocks.FARMLAND) && world.isEmptyBlock(pos.above()))
                .map(pos -> world.getBlockState(pos).is(Blocks.FARMLAND) ? pos.above() : pos)
                .sorted(Comparator.<BlockPos>comparingInt(pos -> mature(world, pos) ? 0 : 1)
                        .thenComparingDouble(pos -> pos.distSqr(farmer.blockPosition())))
                .limit(32)
                .filter(pos -> { var path = farmer.getNavigation().createPath(pos, 1); return path != null && path.canReach(); })
                .findFirst().orElse(null);
        return target != null;
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
        if (world.isEmptyBlock(target) && world.getBlockState(target.below()).is(Blocks.FARMLAND)) {
            InventoryUtils.stream(farmer.getInventory()).filter(this::seed).findFirst().ifPresent(seed -> {
                var plant = ((BlockItem) seed.getItem()).getBlock().defaultBlockState();
                if (plant.canSurvive(world, target) && world.setBlock(target, plant, Block.UPDATE_ALL)) seed.shrink(1);
            });
        }
        farmer.swing(farmer.getDominantHand()); target = null;
    }
}
